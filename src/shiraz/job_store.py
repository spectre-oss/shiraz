# SPDX-License-Identifier: GPL-3.0-only
"""Host-only persisted definitions with atomic due-job reservations."""

import json
import uuid

from . import boot_identity, job_clock
from .job_definition import LONG_MAX, integer, validate

PER_PACKAGE_LIMIT = 100
GLOBAL_LIMIT = 4096
BOOT_PERMISSION = 'android.permission.RECEIVE_BOOT_COMPLETED'
MAX_BACKOFF_MS = 18000000


def initial_delay(definition):
    if definition['interval_ms']:
        return definition['interval_ms'] - definition['flex_ms']
    deadline = definition['deadline_ms']
    return (
        min(definition['minimum_latency_ms'], deadline)
        if deadline is not None
        else definition['minimum_latency_ms']
    )


def retry_delay(definition, attempts):
    integer(attempts, 0, (1 << 31) - 1)
    multiplier = attempts + 1 if definition['backoff_policy'] == 0 else 1 << min(attempts, 31)
    return min(MAX_BACKOFF_MS, definition['initial_backoff_ms'] * multiplier)


def migrate(db):
    """Called inside registry initialization's transaction, including schema stamp."""
    db.execute(
        'CREATE TABLE IF NOT EXISTS job_recovery (token TEXT PRIMARY KEY, device INTEGER NOT NULL, inode INTEGER NOT NULL)'
    )
    columns = {row['name'] for row in db.execute('PRAGMA table_info(persisted_jobs)')}
    if 'next_run_at_ms' not in columns:
        db.execute('ALTER TABLE persisted_jobs ADD COLUMN next_run_at_ms INTEGER')
    if 'attempts' not in columns:
        db.execute('ALTER TABLE persisted_jobs ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0')
    for name in ('next_run_elapsed_ms', 'deadline_elapsed_ms'):
        if name not in columns:
            db.execute('ALTER TABLE persisted_jobs ADD COLUMN ' + name + ' INTEGER')
    for row in db.execute('SELECT * FROM persisted_jobs WHERE next_run_at_ms IS NULL').fetchall():
        definition = validate(json.loads(row['definition']))
        timestamp = integer(row['scheduled_at_ms'], 0, LONG_MAX)
        db.execute(
            'UPDATE persisted_jobs SET next_run_at_ms=? WHERE owner_uid=? AND job_id=?',
            (min(LONG_MAX, timestamp + initial_delay(definition)), row['owner_uid'], row['job_id']),
        )


def reconcile_boot(db):
    """Only a proven kernel reboot invalidates running claims and transient jobs.

    An unstamped legacy registry retains its claims on first adoption. A host
    process crash alone must never release a potentially live worker's claim.
    """
    identity = boot_identity.current()
    db.execute(
        'CREATE TABLE IF NOT EXISTS job_boot_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), identity TEXT NOT NULL)'
    )
    previous = db.execute('SELECT identity FROM job_boot_state WHERE singleton=1').fetchone()
    if previous is not None:
        parsed = uuid.UUID(previous['identity'])
        if str(parsed) != previous['identity'] or parsed.int == 0:
            raise ValueError('Invalid recorded job boot identity')
    changed = previous is not None and previous['identity'] != identity
    if changed:
        rows = db.execute('SELECT owner_uid,job_id,definition FROM persisted_jobs').fetchall()
        for row in rows:
            if not validate(json.loads(row['definition']))['persisted']:
                db.execute(
                    'DELETE FROM persisted_jobs WHERE owner_uid=? AND job_id=?',
                    (row['owner_uid'], row['job_id']),
                )
        # A worker from a different kernel boot cannot still be executing.
        db.execute('DELETE FROM persisted_job_claims')
        db.execute('DELETE FROM job_recovery')
    legacy = db.execute('PRAGMA user_version').fetchone()[0] < 13
    if (
        previous is None
        and not legacy
        and db.execute('SELECT 1 FROM persisted_jobs LIMIT 1').fetchone()
    ):
        raise ValueError('Elapsed job timing has no recorded boot identity')
    rows = db.execute('SELECT * FROM persisted_jobs').fetchall()
    restore = [row for row in rows if changed or (legacy and row['next_run_elapsed_ms'] is None)]
    if restore:
        clock = job_clock.sample()
        if clock.boot_id != identity:
            raise RuntimeError('Job clock boot identity mismatch')
        for row in restore:
            definition = validate(json.loads(row['definition']))
            due = clock.restore_target(integer(row['next_run_at_ms'], 0, LONG_MAX))
            deadline = None
            if row['attempts'] == 0 and definition['deadline_ms'] is not None:
                deadline = clock.restore_target(
                    min(
                        LONG_MAX,
                        integer(row['scheduled_at_ms'], 0, LONG_MAX) + definition['deadline_ms'],
                    )
                )
            db.execute(
                'UPDATE persisted_jobs SET next_run_elapsed_ms=?,deadline_elapsed_ms=? WHERE owner_uid=? AND job_id=?',
                (due, deadline, row['owner_uid'], row['job_id']),
            )
    for row in db.execute('SELECT * FROM persisted_jobs'):
        record(row)  # Corrupt current-schema timing must not be silently repaired.
    db.execute('INSERT OR REPLACE INTO job_boot_state VALUES(1,?)', (identity,))


