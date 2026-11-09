# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic paired-symbol file checks; no execution or complete TLS proof."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_guest_mrs_entry import check_entry
from test_guest_cached_self_record import fixture as macho_fixture
from test_guest_mrs_access import fixture as getter_fixture
from test_guest_mrs_entry import fixture as entry_fixture


def image_fixture(base=0):
    image, _ = macho_fixture(base)
    struct.pack_into('<Q', image, 32 + 72 + 40, 0x1100)  # __text size
    struct.pack_into('<Q', image, 32 + 152 + 32, 0x78000)  # DATA vmsize
    struct.pack_into('<Q', image, 32 + 152 + 72 + 24, base + 0x90000)  # LINKEDIT vmaddr
    entry, getter = base + 0x1000, base + 0x2000
    image[0x1000:0x1000+880] = struct.pack('<220I', *entry_fixture(entry, getter))
    tables = tuple(base + address for address in (0x30280, 0x40280, 0x50280, 0x60280, 0x70280))
    image[0x2000:0x2000+220] = struct.pack('<55I', *getter_fixture(getter, tables))
    entry_name = b'_shiraz_guest_tpidr_entry\0';getter_name = b'_shiraz_guest_tls_access\0'
    strings = b'\0' + entry_name + getter_name
    symtab = 32 + 152 + 72 + 72
    struct.pack_into('<IIII', image, symtab + 8, 0x4000, 2, 0x4020, len(strings))
    image[0x4000:0x4020] = (struct.pack('<IBBHQ', 1, 0xF, 1, 0, entry)
                            + struct.pack('<IBBHQ', 1 + len(entry_name), 0xF, 1, 0, getter))
    image[0x4020:0x4020+len(strings)] = strings
    return image


class MrsEntryFileTests(unittest.TestCase):
    def check(self, image):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bridge';path.write_bytes(image)
            return check_entry(path)

    def test_same_image_symbols_bind_entry_call_to_the_checked_getter(self):
        for base in (0, 0x100000000):
            image = image_fixture(base);row = self.check(image)
            self.assertEqual(row['image_sha256'], hashlib.sha256(image).hexdigest())
            self.assertEqual(row['entry']['accessor_target'], row['accessor']['entry'])
            self.assertEqual(row['entry']['shared_stack_bytes'], 832)
            self.assertEqual(len(row['accessor']['tables']), 5)
            for key in ('execution_claim', 'consumer_proof', 'islands_verified',
                        'runtime_publication_verified', 'table_stack_exclusion_verified',
                        'unwind_metadata_verified'):
                self.assertIs(row[key], False)

    def test_call_body_metadata_and_ownership_tampering_refuse(self):
        original = image_fixture()
        for offset, value in ((0x1000+115*4, 0), (0x1000+167*4, 0), (0x2000+8, 0),
                              (32+152+60, 0), (0x4021, ord('x'))):
            changed = bytearray(original);changed[offset] = value
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                self.check(changed)
        overlap = bytearray(original);struct.pack_into('<Q', overlap, 0x4010+8, 0x1000)
        with self.assertRaisesRegex(ValueError, 'overlap'):
            self.check(overlap)

    def test_missing_getter_or_truncated_input_cannot_pass(self):
        original = image_fixture()
        for image in (b'', original[:32], original[:0x4000]):
            with self.subTest(length=len(image)), self.assertRaises(ValueError):
                self.check(image)
        missing = bytearray(original)
        struct.pack_into('<B', missing, 0x4010+4, 1)
        with self.assertRaises(ValueError):
            self.check(missing)


if __name__ == '__main__':
    unittest.main()
