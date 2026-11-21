# SPDX-License-Identifier: GPL-3.0-only
"""Host-only recovery of registered claims after every lifetime holder exits."""

import os

from . import job_lifetime, job_store


def register(registry, claim, lifetime):
    info = os.fstat(lifetime.fileno())
    with registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        owned = db.execute(
            'SELECT 1 FROM persisted_job_claims WHERE owner_uid=? AND job_id=? AND token=?',
            (claim['owner_uid'], claim['id'], claim['token']),
        ).fetchone()
        if owned is None:
            raise RuntimeError('Job claim disappeared before lifetime registration')
        db.execute(
            'INSERT INTO job_recovery VALUES(?,?,?)', (claim['token'], info.st_dev, info.st_ino)
        )


def candidates(registry):
    with registry.connect() as db:
        return [
            dict(row)
            for row in db.execute(
                '''SELECT c.owner_uid,c.job_id,c.token FROM persisted_job_claims c
            JOIN job_recovery r ON r.token=c.token ORDER BY c.token LIMIT ?''',
                (job_store.GLOBAL_LIMIT,),
            )
        ]


def recover(registry, uid, job_id, token):
    job_store.token_value(token)
    with registry.connect() as db:
        registered = db.execute(
            '''SELECT r.device,r.inode FROM job_recovery r JOIN persisted_job_claims c ON c.token=r.token
            WHERE c.owner_uid=? AND c.job_id=? AND c.token=?''',
            (uid, job_id, token),
        ).fetchone()
    if registered is None:
        return False  # Legacy or already-completed claims are never inferred recoverable.
    lifetime = job_lifetime.probe(registry.root, token)
    if lifetime is None:
        return False
    with lifetime:
        info = os.fstat(lifetime.fileno())
        if (info.st_dev, info.st_ino) != (registered['device'], registered['inode']):
            raise RuntimeError('Job lifetime identity changed')
        # Keep the acquired lifetime lock through the token-checked transaction.
        # Unknown completion is retried with backoff, never marked successful.
        return job_store.complete_claim(registry, uid, job_id, token, retry=True)


def retire(registry, token):
    """Remove an unused lock while holding it and excluding claim creation."""
    try:
        lifetime = job_lifetime.probe(registry.root, token)
    except FileNotFoundError:
        return False  # Another collector already retired it.
    if lifetime is None:
        return False
    with lifetime, registry.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        if db.execute('SELECT 1 FROM persisted_job_claims WHERE token=?', (token,)).fetchone():
            return False
        if db.execute('SELECT 1 FROM job_recovery WHERE token=?', (token,)).fetchone():
            return False
        try:
            job_lifetime.unlink_owned(registry.root, token, lifetime)
        except FileNotFoundError:
            return False
        return True


class LockCollector:
    """Incremental directory scan; at most 64 entries inspected per host tick."""

    def __init__(self, registry):
        self.registry = registry
        self.entries = None

    def tick(self):
        if self.entries is None:
            self.entries = os.scandir(job_lifetime._directory(self.registry.root))
        for _ in range(64):
            try:
                entry = next(self.entries)
            except StopIteration:
                self.close()
                return
            name = entry.name
            if len(name) != 37 or not name.endswith('.lock'):
                continue
            token = name[:-5]
            try:
                job_store.token_value(token)
            except ValueError:
                continue
            retire(self.registry, token)

    def close(self):
        if self.entries is not None:
            self.entries.close()
            self.entries = None