def token_value(value):
    if (
        type(value) is not str
        or len(value) != 32
        or any(c not in '0123456789abcdef' for c in value)
    ):
        raise ValueError('Invalid job revision or claim token')
    return value


def require_boot_permission(caller):
    if BOOT_PERMISSION not in json.loads(caller['manifest']).get('permissions', []):
        raise PermissionError('Persisted jobs require RECEIVE_BOOT_COMPLETED')


def record(row):
    definition = validate(json.loads(row['definition']))
    attempts = integer(row['attempts'], 0, (1 << 31) - 1)
    deadline = row['deadline_elapsed_ms']
    expected_deadline = attempts == 0 and definition['deadline_ms'] is not None
    if (deadline is not None) != expected_deadline:
        raise ValueError('Invalid persisted job deadline state')
    return dict(
        id=row['job_id'],
        apk_sha256=row['apk_sha256'],
        revision=row['revision'],
        scheduled_at_ms=integer(row['scheduled_at_ms'], 0, LONG_MAX),
        next_run_at_ms=integer(row['next_run_at_ms'], 0, LONG_MAX),
        next_run_elapsed_ms=integer(row['next_run_elapsed_ms'], 0, LONG_MAX),
        deadline_elapsed_ms=None if deadline is None else integer(deadline, 0, LONG_MAX),
        attempts=attempts,
        definition=definition,
    )


def put(registry, uid, sha256, definition, *, allow_nonpersisted=False):
    definition = validate(definition)
    if not definition['persisted'] and not allow_nonpersisted:
        raise ValueError('Persistent store requires a persisted job')
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        return put_in_transaction(registry, db, uid, sha256, definition)


def put_in_transaction(registry, db, uid, sha256, definition):
    """Write a validated definition inside the caller's IMMEDIATE transaction."""
    caller = registry._job_caller(db, uid, sha256)
    if definition['persisted']:
        require_boot_permission(caller)
    registry._authorize_job(db, caller, definition['component'], sha256)
    existing = db.execute(
        'SELECT 1 FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, definition['id'])
    ).fetchone()
    if existing is None:
        if (
            db.execute('SELECT count(*) FROM persisted_jobs WHERE owner_uid=?', (uid,)).fetchone()[
                0
            ]
            >= PER_PACKAGE_LIMIT
        ):
            raise ValueError('Persisted job package limit')
        if db.execute('SELECT count(*) FROM persisted_jobs').fetchone()[0] >= GLOBAL_LIMIT:
            raise ValueError('Persisted job global limit')
    revision = uuid.uuid4().hex
    clock = job_clock.sample()
    scheduled_at = clock.wall_ms
    db.execute(
        '''INSERT OR REPLACE INTO persisted_jobs
               (owner_uid,job_id,apk_sha256,revision,scheduled_at_ms,definition,next_run_at_ms,attempts,next_run_elapsed_ms,deadline_elapsed_ms)
               VALUES(?,?,?,?,?,?,?,?,?,?)''',
        (
            uid,
            definition['id'],
            sha256,
            revision,
            scheduled_at,
            json.dumps(definition, sort_keys=True, separators=(',', ':'), ensure_ascii=False),
            min(LONG_MAX, scheduled_at + initial_delay(definition)),
            0,
            min(LONG_MAX, clock.elapsed_ms + initial_delay(definition)),
            None
            if definition['deadline_ms'] is None
            else min(LONG_MAX, clock.elapsed_ms + definition['deadline_ms']),
        ),
    )
    return record(
        db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, definition['id'])
        ).fetchone()
    )


def listing(registry, uid, sha256):
    with registry.connect() as db:
        db.execute('BEGIN')
        caller = registry._job_caller(db, uid, sha256)
        rows = db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? ORDER BY job_id', (uid,)
        ).fetchall()
        for row in rows:
            if row['apk_sha256'] != sha256:
                raise PermissionError('Persisted job generation mismatch')
            definition = validate(json.loads(row['definition']))
            if definition['persisted']:
                require_boot_permission(caller)
            registry._authorize_job(db, caller, definition['component'], sha256)
        return [record(row) for row in rows]


