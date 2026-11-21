# SPDX-License-Identifier: GPL-3.0-only
"""Transactional account operations after registry authentication and owner selection."""

import json
import time
import uuid


def text(value, label, *, nullable=False, limit=1024):
    if value is None and nullable:
        return
    if (
        not isinstance(value, str)
        or (not nullable and not value)
        or len(value) > limit
        or '\x00' in value
    ):
        raise ValueError('Invalid account ' + label)
    if len(json.dumps(value).encode()) > 32768:
        raise ValueError('Account value byte limit')


def string_map(value):
    if not isinstance(value, dict) or len(value) > 128:
        raise ValueError('Invalid account data map')
    for key, item in value.items():
        text(key, 'data key')
        text(item, 'data value', nullable=True, limit=16384)
    encoded = json.dumps(value, separators=(',', ':'))
    if len(encoded.encode()) > 32768:
        raise ValueError('Account data byte limit')
    return encoded


def validate(request):
    fields = {
        'snapshot': set(),
        'list': {'type'},
        'invalidate': {'type', 'value'},
        'prepare_token': {'type', 'name', 'key'},
        'prepare_custom_token': {'type', 'name', 'key'},
        'commit_custom_token': {
            'type',
            'name',
            'key',
            'value',
            'expected_uid',
            'expected_sha256',
            'revision',
            'expires_ms',
        },
        'commit_token': {
            'type',
            'name',
            'key',
            'value',
            'expected_uid',
            'expected_sha256',
            'revision',
        },
        'add': {'name', 'type', 'password', 'extras'},
        'remove': {'name', 'type'},
        'get_password': {'name', 'type'},
        'set_password': {'name', 'type', 'value'},
        'get_data': {'name', 'type', 'key'},
        'set_data': {'name', 'type', 'key', 'value'},
        'get_token': {'name', 'type', 'key'},
        'set_token': {'name', 'type', 'key', 'value'},
    }
    if (
        not isinstance(request, dict)
        or not isinstance(request.get('action'), str)
        or request['action'] not in fields
        or set(request) != fields[request['action']] | {'action'}
    ):
        raise ValueError('Invalid account operation fields')
    if request['action'] == 'snapshot':
        return
    if 'name' in request:
        text(request['name'], 'name')
    text(request['type'], 'type', nullable=request['action'] == 'list')
    if 'key' in request:
        text(request['key'], 'key')
    for field in ('password', 'value'):
        if field in request:
            text(request[field], field, nullable=True, limit=16384)
    if 'extras' in request:
        string_map(request['extras'])
    if request['action'] in ('commit_token', 'commit_custom_token'):
        if (
            type(request['expected_uid']) is not int
            or not 10000 <= request['expected_uid'] <= 19999
        ):
            raise ValueError('Invalid token owner expectation')
        for field, length in (('expected_sha256', 64), ('revision', 32)):
            value = request[field]
            if (
                not isinstance(value, str)
                or len(value) != length
                or any(char not in '0123456789abcdef' for char in value)
            ):
                raise ValueError('Invalid token generation expectation')
        text(request['value'], 'token', limit=16384)
        if request['action'] == 'commit_custom_token' and (
            type(request['expires_ms']) is not int
            or not 0 <= request['expires_ms'] <= 0x7FFFFFFFFFFFFFFF
        ):
            raise ValueError('Invalid custom token expiry')


def token_revision(db, owner_uid, account_type, apk_sha256):
    row = db.execute(
        'SELECT * FROM account_token_revisions WHERE owner_uid=? AND type=?',
        (owner_uid, account_type),
    ).fetchone()
    if row is not None and row['apk_sha256'] == apk_sha256:
        return row['revision']
    revision = uuid.uuid4().hex
    db.execute(
        'INSERT OR REPLACE INTO account_token_revisions VALUES(?,?,?,?)',
        (owner_uid, account_type, apk_sha256, revision),
    )
    if row is not None:
        db.execute(
            "DELETE FROM custom_account_tokens WHERE owner_uid=? AND type=?",
            (owner_uid, account_type),
        )
        db.execute(
            "UPDATE accounts SET tokens='{}' WHERE owner_uid=? AND type=?",
            (owner_uid, account_type),
        )
    return revision


def invalidate_revision(db, owner_uid, account_type, *, clear_custom=True):
    if clear_custom:
        db.execute(
            "DELETE FROM custom_account_tokens WHERE owner_uid=? AND type=?",
            (owner_uid, account_type),
        )
    db.execute(
        'UPDATE account_token_revisions SET revision=? WHERE owner_uid=? AND type=?',
        (uuid.uuid4().hex, owner_uid, account_type),
    )


