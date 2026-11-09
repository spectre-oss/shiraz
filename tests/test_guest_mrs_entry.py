# SPDX-License-Identifier: GPL-3.0-only
"""Exact first-party entry bytes and negative controls; no execution/TLS proof."""

import hashlib
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_mrs_entry import BODY_BYTES, recognize_mrs_entry

# Independent observed reference layout; the test encodes its BL relocation.
WORDS = (
    0xD10D03FF, 0xA90007E0, 0xA9010FE2, 0xA90217E4, 0xA9031FE6, 0xA90427E8,
    0xA9052FEA, 0xA90637EC, 0xA9073FEE, 0xA9094FF2, 0xA90A57F4, 0xA90B5FF6,
    0xA90C67F8, 0xA90D6FFA, 0xA90E77FC, 0xF9007BFE, 0xF9018BF0, 0x910D03E9,
    0xA9402929, 0xA9082BE9, 0xD53B4209, 0xF9007FE9, 0xD53B4409, 0xF90083E9,
    0xD53B4429, 0xF90087E9, 0xAD0887E0, 0xAD098FE2, 0xAD0A97E4, 0xAD0B9FE6,
    0xAD0CA7E8, 0xAD0DAFEA, 0xAD0EB7EC, 0xAD0FBFEE, 0xAD10C7F0, 0xAD11CFF2,
    0xAD12D7F4, 0xAD13DFF6, 0xAD14E7F8, 0xAD15EFFA, 0xAD16F7FC, 0xAD17FFFE,
    0xB85F0209, 0x93406529, 0xD37EF529, 0xD100420A, 0x8B090149, 0xF9019BE9,
    0x910C83E9, 0xA900793D, 0x910C83FD, 0xF9418BF0, 0xB9400E09, 0x7100053F,
    0x540007E1, 0xAA1F03E0, 0xAA1F03E1, 0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4,
    0xAA1F03E5, 0xAA1F03E6, 0x910D43E7, 0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA,
    0xAA1F03EB, 0xAA1F03EC, 0xAA1F03ED, 0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0,
    0xAA1F03F1, 0xAA1F03F3, 0xAA1F03F4, 0xAA1F03F5, 0xAA1F03F6, 0xAA1F03F7,
    0xAA1F03F8, 0xAA1F03F9, 0xAA1F03FA, 0xAA1F03FB, 0xAA1F03FC, 0x6E201C00,
    0x6E211C21, 0x6E221C42, 0x6E231C63, 0x6E241C84, 0x6E251CA5, 0x6E261CC6,
    0x6E271CE7, 0x6E281D08, 0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B, 0x6E2C1D8C,
    0x6E2D1DAD, 0x6E2E1DCE, 0x6E2F1DEF, 0x6E301E10, 0x6E311E31, 0x6E321E52,
    0x6E331E73, 0x6E341E94, 0x6E351EB5, 0x6E361ED6, 0x6E371EF7, 0x6E381F18,
    0x6E391F39, 0x6E3A1F5A, 0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD, 0x6E3E1FDE,
    0x6E3F1FFF, 0x94000000, 0x14000005, 0xF9400A10, 0xAA0803E6, 0x910D43E7,
    0xD63F0200, 0xF9418BF0, 0xB9400A11, 0xF8317BE0, 0xA9482BE9, 0x910D03EB,
    0xA9002969, 0xF94083E9, 0xD51B4409, 0xF94087E9, 0xD51B4429, 0xF9407FE9,
    0xD51B4209, 0xAD4887E0, 0xAD498FE2, 0xAD4A97E4, 0xAD4B9FE6, 0xAD4CA7E8,
    0xAD4DAFEA, 0xAD4EB7EC, 0xAD4FBFEE, 0xAD50C7F0, 0xAD51CFF2, 0xAD52D7F4,
    0xAD53DFF6, 0xAD54E7F8, 0xAD55EFFA, 0xAD56F7FC, 0xAD57FFFE, 0xF9418BF0,
    0xF9400210, 0xA94007E0, 0xA9410FE2, 0xA94217E4, 0xA9431FE6, 0xA94427E8,
    0xA9452FEA, 0xA94637EC, 0xA9473FEE, 0xA9494FF2, 0xA94A57F4, 0xA94B5FF6,
    0xA94C67F8, 0xA94D6FFA, 0xA94E77FC, 0xF9407BFE, 0xF9419BF1, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xD61F0200,
)


