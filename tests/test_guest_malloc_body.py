# SPDX-License-Identifier: GPL-3.0-only
"""Partial native branch inventory does not certify native effects."""
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_guest_malloc_body import SYMBOL, branch_inventory, check_malloc_body
from test_guest_native_function import COMMAND, image_fixture


class MallocBodyInventoryTests(unittest.TestCase):
    def scan(self, *words, entry=0x1000):
        return branch_inventory(struct.pack('<' + 'I' * len(words), *words), entry)

    def test_direct_call_and_branch_signed_offsets(self):
        rows = self.scan(0x94000002, 0x17ffffff, 0xd65f03c0)
        self.assertEqual([row['kind'] for row in rows], ['call', 'branch', 'return'])
        self.assertEqual(rows[0]['target'], 0x1008)
        self.assertEqual(rows[1]['target'], 0x1000)
        self.assertTrue(rows[0]['within_selected_body'])
        self.assertEqual(rows[2]['register'], 30)
        self.assertIsNone(rows[2]['target'])

    def test_conditional_compare_test_and_indirect_sites(self):
        rows = self.scan(0x54000040, 0x34ffffe0, 0xb4000040,
                         0x36000040, 0xb7ffffe0, 0xd63f0100, 0xd61f0120)
        self.assertEqual([row['kind'] for row in rows],
                         ['conditional', 'compare', 'compare', 'test', 'test',
                          'indirect-call', 'indirect-branch'])
        self.assertEqual([row['target'] for row in rows[:5]],
                         [0x1008, 0x1000, 0x1010, 0x1014, 0x100c])
        self.assertEqual(rows[5]['register'], 8)
        self.assertEqual(rows[6]['register'], 9)

    def test_external_target_remains_external(self):
        row, = self.scan(0x94000002)
        self.assertFalse(row['within_selected_body'])
        self.assertEqual(row['target'], 0x1008)

    def test_unknown_words_are_not_misrepresented_as_effect_certificates(self):
        # NOP, an unknown word and an authenticated return are not handled by
        # this intentionally partial encoding inventory. No safety follows.
        self.assertEqual(self.scan(0xd503201f, 0xffffffff, 0xd65f0bff), ())

    def test_bounds_and_signed_target_overflow(self):
        for body, entry in ((b'', 0), (b'123', 0), (b'1234', True),
                            (b'1234', -4), (b'1234', 2),
                            (b'1234', 1 << 64), (b'0' * 65540, 0),
                            (bytearray(4), 0)):
            with self.subTest(entry=entry, length=len(body)), self.assertRaises(ValueError):
                branch_inventory(body, entry)
        with self.assertRaises(ValueError):
            self.scan(0x17ffffff, entry=0)
        with self.assertRaises(ValueError):
            self.scan(0x14000002, entry=(1 << 64) - 4)

    def test_file_extent_and_contents_are_independently_bound(self):
        image = image_fixture()
        strings = b'\0' + SYMBOL.encode() + b'\0'
        image[0x4010:0x4010+len(strings)] = strings
        struct.pack_into('<I', image, COMMAND - 4, len(strings))
        image[0x1000:0x1010] = struct.pack('<4I', 0x94000002, 0xd63f0100,
                                       0xd503201f, 0xd65f03c0)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bridge'
            path.write_bytes(image)
            row = check_malloc_body(path)
            self.assertEqual((row['entry'], row['end'], row['body_bytes']),
                             (0x1000, 0x1010, 16))
            self.assertEqual(len(row['known_branch_encodings']), 3)
            self.assertTrue(row['symbol_to_next_start_extent_verified'])
            for key in ('complete_control_flow_verified', 'memory_effects_verified',
                        'transitive_calls_verified', 'exception_edges_verified',
                        'execution_claim', 'native_memory_purity_verified', 'consumer_proof',
                        'exclusive_instruction_provenance_verified'):
                self.assertIs(row[key], False)
            image[0x1000] ^= 1
            path.write_bytes(image)
            self.assertNotEqual(row['body_sha256'], check_malloc_body(path)['body_sha256'])
            image[COMMAND] = 0
            path.write_bytes(image)
            with self.assertRaises(ValueError):
                check_malloc_body(path)


if __name__ == '__main__':
    unittest.main()
