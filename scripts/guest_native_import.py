# SPDX-License-Identifier: GPL-3.0-only
"""Bind ordinary ARM64 stub bytes to a Mach-O import declaration only.

No live pointer value, dyld provider selection, call behavior, memory effect,
execution or native purity follows from the declaration returned here.
"""
import struct
from dataclasses import dataclass

from guest_cached_self_record import _MachO, _name

UINT64_END = 1 << 64


@dataclass(frozen=True)
class NativeImport:
    symbol: str
    library_ordinal: int
    slot: int
    stub_body: bytes


def read_native_import(source, anchor_symbol, target):
    """Return a checked declaration, or None outside canonical __stubs.

    A selected but malformed/unsupported stub raises ValueError. The anchor
    independently requires a defined executable symbol in the same image.
    """
    if type(target) is not int or not 0 <= target < UINT64_END:
        raise ValueError('native import target bound')
    image = _MachO(source, symbol=anchor_symbol, body_bytes=4)
    data = image.data
    count = struct.unpack_from('<I', data, 16)[0]
    cursor, sections, symtab, dysymtab = 32, [], None, None
    for _ in range(count):
        command, size = struct.unpack_from('<II', data, cursor)
        if command == 0x19:
            protection, number = struct.unpack_from('<iI', data, cursor + 60)
            for index in range(number):
                row = struct.unpack_from('<16s16sQQIIIIIIII', data, cursor + 72 + index * 80)
                sections.append((row, protection))
        elif command == 2:
            symtab = struct.unpack_from('<IIII', data, cursor + 8)
        elif command == 0xb:
            if size != 80 or dysymtab is not None:
                raise ValueError('invalid or duplicate native indirect symbol command')
            dysymtab = struct.unpack_from('<20I', data, cursor)
        cursor += size
    selected = [(row, protection) for row, protection in sections
                if _name(row[0]) == '__stubs' and row[2] <= target < row[2] + row[3]]
    if not selected:
        return None
    if len(selected) != 1:
        raise ValueError('ambiguous native import stub ownership')
    row, protection = selected[0]
    _, owner, start, length, offset, _, _, _, flags, first, stride, _ = row
    if (_name(owner) != '__TEXT' or protection & 5 != 5 or protection & 2
            or flags & 255 != 8 or stride != 12 or start & 3 or length % stride
            or (target - start) % stride or target + stride > start + length):
        raise ValueError('unsupported native import stub section')
    if sum(bool(s[3]) and s[2] < target + 12 and target < s[2] + s[3]
           for s, _ in sections) != 1:
        raise ValueError('overlapping native import stub sections')
    if dysymtab is None:
        raise ValueError('native import lacks indirect symbols')
    indirect_offset, indirect_count = dysymtab[14:16]
    indirect_bytes = indirect_count * 4
    if (not 0 < indirect_count <= 1_000_000 or indirect_offset > len(data)
            or indirect_bytes > len(data) - indirect_offset):
        raise ValueError('native indirect symbol byte bound')
    if sum(name == '__LINKEDIT' and protection & 1 and not protection & 4
           and indirect_offset >= begin and indirect_offset - begin <= length
           and indirect_bytes <= length - (indirect_offset - begin)
           for _, _, begin, length, protection, name in image.segments) != 1:
        raise ValueError('native indirect symbols lack linkedit ownership')
    symbol_offset, symbol_count, strings_offset, strings_size = symtab
    for begin, length in ((symbol_offset, symbol_count * 16), (strings_offset, strings_size)):
        if indirect_offset < begin + length and begin < indirect_offset + indirect_bytes:
            raise ValueError('native indirect symbols overlap symbols/strings')

    def indirect(index):
        if not 0 <= index < indirect_count:
            raise ValueError('native indirect symbol index bound')
        value = struct.unpack_from('<I', data, indirect_offset + index * 4)[0]
        if value >= symbol_count:
            raise ValueError('native indirect symbol is local/absolute/outside table')
        return value

    symbol_index = indirect(first + (target - start) // stride)
    string, kind, section, description, value = struct.unpack_from(
        '<IBBHQ', data, symbol_offset + symbol_index * 16)
    if kind != 1 or section or value:
        raise ValueError('native stub requires ordinary undefined external symbol')
    if not 0 < string < strings_size:
        raise ValueError('native import name offset')
    begin = strings_offset + string
    end = data.find(b'\0', begin, min(strings_offset + strings_size, begin + 4097))
    if end <= begin:
        raise ValueError('native import name bound')
    try:
        symbol = data[begin:end].decode('utf-8')
    except UnicodeDecodeError as error:
        raise ValueError('native import name encoding') from error
    body_offset = offset + target - start
    body = data[body_offset:body_offset + 12]
    adrp, load, branch = struct.unpack('<III', body)
    if (adrp & 0x9f00001f != 0x90000010 or load & 0xffc003ff != 0xf9400210
            or branch != 0xd61f0200):
        raise ValueError('unsupported native import stub instructions')
    immediate = ((adrp >> 29) & 3) | (((adrp >> 5) & 0x7ffff) << 2)
    if immediate & (1 << 20):
        immediate -= 1 << 21
    page = (target & ~4095) + immediate * 4096
    slot = page + ((load >> 10) & 4095) * 8
    if not 0 <= page < UINT64_END or not 0 <= slot <= UINT64_END - 8:
        raise ValueError('native import slot address overflow')
    pointers = [(s, p) for s, p in sections
                if s[3] and s[2] < slot + 8 and slot < s[2] + s[3]]
    if len(pointers) != 1:
        raise ValueError('native import slot ownership')
    pointer, protection = pointers[0]
    _, _, address, length, _, alignment, _, _, flags, first, _, _ = pointer
    if (slot < address or slot + 8 > address + length
            or not protection & 1 or protection & 4 or flags & 255 not in (6, 7)
            or address & 7 or length & 7 or slot & 7 or alignment < 3):
        raise ValueError('native import slot section')
    if indirect(first + (slot - address) // 8) != symbol_index:
        raise ValueError('native stub/slot indirect symbol mismatch')
    return NativeImport(symbol, description >> 8, slot, body)
