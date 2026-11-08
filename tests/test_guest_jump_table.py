# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic byte recognizer tests; never native execution or full TLS proof."""

import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_jump_table import recognize_byte_jump_table

FIRST_ENTRY = 0x44AF5C
FIRST_WORDS = (
    0x7100375F, 0x540001A8, 0x2A1A03E8, 0x90FFEB0B, 0x910F496B,
    0x10000089, 0x3868696A, 0x8B0A0929, 0xD61F0120,
)
SECOND_ENTRY = 0x44B014
SECOND_WORDS = (
    0x7100375F, 0x540004A8, 0x2A1A03E8, 0xF0FFEAEB, 0x910F816B,
    0x10FFFAC9, 0x3868696A, 0x8B0A0929, 0xD61F0120,
)


def adr_word(destination, delta, page=False):
    value = delta & ((1 << 21) - 1)
    return ((0x90000000 if page else 0x10000000)
            | (value & 3) << 29 | (value >> 2) << 5 | destination)


class ByteJumpTableTests(unittest.TestCase):
    @staticmethod
    def recognize(words=FIRST_WORDS, *, entry=FIRST_ENTRY, address=0x1AA3D2,
                  table=bytes(range(14))):
        graph = {entry + index * 4: word for index, word in enumerate(words)}
        return recognize_byte_jump_table(
            graph.get, entry,
            lambda start, count: table if start == address and count == len(table) else None,
        )

    def test_actual_code_encodings_with_purpose_built_table_bytes(self):
        # Code words from unchanged QtCore. Table payload here is synthetic;
        # these tests do not claim that the target APK has these byte entries.
        for entry, words, address, default in (
            (FIRST_ENTRY, FIRST_WORDS, 0x1AA3D2, 0x44AF94),
            (SECOND_ENTRY, SECOND_WORDS, 0x1AA3E0, 0x44B0AC),
        ):
            with self.subTest(entry=entry):
                result = self.recognize(words, entry=entry, address=address)
                self.assertIsNotNone(result)
                self.assertEqual(result.entry, entry)
                self.assertEqual(result.body_size, 36)
                self.assertEqual(result.words, words)
                self.assertEqual(result.default_target, default)
                self.assertEqual(result.table_address, address)
                self.assertEqual(result.branch_base, 0x44AF80)
                self.assertEqual(result.limit, 13)
                self.assertEqual((result.index_source, result.index_register,
                                  result.table_register, result.offset_register,
                                  result.branch_register), ("w26", "x8", "x11", "w10", "x9"))
                self.assertEqual(result.targets, tuple(0x44AF80 + index * 4 for index in range(14)))
                self.assertEqual(result.table_bytes, tuple(range(14)))
                self.assertEqual(result.table_sha256, hashlib.sha256(bytes(range(14))).hexdigest())
                body = b"".join(word.to_bytes(4, "little") for word in words)
                self.assertEqual(result.body_sha256, hashlib.sha256(body).hexdigest())

    def test_each_opcode_must_match_before_table_reader_runs(self):
        for index in range(9):
            words = list(FIRST_WORDS)
            words[index] ^= 1 << 31
            graph = {FIRST_ENTRY + offset * 4: word for offset, word in enumerate(words)}

            def forbidden_reader(start, count):
                self.fail("malformed code must not reach table reader")

            with self.subTest(index=index):
                self.assertIsNone(recognize_byte_jump_table(graph.get, FIRST_ENTRY, forbidden_reader))

    def test_rejects_condition_width_shift_and_register_mismatches(self):
        mutations = (
            (0, FIRST_WORDS[0] | 1 << 22),  # Shifted CMP immediate.
            (1, FIRST_WORDS[1] ^ 1),  # LS instead of unsigned HI.
            (2, FIRST_WORDS[2] ^ 1 << 16),  # MOV has a different W source.
            (2, FIRST_WORDS[2] | 1 << 10),  # Shifted ORR is not MOV.
            (3, FIRST_WORDS[3] ^ 1 << 31),  # ADR, not ADRP.
            (4, FIRST_WORDS[4] | 1 << 22),  # Shifted immediate ADD.
            (4, FIRST_WORDS[4] ^ 1 << 5),  # ADD source is not table register.
            (5, FIRST_WORDS[5] ^ 1 << 31),  # ADRP, not ADR.
            (6, FIRST_WORDS[6] ^ 1 << 13),  # Different extension option.
            (6, FIRST_WORDS[6] | 1 << 12),  # Reject even explicit scaled byte form.
            (6, FIRST_WORDS[6] ^ 1 << 16),  # Different index register.
            (6, FIRST_WORDS[6] ^ 1 << 5),  # Different table base.
            (7, FIRST_WORDS[7] ^ 1 << 10),  # LSL #3, not #2.
            (7, FIRST_WORDS[7] ^ 1 << 16),  # Different offset register.
            (7, FIRST_WORDS[7] ^ 1 << 5),  # Different branch-base register.
            (8, FIRST_WORDS[8] ^ 1 << 5),  # BR reads a different register.
            (8, 0xD73F0920),  # Authenticated branch is not BR.
        )
        for index, replacement in mutations:
            words = list(FIRST_WORDS)
            words[index] = replacement
            with self.subTest(index=index, replacement=hex(replacement)):
                self.assertIsNone(self.recognize(words))

    def test_register_roles_are_distinct_and_exclude_fp_lr_sp_zr(self):
        # Keep MOV and load index consistent, so the overlap check is exercised.
        words = list(FIRST_WORDS)
        words[2] = words[2] & ~31 | 26
        words[6] = words[6] & ~(31 << 16) | 26 << 16
        self.assertIsNone(self.recognize(words))
        for register in (29, 30, 31):
            words = list(FIRST_WORDS)
            words[0] = words[0] & ~(31 << 5) | register << 5
            words[2] = words[2] & ~(31 << 16) | register << 16
            self.assertIsNone(self.recognize(words))
            words = list(FIRST_WORDS)
            words[5] = words[5] & ~31 | register
            words[7] = words[7] & ~1023 | register << 5 | register
            words[8] = words[8] & ~(31 << 5) | register << 5
            self.assertIsNone(self.recognize(words))

    def test_table_size_reader_refusal_and_mutable_payload_are_rejected(self):
        for limit in (64, 4095):
            words = list(FIRST_WORDS)
            words[0] = words[0] & ~(0xFFF << 10) | limit << 10
            self.assertIsNone(self.recognize(words))
        for data in (b"", bytes(13), bytes(15), bytearray(14), None):
            with self.subTest(data=data):
                graph = {FIRST_ENTRY + index * 4: word for index, word in enumerate(FIRST_WORDS)}
                self.assertIsNone(recognize_byte_jump_table(graph.get, FIRST_ENTRY, lambda *_, data=data: data))

    def test_zero_and_maximum_bounds_and_duplicate_targets(self):
        for limit, table in ((0, b"\x00"), (63, bytes([255]) * 64)):
            words = list(FIRST_WORDS)
            words[0] = words[0] & ~(0xFFF << 10) | limit << 10
            result = self.recognize(words, table=table)
            self.assertIsNotNone(result)
            self.assertEqual(len(result.targets), limit + 1)
            self.assertEqual(result.targets, (0x44AF80 + table[0] * 4,) * (limit + 1))

    def test_truncated_or_non_integer_words_and_bad_entry_are_rejected(self):
        for value in (None, True, -1, 1 << 32, "word"):
            words = list(FIRST_WORDS)
            words[4] = value
            self.assertIsNone(self.recognize(words))
        self.assertIsNone(self.recognize(FIRST_WORDS[:-1]))
        for entry in (-4, True, 2, 1 << 64, (1 << 64) - 32):
            self.assertIsNone(self.recognize(entry=entry))

    def test_address_underflow_overflow_and_internal_default_are_rejected(self):
        words = list(FIRST_WORDS)
        words[1] = 0x54000008 | (1 << 5)  # Default enters the MOV.
        self.assertIsNone(self.recognize(words))
        words = list(FIRST_WORDS)
        words[1] = 0x54000008 | (((1 << 19) - 2) << 5)
        self.assertIsNone(self.recognize(words, entry=0))  # Default below zero.
        words = list(FIRST_WORDS)
        words[3] = adr_word(11, -1, page=True)  # Page below zero.
        self.assertIsNone(self.recognize(words, entry=0))
        high_entry = (1 << 64) - 4096
        words = list(FIRST_WORDS)
        words[3] = adr_word(11, 0, page=True)
        words[4] = 0x91000000 | (4090 << 10) | 11 << 5 | 11
        self.assertIsNone(self.recognize(words, entry=high_entry))  # Table span wraps.
        words = list(FIRST_WORDS)
        words[3] = adr_word(11, -1, page=True)
        words[4] = 0x91000000 | 11 << 5 | 11
        words[5] = adr_word(9, 4072)
        # Branch base becomes UINT64_MAX-3; nonzero bytes overflow the target.
        self.assertIsNone(self.recognize(words, entry=high_entry,
                                        address=high_entry - 4096, table=bytes(range(14))))
        words = list(FIRST_WORDS)
        words[5] = adr_word(9, 1)  # Unaligned branch base.
        self.assertIsNone(self.recognize(words))


if __name__ == "__main__":
    unittest.main()
