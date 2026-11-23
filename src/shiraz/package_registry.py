# SPDX-License-Identifier: GPL-3.0-only
"""Host-owned package identities for a future broker; not a guest-accessible IPC API.

Logical UIDs do not provide host OS isolation. The broker must authenticate its
caller before using authorize_service; guest-supplied UIDs are not credentials.
"""

import fcntl
import hashlib
import json
import os
import re
import sqlite3
import stat
import tempfile
from contextlib import ExitStack, contextmanager
from pathlib import Path

from . import account_store, job_store, job_work, sync_owners, sync_requests
from .apk import FormatError, inspect_apk
from .apk_verifier import verify_signatures
from .authenticators import inspect_authenticators
from .service_filter import matches, validate_query
from .sync_adapters import inspect_sync_adapters

NAME = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*')


class ServiceUnavailableError(PermissionError):
    """Authenticated service discovery found no enabled target component."""


class ReceiverFilteredError(PermissionError):
    """Authenticated receiver absent, disabled, or excluded by recipient policy."""


def boolean(value, default):
    if value is None:
        return default
    if value in ('true', '1', True):
        return True
    if value in ('false', '0', False):
        return False
    raise FormatError('Resource-based package flags require resolution')


def version(manifest):
    def part(key):
        value = str(manifest.get(key) or '0')
        try:
            result = int(value, 16 if value.startswith('0x') else 10)
        except ValueError as error:
            raise FormatError('Invalid version code') from error
        if not 0 <= result <= 0x7FFFFFFF:
            raise FormatError('Version code out of range')
        return result

    return (part('version_code_major') << 32) | part('version_code')