def fixture(entry=0x1000, accessor=0x2000):
    words = list(WORDS)
    displacement = accessor - (entry + 115 * 4)
    assert displacement % 4 == 0 and -(1 << 27) <= displacement < 1 << 27
    words[115] = 0x94000000 | (displacement // 4 & 0x3FFFFFF)
    return words


class MrsEntryRecognitionTests(unittest.TestCase):
    def recognize(self, words=None, *, entry=0x1000, accessor=0x2000):
        words = fixture(entry, accessor) if words is None else words
        memory = {entry + index * 4: word for index, word in enumerate(words)}
        return recognize_mrs_entry(memory.get, entry, accessor)

    def test_exact_default_entry_effects_and_nonclaims_are_frozen(self):
        words = fixture();result = self.recognize(words)
        self.assertIsNotNone(result)
        self.assertEqual(result.body_size, 880)
        self.assertEqual(result.body_sha256, hashlib.sha256(
            b"".join(word.to_bytes(4, 'little') for word in words)).hexdigest())
        self.assertEqual((result.accessor_target, result.accessor_call_pc,
                          result.generic_call_pc, result.exit_branch_pc),
                         (0x2000, 0x1000 + 460, 0x1000 + 480, 0x1000 + 876))
        self.assertEqual(result.quarantined_gprs,
                         (*(f'x{i}' for i in range(7)), *(f'x{i}' for i in range(8, 18)),
                          *(f'x{i}' for i in range(19, 29))))
        self.assertEqual(result.quarantined_vectors, tuple(f'v{i}' for i in range(32)))
        self.assertEqual((result.original_sp_register, result.original_sp_from_shared_sp,
                          result.native_fp_from_shared_sp), ('x7', 848, 800))
        self.assertEqual((result.shared_stack_bytes, result.island_stack_bytes,
                          result.shared_clear_stores, result.shared_clear_interval_from_original_sp),
                         (832, 16, 52, (-848, -16)))
        self.assertTrue(result.shared_frame_cleared_on_normal_default_return
                        and result.shared_exit_preserves_incoming_sp and result.native_x18_preserved)
        self.assertTrue(result.requires_validated_default_context and result.requires_validated_return_island
                        and result.requires_island_restore_x16_x17)
        self.assertFalse(result.accessor_body_verified or result.generic_path_effects_verified
                         or result.context_ownership_verified or result.cache_ownership_verified
                         or result.stack_exclusion_verified or result.prewrite_safety_verified
                         or result.lifetime_verified or result.async_verified or result.unwind_verified
                         or result.consumer_proof)
        with self.assertRaises(FrozenInstanceError): result.shared_clear_stores = 51

    def test_every_single_bit_change_refuses_including_call_relocation(self):
        original = fixture()
        for index in range(len(original)):
            for bit in range(32):
                changed = list(original);changed[index] ^= 1 << bit
                with self.subTest(index=index, bit=bit):
                    self.assertIsNone(self.recognize(changed))

    def test_exact_signed_call_relocation_in_both_directions(self):
        entry = 0x20000000;pc = entry + 460
        for target in (pc - (1 << 27), pc + (1 << 27) - 4, entry - 4096, entry + 4096):
            with self.subTest(target=target):
                result = self.recognize(entry=entry, accessor=target)
                self.assertEqual(result.accessor_target, target)
        self.assertIsNotNone(self.recognize(entry=(1 << 64) - BODY_BYTES,
                                           accessor=(1 << 64) - BODY_BYTES - 4096))
        words = fixture(entry, entry + 4096)
        self.assertIsNone(self.recognize(words, entry=entry, accessor=entry + 4100))

    def test_missing_clear_early_clear_status_and_control_changes_refuse(self):
        original = fixture()
        for index, word in ((0, 0xD10CC3FF), (43, 0xD3406529), (54, 0x1400003F),
                            (55, 0xD503201F), (62, 0x910D03E7), (82, 0xD503201F),
                            (116, 0x14000004), (120, 0xD503201F), (123, 0xD503201F),
                            (128, 0xD503201F), (133, 0xA9007FFF), (166, 0xD503201F),
                            (167, 0xD503201F), (218, 0xD503201F), (219, 0xD65F03C0)):
            changed = list(original);changed[index] = word
            with self.subTest(index=index):self.assertIsNone(self.recognize(changed))
        self.assertIsNone(self.recognize(original[:167] + original[168:]))

    def test_all_truncations_invalid_words_and_exact_read_extent(self):
        words = fixture()
        for size in range(len(words)):
            with self.subTest(size=size):self.assertIsNone(self.recognize(words[:size]))
        for index in range(len(words)):
            for bad in (None, True, -1, 1 << 32):
                changed = list(words);changed[index] = bad
                with self.subTest(index=index, bad=bad):self.assertIsNone(self.recognize(changed))
        reads = []
        def read_word(address):
            reads.append(address)
            return words[(address - 0x1000) // 4]
        self.assertIsNotNone(recognize_mrs_entry(read_word, 0x1000, 0x2000))
        self.assertEqual(reads, list(range(0x1000, 0x1000 + BODY_BYTES, 4)))

    def test_invalid_context_addresses_refuse_before_any_read(self):
        def forbidden(_):
            self.fail('invalid arguments must not read code')
        for entry in (True, None, -4, 1, (1 << 64) - BODY_BYTES + 4, 1 << 64):
            with self.subTest(entry=entry):
                self.assertIsNone(recognize_mrs_entry(forbidden, entry, 0x2000))
        for accessor in (True, None, -4, 1, 1 << 64, 0x1000, 0x1004, 0x1000 + BODY_BYTES - 4):
            with self.subTest(accessor=accessor):
                self.assertIsNone(recognize_mrs_entry(forbidden, 0x1000, accessor))

    def test_decoded_call_target_must_not_wrap_uint64(self):
        high = (1 << 64) - BODY_BYTES
        words = fixture(high, high - 4096);words[115] = 0x94000100
        self.assertIsNone(self.recognize(words, entry=high, accessor=high - 4096))
        words = fixture(0, 0x2000);words[115] = 0x97FFF000
        self.assertIsNone(self.recognize(words, entry=0, accessor=0x2000))


if __name__ == '__main__':
    unittest.main()
