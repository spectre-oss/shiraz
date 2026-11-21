# SPDX-License-Identifier: GPL-3.0-only
"""Host-only PendingIntent authority. Payloads are opaque, bounded snapshots.

No guest UID argument is authentication: callers must come from the broker's
verified channel identity. This store does not dispatch or unmarshal intents.
"""

import hashlib
import json
import re
import secrets

ONE_SHOT = 0x40000000
NO_CREATE = 0x20000000
CANCEL_CURRENT = 0x10000000
UPDATE_CURRENT = 0x08000000
IMMUTABLE = 0x04000000
MUTABLE = 0x02000000
IDENTITY_FLAGS = ONE_SHOT | IMMUTABLE | MUTABLE
ALL_FLAGS = IDENTITY_FLAGS | NO_CREATE | CANCEL_CURRENT | UPDATE_CURRENT
MAX_PAYLOAD = 32768


class CanceledTokenError(LookupError):
    pass


class PendingIntentStore:
    def __init__(self, registry):
        self.registry = registry

    @staticmethod
    def _caller(db, uid, sha):
        row = db.execute('SELECT * FROM packages WHERE uid=?', (uid,)).fetchone()
        if row is None or row['sha256'] != sha:
            raise PermissionError('PendingIntent caller generation changed')
        if json.loads(row['manifest']).get('application_enabled') in ('false', '0', False):
            raise PermissionError('PendingIntent caller disabled')
        return row

    @staticmethod
    def _token(value):
        if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value):
            raise ValueError('Invalid PendingIntent capability')

    @staticmethod
    def _facts(row):
        return dict(
            token=row['token'],
            package=row['package'],
            uid=row['owner_uid'],
            sha256=row['owner_sha256'],
            kind=row['kind'],
            flags=row['flags'],
        )

    def obtain(self, uid, sha, *, kind, request_code, identity, payload, flags):
        if type(kind) is not int or kind not in range(4):
            raise ValueError('Invalid PendingIntent kind')
        if type(request_code) is not int or not -(2**31) <= request_code < 2**31:
            raise ValueError('Invalid PendingIntent request code')
        if type(flags) is not int or flags & ~ALL_FLAGS or flags & IMMUTABLE and flags & MUTABLE:
            raise ValueError('Invalid PendingIntent flags')
        if not isinstance(identity, bytes) or not 1 <= len(identity) <= 8192:
            raise ValueError('Invalid PendingIntent filter identity')
        if not isinstance(payload, bytes) or not 1 <= len(payload) <= MAX_PAYLOAD:
            raise ValueError('Invalid PendingIntent snapshot')
        key = hashlib.sha256(identity).hexdigest()
        with self.registry.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            owner = self._caller(db, uid, sha)
            # Stale APK generations cannot retain authority or exhaust capacity.
            db.execute('''DELETE FROM pending_intent_tokens WHERE NOT EXISTS
                (SELECT 1 FROM packages p WHERE p.uid=owner_uid AND p.sha256=owner_sha256)''')
            args = (uid, kind, request_code, flags & IDENTITY_FLAGS, key)
            old = db.execute(
                '''SELECT * FROM pending_intent_tokens WHERE owner_uid=?
                AND kind=? AND request_code=? AND flags=? AND filter_hash=?''',
                args,
            ).fetchone()
            if old is not None and flags & CANCEL_CURRENT:
                db.execute('DELETE FROM pending_intent_tokens WHERE token=?', (old['token'],))
                old = None
            if old is not None:
                if flags & UPDATE_CURRENT:
                    db.execute(
                        'UPDATE pending_intent_tokens SET payload=? WHERE token=?',
                        (payload, old['token']),
                    )
                return self._facts(old)
            if flags & NO_CREATE:
                return None
            count, owned = db.execute(
                '''SELECT count(*), coalesce(sum(owner_uid=?),0)
                FROM pending_intent_tokens''',
                (uid,),
            ).fetchone()
            if count >= 2048 or owned >= 256:
                raise ValueError('PendingIntent capability limit')
            token = secrets.token_hex(32)
            db.execute(
                '''INSERT INTO pending_intent_tokens
                (token,owner_uid,owner_sha256,package,kind,request_code,flags,filter_hash,payload)
                VALUES(?,?,?,?,?,?,?,?,?)''',
                (
                    token,
                    uid,
                    sha,
                    owner['package'],
                    kind,
                    request_code,
                    flags & IDENTITY_FLAGS,
                    key,
                    payload,
                ),
            )
            return self._facts(
                db.execute('SELECT * FROM pending_intent_tokens WHERE token=?', (token,)).fetchone()
            )

    def _resolve(self, db, uid, sha, token):
        self._token(token)
        self._caller(db, uid, sha)
        row = db.execute('SELECT * FROM pending_intent_tokens WHERE token=?', (token,)).fetchone()
        if row is None:
            raise CanceledTokenError('PendingIntent is canceled or unknown')
        owner = db.execute(
            'SELECT sha256,manifest FROM packages WHERE uid=?', (row['owner_uid'],)
        ).fetchone()
        if (
            owner is None
            or owner['sha256'] != row['owner_sha256']
            or json.loads(owner['manifest']).get('application_enabled') in ('false', '0', False)
        ):
            raise CanceledTokenError('PendingIntent creator unavailable')
        return row

    def inspect(self, uid, sha, token):
        with self.registry.connect() as db:
            db.execute('BEGIN')
            return self._facts(self._resolve(db, uid, sha, token))

    def cancel(self, uid, sha, token):
        with self.registry.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = self._resolve(db, uid, sha, token)
            if row['owner_uid'] != uid:
                raise PermissionError('Only the PendingIntent creator may cancel it')
            db.execute('DELETE FROM pending_intent_tokens WHERE token=?', (token,))

    def claim_send(self, uid, sha, token):
        """Host dispatcher snapshot; consumes one-shot before any external delivery.

        A delivery failure must not resurrect a consumed capability. Returned
        payloads are for the host dispatcher, not package-metadata callers.
        """
        with self.registry.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = self._resolve(db, uid, sha, token)
            if row['flags'] & ONE_SHOT:
                db.execute('DELETE FROM pending_intent_tokens WHERE token=?', (token,))
            return dict(self._facts(row), payload=bytes(row['payload']))