def cancel(registry, uid, sha256, job_id, expected_revision=None):
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    if expected_revision is not None:
        token_value(expected_revision)
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        registry._job_caller(db, uid, sha256)
        query = 'DELETE FROM persisted_jobs WHERE owner_uid=? AND job_id=? AND apk_sha256=?'
        values = [uid, job_id, sha256]
        if expected_revision is not None:
            query += ' AND revision=?'
            values.append(expected_revision)
        return db.execute(query, values).rowcount != 0


def cancel_all(registry, uid, sha256):
    """Atomically remove this package's jobs and snapshot preexisting claims.

    Tokens stay host-private and reserved until their workers terminate. A job
    scheduled after this transaction belongs to a later operation and survives.
    """
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        registry._job_caller(db, uid, sha256)
        claims = {
            row['token']
            for row in db.execute(
                'SELECT token FROM persisted_job_claims WHERE owner_uid=?', (uid,)
            )
        }
        removed = db.execute('DELETE FROM persisted_jobs WHERE owner_uid=?', (uid,)).rowcount != 0
        return removed, claims


def due(registry, *, limit=100):
    """Host-only elapsed-time discovery snapshots, not execution capabilities.

    The dispatcher must pin the package generation and claim again before launch.
    Outstanding reservations exclude work even after cancellation/replacement.
    Elapsed eligibility is shared across hosts within the recorded kernel boot.
    """
    integer(limit, 1, GLOBAL_LIMIT)
    with registry.connect() as db:
        db.execute('BEGIN')
        rows = db.execute(
            '''SELECT j.*,p.package FROM persisted_jobs j
            JOIN packages p ON p.uid=j.owner_uid AND p.sha256=j.apk_sha256
            WHERE j.next_run_elapsed_ms<=? AND NOT EXISTS(
                SELECT 1 FROM persisted_job_claims c WHERE c.owner_uid=j.owner_uid AND c.job_id=j.job_id)
            ORDER BY j.next_run_elapsed_ms,j.owner_uid,j.job_id LIMIT ?''',
            (job_clock.elapsed_ms(), GLOBAL_LIMIT),
        ).fetchall()
        result = []
        for row in rows:
            item = record(row)
            try:
                caller = registry._job_caller(db, row['owner_uid'], row['apk_sha256'])
                if item['definition']['persisted']:
                    require_boot_permission(caller)
                registry._authorize_job(
                    db, caller, item['definition']['component'], row['apk_sha256']
                )
            except PermissionError:
                continue
            result.append(dict(item, owner_uid=row['owner_uid'], package=row['package']))
            if len(result) == limit:
                break
        return result


def claim(registry, uid, sha256, job_id, expected_revision, *, require_due=False):
    """Reserve one revision, optionally checking elapsed eligibility atomically.

    Claims do not expire or clear on registry reopen. Cancellation, replacement
    and package updates deliberately retain them until the old worker is stopped.
    A future dispatcher must coordinate process lifetime before release/recovery.
    """
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(expected_revision)
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        caller = registry._job_caller(db, uid, sha256)
        row = db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, job_id)
        ).fetchone()
        if row is None or row['revision'] != expected_revision or row['apk_sha256'] != sha256:
            return None
        result = record(row)
        if result['definition']['persisted']:
            require_boot_permission(caller)
        registry._authorize_job(db, caller, result['definition']['component'], sha256)
        if require_due and result['next_run_elapsed_ms'] > job_clock.elapsed_ms():
            return None
        if db.execute(
            'SELECT 1 FROM persisted_job_claims WHERE owner_uid=? AND job_id=?', (uid, job_id)
        ).fetchone():
            return None
        if db.execute('SELECT count(*) FROM persisted_job_claims').fetchone()[0] >= GLOBAL_LIMIT:
            raise ValueError('Persisted job claim limit')
        token = uuid.uuid4().hex
        db.execute(
            'INSERT INTO persisted_job_claims VALUES(?,?,?,?,?)',
            (uid, job_id, sha256, expected_revision, token),
        )
        return dict(result, owner_uid=uid, token=token)


