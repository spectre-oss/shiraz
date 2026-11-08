# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic contract/CFG checks, never native execution or full TLS proof."""

import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_allocator_contract import MALLOC_ABI, managed_malloc_binding
from test_guest_native_target import self_fixture
from test_guest_tpidr_audit import BindingElfFixture, I, M, R, audit, instruction


def malloc_binding():
    return {
        "relocation": 0x1500, "relocation_type": 1026, "addend": 0,
        "symbol": "malloc", "version": "", "provider": "libc.so",
        "provider_elf_sha256": None, "target": None, "kind": "function",
        "abi": MALLOC_ABI, "nonreturning": False,
    }


class AllocatorBindingTests(unittest.TestCase):
    def test_contract_requires_exact_native_row_and_types(self):
        self.assertTrue(managed_malloc_binding(malloc_binding()))
        self.assertTrue(managed_malloc_binding({**malloc_binding(), "version": "LIBC"}))
        self.assertFalse(managed_malloc_binding({**malloc_binding(), "abi": "fixed/errno-null"}))
        for field, value in (
            ("symbol", "calloc"), ("version", "LIBC_O"), ("provider", "other.so"),
            ("kind", "object"), ("target", 0), ("provider_elf_sha256", "a" * 64),
            ("addend", False), ("addend", 4), ("relocation_type", True),
            ("relocation_type", 257), ("nonreturning", True), ("nonreturning", 0),
            ("native_target", None),
        ):
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                managed_malloc_binding({**malloc_binding(), field: value})

    def test_resolver_needs_explicit_bridge_and_independent_elf_binding(self):
        fixture = BindingElfFixture()
        fixture.data[0x1301:0x1308] = b"malloc\0"
        with tempfile.TemporaryDirectory() as tmp:
            bridge = Path(tmp) / "bridge.so"
            bridge.write_bytes(b"synthetic first-party identity, not executable")

            def proof():
                value = fixture.proof()
                value["modules"][0]["bindings"] = [malloc_binding()]
                value["native_bridge"] = {
                    "path": str(bridge.resolve()),
                    "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                    "verification": "observed-before-and-after-process",
                }
                return value

            self.assertEqual(fixture.metadata(proof()).resolve(0x1500)["reason"],
                             "managed-malloc-contract-untrusted")
            selected = fixture.metadata(proof(), bridge).resolve(0x1500)
            self.assertEqual(selected["effect"], "managed-malloc")
            self.assertIsNone(selected["target"])
            wrong_version = proof()
            wrong_version["modules"][0]["bindings"][0]["version"] = "LIBC"
            with self.assertRaisesRegex(ValueError, "symbol/version"):
                fixture.metadata(wrong_version, bridge).resolve(0x1500)
            changed = proof()
            changed["modules"][0]["bindings"][0]["relocation_type"] = 1025
            self.assertNotIn("effect", fixture.metadata(changed, bridge).resolve(0x1500))
            struct.pack_into("<q", fixture.data, 0x1410, 4)
            self.assertNotIn("effect", fixture.metadata(proof(), bridge).resolve(0x1500))
            struct.pack_into("<q", fixture.data, 0x1410, 0)
            fixture.data[0x121C] = 0x11  # Independent source symbol is an object.
            self.assertNotIn("effect", fixture.metadata(proof(), bridge).resolve(0x1500))
            fixture.data[0x121C] = 0x12
            stale = proof()
            bridge.write_bytes(b"changed native bridge")
            with self.assertRaisesRegex(ValueError, "bridge identity"):
                fixture.metadata(stale, bridge)

    def test_named_import_requires_its_independent_needed_library(self):
        fixture = self_fixture()
        fixture.data[0x1301:0x1308] = b"malloc\0"
        with tempfile.TemporaryDirectory() as tmp:
            bridge = Path(tmp) / "bridge.so"
            bridge.write_bytes(b"synthetic native identity")

            def proof():
                value = fixture.proof()
                value["modules"][0]["bindings"] = [{**malloc_binding(), "version": "LIBC"}]
                value["native_bridge"] = {
                    "path": str(bridge.resolve()),
                    "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                    "verification": "observed-before-and-after-process",
                }
                return value

            self.assertEqual(fixture.metadata(proof(), bridge).resolve(0x1500)["effect"], "managed-malloc")
            filename = next(value for tag, value in fixture.tags if tag == 1)
            fixture.data[0x1300 + filename:0x1300 + filename + 8] = b"other.c\0"
            result = fixture.metadata(proof(), bridge).resolve(0x1500)
            self.assertEqual(result["reason"], "managed-malloc-binding-mismatch")
            self.assertNotIn("effect", result)


