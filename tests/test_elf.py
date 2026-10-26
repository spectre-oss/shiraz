# SPDX-License-Identifier: GPL-3.0-only
import struct
import unittest
from shiraz.elf import ElfError, inspect_elf


def elf_fixture(bits=64):
    header = 64 if bits == 64 else 52
    stride = 64 if bits == 64 else 40
    sections = 4
    offset = header + sections * stride
    strings = b'\0ANativeWindow_lock\0libandroid.so\0'
    symbol_size = 24 if bits == 64 else 16
    dynamic_size = 16 if bits == 64 else 8
    symoff = offset + len(strings)
    dynoff = symoff + 2 * symbol_size
    blob = bytearray(dynoff + dynamic_size * 2)
    blob[:7] = b'\x7fELF' + bytes([2 if bits == 64 else 1, 1, 1])
    struct.pack_into('<H', blob, 18, 62 if bits == 64 else 3)
    if bits == 64:
        struct.pack_into('<Q', blob, 40, header); struct.pack_into('<HH', blob, 58, stride, sections)
        sym = struct.pack('<IBBHQQ', 1, 0x12, 0, 0, 0, 0)
        dyn = struct.pack('<qQqQ', 1, 20, 0, 0)
        fmt = '<IIQQQQIIQQ'
    else:
        struct.pack_into('<I', blob, 32, header); struct.pack_into('<HH', blob, 46, stride, sections)
        sym = struct.pack('<IIIBBH', 1, 0, 0, 0x12, 0, 0)
        dyn = struct.pack('<iIiI', 1, 20, 0, 0)
        fmt = '<IIIIIIIIII'
    for i, kind, off, size, link, ent in [(1, 3, offset, len(strings), 0, 0),
                                        (2, 11, symoff, symbol_size * 2, 1, symbol_size),
                                        (3, 6, dynoff, len(dyn), 1, dynamic_size)]:
        struct.pack_into(fmt, blob, header + i * stride, 0, kind, 0, 0, off, size, link, 0, 1, ent)
    blob[offset:offset + len(strings)] = strings
    blob[symoff + symbol_size:symoff + 2 * symbol_size] = sym
    blob[dynoff:] = dyn
    return bytes(blob)


class ElfTests(unittest.TestCase):
    def test_imports(self):
        for bits in (32, 64):
            report = inspect_elf(elf_fixture(bits))
            self.assertEqual(report['bits'], bits)
            self.assertEqual(report['undefined_symbols'], ['ANativeWindow_lock'])
            self.assertEqual(report['needed'], ['libandroid.so'])

    def test_bad_header(self):
        for data in (b'', b'\x7fELF', b'not elf' * 20):
            with self.assertRaises(ElfError):
                inspect_elf(data)

    def test_bad_offsets(self):
        blob = bytearray(elf_fixture())
        struct.pack_into('<Q', blob, 40, 0xffffffff)
        with self.assertRaises(ElfError):
            inspect_elf(blob)

    def test_bad_string_index(self):
        blob = bytearray(elf_fixture())
        symoff, = struct.unpack_from('<Q', blob, 64 + 2 * 64 + 24)
        struct.pack_into('<I', blob, symoff + 24, 0xffffffff)
        with self.assertRaises(ElfError):
            inspect_elf(blob)
