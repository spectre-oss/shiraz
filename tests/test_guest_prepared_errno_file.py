# SPDX-License-Identifier: GPL-3.0-only
"""Bounded compiled query checks; no live errno ownership/lifetime proof."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_guest_prepared_errno import SYMBOL, check_prepared_errno
from guest_prepared_errno import BODY_BYTES
from test_guest_native_function import COMMAND
from test_guest_native_function import image_fixture as native_fixture
from test_guest_prepared_errno import fixture


def image_fixture(base=0):
    image = native_fixture(base, deltas=(0x1000, BODY_BYTES))
    struct.pack_into('<Q', image, 32 + 72 + 40, 0x200)
    tables = tuple(base + offset for offset in (0x10000, 0x18000, 0x20000, 0x28000, 0x30000))
    words = fixture(base + 0x1000, tables)
    image[0x1000:0x1000+BODY_BYTES] = struct.pack('<' + 'I' * len(words), *words)
    names = b'\0' + SYMBOL.encode() + b'\0'
    image[0x4010:0x4010+len(names)] = names
    struct.pack_into('<I', image, COMMAND - 4, len(names))
    return image


class PreparedErrnoFileTests(unittest.TestCase):
    def check(self, image):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bridge'
            path.write_bytes(image)
            return check_prepared_errno(path)

    def test_complete_file_extent_and_explicit_nonclaims(self):
        for base in (0, 0x100000000):
            image = image_fixture(base)
            row = self.check(image)
            self.assertEqual(row['image_sha256'], hashlib.sha256(image).hexdigest())
            self.assertTrue(row['symbol_to_next_start_extent_verified'])
            self.assertEqual(len(row['effects']['tables']), 5)
            for key in ('table_identity_verified', 'publication_verified', 'cell_ownership_verified',
                        'cell_lifetime_verified', 'stack_exclusion_verified', 'execution_claim',
                        'native_memory_purity_verified', 'consumer_proof'):
                self.assertIs(row[key], False)

    def test_mutated_code_symbol_or_table_ownership_refused(self):
        for offset in (0x1000, 0x1000+BODY_BYTES-4, 0x4011):
            image = image_fixture(); image[offset] ^= 1
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                self.check(image)
        image = image_fixture(); struct.pack_into('<I', image, 32 + 152 + 60, 0)
        with self.assertRaises(ValueError):self.check(image)
        image = image_fixture(); struct.pack_into('<Q', image, 0x4008, 0x1004)
        with self.assertRaises(ValueError):self.check(image)

    def test_next_start_is_independent_of_the_expected_instruction_count(self):
        image = image_fixture()
        # A different next independent start cannot be hidden by matching the
        # expected prefix. Change its canonical ULEB delta from252 to248.
        image[0x4102] = 248
        with self.assertRaises(ValueError):self.check(image)
        for image in (b'', image_fixture()[:32], image_fixture()[:0x4000]):
            with self.assertRaises(ValueError):self.check(image)


if __name__ == '__main__':
    unittest.main()
