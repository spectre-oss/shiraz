# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic query-profile controls; no runtime row or lifetime certificate."""

import hashlib
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_prepared_cleanup import (
    BODY_BYTES,
    BODY_WORDS,
    TABLE_BYTES,
    recognize_prepared_cleanup,
)

WORDS = (
    0x52800008, 0xD53BD069, 0xF2400C1F, 0x54000621, 0xB4000600, 0xB40005E9,
    0xD2800008, 0, 0, 0, 0, 0x14000004,
    0x91002108, 0xF140211F, 0x540004A0, 0x8B08016C, 0xF8BFC18C, 0xF100099F,
    0x54FFFF43, 0xF868694D, 0xEB0901BF, 0x54FFFEE1, 0, 0,
    0xF86869AD, 0, 0, 0xF86869CE, 0xEB0E001F, 0xFA4D3000,
    0xFA4E21A2, 0x54000282, 0, 0, 0x8B0801AE, 0xF8BFC1CE,
    0xF10005DF, 0x540001C1, 0x8B08016B, 0xF8BFC16B, 0xEB0C017F, 0x54000141,
    0x8B08014A, 0xF8BFC14A, 0xEB09015F, 0x540000C1, 0x8B0801A8, 0xF8BFC108,
    0xF100051F, 0x1A9F17E0, 0xD65F03C0, 0x52800008, 0xAA0803E0, 0xD65F03C0,
)
TABLES = (0x30280, 0x40280, 0x50280, 0x60280, 0x70280)
PAIRS = ((7, 11), (9, 10), (22, 13), (25, 14), (32, 13))
ACQUIRES = ((16, 12, 12), (35, 14, 14), (39, 11, 11), (43, 10, 10), (47, 8, 8))


def fixture(entry=0x1000, tables=TABLES):
    words = list(WORDS)
    for (index, register), address in zip(PAIRS, tables):
        pages = ((address & ~4095) - ((entry + index * 4) & ~4095)) // 4096
        assert -(1 << 20) <= pages < 1 << 20
        encoded = pages & ((1 << 21) - 1)
        words[index] = 0x90000000 | (encoded & 3) << 29 | (encoded >> 2) << 5 | register
        words[index+1] = 0x91000000 | (address & 4095) << 10 | register << 5 | register
    return words


class PreparedCleanupRecognitionTests(unittest.TestCase):
    def recognize(self, words=None, entry=0x1000, tables=TABLES, owned=None):
        words = fixture(entry, tables) if words is None else words
        memory = {entry + index*4: word for index, word in enumerate(words)}
        if owned is None:
            owned = lambda address, size: address in tables and size == TABLE_BYTES
        return recognize_prepared_cleanup(memory.get, entry, owned)

    def test_complete_effects_are_conditional_explicit_and_frozen(self):
        words = fixture();result = self.recognize(words)
        self.assertIsNotNone(result)
        self.assertEqual((result.body_size, result.rows), (216, 4096))
        self.assertEqual(result.body_sha256, hashlib.sha256(
            b''.join(word.to_bytes(4, 'little') for word in words)).hexdigest())
        self.assertEqual(result.ret_pcs, (0x1000+200, 0x1000+212))
        self.assertEqual([(t.role, t.address, t.byte_count) for t in result.tables],
                         list(zip(('owners', 'keys', 'low', 'high', 'admission'), TABLES, (32768,)*5)))
        self.assertEqual((result.normal_path_calls, result.stack_read_bytes, result.stack_write_bytes), (0, 0, 0))
        self.assertEqual(result.native_register_read, 'TPIDRRO_EL0')
        self.assertEqual(result.incoming_sp_register, 'x0')
        self.assertEqual(result.normal_path_clobbers, ('x0', *(f'x{i}' for i in range(8, 15))))
        self.assertTrue(result.boolean_result and result.normal_path_preserves_sp
                        and result.normal_path_preserves_lr and result.normal_path_preserves_unlisted_gprs
                        and result.normal_path_preserves_vectors and result.normal_path_preserves_fpcr
                        and result.normal_path_preserves_fpsr)
        self.assertFalse(result.normal_path_preserves_nzcv or result.normal_path_writes_memory)
        self.assertTrue(result.requires_matching_runtime_tables and result.requires_metadata_row_contract
                        and result.return_scratch_may_contain_table_values)
        for name in ('table_identity_verified', 'metadata_publication_verified',
                     'table_stack_exclusion_verified', 'caller_inputs_verified', 'lifetime_verified',
                     'async_verified', 'unwind_verified', 'native_memory_purity_verified', 'consumer_proof'):
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
            self.assertIsNone(recognize_prepared_cleanup(forbidden, entry, lambda *_: True))

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
        replacements = {1: 0xD53BD049, 2: 0xF240081F, 3: 0x54000601,
                        12: 0x91004108, 13: 0xF140251F, 16: 0xF940018C,
                        24: 0x94000040, 28: 0xEB00001F, 29: 0xD503201F,
                        30: 0xD503201F, 35: 0xF90001CE, 36: 0xF10009DF,
                        39: 0xF940016B, 43: 0xF940014A, 47: 0xF9400108,
                        49: 0x1A9F07E0, 50: 0xD65F0200, 53: 0xD503201F}
        for index, word in replacements.items():
            words = fixture();words[index] = word
            with self.subTest(index=index):self.assertIsNone(self.recognize(words))


if __name__ == '__main__':
    unittest.main()
