# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic recognition checks; never actual ELF execution evidence."""

import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_callee_effects import recognize_atomic32, recognize_cas64

WORDS = (
    0xD503245F,
    0x90000290,
    0x39552210,
    0x34000070,
    0xB8E00020,
    0xD65F03C0,
    0x2A0003F0,
    0x885FFC20,
    0x0B100011,
    0x880FFC31,
    0x35FFFFAF,
    0xD65F03C0,
)

CAS_WORDS = (
    0xD503245F,
    0x90000290,
    0x39552210,
    0x34000070,
    0xC8A07C41,
    0xD65F03C0,
    0xAA0003F0,
    0xC85F7C40,
    0xEB10001F,
    0x54000061,
    0xC8117C41,
    0x35FFFF91,
    0xD65F03C0,
)


class CalleeEffectsTests(unittest.TestCase):
    def recognize(self, words=WORDS, entry=0x1000, owned=None):
        table = {entry + index * 4: value for index, value in enumerate(words)}
        return recognize_atomic32(
            table.get,
            entry,
            owned or (lambda address, width: address == 0x51548 and width == 1),
        )

    def test_exact_body_has_two_distinct_poststates_and_explicit_memory_effect(self):
        summary = self.recognize()
        self.assertIsNotNone(summary)
        self.assertEqual(summary.body_size, 48)
        self.assertEqual(
            summary.body_sha256,
            hashlib.sha256(
                b"".join(w.to_bytes(4, "little") for w in WORDS)
            ).hexdigest(),
        )
        self.assertEqual(summary.flag_address, 0x51548)
        self.assertEqual(
            (
                summary.memory_base,
                summary.memory_payload,
                summary.memory_result,
                summary.rmw_width,
            ),
            ("x1", "x0", "x0", 4),
        )
        self.assertEqual(summary.paths[0].clobbers, ("x0", "x16"))
        self.assertEqual(summary.paths[1].clobbers, ("x0", "x15", "x16", "x17"))
        self.assertEqual([path.ret_pc for path in summary.paths], [0x1014, 0x102C])
        self.assertTrue(
            summary.preserves_sp and summary.preserves_lr and summary.preserves_vectors
        )

    def test_no_target_address_allowlist_and_bss_flag_is_accepted(self):
        entry = 0x80000000
        address = entry + 0x50548
        # The flag is in allocated BSS, after the example file-backed extent.
        file_end, memory_end = address - 1, address + 1
        summary = self.recognize(
            entry=entry,
            owned=lambda ptr, width: file_end < ptr and ptr + width <= memory_end,
        )
        self.assertIsNotNone(summary)
        self.assertEqual(summary.flag_address, address)

    def test_adrp_uses_its_own_pc_when_entry_crosses_a_page(self):
        summary = self.recognize(
            entry=0x1FFC, owned=lambda address, width: address == 0x52548 and width == 1
        )
        self.assertIsNotNone(summary)
        self.assertEqual(summary.flag_address, 0x52548)

    def test_flag_requires_explicit_owned_readable_data_proof(self):
        self.assertIsNone(self.recognize(owned=lambda *_: False))
        self.assertIsNone(self.recognize(owned=lambda *_: None))
        self.assertIsNone(self.recognize(owned=lambda *_: 1))

    def test_signed_adrp_and_immediate_load_offset_are_decoded(self):
        words = list(WORDS)
        delta = (-7) & ((1 << 21) - 1)
        words[1] = 0x90000010 | ((delta & 3) << 29) | ((delta >> 2) << 5)
        words[2] = 0x39400210 | (0xFFF << 10)
        summary = self.recognize(
            words,
            entry=0x8000,
            owned=lambda address, width: address == 0x1FFF and width == 1,
        )
        self.assertIsNotNone(summary)
        self.assertEqual(summary.flag_address, 0x1FFF)
        self.assertIsNone(self.recognize(words, entry=0, owned=lambda *_: True))

    def test_changed_registers_branches_widths_or_shift_are_rejected(self):
        for index in range(12):
            with self.subTest(index=index):
                words = list(WORDS)
                words[index] ^= 1
                self.assertIsNone(self.recognize(words))
        words = list(WORDS)
        words[8] |= 1 << 10  # Shifted ADD is not this atomic operation.
        self.assertIsNone(self.recognize(words))
        words = list(WORDS)
        words[4] |= 1 << 30  # 64-bit atomic is a different memory contract.
        self.assertIsNone(self.recognize(words))

    def test_truncated_invalid_and_overflowing_inputs_are_rejected(self):
        self.assertIsNone(self.recognize(WORDS[:-1]))
        for entry in (-4, 1, True, (1 << 64) - 44):
            self.assertIsNone(
                recognize_atomic32(lambda _: WORDS[0], entry, lambda *_: True)
            )
        for invalid in (-1, 1 << 32, True):
            words = list(WORDS)
            words[7] = invalid
            self.assertIsNone(self.recognize(words))


