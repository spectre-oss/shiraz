# SPDX-License-Identifier: GPL-3.0-only
"""Result-parser refusals only; these tests do not establish ART execution."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from macos_guest_jni_descriptor_local import PASS, validate_result, validate_signature

REFUSAL = "SHIRAZ_GUEST_FATAL invalid guest JNI method descriptor\nFatal signal 6 (SIGABRT), code 0 (?)\n"


class DescriptorResultTests(unittest.TestCase):
    def test_hardened_signature_requires_team_runtime_and_exact_entitlements(self):
        signature = "flags=0x10000(runtime)\nTeamIdentifier=TEAM123\n"
        jit = {"com.apple.security.cs.allow-jit": True}
        validate_signature(signature, jit, "TEAM123", True)
        validate_signature(signature, {}, "TEAM123", False)
        for shown, entitlements, team, main in (
            (signature.replace("10000", "0"), jit, "TEAM123", True),
            (signature, jit, "OTHER", True), (signature, {}, "TEAM123", True),
            (signature, jit, "TEAM123", False),
            (signature, {**jit, "com.apple.security.cs.disable-library-validation": True}, "TEAM123", True),
            (signature.replace("TEAM123", "not set"), jit, "not set", True),
        ):
            with self.assertRaises(ValueError):
                validate_signature(shown, entitlements, team, main)

    def test_complete_positive_and_specific_art_refusal_accept(self):
        validate_result(PASS + "\n", "", 0, True)
        validate_result("", REFUSAL, 1, False)
        validate_result("", REFUSAL, -6, False)

    def test_unrelated_failure_or_normal_exit_cannot_prove_refusal(self):
        for output, code in ((REFUSAL, 0), (REFUSAL, 134), (REFUSAL, -11),
                             (REFUSAL.replace("method descriptor", "native load"), 1),
                             (REFUSAL.replace("Fatal signal 6", "Fatal signal 11"), 1)):
            with self.assertRaises(ValueError):
                validate_result("", output, code, False)

    def test_duplicate_or_contradictory_results_refused(self):
        for output in (REFUSAL + REFUSAL, REFUSAL + PASS):
            with self.assertRaises(ValueError):
                validate_result("", output, 1, False)
        with self.assertRaises(ValueError):
            validate_result(PASS + "\n", PASS + "\n", 0, True)

    def test_partial_or_failed_positive_is_not_execution_proof(self):
        for output, code in ((PASS.replace("mixed24=1", "mixed24=0"), 0), (PASS, 1), ("", 0)):
            with self.assertRaises(ValueError):
                validate_result(output, "", code, True)


if __name__ == "__main__":
    unittest.main()
