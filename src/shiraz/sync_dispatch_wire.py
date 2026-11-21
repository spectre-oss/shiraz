# SPDX-License-Identifier: GPL-3.0-only
"""Private host-to-worker sync payload; never accepted from an app session."""

import re
import struct

from .sync_requests import validate


def encode(claim):
    adapter = claim['adapter']
    validate(dict(account=claim['account'], authority=adapter['authority'], extras=claim['extras']))
    if not re.fullmatch('[0-9a-f]{64}', claim['request_id']):
        raise ValueError('Invalid sync request identity')
    result = bytearray(struct.pack('!I', 3))

    def string(value):
        if not isinstance(value, str) or not value or '\x00' in value:
            raise ValueError('Invalid sync dispatch string')
        raw = value.encode('utf-8')
        if len(raw) > 32768:
            raise ValueError('Sync dispatch string limit')
        result.extend(struct.pack('!I', len(raw)))
        result.extend(raw)

    def account(value):
        string(value['name'])
        string(value['type'])

    for value in (
        claim['request_id'],
        adapter['package'],
        adapter['component'],
        adapter['authority'],
    ):
        string(value)
    account(claim['account'])
    result.extend(struct.pack('!I', len(claim['extras'])))
    tags = {
        'null': 0,
        'boolean': 1,
        'int': 2,
        'long': 3,
        'float': 4,
        'double': 5,
        'string': 6,
        'account': 7,
    }
    for key, item in sorted(claim['extras'].items()):
        string(key)
        kind, value = item['type'], item['value']
        result.append(tags[kind])
        if kind in ('boolean', 'int', 'long', 'float', 'double'):
            result.extend(
                struct.pack(
                    {'boolean': '!B', 'int': '!i', 'long': '!q', 'float': '!f', 'double': '!d'}[
                        kind
                    ],
                    value,
                )
            )
        elif kind == 'string':
            raw = value.encode('utf-8')
            result.extend(struct.pack('!I', len(raw)))
            result.extend(raw)
        elif kind == 'account':
            account(value)
    if len(result) > 65536:
        raise ValueError('Sync dispatch frame limit')
    return bytes(result)
