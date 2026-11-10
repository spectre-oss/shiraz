# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic query-profile controls; no runtime row or lifetime certificate."""

import hashlib
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_prepared_errno import (
    BODY_BYTES,
    BODY_WORDS,
    TABLE_BYTES,
    recognize_prepared_errno,
)

WORDS = (
    0xAA0003E8, 0xD2800000, 0xD53BD069, 0xF2400D1F, 0x54000741, 0xB4000728,
    0xB4000709, 0xD280000A, 0, 0, 0, 0,
    0x14000004, 0x9100214A, 0xF140215F, 0x540005C0, 0x8B0A018D, 0xF8BFC1AD,
    0xF10009BF, 0x54FFFF43, 0xF86A696E, 0xEB0901DF, 0x54FFFEE1, 0,
    0, 0xF86A69CF, 0, 0, 0xF86A69D0, 0xEB10011F,
    0xFA4F3100, 0xFA5021E2, 0x540003A2, 0, 0, 0x8B0A01C8,
    0xF8BFC108, 0xB4000308, 0xD2800000, 0xB100151F, 0x540002C8, 0x92400511,
    0xB5000291, 0xEB10011F, 0x54000082, 0x91001110, 0xEB1001FF, 0x540001C3,
    0x8B0A018C, 0xF8BFC18C, 0xEB0D019F, 0x54000141, 0x8B0A016B, 0xF8BFC16B,
    0xEB09017F, 0x540000C1, 0x8B0A01C9, 0xF8BFC129, 0xEB08013F, 0x9A9F0100,
    0xD65F03C0, 0xD2800000, 0xD65F03C0,
)
TABLES = (0x30280, 0x40280, 0x50280, 0x60280, 0x70280)
PAIRS = ((8, 12), (10, 11), (23, 14), (26, 14), (33, 14))
ACQUIRES = ((17, 13, 13), (36, 8, 8), (49, 12, 12), (53, 11, 11), (57, 9, 9))


def fixture(entry=0x1000, tables=TABLES):
    words = list(WORDS)
    for (index, register), address in zip(PAIRS, tables):
        pages = ((address & ~4095) - ((entry + index * 4) & ~4095)) // 4096
        assert -(1 << 20) <= pages < 1 << 20
        encoded = pages & ((1 << 21) - 1)
        words[index] = 0x90000000 | (encoded & 3) << 29 | (encoded >> 2) << 5 | register
        words[index+1] = 0x91000000 | (address & 4095) << 10 | register << 5 | register
    return words


