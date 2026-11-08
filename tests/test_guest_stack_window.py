# SPDX-License-Identifier: GPL-3.0-only
"""Observed symbolic windows are diagnostics, never installed runtime proofs."""

import unittest

from test_guest_tpidr_audit import I, M, R, audit, instruction


class StackWindowTests(unittest.TestCase):
    def report(self, items, **options):
        graph = {item.address: item for item in items}
        return audit.Audit(graph.get).site(0, **options)

    def test_sp_changes_and_reseed_keep_first_mrs_anchor(self):
        report = self.report(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp", 24)),
                instruction(8, "sub", R("sp"), R("sp"), I(32)),
                instruction(12, "str", R("x19"), M("sp", 8)),
                instruction(16, "mrs", R("x20")),
                instruction(20, "str", R("x20"), M("sp", 16)),
                instruction(24, "ret"),
            ]
        )
        window = report["observed_stack_fact_window"]
        self.assertEqual(window["abstract_anchor_sp"], 0)
        self.assertEqual(
            window["windows"]["thread_pointer"], {"min_offset": -24, "end_offset": 32}
        )
        self.assertFalse(window["runtime_enforced"])

    def test_entry_frame_coordinates_are_not_mrs_coordinates(self):
        graph = {
            item.address: item
            for item in [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp", 16)),
                instruction(8, "ret"),
            ]
        }

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def caller_frame(self, pc):
                return audit.State(sp=-48, stack_returns={-8: frozenset({1234})}), {
                    "status": "synthetic-entry-context"
                }

        report = audit.Audit(Decoder()).site(0, entry_context=True)
        window = report["observed_stack_fact_window"]
        self.assertEqual(window["abstract_anchor_sp"], -48)
        self.assertEqual(
            window["windows"]["thread_pointer"], {"min_offset": 16, "end_offset": 24}
        )
        self.assertEqual(
            window["windows"]["protected_facts"], {"min_offset": 16, "end_offset": 48}
        )
        self.assertIn("initial stack origin unproved", report["entry_frame"]["scope"])

    def test_window_cannot_waive_unknown_load_alias(self):
        report = self.report(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp", 24)),
                instruction(8, "ldr", R("x8"), M("x0")),
                instruction(12, "ret"),
            ]
        )
        self.assertEqual(report["classification"], "unknown")
        self.assertIn(
            "unmodelled-load-may-alias-thread-pointer-stack-spill",
            {item["reason"] for item in report["reasons"]},
        )
        self.assertFalse(report["observed_stack_fact_window"]["runtime_enforced"])


if __name__ == "__main__":
    unittest.main()
