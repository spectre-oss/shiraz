# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic full-body profile checks, not execution or complete TLS proof."""

import hashlib
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_mrs_access import BODY_BYTES, TABLE_BYTES, recognize_mrs_access

# Observed compiled layout; relocations and four cold BL targets are encoded
# independently. Changing those targets never authenticates their handlers.
WORDS = (
    0xA9BF7BFD, 0x910003FD, 0xD53BD069, 0xB4000629, 0xD2800008,
    0, 0, 0, 0, 0, 0, 0, 0,
    0x14000004, 0x91002108, 0xF140211F, 0x54000460, 0x8B08014E,
    0xF8BFC1D0, 0xF1000A1F, 0x54FFFF43, 0xF868696E, 0xEB0901DF,
    0x54FFFEE1, 0xF868698F, 0xF86869AE, 0x8B080151, 0xF8BFC231,
    0xEB10023F, 0x54FFFE21, 0x8B080170, 0xF8BFC210, 0xEB09021F,
    0x54FFFDA1, 0xF2400CFF, 0x54000201, 0xEB0F00E9, 0x540001C3,
    0xEB0701CA, 0x54000189, 0xF140053F, 0x54000183, 0xF103FD5F,
    0x54000149, 0, 0, 0x8B080128, 0xF8BFC100, 0xB40000C0,
    0xA8C17BFD, 0xD65F03C0, 0, 0, 0, 0,
)
TABLES = (0x30280, 0x40280, 0x50280, 0x60280, 0x70280)
ACQUIRES = ((18, 14, 16), (27, 17, 17), (31, 16, 16), (47, 8, 0))


