# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic decoder routing/identity checks, never guest execution evidence."""

import hashlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_guest_tpidr as audit
from guest_namespace import STRIDE, NamespaceDecoder


def ins(pc, op, *operands):
    return audit.Instruction(pc, op, operands, tpidr_read=op == "mrs",
                             control_transfer=op in {"b", "bl", "cbz", "cbnz", "tbz", "tbnz"} or op.startswith("b."))


def r(name):
    return audit.Operand("reg", name)


def i(value):
    return audit.Operand("imm", value)


def mem(base, displacement=0):
    return audit.Operand("mem", base=base, displacement=displacement)


class NamespaceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name).resolve()
        self.root_path = self.directory / "libroot.so"
        self.provider_path = self.directory / "libprovider.so"
        self.root_path.write_bytes(b"synthetic root identity only")
        self.provider_path.write_bytes(b"synthetic provider identity only")
        self.report = {"schema": 1, "namespace": "nativebridge", "snapshot": "post-link-before-open",
                       "modules": [{"name": path.name, "file": str(path),
                                    "elf_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                                   for path in (self.root_path, self.provider_path)]}
        self.report_path = self.directory / "bindings.json"
        self.root_graph = {x.address: x for x in [
            ins(0, "mrs", r("x19")), ins(4, "ldr", r("x8"), mem("x19", 40)),
            ins(8, "bl", i(64)), ins(12, "mov", r("x19"), r("xzr")), ins(16, "ret"),
            ins(64, "adrp", r("x16"), i(0))]}
        self.provider_graph = {x.address: x for x in [
            ins(32, "sub", r("sp"), r("sp"), i(16)), ins(36, "str", r("x30"), mem("sp", 8)),
            ins(40, "bl", i(80)), ins(44, "ldr", r("x30"), mem("sp", 8)),
            ins(48, "add", r("sp"), r("sp"), i(16)), ins(52, "ret"),
            ins(80, "mov", r("x0"), i(0)), ins(84, "ret"),
            ins(120, "adrp", r("x10"), i(0x100)), ins(124, "add", r("x10"), r("x10"), i(8))]}
        self.provider_name = "libprovider.so"

    def decoder(self, factory=None):
        self.report_path.write_text(json.dumps(self.report))
        owner = self

        class FakeDecoder:
            def __init__(self, path, report_path, bridge):
                self.data = path.read_bytes()
                self.bindings = SimpleNamespace(
                    name=path.name, proof_hash=hashlib.sha256(report_path.read_bytes()).hexdigest(),
                    symbol_count=2,
                    loads=[(0, 0x1000, 0, len(self.data), 4)], number=lambda value: int(value, 16),
                    proof={24: {"provider": owner.provider_name}})
                self.binding_metadata_unavailable = None
                self.identity = {"version": "synthetic-only"}
                self.graph = owner.root_graph if path == owner.root_path else owner.provider_graph

            def __call__(self, pc):
                return self.graph.get(pc)

            def plt(self, pc):
                if self.bindings.name == "libroot.so" and pc == 64:
                    return {"target": None, "slot": "0x18", "symbol": "work",
                            "reason": "external-or-preempted-namespace-binding"}
                return None

            def word(self, pc):
                return b"fake" if pc in self.graph else None

            def sites(self):
                return [0]

            def owned_image_data(self, address, width):
                return 0 <= address and address + width <= 0x1000

            def atomic32(self, pc):
                return None

            def cas64(self, pc):
                return None

        return NamespaceDecoder(self.root_path, self.report_path, self.directory, factory or FakeDecoder)

    def edge(self):
        # Only address routing is mocked here. The separate cross-binding test
        # module independently verifies actual metadata rejection/selection.
        return patch("guest_namespace.guest_cross_binding.verify_guest_target", return_value={
            "target": 32, "provider": "libprovider.so", "reason": "cross-module-production-binding"})

    def test_guest_cross_call_keeps_nested_sp_and_exact_return_pc(self):
        decoder = self.decoder()
        with self.edge() as verify:
            result = audit.Audit(decoder).site(0)
        self.assertEqual(result["classification"], "guard-only")
        verify.assert_called_once()
        self.assertTrue(any(x["target"] == hex(STRIDE + 80) for x in result["callees"]))
        self.assertEqual(result["plt_bindings"][0]["target_module"], "libprovider.so")

    def test_foreign_mrs_is_reseeded_and_unsafe_use_is_not_erased(self):
        self.provider_graph = {x.address: x for x in [
            ins(32, "mrs", r("x20")), ins(36, "ldr", r("x8"), mem("x20", 8)),
            ins(40, "mov", r("x20"), r("xzr")), ins(44, "ret")]}
        with self.edge():
            result = audit.Audit(self.decoder()).site(0)
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", {x["reason"] for x in result["reasons"]})

    def test_native_provider_has_no_guest_body_fallback(self):
        self.provider_name = "libc.so"
        with self.edge() as verify:
            result = audit.Audit(self.decoder()).site(0)
        verify.assert_not_called()
        self.assertEqual(result["classification"], "unknown")

    def test_cross_symbol_work_budget_refuses_without_guessing_a_target(self):
        with patch("guest_namespace.MAX_CROSS_SYMBOL_WORK", 1), self.edge() as verify:
            result = audit.Audit(self.decoder()).site(0)
        verify.assert_not_called()
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("PLT-binding:cross-binding-symbol-work-budget", {x["reason"] for x in result["reasons"]})

    def test_pc_relative_operands_rebase_but_scalar_immediates_do_not(self):
        decoder = self.decoder()
        self.assertEqual(decoder(STRIDE + 40).operands[0].value, STRIDE + 80)
        self.assertEqual(decoder(STRIDE + 120).operands[1].value, STRIDE + 0x100)
        self.assertEqual(decoder(STRIDE + 124).operands[2].value, 8)
        self.assertTrue(decoder.owned_image_data(STRIDE + 0x180, 8))
        self.assertFalse(decoder.owned_image_data(STRIDE - 4, 8))
        self.assertIsNone(decoder.locate(-1))
        self.assertIsNone(decoder.locate(True))
        self.assertIsNone(decoder(2 * STRIDE))
        self.assertEqual(decoder.sites(), [0])

    def test_image_arithmetic_cannot_change_origin_module(self):
        self.root_graph.update({x.address: x for x in [
            ins(8, "adr", r("x10"), i(STRIDE - 4)),
            ins(12, "add", r("x10"), r("x10"), i(8)),
            ins(16, "ldr", r("x11"), mem("x10")),
            ins(20, "mov", r("x19"), r("xzr")), ins(24, "ret")]})
        decoder = self.decoder()
        self.assertIsNone(decoder.adjust_image_address(STRIDE - 4, 8))
        self.assertIsNone(decoder.adjust_image_address(STRIDE + 4, -8))
        self.assertEqual(decoder.adjust_image_address(STRIDE + 4, 8), STRIDE + 12)
        result = audit.Audit(decoder).site(0)
        self.assertFalse(result["owned_image_accesses"])

    def test_synthetic_address_space_is_explicit_and_deterministic(self):
        decoder = self.decoder()
        self.assertIn("not-native-addresses", decoder.address_space["kind"])
        self.assertEqual(decoder.address_space["modules"][0]["base"], "0x0")
        self.assertEqual(decoder.address_space["modules"][1]["base"], hex(STRIDE))

    def test_branch_bit_index_stays_scalar_and_outside_slot_target_is_refused(self):
        self.provider_graph[140] = ins(140, "tbnz", r("x0"), i(5), i(80))
        decoder = self.decoder()
        decoded = decoder(STRIDE + 140)
        self.assertEqual(decoded.operands[1].value, 5)
        self.assertEqual(decoded.operands[2].value, STRIDE + 80)
        self.provider_graph[32] = ins(32, "b", i(-4))
        with self.edge():
            result = audit.Audit(self.decoder()).site(0)
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unhandled-control-transfer:b", {x["reason"] for x in result["reasons"]})

    def test_changed_or_escaped_files_are_rejected_before_decoder(self):
        self.report["modules"][1]["elf_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            self.decoder()
        self.report["modules"][1]["elf_sha256"] = hashlib.sha256(self.provider_path.read_bytes()).hexdigest()
        self.report["modules"][1]["file"] = str(self.root_path)
        with self.assertRaisesRegex(ValueError, "explicit directory"):
            self.decoder()

    def test_duplicate_module_and_aggregate_bounds_are_refused(self):
        self.report["modules"].append(dict(self.report["modules"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.decoder()
        self.report["modules"].pop()
        with patch("guest_namespace.MAX_TOTAL", 1), self.assertRaisesRegex(ValueError, "aggregate"):
            self.decoder()

    def test_file_growth_after_stat_is_read_with_a_hard_bound(self):
        original_open = Path.open
        growing = io.BytesIO(b"x" * 1000)

        def open_file(path, *args, **kwargs):
            if path == self.provider_path:
                return growing
            return original_open(path, *args, **kwargs)

        # Existing files fit, but the selected file grows after stat. Closing
        # BytesIO must retain the observed read position for the assertion.
        with patch.object(growing, "close"), patch("guest_namespace.MAX_FILE", 32), \
                patch.object(Path, "open", open_file), self.assertRaisesRegex(ValueError, "aggregate"):
            self.decoder()
        self.assertEqual(growing.tell(), 33)


if __name__ == "__main__":
    unittest.main()
