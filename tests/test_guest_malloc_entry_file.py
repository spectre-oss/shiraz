# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic allocator symbol-pair checks, not native allocator semantics."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_guest_malloc_entry import check_malloc_entry
from guest_malloc_entry import BODY_WORDS
from test_guest_malloc_entry import fixture
from test_guest_mrs_entry_file import image_fixture as macho_fixture


def image_fixture(base=0):
    image = macho_fixture(base)
    words = fixture(base+0x1000, base+0x2000)
    image[0x1000:0x1000+len(words)*4] = struct.pack(f'<{len(words)}I', *words)
    struct.pack_into('<I', image, 0x2000, 0xD503201F)  # Opaque native body.
    names = b'\0_shiraz_guest_managed_malloc_entry\0_shiraz_guest_managed_malloc_body\0'
    image[0x4020:0x4020+len(names)] = names
    struct.pack_into('<I', image, 32+152+72+72+20, len(names))
    struct.pack_into('<I', image, 0x4010, 1+len(b'_shiraz_guest_managed_malloc_entry\0'))
    return image


class MallocEntryFileTests(unittest.TestCase):
    def check(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'bridge'
            path.write_bytes(data)
            return check_malloc_entry(path)

    def test_call_identity_is_bound_to_same_image_symbol(self):
        for base in (0, 0x100000000):
            data = image_fixture(base)
            row = self.check(data)
            self.assertEqual(row['image_sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(row['native_body']['entry'], base+0x2000)
            for key in ('execution_claim', 'consumer_proof', 'caller_inputs_verified',
                        'runtime_publication_verified', 'unwind_metadata_verified',
                        'native_memory_purity_verified'):
                self.assertIs(row[key], False)
            self.assertIs(row['native_body']['body_semantics_verified'], False)

    def test_call_clear_and_symbol_tampering_cannot_pass(self):
        original = image_fixture()
        for offset in (0x1000+72*4, 0x1000+(BODY_WORDS-11)*4, 0x4021):
            image = bytearray(original);image[offset] ^= 1
            with self.subTest(offset=offset), self.assertRaises(ValueError):self.check(image)
        overlap = bytearray(original);struct.pack_into('<Q', overlap, 0x4018, 0x1000)
        with self.assertRaises(ValueError):self.check(overlap)
        missing = bytearray(original);struct.pack_into('<I', missing, 32+152+72+72+12, 1)
        with self.assertRaises(ValueError):self.check(missing)

    def test_native_body_bytes_are_explicitly_outside_the_certificate(self):
        image = image_fixture();struct.pack_into('<I', image, 0x2000, 0xD4200000)
        row = self.check(image)
        self.assertFalse(row['native_body']['body_semantics_verified'])
        self.assertFalse(row['native_memory_purity_verified'])

    def test_absent_or_truncated_image_is_refused(self):
        original = image_fixture()
        for data in (b'', original[:32], original[:0x4000]):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):self.check(data)


if __name__ == '__main__':
    unittest.main()
