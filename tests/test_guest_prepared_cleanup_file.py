# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic file read-set checks, without runtime table identity claims."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_guest_prepared_cleanup import SYMBOL, check_prepared_cleanup
from test_guest_mrs_entry_file import image_fixture as macho_fixture
from test_guest_prepared_cleanup import fixture


def image_fixture(base=0, owner_delta=0):
    image = macho_fixture(base)
    tables = tuple(base+value for value in (0x20000+owner_delta, 0x30000, 0x40000, 0x50000, 0x60000))
    words = fixture(base+0x1000, tables)
    image[0x1000:0x1000+len(words)*4] = struct.pack(f'<{len(words)}I', *words)
    names = b'\0'+SYMBOL.encode()+b'\0'
    image[0x4020:0x4020+len(names)] = names
    struct.pack_into('<IIII', image, 32+152+72+72+8, 0x4000, 1, 0x4020, len(names))
    return image


class PreparedCleanupFileTests(unittest.TestCase):
    def check(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'bridge';path.write_bytes(data)
            return check_prepared_cleanup(path)

    def test_file_ownership_and_nonclaims(self):
        for base in (0, 0x100000000):
            data = image_fixture(base);row = self.check(data)
            self.assertEqual(row['image_sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(len(row['effects']['tables']), 5)
            for key in ('execution_claim', 'consumer_proof', 'table_identity_verified',
                        'publication_verified', 'stack_exclusion_verified',
                        'caller_guard_verified', 'native_memory_purity_verified'):
                self.assertIs(row[key], False)
            self.assertTrue(row['effects']['requires_matching_runtime_tables'])

    def test_other_readable_storage_does_not_authenticate_runtime_identity(self):
        # Readable segment membership supplies no array-role identity. Even a
        # structurally accepted shifted span must retain this explicit gap.
        row = self.check(image_fixture(owner_delta=8))
        self.assertEqual(row['effects']['tables'][0]['address'], 0x20008)
        self.assertFalse(row['table_identity_verified'])
        self.assertFalse(row['native_memory_purity_verified'])

    def test_code_symbol_and_data_permissions_tampering_refuse(self):
        original = image_fixture()
        for offset in (0x1000, 0x1000+14*4, 0x4021):
            data = bytearray(original);data[offset] ^= 1
            with self.subTest(offset=offset), self.assertRaises(ValueError):self.check(data)
        data = bytearray(original);struct.pack_into('<I', data, 32+152+60, 0)
        with self.assertRaises(ValueError):self.check(data)
        data = bytearray(original);data[0x4004] = 0
        with self.assertRaises(ValueError):self.check(data)

    def test_empty_and_truncated_files_cannot_pass(self):
        original = image_fixture()
        for data in (b'', original[:32], original[:0x4000]):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):self.check(data)


if __name__ == '__main__':
    unittest.main()
