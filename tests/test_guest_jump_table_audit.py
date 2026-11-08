# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic reader/state checks, not APK execution or full TLS proof."""

import struct
import unittest
from dataclasses import replace
from types import SimpleNamespace

import test_guest_jump_table as jump_tests
import test_guest_namespace as namespace_tests
from test_guest_tpidr_audit import BindingElfFixture, I, M, R, audit, instruction


class ReadonlyJumpTableTests(unittest.TestCase):
    def fixture(self):
        fixture = BindingElfFixture()
        struct.pack_into("<H", fixture.data, 56, 4)
        struct.pack_into("<IIQQQQQQ", fixture.data, 64 + 3 * 56,
                         1, 4, 0x1B00, 0x3000, 0, 0x100, 0x200, 0x1000)
        fixture.data[0x1B00:0x1B0E] = bytes(range(14))
        return fixture

    def metadata(self, fixture):
        metadata = fixture.metadata()
        # Explicit synthetic semantic trust; not signature/bridge verification.
        metadata.trusted_provider_contracts = True
        return metadata

    def test_requires_explicit_trust_readonly_data_and_complete_file_backing(self):
        fixture = self.fixture()
        self.assertIsNone(fixture.metadata().readonly_jump_table_bytes(0x3000, 14))
        self.assertEqual(self.metadata(fixture).readonly_jump_table_bytes(0x3000, 14), bytes(range(14)))
        for address, size in ((0x30FF, 2), (0x3100, 1), (0x3000, 0), (0x3000, 65),
                              (0x1500, 1), (0x200, 1), (-1, 1), (False, 1)):
            self.assertIsNone(self.metadata(fixture).readonly_jump_table_bytes(address, size))
        for flags in (5, 6, 7, 0):
            struct.pack_into("<I", fixture.data, 64 + 3 * 56 + 4, flags)
            self.assertIsNone(self.metadata(fixture).readonly_jump_table_bytes(0x3000, 14))

    def test_tracks_relative_and_absolute_writes_and_refuses_unknown_widths(self):
        for kind in (257, 1025, 1026, 1027):
            fixture = self.fixture()
            struct.pack_into("<Q", fixture.data, 0x1408, (1 << 32) | kind)
            metadata = self.metadata(fixture)
            self.assertIn(0x1500, metadata.relocation_writes)
            self.assertEqual(metadata.readonly_jump_table_bytes(0x3000, 14), bytes(range(14)))
            struct.pack_into("<Q", fixture.data, 0x1400, 0x3000)
            self.assertIsNone(self.metadata(fixture).readonly_jump_table_bytes(0x3000, 14))
        fixture = self.fixture()
        struct.pack_into("<Q", fixture.data, 0x1408, (1 << 32) | 1024)
        self.assertIsNone(self.metadata(fixture).readonly_jump_table_bytes(0x3000, 14))

    def test_unsupported_encoding_or_textrel_cannot_certify_original_bytes(self):
        for tag, value in ((17, 0), (35, 1), (36, 0), (37, 8), (22, 0), (30, 4),
                           (0x6000000F, 0), (0x60000011, 1), (0x6FFFE000, 0),
                           (0x6FFFE001, 8), (0x6FFFE003, 8), (0x6FFFE005, 1)):
            fixture = self.fixture()
            fixture.tags.append((tag, value))
            fixture.dynamic()
            self.assertIsNone(self.metadata(fixture).readonly_jump_table_bytes(0x3000, 14))


