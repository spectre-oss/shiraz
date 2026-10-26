# SPDX-License-Identifier: GPL-3.0-only
"""Section-based native import inventory. Never loads or executes the library."""
import struct


class ElfError(ValueError):
    pass


def inspect_elf(data):
    def unpack(fmt, off):
        if off < 0 or off + struct.calcsize(fmt) > len(data):
            raise ElfError('ELF structure outside file')
        return struct.unpack_from(fmt, data, off)
    def region(off, size):
        if off < 0 or size < 0 or off + size > len(data):
            raise ElfError('ELF section outside file')
        return data[off:off + size]
    if len(data) < 20 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5:7] != b'\x01\x01':
        raise ElfError('unsupported ELF identification (requires 32/64-bit little endian)')
    bits = 64 if data[4] == 2 else 32
    if bits == 64:
        region(0, 64)
        offset, = unpack('<Q', 40)
        stride, count = unpack('<HH', 58)
        section_fmt, symbol_size, dynamic_fmt = '<IIQQQQIIQQ', 24, '<qQ'
    else:
        region(0, 52)
        offset, = unpack('<I', 32)
        stride, count = unpack('<HH', 46)
        section_fmt, symbol_size, dynamic_fmt = '<IIIIIIIIII', 16, '<iI'
    if count and (offset == 0 or stride < struct.calcsize(section_fmt)):
        raise ElfError('invalid ELF section-header layout')
    region(offset, count * stride)
    sections = [unpack(section_fmt, offset + i * stride) for i in range(count)]
    imports, needed = set(), set()
    has_symbols = False
    def string(table, idx):
        if idx >= len(table):
            raise ElfError('ELF string index outside table')
        end = table.find(b'\0', idx)
        if end < 0:
            raise ElfError('unterminated ELF string')
        return table[idx:end].decode('utf-8', errors='replace')
    for section in sections:
        kind, off, size, link, entsize = section[1], section[4], section[5], section[6], section[9]
        if kind not in (6, 11):
            continue
        if link >= len(sections) or sections[link][1] != 3:
            raise ElfError('ELF symbol/dynamic table has no string table')
        table_section = sections[link]
        strings = region(table_section[4], table_section[5])
        region(off, size)
        minimum = symbol_size if kind == 11 else struct.calcsize(dynamic_fmt)
        if entsize < minimum or size % entsize:
            raise ElfError('invalid ELF entry stride')
        for entry in range(off, off + size, entsize):
            if kind == 11:
                has_symbols = True
                name, = unpack('<I', entry)
                info, _, shndx = unpack('<BBH', entry + (4 if bits == 64 else 12))
                if shndx == 0 and (info >> 4) in (1, 2) and name:
                    imports.add(string(strings, name))
            else:
                tag, value = unpack(dynamic_fmt, entry)
                if tag == 0:
                    break
                if tag == 1:
                    needed.add(string(strings, value))
    return {'bits': bits, 'machine': unpack('<H', 18)[0], 'needed': sorted(needed),
            'undefined_symbols': sorted(imports), 'has_dynamic_symbol_table': has_symbols,
            'inventory_kind': 'section-based imports; symbol resolution and Bionic ABI compatibility unverified'}