def claim_is_current(registry, uid, job_id, token):
    """Host observation only; false never authorizes releasing a reservation."""
    integer(uid, 10000, 19999)
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(token)
    with registry.connect() as db:
        return (
            db.execute(
                '''SELECT 1 FROM persisted_job_claims c JOIN persisted_jobs j
            ON j.owner_uid=c.owner_uid AND j.job_id=c.job_id
            AND j.apk_sha256=c.apk_sha256 AND j.revision=c.revision
            WHERE c.owner_uid=? AND c.job_id=? AND c.token=?''',
                (uid, job_id, token),
            ).fetchone()
            is not None
        )


def release_claim(registry, uid, job_id, token):
    """Host-only: caller must have stopped/joined its worker, or never started it."""
    integer(uid, 10000, 19999)
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(token)
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        removed = (
            db.execute(
                'DELETE FROM persisted_job_claims WHERE owner_uid=? AND job_id=? AND token=?',
                (uid, job_id, token),
            ).rowcount
            != 0
        )
        if removed:
            db.execute('DELETE FROM job_recovery WHERE token=?', (token,))
        return removed


def complete_claim(registry, uid, job_id, token, *, retry=False):
    """Host-only terminal transition after worker teardown, never guest caller input.

    Retries and fixed-delay periodic runs rotate revision and save their next UTC
    and elapsed time atomically with claim removal. Full Android batching and
    wake-from-suspend delivery are not implemented.
    """
    if type(retry) is not bool:
        raise ValueError('Invalid job retry flag')
    integer(uid, 10000, 19999)
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(token)
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        owned = db.execute(
            'SELECT * FROM persisted_job_claims WHERE owner_uid=? AND job_id=? AND token=?',
            (uid, job_id, token),
        ).fetchone()
        if owned is None:
            return False
        row = db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, job_id)
        ).fetchone()
        current = (
            row is not None
            and row['revision'] == owned['revision']
            and row['apk_sha256'] == owned['apk_sha256']
        )
        if current:
            caller = registry._job_caller(db, uid, owned['apk_sha256'])
            definition = record(row)['definition']
            if definition['persisted']:
                require_boot_permission(caller)
            registry._authorize_job(db, caller, definition['component'], owned['apk_sha256'])
            work = db.execute(
                '''SELECT count(*) AS remaining,
                count(delivery_token) AS delivered FROM job_work_items
                WHERE owner_uid=? AND job_id=?''',
                (uid, job_id),
            ).fetchone()
            # A worker may finish concurrently with enqueue, or fail to ack
            # delivered work. Neither case may silently discard accepted items.
            work_retry = retry or work['delivered'] > 0
            if work_retry or definition['interval_ms'] or work['remaining']:
                delay = (
                    retry_delay(definition, row['attempts'])
                    if work_retry
                    else definition['interval_ms']
                )
                attempts = min((1 << 31) - 1, row['attempts'] + 1) if work_retry else 0
                clock = job_clock.sample()
                db.execute(
                    'UPDATE persisted_jobs SET revision=?,next_run_at_ms=?,next_run_elapsed_ms=?,deadline_elapsed_ms=NULL,attempts=? WHERE owner_uid=? AND job_id=?',
                    (
                        uuid.uuid4().hex,
                        min(LONG_MAX, clock.wall_ms + delay),
                        min(LONG_MAX, clock.elapsed_ms + delay),
                        attempts,
                        uid,
                        job_id,
                    ),
                )
            else:
                db.execute(
                    'DELETE FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, job_id)
                )
        db.execute(
            'DELETE FROM persisted_job_claims WHERE owner_uid=? AND job_id=? AND token=?',
            (uid, job_id, token),
        )
        db.execute('DELETE FROM job_recovery WHERE token=?', (token,))
        return current


def reconcile(registry, db, caller):
    """Carry compatible definitions across an enrollment update atomically.

    Every changed generation gets fresh revisions, invalidating old completion
    tokens. Removed/disabled services or lost boot permission remove their jobs.
    """
    rows = db.execute('SELECT * FROM persisted_jobs WHERE owner_uid=?', (caller['uid'],)).fetchall()
    for row in rows:
        definition = validate(json.loads(row['definition']))
        try:
            if definition['persisted']:
                require_boot_permission(caller)
            registry._authorize_job(db, caller, definition['component'], caller['sha256'])
        except (PermissionError, ValueError):
            db.execute(
                'DELETE FROM persisted_jobs WHERE owner_uid=? AND job_id=?',
                (caller['uid'], row['job_id']),
            )
            continue
        if row['apk_sha256'] != caller['sha256']:
            db.execute(
                'UPDATE persisted_jobs SET apk_sha256=?,revision=? WHERE owner_uid=? AND job_id=?',
                (caller['sha256'], uuid.uuid4().hex, caller['uid'], row['job_id']),
            )
