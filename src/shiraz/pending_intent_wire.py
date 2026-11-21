# SPDX-License-Identifier: GPL-3.0-only
"""Strict registration/inspection messages; sending remains host-dispatch only."""

import base64
import binascii

from .pending_intent_store import CanceledTokenError, PendingIntentStore


def blob(value, limit):
    if not isinstance(value, str) or not 1 <= len(value) <= 4 * ((limit + 2) // 3):
        raise ValueError('PendingIntent snapshot encoding limit')
    try:
        raw = base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError('Invalid PendingIntent snapshot encoding') from error
    if not raw or len(raw) > limit or base64.b64encode(raw).decode('ascii') != value:
        raise ValueError('Noncanonical PendingIntent snapshot')
    return raw


def handle(registry, identity, request):
    store = PendingIntentStore(registry)
    caller = identity.uid, identity.sha256
    try:
        if request['op'] == 'pending_intent_obtain':
            if set(request) != {'op', 'kind', 'request_code', 'identity', 'payload', 'flags'}:
                raise ValueError('Invalid PendingIntent registration fields')
            result = store.obtain(
                *caller,
                kind=request['kind'],
                request_code=request['request_code'],
                identity=blob(request['identity'], 8192),
                payload=blob(request['payload'], 32768),
                flags=request['flags'],
            )
        elif request['op'] in ('pending_intent_inspect', 'pending_intent_cancel'):
            if set(request) != {'op', 'token'}:
                raise ValueError('Invalid PendingIntent capability fields')
            result = store.inspect(*caller, request['token'])
            if request['op'] == 'pending_intent_cancel':
                store.cancel(*caller, request['token'])
                result = None
        else:
            raise ValueError('Unknown PendingIntent operation')
        return dict(ok=True, pending_intent=result)
    except CanceledTokenError:
        return dict(ok=False, canceled=True, error='PendingIntent is canceled or unavailable')
