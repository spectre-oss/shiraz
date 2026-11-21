# SPDX-License-Identifier: GPL-3.0-only
"""Versioned job data for host storage; contains no caller identity or capability.

This validates data only. Admission, persistence permission, package generation,
dispatch and clock conversion remain the host scheduler's responsibility.
"""

import json
import math
import re

MAX_BYTES = 32768
LONG_MAX = (1 << 63) - 1
NAME = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*')
FIELDS = {
    'version',
    'id',
    'component',
    'persisted',
    'minimum_latency_ms',
    'deadline_ms',
    'interval_ms',
    'flex_ms',
    'initial_backoff_ms',
    'backoff_policy',
    'extras',
}


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError('Invalid job integer')
    return value


def string(value, limit):
    if type(value) is not str or len(value) > limit:
        raise ValueError('Invalid job string')
    # Reject unpaired UTF-16 surrogates before storing or emitting UTF-8.
    value.encode('utf-8')
    return value


def validate(value):
    """Return an independent, normalized definition or reject without mutation.

    Extras use explicit types so int/long, nullable string, arrays and nested
    PersistableBundle values survive JSON storage without numeric ambiguity.
    Unknown constraints/fields fail rather than being silently discarded.
    """
    if type(value) is not dict:
        raise ValueError('Invalid job definition fields')
    version = integer(value.get('version'), 1, 2)
    fields = FIELDS | {'requires_storage_not_low'} if version == 2 else FIELDS
    if set(value) != fields:
        raise ValueError('Invalid job definition fields')
    if version == 2 and type(value['requires_storage_not_low']) is not bool:
        raise ValueError('Invalid storage constraint')
    integer(value['id'], -(1 << 31), (1 << 31) - 1)
    component = string(value['component'], 1024)
    if not NAME.fullmatch(component):
        raise ValueError('Invalid job component')
    if type(value['persisted']) is not bool:
        raise ValueError('Invalid persisted flag')
    integer(value['minimum_latency_ms'], 0, LONG_MAX)
    if value['deadline_ms'] is not None:
        integer(value['deadline_ms'], 0, LONG_MAX)
    interval = integer(value['interval_ms'], 0, LONG_MAX)
    flex = integer(value['flex_ms'], 0, LONG_MAX)
    if interval:
        if (
            interval < 900000
            or not max(300000, interval // 20) <= flex <= interval
            or value['minimum_latency_ms'] != 0
            or value['deadline_ms'] is not None
        ):
            raise ValueError('Invalid periodic timing')
    elif flex:
        raise ValueError('Flex requires periodic timing')
    integer(value['initial_backoff_ms'], 10000, LONG_MAX)
    integer(value['backoff_policy'], 0, 1)
    remaining = [4096]
    string_bytes = [0]

    def bounded_string(item, limit):
        string(item, limit)
        string_bytes[0] += len(item.encode('utf-8'))
        if string_bytes[0] > MAX_BYTES:
            raise ValueError('Job extras byte limit')

    def scalar(kind, item):
        if kind == 'boolean':
            if type(item) is not bool:
                raise ValueError('Invalid boolean extra')
        elif kind in ('int', 'long'):
            bits = 32 if kind == 'int' else 64
            integer(item, -(1 << (bits - 1)), (1 << (bits - 1)) - 1)
        elif kind == 'double':
            try:
                valid = type(item) in (int, float) and math.isfinite(item)
            except OverflowError:
                valid = False
            if not valid:
                raise ValueError('Invalid double extra')
        elif kind == 'string':
            if item is not None:
                bounded_string(item, MAX_BYTES)
        else:
            raise ValueError('Unsupported job extra type')

    def bundle(items, depth):
        if depth > 8 or type(items) is not dict or len(items) > 1024:
            raise ValueError('Job extras nesting or count limit')
        for key, node in items.items():
            bounded_string(key, 1024)
            remaining[0] -= 1
            if remaining[0] < 0 or type(node) is not dict or set(node) != {'type', 'value'}:
                raise ValueError('Invalid job extra node')
            kind, item = node['type'], node['value']
            if type(kind) is not str or len(kind) > 16:
                raise ValueError('Invalid job extra type')
            if kind == 'bundle':
                if item is not None:
                    bundle(item, depth + 1)
            elif kind.endswith('[]'):
                base = kind[:-2]
                if base not in ('boolean', 'int', 'long', 'double', 'string'):
                    raise ValueError('Unsupported job extra array')
                if item is not None:
                    if type(item) is not list or len(item) > 1024:
                        raise ValueError('Job extra array limit')
                    remaining[0] -= len(item)
                    if remaining[0] < 0:
                        raise ValueError('Job extra element limit')
                    for entry in item:
                        scalar(base, entry)
            else:
                scalar(kind, item)

    bundle(value['extras'], 0)
    encoded = json.dumps(
        value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False
    )
    if len(encoded.encode('utf-8')) > MAX_BYTES:
        raise ValueError('Job definition byte limit')
    return json.loads(encoded)
