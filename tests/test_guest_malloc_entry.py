# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic exact-code controls; no native execution or complete TLS proof."""
import dataclasses
import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_malloc_entry import (
    BODY_BYTES,
    BODY_CALL_INDEX,
    BODY_WORDS,
    recognize_malloc_entry,
)

_WORDS = (
    0xD10283FF, 0xA90053F3, 0xA9015BF5, 0xA90263F7, 0xA9036BF9, 0xA90473FB,
    0xA9057BFD, 0x6D0627E8, 0x6D072FEA, 0x6D0837EC, 0x6D093FEE, 0xAA1F03E1,
    0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4, 0xAA1F03E5, 0xAA1F03E6, 0xAA1F03E7,
    0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA, 0xAA1F03EB, 0xAA1F03EC, 0xAA1F03ED,
    0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0, 0xAA1F03F1, 0xAA1F03F3, 0xAA1F03F4,
    0xAA1F03F5, 0xAA1F03F6, 0xAA1F03F7, 0xAA1F03F8, 0xAA1F03F9, 0xAA1F03FA,
    0xAA1F03FB, 0xAA1F03FC, 0xAA1F03FD, 0x6E201C00, 0x6E211C21, 0x6E221C42,
    0x6E231C63, 0x6E241C84, 0x6E251CA5, 0x6E261CC6, 0x6E271CE7, 0x6E281D08,
    0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B, 0x6E2C1D8C, 0x6E2D1DAD, 0x6E2E1DCE,
    0x6E2F1DEF, 0x6E301E10, 0x6E311E31, 0x6E321E52, 0x6E331E73, 0x6E341E94,
    0x6E351EB5, 0x6E361ED6, 0x6E371EF7, 0x6E381F18, 0x6E391F39, 0x6E3A1F5A,
    0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD, 0x6E3E1FDE, 0x6E3F1FFF, 0x910283E1,
    0x94000000, 0xAA1F03E1, 0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4, 0xAA1F03E5,
    0xAA1F03E6, 0xAA1F03E7, 0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA, 0xAA1F03EB,
    0xAA1F03EC, 0xAA1F03ED, 0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0, 0xAA1F03F1,
    0x6E201C00, 0x6E211C21, 0x6E221C42, 0x6E231C63, 0x6E241C84, 0x6E251CA5,
    0x6E261CC6, 0x6E271CE7, 0x6E281D08, 0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B,
    0x6E2C1D8C, 0x6E2D1DAD, 0x6E2E1DCE, 0x6E2F1DEF, 0x6E301E10, 0x6E311E31,
    0x6E321E52, 0x6E331E73, 0x6E341E94, 0x6E351EB5, 0x6E361ED6, 0x6E371EF7,
    0x6E381F18, 0x6E391F39, 0x6E3A1F5A, 0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD,
    0x6E3E1FDE, 0x6E3F1FFF, 0x6D4627E8, 0x6D472FEA, 0x6D4837EC, 0x6D493FEE,
    0xA94053F3, 0xA9415BF5, 0xA94263F7, 0xA9436BF9, 0xA94473FB, 0xA9457BFD,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xD65F03C0,
)