class CompareSwapEffectsTests(unittest.TestCase):
    def recognize(self, words=CAS_WORDS, entry=0x1000, owned=None):
        table = {entry + index * 4: value for index, value in enumerate(words)}
        return recognize_cas64(
            table.get,
            entry,
            owned or (lambda address, width: address == 0x51548 and width == 1),
        )

    def test_exact_compare_and_payload_are_distinct_and_all_returns_are_bounded(self):
        summary = self.recognize()
        self.assertIsNotNone(summary)
        self.assertEqual(summary.body_size, 52)
        self.assertEqual(
            summary.body_sha256,
            hashlib.sha256(
                b"".join(word.to_bytes(4, "little") for word in CAS_WORDS)
            ).hexdigest(),
        )
        self.assertEqual(
            (
                summary.memory_base,
                summary.memory_compare,
                summary.memory_payload,
                summary.memory_result,
                summary.rmw_width,
            ),
            ("x2", "x0", "x1", "x0", 8),
        )
        self.assertEqual(summary.flag_address, 0x51548)
        self.assertEqual([path.ret_pc for path in summary.paths], [0x1014, 0x1030, 0x1030])
        self.assertTrue(
            summary.preserves_sp and summary.preserves_lr and summary.preserves_vectors
        )
        self.assertFalse(summary.preserves_nzcv)

    def test_initial_mismatch_and_retry_mismatch_keep_distinct_register_effects(self):
        lse, mismatch, retry = self.recognize().paths
        self.assertEqual(lse.clobbers, ("x0", "x16"))
        self.assertTrue(lse.preserves_nzcv)
        self.assertTrue(lse.may_write_memory)
        self.assertEqual(mismatch.clobbers, ("x0", "x16"))
        self.assertFalse(mismatch.preserves_nzcv)
        self.assertFalse(mismatch.may_write_memory)
        self.assertEqual(retry.clobbers, ("x0", "x16", "x17"))
        self.assertFalse(retry.preserves_nzcv)
        self.assertTrue(retry.may_write_memory)

    def test_three_exact_memory_order_triplets_and_no_mixed_order_inference(self):
        forms = (
            ("relaxed", (0xC8A07C41, 0xC85F7C40, 0xC8117C41)),
            ("acquire", (0xC8E07C41, 0xC85FFC40, 0xC8117C41)),
            ("acquire-release", (0xC8E0FC41, 0xC85FFC40, 0xC811FC41)),
        )
        for order, values in forms:
            with self.subTest(order=order):
                words = list(CAS_WORDS)
                for index, value in zip((4, 7, 10), values):
                    words[index] = value
                self.assertEqual(self.recognize(words).memory_order, order)
        mixed = list(CAS_WORDS)
        mixed[10] = 0xC811FC41  # A release-only form is not among the exact bodies.
        self.assertIsNone(self.recognize(mixed))
        mixed = list(CAS_WORDS)
        mixed[4] = 0xC8E0FC41  # CASAL cannot pair with relaxed LDXR/STXR.
        self.assertIsNone(self.recognize(mixed))

    def test_modified_registers_widths_control_and_truncation_are_rejected(self):
        for index in range(len(CAS_WORDS)):
            with self.subTest(index=index):
                words = list(CAS_WORDS)
                words[index] ^= 1
                self.assertIsNone(self.recognize(words))
        words = list(CAS_WORDS)
        words[4] ^= 1 << 30  # CAS w0,w1,[x2] has a distinct memory width.
        self.assertIsNone(self.recognize(words))
        self.assertIsNone(self.recognize(CAS_WORDS[:-1]))
        self.assertIsNone(self.recognize(WORDS))
        self.assertIsNone(
            recognize_atomic32(
                {0x1000 + i * 4: word for i, word in enumerate(CAS_WORDS)}.get,
                0x1000,
                lambda *_: True,
            )
        )

    def test_relocated_signed_flag_uses_adrp_pc_and_requires_owned_data(self):
        self.assertIsNotNone(
            self.recognize(
                entry=0x1FFC,
                owned=lambda address, width: address == 0x52548 and width == 1,
            )
        )
        words = list(CAS_WORDS)
        delta = (-7) & ((1 << 21) - 1)
        words[1] = 0x90000010 | ((delta & 3) << 29) | ((delta >> 2) << 5)
        words[2] = 0x39400210 | (0xFFF << 10)
        self.assertIsNotNone(
            self.recognize(
                words,
                entry=0x8000,
                owned=lambda address, width: address == 0x1FFF and width == 1,
            )
        )
        self.assertIsNone(self.recognize(words, entry=0, owned=lambda *_: True))
        for response in (False, None, 1):
            with self.subTest(response=response):
                self.assertIsNone(self.recognize(owned=lambda *_, value=response: value))

    def test_invalid_word_and_entry_bounds_are_rejected(self):
        for entry in (-4, 1, True, (1 << 64) - 48):
            self.assertIsNone(
                recognize_cas64(lambda _: CAS_WORDS[0], entry, lambda *_: True)
            )
        for invalid in (-1, 1 << 32, True):
            words = list(CAS_WORDS)
            words[7] = invalid
            self.assertIsNone(self.recognize(words))


if __name__ == "__main__":
    unittest.main()