class ManagedMallocAuditTests(unittest.TestCase):
    def report(self, body, *, profile=True, initial=None, plt=100, atomic=None):
        graph = {item.address: item for item in body}
        graph[plt] = instruction(plt, "adrp", R("x16"), I(0x8000))

        class Decoder:
            def __init__(self):
                self.return_auth_profile = {"feature_pauth": 1, "feature_pauth_lr": 0}

            def __call__(self, pc):
                return graph.get(pc)

            def plt(self, pc):
                return ({"target": None, "effect": "managed-malloc", "reason": "synthetic-contract"}
                        if pc == plt else None)

            def mrs_stack_window(self, pc):
                return ({"profile": 1, "before": 4096, "after": 256,
                         "image_stack_exclusion_profile": 1} if profile else None)

            def caller_frame(self, pc):
                return initial.copy(), {"status": "synthetic-initial-state"}

            def owned_image_data(self, address, width):
                return 0x8000 <= address and address + width <= 0x8100

            def atomic32(self, pc):
                return atomic if atomic is not None and pc == atomic.entry else None

        return audit.Audit(Decoder()).site(0, entry_context=initial is not None)

    @staticmethod
    def reasons(report):
        return {row["reason"] for row in report["reasons"]}

    @staticmethod
    def basic(spill=24):
        return [instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x30"), M("sp", 16)),
                instruction(8, "str", R("x19"), M("sp", spill)),
                instruction(12, "bl", I(100)),
                instruction(16, "ldr", R("x8"), M("x19", 40)),
                instruction(20, "mov", R("x19"), R("xzr")),
                instruction(24, "str", R("xzr"), M("sp", spill)),
                instruction(28, "ldr", R("x30"), M("sp", 16)),
                instruction(32, "ret")]

    def test_crossing_preserves_guard_but_never_claims_full_native_spill_proof(self):
        report = self.report(self.basic())
        self.assertEqual(len(report["conditional_managed_malloc"]), 1)
        self.assertEqual(report["conditional_managed_malloc"][0]["continuation"], "0x10")
        self.assertTrue(any(row["pc"] == "0x10" and row["offset"] == 40
                            for row in report["accesses"]))
        self.assertIn("native-callee-spills-return-needs-caller-context", self.reasons(report))
        self.assertEqual(report["classification"], "unknown")
        effect = report["callee_effects"][0]
        self.assertEqual(effect["kind"], "conditional-managed-malloc")
        self.assertTrue(effect["assumptions"])
        self.assertIn("no heap", effect["result"])

    def test_missing_window_and_retired_or_outside_facts_reject_crossing(self):
        report = self.report(self.basic(), profile=False)
        self.assertIn("managed-malloc-mrs-window-unproved", self.reasons(report))
        for spill in (-8, 256):
            report = self.report(self.basic(spill))
            self.assertFalse(report["conditional_managed_malloc"])
            self.assertIn("managed-malloc-protected-stack-facts-outside-active-window", self.reasons(report))
        initial = audit.State(stack_returns={256: frozenset({0x1234})})
        report = self.report(self.basic(), initial=initial)
        self.assertIn("managed-malloc-protected-stack-facts-outside-active-window", self.reasons(report))

    def test_size_pointer_control_signature_and_simd_facts_are_refused(self):
        for initial in (
            audit.State(registers={"x0": frozenset({0})}),
            audit.State(aliases={"x0": frozenset({0})}),
            audit.State(return_values={"x0": frozenset({12})}),
            audit.State(return_signatures={"x0": audit.ReturnSignature(frozenset({12}), 0, "A")}),
        ):
            report = self.report(self.basic(), initial=initial)
            self.assertIn("managed-malloc-size-has-pointer-or-control-fact", self.reasons(report))
            self.assertFalse(report["conditional_managed_malloc"])
        report = self.report(self.basic(), initial=audit.State(registers={"d8": frozenset({0})}))
        self.assertIn("managed-malloc-simd-facts-unmodelled", self.reasons(report))

    def test_tail_veneer_bad_lr_and_bad_sp_do_not_get_a_continuation(self):
        cases = [
            ([instruction(0, "mrs", R("x19")), instruction(4, "b", I(100))],
             "managed-malloc-return-continuation-unproved"),
            ([instruction(0, "mrs", R("x19")), instruction(4, "bl", I(80)),
              instruction(80, "b", I(100))], "managed-malloc-direct-bl-unproved"),
            ([instruction(0, "mrs", R("x19")), instruction(4, "bl", I(80)),
              instruction(80, "mov", R("x30"), R("xzr")), instruction(84, "b", I(100))],
             "managed-malloc-return-continuation-unproved"),
            ([instruction(0, "mrs", R("x19")), instruction(4, "bl", I(80)),
              instruction(80, "sub", R("sp"), R("sp"), I(16)), instruction(84, "b", I(100))],
             "managed-malloc-return-continuation-unproved"),
            ([instruction(0, "mrs", R("x19")), instruction(4, "bl", I(80)),
              instruction(80, "pacibsp"), instruction(84, "b", I(100))],
             "managed-malloc-return-continuation-unproved"),
        ]
        for body, reason in cases:
            report = self.report(body)
            self.assertIn(reason, self.reasons(report))
            self.assertFalse(report["conditional_managed_malloc"])

    def test_caller_saved_tp_and_image_facts_do_not_survive(self):
        report = self.report([
            instruction(0, "mrs", R("x19")), instruction(4, "mov", R("x1"), R("x19")),
            instruction(8, "adrp", R("x2"), I(0x8000)), instruction(12, "bl", I(100)),
            instruction(16, "ldr", R("x8"), M("x1", 40)),
            instruction(20, "ldr", R("x8"), M("x2")),
            instruction(24, "ldr", R("x8"), M("x19", 40)), instruction(28, "ret"),
        ])
        self.assertEqual([row["pc"] for row in report["accesses"] if row["kind"] == "guard"], ["0x18"])
        self.assertTrue(any(row["kind"] == "unsupported" and row["pc"] == "0x10"
                            for row in report["accesses"]))
        self.assertFalse(report["owned_image_accesses"])

    def test_native_spill_reload_stays_unknown_even_after_a_new_store(self):
        for overwrite in (False, True):
            body = [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(100))]
            if overwrite:
                body.append(instruction(8, "str", R("xzr"), M("sp", -8)))
            body.extend([instruction(12 if overwrite else 8, "ldr", R("x8"), M("sp", -8)),
                         instruction(16 if overwrite else 12, "ret")])
            report = self.report(body)
            self.assertIn("native-callee-stack-spill-reload-unproved", self.reasons(report))
        report = self.report([
            instruction(0, "mrs", R("x19")), instruction(4, "bl", I(100)),
            instruction(8, "mov", R("x19"), R("xzr")),
            instruction(12, "ldr", R("x8"), M("x0")), instruction(16, "ret"),
        ])
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(report))
        self.assertFalse(report["owned_image_accesses"])

    def test_preserved_lr_copy_must_be_restored_after_malloc(self):
        for restore in (False, True):
            body = [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(40)),
                    instruction(8, "ret"), instruction(40, "mov", R("x20"), R("x30")),
                    instruction(44, "bl", I(100))]
            if restore:
                body.append(instruction(48, "mov", R("x30"), R("x20")))
            body.append(instruction(52 if restore else 48, "ret"))
            report = self.report(body)
            self.assertTrue(report["conditional_managed_malloc"])
            if restore:
                self.assertNotIn("unproved-return-address", self.reasons(report))
                self.assertNotIn("thread-pointer-used-as-return-target", self.reasons(report))
            else:
                self.assertTrue(self.reasons(report) & {
                    "unproved-return-address", "thread-pointer-used-as-return-target"})

    def test_all_simd_alias_fact_kinds_refuse_native_crossing(self):
        for name in ("b0", "h7", "s8", "d8", "d15", "q8", "v8", "q31", "v31"):
            for field in ("registers", "aliases", "return_values"):
                with self.subTest(register=name, field=field):
                    initial = audit.State(**{field: {name: frozenset({0})}})
                    report = self.report(self.basic(), initial=initial)
                    self.assertFalse(report["conditional_managed_malloc"])
                    self.assertIn("managed-malloc-simd-facts-unmodelled", self.reasons(report))

    def test_native_spill_uncertainty_survives_clearing_visible_tp_and_literal_read(self):
        report = self.report([
            instruction(0, "mrs", R("x19")), instruction(4, "bl", I(100)),
            instruction(8, "mov", R("x19"), R("xzr")),
            instruction(12, "ldr", R("x8"), I(0x9000)), instruction(16, "ret"),
        ])
        self.assertTrue(report["conditional_managed_malloc"])
        self.assertIn("unmodelled-literal-load-may-alias-thread-pointer-stack-spill", self.reasons(report))
        self.assertEqual(report["classification"], "unknown")

    def test_native_spill_uncertainty_reaches_atomic_flag_and_rmw_reads(self):
        from guest_callee_effects import Atomic32Effects, AtomicPath

        effect = Atomic32Effects(32, 48, "a" * 64, 0x9000,
                                 (AtomicPath("lse", 52, ("x0", "x16")),))
        report = self.report([
            instruction(0, "mrs", R("x19")), instruction(4, "bl", I(100)),
            instruction(8, "mov", R("x19"), R("xzr")),
            instruction(12, "mov", R("x0"), R("xzr")),
            instruction(16, "mov", R("x1"), R("xzr")),
            instruction(20, "bl", I(32)), instruction(24, "ret"),
            instruction(32, "bti"), instruction(52, "ret"),
        ], atomic=effect)
        self.assertTrue(report["conditional_managed_malloc"])
        self.assertIn("atomic-flag-load-may-alias-thread-pointer-stack-spill", self.reasons(report))
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(report))
        self.assertEqual(report["classification"], "unknown")


if __name__ == "__main__":
    unittest.main()
