# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic relocated-pointer/control proofs, never native execution evidence."""
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from test_guest_tpidr_audit import BindingElfFixture, I, M, R, audit, instruction


def fixture():
    value = BindingElfFixture()
    # Separate executable and writable data onto different Darwin host pages.
    struct.pack_into("<Q", value.data, 64 + 56 + 16, 0x4000)
    struct.pack_into("<Q", value.data, 64 + 112 + 16, 0x4100)
    for section in (2, 3):
        address = 0x1800 + section * 64 + 16
        old = struct.unpack_from("<Q", value.data, address)[0]
        struct.pack_into("<Q", value.data, address, old + 0x3000)
    value.tags = [(tag, address + 0x3000 if tag in (5, 6) else address)
                  for tag, address in value.tags if tag not in (2, 20, 23)]
    value.tags += [(7, 0x4400), (8, 24), (9, 24)]
    value.dynamic()
    struct.pack_into("<H", value.data, 56, 4)
    struct.pack_into("<IIQQQQQQ", value.data, 64 + 3 * 56,
                     0x6474E552, 4, 0x1000, 0x4000, 0, 0x1000, 0x1000, 1)
    struct.pack_into("<QQq", value.data, 0x1400, 0x4500, 1027, 0x200)
    struct.pack_into("<Q", value.data, 0x1500, 0xBADBAD)
    return value


def metadata(value, trusted=True):
    result = value.metadata()
    # Synthetic first-party contract switch; no binary or signing claim.
    result.trusted_provider_contracts = trusted
    return result


class RelroPointerTests(unittest.TestCase):
    def test_relative_addend_not_original_bytes_supplies_exact_function_entry(self):
        result = metadata(fixture()).relocated_code_pointer(0x4500)
        self.assertEqual(result["target"], 0x200)
        self.assertEqual(result["function"]["entry"], "0x200")
        self.assertEqual(result["page_size"], 16384)
        self.assertEqual(result["relocation_type"], 1027)

    def test_untrusted_missing_relro_and_wrong_slot_are_not_values(self):
        self.assertIsNone(metadata(fixture(), False).relocated_code_pointer(0x4500))
        value = fixture()
        struct.pack_into("<I", value.data, 64 + 3 * 56, 0)
        self.assertIsNone(metadata(value).relocated_code_pointer(0x4500))
        for slot in (0x4501, 0x4508, -8, 1 << 64):
            self.assertIsNone(metadata(fixture()).relocated_code_pointer(slot))

    def test_symbolic_wrong_type_and_non_entry_targets_refuse(self):
        for info, target in ((1027 | (1 << 32), 0x200), (257, 0x200),
                             (1027, 0x204), (1027, 0x202), (1027, -4), (1027, 0x4400)):
            value = fixture()
            struct.pack_into("<QQq", value.data, 0x1400, 0x4500, info, target)
            self.assertIsNone(metadata(value).relocated_code_pointer(0x4500))

    def test_shared_executable_mutable_padding_and_short_relro_refuse(self):
        cases = ((64 + 3 * 56 + 16, 0x4008),
                 (64 + 3 * 56 + 40, 0x400),
                 (64 + 56 + 4, 7))
        for offset, replacement in cases:
            value = fixture()
            struct.pack_into("<I" if offset == 64 + 56 + 4 else "<Q", value.data, offset, replacement)
            if offset == 64 + 3 * 56 + 40:
                struct.pack_into("<Q", value.data, 64 + 3 * 56 + 32, 0x400)
            self.assertIsNone(metadata(value).relocated_code_pointer(0x4500))

    def test_neighbor_overlapping_writer_refuses(self):
        value = fixture()
        value.tags = [(tag, 48 if tag == 8 else address) for tag, address in value.tags]
        value.dynamic()
        struct.pack_into("<QQq", value.data, 0x1418, 0x4504, 1027, 0x200)
        self.assertIsNone(metadata(value).relocated_code_pointer(0x4500))

    def test_bss_slot_and_bounded_entry_work(self):
        value = fixture()
        struct.pack_into("<Q", value.data, 64 + 56 + 32, 0x500)
        result = metadata(value)
        self.assertIsNotNone(result.relocated_code_pointer(0x4500))
        result = metadata(fixture())
        result.function_entry_work = 2_000_000
        self.assertIsNone(result.relocated_code_pointer(0x4500))

    def test_writable_slot_outside_relro_and_executable_page_neighbor_refuse(self):
        for executable in (False, True):
            value = fixture()
            struct.pack_into("<H", value.data, 56, 5)
            struct.pack_into("<IIQQQQQQ", value.data, 64 + 4 * 56, 1,
                             5 if executable else 6, 0x200 if executable else 0,
                             0x5000 if executable else 0x8000, 0,
                             4 if executable else 0, 4 if executable else 0x1000, 1)
            if not executable:
                struct.pack_into("<QQq", value.data, 0x1400, 0x8500, 1027, 0x200)
            self.assertIsNone(metadata(value).relocated_code_pointer(0x4500 if executable else 0x8500))

    def test_duplicate_relro_rejected_at_parse(self):
        value = fixture()
        struct.pack_into("<H", value.data, 56, 5)
        value.data[64 + 4 * 56:64 + 5 * 56] = value.data[64 + 3 * 56:64 + 4 * 56]
        with self.assertRaisesRegex(ValueError, "duplicate PT_GNU_RELRO"):
            metadata(value)


