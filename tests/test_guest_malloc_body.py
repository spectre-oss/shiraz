# SPDX-License-Identifier: GPL-3.0-only
"""Partial native branch inventory does not certify native effects."""
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

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
            self.assertEqual(row['direct_call_target_identities'][0]['kind'],
                             'unresolved-direct-target')
            self.assertFalse(row['direct_call_declared_identity_coverage'])
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

    def test_helper_call_identity_does_not_grant_transitive_effects(self):
        image = image_fixture(deltas=(0x1000, 0x20, 0x10))
        names = b'\0' + SYMBOL.encode() + b'\0_helper\0'
        struct.pack_into('<IIII', image, COMMAND - 24 + 8, 0x4000, 2, 0x4040, len(names))
        image[0x4040:0x4040+len(names)] = names
        struct.pack_into('<IBBHQ', image, 0x4010, len(SYMBOL.encode()) + 2, 0xf, 1, 0, 0x1020)
        words = (0x94000008, 0xd63f0100, 0xd65f03c0) + (0xd503201f,) * 5
        image[0x1000:0x1020] = struct.pack('<8I', *words)
        image[0x1020:0x1030] = struct.pack('<4I', 0x94000001, 0xd65f03c0,
                                        0xd503201f, 0xd503201f)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bridge'
            path.write_bytes(image)
            row = check_malloc_body(path)
            helper, = row['direct_call_target_identities']
            self.assertEqual(helper['names'], ('_helper',))
            self.assertEqual((helper['entry'], helper['end']), (0x1020, 0x1030))
            self.assertEqual(helper['known_branch_encodings'][0]['kind'], 'call')
            self.assertFalse(helper['semantics_verified'])
            self.assertTrue(row['direct_call_declared_identity_coverage'])
            self.assertFalse(row['transitive_calls_verified'])
            self.assertFalse(row['indirect_call_targets_verified'])
            self.assertFalse(row['native_memory_purity_verified'])
            for budget in ('MAX_DIRECT_TARGETS', 'MAX_TARGET_METADATA_VISITS'):
                with patch('check_guest_malloc_body.' + budget, 0), \
                        self.assertRaisesRegex(ValueError, 'budget'):
                    check_malloc_body(path)


if __name__ == '__main__':
    unittest.main()
