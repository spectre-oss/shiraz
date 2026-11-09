# SPDX-License-Identifier: GPL-3.0-only
"""Match a prepared-query snapshot to independent first-party Mach-O bytes.

The caller must authenticate and explicitly trust the exact bridge identity and
its publication/stack-exclusion contract. A matching record binds observed table
addresses to the instruction read set; it does not authenticate the producer,
read live rows, prove execution, or establish lifetime, asynchronous safety,
native-body purity or complete TLS consumers.
"""

from dataclasses import dataclass

from guest_cached_self_record import _MachO
from guest_prepared_cleanup import (
    BODY_BYTES,
    TABLE_BYTES,
    PreparedCleanupEffects,
    recognize_prepared_cleanup,
)

SYMBOL = '__ZN6shiraz5guest31prepared_guest_cleanup_admittedEm'
ROLES = ('owners', 'keys', 'low', 'high', 'admission')
UINT64_END = 1 << 64


@dataclass(frozen=True)
class PreparedCleanupRecordTable:
    role: str
    offset: int
    byte_count: int


@dataclass(frozen=True)
class PreparedCleanupRecord:
    entry_offset: int
    body_hex: str
    tables: tuple[PreparedCleanupRecordTable, ...]
    stack_exclusion_profile: int = 1


@dataclass(frozen=True)
class ValidatedPreparedCleanup:
    record: PreparedCleanupRecord
    effects: PreparedCleanupEffects


def _span(start, size):
    return type(start) is int and 0 <= start <= UINT64_END - size


def _record(record):
    fields = {'profile', 'verification', 'execution_claim', 'entry_offset',
              'body_hex', 'tables', 'stack_exclusion_profile'}
    if type(record) is not dict or set(record) != fields:
        raise ValueError('prepared cleanup record schema')
    if (type(record['profile']) is not int or record['profile'] != 1
            or type(record['stack_exclusion_profile']) is not int
            or record['stack_exclusion_profile'] != 1
            or type(record['verification']) is not str
            or record['verification'] != 'mapped-first-party-code-and-cache-ranges'
            or record['execution_claim'] is not False
            or not _span(record['entry_offset'], BODY_BYTES)
            or record['entry_offset'] & 3):
        raise ValueError('prepared cleanup record fields')
    body = record['body_hex']
    if (type(body) is not str or len(body) != BODY_BYTES * 2
            or any(character not in '0123456789abcdef' for character in body)):
        raise ValueError('prepared cleanup body hex bound')
    rows = record['tables']
    if type(rows) is not list or len(rows) != len(ROLES):
        raise ValueError('prepared cleanup table count')
    spans = [(record['entry_offset'], record['entry_offset'] + BODY_BYTES)]
    tables = []
    for role, row in zip(ROLES, rows):
        if (type(row) is not dict or set(row) != {'role', 'offset', 'bytes'}
                or type(row['role']) is not str or row['role'] != role
                or type(row['bytes']) is not int or row['bytes'] != TABLE_BYTES
                or not _span(row['offset'], TABLE_BYTES) or row['offset'] & 7):
            raise ValueError('prepared cleanup table fields')
        start, end = row['offset'], row['offset'] + TABLE_BYTES
        if any(start < high and low < end for low, high in spans):
            raise ValueError('prepared cleanup table/code overlap')
        spans.append((start, end))
        tables.append(PreparedCleanupRecordTable(role, start, TABLE_BYTES))
    return PreparedCleanupRecord(record['entry_offset'], body, tuple(tables))


def validate_prepared_cleanup_record(record, bridge_bytes=None):
    """Return matched immutable facts only when exact trusted bytes are supplied.

    None is optional absence. Present records are fully schema-checked even
    without bytes, but then return no effects. Invalid or contradictory records
    raise ValueError instead of silently becoming an unavailable certificate.
    Trust in the producer and runtime publication remains the caller's job.
    """
    if record is None:
        return None
    snapshot = _record(record)
    if bridge_bytes is None:
        return None
    image = _MachO(bridge_bytes, symbol=SYMBOL, body_bytes=BODY_BYTES)
    if (image.base > UINT64_END - BODY_BYTES - snapshot.entry_offset
            or image.entry != image.base + snapshot.entry_offset):
        raise ValueError('prepared cleanup record disagrees with independent symbol')
    words = tuple(image.read_word(image.entry + offset) for offset in range(0, BODY_BYTES, 4))
    if (any(word is None for word in words)
            or b''.join(word.to_bytes(4, 'little') for word in words).hex() != snapshot.body_hex):
        raise ValueError('prepared cleanup mapped/file body mismatch')
    effects = recognize_prepared_cleanup(image.read_word, image.entry, image.readable_data)
    if effects is None:
        raise ValueError('unsupported prepared cleanup complete body/table effects')
    expected = []
    for table in snapshot.tables:
        if image.base > UINT64_END - TABLE_BYTES - table.offset:
            raise ValueError('prepared cleanup table normalization overflow')
        expected.append((table.role, image.base + table.offset, table.byte_count))
    if [(table.role, table.address, table.byte_count) for table in effects.tables] != expected:
        raise ValueError('prepared cleanup instruction/record table mismatch')
    return ValidatedPreparedCleanup(snapshot, effects)
