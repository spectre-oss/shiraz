# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic SWP64 recognition controls, never native or APK execution proof."""

import hashlib
import itertools
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_swap_effects import BODY_BYTES, recognize_swap64

ORDERS = {
    "relaxed": (0xF8208020, 0xC85F7C20, 0xC8117C30),
    "acquire": (0xF8A08020, 0xC85FFC20, 0xC8117C30),
    "acquire-release": (0xF8E08020, 0xC85FFC20, 0xC811FC30),
}


def fixture(order="acquire", entry=0x1000, flag=0x51548):
    pages = ((flag & ~4095) - ((entry + 4) & ~4095)) // 4096
    assert -(1 << 20) <= pages < 1 << 20
    encoded = pages & ((1 << 21) - 1)
    adrp = 0x90000010 | (encoded & 3) << 29 | (encoded >> 2) << 5
    load = 0x39400210 | (flag & 4095) << 10
    lse, exclusive_load, exclusive_store = ORDERS[order]
    return [0xD503245F, adrp, load, 0x34000070, lse, 0xD65F03C0,
            0xAA0003F0, exclusive_load, exclusive_store, 0x35FFFFD1, 0xD65F03C0]


class SwapEffectsTests(unittest.TestCase):
    def recognize(self, words=None, *, order="acquire", entry=0x1000, flag=0x51548, owned=None):
        words = fixture(order, entry, flag) if words is None else words
        memory = {entry + index * 4: word for index, word in enumerate(words)}
        if owned is None:
            owned = lambda address, width: address == flag and width == 1
        return recognize_swap64(memory.get, entry, owned)

    def test_each_complete_observed_order_has_exact_frozen_effects(self):
        for order in ORDERS:
            with self.subTest(order=order):
                words = fixture(order);result = self.recognize(words)
                self.assertEqual(result.memory_order, order)
                self.assertEqual(result.body_size, 44)
                self.assertEqual(result.body_sha256, hashlib.sha256(
                    b"".join(word.to_bytes(4, "little") for word in words)).hexdigest())
                self.assertEqual(result.flag_address, 0x51548)
                self.assertEqual((result.memory_base, result.memory_payload, result.memory_result,
                                  result.rmw_width, result.flag_width), ("x1", "x0", "x0", 8, 1))
                self.assertEqual(result.operation, "atomic-swap64")
                self.assertEqual([(p.name, p.ret_pc, p.clobbers) for p in result.paths],
                                 [("lse", 0x1014, ("x0", "x16")),
                                  ("llsc", 0x1028, ("x0", "x16", "x17"))])
                self.assertTrue(all(p.may_write_memory and p.preserves_nzcv for p in result.paths))
                self.assertTrue(result.preserves_sp and result.preserves_lr and
                                result.preserves_vectors and result.preserves_nzcv)
                with self.assertRaises(FrozenInstanceError):result.rmw_width = 4
                with self.assertRaises(FrozenInstanceError):result.paths[0].ret_pc = 0

    def test_every_single_bit_mutation_refuses_with_exact_flag_ownership(self):
        for order in ORDERS:
            words = fixture(order)
            for index in range(len(words)):
                for bit in range(32):
                    changed = words.copy();changed[index] ^= 1 << bit
                    with self.subTest(order=order, index=index, bit=bit):
                        self.assertIsNone(self.recognize(changed))

    def test_mixed_orders_and_unobserved_release_only_are_not_inferred(self):
        lse_words = (0xF8208020, 0xF8A08020, 0xF8608020, 0xF8E08020)
        for selected in itertools.product(lse_words, (0xC85F7C20, 0xC85FFC20),
                                          (0xC8117C30, 0xC811FC30)):
            words = fixture();words[4], words[7], words[8] = selected
            result = self.recognize(words)
            if selected in ORDERS.values():
                self.assertIsNotNone(result)
            else:
                self.assertIsNone(result)

    def test_incomplete_bad_words_and_unsupported_register_control_forms_refuse(self):
        words = fixture()
        for index in range(len(words)):
            self.assertIsNone(self.recognize(words[:index]))
            for invalid in (None, True, -1, 1 << 32, "word", 1.5):
                changed = words.copy();changed[index] = invalid
                with self.subTest(index=index, invalid=invalid):
                    self.assertIsNone(self.recognize(changed))
        for index, replacement in ((0, 0xD503201F), (4, 0xB8A08020),
                                   (5, 0xD65F0020), (6, 0xAA0103F0),
                                   (7, 0xC85FFC40), (8, 0xC8107C30),
                                   (9, 0x35FFFFB1), (10, 0xD65F0020)):
            changed = words.copy();changed[index] = replacement
            self.assertIsNone(self.recognize(changed))

    def test_invalid_entry_is_rejected_before_reading(self):
        def forbidden(_):
            self.fail("invalid entry read executable memory")
        for entry in (None, True, -4, 1, 1 << 64, (1 << 64) - BODY_BYTES + 4):
            with self.subTest(entry=entry):
                self.assertIsNone(recognize_swap64(forbidden, entry, lambda *_: True))

    def test_readable_nonexec_flag_ownership_requires_literal_true(self):
        for verdict in (False, None, 1, "owned"):
            with self.subTest(verdict=verdict):
                self.assertIsNone(self.recognize(owned=lambda *_, verdict=verdict: verdict))
        calls = []
        result = self.recognize(owned=lambda address, width: calls.append((address, width)) is None)
        self.assertIsNotNone(result)
        self.assertEqual(calls, [(0x51548, 1)])
        self.assertIsNone(self.recognize(flag=0x1000, owned=lambda *_: True))
        self.assertIsNone(self.recognize(flag=0x102B, owned=lambda *_: True))

    def test_relocation_signed_page_and_pc_crossing_without_address_allowlist(self):
        for entry, flag in ((0x1FFC, 0x51548), (0x80000000, 0x80050548),
                            (0x50000, 0x1000), (0x1000, 0),
                            ((1 << 64) - BODY_BYTES, (1 << 64) - 0x1040)):
            with self.subTest(entry=entry, flag=flag):
                result = self.recognize(entry=entry, flag=flag)
                self.assertIsNotNone(result)
                self.assertEqual(result.flag_address, flag)
                self.assertEqual(result.paths[-1].ret_pc, entry + 40)

    def test_adrp_arithmetic_underflow_overflow_and_changed_operand_refuse(self):
        words = fixture(entry=0, flag=0x1000)
        words[1] = 0xF0FFFFF0  # ADRP x16, negative page from PC0.
        self.assertIsNone(self.recognize(words, entry=0, flag=0x1000, owned=lambda *_: True))
        entry = (1 << 64) - BODY_BYTES
        words = fixture(entry=entry, flag=entry - 4096)
        words[1] = 0xB0000010  # ADRP x16,+4096 wraps the top page.
        self.assertIsNone(self.recognize(words, entry=entry, flag=entry - 4096, owned=lambda *_: True))
        for index, replacement in ((1, 0x90000291), (1, 0x10000290),
                                   (2, 0x39552211), (2, 0x39552230), (2, 0xB9452210)):
            words = fixture();words[index] = replacement
            self.assertIsNone(self.recognize(words))


if __name__ == "__main__":
    unittest.main()