class JumpTableAuditTests(unittest.TestCase):
    def test_decoder_requires_every_target_to_have_executable_file_bytes(self):
        entry = jump_tests.FIRST_ENTRY
        words = {entry + 4 * i: word.to_bytes(4, "little") for i, word in enumerate(jump_tests.FIRST_WORDS)}
        summary = jump_tests.ByteJumpTableTests.recognize()
        for target in (*summary.targets, summary.default_target):
            words[target] = b"\x1f\x20\x03\xd5"

        def decoder():
            result = object.__new__(audit.ElfDecoder)
            result.jump_table_cache = {}
            result.bindings = SimpleNamespace(readonly_jump_table_bytes=lambda *_: bytes(range(14)))
            result.word = words.get
            return result

        self.assertIsNotNone(decoder().byte_jump_table(entry))
        del words[summary.targets[-1]]
        self.assertIsNone(decoder().byte_jump_table(entry))

    def test_entering_after_guard_does_not_acquire_a_switch_summary(self):
        summary = replace(jump_tests.ByteJumpTableTests.recognize(), entry=8)
        graph = {item.address: item for item in (
            instruction(0, "mrs", R("x19")), instruction(4, "b", I(12)),
            instruction(8, "cmp", R("w26"), I(13)), instruction(12, "br", R("x9")),
        )}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def byte_jump_table(self, pc):
                return summary if pc == 8 else None

        result = audit.Audit(Decoder()).site(0)
        self.assertFalse(result["bounded_jump_tables"])
        self.assertIn("indirect-call-or-branch-with-unproved-summary", {x["reason"] for x in result["reasons"]})

    def report(self, *, unsafe_default=False, unsafe_case=False, source="xzr", spill=False, window=True):
        summary = replace(jump_tests.ByteJumpTableTests.recognize(), entry=12, default_target=64,
                          table_address=0x3000, branch_base=80, limit=1,
                          table_bytes=(0, 4), targets=(80, 96))
        body = [instruction(0, "mrs", R("x19")), instruction(4, "mov", R("x26"), R(source)),
                instruction(8, "str", R("x19" if spill else "xzr"), M("sp", 8)),
                instruction(12, "cmp", R("w26"), I(1))]
        for pc, offset in ((64, 8 if unsafe_default else 40), (80, 40), (96, 16 if unsafe_case else 40)):
            body.extend([instruction(pc, "ldr", R("x8"), M("x19", offset)),
                         instruction(pc + 4, "mov", R("x19"), R("xzr")),
                         instruction(pc + 8, "str", R("xzr"), M("sp", 8)),
                         instruction(pc + 12, "ret")])
        graph = {item.address: item for item in body}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def byte_jump_table(self, pc):
                return summary if pc == 12 else None

            def owned_image_data(self, address, width):
                return address == 0x3000 and width == 2

            def mrs_stack_window(self, pc):
                return ({"profile": 1, "before": 4096, "after": 256,
                         "image_stack_exclusion_profile": 1} if window else None)

        return audit.Audit(Decoder()).site(0)

    def test_all_cases_and_default_are_traversed(self):
        result = self.report()
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(len(result["bounded_jump_tables"]), 1)
        self.assertEqual({row["pc"] for row in result["accesses"]}, {"0x40", "0x50", "0x60"})
        for kwargs in ({"unsafe_default": True}, {"unsafe_case": True}):
            result = self.report(**kwargs)
            self.assertEqual(result["classification"], "unknown")
            self.assertTrue(any(row["kind"] == "unsupported" for row in result["accesses"]))

    def test_index_facts_and_unproved_stack_separation_remain_refused(self):
        for source in ("x19", "sp"):
            result = self.report(source=source)
            self.assertIn("jump-table-index-has-pointer-or-signature-fact",
                          {row["reason"] for row in result["reasons"]})
            self.assertFalse(result["bounded_jump_tables"])
        result = self.report(spill=True, window=False)
        self.assertIn("jump-table-load-may-alias-thread-pointer-stack-spill",
                      {row["reason"] for row in result["reasons"]})
        self.assertFalse(result["bounded_jump_tables"])
        self.assertEqual(self.report(spill=True)["classification"], "guard-only")

    def test_namespace_biases_every_address_and_refuses_cross_slot_values(self):
        # Reuse only the synthetic namespace setup; no external module load.
        helper = namespace_tests.NamespaceTests()
        helper.setUp()
        self.addCleanup(helper.doCleanups)
        decoder = helper.decoder()
        summary = jump_tests.ByteJumpTableTests.recognize()
        decoder.decoders[1].byte_jump_table = lambda pc: summary
        stride = 1 << 40
        result = decoder.byte_jump_table(stride + summary.entry)
        self.assertEqual(result.table_address, stride + summary.table_address)
        self.assertEqual(result.branch_base, stride + summary.branch_base)
        self.assertEqual(result.default_target, stride + summary.default_target)
        self.assertEqual(result.targets, tuple(stride + pc for pc in summary.targets))
        decoder.decoders[1].byte_jump_table = lambda pc: replace(summary, targets=(stride,))
        self.assertIsNone(decoder.byte_jump_table(stride + summary.entry))


if __name__ == "__main__":
    unittest.main()