def fixture(entry=0x1FF8, tables=TABLES, failure_targets=None):
    words = list(WORDS)
    for index, register, address in zip((5, 7, 9, 11, 44), (10, 11, 12, 13, 9), tables):
        pages = ((address & ~4095) - ((entry + index * 4) & ~4095)) // 4096
        assert -(1 << 20) <= pages < 1 << 20
        encoded = pages & ((1 << 21) - 1)
        words[index] = 0x90000000 | (encoded & 3) << 29 | (encoded >> 2) << 5 | register
        words[index + 1] = 0x91000000 | (address & 4095) << 10 | register << 5 | register
    failure_targets = failure_targets or tuple(entry + 0x1000 + i * 16 for i in range(4))
    for index, target in zip(range(51, 55), failure_targets):
        displacement = target - (entry + index * 4)
        assert not displacement & 3 and -(1 << 27) <= displacement < 1 << 27
        words[index] = 0x94000000 | (displacement // 4 & 0x3FFFFFF)
    return words


class MrsAccessRecognitionTests(unittest.TestCase):
    def recognize(self, words=None, entry=0x1FF8, tables=TABLES, owned=None):
        words = fixture(entry, tables) if words is None else words
        memory = {entry + index * 4: word for index, word in enumerate(words)}
        if owned is None:
            owned = lambda address, width: address in tables and width == TABLE_BYTES
        return recognize_mrs_access(memory.get, entry, owned)

    def test_complete_profile_effects_are_explicit_and_frozen(self):
        words = fixture();result = self.recognize(words)
        self.assertIsNotNone(result)
        self.assertEqual(result.body_size, 220)
        self.assertEqual(result.rows, 4096)
        self.assertEqual(result.ret_pc, 0x1FF8 + 200)
        self.assertEqual(result.body_sha256, hashlib.sha256(
            b"".join(word.to_bytes(4, 'little') for word in words)).hexdigest())
        self.assertEqual([(table.role, table.address, table.byte_count) for table in result.tables],
                         list(zip(('owners', 'keys', 'low', 'high', 'bases'), TABLES, (32768,) * 5)))
        self.assertEqual(result.failure_calls, tuple((0x1FF8 + (51 + i) * 4,
                                                     0x1FF8 + 0x1000 + i * 16) for i in range(4)))
        self.assertFalse(result.failure_nonreturning_verified)
        self.assertEqual(result.normal_path_calls, 0)
        self.assertEqual((result.stack_write_offset, result.stack_write_bytes), (-16, 16))
        self.assertEqual((result.stack_read_offset, result.stack_read_bytes), (-16, 16))
        self.assertEqual((result.stack_bytes_before_original_sp, result.stack_bytes_after_original_sp), (4096, 256))
        self.assertEqual(result.native_register_read, 'TPIDRRO_EL0')
        self.assertEqual(result.original_sp_register, 'x7')
        self.assertEqual(result.normal_path_clobbers, ('x0', *(f'x{i}' for i in range(8, 18))))
        self.assertFalse(result.normal_path_preserves_nzcv or result.normal_path_writes_outside_stack_frame)
        self.assertTrue(result.normal_path_preserves_sp and result.normal_path_preserves_lr
                        and result.normal_path_preserves_vectors and result.normal_path_preserves_fpcr
                        and result.normal_path_preserves_fpsr and result.normal_path_preserves_unlisted_gprs)
        with self.assertRaises(FrozenInstanceError):result.rows = 1
        with self.assertRaises(FrozenInstanceError):result.tables[0].address = 0

    def test_each_bit_is_rejected_except_bounded_external_cold_call_relocation(self):
        words = fixture();entry = 0x1FF8
        for index in range(len(words)):
            for bit in range(32):
                changed = list(words);changed[index] ^= 1 << bit
                result = self.recognize(changed)
                if index in range(51, 55) and bit < 26:
                    immediate = changed[index] & 0x3FFFFFF
                    if immediate & 1 << 25:immediate -= 1 << 26
                    target = entry + index * 4 + immediate * 4
                    admitted = 0 <= target < 1 << 64 and not entry <= target < entry + BODY_BYTES
                    with self.subTest(index=index, bit=bit):
                        self.assertEqual(result is not None, admitted)
                        if admitted:
                            self.assertFalse(result.failure_nonreturning_verified)
                            self.assertEqual(result.failure_calls[index - 51][1], target)
                else:
                    with self.subTest(index=index, bit=bit):self.assertIsNone(result)

    def test_all_acquire_variants_and_mixed_forms_are_admitted(self):
        for mask in range(16):
            words = fixture()
            for bit, (index, base, target) in enumerate(ACQUIRES):
                if mask & 1 << bit:words[index] = 0xC8DFFC00 | base << 5 | target
            result = self.recognize(words)
            self.assertIsNotNone(result)
            self.assertEqual(result.normal_path_calls, 0)
            self.assertEqual(result.stack_write_bytes, 16)

    def test_signed_relocations_and_code_page_crossing(self):
        for entry, tables in ((0x1FF8, TABLES), (0x100000, TABLES),
                              (0x100000000, tuple(address + 0x100000000 for address in TABLES))):
            with self.subTest(entry=entry):
                result = self.recognize(entry=entry, tables=tables)
                self.assertIsNotNone(result)
                self.assertEqual(tuple(table.address for table in result.tables), tables)

    def test_all_words_required_and_typed_unsigned(self):
        for index in range(len(WORDS)):
            for bad in (None, True, -1, 1 << 32):
                words = fixture();words[index] = bad
                with self.subTest(index=index, bad=bad):self.assertIsNone(self.recognize(words))
        for size in range(len(WORDS)):
            self.assertIsNone(self.recognize(fixture()[:size]))

    def test_invalid_entry_is_refused_before_reads(self):
        def forbidden(_):self.fail('invalid entry must not read')
        for entry in (None, True, -4, 1, (1 << 64) - BODY_BYTES + 4, 1 << 64):
            self.assertIsNone(recognize_mrs_access(forbidden, entry, lambda *_: True))

    def test_all_five_complete_owned_nonoverlapping_tables_required(self):
        for table in TABLES:
            for verdict in (False, None, 1):
                with self.subTest(table=table, verdict=verdict):
                    self.assertIsNone(self.recognize(owned=lambda address, width, table=table, verdict=verdict:
                        verdict if address == table else True))
        seen = []
        self.assertIsNotNone(self.recognize(owned=lambda address, width: seen.append((address, width)) is None))
        self.assertEqual(seen, [(address, TABLE_BYTES) for address in TABLES])
        for tables in ((TABLES[0], TABLES[0], *TABLES[2:]),
                       (*TABLES[:4], TABLES[0]), (*TABLES[:4], TABLES[0] + TABLE_BYTES - 8),
                       (0x1FF8, *TABLES[1:]), (TABLES[0] + 1, *TABLES[1:])):
            self.assertIsNone(self.recognize(tables=tables, owned=lambda *_: True))
        entry = (1 << 64) - 0x20000
        tables = ((1 << 64) - 0x1000, *(entry - i * 0x10000 for i in range(3, 7)))
        self.assertIsNone(self.recognize(entry=entry, tables=tables, owned=lambda *_: True))

    def test_cold_calls_cannot_target_body_or_wrap(self):
        entry = 0x1FF8
        for target in range(entry, entry + BODY_BYTES, 4):
            words = fixture(failure_targets=(target, *(entry + 0x1000 + i * 16 for i in range(1, 4))))
            self.assertIsNone(self.recognize(words))
        words = fixture(entry=0, failure_targets=(-4, 0x1000, 0x1010, 0x1020))
        self.assertIsNone(self.recognize(words, entry=0))
        entry = (1 << 64) - 0x20000
        tables = tuple(entry - i * 0x10000 for i in range(1, 6))
        words = fixture(entry=entry, tables=tables,
                        failure_targets=(1 << 64, entry - 0x1000, entry - 0x1010, entry - 0x1020))
        self.assertIsNone(self.recognize(words, entry=entry, tables=tables))

    def test_normal_calls_tlv_writes_window_changes_or_wrong_native_register_refuse(self):
        replacements = {0: 0xA9BE7BFD, 2: 0xD53BD049, 14: 0x91004108,
                        15: 0xF140251F, 18: 0xF94001D0, 21: 0xF828696E,
                        24: 0x94000040, 27: 0xC8DFFE10, 34: 0xF24008FF,
                        40: 0xF140093F, 42: 0xF107FD5F, 47: 0xF9000100,
                        49: 0xA8C27BFD, 50: 0xD65F0200, 51: 0xD503201F}
        for index, replacement in replacements.items():
            words = fixture();words[index] = replacement
            with self.subTest(index=index):self.assertIsNone(self.recognize(words))


if __name__ == '__main__':
    unittest.main()