class PreparedErrnoRecognitionTests(unittest.TestCase):
    def recognize(self, words=None, entry=0x1000, tables=TABLES, owned=None):
        words = fixture(entry, tables) if words is None else words
        memory = {entry + index*4: word for index, word in enumerate(words)}
        if owned is None:
            owned = lambda address, size: address in tables and size == TABLE_BYTES
        return recognize_prepared_errno(memory.get, entry, owned)

    def test_complete_effects_are_conditional_explicit_and_frozen(self):
        words = fixture();result = self.recognize(words)
        self.assertIsNotNone(result)
        self.assertEqual((result.body_size, result.rows), (252, 4096))
        self.assertEqual(result.body_sha256, hashlib.sha256(
            b''.join(word.to_bytes(4, 'little') for word in words)).hexdigest())
        self.assertEqual(result.ret_pcs, (0x1000+240, 0x1000+248))
        self.assertEqual([(t.role, t.address, t.byte_count) for t in result.tables],
                         list(zip(('owners', 'keys', 'low', 'high', 'errno-cells'), TABLES, (32768,)*5)))
        self.assertEqual((result.normal_path_calls, result.stack_read_bytes, result.stack_write_bytes), (0, 0, 0))
        self.assertEqual(result.native_register_read, 'TPIDRRO_EL0')
        self.assertEqual(result.incoming_sp_register, 'x0')
        self.assertEqual(result.normal_path_clobbers, ('x0', *(f'x{i}' for i in range(8, 18))))
        self.assertTrue(result.pointer_result and result.normal_path_preserves_sp
                        and result.normal_path_preserves_lr and result.normal_path_preserves_unlisted_gprs
                        and result.normal_path_preserves_vectors and result.normal_path_preserves_fpcr
                        and result.normal_path_preserves_fpsr)
        self.assertFalse(result.normal_path_preserves_nzcv or result.normal_path_writes_memory)
        self.assertEqual((result.returned_cell_read_bytes, result.returned_cell_write_bytes), (0, 0))
        self.assertEqual((result.nonnull_result_cell_alignment, result.nonnull_result_cell_bytes), (4, 4))
        self.assertTrue(result.nonnull_result_disjoint_row_interval)
        self.assertTrue(result.requires_matching_runtime_tables and result.requires_metadata_row_contract
                        and result.return_scratch_may_contain_table_values)
        for name in ('table_identity_verified', 'metadata_publication_verified',
                     'table_stack_exclusion_verified', 'caller_inputs_verified', 'lifetime_verified',
                     'async_verified', 'unwind_verified', 'native_memory_purity_verified', 'consumer_proof',
                     'returned_cell_readability_verified', 'returned_cell_writability_verified'):
            self.assertIs(getattr(result, name), False)
        with self.assertRaises(FrozenInstanceError):result.rows = 1
        with self.assertRaises(FrozenInstanceError):result.tables[0].address = 0

    def test_every_single_bit_mutation_is_refused_with_exact_ownership(self):
        words = fixture()
        for index in range(BODY_WORDS):
            for bit in range(32):
                changed = list(words);changed[index] ^= 1 << bit
                with self.subTest(index=index, bit=bit):self.assertIsNone(self.recognize(changed))

    def test_exact_acquire_variants_and_mixed_forms(self):
        for mask in range(32):
            words = fixture()
            for bit, (index, base, target) in enumerate(ACQUIRES):
                if mask & 1 << bit:words[index] = 0xC8DFFC00 | base << 5 | target
            result = self.recognize(words)
            self.assertIsNotNone(result)
            self.assertFalse(result.table_identity_verified or result.lifetime_verified)

    def test_signed_page_relocations_and_page_crossing(self):
        for entry, tables in ((0x1FF8, TABLES), (0x100000, TABLES),
                              (0x100000000, tuple(address+0x100000000 for address in TABLES))):
            with self.subTest(entry=entry):
                result = self.recognize(entry=entry, tables=tables)
                self.assertIsNotNone(result)
                self.assertEqual(tuple(t.address for t in result.tables), tables)

    def test_all_words_are_required_and_typed_unsigned(self):
        for index in range(BODY_WORDS):
            for bad in (None, True, -1, 1 << 32):
                words = fixture();words[index] = bad
                with self.subTest(index=index, bad=bad):self.assertIsNone(self.recognize(words))
        for size in range(BODY_WORDS):self.assertIsNone(self.recognize(fixture()[:size]))

    def test_invalid_entry_does_not_read(self):
        def forbidden(_):self.fail('invalid entry must not read')
        for entry in (None, True, -4, 1, (1 << 64)-BODY_BYTES+4, 1 << 64):
            self.assertIsNone(recognize_prepared_errno(forbidden, entry, lambda *_: True))

    def test_complete_owned_disjoint_table_spans_are_required(self):
        for table in TABLES:
            for verdict in (False, None, 1):
                self.assertIsNone(self.recognize(owned=lambda address, size, table=table, verdict=verdict:
                    verdict if address == table else True))
        seen = []
        self.assertIsNotNone(self.recognize(owned=lambda address, size: seen.append((address, size)) is None))
        self.assertEqual(seen, [(address, TABLE_BYTES) for address in TABLES])
        for tables in ((TABLES[0], TABLES[0], *TABLES[2:]),
                       (*TABLES[:4], TABLES[0]+TABLE_BYTES-8),
                       (0x1000, *TABLES[1:]), (TABLES[0]+1, *TABLES[1:]), (-8, *TABLES[1:])):
            self.assertIsNone(self.recognize(tables=tables, owned=lambda *_: True))
        entry = (1 << 64)-0x20000
        tables = ((1 << 64)-0x1000, *(entry-i*0x10000 for i in range(3, 7)))
        self.assertIsNone(self.recognize(entry=entry, tables=tables, owned=lambda *_: True))

    def test_ownership_does_not_authenticate_runtime_table_roles(self):
        # The substituted span could be guest-base storage. Readable ownership
        # alone must never become a no-TP, publication or content certificate.
        tables = (*TABLES[:4], 0x80280)
        result = self.recognize(tables=tables, owned=lambda *_: True)
        self.assertIsNotNone(result)
        self.assertEqual(result.tables[-1].address, 0x80280)
        self.assertTrue(result.requires_matching_runtime_tables)
        self.assertFalse(result.table_identity_verified or result.native_memory_purity_verified
                         or result.consumer_proof)

    def test_calls_tlv_stores_loop_changes_bounds_and_rechecks_refuse(self):
        replacements = {2: 0xD53BD049, 3: 0xF240091F, 4: 0x54000721,
                        13: 0x9100414A, 14: 0xF140255F, 17: 0xF94001AD,
                        25: 0x94000040, 29: 0xEB00001F, 30: 0xD503201F,
                        31: 0xD503201F, 36: 0xF9000108, 37: 0xD503201F,
                        39: 0xB100111F, 41: 0x92400111, 42: 0xD503201F,
                        44: 0xD503201F, 45: 0x91002110, 47: 0xD503201F,
                        49: 0xF940018C, 53: 0xF940016B, 57: 0xF9400129,
                        59: 0xAA0803E0, 60: 0xD65F0200, 62: 0xD503201F}
        for index, word in replacements.items():
            words = fixture();words[index] = word
            with self.subTest(index=index):self.assertIsNone(self.recognize(words))


if __name__ == '__main__':
    unittest.main()
