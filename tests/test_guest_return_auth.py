# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic matching-return identities, not PAC cryptography/hardware tests."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_guest_tpidr as a
from guest_callee_effects import Atomic32Effects, AtomicPath

PROFILE = {"source": "synthetic-unit-only", "feature_pauth": 1, "feature_pauth_lr": 0}


def r(value):
    return a.Operand("reg", value)


def imm(value):
    return a.Operand("imm", value)


def mem(base, displacement=0):
    return a.Operand("mem", base=base, displacement=displacement)


def ins(pc, op, *operands, **kw):
    return a.Instruction(pc, op, operands, tpidr_read=op == "mrs", **kw)


class ReturnAuthenticationTests(unittest.TestCase):
    def run_body(self, body, profile=PROFILE, extra=()):
        graph = {i.address: i for i in [
            ins(0, "mrs", r("x19")), ins(4, "ldr", r("x8"), mem("x19", 40)),
            ins(8, "bl", imm(64)), ins(12, "mov", r("x19"), r("xzr")), ins(16, "ret"),
            *body, *extra]}

        class Decoder:
            return_auth_profile = profile

            def __call__(self, pc):
                return graph.get(pc)

            def atomic32(self, pc):
                return Atomic32Effects(192, 48, "a" * 64, 1024,
                    (AtomicPath("lse", 212, ("x0", "x16")),
                     AtomicPath("llsc", 236, ("x0", "x15", "x16", "x17")))) if pc == 192 else None

        return a.Audit(Decoder()).site(0)

    def reasons(self, report):
        return {row["reason"] for row in report["reasons"]}

    def framed_body(self, key="a", middle=()):
        return [ins(64, "paci" + key + "sp"), ins(68, "sub", r("sp"), r("sp"), imm(16)),
                ins(72, "str", r("x30"), mem("sp", 8)), *middle,
                ins(96, "ldr", r("x30"), mem("sp", 8)),
                ins(100, "add", r("sp"), r("sp"), imm(16)),
                ins(104, "auti" + key + "sp"), ins(108, "ret")]

    def test_exact_a_and_b_key_pairs_restore_plain_return_identity(self):
        for key in ("a", "b"):
            report = self.run_body([ins(64, "paci" + key + "sp"),
                                    ins(68, "auti" + key + "sp"), ins(72, "ret")])
            self.assertEqual(report["classification"], "guard-only")
            self.assertEqual(len(report["return_authentication"]), 2)

    def test_framed_spill_reload_survives_a_nested_guest_call(self):
        report = self.run_body(self.framed_body(middle=[ins(76, "bl", imm(128)), ins(80, "b", imm(96))]),
                               extra=[ins(128, "mov", r("x0"), imm(0)), ins(132, "ret")])
        self.assertEqual(report["classification"], "guard-only")

    def test_fullwidth_copy_and_pair_spill_restore_b_key_signature(self):
        report = self.run_body([
            ins(64, "pacibsp"), ins(68, "sub", r("sp"), r("sp"), imm(32)),
            ins(72, "mov", r("x9"), r("x30")),
            ins(76, "stp", r("x29"), r("x9"), mem("sp", 16)),
            ins(80, "mov", r("x30"), r("xzr")),
            ins(84, "ldp", r("x29"), r("x30"), mem("sp", 16)),
            ins(88, "add", r("sp"), r("sp"), imm(32)), ins(92, "autibsp"), ins(96, "ret")])
        self.assertEqual(report["classification"], "guard-only")

    def test_key_sp_double_sign_and_missing_pairs_are_refused(self):
        cases = [([ins(64, "paciasp"), ins(68, "autibsp"), ins(72, "ret")], "return-auth-key-or-sp-mismatch"),
                 ([ins(64, "paciasp"), ins(68, "sub", r("sp"), r("sp"), imm(16)),
                   ins(72, "autiasp"), ins(76, "ret")], "return-auth-key-or-sp-mismatch"),
                 ([ins(64, "paciasp"), ins(68, "paciasp"), ins(72, "ret")], "return-auth-double-sign"),
                 ([ins(64, "autiasp"), ins(68, "ret")], "return-auth-missing-signature"),
                 ([ins(64, "paciasp"), ins(68, "ret")], "authenticated-return-not-authenticated")]
        for body, reason in cases:
            report = self.run_body(body)
            self.assertEqual(report["classification"], "unknown")
            self.assertIn(reason, self.reasons(report))

    def test_register_clobber_narrowing_and_stripping_cannot_preserve_authentication(self):
        for middle in ([ins(68, "mov", r("x30"), r("xzr"))],
                       [ins(68, "mov", r("w9"), r("w30")), ins(72, "mov", r("x30"), r("x9"))],
                       [ins(68, "xpaclri")]):
            report = self.run_body([ins(64, "paciasp"), *middle, ins(76, "autiasp"), ins(80, "ret")])
            self.assertEqual(report["classification"], "unknown")

    def test_unknown_store_or_partial_overlap_invalidates_saved_signature(self):
        for store in (ins(76, "str", r("x0"), mem("x1")),
                      ins(76, "str", r("w0"), mem("sp", 12), width=4)):
            report = self.run_body(self.framed_body(middle=[store, ins(80, "b", imm(96))]))
            self.assertEqual(report["classification"], "unknown")
            self.assertIn("return-auth-missing-signature", self.reasons(report))

    def test_arithmetic_and_partial_load_cannot_authenticate_the_original_token(self):
        arithmetic = self.run_body([ins(64, "paciasp"),
            ins(68, "add", r("x30"), r("x30"), imm(1)), ins(72, "autiasp"), ins(76, "ret")])
        self.assertIn("authenticated-return-arithmetic", self.reasons(arithmetic))
        partial = self.run_body([
            ins(64, "paciasp"), ins(68, "sub", r("sp"), r("sp"), imm(16)),
            ins(72, "str", r("x30"), mem("sp", 8)),
            ins(76, "ldr", r("w9"), mem("sp", 8), width=4),
            ins(80, "mov", r("x30"), r("x9")),
            ins(84, "add", r("sp"), r("sp"), imm(16)), ins(88, "autiasp"), ins(92, "ret")])
        self.assertIn("partial-overlap-with-authenticated-return", self.reasons(partial))
        self.assertEqual(partial["classification"], "unknown")

    def test_atomic_write_invalidates_only_overlapping_saved_signature(self):
        for offset in (0, 8):
            report = self.run_body(self.framed_body(middle=[
                ins(76, "add", r("x1"), r("sp"), imm(offset)),
                ins(80, "bl", imm(192)), ins(84, "b", imm(96))]),
                extra=[ins(192, "bti"), ins(212, "ret"), ins(236, "ret")])
            self.assertEqual(report["classification"], "unknown" if offset == 8 else "guard-only")

    def test_atomic_clobbered_register_cannot_recover_signature(self):
        report = self.run_body([
            ins(64, "paciasp"), ins(68, "mov", r("x16"), r("x30")),
            ins(72, "bl", imm(192)), ins(76, "mov", r("x30"), r("x16")),
            ins(80, "autiasp"), ins(84, "ret")],
            extra=[ins(192, "bti"), ins(212, "ret"), ins(236, "ret")])
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("return-auth-missing-signature", self.reasons(report))

    def test_conditional_select_keeps_missing_signature_alternative(self):
        for alternative in ("x9", "xzr"):
            report = self.run_body([
                ins(64, "paciasp"), ins(68, "mov", r("x9"), r("x30")),
                ins(72, "csel", r("x30"), r("x9"), r(alternative)),
                ins(76, "autiasp"), ins(80, "ret")])
            self.assertEqual(report["classification"], "guard-only" if alternative == "x9" else "unknown")

    def test_unknown_newer_disabled_or_boolean_capability_is_not_a_legacy_profile(self):
        for profile in (None, {}, {**PROFILE, "feature_pauth_lr": 1},
                        {**PROFILE, "feature_pauth": 0},
                        {**PROFILE, "feature_pauth": True}, {**PROFILE, "feature_pauth_lr": False}):
            report = self.run_body([ins(64, "paciasp"), ins(68, "autiasp"), ins(72, "ret")], profile=profile)
            self.assertEqual(report["classification"], "unknown")
            self.assertIn("return-auth-profile-unproved", self.reasons(report))

    def test_system_write_and_unhandled_combined_returns_remain_refused(self):
        report = self.run_body([ins(64, "paciasp"), ins(68, "msr", a.Operand("other"), r("x0")),
                                ins(72, "autiasp"), ins(76, "ret")])
        self.assertIn("return-auth-system-state-write", self.reasons(report))
        for op in ("retaa", "retab"):
            report = self.run_body([ins(64, "paciasp"), ins(68, op)])
            self.assertIn("unhandled-control-transfer:" + op, self.reasons(report))

    def test_traced_pointer_is_not_treated_as_a_return_identity(self):
        for source in ("x19", "sp"):
            report = self.run_body([ins(64, "mov", r("x30"), r(source)),
                                    ins(68, "paciasp"), ins(72, "autiasp"), ins(76, "ret")])
            self.assertIn("return-authentication-of-traced-pointer", self.reasons(report))

    def test_signature_is_in_copy_partition_key_and_invalidated_on_register_write(self):
        state = a.State()
        state.set_signature("x30", a.ReturnSignature(frozenset({12}), 0, "A"))
        other = state.copy()
        self.assertEqual(state.fingerprint(), other.fingerprint())
        other.set("x30", a.CLEAN)
        self.assertIsNone(other.signature("x30"))
        self.assertNotEqual(state.fingerprint(), other.fingerprint())
        state.stack_signatures[8] = state.signature("x30")
        other = state.copy()
        other.stack_signatures.clear()
        self.assertNotEqual(state.fingerprint(), other.fingerprint())


if __name__ == "__main__":
    unittest.main()