class PackageRegistry:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        if self.root.is_symlink() or self.root.stat().st_mode & 0o077:
            raise FormatError('Package registry must be a private directory')
        self.objects = self.root / 'apks'
        self.objects.mkdir(exist_ok=True, mode=0o700)
        self.db = self.root / 'registry.sqlite'
        with self.connect() as db:
            if db.execute('PRAGMA user_version').fetchone()[0] not in (
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                17,
                18,
                19,
                20,
            ):
                raise FormatError('Unsupported package registry schema')
            db.executescript('''
                BEGIN IMMEDIATE;
                CREATE TABLE IF NOT EXISTS packages(
                    package TEXT PRIMARY KEY, uid INTEGER UNIQUE NOT NULL,
                    version INTEGER NOT NULL, sha256 TEXT NOT NULL,
                    signers TEXT NOT NULL, manifest TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS permissions(
                    name TEXT PRIMARY KEY, owner TEXT NOT NULL, protection INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS retired_uids(uid INTEGER PRIMARY KEY);
                CREATE TABLE IF NOT EXISTS authenticator_owners(
                    type TEXT PRIMARY KEY, package TEXT NOT NULL, uid INTEGER NOT NULL, signers TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS sync_adapter_owners(
                    authority TEXT NOT NULL, account_type TEXT NOT NULL,
                    package TEXT NOT NULL, uid INTEGER NOT NULL, signers TEXT NOT NULL,
                    PRIMARY KEY(authority,account_type));
                CREATE TABLE IF NOT EXISTS accounts(
                    owner_uid INTEGER NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    password TEXT, userdata TEXT NOT NULL, tokens TEXT NOT NULL,
                    PRIMARY KEY(owner_uid,type,name));
                CREATE TABLE IF NOT EXISTS account_token_revisions(
                    owner_uid INTEGER NOT NULL,type TEXT NOT NULL,apk_sha256 TEXT NOT NULL,
                    revision TEXT NOT NULL,PRIMARY KEY(owner_uid,type));
                CREATE TABLE IF NOT EXISTS custom_account_tokens(
                    owner_uid INTEGER NOT NULL,type TEXT NOT NULL,name TEXT NOT NULL,
                    caller_uid INTEGER NOT NULL,caller_sha256 TEXT NOT NULL,key TEXT NOT NULL,
                    owner_sha256 TEXT NOT NULL,token TEXT NOT NULL,expires_ms INTEGER NOT NULL,
                    PRIMARY KEY(owner_uid,type,name,caller_uid,caller_sha256,key));
                CREATE TABLE IF NOT EXISTS account_change_revisions(
                    signers TEXT PRIMARY KEY, revision TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS persisted_jobs(
                    owner_uid INTEGER NOT NULL, job_id INTEGER NOT NULL,
                    apk_sha256 TEXT NOT NULL, revision TEXT NOT NULL,
                    scheduled_at_ms INTEGER NOT NULL, definition TEXT NOT NULL,
                    PRIMARY KEY(owner_uid,job_id));
                CREATE TABLE IF NOT EXISTS persisted_job_claims(
                    owner_uid INTEGER NOT NULL, job_id INTEGER NOT NULL,
                    apk_sha256 TEXT NOT NULL, revision TEXT NOT NULL, token TEXT NOT NULL,
                    PRIMARY KEY(owner_uid,job_id));
                CREATE TABLE IF NOT EXISTS pending_intent_tokens(
                    token TEXT PRIMARY KEY, owner_uid INTEGER NOT NULL,
                    owner_sha256 TEXT NOT NULL, package TEXT NOT NULL,
                    kind INTEGER NOT NULL, request_code INTEGER NOT NULL,
                    flags INTEGER NOT NULL, filter_hash TEXT NOT NULL, payload BLOB NOT NULL,
                    UNIQUE(owner_uid,kind,request_code,flags,filter_hash));
                CREATE TABLE IF NOT EXISTS pending_sync_requests(
                    request_id TEXT PRIMARY KEY, caller_uid INTEGER NOT NULL, payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS sync_request_claims(
                    request_id TEXT PRIMARY KEY, token TEXT NOT NULL, service TEXT NOT NULL,
                    account TEXT NOT NULL, parallel INTEGER NOT NULL, rerun INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS sync_request_schedule(
                    request_id TEXT PRIMARY KEY, attempts INTEGER NOT NULL,
                    not_before_ms INTEGER NOT NULL, suspended INTEGER NOT NULL);
            ''')
            job_store.migrate(db)
            job_work.migrate(db)
            job_store.reconcile_boot(db)
            self._reconcile_authenticator_owners(db)
            sync_owners.reconcile(db, boolean)
            db.execute('PRAGMA user_version=20')

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.db, timeout=10)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def enroll(self, apk, *, expected_package=None, expected_sha256=None, expected_current=None):
        if expected_package is not None and (
            not isinstance(expected_package, str)
            or len(expected_package) > 1024
            or not NAME.fullmatch(expected_package)
        ):
            raise FormatError('Invalid expected package identity')
        if expected_sha256 is not None and (
            not isinstance(expected_sha256, str)
            or not re.fullmatch('[0-9a-f]{64}', expected_sha256)
        ):
            raise FormatError('Invalid expected APK digest')
        if expected_current is not None and (
            not isinstance(expected_current, str)
            or expected_current != 'absent'
            and not re.fullmatch('[0-9a-f]{64}', expected_current)
        ):
            raise FormatError('Invalid expected current package state')
        # No launcher requirement: Google services and other service-only APKs need identities too.
        with tempfile.TemporaryDirectory(dir=self.root) as temporary:
            frozen = Path(temporary) / 'input.apk'
            fd = os.open(apk, os.O_RDONLY | os.O_NONBLOCK)
            with os.fdopen(fd, 'rb') as source, frozen.open('wb') as out:
                if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
                    raise FormatError('APK must be a regular file')
                remaining = 1024 * 1024 * 1024
                while chunk := source.read(min(remaining + 1, 1024 * 1024)):
                    remaining -= len(chunk)
                    if remaining < 0:
                        raise FormatError('APK size limit')
                    out.write(chunk)
                out.flush()
                os.fsync(out.fileno())
            verified = verify_signatures(frozen)
            report = inspect_apk(frozen)
            if verified['apk_sha256'] != report['apk_sha256']:
                raise FormatError('Verified APK content changed')
            from .manifest_flags import normalize

            manifest = normalize(frozen, report['manifest'])
            authenticators = inspect_authenticators(frozen, manifest)
            if authenticators:
                manifest['authenticators'] = authenticators
            sync_adapters = inspect_sync_adapters(frozen, manifest)
            if sync_adapters:
                manifest['sync_adapters'] = sync_adapters
            package = manifest['package']
            if not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package):
                raise FormatError('Invalid package identity')
            if expected_package is not None and package != expected_package:
                raise FormatError('Verified APK package differs from enrollment request')
            if expected_sha256 is not None and verified['apk_sha256'] != expected_sha256:
                raise FormatError('Verified APK digest differs from enrollment request')
            code = version(manifest)
            signers = json.dumps(sorted(verified['verified_signers_sha256']))
            digest = verified['apk_sha256']
            with ExitStack() as leases, self.connect() as db:
                db.execute('BEGIN IMMEDIATE')
                old = db.execute('SELECT * FROM packages WHERE package=?', (package,)).fetchone()
                current = old['sha256'] if old is not None else 'absent'
                if expected_current is not None and current != expected_current:
                    raise FormatError('Registered package changed since enrollment was prepared')
                if current != digest:
                    leases.enter_context(self._generation_lock(package, exclusive=True))
                if old is not None:
                    if old['signers'] != signers:
                        raise FormatError(
                            'Package signer changed; signing lineage updates are unsupported'
                        )
                    if code < old['version']:
                        raise FormatError('Package downgrade rejected')
                    if code == old['version'] and digest != old['sha256']:
                        raise FormatError('Changed APK requires a higher version code')
                    uid = old['uid']
                else:
                    uid = db.execute('''SELECT COALESCE(MAX(uid),9999)+1 FROM
                        (SELECT uid FROM packages UNION ALL SELECT uid FROM retired_uids)''').fetchone()[
                        0
                    ]
                    if uid > 19999:
                        raise FormatError('Package UID capacity exceeded')
                definitions = manifest.get('defined_permissions', [])
                if len(definitions) > 256:
                    raise FormatError('Permission definition limit')
                seen = set()
                for definition in definitions:
                    name = definition.get('name')
                    if not name or not NAME.fullmatch(name) or name in seen:
                        raise FormatError('Invalid or duplicate permission definition')
                    seen.add(name)
                    # Platform declarations remain APK metadata, never package-owned
                    # grant definitions. System companion APKs may declare them too.
                    if name.startswith('android.'):
                        continue
                    owner = db.execute(
                        'SELECT owner FROM permissions WHERE name=?', (name,)
                    ).fetchone()
                    if owner and owner['owner'] != package:
                        raise FormatError('Permission already owned by another package')
                destination = self.objects / (digest + '.apk')
                if destination.exists():
                    if destination.is_symlink() or not destination.is_file():
                        raise FormatError('Stored APK type mismatch')
                    stored = hashlib.sha256()
                    with destination.open('rb') as source:
                        while chunk := source.read(1024 * 1024):
                            stored.update(chunk)
                    if stored.hexdigest() != digest:
                        raise FormatError('Stored APK content mismatch')
                else:
                    os.replace(frozen, destination)
                    directory = os.open(self.objects, os.O_RDONLY)
                    try:
                        os.fsync(directory)
                    finally:
                        os.close(directory)
                db.execute(
                    'INSERT OR REPLACE INTO packages VALUES(?,?,?,?,?,?)',
                    (package, uid, code, digest, signers, json.dumps(manifest)),
                )
                db.execute('DELETE FROM permissions WHERE owner=?', (package,))
                db.executemany(
                    'INSERT INTO permissions VALUES(?,?,?)',
                    [
                        (item['name'], package, int(item.get('protection', 0)))
                        for item in definitions
                        if not item['name'].startswith('android.')
                    ],
                )
                self._reconcile_authenticator_owners(db)
                sync_owners.reconcile(db, boolean)
                job_store.reconcile(
                    self, db, db.execute('SELECT * FROM packages WHERE uid=?', (uid,)).fetchone()
                )
            return {'package': package, 'uid': uid, 'version': code, 'apk_sha256': digest}

    @contextmanager
    def _generation_lock(self, package, *, exclusive):
        if not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package):
            raise FormatError('Invalid package identity')
        directory = self.root / 'leases'
        directory.mkdir(mode=0o700, exist_ok=True)
        if directory.is_symlink() or directory.stat().st_mode & 0o077:
            raise FormatError('Package leases require a private directory')
        path = directory / (hashlib.sha256(package.encode()).hexdigest() + '.lock')
        fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_CLOEXEC | os.O_NOFOLLOW, 0o600)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise FormatError('Package lease must be a regular file')
            try:
                fcntl.flock(fd, (fcntl.LOCK_EX if exclusive else fcntl.LOCK_SH) | fcntl.LOCK_NB)
            except BlockingIOError:
                raise FormatError('Package generation is in use or being replaced') from None
            yield
        finally:
            os.close(fd)

    @contextmanager
    def lease(self, package, expected_sha256):
        """Pin a registered generation until the trusted process owner releases it."""
        with self._generation_lock(package, exclusive=False):
            identity = self.identity(package)
            if identity['sha256'] != expected_sha256:
                raise FormatError('Lease APK does not match enrolled package')
            yield identity

    def retire(self, package, *, expected_sha256, expected_uid):
        """Revoke a registered generation, reserving its UID permanently.

        Host lifecycle coordination must stop its processes and existing channels
        separately. Immutable APK objects and application data are retained.
        """
        if not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package):
            raise FormatError('Invalid package identity')
        if not isinstance(expected_sha256, str) or not re.fullmatch(
            '[0-9a-f]{64}', expected_sha256
        ):
            raise FormatError('Invalid expected APK digest')
        if type(expected_uid) is not int or not 10000 <= expected_uid <= 19999:
            raise FormatError('Invalid expected package UID')
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute(
                'SELECT uid,sha256 FROM packages WHERE package=?', (package,)
            ).fetchone()
            if row is None:
                return False
            if row['sha256'] != expected_sha256 or row['uid'] != expected_uid:
                raise FormatError('Registered package changed since retirement was prepared')
            db.execute('INSERT INTO retired_uids VALUES(?)', (row['uid'],))
            db.execute('DELETE FROM permissions WHERE owner=?', (package,))
            db.execute('DELETE FROM packages WHERE package=?', (package,))
            db.execute('DELETE FROM persisted_jobs WHERE owner_uid=?', (row['uid'],))
            self._reconcile_authenticator_owners(db)
            sync_owners.reconcile(db, boolean)
        return True

    def identity(self, package):
        with self.connect() as db:
            row = db.execute(
                'SELECT package,uid,sha256 FROM packages WHERE package=?', (package,)
            ).fetchone()
            if row is None:
                raise PermissionError('Unknown package')
            return dict(row)

    _VERIFIED_APK_CACHE: dict[tuple[str, str], tuple[int, int, int, int, int]] = {}

    @staticmethod
    def _verified_apk_cache_path(expected_sha256):
        if os.environ.get('SHIRAZ_FAST_INSPECT') != '1':
            return None
        base = os.environ.get('XDG_CACHE_HOME', '')
        cache_root = Path(base) if base and Path(base).is_absolute() else Path.home() / '.cache'
        return cache_root / 'shiraz' / 'verified' / (expected_sha256 + '.json')

    @staticmethod
    def _stat_stamp(st):
        return (st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_ctime_ns)

    def launch_apk(self, package, expected_sha256):
        identity = self.identity(package)
        if identity['sha256'] != expected_sha256:
            raise FormatError('Launch APK does not match enrolled package')
        path = self.objects / (identity['sha256'] + '.apk')
        try:
            st = os.lstat(path)
        except OSError:
            raise FormatError('Registered APK missing or not a regular file') from None
        if not stat.S_ISREG(st.st_mode):
            raise FormatError('Registered APK missing or not a regular file')
        resolved = path.resolve()
        stamp = self._stat_stamp(st)
        cache_path = self._verified_apk_cache_path(expected_sha256)
        mem_key = (str(resolved), expected_sha256)
        if cache_path is not None:
            if self._VERIFIED_APK_CACHE.get(mem_key) == stamp:
                return resolved
            try:
                if cache_path.is_file() and not cache_path.is_symlink():
                    cached = json.loads(cache_path.read_text(encoding='utf-8'))
                    if (
                        isinstance(cached, dict)
                        and cached.get('path') == str(resolved)
                        and cached.get('sha256') == expected_sha256
                        and cached.get('stamp') == list(stamp)
                    ):
                        self._VERIFIED_APK_CACHE[mem_key] = stamp
                        return resolved
            except (OSError, ValueError, TypeError):
                pass
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != expected_sha256:
            raise FormatError('Registered APK content mismatch')
        if cache_path is not None:
            self._VERIFIED_APK_CACHE[mem_key] = stamp
            try:
                cache_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                tmp = cache_path.with_name(f'.{cache_path.name}.tmp.{os.getpid()}')
                fd = os.open(tmp, os.O_CREAT | os.O_WRONLY | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
                try:
                    payload = json.dumps(
                        {'path': str(resolved), 'sha256': expected_sha256, 'stamp': list(stamp)}
                    ).encode('utf-8')
                    os.write(fd, payload)
                finally:
                    os.close(fd)
                os.replace(tmp, cache_path)
            except OSError:
                pass
        return resolved

    def lookup_package(
        self, caller_uid, caller_sha256, *, uid=None, package=None, all_packages=False
    ):
        """Authenticated installed identity lookup; no signer or filesystem data exposed."""
        if (
            type(all_packages) is not bool
            or all_packages
            and (uid is not None or package is not None)
        ):
            raise ValueError('Invalid package enumeration')
        if not all_packages and (uid is None) == (package is None):
            raise ValueError('Exactly one package lookup key is required')
        if uid is not None and (type(uid) is not int or not 0 <= uid <= 0x7FFFFFFF):
            raise ValueError('Invalid package UID')
        if package is not None and (
            not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package)
        ):
            raise ValueError('Invalid package name')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            if all_packages:
                rows = db.execute('SELECT package,uid FROM packages ORDER BY package').fetchall()
            else:
                column, value = ('uid', uid) if uid is not None else ('package', package)
                rows = db.execute(
                    'SELECT package,uid FROM packages WHERE ' + column + '=?', (value,)
                ).fetchall()
            result = [dict(row) for row in rows]
            if len(json.dumps({'ok': True, 'packages': result}).encode()) > 60000:
                raise FormatError('Package enumeration exceeds reply size limit')
            return result

    def _authenticator_candidates(self, db):
        candidates = {}
        for row in db.execute('SELECT * FROM packages ORDER BY uid'):
            manifest = json.loads(row['manifest'])
            if not boolean(manifest.get('application_enabled'), True):
                continue
            seen = set()
            for descriptor in manifest.get('authenticators', []):
                services = [
                    item
                    for item in manifest.get('services', [])
                    if item['name'] == descriptor['component']
                ]
                if len(services) != 1:
                    raise FormatError('Authenticator service identity is ambiguous or absent')
                service = services[0]
                if not boolean(service.get('enabled'), True):
                    continue
                kind = descriptor['type']
                if kind in seen:
                    raise FormatError('Duplicate authenticator type in one package')
                seen.add(kind)
                candidates.setdefault(kind, []).append((row, service, descriptor))
        return candidates

    def _reconcile_authenticator_owners(self, db):
        """Preserve an available owner; otherwise select the earliest enrolled live UID.

        A replacement owns only its own future account namespace, never credentials
        belonging to the previous UID. System-image precedence is not modeled.
        """
        candidates = self._authenticator_candidates(db)
        previous = {row['type']: row for row in db.execute('SELECT * FROM authenticator_owners')}
        for kind, entries in candidates.items():
            prior = previous.get(kind)
            selected = entries[0][0]
            if prior is not None:
                for row, _, _ in entries:
                    if all(row[key] == prior[key] for key in ('package', 'uid', 'signers')):
                        selected = row
                        break
            db.execute(
                'INSERT OR REPLACE INTO authenticator_owners VALUES(?,?,?,?)',
                (kind, selected['package'], selected['uid'], selected['signers']),
            )
        for kind in previous.keys() - candidates.keys():
            db.execute('DELETE FROM authenticator_owners WHERE type=?', (kind,))

    def account_operation(self, caller_uid, caller_sha256, request):
        """Authenticate every read/write in the same transaction as its account row."""
        account_store.validate(request)
        with self.connect() as db:
            db.execute('BEGIN' if request['action'] in ('list', 'snapshot') else 'BEGIN IMMEDIATE')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            if request['action'] in ('list', 'snapshot'):
                # Consent-based visibility for other signers is not implemented.
                rows = db.execute(
                    '''SELECT a.name,a.type,p.manifest FROM accounts a
                    JOIN authenticator_owners o ON a.owner_uid=o.uid AND a.type=o.type
                    JOIN packages p ON p.package=o.package AND p.uid=o.uid AND p.signers=o.signers
                    WHERE o.signers=? AND (? IS NULL OR a.type=?) ORDER BY a.type,a.name''',
                    (caller['signers'], request.get('type'), request.get('type')),
                ).fetchall()
                result = [
                    dict(name=row['name'], type=row['type'])
                    for row in rows
                    if boolean(json.loads(row['manifest']).get('application_enabled'), True)
                ]
                if (
                    len(result) > 1024
                    or len(json.dumps({'ok': True, 'result': result}).encode()) > 60000
                ):
                    raise ValueError('Account enumeration reply limit')
                if request['action'] == 'snapshot':
                    row = db.execute(
                        'SELECT revision FROM account_change_revisions WHERE signers=?',
                        (caller['signers'],),
                    ).fetchone()
                    # Include current visibility so owner disable/removal also changes the snapshot.
                    stamp = json.dumps(
                        [row['revision'] if row else '', result], separators=(',', ':')
                    ).encode()
                    return dict(revision=hashlib.sha256(stamp).hexdigest(), accounts=result)
                return result
            owner = db.execute(
                'SELECT * FROM authenticator_owners WHERE type=?', (request['type'],)
            ).fetchone()
            if owner is None or caller['signers'] != owner['signers']:
                raise PermissionError('Caller does not manage this account type')
            current = db.execute(
                'SELECT * FROM packages WHERE package=?', (owner['package'],)
            ).fetchone()
            if current is None or any(current[key] != owner[key] for key in ('uid', 'signers')):
                raise PermissionError('Authenticator owner unavailable')
            if not boolean(json.loads(current['manifest']).get('application_enabled'), True):
                raise PermissionError('Authenticator owner disabled')
            revision = account_store.token_revision(
                db, owner['uid'], request['type'], current['sha256']
            )
            if request['action'] in (
                'prepare_token',
                'commit_token',
                'prepare_custom_token',
                'commit_custom_token',
            ):
                descriptors = [
                    item
                    for item in json.loads(current['manifest']).get('authenticators', [])
                    if item['type'] == request['type']
                ]
                if len(descriptors) != 1:
                    raise PermissionError('Authenticator descriptor unavailable')
                custom = descriptors[0]['custom_tokens']
                if request['action'] in ('prepare_custom_token', 'commit_custom_token'):
                    return account_store.custom_token_operation(
                        db,
                        owner['uid'],
                        current['sha256'],
                        caller_uid,
                        caller_sha256,
                        revision,
                        custom,
                        request,
                    )
                if request['action'] == 'prepare_token':
                    token = (
                        None
                        if custom
                        else account_store.operate(
                            db, owner['uid'], dict(request, action='get_token')
                        )
                    )
                    return dict(
                        owner_uid=owner['uid'],
                        owner_sha256=current['sha256'],
                        revision=revision,
                        custom_tokens=custom,
                        token=token,
                    )
                if (
                    custom
                    or request['expected_uid'] != owner['uid']
                    or request['expected_sha256'] != current['sha256']
                    or request['revision'] != revision
                ):
                    return False
                row = db.execute(
                    'SELECT 1 FROM accounts WHERE owner_uid=? AND type=? AND name=?',
                    (owner['uid'], request['type'], request['name']),
                ).fetchone()
                if row is None:
                    return False
                account_store.operate(
                    db,
                    owner['uid'],
                    {key: request[key] for key in ('name', 'type', 'key', 'value')}
                    | {'action': 'set_token'},
                )
                return True
            result = account_store.operate(db, owner['uid'], request)
            if request['action'] in ('add', 'remove') and result:
                db.execute(
                    'INSERT OR REPLACE INTO account_change_revisions VALUES(?,?)',
                    (caller['signers'], os.urandom(16).hex()),
                )
            return result

    def resolve_authenticator(self, caller_uid, caller_sha256, account_type):
        """Resolve a managed type for a future host-authorized authentication session.

        This does not grant ACCOUNT_MANAGER or expose a service endpoint. General
        sessions require matching signers; account-add uses a separate selector
        and an operation-limited endpoint. Cross-signer token consent is not implemented.
        """
        return self._resolve_authenticator(caller_uid, caller_sha256, account_type, managed=True)

    def resolve_add_account(self, caller_uid, caller_sha256, account_type):
        """Select an owner for an add-only session; grants no account/token access."""
        return self._resolve_authenticator(caller_uid, caller_sha256, account_type, managed=False)

    def _resolve_authenticator(self, caller_uid, caller_sha256, account_type, *, managed):
        account_store.text(account_type, 'type')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            owner = db.execute(
                'SELECT * FROM authenticator_owners WHERE type=?', (account_type,)
            ).fetchone()
            if owner is None or managed and caller['signers'] != owner['signers']:
                raise PermissionError('Caller does not manage this account type')
            selected = [
                (row, service, descriptor)
                for row, service, descriptor in self._authenticator_candidates(db).get(
                    account_type, []
                )
                if all(row[key] == owner[key] for key in ('package', 'uid', 'signers'))
            ]
            if len(selected) != 1:
                raise PermissionError('Authenticator owner unavailable')
            row, service, descriptor = selected[0]
            if boolean(service.get('isolated'), False) or boolean(
                service.get('externalService'), False
            ):
                raise FormatError('Isolated/external authenticator services are unsupported')
            result = dict(
                descriptor,
                package=row['package'],
                uid=row['uid'],
                apk_sha256=row['sha256'],
                process=service.get('process') or row['package'],
            )
            if len(json.dumps(result).encode()) > 60000:
                raise FormatError('Authenticator resolution reply size limit')
            return result

    def list_authenticators(self, caller_uid, caller_sha256):
        """Current enabled authenticator descriptors; enumeration grants no account access."""
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            result = []
            owners = {row['type']: row for row in db.execute('SELECT * FROM authenticator_owners')}
            for kind, entries in sorted(self._authenticator_candidates(db).items()):
                owner = owners.get(kind)
                if owner is None:
                    raise FormatError('Authenticator owner unavailable')
                selected = [
                    (row, service, descriptor)
                    for row, service, descriptor in entries
                    if all(row[key] == owner[key] for key in ('package', 'uid', 'signers'))
                ]
                if len(selected) != 1:
                    raise FormatError('Authenticator owner identity changed')
                row, service, descriptor = selected[0]
                result.append(
                    dict(
                        descriptor,
                        package=row['package'],
                        uid=row['uid'],
                        apk_sha256=row['sha256'],
                        process=service.get('process') or row['package'],
                    )
                )
                if len(result) > 256:
                    raise FormatError('Authenticator enumeration count limit')
            if len(json.dumps({'ok': True, 'authenticators': result}).encode()) > 60000:
                raise FormatError('Authenticator enumeration reply size limit')
            return result

    def request_sync(self, caller_uid, caller_sha256, request):
        """Host queue admission only; dispatch must revalidate all recorded generations."""
        sync_requests.validate(request)
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            sync_requests.pending(db, boolean)
            return sync_requests.enqueue(db, caller, request, boolean)

    def pending_sync_requests(self):
        """Trusted host API, never a guest-visible account enumeration."""
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            return sync_requests.pending(db, boolean)

    def claim_sync_request(self, request_id, *, now_ms=None):
        """Trusted host admission; caller must hold worker lifetime until finish."""
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            return sync_requests.claim(db, boolean, request_id, now_ms=now_ms)

    def finish_sync_request(
        self, request_id, token, *, success, status=None, delay_until=0, now_ms=None
    ):
        """Trusted host callback after confirmed worker termination/completion."""
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            return sync_requests.finish(
                db,
                request_id,
                token,
                success,
                status=status,
                delay_until=delay_until,
                now_ms=now_ms,
            )

    def list_sync_adapters(self, caller_uid, caller_sha256, *, selected_only=False):
        """Current declarations, including competing candidates; never selects or starts one."""
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            result = []
            for row in db.execute(
                'SELECT package,uid,sha256,manifest FROM packages ORDER BY package'
            ):
                manifest = json.loads(row['manifest'])
                if not boolean(manifest.get('application_enabled'), True):
                    continue
                for descriptor in manifest.get('sync_adapters', []):
                    services = [
                        item
                        for item in manifest.get('services', [])
                        if item['name'] == descriptor['component']
                    ]
                    if len(services) != 1:
                        raise FormatError('Sync adapter service identity is ambiguous or absent')
                    service = services[0]
                    if not boolean(service.get('enabled'), True):
                        continue
                    result.append(
                        dict(
                            descriptor,
                            package=row['package'],
                            uid=row['uid'],
                            apk_sha256=row['sha256'],
                            process=service.get('process') or row['package'],
                        )
                    )
                    if len(result) > 256:
                        raise FormatError('Sync adapter enumeration count limit')
            if selected_only:
                result = sync_owners.selected(db, boolean)
            if len(json.dumps({'ok': True, 'sync_adapters': result}).encode()) > 60000:
                raise FormatError('Sync adapter enumeration reply size limit')
            return result

    def filter_dynamic_receivers(
        self, caller_uid, caller_sha256, candidates, recipient_permission=None
    ):
        """Apply current package policy to broker-owned registrations in one snapshot."""
        if recipient_permission is not None and (
            not isinstance(recipient_permission, str)
            or len(recipient_permission) > 1024
            or not NAME.fullmatch(recipient_permission)
        ):
            raise ValueError('Invalid receiver permission')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            targets, grants, allowed = {}, {}, []

            def granted(subject, permission):
                key = (subject['uid'], permission)
                if key not in grants:
                    grants[key] = (
                        permission is None
                        or self._permission_denial(db, subject, permission) is None
                    )
                return grants[key]

            for item in candidates:
                package = item['package']
                if package not in targets:
                    targets[package] = db.execute(
                        'SELECT * FROM packages WHERE package=?', (package,)
                    ).fetchone()
                target = targets[package]
                if (
                    target is None
                    or target['uid'] != item['uid']
                    or target['sha256'] != item['apk_sha256']
                ):
                    continue
                if not boolean(json.loads(target['manifest']).get('application_enabled'), True):
                    continue
                if caller['uid'] != target['uid'] and item['flags'] & 4:
                    continue
                if not granted(caller, item['permission']) or not granted(
                    target, recipient_permission
                ):
                    continue
                allowed.append(item)
            return allowed

    def authenticate_process(self, package, uid, sha256):
        """Validate a host-issued process identity without re-reading its APK."""
        with self.connect() as db:
            row = db.execute(
                'SELECT uid,sha256,manifest FROM packages WHERE package=?', (package,)
            ).fetchone()
            if row is None or row['uid'] != uid or row['sha256'] != sha256:
                raise PermissionError('Process package generation changed')
            if not boolean(json.loads(row['manifest']).get('application_enabled'), True):
                raise PermissionError('Process package disabled')

    def package_metadata(self, caller_uid, caller_sha256, package, *, stream=False):
        """Authenticated enrolled manifest facts, independent of launcher caches."""
        if not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package):
            raise ValueError('Invalid package name')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            target = db.execute('SELECT * FROM packages WHERE package=?', (package,)).fetchone()
            if target is None:
                return None
            from .runtime import ROOT, application_metadata

            facts = application_metadata(
                json.loads(target['manifest']),
                (self.objects / (target['sha256'] + '.apk')).resolve(),
                ROOT / 'data/apps' / package,
            )
            facts.update(uid=target['uid'], sha256=target['sha256'])
            facts = {key: str(value) for key, value in facts.items() if value is not None}
            if stream:
                if len(facts) > 65536 or len(json.dumps(facts).encode('utf-8')) > 8 * 1024 * 1024:
                    raise FormatError('Registered package metadata exceeds aggregate limit')
                return facts
            if (
                len(facts) > 4096
                or len(json.dumps({'ok': True, 'metadata': facts}).encode('utf-8')) > 60000
            ):
                raise FormatError('Registered package metadata exceeds broker frame limit')
            return facts

    def compare_signatures(self, caller_uid, caller_sha256, kind, first, second):
        """Compare current verified signer sets; signing rotation is not supported."""
        if kind not in ('package', 'uid'):
            raise ValueError('Invalid signature selector')
        for value in (first, second):
            if kind == 'uid':
                if type(value) is not int or not 0 <= value <= 0x7FFFFFFF:
                    raise ValueError('Invalid package UID')
            elif not isinstance(value, str) or len(value) > 1024 or not NAME.fullmatch(value):
                raise ValueError('Invalid package name')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            # kind is a validated column name; values remain bound parameters.
            rows = [
                db.execute('SELECT signers FROM packages WHERE ' + kind + '=?', (value,)).fetchone()
                for value in (first, second)
            ]
            if any(row is None for row in rows):
                return -4  # PackageManager.SIGNATURE_UNKNOWN_PACKAGE
            return 0 if rows[0]['signers'] == rows[1]['signers'] else -3

    @staticmethod
    def _permission_denial(db, subject, permission):
        if subject is None or not boolean(
            json.loads(subject['manifest']).get('application_enabled'), True
        ):
            return 'Package unavailable'
        if permission not in json.loads(subject['manifest']).get('permissions', []):
            return 'Caller did not request service permission'
        rule = db.execute('SELECT * FROM permissions WHERE name=?', (permission,)).fetchone()
        if rule is None:
            return 'Service permission has no trusted definition'
        if rule['protection'] == 2:
            owner = db.execute(
                'SELECT signers FROM packages WHERE package=?', (rule['owner'],)
            ).fetchone()
            if owner is None or owner['signers'] != subject['signers']:
                return 'Signature permission denied'
        elif rule['protection'] != 0:
            return 'Runtime or privileged permission grant unavailable'
        return None

    def check_permission(self, caller_uid, caller_sha256, permission, kind, subject):
        """Query custom install-time grants; platform/runtime grants remain unsupported."""
        if (
            not isinstance(permission, str)
            or len(permission) > 1024
            or not NAME.fullmatch(permission)
        ):
            raise ValueError('Invalid permission name')
        if kind not in ('package', 'uid'):
            raise ValueError('Invalid permission selector')
        if kind == 'uid':
            if type(subject) is not int or not 0 <= subject <= 0x7FFFFFFF:
                raise ValueError('Invalid permission UID')
        elif not isinstance(subject, str) or len(subject) > 1024 or not NAME.fullmatch(subject):
            raise ValueError('Invalid permission package')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute(
                'SELECT sha256,manifest FROM packages WHERE uid=?', (caller_uid,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            row = db.execute('SELECT * FROM packages WHERE ' + kind + '=?', (subject,)).fetchone()
            return 0 if self._permission_denial(db, row, permission) is None else -1

    def resolve_activity(self, caller_uid, target_package, component, *, caller_sha256):
        """Authorize an explicit component; a result is not a launch or a lease."""
        for value in (target_package, component):
            if not isinstance(value, str) or len(value) > 1024 or not NAME.fullmatch(value):
                raise ValueError('Invalid activity target')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            if caller is None or target is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Activity caller or target unavailable')
            caller_manifest, manifest = (
                json.loads(caller['manifest']),
                json.loads(target['manifest']),
            )
            if not boolean(caller_manifest.get('application_enabled'), True) or not boolean(
                manifest.get('application_enabled'), True
            ):
                raise PermissionError('Package disabled')
            direct = [item for item in manifest.get('activities', []) if item['name'] == component]
            aliases = [
                item for item in manifest.get('activity_aliases', []) if item['name'] == component
            ]
            if len(direct) + len(aliases) != 1:
                raise PermissionError('Activity unavailable')
            policy = (direct or aliases)[0]
            if not boolean(policy.get('enabled'), True):
                raise PermissionError('Activity disabled')
            if aliases:
                direct = [
                    item
                    for item in manifest.get('activities', [])
                    if item['name'] == policy.get('target')
                ]
                if len(direct) != 1:
                    raise PermissionError('Activity alias target unavailable')
            activity = direct[0]
            permission = policy.get('permission')
            if caller['uid'] != target['uid']:
                if not boolean(policy.get('exported'), False):
                    raise PermissionError('Activity is not exported')
                if permission:
                    denial = self._permission_denial(db, caller, permission)
                    if denial is not None:
                        raise PermissionError(denial)
            return dict(
                package=target['package'],
                uid=target['uid'],
                apk_sha256=target['sha256'],
                requested_component=component,
                component=activity['name'],
                process=activity.get('process') or target['package'],
                launch_mode=activity.get('launch_mode', 0),
            )

    def authorize_launcher(self, caller_uid, target_package, *, caller_sha256, target_sha256):
        """Policy for the exact default launcher used by run_activity.

        The trusted host must authenticate caller_uid and pin both generations
        through admission. This method does not start an activity.
        """
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            if caller is None or target is None:
                raise PermissionError('Unknown caller or target package')
            if caller['sha256'] != caller_sha256 or target['sha256'] != target_sha256:
                raise PermissionError('Launch package generation changed')
            caller_manifest, manifest = (
                json.loads(caller['manifest']),
                json.loads(target['manifest']),
            )
            if not boolean(caller_manifest.get('application_enabled'), True) or not boolean(
                manifest.get('application_enabled'), True
            ):
                raise PermissionError('Package disabled')
            launchers = manifest.get('launcher_activities', [])
            if not launchers or not launchers[0].get('target'):
                raise PermissionError('Package has no launcher')
            launcher = launchers[0]
            activities = [
                item
                for item in manifest.get('activities', [])
                if item['name'] == launcher['target']
            ]
            if len(activities) != 1:
                raise PermissionError('Launcher activity unavailable')
            activity = activities[0]
            if launcher.get('name') != launcher['target']:
                aliases = [
                    item
                    for item in manifest.get('activity_aliases', [])
                    if item['name'] == launcher['name'] and item['target'] == launcher['target']
                ]
                if len(aliases) != 1:
                    raise FormatError('Launcher alias policy is unavailable')
                activity = aliases[0]
            if not boolean(activity.get('enabled'), True):
                raise PermissionError('Launcher activity unavailable')
            if caller['uid'] != target['uid']:
                if not boolean(activity.get('exported'), False):
                    raise PermissionError('Launcher activity is not exported')
                if activity.get('permission'):
                    denial = self._permission_denial(db, caller, activity['permission'])
                    if denial is not None:
                        raise PermissionError(denial)
            return dict(
                package=target['package'],
                uid=target['uid'],
                apk_sha256=target['sha256'],
                component=launcher['target'],
            )

    def authorize_receiver(
        self,
        caller_uid,
        target_package,
        component,
        *,
        caller_sha256,
        target_sha256,
        recipient_permission=None,
    ):
        """Policy only; the broker authenticates the caller and pins both generations."""
        if recipient_permission is not None and (
            not isinstance(recipient_permission, str)
            or len(recipient_permission) > 1024
            or not NAME.fullmatch(recipient_permission)
        ):
            raise ValueError('Invalid receiver permission')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            if caller is None:
                raise PermissionError('Unknown caller package')
            if caller['sha256'] != caller_sha256:
                raise PermissionError('Broadcast package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            if target is None:
                if target_sha256 is not None:
                    raise PermissionError('Broadcast package generation changed')
                raise ReceiverFilteredError('Target package unavailable')
            if target['sha256'] != target_sha256:
                raise PermissionError('Broadcast package generation changed')
            manifest = json.loads(target['manifest'])
            if not boolean(manifest.get('application_enabled'), True):
                raise ReceiverFilteredError('Target package disabled')
            receivers = [
                item for item in manifest.get('receivers', []) if item['name'] == component
            ]
            if len(receivers) > 1:
                raise FormatError('Ambiguous receiver declaration')
            if not receivers or not boolean(receivers[0].get('enabled'), True):
                raise ReceiverFilteredError('Receiver unavailable')
            receiver = receivers[0]
            denial = self._receiver_denial(db, caller, target, receiver, recipient_permission)
            if denial is not None:
                raise ReceiverFilteredError(denial)
            return dict(
                package=target['package'],
                uid=target['uid'],
                apk_sha256=target['sha256'],
                receiver=receiver,
            )

    def _receiver_denial(self, db, caller, target, receiver, recipient_permission):
        if caller['uid'] != target['uid']:
            if not boolean(receiver.get('exported'), False):
                return 'Receiver is not exported'
            if receiver.get('permission'):
                denial = self._permission_denial(db, caller, receiver['permission'])
                if denial is not None:
                    return denial
        return (
            self._permission_denial(db, target, recipient_permission)
            if recipient_permission
            else None
        )

    def query_receivers(
        self,
        caller_uid,
        target_package,
        action,
        *,
        caller_sha256,
        recipient_permission=None,
        mime=None,
        scheme=None,
        has_data=False,
        categories=None,
    ):
        """Eligible package-scoped manifest recipients from one snapshot; not an execution grant."""
        if (
            not isinstance(target_package, str)
            or len(target_package) > 1024
            or not NAME.fullmatch(target_package)
        ):
            raise ValueError('Invalid receiver package')
        if not isinstance(action, str) or not 1 <= len(action) <= 1024:
            raise ValueError('Invalid receiver action')
        if recipient_permission is not None and (
            not isinstance(recipient_permission, str)
            or len(recipient_permission) > 1024
            or not NAME.fullmatch(recipient_permission)
        ):
            raise ValueError('Invalid receiver permission')
        query = validate_query(mime, scheme, has_data, categories)
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            if target is None:
                return []
            manifest = json.loads(target['manifest'])
            if not boolean(manifest.get('application_enabled'), True):
                return []
            receivers = manifest.get('receivers', [])
            if len(receivers) > 256 or len({item['name'] for item in receivers}) != len(receivers):
                raise FormatError('Invalid receiver declaration count or duplicates')
            candidates = []
            for receiver in receivers:
                if not boolean(receiver.get('enabled'), True) or self._receiver_denial(
                    db, caller, target, receiver, recipient_permission
                ):
                    continue
                priority = None
                for rule in receiver.get('filters', []):
                    if action not in rule.get('action', []) or not matches(rule, *query):
                        continue
                    if rule.get('unsupported_data'):
                        raise FormatError('Receiver authority/path matching is unsupported')
                    try:
                        value = int(rule.get('priority', '0'))
                    except (ValueError, TypeError) as error:
                        raise FormatError('Invalid receiver filter priority') from error
                    if not -0x80000000 <= value <= 0x7FFFFFFF:
                        raise FormatError('Receiver filter priority out of range')
                    priority = value if priority is None else max(priority, value)
                if priority is not None:
                    candidates.append((priority, receiver))
            candidates.sort(key=lambda item: (-item[0], item[1]['name']))
            return [
                dict(
                    package=target['package'],
                    uid=target['uid'],
                    apk_sha256=target['sha256'],
                    component=receiver['name'],
                    process=receiver.get('process') or target['package'],
                )
                for _, receiver in candidates
            ]

    def authorize_job(self, caller_uid, caller_sha256, component):
        """Validate an own-package JobService for future host-owned scheduling.

        UID/hash must come from an authenticated host session. This policy result
        is a generation-bound snapshot, not a durable execution capability.
        """
        if (
            not isinstance(component, str)
            or not 1 <= len(component) <= 1024
            or not NAME.fullmatch(component)
        ):
            raise ValueError('Invalid job service component')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = self._job_caller(db, caller_uid, caller_sha256)
            return self._authorize_job(db, caller, component, caller_sha256)

    def _job_caller(self, db, caller_uid, caller_sha256):
        if type(caller_uid) is not int or not 10000 <= caller_uid <= 19999:
            raise ValueError('Invalid job caller UID')
        if not isinstance(caller_sha256, str) or not re.fullmatch(r'[0-9a-f]{64}', caller_sha256):
            raise ValueError('Invalid job caller generation')
        caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
        if caller is None or caller['sha256'] != caller_sha256:
            raise PermissionError('Job caller generation unavailable')
        if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
            raise PermissionError('Job caller disabled')
        return caller

    def _authorize_job(self, db, caller, component, caller_sha256):
        if (
            not isinstance(component, str)
            or not 1 <= len(component) <= 1024
            or not NAME.fullmatch(component)
        ):
            raise ValueError('Invalid job service component')
        result = self._authorize_service(db, caller, caller, component, caller_sha256)
        if result['service'].get('permission') != 'android.permission.BIND_JOB_SERVICE':
            raise PermissionError('Job service requires BIND_JOB_SERVICE')
        return result

    def store_persisted_job(self, caller_uid, caller_sha256, definition):
        """Host-only storage, not runnable scheduling admission."""
        return job_store.put(self, caller_uid, caller_sha256, definition)

    def store_shared_job(self, caller_uid, caller_sha256, definition):
        """Host-owned jobs; nonpersisted definitions live only for this kernel boot."""
        return job_store.put(self, caller_uid, caller_sha256, definition, allow_nonpersisted=True)

    def list_persisted_jobs(self, caller_uid, caller_sha256):
        return job_store.listing(self, caller_uid, caller_sha256)

    def cancel_persisted_job(self, caller_uid, caller_sha256, job_id, *, expected_revision=None):
        return job_store.cancel(self, caller_uid, caller_sha256, job_id, expected_revision)

    def cancel_all_shared_jobs(self, caller_uid, caller_sha256):
        return job_store.cancel_all(self, caller_uid, caller_sha256)

    def claim_persisted_job(self, owner_uid, apk_sha256, job_id, expected_revision):
        return job_store.claim(self, owner_uid, apk_sha256, job_id, expected_revision)

    def due_persisted_jobs(self, *, limit=100):
        return job_store.due(self, limit=limit)

    def claim_due_persisted_job(self, owner_uid, apk_sha256, job_id, expected_revision):
        """Host-only atomic UTC eligibility check and reservation; does not launch."""
        return job_store.claim(
            self, owner_uid, apk_sha256, job_id, expected_revision, require_due=True
        )

    def job_claim_is_current(self, owner_uid, job_id, token):
        return job_store.claim_is_current(self, owner_uid, job_id, token)

    def release_persisted_job_claim(self, owner_uid, job_id, token):
        return job_store.release_claim(self, owner_uid, job_id, token)

    def complete_persisted_job_claim(self, owner_uid, job_id, token, *, retry=False):
        return job_store.complete_claim(self, owner_uid, job_id, token, retry=retry)

    def resolve_provider(self, caller_uid, caller_sha256, authority, access):
        """Authorize a whole-provider read/write lookup, not an open endpoint or URI grant."""
        if (
            not isinstance(authority, str)
            or len(authority) > 1024
            or not re.fullmatch(r'[A-Za-z0-9_.-]+', authority)
        ):
            raise ValueError('Invalid provider authority')
        if access not in ('read', 'write'):
            raise ValueError('Provider access must be read or write')
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            matches = []
            for row in db.execute('SELECT * FROM packages'):
                manifest = json.loads(row['manifest'])
                if not boolean(manifest.get('application_enabled'), True):
                    continue
                for provider in manifest.get('providers', []):
                    if authority in (provider.get('authorities') or '').split(';') and boolean(
                        provider.get('enabled'), True
                    ):
                        matches.append((row, manifest, provider))
            if len(matches) != 1:
                raise PermissionError('Provider authority unavailable or ambiguous')
            target, manifest, provider = matches[0]
            if target['uid'] != caller['uid']:
                if provider.get('access_metadata_version') != 1:
                    raise PermissionError('Provider access metadata requires package re-enrollment')
                sdk = manifest.get('target_sdk') or manifest.get('min_sdk') or '1'
                if not isinstance(sdk, str) or not sdk.isdigit():
                    raise FormatError('Unresolved provider target SDK')
                if not boolean(provider.get('exported'), int(sdk) <= 16):
                    raise PermissionError('Provider is not exported')
                if provider.get('has_path_permissions'):
                    raise PermissionError(
                        'Path-scoped provider authorization requires URI-aware access'
                    )
                permission = provider.get(
                    'readPermission' if access == 'read' else 'writePermission'
                )
                if permission:
                    denial = self._permission_denial(db, caller, permission)
                    if denial is not None:
                        raise PermissionError(denial)
            return dict(
                package=target['package'],
                uid=target['uid'],
                apk_sha256=target['sha256'],
                authority=authority,
                access=access,
                provider=provider,
            )

    def authorize_service(self, caller_uid, target_package, component, *, caller_sha256=None):
        """Policy only: caller_uid must come from an authenticated broker session."""
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            return self._authorize_service(db, caller, target, component, caller_sha256)

    def resolve_service_action(
        self,
        caller_uid,
        target_package,
        action,
        *,
        caller_sha256,
        mime=None,
        scheme=None,
        has_data=False,
        categories=None,
    ):
        """Resolve a package-scoped intent against one registry snapshot.

        Tied best matches fail explicitly rather than choosing an arbitrary component.
        """
        if (
            not isinstance(target_package, str)
            or len(target_package) > 1024
            or not NAME.fullmatch(target_package)
        ):
            raise ValueError('Invalid service package')
        if not isinstance(action, str) or not 1 <= len(action) <= 1024:
            raise ValueError('Invalid service action')
        query = validate_query(mime, scheme, has_data, categories)
        with self.connect() as db:
            db.execute('BEGIN')
            caller = db.execute('SELECT * FROM packages WHERE uid=?', (caller_uid,)).fetchone()
            target = db.execute(
                'SELECT * FROM packages WHERE package=?', (target_package,)
            ).fetchone()
            if caller is None or caller['sha256'] != caller_sha256:
                raise PermissionError('Caller package generation changed')
            if not boolean(json.loads(caller['manifest']).get('application_enabled'), True):
                raise PermissionError('Caller package disabled')
            if target is None:
                raise ServiceUnavailableError('Unknown target package')
            if not boolean(json.loads(target['manifest']).get('application_enabled'), True):
                raise ServiceUnavailableError('Target package disabled')
            candidates = {}
            for service in json.loads(target['manifest']).get('services', []):
                if not boolean(service.get('enabled'), True):
                    continue
                for rule in service.get('filters', []):
                    if action not in rule.get('action', []) or not matches(rule, *query):
                        continue
                    if rule.get('unsupported_data'):
                        raise FormatError('Service authority/path matching is unsupported')
                    try:
                        priority = int(rule.get('priority', '0'))
                    except (ValueError, TypeError) as error:
                        raise FormatError('Invalid service filter priority') from error
                    if not -0x80000000 <= priority <= 0x7FFFFFFF:
                        raise FormatError('Service filter priority out of range')
                    name = service['name']
                    candidates[name] = max(candidates.get(name, -0x80000000), priority)
            if not candidates:
                raise ServiceUnavailableError('No matching service')
            best = [
                name
                for name, priority in candidates.items()
                if priority == max(candidates.values())
            ]
            if len(best) != 1:
                raise FormatError('Ambiguous service action resolution')
            return self._authorize_service(db, caller, target, best[0], caller_sha256)

    def _authorize_service(self, db, caller, target, component, caller_sha256):
        if caller is None:
            raise PermissionError('Unknown caller package')
        if caller_sha256 is not None and caller['sha256'] != caller_sha256:
            raise PermissionError('Caller package generation changed')
        caller_manifest = json.loads(caller['manifest'])
        if not boolean(caller_manifest.get('application_enabled'), True):
            raise PermissionError('Caller package disabled')
        if target is None:
            raise ServiceUnavailableError('Unknown target package')
        target_manifest = json.loads(target['manifest'])
        if not boolean(target_manifest.get('application_enabled'), True):
            raise ServiceUnavailableError('Target package disabled')
        services = [
            item for item in target_manifest.get('services', []) if item['name'] == component
        ]
        if len(services) != 1 or not boolean(services[0].get('enabled'), True):
            raise ServiceUnavailableError('Service unavailable')
        service = services[0]
        if boolean(service.get('isolated'), False) or boolean(
            service.get('externalService'), False
        ):
            raise FormatError('Isolated/external service processes are unsupported')
        if caller['uid'] != target['uid']:
            if not boolean(service.get('exported'), False):
                raise PermissionError('Service is not exported')
            permission = service.get('permission')
            if permission:
                denial = self._permission_denial(db, caller, permission)
                if denial is not None:
                    raise PermissionError(denial)
        return {
            'package': target['package'],
            'uid': target['uid'],
            'apk_sha256': target['sha256'],
            'service': service,
        }
