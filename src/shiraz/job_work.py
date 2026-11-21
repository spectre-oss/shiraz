# SPDX-License-Identifier: GPL-3.0-only
"""Host-owned job work; delivery requires the current private worker claim.

Payloads are bounded opaque Intent parcels. This module does not grant URI access
or accept guest-supplied worker tokens. Broker wire integration is separate.
"""

from .job_definition import integer, validate
from .job_store import put_in_transaction, record, token_value

MAX_PAYLOAD = 32768
PER_JOB_LIMIT = 128
PER_PACKAGE_LIMIT = 1024
GLOBAL_LIMIT = 4096


def migrate(db):
    db.execute('CREATE TABLE IF NOT EXISTS job_work_drains(token TEXT PRIMARY KEY)')
    db.execute('''CREATE TABLE IF NOT EXISTS job_work_items(
        work_id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uid INTEGER NOT NULL,
        job_id INTEGER NOT NULL, payload BLOB NOT NULL,
        deliveries INTEGER NOT NULL DEFAULT 0, delivery_token TEXT)''')
    db.execute(
        'CREATE INDEX IF NOT EXISTS job_work_owner ON job_work_items(owner_uid,job_id,work_id)'
    )
    # BEFORE INSERT also handles SQLite REPLACE without recursive triggers.
    db.execute('''CREATE TRIGGER IF NOT EXISTS job_work_replace BEFORE INSERT ON persisted_jobs
        BEGIN DELETE FROM job_work_items WHERE owner_uid=NEW.owner_uid AND job_id=NEW.job_id; END''')
    db.execute('''CREATE TRIGGER IF NOT EXISTS job_work_delete AFTER DELETE ON persisted_jobs
        BEGIN DELETE FROM job_work_items WHERE owner_uid=OLD.owner_uid AND job_id=OLD.job_id; END''')
    db.execute('''CREATE TRIGGER IF NOT EXISTS job_work_generation AFTER UPDATE OF apk_sha256 ON persisted_jobs
        WHEN NEW.apk_sha256 != OLD.apk_sha256
        BEGIN DELETE FROM job_work_items WHERE owner_uid=OLD.owner_uid AND job_id=OLD.job_id; END''')
    db.execute('''CREATE TRIGGER IF NOT EXISTS job_work_release AFTER DELETE ON persisted_job_claims
        BEGIN UPDATE job_work_items SET delivery_token=NULL
        WHERE owner_uid=OLD.owner_uid AND job_id=OLD.job_id AND delivery_token=OLD.token; END''')
    db.execute('''CREATE TRIGGER IF NOT EXISTS job_work_drain_release AFTER DELETE ON persisted_job_claims
        BEGIN DELETE FROM job_work_drains WHERE token=OLD.token; END''')


def append(registry, uid, sha256, job_id, revision, payload):
    """Append to an existing exact job revision, without replacing a live job."""
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(revision)
    if type(payload) is not bytes or not 1 <= len(payload) <= MAX_PAYLOAD:
        raise ValueError('Invalid job work payload size')
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        caller = registry._job_caller(db, uid, sha256)
        row = db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, job_id)
        ).fetchone()
        if row is None or row['apk_sha256'] != sha256 or row['revision'] != revision:
            raise PermissionError('Job work generation changed')
        definition = record(row)['definition']
        registry._authorize_job(db, caller, definition['component'], sha256)
        if definition['persisted'] or definition['interval_ms']:
            raise ValueError('Intent work requires a nonpersisted one-off job')
        return _insert(db, uid, job_id, payload)


def _insert(db, uid, job_id, payload):
    for query, args, limit in (
        (
            'SELECT count(*) FROM job_work_items WHERE owner_uid=? AND job_id=?',
            (uid, job_id),
            PER_JOB_LIMIT,
        ),
        ('SELECT count(*) FROM job_work_items WHERE owner_uid=?', (uid,), PER_PACKAGE_LIMIT),
        ('SELECT count(*) FROM job_work_items', (), GLOBAL_LIMIT),
    ):
        if db.execute(query, args).fetchone()[0] >= limit:
            raise ValueError('Job work queue capacity exceeded')
    cursor = db.execute(
        'INSERT INTO job_work_items(owner_uid,job_id,payload) VALUES(?,?,?)', (uid, job_id, payload)
    )
    return integer(cursor.lastrowid, 1, (1 << 31) - 1)


