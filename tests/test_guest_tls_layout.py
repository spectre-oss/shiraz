# SPDX-License-Identifier: GPL-3.0-only
"""Compiler-output refusal checks; no bionic or hardware execution proof."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from generate_guest_tls_layout import parse_layout_ir

VALUES = {"TlsMinSlot": -1, "TlsMaxSlot": 7, "TlsSlotCount": 9,
          "TlsStackGuardSlot": 5, "TlsPointerBytes": 8, "TlsStackGuardOffset": 40}


def ir(values):
    return "\n".join(f"@layout_{name} = constant i64 {value}" for name, value in values.items())


class TlsLayoutTests(unittest.TestCase):
    def test_exact_arm64_facts(self):
        self.assertEqual(parse_layout_ir(ir(VALUES)), VALUES)

    def test_missing_duplicate_and_unknown_constants_refused(self):
        for text in (ir({k: v for k, v in VALUES.items() if k != "TlsMinSlot"}),
                     ir(VALUES) + "\n@layout_TlsMinSlot = constant i64 -1",
                     ir({**VALUES, "Unknown": 1})):
            with self.assertRaises(ValueError):
                parse_layout_ir(text)

    def test_inconsistent_and_wrong_architecture_facts_refused(self):
        for key, value in (("TlsMinSlot", -100), ("TlsMaxSlot", 100),
                           ("TlsSlotCount", 8), ("TlsStackGuardSlot", -1),
                           ("TlsPointerBytes", 4), ("TlsStackGuardOffset", 41)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                parse_layout_ir(ir({**VALUES, key: value}))


if __name__ == "__main__":
    unittest.main()
