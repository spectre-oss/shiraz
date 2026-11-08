# SPDX-License-Identifier: GPL-3.0-only
"""Collector result refusal checks; no hardware execution is claimed here."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from macos_guest_return_auth_local import validate_execution_marker

MARKER = ("M5_ATOMIC_EFFECTS_PASS guest_elf=1 exact_body=1 cases=10 cas_cases=36 "
          "cas_orderings=3 sp_lr=1 nzcv=1 fp_control=1 vector_samples=2 "
          "android_execution=0 app_execution=0")


class LocalReturnResultTests(unittest.TestCase):
    def test_exact_result_accepts_unrelated_diagnostics(self):
        self.assertEqual(validate_execution_marker("diagnostic\n" + MARKER + "\n", 0), MARKER)

    def test_failed_or_duplicate_execution_cannot_be_a_success(self):
        for output, code in ((MARKER, 1), (MARKER + "\n" + MARKER, 0), ("", 0)):
            with self.assertRaises(ValueError):
                validate_execution_marker(output, code)

    def test_partial_substrings_missing_fields_and_wrong_effects_are_refused(self):
        for output in (MARKER.replace("cases=10", "cases=100"),
                       MARKER.replace("cas_cases=36", "cas_cases=360"),
                       MARKER.replace("sp_lr=1", "sp_lr=0"),
                       MARKER.replace(" exact_body=1", "")):
            with self.assertRaises(ValueError):
                validate_execution_marker(output, 0)

    def test_duplicate_and_malformed_fields_are_refused(self):
        for suffix in (" cases=10", " malformed", " malformed=a=b"):
            with self.assertRaises(ValueError):
                validate_execution_marker(MARKER + suffix, 0)


if __name__ == "__main__":
    unittest.main()
