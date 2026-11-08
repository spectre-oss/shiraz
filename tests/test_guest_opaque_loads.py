# SPDX-License-Identifier: GPL-3.0-only
"""Experimental opaque-load transfers; synthetic graphs are not ELF proof."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_guest_tpidr as a
from guest_callee_effects import Atomic32Effects, AtomicPath


def r(name):
    return a.Operand("reg", name)


def imm(value):
    return a.Operand("imm", value)


def mem(base, offset=0):
    return a.Operand("mem", base=base, displacement=offset)


def ins(pc, op, *args, **kw):
    return a.Instruction(pc, op, args, **kw)


class OpaqueLoadTests(unittest.TestCase):
    def run_case(self, middle=(), *, experimental=True, load=None, cleanup=True, atomic=False):
        graph = {i.address: i for i in [
            ins(0, "mrs", r("x19")), ins(4, "ldr", r("x8"), mem("x19", 40)),
            ins(8, "str", r("x19"), mem("sp")),
            load or ins(12, "ldr", r("x10"), mem("x1")), *middle]}
        if cleanup:
            end = max(graph) + 4
            for index in range(31):
                graph[end + 4 * index] = ins(end + 4 * index, "mov", r(f"x{index}"), r("xzr"))
            graph[end + 124] = ins(end + 124, "str", r("xzr"), mem("sp"))
            graph[end + 128] = ins(end + 128, "str", r("xzr"), mem("sp", 8))
            graph[end + 132] = ins(end + 132, "ret")

        class Decoder:
            def __init__(self):
                self.return_auth_profile = {"feature_pauth": 1, "feature_pauth_lr": 0}

            def __call__(self, pc):
                return graph.get(pc)

            def atomic32(self, pc):
                return Atomic32Effects(16, 48, "a" * 64, 0x1000,
                    (AtomicPath("synthetic", 20, ("x0", "x16")),)) if atomic and pc == 16 else None

        return a.Audit(Decoder(), track_opaque_loads=experimental).site(0)

    def reasons(self, report):
        return {item["reason"] for item in report["reasons"]}

    def test_only_explicit_experimental_mode_accepts_a_dead_gpr_load(self):
        report = self.run_case()
        self.assertEqual(report["classification"], "guard-only")
        self.assertTrue(report["opaque_loads"])
        self.assertEqual(self.run_case(experimental=False)["classification"], "unknown")

    def test_fullwidth_copy_select_and_exact_spill_can_die_without_escape(self):
        for middle in ([ins(16, "mov", r("x11"), r("x10"))],
                       [ins(16, "csel", r("x11"), r("x10"), r("x0"))],
                       [ins(16, "str", r("x10"), mem("sp", 8)),
                        ins(20, "ldr", r("x11"), mem("sp", 8))]):
            self.assertEqual(self.run_case(middle)["classification"], "guard-only")

    def test_opaque_address_never_becomes_guard_and_mixed_diagnostics_serialize(self):
        report = self.run_case([ins(16, "csel", r("x11"), r("x10"), r("x19")),
                                ins(20, "ldr", r("x8"), mem("x11", 40))])
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("opaque-thread-pointer-memory-base", self.reasons(report))
        self.assertTrue(any(item.get("value_kind") == "opaque-derived" for item in report["accesses"]))
        json.dumps(report)

    def test_arithmetic_narrowing_and_generic_consumers_stay_refused(self):
        for body in ([ins(16, "add", r("x11"), r("x10"), imm(0))],
                     [ins(16, "mov", r("w11"), r("w10"))],
                     [ins(16, "fmov", r("d0"), r("x10"), reads=("x10",), writes=("d0",))]):
            self.assertEqual(self.run_case(body)["classification"], "unknown")

    def test_escape_return_and_authentication_never_treat_opaque_as_control(self):
        for body in ([ins(16, "str", r("x10"), mem("x2"))],
                     [ins(16, "mov", r("x30"), r("x10")), ins(20, "ret")],
                     [ins(16, "mov", r("x30"), r("x10")), ins(20, "paciasp")]):
            self.assertEqual(self.run_case(body)["classification"], "unknown")
        report = self.run_case([ins(16, "mov", r("x19"), r("xzr")),
                                ins(20, "str", r("xzr"), mem("sp")), ins(24, "ret")], cleanup=False)
        self.assertEqual(report["classification"], "unknown")

    def test_literal_partial_gpr_and_pair_loads_track_but_vectors_refuse(self):
        for load in (ins(12, "ldr", r("x10"), imm(0x4000)),
                     ins(12, "ldrsw", r("x10"), mem("x1"), width=4),
                     ins(12, "ldp", r("x10"), r("x11"), mem("x1"))):
            self.assertEqual(self.run_case(load=load)["classification"], "guard-only")
        for load in (ins(12, "ldr", r("d0"), mem("x1")),
                     ins(12, "ldr", r("q0"), imm(0x4000), width=16)):
            self.assertEqual(self.run_case(load=load)["classification"], "unknown")

    def test_writeback_and_partial_spills_refuse_without_type_confusion(self):
        for body in ([ins(16, "ldr", r("x8"), mem("x10"), writeback=True, post_index=8)],
                     [ins(16, "str", r("w10"), mem("sp", 12), width=4)]):
            self.assertEqual(self.run_case(body)["classification"], "unknown")

    def test_opaque_value_is_nonnumeric_copied_and_partitioned(self):
        self.assertNotIsInstance(a.OPAQUE, int)
        state = a.State(registers={"x10": a.OPAQUE_VALUE})
        self.assertEqual(state.fingerprint(), state.copy().fingerprint())
        self.assertNotEqual(state.fingerprint(), a.State().fingerprint())
        with self.assertRaises(TypeError):
            a.OPAQUE + 40

    def test_atomic_unknown_memory_keeps_its_original_alias_refusal(self):
        report = self.run_case([ins(16, "bti"), ins(20, "nop")], atomic=True)
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(report))

    def test_vector_view_cannot_launder_a_known_or_opaque_spill(self):
        body = [ins(16, "str", r("x10"), mem("sp", 8)),
                ins(20, "ldr", r("d0"), mem("sp", 8)),
                ins(24, "fmov", r("w0"), r("s0"), reads=("s0",), writes=("w0",)),
                ins(28, "fmov", r("d0"), r("xzr"), reads=("xzr",), writes=("d0",)),
                ins(32, "mov", r("x19"), r("xzr")), ins(36, "mov", r("x10"), r("xzr")),
                ins(40, "str", r("xzr"), mem("sp")), ins(44, "str", r("xzr"), mem("sp", 8)),
                ins(48, "str", r("w0"), mem("x2"), width=4), ins(52, "ret")]
        report = self.run_case(body, cleanup=False)
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("tainted-vector-stack-reload-unmodelled", self.reasons(report))
        known_body = [ins(16, "fmov", r("w0"), r("s0"), reads=("s0",), writes=("w0",)),
                      ins(20, "fmov", r("d0"), r("xzr"), reads=("xzr",), writes=("d0",)),
                      ins(24, "mov", r("x19"), r("xzr")), ins(28, "str", r("xzr"), mem("sp")),
                      ins(32, "str", r("w0"), mem("x2"), width=4), ins(36, "ret")]
        for experimental in (False, True):
            report = self.run_case(known_body, load=ins(12, "ldr", r("d0"), mem("sp")),
                                   experimental=experimental, cleanup=False)
            self.assertEqual(report["classification"], "unknown")
            self.assertIn("tainted-vector-stack-reload-unmodelled", self.reasons(report))

    def test_known_rmw_address_does_not_prove_flag_load_stack_separation(self):
        graph = {i.address: i for i in [
            ins(0, "mrs", r("x19")), ins(4, "ldr", r("x8"), mem("x19", 40)),
            ins(8, "str", r("x19"), mem("sp")), ins(12, "mov", r("x19"), r("xzr")),
            ins(16, "add", r("x1"), r("sp"), imm(16)), ins(20, "mov", r("x0"), imm(1)),
            ins(24, "bl", imm(64)), ins(28, "str", r("xzr"), mem("sp")),
            ins(32, "str", r("w16"), mem("x2"), width=4),
            ins(36, "ldr", r("x30"), mem("sp", 8)), ins(40, "ret"),
            ins(64, "bti"), ins(84, "ret")]}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def atomic32(self, pc):
                return Atomic32Effects(64, 48, "a" * 64, 4096,
                    (AtomicPath("lse", 84, ("x0", "x16")),)) if pc == 64 else None

        for experimental in (False, True):
            report = a.Audit(Decoder(), track_opaque_loads=experimental).site(0)
            self.assertEqual(report["classification"], "unknown")
            self.assertIn("atomic-flag-load-may-alias-thread-pointer-stack-spill", self.reasons(report))


if __name__ == "__main__":
    unittest.main()