def operate(db, owner_uid, request):
    """No caller-provided UID is accepted; caller owns the surrounding transaction."""
    if request['action'] == 'invalidate':
        if request['value'] is None:
            return None
        invalidate_revision(db, owner_uid, request['type'])
        for row in db.execute(
            'SELECT name,tokens FROM accounts WHERE owner_uid=? AND type=?',
            (owner_uid, request['type']),
        ).fetchall():
            tokens = json.loads(row['tokens'])
            remaining = {key: value for key, value in tokens.items() if value != request['value']}
            if remaining != tokens:
                db.execute(
                    'UPDATE accounts SET tokens=? WHERE owner_uid=? AND type=? AND name=?',
                    (string_map(remaining), owner_uid, request['type'], row['name']),
                )
        return None
    key = (owner_uid, request['type'], request['name'])
    row = db.execute(
        'SELECT * FROM accounts WHERE owner_uid=? AND type=? AND name=?', key
    ).fetchone()
    action = request['action']
    if action == 'add':
        if row is not None:
            return False
        if (
            db.execute('SELECT COUNT(*) FROM accounts WHERE owner_uid=?', (owner_uid,)).fetchone()[
                0
            ]
            >= 1024
        ):
            raise ValueError('Account capacity exceeded')
        invalidate_revision(db, owner_uid, request['type'])
        db.execute(
            'INSERT INTO accounts VALUES(?,?,?,?,?,?)',
            (*key, request['password'], string_map(request['extras']), '{}'),
        )
        return True
    if action == 'remove':
        if row is None:
            return False
        invalidate_revision(db, owner_uid, request['type'])
        db.execute('DELETE FROM accounts WHERE owner_uid=? AND type=? AND name=?', key)
        return True
    if row is None:
        return None
    if action == 'get_password':
        return row['password']
    if action == 'set_password':
        invalidate_revision(db, owner_uid, request['type'])
        db.execute(
            "UPDATE accounts SET password=?,tokens='{}' WHERE owner_uid=? AND type=? AND name=?",
            (request['value'], *key),
        )
        return None
    column = 'userdata' if action.endswith('_data') else 'tokens'
    values = json.loads(row[column])
    if action.startswith('get_'):
        return values.get(request['key'])
    if request['value'] is None:
        values.pop(request['key'], None)
    else:
        values[request['key']] = request['value']
    encoded = string_map(values)
    invalidate_revision(db, owner_uid, request['type'])
    db.execute(
        'UPDATE accounts SET ' + column + '=? WHERE owner_uid=? AND type=? AND name=?',
        (encoded, *key),
    )
    return None


def custom_token_operation(
    db, owner_uid, owner_sha256, caller_uid, caller_sha256, revision, custom, request
):
    """Caller identity is supplied by the authenticated registry session only."""
    now = time.time_ns() // 1000000
    db.execute('DELETE FROM custom_account_tokens WHERE expires_ms<=?', (now,))
    # Retired/replaced packages must not retain capacity or expose old generations.
    db.execute('''DELETE FROM custom_account_tokens WHERE NOT EXISTS(
        SELECT 1 FROM packages p WHERE p.uid=custom_account_tokens.caller_uid
        AND p.sha256=custom_account_tokens.caller_sha256) OR NOT EXISTS(
        SELECT 1 FROM authenticator_owners o JOIN packages p ON p.package=o.package
        AND p.uid=o.uid AND p.signers=o.signers WHERE o.uid=custom_account_tokens.owner_uid
        AND o.type=custom_account_tokens.type AND p.sha256=custom_account_tokens.owner_sha256)''')
    key = (owner_uid, request['type'], request['name'], caller_uid, caller_sha256, request['key'])
    if request['action'] == 'prepare_custom_token':
        row = (
            db.execute(
                'SELECT token,expires_ms FROM custom_account_tokens WHERE owner_uid=? AND type=? AND name=? AND caller_uid=? AND caller_sha256=? AND key=? AND owner_sha256=?',
                (*key, owner_sha256),
            ).fetchone()
            if custom
            else None
        )
        return dict(
            owner_uid=owner_uid,
            owner_sha256=owner_sha256,
            revision=revision,
            custom_tokens=custom,
            token=row['token'] if row else None,
            expires_ms=row['expires_ms'] if row else None,
        )
    if (
        not custom
        or request['expected_uid'] != owner_uid
        or request['expected_sha256'] != owner_sha256
        or request['revision'] != revision
        or request['expires_ms'] <= now
    ):
        return False
    if (
        db.execute(
            'SELECT 1 FROM accounts WHERE owner_uid=? AND type=? AND name=?', key[:3]
        ).fetchone()
        is None
    ):
        return False
    existing = db.execute(
        'SELECT 1 FROM custom_account_tokens WHERE owner_uid=? AND type=? AND name=? AND caller_uid=? AND caller_sha256=? AND key=?',
        key,
    ).fetchone()
    if (
        existing is None
        and db.execute('SELECT COUNT(*) FROM custom_account_tokens').fetchone()[0] >= 256
    ):
        return False
    db.execute(
        'INSERT OR REPLACE INTO custom_account_tokens VALUES(?,?,?,?,?,?,?,?,?)',
        (*key, owner_sha256, request['value'], request['expires_ms']),
    )
    invalidate_revision(db, owner_uid, request['type'], clear_custom=False)
    return True
