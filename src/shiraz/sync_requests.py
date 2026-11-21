# SPDX-License-Identifier: GPL-3.0-only
"""Bounded durable sync admission. Queueing is not dispatch or completion."""

import hashlib
import json
import math
import secrets
import time

from . import account_store, sync_owners


def validate(request):
    if not isinstance(request, dict) or set(request) != {'account', 'authority', 'extras'}:
        raise ValueError('Invalid sync request fields')
    account = request['account']
    if account is not None:
        if not isinstance(account, dict) or set(account) != {'name', 'type'}:
            raise ValueError('Invalid sync account')
        for key in ('name', 'type'):
            account_store.text(account[key], key)
    account_store.text(request['authority'], 'sync authority', nullable=True)
    extras = request['extras']
    if not isinstance(extras, dict) or len(extras) > 128:
        raise ValueError('Sync extras count limit')
    for key, item in extras.items():
        account_store.text(key, 'sync extra key')
        if not isinstance(item, dict) or set(item) != {'type', 'value'}:
            raise ValueError('Invalid typed sync extra')
        kind, value = item['type'], item['value']
        valid = False
        if kind == 'null':
            valid = value is None
        elif kind == 'boolean':
            valid = type(value) is bool
        elif kind in ('int', 'long'):
            bits = 32 if kind == 'int' else 64
            valid = type(value) is int and -(1 << (bits - 1)) <= value < (1 << (bits - 1))
        elif kind in ('float', 'double'):
            valid = (
                type(value) in (float, int)
                and abs(value)
                <= (3.4028234663852886e38 if kind == 'float' else 1.7976931348623157e308)
                and math.isfinite(value)
            )
        elif kind == 'string':
            valid = isinstance(value, str) and len(value) <= 16384 and '\x00' not in value
        elif kind == 'account':
            valid = isinstance(value, dict) and set(value) == {'name', 'type'}
            if valid:
                for field in ('name', 'type'):
                    account_store.text(value[field], field)
        if not valid:
            raise ValueError('Invalid sync extra type or value')
    if len(json.dumps(request, allow_nan=False).encode()) > 32768:
        raise ValueError('Sync request byte limit')


def enqueue(db, caller, request, boolean):
    """Called under the registry write transaction after authenticating caller."""
    validate(request)
    rows = db.execute(
        '''SELECT a.name,a.type,p.uid,p.sha256,p.manifest FROM accounts a
        JOIN authenticator_owners o ON a.owner_uid=o.uid AND a.type=o.type
        JOIN packages p ON p.package=o.package AND p.uid=o.uid AND p.signers=o.signers
        WHERE o.signers=? ORDER BY a.type,a.name''',
        (caller['signers'],),
    ).fetchall()
    accounts = [
        row
        for row in rows
        if boolean(json.loads(row['manifest']).get('application_enabled'), True)
        and (
            request['account'] is None
            or request['account'] == dict(name=row['name'], type=row['type'])
        )
    ]
    adapters = sync_owners.selected(db, boolean)
    revision = db.execute(
        'SELECT revision FROM account_change_revisions WHERE signers=?', (caller['signers'],)
    ).fetchone()
    identities = []
    for account in accounts:
        for adapter in adapters:
            if adapter['account_type'] != account['type'] or request['authority'] not in (
                None,
                adapter['authority'],
            ):
                continue
            payload = dict(
                caller_uid=caller['uid'],
                caller_sha256=caller['sha256'],
                account_revision=revision['revision'] if revision else '',
                account=dict(name=account['name'], type=account['type']),
                account_owner_uid=account['uid'],
                account_owner_sha256=account['sha256'],
                adapter=adapter,
                extras=request['extras'],
            )
            encoded = json.dumps(payload, sort_keys=True, separators=(',', ':'), allow_nan=False)
            key = hashlib.sha256(encoded.encode()).hexdigest()
            exists = db.execute(
                'SELECT 1 FROM pending_sync_requests WHERE request_id=?', (key,)
            ).fetchone()
            if exists:
                # A new request during execution must survive that execution's completion.
                db.execute('UPDATE sync_request_claims SET rerun=1 WHERE request_id=?', (key,))
                db.execute(
                    'UPDATE sync_request_schedule SET suspended=0 WHERE request_id=?', (key,)
                )
            if not exists:
                total, own = db.execute(
                    'SELECT COUNT(*),COALESCE(SUM(caller_uid=?),0) FROM pending_sync_requests',
                    (caller['uid'],),
                ).fetchone()
                if total >= 1024 or own >= 128:
                    raise ValueError('Pending sync request limit')
                db.execute(
                    'INSERT INTO pending_sync_requests VALUES(?,?,?)', (key, caller['uid'], encoded)
                )
            identities.append(key)
    return identities


