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
    def variant_report(self, setup=(), *, move=("x20", "x19"), branch=None, window=True):
        """Synthetic state transfer only; the byte recognizer is tested separately."""
        body = [instruction(0, "mrs", R("x19"))]
        for mnemonic, operands in setup:
            body.append(instruction(len(body) * 4, mnemonic, *operands))
        entry = len(body) * 4
        body.append(instruction(entry, "cmp", R("w10"), I(1)))
        summary = replace(jump_tests.ByteJumpTableTests.recognize(), entry=entry,
                          default_target=80, table_address=0x3000, branch_base=128,
                          index_source="w10", index_register="x10", table_register="x11",
                          offset_register="w13", branch_register="x12", limit=1,
                          table_bytes=(0, 0), targets=(128, 128), prebranch_move=move,
                          requires_zero_extended_index=True)
        branch = branch or (("ldr", (R("x8"), M("x20", 40))),)
        for start in (80, 128):
            tail = [*branch, ("mov", (R("x19"), R("xzr"))),
                    ("mov", (R("x20"), R("xzr"))),
                    ("mov", (R("x21"), R("xzr"))),
                    ("mov", (R("x22"), R("xzr"))),
                    ("str", (R("xzr"), M("sp", 8))), ("ret", ())]
            for index, (mnemonic, operands) in enumerate(tail):
                body.append(instruction(start + index * 4, mnemonic, *operands))
        graph = {item.address: item for item in body}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def byte_jump_table(self, pc):
                return summary if pc == entry else None

            def owned_image_data(self, address, width):
                return address == 0x3000 and width in (2, 8)

            def mrs_stack_window(self, pc):
                return ({"profile": 1, "before": 4096, "after": 256,
                         "image_stack_exclusion_profile": 1} if window else None)

        return audit.Audit(Decoder()).site(0)

    def test_interleaved_variant_requires_executed_w_write_or_exact_small_scalar(self):
        for setup in (
            (("mov", (R("w10"), R("w3"))),),
            (("sub", (R("w10"), R("w3"), I(1))),),
            (("mov", (R("x10"), I(1))),),
        ):
            with self.subTest(setup=setup):
                result = self.variant_report(setup)
                self.assertEqual(result["classification"], "guard-only", result["reasons"])
                self.assertEqual({r["pc"] for r in result["accesses"]}, {"0x50", "0x80"})
        for setup in (
            (),
            (("mov", (R("x10"), R("x3"))),),
            (("mov", (R("w10"), R("w3"))), ("mov", (R("x10"), R("x4")))),
            (("mov", (R("x10"), I(1 << 32))),),
        ):
            with self.subTest(setup=setup):
                result = self.variant_report(setup)
                self.assertIn("jump-table-index-upper-bits-unproved",
                              {r["reason"] for r in result["reasons"]})
                self.assertFalse(result["bounded_jump_tables"])

    def test_interleaved_variant_narrowed_pointer_and_spill_alias_remain_refused(self):
        result = self.variant_report((("mov", (R("w10"), R("w19"))),))
        self.assertIn("narrowed-thread-pointer", {r["reason"] for r in result["reasons"]})
        result = self.variant_report((("mov", (R("w10"), R("w3"))),
                                      ("str", (R("x19"), M("sp", 8)))), window=False)
        self.assertIn("jump-table-load-may-alias-thread-pointer-stack-spill",
                      {r["reason"] for r in result["reasons"]})
        self.assertFalse(result["bounded_jump_tables"])

    def test_prebranch_move_preserves_stack_alias_on_default_and_case(self):
        result = self.variant_report(
            (("mov", (R("w10"), R("w3"))), ("mov", (R("x21"), R("sp"))),
             ("str", (R("x19"), M("sp", 8)))), move=("x20", "x21"),
            branch=(("ldr", (R("x22"), M("x20", 8))),
                    ("ldr", (R("x8"), M("x22", 40)))))
        self.assertEqual(result["classification"], "guard-only", result["reasons"])
        self.assertEqual({r["pc"] for r in result["accesses"]}, {"0x54", "0x84"})

    def test_prebranch_move_preserves_image_and_scalar_facts_on_both_paths(self):
        scalar = self.variant_report(
            (("mov", (R("w10"), R("w3"))), ("mov", (R("x21"), I(40)))),
            move=("x20", "x21"),
            branch=(("add", (R("x22"), R("x19"), R("x20"))),
                    ("ldr", (R("x8"), M("x22")))))
        self.assertEqual(scalar["classification"], "guard-only", scalar["reasons"])
        self.assertEqual({r["pc"] for r in scalar["accesses"]}, {"0x54", "0x84"})
        image = self.variant_report(
            (("mov", (R("w10"), R("w3"))), ("adr", (R("x21"), I(0x3000))),
             ("str", (R("x19"), M("sp", 8)))), move=("x20", "x21"),
            branch=(("ldr", (R("x8"), M("x20"))),
                    ("ldr", (R("x8"), M("x19", 40)))))
        self.assertEqual(image["classification"], "guard-only", image["reasons"])
        self.assertEqual({r["pc"] for r in image["accesses"]}, {"0x54", "0x84"})

    def test_prebranch_move_preserves_return_tokens_and_signatures(self):
        for signed in (False, True):
            with self.subTest(signed=signed):
                body = [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(32)),
                        instruction(8, "ldr", R("x8"), M("x19", 40)),
                        instruction(12, "mov", R("x19"), R("xzr")), instruction(16, "ret")]
                pc = 32
                if signed:
                    body.append(instruction(pc, "paciasp"))
                    pc += 4
                body.extend((instruction(pc, "mov", R("x21"), R("x30")),
                             instruction(pc + 4, "mov", R("w10"), R("w3"))))
                entry = pc + 8
                body.append(instruction(entry, "cmp", R("w10"), I(1)))
                summary = replace(jump_tests.ByteJumpTableTests.recognize(), entry=entry,
                                  default_target=96, table_address=0x3000, branch_base=128,
                                  index_source="w10", index_register="x10", table_register="x11",
                                  offset_register="w13", branch_register="x12", limit=1,
                                  table_bytes=(0, 0), targets=(128, 128),
                                  prebranch_move=("x20", "x21"), requires_zero_extended_index=True)
                for start in (96, 128):
                    tail = (("mov", (R("x30"), R("x20"))),)
                    if signed:
                        tail += (("autiasp", ()),)
                    tail += (("ret", ()),)
                    body.extend(instruction(start + index * 4, mnemonic, *operands)
                                for index, (mnemonic, operands) in enumerate(tail))
                graph = {item.address: item for item in body}

                class Decoder:
                    def __init__(self, graph, summary, entry):
                        self.graph, self.summary, self.entry = graph, summary, entry
                        self.return_auth_profile = {"feature_pauth": 1, "feature_pauth_lr": 0}

                    def __call__(self, address):
                        return self.graph.get(address)

                    def byte_jump_table(self, address):
                        return self.summary if address == self.entry else None

                result = audit.Audit(Decoder(graph, summary, entry)).site(0)
                self.assertEqual(result["classification"], "guard-only", result["reasons"])
                self.assertEqual({r["pc"] for r in result["accesses"]}, {"0x8"})

    def test_zero_upper_facts_are_copied_partitioned_and_invalidated_by_x_writes(self):
        state = audit.State()
        state.set("w10", audit.CLEAN)
        self.assertIn("x10", state.zero_upper)
        clone = state.copy()
        self.assertEqual(state.fingerprint(), clone.fingerprint())
        clone.set("x10", audit.CLEAN)
        self.assertNotIn("x10", clone.zero_upper)
        self.assertIn("x10", state.zero_upper)
        self.assertNotEqual(state.fingerprint(), clone.fingerprint())
        for mutate in (lambda s: s.set_control("x10", audit.CLEAN),
                       lambda s: s.set_signature("x10", None)):
            clone = state.copy()
            mutate(clone)
            self.assertNotIn("x10", clone.zero_upper)

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