def fixture(entry=0x1000, body=0x2000):
    words = list(_WORDS)
    displacement = body - (entry + BODY_CALL_INDEX * 4)
    if displacement & 3 or not -(1 << 27) <= displacement <= (1 << 27) - 4:
        raise ValueError('test fixture BL displacement')
    words[BODY_CALL_INDEX] = 0x94000000 | ((displacement // 4) & 0x3FFFFFF)
    return words


def recognize(words, entry=0x1000, body=0x2000):
    memory = {entry + index * 4: word for index, word in enumerate(words)}
    return recognize_malloc_entry(memory.get, entry, body)


class MallocEntryTests(unittest.TestCase):
    def test_whole_body_effects_are_conditional_and_frozen(self):
        words = fixture(); result = recognize(words)
        self.assertIsNotNone(result)
        self.assertEqual(result.body_size, 572)
        self.assertEqual(result.native_body_call_pc, 0x1000 + 72 * 4)
        self.assertEqual(result.ret_pc, 0x1000 + 142 * 4)
        self.assertEqual(result.body_sha256, hashlib.sha256(b''.join(word.to_bytes(4, 'little') for word in words)).hexdigest())
        self.assertEqual(result.saved_frame_clear_interval_from_original_sp, (-160, 0))
        self.assertEqual(result.saved_frame_clear_stores, 10)
        self.assertEqual(result.preserved_gprs_on_normal_return, tuple(f'x{i}' for i in range(19, 31)))
        self.assertEqual(result.preserved_low_vector_halves, tuple(f'd{i}' for i in range(8, 16)))
        self.assertNotIn('x1', result.quarantined_gprs_at_body_call)
        self.assertEqual(result.original_sp_register, 'x1')
        self.assertNotIn('x18', result.cleared_return_gprs)
        self.assertEqual(len(result.cleared_return_vectors), 24)
        self.assertEqual(len(result.cleared_return_vector_upper_halves), 8)
        for name in ('native_body_verified', 'allocator_verified', 'object_validity_verified',
                     'stack_ownership_verified', 'prewrite_safety_verified', 'unwind_verified',
                     'async_verified', 'lifetime_verified', 'native_memory_purity_verified', 'consumer_proof'):
            self.assertIs(getattr(result, name), False)
        with self.assertRaises(dataclasses.FrozenInstanceError):
            result.consumer_proof = True

    def test_every_single_bit_mutation_refuses(self):
        original = fixture()
        for index in range(BODY_WORDS):
            for bit in range(32):
                words = original.copy(); words[index] ^= 1 << bit
                with self.subTest(index=index, bit=bit):
                    self.assertIsNone(recognize(words))

    def test_truncation_invalid_words_and_missing_clear_refuse(self):
        original = fixture()
        for length in (0, 1, BODY_CALL_INDEX, BODY_WORDS - 1):
            self.assertIsNone(recognize(original[:length]))
        for value in (None, True, False, -1, 1 << 32, 0.0, '0'):
            words = original.copy(); words[0] = value
            self.assertIsNone(recognize(words))
        for index in range(132, 142):
            words = original.copy(); words[index] = 0x910043FF  # ADD SP,SP,#16; no clear.
            self.assertIsNone(recognize(words))

    def test_exact_signed_external_call_targets(self):
        for entry, body in ((0x1000, 0x2000), (0x2000, 0x1000),
                            (0x100000000, 0x100001000),
                            (0x10000000, 0x10000000 + 72 * 4 - (1 << 27)),
                            (0x10000000, 0x10000000 + 72 * 4 + (1 << 27) - 4)):
            result = recognize(fixture(entry, body), entry, body)
            self.assertIsNotNone(result)
            self.assertEqual(result.native_body_target, body)
            self.assertIsNone(recognize(fixture(entry, body), entry, body + 4))
        self.assertIsNone(recognize(fixture(), body=0x1000))
        self.assertIsNone(recognize(fixture(), body=0x1000 + BODY_BYTES - 4))
        # Wrapped targets must not be recovered by modular uint64 arithmetic.
        entry = 0; words = fixture(entry, 0x2000)
        words[BODY_CALL_INDEX] = 0x94000000 | (1 << 25)
        self.assertIsNone(recognize(words, entry, (1 << 64) - (1 << 27) + 72 * 4))

    def test_bounds_and_types_refuse_before_reads(self):
        def forbidden(_):
            self.fail('invalid address attempted a read')
        invalid = (True, False, None, -4, 2, 1.0, '0', 1 << 64)
        for entry in (*invalid, (1 << 64) - BODY_BYTES + 4):
            self.assertIsNone(recognize_malloc_entry(forbidden, entry, 0x2000))
        for body in (*invalid, (1 << 64) - 2):
            self.assertIsNone(recognize_malloc_entry(forbidden, 0x1000, body))
        self.assertIsNone(recognize_malloc_entry(forbidden, 0x1000, 0x1000))


if __name__ == '__main__':
    unittest.main()