class IndirectFlowTests(unittest.TestCase):
    def report(self, *, operation="blr", pointer=True, separation=True, valid_entry=True, setup=(), seed=None, multiple=False):
        graph = {item.address: item for item in [
            instruction(0, "mrs", R("x19")), instruction(4, "sub", R("sp"), R("sp"), I(32)),
            instruction(8, "str", R("x30"), M("sp", 24)),
            instruction(12, "str", R("x19"), M("sp")),
            instruction(16, "adr", R("x9"), I(0x4500)),
            instruction(20, "ldr", R("x8"), M("x9")),
            instruction(24, operation, R("x8")),
            instruction(28, "mov", R("x19"), R("xzr")),
            instruction(32, "str", R("xzr"), M("sp")),
            instruction(36, "ldr", R("x30"), M("sp", 24)),
            instruction(40, "add", R("sp"), R("sp"), I(32)), instruction(44, "ret"),
            instruction(0x200, "ldr", R("x8"), M("x19", 40)), instruction(0x204, "ret"),
            instruction(0x208, "ldr", R("x8"), M("x19", 40)), instruction(0x20c, "ret") ]}

        graph.update({item.address: item for item in setup})

        class Decoder:
            def __call__(self, address):
                return graph.get(address)

            def owned_image_data(self, address, width):
                return address in ((0x4500, 0x4510) if multiple else (0x4500,)) and width == 8

            def mrs_stack_window(self, address):
                return {"before": 4096, "after": 256} if separation else None

            def relocated_code_pointer(self, address):
                return {"slot": hex(address), "target": 0x208 if address == 0x4510 else 0x200} if pointer else None

            def indirect_function_entry(self, address):
                return {"entry": hex(address)} if valid_entry and address in (0x200, 0x208) else None

            def caller_frame(self, pc):
                return seed.copy(), {"status": "synthetic-initial-state"}

        return audit.Audit(Decoder()).site(0, entry_context=seed is not None)

    def test_exact_relocated_load_and_plain_call_follow_guest_body(self):
        report = self.report()
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual([(edge["pc"], edge["target"], edge["operation"])
                          for edge in report["indirect_edges"]], [("0x18", "0x200", "blr")])
        self.assertEqual(len(report["relocated_pointer_loads"]), 1)
        self.assertTrue(any(row["pc"] == "0x200" and row["kind"] == "guard" for row in report["accesses"]))

    def test_no_pointer_no_separation_nonentry_or_authenticated_branch_refuses(self):
        for arguments in ({"pointer": False}, {"separation": False},
                          {"valid_entry": False}, {"operation": "blraa"}):
            report = self.report(**arguments)
            self.assertEqual(report["classification"], "unknown")
            self.assertEqual(report["indirect_edges"], [])

    def test_exact_pc_relative_target_is_revalidated_without_relocation_name(self):
        report = self.report(setup=(instruction(16, "adr", R("x8"), I(0x200)), instruction(20, "nop")))
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(report["relocated_pointer_loads"], [])
        self.assertEqual(len(report["indirect_edges"]), 1)

    def test_call_stack_and_tainted_control_target_are_checked(self):
        report = self.report(setup=(instruction(0x200, "sub", R("sp"), R("sp"), I(16)),))
        self.assertIn("callee-stack-not-restored-at-return", {row["reason"] for row in report["reasons"]})
        report = self.report(setup=(instruction(20, "mov", R("x8"), R("x19")),))
        self.assertEqual(report["indirect_edges"], [])
        self.assertEqual(report["classification"], "unknown")

    def test_narrow_pointer_load_cannot_supply_exact_target(self):
        report = self.report(setup=(instruction(20, "ldr", R("w8"), M("x9")),))
        self.assertEqual(report["relocated_pointer_loads"], [])
        self.assertEqual(report["indirect_edges"], [])

    def test_namespace_rebases_slot_target_relro_and_function_proof(self):
        from types import SimpleNamespace

        from guest_namespace import STRIDE, NamespaceDecoder
        pointer = {"slot": "0x4500", "target": 0x200, "relro_begin": "0x4000",
                   "function": {"entry": "0x200", "size": 8}, "scope": "synthetic"}
        decoder = SimpleNamespace(relocated_code_pointer=lambda pc: pointer,
                                  indirect_function_entry=lambda pc: {"entry": hex(pc), "size": 8})
        namespace = object.__new__(NamespaceDecoder)
        namespace.names = ["root", "provider"]
        namespace.locate = lambda pc: (1, pc - STRIDE, decoder)
        result = namespace.relocated_code_pointer(STRIDE + 0x4500)
        self.assertEqual(result["slot"], hex(STRIDE + 0x4500))
        self.assertEqual(result["target"], STRIDE + 0x200)
        self.assertEqual(result["relro_begin"], hex(STRIDE + 0x4000))
        self.assertEqual(result["function"]["entry"], hex(STRIDE + 0x200))
        self.assertEqual(namespace.indirect_function_entry(STRIDE + 0x200)["entry"], hex(STRIDE + 0x200))
        self.assertEqual(pointer["target"], 0x200)

    def test_latent_native_state_never_promotes_pointer_load(self):
        report = self.report(seed=audit.State(native_memory_may_contain_tp=True))
        self.assertEqual(report["classification"], "unknown")
        self.assertEqual(report["relocated_pointer_loads"], [])
        self.assertEqual(report["indirect_edges"], [])

    def test_resolved_indirect_call_still_obeys_depth_limit(self):
        from unittest.mock import patch
        with patch.object(audit, "MAX_CALL_DEPTH", 0):
            report = self.report()
        self.assertIn("callee-depth-limit", {row["reason"] for row in report["reasons"]})
        self.assertFalse(any(row["pc"] == "0x200" for row in report["accesses"]))

    def test_plain_tail_branch_preserves_existing_return_path(self):
        graph = {item.address: item for item in [
            instruction(0, "mrs", R("x19")), instruction(4, "adr", R("x8"), I(0x200)),
            instruction(8, "br", R("x8")), instruction(0x200, "ldr", R("x8"), M("x19", 40)),
            instruction(0x204, "mov", R("x19"), R("xzr")), instruction(0x208, "ret")]}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def indirect_function_entry(self, pc):
                return {"entry": hex(pc)} if pc == 0x200 else None

        report = audit.Audit(Decoder()).site(0)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(report["indirect_edges"][0]["operation"], "br")

    def test_pointer_load_evidence_retains_multiple_exact_contexts(self):
        seed = audit.State(image_addresses={"x9": 0x4500, "x10": 0x4510})
        report = self.report(seed=seed, multiple=True, setup=(instruction(16, "csel", R("x9"), R("x9"), R("x10")),))
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual({row["target"] for row in report["relocated_pointer_loads"]}, {0x200, 0x208})
        self.assertEqual(len(report["indirect_edges"]), 2)