def pending(db, boolean):
    """Trusted coordinator view. Discard stale work; this does not claim execution."""
    adapters = sync_owners.selected(db, boolean)
    result = []
    for row in db.execute('SELECT * FROM pending_sync_requests ORDER BY request_id').fetchall():
        item = json.loads(row['payload'])
        caller = db.execute('SELECT * FROM packages WHERE uid=?', (item['caller_uid'],)).fetchone()
        valid = caller is not None and caller['sha256'] == item['caller_sha256']
        if valid:
            valid = boolean(json.loads(caller['manifest']).get('application_enabled'), True)
            revision = db.execute(
                'SELECT revision FROM account_change_revisions WHERE signers=?',
                (caller['signers'],),
            ).fetchone()
            valid = valid and item['account_revision'] == (revision['revision'] if revision else '')
            owner = db.execute(
                '''SELECT p.* FROM authenticator_owners o JOIN packages p
                ON p.uid=o.uid AND p.package=o.package AND p.signers=o.signers
                JOIN accounts a ON a.owner_uid=o.uid AND a.type=o.type
                WHERE a.type=? AND a.name=? AND o.signers=?''',
                (item['account']['type'], item['account']['name'], caller['signers']),
            ).fetchone()
            valid = (
                valid
                and owner is not None
                and owner['uid'] == item['account_owner_uid']
                and owner['sha256'] == item['account_owner_sha256']
            )
            if valid:
                valid = boolean(json.loads(owner['manifest']).get('application_enabled'), True)
        if valid and item['adapter'] in adapters:
            result.append(dict(request_id=row['request_id'], **item))
        else:
            db.execute('DELETE FROM pending_sync_requests WHERE request_id=?', (row['request_id'],))
            db.execute('DELETE FROM sync_request_schedule WHERE request_id=?', (row['request_id'],))
    return result


def claim(db, boolean, request_id, *, now_ms=None):
    """Atomically reserve revalidated work. Only a trusted host coordinator may call."""
    selected = next(
        (item for item in pending(db, boolean) if item['request_id'] == request_id), None
    )
    if selected is None:
        return None
    now_ms = int(time.time() * 1000) if now_ms is None else now_ms
    schedule = db.execute(
        'SELECT * FROM sync_request_schedule WHERE request_id=?', (request_id,)
    ).fetchone()
    if schedule is not None and (schedule['suspended'] or schedule['not_before_ms'] > now_ms):
        return None
    adapter = selected['adapter']
    service = json.dumps([adapter['package'], adapter['component']], separators=(',', ':'))
    account = json.dumps(selected['account'], sort_keys=True, separators=(',', ':'))
    active = db.execute('SELECT * FROM sync_request_claims').fetchall()
    if len(active) >= 32:
        return None
    for row in active:
        if row['request_id'] == request_id:
            return None
        if row['service'] == service and (
            not adapter.get('allow_parallel_syncs', False)
            or not row['parallel']
            or row['account'] == account
        ):
            return None
    token = secrets.token_hex(32)
    db.execute(
        'INSERT INTO sync_request_claims VALUES(?,?,?,?,?,0)',
        (request_id, token, service, account, int(adapter.get('allow_parallel_syncs', False))),
    )
    return dict(selected, claim_token=token)


def finish(db, request_id, token, success, *, status=None, delay_until=0, now_ms=None):
    """Release only after the worker has stopped; no timeout-based stealing or blind retry."""
    if type(success) is not bool:
        raise ValueError('Sync outcome must be boolean')
    status = ('succeeded' if success else 'retry') if status is None else status
    if status not in ('succeeded', 'retry', 'failed', 'canceled') or success != (
        status == 'succeeded'
    ):
        raise ValueError('Inconsistent sync outcome')
    if type(delay_until) is not int or not 0 <= delay_until <= 0x7FFFFFFFFFFFFFFF:
        raise ValueError('Invalid sync delay')
    row = db.execute(
        'SELECT * FROM sync_request_claims WHERE request_id=?', (request_id,)
    ).fetchone()
    if row is None or not isinstance(token, str) or not secrets.compare_digest(row['token'], token):
        return False
    db.execute(
        'DELETE FROM sync_request_claims WHERE request_id=? AND token=?', (request_id, token)
    )
    if success and not row['rerun']:
        db.execute('DELETE FROM pending_sync_requests WHERE request_id=?', (request_id,))
        db.execute('DELETE FROM sync_request_schedule WHERE request_id=?', (request_id,))
    elif db.execute(
        'SELECT 1 FROM pending_sync_requests WHERE request_id=?', (request_id,)
    ).fetchone():
        now_ms = int(time.time() * 1000) if now_ms is None else now_ms
        previous = db.execute(
            'SELECT attempts FROM sync_request_schedule WHERE request_id=?', (request_id,)
        ).fetchone()
        attempts = 0 if success else min(32, (previous['attempts'] if previous else 0) + 1)
        backoff = 0 if success else min(86400000, 30000 * (2 ** min(attempts - 1, 12)))
        # Adapter delayUntil is epoch seconds. Clamp multiplication to SQLite's signed range.
        not_before = max(now_ms + backoff, min(delay_until, 0x7FFFFFFFFFFFFFFF // 1000) * 1000)
        db.execute(
            'INSERT OR REPLACE INTO sync_request_schedule VALUES(?,?,?,?)',
            (request_id, attempts, not_before, int(status in ('failed', 'canceled'))),
        )
    return True
