# SPDX-License-Identifier: GPL-3.0-only
"""Bound one native function with independent Mach-O function-start metadata.

This returns file bytes only. It authenticates neither instructions, effects,
execution, callers, lifetime nor the producer of the supplied image.
"""

import struct
from dataclasses import dataclass

from guest_cached_self_record import _MachO

MAX_FUNCTION_BYTES = 64 << 10
MAX_STARTS = 1_000_000
MAX_START_BYTES = 10 * MAX_STARTS + 1
UINT64_END = 1 << 64


@dataclass(frozen=True)
class NativeFunction:
    entry: int
    end: int
    body: bytes


def _uleb(data, cursor):
    start = cursor
    value = 0
    for index in range(10):
        if cursor >= len(data):
            raise ValueError('truncated native function-start ULEB')
        byte = data[cursor]
        cursor += 1
        if index == 9 and byte > 1:
            raise ValueError('native function-start ULEB overflow')
        value |= (byte & 127) << (7 * index)
        if not byte & 128:
            if cursor - start > 1 and not byte:
                raise ValueError('noncanonical native function-start ULEB')
            return value, cursor
    raise ValueError('unterminated native function-start ULEB')


def read_native_function(source, symbol):
    """Require a symbol/start match and a following start, returning frozen bytes.

    Every advertised start must be aligned, file-backed RX __TEXT,__text. A
    final function lacking its next independent start is intentionally refused.
    """
    image = _MachO(source, symbol=symbol, body_bytes=4)
    count, command_bytes = struct.unpack_from('<II', image.data, 16)
    cursor, limit = 32, 32 + command_bytes
    starts = None
    symbol_spans = []
    for _ in range(count):
        command, size = struct.unpack_from('<II', image.data, cursor)
        # _MachO has already validated the entire command sequence.
        if command == 0x26:  # LC_FUNCTION_STARTS / linkedit_data_command
            if size != 16 or starts is not None:
                raise ValueError('invalid or duplicate native function-start command')
            starts = struct.unpack_from('<II', image.data, cursor + 8)
        elif command == 2:
            offset, number, strings, length = struct.unpack_from('<IIII', image.data, cursor + 8)
            symbol_spans = [(offset, number * 16), (strings, length)]
        cursor += size
    if cursor != limit or starts is None:
        raise ValueError('missing native function-start command')
    offset, size = starts
    if not 0 < size <= MAX_START_BYTES or offset > len(image.data) or size > len(image.data) - offset:
        raise ValueError('native function-start byte bound')
    owners = sum(name == '__LINKEDIT' and protection & 1 and not protection & 4
                 and offset >= start and offset - start <= length
                 and size <= length - (offset - start)
                 for _, _, start, length, protection, name in image.segments)
    if owners != 1:
        raise ValueError('native function-start data lacks independent linkedit ownership')
    if any(offset < start + length and start < offset + size for start, length in symbol_spans):
        raise ValueError('native function-start data overlaps symbols/strings')
    data = image.data[offset:offset + size]
    cursor, address = 0, image.base
    addresses = []
    terminated = False
    while cursor < len(data):
        delta, cursor = _uleb(data, cursor)
        if not delta:
            if any(data[cursor:]):
                raise ValueError('native function-start nonzero trailing bytes')
            terminated = True
            break
        if len(addresses) >= MAX_STARTS or delta > UINT64_END - 1 - address:
            raise ValueError('native function-start count/address bound')
        address += delta
        if address & 3 or sum(image._text(section, address, 4) for section in image.sections) != 1:
            raise ValueError('native function start lacks aligned complete RX text')
        addresses.append(address)
    if not terminated:
        raise ValueError('native function-start terminator required')
    try:
        index = addresses.index(image.entry)
    except ValueError as error:
        raise ValueError('native symbol is not an independent function start') from error
    if index + 1 == len(addresses):
        raise ValueError('native function lacks next independent start')
    end = addresses[index + 1]
    size = end - image.entry
    if not 0 < size <= MAX_FUNCTION_BYTES or size & 3:
        raise ValueError('native function body size bound')
    sections = [section for section in image.sections if image._text(section, image.entry, size)]
    if len(sections) != 1:
        raise ValueError('native function body crosses text ownership')
    section = sections[0]
    offset = section[2] + image.entry - section[0]
    return NativeFunction(image.entry, end, image.data[offset:offset + size])
