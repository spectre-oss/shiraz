# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic complete-leaf recognition tests, not execution/TLS proof."""

import hashlib
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_cached_self import BODY_BYTES, TABLE_BYTES, recognize_cached_self

# Exact observed instruction roles; addresses are encoded independently below.
WORDS = (
    0xD2800008, 0x910003E9, 0xD53BD06A,
    0, 0, 0, 0, 0, 0, 0, 0,
    0x14000004, 0x91002108, 0xF140211F, 0x540002E0, 0x8B08016F,
    0xF8BFC1E0, 0xF100081F, 0xFA402944, 0x54FFFF20, 0xF868698F,
    0xEB0A01FF, 0x54FFFEC1, 0xF86869AF, 0xF86869D0, 0xEB0F013F,
    0xFA502122, 0x54FFFE22, 0x8B08016F, 0xF8BFC1EF, 0xEB0001FF,
    0x54FFFDA1, 0x8B08018F, 0xF8BFC1EF, 0xEB0A01FF, 0x54FFFD21,
    0xD65F03C0, 0xD4200020,
)
TABLES = (0x30280, 0x40280, 0x50280, 0x60280)


def fixture(entry=0x1FF8, tables=TABLES):
    words = list(WORDS)
    for index, register, address in zip((3, 5, 7, 9), (11, 12, 13, 14), tables):
        pages = ((address & ~4095) - ((entry + index * 4) & ~4095)) // 4096
        assert -(1 << 20) <= pages < 1 << 20
        encoded = pages & ((1 << 21) - 1)
        words[index] = 0x90000000 | (encoded & 3) << 29 | (encoded >> 2) << 5 | register
        words[index + 1] = 0x91000000 | (address & 4095) << 10 | register << 5 | register
    return words


class CachedSelfRecognitionTests(unittest.TestCase):
    def recognize(self, words=None, entry=0x1FF8, tables=TABLES, owned=None):
        words = fixture(entry, tables) if words is None else words
        memory = {entry + index * 4: word for index, word in enumerate(words)}
        if owned is None:
            owned = lambda address, width: address in tables and width == TABLE_BYTES
        return recognize_cached_self(memory.get, entry, owned)

    def test_complete_body_effects_and_hash_are_explicit_and_frozen(self):
        words = fixture()
        summary = self.recognize(words)
        self.assertIsNotNone(summary)
        self.assertEqual(summary.body_size, 152)
        self.assertEqual(summary.rows, 4096)
        self.assertEqual(summary.ret_pc, 0x1FF8 + 144)
        self.assertEqual(summary.trap_pc, 0x1FF8 + 148)
        self.assertEqual(summary.body_sha256, hashlib.sha256(
            b"".join(word.to_bytes(4, "little") for word in words)).hexdigest())
        self.assertEqual([(table.role, table.address, table.byte_count) for table in summary.tables],
                         list(zip(("owners", "keys", "low", "high"), TABLES, (32768,) * 4)))
        self.assertEqual(summary.clobbers, ("x0", *(f"x{i}" for i in range(8, 17))))
        self.assertEqual(summary.sp_copy_register, "x9")
        self.assertEqual(summary.native_register_read, "TPIDRRO_EL0")
        self.assertFalse(summary.may_write_memory or summary.preserves_nzcv)
        self.assertTrue(summary.preserves_sp and summary.preserves_lr and summary.preserves_vectors
                        and summary.preserves_unlisted_gprs and summary.preserves_fpcr and summary.preserves_fpsr)
        with self.assertRaises(FrozenInstanceError):
            summary.rows = 1
        with self.assertRaises(FrozenInstanceError):
            summary.tables[0].address = 0

    def test_each_bit_of_every_instruction_is_checked(self):
        words = fixture()
        for index in range(len(words)):
            for bit in range(32):
                changed = list(words)
                changed[index] ^= 1 << bit
                with self.subTest(index=index, bit=bit):
                    self.assertIsNone(self.recognize(changed))

    def test_both_acquire_forms_and_mixed_forms_have_identical_bounded_effects(self):
        for mask in range(8):
            words = fixture()
            for bit, index in enumerate((16, 29, 33)):
                if mask & (1 << bit):
                    words[index] = 0xC8DFFDE0 | (15 if index != 16 else 0)
            result = self.recognize(words)
            self.assertIsNotNone(result)
            self.assertEqual(result.rows, 4096)
            self.assertFalse(result.may_write_memory)

    def test_relocation_signed_pages_and_crossing_code_page(self):
        for entry, tables in ((0x1FF8, TABLES), (0x100000, TABLES),
                              (0x100000000, tuple(address + 0x100000000 for address in TABLES))):
            with self.subTest(entry=entry):
                result = self.recognize(entry=entry, tables=tables)
                self.assertIsNotNone(result)
                self.assertEqual(tuple(table.address for table in result.tables), tables)

    def test_all_words_required_and_typed_unsigned(self):
        for index in range(len(WORDS)):
            for bad in (None, True, -1, 1 << 32):
                words = fixture()
                words[index] = bad
                with self.subTest(index=index, bad=bad):
                    self.assertIsNone(self.recognize(words))
        for size in range(len(WORDS)):
            self.assertIsNone(self.recognize(fixture()[:size]))

    def test_entry_bounds_refuse_before_reading(self):
        def forbidden(_):
            self.fail("invalid entry must not read words")
        for entry in (True, None, -4, 1, (1 << 64) - BODY_BYTES + 4, 1 << 64):
            with self.subTest(entry=entry):
                self.assertIsNone(recognize_cached_self(forbidden, entry, lambda *_: True))

    def test_complete_table_ownership_is_required(self):
        for table in TABLES:
            for verdict in (False, None, 1):
                with self.subTest(table=table, verdict=verdict):
                    self.assertIsNone(self.recognize(owned=lambda address, width, verdict=verdict, table=table:
                        verdict if address == table else True))
        seen = []
        result = self.recognize(owned=lambda address, width: seen.append((address, width)) is None)
        self.assertIsNotNone(result)
        self.assertEqual(seen, [(address, TABLE_BYTES) for address in TABLES])

    def test_overlapping_unaligned_or_overflowing_tables_rejected(self):
        for tables in ((TABLES[0], TABLES[0], *TABLES[2:]),
                       (TABLES[0], TABLES[0] + TABLE_BYTES - 8, *TABLES[2:]),
                       (0x1FF8, *TABLES[1:]), (TABLES[0] + 1, *TABLES[1:])):
            with self.subTest(tables=tables):
                self.assertIsNone(self.recognize(tables=tables, owned=lambda *_: True))
        # Encode an address near the end of uint64 within ADRP reach.
        entry = (1 << 64) - 0x20000
        tables = ((1 << 64) - 0x1000, entry - 0x30000, entry - 0x40000, entry - 0x50000)
        self.assertIsNone(self.recognize(entry=entry, tables=tables, owned=lambda *_: True))

    def test_wrong_native_register_access_order_or_memory_operation_rejected(self):
        replacements = {2: 0xD53BD04A, 12: 0x91004108, 13: 0xF140251F,
                        16: 0xF94001E0, 20: 0xF828698F, 23: 0xF86879AF,
                        29: 0xC8DFFDCE, 33: 0xF94001EF,
                        36: 0xD65F0200, 37: 0xD503201F}
        for index, replacement in replacements.items():
            words = fixture()
            words[index] = replacement
            with self.subTest(index=index):
                self.assertIsNone(self.recognize(words))
