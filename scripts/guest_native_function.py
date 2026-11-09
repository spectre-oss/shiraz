# SPDX-License-Identifier: GPL-3.0-only
"""Bound one native function with independent Mach-O function-start metadata.

This returns file bytes only. It authenticates neither instructions, effects,
execution, callers, lifetime nor the producer of the supplied image.
"""

import struct
from dataclasses import dataclass
from itertools import pairwise

from guest_cached_self_record import _MachO

MAX_FUNCTION_BYTES = 64 << 10
MAX_STARTS = 1_000_000
MAX_START_BYTES = 10 * MAX_STARTS + 1
MAX_FUNCTION_ALIASES = 64
UINT64_END = 1 << 64


@dataclass(frozen=True)
class NativeFunction:
    entry: int
    end: int
    body: bytes


@dataclass(frozen=True)
class NativeFunctionIdentity:
    names: tuple[str, ...]
    function: NativeFunction


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
    text_sections = sorted((section for section in image.sections
                            if section[1] and image._text(section, section[0], section[1])),
                           key=lambda section: section[0])
    if any(left[0] + left[1] > right[0] for left, right in pairwise(text_sections)):
        raise ValueError('overlapping native function-start text sections')
    text_index = 0
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
        while (text_index < len(text_sections)
               and text_sections[text_index][0] + text_sections[text_index][1] <= address):
            text_index += 1
        if (address & 3 or text_index == len(text_sections)
                or not image._text(text_sections[text_index], address, 4)):
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
    intersections = sum(bool(section[1]) and section[0] < end
                        and image.entry < section[0] + section[1] for section in image.sections)
    if len(sections) != 1 or intersections != 1:
        raise ValueError('native function body crosses text ownership')
    section = sections[0]
    offset = section[2] + image.entry - section[0]
    return NativeFunction(image.entry, end, image.data[offset:offset + size])


def read_native_function_at(source, anchor_symbol, target):
    """Bind a direct target to named same-file text and independent start bounds.

    None means no named text definition, never an innocuous target. Aliases are
    retained, rather than selecting a convenient name for an identical address.
    Declared symbols and bounded bytes establish no callable semantics.
    """
    if type(target) is not int or not 0 <= target < UINT64_END or target & 3:
        raise ValueError('native function target bound')
    image = _MachO(source, symbol=anchor_symbol, body_bytes=4)
    if sum(image._text(section, target, 4) for section in image.sections) != 1:
        return None
    count = struct.unpack_from('<I', image.data, 16)[0]
    cursor = 32
    for _ in range(count):
        command, size = struct.unpack_from('<II', image.data, cursor)
        if command == 2:
            symbols, number, strings, length = struct.unpack_from('<IIII', image.data, cursor + 8)
            break
        cursor += size
    names = set()
    for index in range(number):
        string, kind, section, description, value = struct.unpack_from(
            '<IBBHQ', image.data, symbols + index * 16)
        if value != target or kind & 0xe0:
            continue
        if (kind & 0xe != 0xe or description & 0x100
                or not 1 <= section <= len(image.sections)
                or not image._text(image.sections[section - 1], target, 4)):
            raise ValueError('native target symbol lacks text definition')
        # The anchor reader already bounded every symbol string. Recheck here
        # to keep the selected identity independent of that implementation detail.
        if string >= length:
            raise ValueError('native target symbol string offset')
        start = strings + string
        end = image.data.find(b'\0', start, min(strings + length, start + 4097))
        if end < 0 or end == start:
            raise ValueError('native target symbol string bound')
        try:
            name = image.data[start:end].decode('utf-8')
        except UnicodeDecodeError as error:
            raise ValueError('native target symbol encoding') from error
        if name in names:
            raise ValueError('duplicate native target symbol')
        if len(names) >= MAX_FUNCTION_ALIASES:
            raise ValueError('native target symbol alias budget')
        names.add(name)
    if not names:
        return None
    names = sorted(names)
    function = read_native_function(image.data, names[0])
    if function.entry != target:
        raise ValueError('native target independent symbol mismatch')
    return NativeFunctionIdentity(tuple(names), function)
