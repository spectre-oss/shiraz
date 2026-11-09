# SPDX-License-Identifier: GPL-3.0-only
"""Conditional byte-leaf range/fact checks, not complete TLS or native proof."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from test_guest_tpidr_audit import I, M, R, audit, instruction

a = audit


class StackCopyTests(unittest.TestCase):
    def populated(self):
        state = a.State(sp=-128)
        state.stack[-112] = frozenset({0})
        state.stack_aliases[-112] = frozenset({-120})
        state.stack_returns[-112] = frozenset({444})
        state.stack_images[-112] = 8192
        state.stack_signatures[-112] = a.ReturnSignature(frozenset({444}), -128, "A")
        return state

    def test_full_fact_copy_shifts_only_keys_and_never_mutates_input(self):
        state = self.populated()
        before = state.fingerprint()
        post = a.copied_stack_facts(state, -112, -80, 8)
        for domain in ("stack", "stack_aliases", "stack_returns", "stack_images", "stack_signatures"):
            self.assertEqual(getattr(post, domain)[-80], getattr(state, domain)[-112])
        self.assertEqual(post.stack_signatures[-80].sp, -128)
        self.assertEqual(state.fingerprint(), before)

    def test_overlap_uses_pre_call_snapshot_in_both_directions(self):
        for delta in (-8, 8):
            state = self.populated()
            state.stack[-104] = frozenset({40})
            post = a.copied_stack_facts(state, -112, -112 + delta, 16)
            self.assertEqual(post.stack[-112 + delta], frozenset({0}))
            self.assertEqual(post.stack[-104 + delta], frozenset({40}))

    def test_every_partial_source_and_destination_fact_is_refused(self):
        for domain in ("stack", "stack_aliases", "stack_returns", "stack_images", "stack_signatures"):
            state = self.populated()
            value = getattr(state, domain)[-112]
            for name in ("stack", "stack_aliases", "stack_returns", "stack_images", "stack_signatures"):
                getattr(state, name).clear()
            getattr(state, domain)[-112] = value
            for size in range(1, 8):
                for offset in range(-7, 8):
                    if not -112 + offset < -104 or not -112 < -112 + offset + size:
                        continue
                    for source, dest in ((-112 + offset, -80), (-80, -112 + offset)):
                        with self.subTest(domain=domain, size=size, offset=offset), self.assertRaises(a.MetadataUnavailable):
                            a.copied_stack_facts(state, source, dest, size)

    def test_unaligned_full_fact_and_extra_clean_bytes_are_supported(self):
        state = self.populated()
        post = a.copied_stack_facts(state, -113, -81, 10)
        self.assertEqual(post.stack[-80], frozenset({0}))

    def test_disjoint_image_source_erases_complete_destination_facts(self):
        state = self.populated()
        post = a.copied_stack_facts(state, None, -112, 8)
        for domain in ("stack", "stack_aliases", "stack_returns", "stack_images", "stack_signatures"):
            self.assertNotIn(-112, getattr(post, domain))

    def test_union_slot_limit_and_rollback(self):
        state = self.populated()
        state.stack_images[-64] = 123
        before = state.fingerprint()
        with patch.object(a, "MAX_STACK_SLOTS", 2), self.assertRaises(a.MetadataUnavailable):
            a.copied_stack_facts(state, -112, -80, 8)
        self.assertEqual(state.fingerprint(), before)


class ByteCopyFlowTests(unittest.TestCase):
    def report(self, *, size=8, source=16, destination=32, move=False, clear_copy=True,
               known_source=True, setup=(), tail=False, image=False, profile=True,
               native=False, native_below=None, seed=None, got_owned=True, destination_image=False,
               window_before=4096, window_after=256):
        body = [instruction(0, "mrs", R("x19")),
                instruction(4, "sub", R("sp"), R("sp"), I(128)),
                instruction(8, "str", R("x30"), M("sp", 120)),
                instruction(12, "str", R("x19"), M("sp", 16)),
                instruction(16, "add", R("x0"), R("sp"), I(destination)),
                instruction(20, "add", R("x1"), R("sp"), I(source)) if known_source
                else instruction(20, "mov", R("x1"), R("x9")),
                instruction(24, "mov", R("x2"), I(size)) if size is not None
                else instruction(24, "mov", R("x2"), R("x9")),
                instruction(28, "mov", R("x16"), R("x19")),
                instruction(32, "b" if tail else "bl", I(256)),
                instruction(36, "ldr", R("x20"), M("sp", destination)),
                instruction(40, "ldr", R("x8"), M("x20", 40)),
                instruction(44, "mov", R("x20"), R("xzr")),
                instruction(48, "mov", R("x19"), R("xzr")),
                instruction(52, "str", R("xzr"), M("sp", 16)),
                instruction(56, "str", R("xzr"), M("sp", destination)) if clear_copy
                else instruction(56, "nop"),
                instruction(60, "ldr", R("x30"), M("sp", 120)),
                instruction(64, "add", R("sp"), R("sp"), I(128)),
                instruction(68, "ret"), instruction(256, "adrp", R("x16"), I(0))]
        graph = {item.address: item for item in body}
        if destination_image:
            graph[16] = instruction(16, "adr", R("x0"), I(12288))
        if image:
            graph[20] = instruction(20, "adr", R("x1"), I(12288))
        graph.update({item.address: item for item in setup})

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def plt(self, pc):
                return ({"effect": "byte-memory", "abi": "memory/byte-move-v1" if move
                         else "memory/byte-copy-v1", "target": None, "read_slot": 8192} if pc == 256 else None)

            def owned_image_data(self, address, width):
                return (got_owned or address != 8192) and 8192 <= address < address + width <= (16384 if image or destination_image else 12288)

            def mrs_stack_window(self, pc):
                return ({"profile": 1, "before": window_before, "after": window_after,
                         "image_stack_exclusion_profile": 1} if profile else None)

            def caller_frame(self, pc):
                return (seed.copy() if seed is not None else a.State(
                    native_memory_may_contain_tp=native,
                    native_stack_clobber_below=native_below)), {"status": "synthetic"}

        return a.Audit(Decoder()).site(0, entry_context=native or native_below is not None or seed is not None)

    def reasons(self, report):
        return {row["reason"] for row in report["reasons"]}

    def test_copy_reaches_guard_and_clears_plt_and_leaf_scratch(self):
        report = self.report()
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(len(report["conditional_byte_copies"]), 1)
        self.assertEqual(report["conditional_byte_copies"][0]["bytes"], 8)
        self.assertTrue(any(row["pc"] == "0x28" and row["kind"] == "guard" for row in report["accesses"]))

    def test_copied_tp_fact_remains_live_until_erased(self):
        report = self.report(clear_copy=False)
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("thread-pointer-stack-return-needs-caller-context", self.reasons(report))

    def test_unknown_size_pointer_partial_frame_and_overlap_refuse(self):
        cases = (({"size": None}, "byte-memory-size-unproved-or-bound"),
                 ({"size": a.MAX_BYTE_COPY + 1}, "byte-memory-size-unproved-or-bound"),
                 ({"known_source": False}, "byte-memory-read-write-ranges-unproved"),
                 ({"size": 7}, "byte-memory-partial-protected-fact"),
                 ({"destination": 16}, "byte-memory-memcpy-overlap"),
                 ({"source": 127}, "byte-memory-read-write-ranges-unproved"),
                 ({"tail": True}, "byte-memory-return-continuation-unproved"),
                 ({"profile": False}, "byte-memory-plt-read-separation-unproved"))
        for arguments, reason in cases:
            report = self.report(**arguments)
            with self.subTest(arguments=arguments):
                self.assertEqual(report["classification"], "unknown")
                self.assertIn(reason, self.reasons(report))
                self.assertEqual(report["conditional_byte_copies"], [])

    def test_memmove_same_range_is_valid_and_preserves_guard_fact(self):
        report = self.report(destination=16, move=True)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])

    def test_zero_size_uses_no_unknown_pointer_read(self):
        report = self.report(size=0, known_source=False)
        self.assertEqual(len(report["conditional_byte_copies"]), 1)
        self.assertEqual(report["conditional_byte_copies"][0]["source_kind"], "unused")

    def test_image_read_needs_independent_separation_and_keeps_normal_scope(self):
        report = self.report(image=True, profile=True, setup=(
            instruction(36, "nop"), instruction(40, "ldr", R("x8"), M("x19", 40))))
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(report["conditional_byte_copies"][0]["source_kind"], "image")
        self.assertEqual(len(report["conditional_image_stack_separation"]), 2)

    def test_native_latent_copies_and_unproved_native_stack_reads_refuse(self):
        for arguments, reason in (({"native": True}, "byte-memory-latent-native-copies-unproved"),
                                  ({"native_below": 0}, "byte-memory-native-stack-read-unproved")):
            report = self.report(**arguments)
            self.assertIn(reason, self.reasons(report))
            self.assertEqual(report["conditional_byte_copies"], [])

    def test_tainted_size_and_tainted_pointer_are_refused(self):
        for operation, reason in ((instruction(24, "mov", R("x2"), R("x19")),
                                   "byte-memory-size-unproved-or-bound"),
                                  (instruction(20, "mov", R("x1"), R("x19")),
                                   "byte-memory-read-write-ranges-unproved")):
            self.assertIn(reason, self.reasons(self.report(setup=(operation,))))

    def test_unchanged_live_registers_are_not_silently_quarantined(self):
        report = self.report(setup=(instruction(28, "mov", R("x7"), R("x19")),))
        self.assertEqual(len(report["conditional_byte_copies"]), 1)
        self.assertIn("pointer-return-escape", self.reasons(report))

    def test_sticky_prior_failure_survives_admitted_copy(self):
        report = self.report(setup=(instruction(28, "ldr", R("x16"), M("x9")),))
        self.assertEqual(len(report["conditional_byte_copies"]), 1)
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(report))

    def test_plt_also_invalidates_old_x17_pointer_fact(self):
        seed = a.State(registers={"x17": frozenset({0})})
        report = self.report(seed=seed)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])

    def test_plt_scratch_values_are_unknown_rather_than_scalar_zero(self):
        for name in ("x16", "x17"):
            report = self.report(setup=(instruction(36, "mov", R("x2"), R(name)),
                                        instruction(40, "bl", I(256))))
            self.assertEqual(len(report["conditional_byte_copies"]), 1)
            self.assertIn("byte-memory-size-unproved-or-bound", self.reasons(report))

    def test_zero_count_preserves_old_flags_and_plt_clobbers_are_not_zero(self):
        seed = a.State(registers={"nzcv": frozenset({0})})
        report = self.report(size=0, known_source=False, seed=seed)
        self.assertEqual(len(report["conditional_byte_copies"]), 1)
        self.assertIn("live-pointer-return-needs-caller-context", self.reasons(report))
        report = self.report(seed=seed)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])

    def test_namespace_rebases_independent_got_read_slot(self):
        from types import SimpleNamespace

        from guest_namespace import STRIDE, NamespaceDecoder
        namespace = object.__new__(NamespaceDecoder)
        namespace.plt_cache = {}
        namespace.names = ["root", "provider"]
        decoder = SimpleNamespace(plt=lambda pc: {
            "effect": "byte-memory", "read_slot": 8192, "target": None})
        namespace.locate = lambda pc: (1, 256, decoder)
        self.assertEqual(namespace.plt(STRIDE + 256)["read_slot"], STRIDE + 8192)
        decoder.plt = lambda pc: {"effect": "byte-memory", "read_slot": STRIDE, "target": None}
        with self.assertRaises(ValueError):
            namespace.plt(STRIDE + 260)

    def test_missing_got_ownership_window_overflow_and_image_destination_refuse(self):
        for arguments, reason in (({"got_owned": False}, "byte-memory-plt-read-separation-unproved"),
                                  ({"window_before": 16}, "byte-memory-plt-read-separation-unproved"),
                                  ({"destination_image": True}, "byte-memory-read-write-ranges-unproved")):
            report = self.report(**arguments)
            self.assertIn(reason, self.reasons(report))
            self.assertEqual(report["conditional_byte_copies"], [])

    def test_mixed_pointer_provenance_and_indirect_tail_do_not_admit(self):
        seed = a.State(aliases={"x9": frozenset({None, -112})})
        self.assertIn("byte-memory-read-write-ranges-unproved",
                      self.reasons(self.report(known_source=False, seed=seed)))
        report = self.report(setup=(instruction(32, "bl", I(64)), instruction(64, "b", I(256))))
        self.assertIn("byte-memory-direct-bl-unproved", self.reasons(report))


class CopiedFactUseTests(unittest.TestCase):
    def run_body(self, state, body, caller=None):
        graph = {item.address: item for item in body}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def caller_frame(self, pc):
                return state.copy(), {"status": "synthetic-copied-state"}

        return a.Audit(Decoder()).site(0, caller=caller, entry_context=caller is None)

    def test_reloaded_copy_preserves_original_stack_alias_coordinates(self):
        state = a.State(sp=-128, stack_aliases={-112: frozenset({-120})})
        copied = a.copied_stack_facts(state, -112, -80, 8)
        report = self.run_body(copied, [
            instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x0"), M("sp", 48)),
            instruction(8, "str", R("x19"), M("x0")), instruction(12, "ldr", R("x20"), M("x0")),
            instruction(16, "ldr", R("x8"), M("x20", 40)),
            instruction(20, "mov", R("x19"), R("xzr")), instruction(24, "mov", R("x20"), R("xzr")),
            instruction(28, "str", R("xzr"), M("x0")), instruction(32, "add", R("sp"), R("sp"), I(128)),
            instruction(36, "ret")])
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertTrue(any(row["pc"] == "0x10" and row["kind"] == "guard" for row in report["accesses"]))

    def test_reloaded_return_copy_keeps_exact_unsigned_token(self):
        for token in (100, 104):
            state = a.State(sp=-128, stack_returns={-112: frozenset({token})})
            copied = a.copied_stack_facts(state, -112, -80, 8)
            report = self.run_body(copied, [
                instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x30"), M("sp", 48)),
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "mov", R("x19"), R("xzr")), instruction(16, "ret"),
                instruction(96, "bl", I(0)), instruction(100, "add", R("sp"), R("sp"), I(128)),
                instruction(104, "ret")], caller=96)
            self.assertEqual(report["classification"], "guard-only" if token == 100 else "unknown", report["reasons"])
            if token != 100:
                self.assertIn("unproved-return-address", {row["reason"] for row in report["reasons"]})
