# SPDX-License-Identifier: GPL-3.0-only
"""Session-owned dynamic broadcast registrations; no guest-supplied process identity."""

import json

from .package_registry import NAME


def validate_registration(request):
    if set(request) != {'op', 'receiver', 'filter', 'permission', 'flags'}:
        raise ValueError('Invalid dynamic receiver registration fields')
    receiver = request['receiver']
    if (
        not isinstance(receiver, str)
        or len(receiver) != 32
        or any(c not in '0123456789abcdef' for c in receiver)
    ):
        raise ValueError('Invalid dynamic receiver identity')
    permission, flags, rule = request['permission'], request['flags'], request['filter']
    if permission is not None and (
        not isinstance(permission, str) or len(permission) > 1024 or not NAME.fullmatch(permission)
    ):
        raise ValueError('Invalid dynamic receiver permission')
    if type(flags) is not int or flags & ~7 or flags & 6 == 6 or flags & 5 == 5:
        raise ValueError('Invalid dynamic receiver flags')
    if not isinstance(rule, dict) or set(rule) != {
        'action',
        'category',
        'mime',
        'scheme',
        'priority',
    }:
        raise ValueError('Unsupported dynamic receiver filter fields')
    if type(rule['priority']) is not int or not -2147483648 <= rule['priority'] <= 2147483647:
        raise ValueError('Invalid dynamic receiver priority')
    for key in ('action', 'category', 'mime', 'scheme'):
        values = rule[key]
        if (
            not isinstance(values, list)
            or len(values) > 128
            or any(not isinstance(value, str) or not 1 <= len(value) <= 4096 for value in values)
        ):
            raise ValueError('Invalid dynamic receiver filter values')
        if len(set(values)) != len(values):
            raise ValueError('Duplicate dynamic receiver filter values')
    for mime in rule['mime']:
        parts = mime.split('/')
        if (
            len(parts) != 2
            or not all(parts)
            or '*' in parts[0]
            and mime != '*/*'
            or '*' in parts[1]
            and parts[1] != '*'
        ):
            raise ValueError('Invalid dynamic receiver MIME type')
    # Snapshot and account the entire registration, including permissions.
    encoded = json.dumps(request, separators=(',', ':'), ensure_ascii=True).encode()
    if len(encoded) > 32768:
        raise ValueError('Dynamic receiver registration byte limit')
    return json.loads(encoded), len(encoded)
