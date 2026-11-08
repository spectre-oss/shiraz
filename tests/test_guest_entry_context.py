# SPDX-License-Identifier: GPL-3.0-only
"""Conditional synthetic entry-frame checks, not complete caller provenance."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_guest_tpidr as a
from test_guest_tpidr_audit import I, M, R, instruction


class EntryContextTests(unittest.TestCase):
    def decoder(self, restore=True):
        graph = {item.address: item for item in [
            instruction(0, "sub", R("sp"), R("sp"), I(16)),
            instruction(4, "str", R("x29"), M("sp")),
            instruction(8, "mov", R("x29"), R("sp")),
            instruction(12, "mrs", R("x19")),
            instruction(16, "ldr", R("x8"), M("x19", 40)),
            instruction(20, "mov", R("x19"), R("xzr")),
            instruction(24, "mov", R("sp"), R("x29")),
            instruction(28, "add", R("sp"), R("sp"), I(16 if restore else 0)),
            instruction(32, "ret"),
        ]}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def caller_frame(self, pc):
                return a.caller_prologue(self, 0, pc), {"status": "validated-entry-context", "entry": "0x0"}

        return Decoder()

    def test_frame_prologue_enables_only_explicit_conditional_context(self):
        decoder = self.decoder()
        self.assertEqual(a.Audit(decoder).site(12)["classification"], "unknown")
        report = a.Audit(decoder).site(12, entry_context=True)
        self.assertEqual(report["classification"], "guard-only")
        self.assertIn("alternate entries", report["entry_frame"]["scope"])

    def test_function_entry_context_requires_restored_stack(self):
        report = a.Audit(self.decoder(False)).site(12, entry_context=True)
        self.assertEqual(report["classification"], "unknown")
        self.assertIn("entry-context-stack-not-restored-at-return", {x["reason"] for x in report["reasons"]})

    def test_no_metadata_or_conflicting_context_cannot_be_accepted(self):
        decoder = self.decoder()
        report = a.Audit(decoder.__call__).site(12, entry_context=True)
        self.assertEqual(report["classification"], "unknown")
        with self.assertRaises(ValueError):
            a.Audit(decoder).site(12, caller=8, entry_context=True)


if __name__ == "__main__":
    unittest.main()
