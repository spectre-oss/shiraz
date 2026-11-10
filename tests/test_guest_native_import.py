# SPDX-License-Identifier: GPL-3.0-only
"""Independent synthetic native import declarations, not live dyld binding."""
import dataclasses
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_native_import import read_native_import

ANCHOR = '_anchor'
TEXT = 32
STUB_SECTION = TEXT + 72 + 80
DATA = TEXT + 232
POINTER_SECTION = DATA + 72
LINK = DATA + 152
SYMTAB = LINK + 72
DYSYM = SYMTAB + 24


def name(value):
    return value.encode().ljust(16, b'\0')


def fixture(base=0, slot_offset=0x4000):
    text = struct.pack('<II16sQQQQiiII', 0x19, 232, name('__TEXT'), base, 0x4000, 0, 0x4000, 5, 5, 2, 0)
    text += struct.pack('<16s16sQQIIIIIIII', name('__text'), name('__TEXT'), base+0x1000, 16,
                        0x1000, 2, 0, 0, 0x80000400, 0, 0, 0)
    text += struct.pack('<16s16sQQIIIIIIII', name('__stubs'), name('__TEXT'), base+0x2000, 12,
                        0x2000, 2, 0, 0, 0x80000408, 0, 12, 0)
    data = struct.pack('<II16sQQQQiiII', 0x19, 152, name('__DATA'), base+0x4000, 0x1000,
                       0x4000, 0x1000, 3, 3, 1, 0)
    data += struct.pack('<16s16sQQIIIIIIII', name('__la_symbol_ptr'), name('__DATA'), base+0x4000,
                        0x100, 0x4000, 3, 0, 0, 7, 1, 0, 0)
    link = struct.pack('<II16sQQQQiiII', 0x19, 72, name('__LINKEDIT'), base+0x5000, 0x1000,
                       0x5000, 0x1000, 1, 1, 0, 0)
    names = b'\0_anchor\0_malloc\0'
    symtab = struct.pack('<6I', 2, 24, 0x5000, 2, 0x5020, len(names))
    dynamic = [0xb, 80] + [0]*18
    dynamic[14:16] = [0x5100, 33]
    commands = text + data + link + symtab + struct.pack('<20I', *dynamic)
    image = bytearray(0x6000)
    image[:32] = struct.pack('<IiiIIIII', 0xfeedfacf, 0x100000c, 0, 6, 5, len(commands), 0, 0)
    image[32:32+len(commands)] = commands
    image[0x1000:0x1010] = struct.pack('<4I', 0xd503201f, 0xd503201f, 0xd503201f, 0xd65f03c0)
    image[0x5000:0x5020] = struct.pack('<IBBHQIBBHQ', 1, 0xf, 1, 0, base+0x1000,
                                     9, 1, 0, 0x200, 0)
    image[0x5020:0x5020+len(names)] = names
    struct.pack_into('<33I', image, 0x5100, *([1]*33))
    target, slot = base+0x2000, base+slot_offset
    pages = ((slot & ~4095) - (target & ~4095)) // 4096
    encoded = pages & ((1 << 21)-1)
    adrp = 0x90000010 | (encoded & 3) << 29 | (encoded >> 2) << 5
    struct.pack_into('<III', image, 0x2000, adrp, 0xf9400210 | ((slot & 4095)//8) << 10, 0xd61f0200)
    return image


def add_overlapping_section(image, command, owner, address, length):
    size = struct.unpack_from('<I', image, command + 4)[0]
    total = 32 + struct.unpack_from('<I', image, 20)[0]
    insertion = command + size
    image[insertion+80:total+80] = bytes(image[insertion:total])
    image[insertion:insertion+80] = struct.pack(
        '<16s16sQQIIIIIIII', name('__overlap'), name(owner), address, length,
        address, 2, 0, 0, 0, 0, 0, 0)
    struct.pack_into('<I', image, command + 4, size + 80)
    count = struct.unpack_from('<I', image, command + 64)[0]
    struct.pack_into('<I', image, command + 64, count + 1)
    struct.pack_into('<I', image, 20, total + 80 - 32)


class NativeImportTests(unittest.TestCase):
    def read(self, image, target=0x2000):
        return read_native_import(image, ANCHOR, target)

    def test_exact_symbol_slot_and_immutable_bytes(self):
        for base in (0, 0x100000000):
            row = self.read(fixture(base), base+0x2000)
            self.assertEqual((row.symbol, row.library_ordinal, row.slot), ('_malloc', 2, base+0x4000))
            self.assertEqual(len(row.stub_body), 12)
            self.assertEqual(set(dataclasses.asdict(row)), {'symbol', 'library_ordinal', 'slot', 'stub_body'})
            with self.assertRaises(dataclasses.FrozenInstanceError):row.slot = 0
        image = fixture();row = self.read(image);image[0x2000] ^= 1
        self.assertNotEqual(row.stub_body, bytes(image[0x2000:0x200c]))

    def test_outside_stub_is_unresolved_but_interior_is_refused(self):
        for target in (0, 0x1000, 0x200c, 0x4000):self.assertIsNone(self.read(fixture(), target))
        for target in (0x2001, 0x2004, 0x2008):
            with self.subTest(target=target), self.assertRaises(ValueError):self.read(fixture(), target)

    def test_stub_instructions_are_exact(self):
        for offset in range(12):
            image = fixture();image[0x2000+offset] ^= 1
            # Some immediate-bit mutations name another valid pointer slot;
            # force that distinct slot's symbol index to disagree.
            struct.pack_into('<32I', image, 0x5104, *([0]*32))
            struct.pack_into('<I', image, 0x5104, 1)
            with self.subTest(offset=offset), self.assertRaises(ValueError):self.read(image)

    def test_ordinal_is_declaration_only_and_weak_ref_is_not_resolver(self):
        for description in (0, 0x100, 0xfe40, 0xff00):
            image = fixture();struct.pack_into('<H', image, 0x5016, description)
            self.assertEqual(self.read(image).library_ordinal, description >> 8)

    def test_indirect_metadata_bounds_ownership_and_symbol_agreement(self):
        for offset, value in ((DYSYM+4, 72), (DYSYM+56, 0x2000), (DYSYM+56, 0x5000),
                              (DYSYM+56, 0x5020), (DYSYM+60, 0), (DYSYM+60, 1_000_001),
                              (STUB_SECTION+68, 99), (POINTER_SECTION+68, 99),
                              (0x5100, 0x80000000), (0x5100, 2), (0x5104, 0)):
            image = fixture();struct.pack_into('<I', image, offset, value)
            with self.subTest(offset=offset, value=value), self.assertRaises(ValueError):self.read(image)
        image = fixture();struct.pack_into('<I', image, DYSYM, 0x31)
        with self.assertRaises(ValueError):self.read(image)
        image = fixture();image[DYSYM+80:DYSYM+160] = image[DYSYM:DYSYM+80]
        struct.pack_into('<II', image, 16, 6, DYSYM+160-32)
        with self.assertRaises(ValueError):self.read(image)

    def test_undefined_external_symbol_and_bounded_name_required(self):
        for offset, form, value in ((0x5014, '<B', 0xf), (0x5014, '<B', 0xe1),
                                    (0x5015, '<B', 1), (0x5018, '<Q', 1),
                                    (0x5010, '<I', 0), (0x5010, '<I', 100)):
            image = fixture();struct.pack_into(form, image, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):self.read(image)
        image = fixture();image[0x5030] = ord('x')
        with self.assertRaises(ValueError):self.read(image)

    def test_stub_and_slot_section_properties(self):
        for offset, value in ((STUB_SECTION+64, 0), (STUB_SECTION+72, 16),
                              (TEXT+60, 7), (POINTER_SECTION+64, 0),
                              (POINTER_SECTION+52, 2), (DATA+60, 5)):
            image = fixture();struct.pack_into('<I', image, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):self.read(image)
        with self.assertRaises(ValueError):self.read(fixture(slot_offset=0x4100))

    def test_signed_adrp_and_scaled_slot_offset(self):
        image = fixture(0x100000)
        struct.pack_into('<Q', image, DATA + 24, 0x8000)
        struct.pack_into('<Q', image, POINTER_SECTION + 32, 0x8000)
        target, slot = 0x102000, 0x8028
        encoded = ((slot // 4096 - target // 4096) & ((1 << 21)-1))
        adrp = 0x90000010 | (encoded & 3) << 29 | (encoded >> 2) << 5
        struct.pack_into('<I', image, 0x2000, adrp)
        struct.pack_into('<I', image, 0x2004, 0xf9400210 | 5 << 10)
        self.assertEqual(self.read(image, target).slot, slot)

    def test_inputs_and_address_bounds(self):
        for target in (True, -1, 1 << 64, None):
            with self.subTest(target=target), self.assertRaises(ValueError):self.read(fixture(), target)
        for image in (b'', fixture()[:32], fixture()[:0x5100]):
            with self.assertRaises(ValueError):self.read(image)
        image = fixture();struct.pack_into('<I', image, 0x2000, 0xd0fffff0)
        with self.assertRaises(ValueError):self.read(image)

    def test_partial_section_aliases_are_ambiguous(self):
        for command, owner, address, length in ((TEXT, '__TEXT', 0x2008, 4),
                                                (DATA, '__DATA', 0x4004, 4)):
            image = fixture()
            add_overlapping_section(image, command, owner, address, length)
            with self.subTest(address=address), self.assertRaises(ValueError):
                self.read(image)


if __name__ == '__main__':
    unittest.main()