def enqueue(registry, uid, sha256, definition, payload):
    """Commit the job definition and its item together, retaining existing work.

    A changed definition rotates the revision; the dispatcher must stop the old
    worker before releasing its claim. Enqueueing an identical job preserves its
    running claim and timing, making the new item available to its next dequeue.
    """
    definition = validate(definition)
    if definition['persisted'] or definition['interval_ms']:
        raise ValueError('Intent work requires a nonpersisted one-off job')
    if type(payload) is not bytes or not 1 <= len(payload) <= MAX_PAYLOAD:
        raise ValueError('Invalid job work payload size')
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        caller = registry._job_caller(db, uid, sha256)
        registry._authorize_job(db, caller, definition['component'], sha256)
        row = db.execute(
            'SELECT * FROM persisted_jobs WHERE owner_uid=? AND job_id=?', (uid, definition['id'])
        ).fetchone()
        if row is None:
            stored = put_in_transaction(registry, db, uid, sha256, definition)
        else:
            if row['apk_sha256'] != sha256:
                raise PermissionError('Job work package generation changed')
            stored = record(row)
            drained = (
                db.execute(
                    '''SELECT 1 FROM persisted_job_claims c JOIN job_work_drains d ON c.token=d.token
                WHERE c.owner_uid=? AND c.job_id=?''',
                    (uid, definition['id']),
                ).fetchone()
                is not None
            )
            if stored['definition'] != definition or drained:
                # The existing definition writer validates limits and timing.
                # Retain items explicitly around its replacement trigger within
                # this same transaction, including old worker delivery tokens.
                items = db.execute(
                    'SELECT * FROM job_work_items WHERE owner_uid=? AND job_id=? ORDER BY work_id',
                    (uid, definition['id']),
                ).fetchall()
                stored = put_in_transaction(registry, db, uid, sha256, definition)
                db.executemany(
                    'INSERT INTO job_work_items VALUES(?,?,?,?,?,?)',
                    [tuple(item) for item in items],
                )
        work_id = _insert(db, uid, definition['id'], payload)
        return dict(stored, work_id=work_id)


def _current(registry, db, uid, job_id, token, *, allow_drained=False):
    integer(uid, 10000, 19999)
    integer(job_id, -(1 << 31), (1 << 31) - 1)
    token_value(token)
    row = db.execute(
        '''SELECT j.* FROM persisted_jobs j JOIN persisted_job_claims c
        ON j.owner_uid=c.owner_uid AND j.job_id=c.job_id
        AND j.apk_sha256=c.apk_sha256 AND j.revision=c.revision
        WHERE c.owner_uid=? AND c.job_id=? AND c.token=?''',
        (uid, job_id, token),
    ).fetchone()
    if row is None:
        raise PermissionError('Job work claim is not current')
    caller = registry._job_caller(db, uid, row['apk_sha256'])
    registry._authorize_job(db, caller, record(row)['definition']['component'], row['apk_sha256'])
    if (
        not allow_drained
        and db.execute('SELECT 1 FROM job_work_drains WHERE token=?', (token,)).fetchone()
    ):
        raise PermissionError('Job work claim has finished draining')


def seal_drained(registry, uid, job_id, token):
    """Atomically close an empty work stream before its worker exits.

    Enqueue after this point rotates the revision, retaining the old worker's
    claim until teardown. Its eventual completion cannot delete that new job.
    Outstanding delivered work prevents sealing, just like pending work.
    """
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        _current(registry, db, uid, job_id, token, allow_drained=True)
        if db.execute(
            'SELECT 1 FROM job_work_items WHERE owner_uid=? AND job_id=?', (uid, job_id)
        ).fetchone():
            return False
        db.execute('INSERT OR IGNORE INTO job_work_drains VALUES(?)', (token,))
        return True


def dequeue(registry, uid, job_id, token):
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        _current(registry, db, uid, job_id, token)
        row = db.execute(
            '''SELECT * FROM job_work_items WHERE owner_uid=? AND job_id=?
            AND delivery_token IS NULL ORDER BY work_id LIMIT 1''',
            (uid, job_id),
        ).fetchone()
        if row is None:
            return None
        count = integer(row['deliveries'] + 1, 1, (1 << 31) - 1)
        db.execute(
            'UPDATE job_work_items SET deliveries=?,delivery_token=? WHERE work_id=?',
            (count, token, row['work_id']),
        )
        return dict(id=row['work_id'], deliveries=count, payload=row['payload'])


def complete(registry, uid, job_id, token, work_id):
    integer(work_id, 1, (1 << 31) - 1)
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        _current(registry, db, uid, job_id, token)
        return (
            db.execute(
                '''DELETE FROM job_work_items WHERE owner_uid=? AND job_id=?
            AND delivery_token=? AND work_id=?''',
                (uid, job_id, token, work_id),
            ).rowcount
            == 1
        )
