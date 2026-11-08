# SPDX-License-Identifier: GPL-3.0-only
"""Pure synthetic CFG checks; these do not prove behavior of actual APK ELFs."""

import hashlib
import importlib.util
import struct
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "scripts/audit_guest_tpidr.py"
sys.path.insert(0, str(MODULE_PATH.parent))
SPEC = importlib.util.spec_from_file_location("guest_tpidr_audit", MODULE_PATH)
audit = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = audit
SPEC.loader.exec_module(audit)


def R(value):
    return audit.Operand("reg", value)


def I(value):
    return audit.Operand("imm", value)


def M(base, offset=0):
    return audit.Operand("mem", base=base, displacement=offset)


def instruction(pc, mnemonic, *operands, reads=(), writes=()):
    return audit.Instruction(pc, mnemonic, operands, reads, writes)


class TpidrAuditTests(unittest.TestCase):
    @unittest.skipUnless(importlib.util.find_spec("capstone"), "requires pinned Capstone decoder")
    def test_real_add_words_preserve_register_extension_and_shift(self):
        import capstone
        from capstone import arm64

        self.assertEqual(capstone.__version__, "5.0.3")
        for word, expected in (
            (0x8B090040, audit.Operand("reg", "x9")),
            (0x8B35C769, audit.Operand("reg", "w21", shift="lsl", shift_amount=1, extend="sxtw")),
        ):
            with self.subTest(word=hex(word)):
                decoder = audit.ElfDecoder.__new__(audit.ElfDecoder)
                decoder.cache = {}
                decoder.data = word.to_bytes(4, "little")
                decoder.segments = [(0, 4, 0)]
                decoder.arm64 = arm64
                decoder.decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
                decoder.decoder.detail = True
                decoder.control_groups = set()
                self.assertEqual(decoder(0).operands[2], expected)

    def indexed_guard_graph(self, index_setup, index, *, operation="add", displacement=0):
        body = [instruction(0, "mrs", R("x19"))]
        for op, operands, writes in index_setup:
            body.append(instruction(len(body) * 4, op, *operands, writes=writes))
        body.extend([
            instruction(len(body) * 4, operation, R("x20"), R("x19"), index),
            instruction((len(body) + 1) * 4, "ldr", R("x8"), M("x20", displacement)),
            instruction((len(body) + 2) * 4, "mov", R("x19"), R("xzr")),
            instruction((len(body) + 3) * 4, "mov", R("x20"), R("xzr")),
            instruction((len(body) + 4) * 4, "ret"),
        ])
        return self.run_graph(body)

    def test_exact_register_indices_preserve_only_proved_affine_offsets(self):
        cases = [
            (40, R("x9"), "add", 0),
            (5, audit.Operand("reg", "x9", shift="lsl", shift_amount=3), "add", 0),
            (0xFFFFFFFF, audit.Operand("reg", "w9", extend="sxtw", shift="lsl", shift_amount=3), "add", 48),
            (5, audit.Operand("reg", "w9", extend="uxtw", shift="lsl", shift_amount=3), "add", 0),
            (0xFFFFFFD8, audit.Operand("reg", "w9", extend="sxtw"), "sub", 0),
            (1 << 63, audit.Operand("reg", "x9", shift="lsl", shift_amount=1), "add", 40),
        ]
        for value, index, operation, displacement in cases:
            with self.subTest(value=value, index=index, operation=operation):
                result = self.indexed_guard_graph(
                    [("mov", (R("x9"), I(value)), ())], index,
                    operation=operation, displacement=displacement)
                self.assertEqual(result["classification"], "guard-only", result["reasons"])
                self.assertEqual(result["accesses"][0]["offset"], 40)

    def test_exact_scalar_arithmetic_snapshots_and_writes_zero_extend(self):
        for setup in (
            [("mov", (R("x9"), I(-1)), ()), ("add", (R("x9"), R("x9"), I(41)), ())],
            [("mov", (R("x10"), I((1 << 40) + 40)), ()), ("mov", (R("w9"), R("w10")), ())],
        ):
            result = self.indexed_guard_graph(setup, R("x9"))
            self.assertEqual(result["classification"], "guard-only", result["reasons"])

    def test_unproved_modified_or_clobbered_register_indices_are_refused(self):
        cases = [
            ([], R("x9")),
            ([("mov", (R("x9"), R("sp")), ())], R("x9")),
            ([("mov", (R("x9"), R("x19")), ())], R("x9")),
            ([("mov", (R("x9"), I(40)), ()), ("unknown", (), ("x9",))], R("x9")),
            ([("mov", (R("x9"), I(40)), ()), ("ldr", (R("x9"), M("x0")), ())], R("x9")),
            ([("mov", (R("x9"), I(40)), ())], audit.Operand("reg", "x9", shift="ror", shift_amount=1)),
            ([("mov", (R("x9"), I(40)), ())], audit.Operand("reg", "x9", shift="lsl", shift_amount=64)),
            ([("mov", (R("w9"), I(5)), ())], audit.Operand("reg", "w9", extend="sxtw", shift_amount=5)),
            ([("mov", (R("x9"), I(40)), ())], audit.Operand("reg", "x9", extend="sxtw")),
            ([("mov", (R("w9"), I(20)), ())], audit.Operand("reg", "w9", extend="sxtw", shift_amount=1)),
        ]
        for setup, index in cases:
            with self.subTest(setup=setup, index=index):
                result = self.indexed_guard_graph(setup, index)
                self.assertEqual(result["classification"], "unknown")
                self.assertIn("register-arithmetic-index-unproved", self.reasons(result))

    def test_different_scalar_paths_cannot_merge_into_one_guard_address(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")),
            instruction(4, "cbz", R("x0"), I(16)),
            instruction(8, "mov", R("x9"), I(40)), instruction(12, "b", I(20)),
            instruction(16, "mov", R("x9"), I(48)),
            instruction(20, "add", R("x20"), R("x19"), R("x9")),
            instruction(24, "ldr", R("x8"), M("x20")),
            instruction(28, "mov", R("x19"), R("xzr")),
            instruction(32, "mov", R("x20"), R("xzr")), instruction(36, "ret"),
        ])
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))

    def test_unknown_scalar_add_does_not_invent_an_address_or_stop_clean_paths(self):
        prefix = [instruction(0, "mrs", R("x19")), instruction(4, "mov", R("x9"), I(40)),
                  instruction(8, "add", R("x9"), R("x0"), R("x1"),
                              reads=("x0", "x1"), writes=("x9",))]
        clean = self.run_graph(prefix + [
            instruction(12, "ldr", R("x8"), M("x19", 40)),
            instruction(16, "mov", R("x19"), R("xzr")), instruction(20, "ret"),
        ])
        self.assertEqual(clean["classification"], "guard-only")
        indexed = self.run_graph(prefix + [
            instruction(12, "add", R("x20"), R("x19"), R("x9")), instruction(16, "ret"),
        ])
        self.assertEqual(indexed["classification"], "unknown")
        self.assertIn("register-arithmetic-index-unproved", self.reasons(indexed))

    def test_scalar_facts_are_partitioned_copied_and_invalidated(self):
        state = audit.State()
        state.set_scalar("x9", (1 << 40) + 7)
        clone = state.copy()
        self.assertEqual(clone.scalar("w9"), 7)
        self.assertEqual(clone.fingerprint(), state.fingerprint())
        clone.set_scalar("w9", 8)
        self.assertEqual(clone.scalar("x9"), 8)
        self.assertNotEqual(clone.fingerprint(), state.fingerprint())
        for setter in (
            lambda: clone.set("w9", audit.CLEAN),
            lambda: clone.set_control("x9", audit.CLEAN),
            lambda: clone.set_signature("x9", None),
        ):
            clone.set_scalar("x9", 40)
            setter()
            self.assertIsNone(clone.scalar("x9"))
        self.assertIsNone(state.scalar("sp"))
        self.assertIsNone(state.scalar("x0"))

    def test_unknown_stores_cannot_preserve_exact_spilled_thread_pointer(self):
        for op, registers, width in (("str", (R("w0"),), 4), ("str", (R("x0"),), 8),
                                     ("str", (R("q0"),), 16), ("stlr", (R("w0"),), 4),
                                     ("stp", (R("x0"), R("x2")), 8)):
            with self.subTest(op=op, width=width):
                result = self.run_graph([
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "str", R("x19"), M("sp")),
                    instruction(8, "mov", R("x19"), R("xzr")),
                    audit.Instruction(12, op, (*registers, M("x1")), width=width),
                    instruction(16, "ldr", R("x19"), M("sp")),
                    instruction(20, "ldr", R("x8"), M("x19", 40)),
                    instruction(24, "mov", R("x19"), R("xzr")),
                    instruction(28, "str", R("xzr"), M("sp")), instruction(32, "ret"),
                ])
                self.assertEqual(result["classification"], "unknown")
                self.assertIn("unmodelled-store-may-clobber-thread-pointer-stack-spill", self.reasons(result))
                self.assertEqual(result["accesses"], [])

    def test_narrow_arithmetic_cannot_authorize_a_fullwidth_guard_load(self):
        for op in ("add", "sub"):
            for immediate in (0, 8):
                with self.subTest(op=op, immediate=immediate):
                    result = self.run_graph([
                        instruction(0, "mrs", R("x19")),
                        instruction(4, op, R("w19"), R("w19"), I(immediate)),
                        instruction(8, "ldr", R("x8"), M("x19", 40)),
                        instruction(12, "mov", R("x19"), R("xzr")),
                        instruction(16, "ret"),
                    ])
                    self.assertEqual(result["classification"], "unknown")
                    self.assertIn("narrowed-thread-pointer", self.reasons(result))
                    self.assertEqual(result["accesses"], [])

    def test_literal_load_cannot_forget_tp_spilled_into_an_unproved_initial_stack(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x8"), M("x19", 40)),
            instruction(8, "str", R("x19"), M("sp")),
            instruction(12, "ldr", R("x12"), I(0x4020)),
            instruction(16, "mov", R("x19"), R("xzr")),
            instruction(20, "str", R("xzr"), M("sp")),
            instruction(24, "ldr", R("x13"), M("x12", 8)), instruction(28, "ret"),
        ])
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unmodelled-literal-load-may-alias-thread-pointer-stack-spill", self.reasons(result))

    def test_literal_load_without_a_saved_tp_does_not_invent_a_pointer_fact(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x8"), M("x19", 40)),
            instruction(8, "ldr", R("x19"), I(0x4020)), instruction(12, "ret"),
        ])
        self.assertEqual(result["classification"], "guard-only")

    def image_graph(self, middle, owned=None):
        body = [instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp")), *middle,
                instruction(256, "str", R("xzr"), M("sp")),
                instruction(260, "mov", R("x19"), R("xzr")),
                instruction(264, "ret")]
        graph = {item.address: item for item in body}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def owned_image_data(self, address, width):
                return (0x4000 <= address and address + width <= 0x4100) if owned is None else owned(address, width)

        return audit.Audit(Decoder()).site(0)

    def test_image_address_requires_full_independent_owned_extent(self):
        body = [instruction(12, "adrp", R("x10"), I(0x4000)),
                instruction(16, "add", R("x10"), R("x10"), I(32)),
                instruction(20, "ldr", R("x12"), M("x10")),
                instruction(24, "b", I(256))]
        result = self.image_graph(body)
        self.assertEqual(result["classification"], "unknown")
        self.assertTrue(result["owned_image_accesses"])
        self.assertTrue(all(item["stack_disjointness"] == "unproved-initial-SP"
                            for item in result["owned_image_accesses"]))
        for proof in (False, None, 1):
            invalid = self.image_graph(body, owned=lambda *_, proof=proof: proof)
            self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(invalid))
        partial = self.image_graph(body, owned=lambda start, width: 0x4000 <= start and start + width <= 0x4024)
        self.assertEqual(partial["classification"], "unknown")

    def test_image_load_does_not_infer_ownership_of_loaded_pointer(self):
        result = self.image_graph([
            instruction(12, "adr", R("x10"), I(0x4020)),
            instruction(16, "ldr", R("x11"), M("x10")),
            instruction(20, "ldr", R("x12"), M("x11")), instruction(24, "b", I(256)),
        ])
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(result))

    def test_image_facts_are_invalidated_by_narrow_or_arbitrary_register_writes(self):
        for overwrite in (instruction(16, "mov", R("w10"), R("w0")),
                          instruction(16, "eor", R("x10"), R("x0"), R("x0"), writes=("x10",))):
            result = self.image_graph([
                instruction(12, "adr", R("x10"), I(0x4020)), overwrite,
                instruction(20, "ldr", R("x12"), M("x10")), instruction(24, "b", I(256)),
            ])
            self.assertEqual(result["classification"], "unknown")

    def test_image_facts_survive_exact_fullwidth_spill_and_nonoverlapping_store_only(self):
        for offset in (12, 16):
            result = self.image_graph([
                instruction(12, "adr", R("x10"), I(0x4020)),
                instruction(16, "str", R("x10"), M("sp", 8)),
                audit.Instruction(20, "str", (R("w0"), M("sp", offset)), width=4),
                instruction(24, "ldr", R("x11"), M("sp", 8)),
                instruction(28, "ldr", R("x12"), M("x11")), instruction(32, "b", I(256)),
            ])
            self.assertEqual(result["classification"], "unknown")
            self.assertEqual(bool(result["owned_image_accesses"]), offset == 16)

    def test_unknown_store_invalidates_spilled_image_address_proof(self):
        result = self.image_graph([
            instruction(12, "adr", R("x10"), I(0x4020)),
            instruction(16, "str", R("x10"), M("sp", 8)),
            instruction(20, "str", R("x0"), M("x1")),
            instruction(24, "ldr", R("x11"), M("sp", 8)),
            instruction(28, "ldr", R("x12"), M("x11")), instruction(32, "b", I(256)),
        ])
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unmodelled-store-may-clobber-thread-pointer-stack-spill", self.reasons(result))
        self.assertEqual(result["owned_image_accesses"], [])

    def test_unknown_store_cannot_retain_an_obsolete_spilled_stack_alias(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x8"), M("x19", 40)),
            instruction(8, "str", R("x19"), M("sp")),
            instruction(12, "add", R("x10"), R("sp"), I(16)),
            instruction(16, "str", R("x10"), M("sp", 8)),
            instruction(20, "str", R("x0"), M("x1")),
            instruction(24, "ldr", R("x11"), M("sp", 8)),
            instruction(28, "ldr", R("x12"), M("x11")),
            instruction(32, "mov", R("x19"), R("xzr")),
            instruction(36, "str", R("xzr"), M("sp")),
            instruction(40, "ldr", R("x13"), M("x12", 8)), instruction(44, "ret"),
        ])
        self.assertEqual(result["classification"], "unknown")

    def test_image_store_still_rejects_tp_and_stack_payload_escapes(self):
        for payload, reason in (("x19", "thread-pointer-store-escape"),
                                ("x11", "frame-address-store-escape-unmodelled")):
            result = self.image_graph([
                instruction(12, "adr", R("x10"), I(0x4020)),
                instruction(16, "mov", R("x11"), R("sp")),
                instruction(20, "str", R(payload), M("x10")), instruction(24, "b", I(256)),
            ])
            self.assertIn(reason, self.reasons(result))

    def test_image_address_index_and_unsigned_overflow_cannot_prove_disjointness(self):
        for body in ([instruction(12, "adr", R("x10"), I(0x4020)),
                      instruction(16, "ldr", R("x12"), audit.Operand("mem", base="x10", index="x0"))],
                     [instruction(12, "adr", R("x10"), I((1 << 64) - 4)),
                      instruction(16, "add", R("x10"), R("x10"), I(16)),
                      instruction(20, "ldr", R("x12"), M("x10"))]):
            result = self.image_graph([*body, instruction(24, "b", I(256))], owned=lambda *_: True)
            self.assertEqual(result["classification"], "unknown")

    def test_image_select_retains_unknown_alternative(self):
        for source in ("xzr", "x10"):
            result = self.image_graph([
                instruction(12, "adr", R("x10"), I(0x4020)),
                instruction(16, "csel", R("x11"), R("x10"), R(source)),
                instruction(20, "ldr", R("x12"), M("x11")), instruction(24, "b", I(256)),
            ])
            self.assertEqual(result["classification"], "unknown")
            self.assertTrue(result["owned_image_accesses"])

    def test_image_store_cannot_assume_saved_lr_is_disjoint(self):
        result = self.image_graph([
            instruction(12, "bl", I(64)), instruction(16, "b", I(256)),
            instruction(64, "str", R("x30"), M("sp", 8)),
            instruction(68, "adr", R("x10"), I(0x4020)),
            instruction(72, "str", R("xzr"), M("x10")),
            instruction(76, "ldr", R("x30"), M("sp", 8)), instruction(80, "ret"),
        ])
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unproved-return-address", self.reasons(result))

    def test_initial_stack_may_be_inside_owned_image_without_independent_root_proof(self):
        # Actual initial SP may be image+0x4020 after a stack pivot before MRS.
        # Image ownership cannot make the reload forget the saved TP pointer.
        result = self.image_graph([
            instruction(12, "adr", R("x10"), I(0x4020)),
            instruction(16, "ldr", R("x12"), M("x10")),
            instruction(20, "mov", R("x19"), R("xzr")),
            instruction(24, "str", R("xzr"), M("sp")),
            instruction(28, "ldr", R("x13"), M("x12", 8)), instruction(32, "b", I(256)),
        ])
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(result))

    def test_store_writeback_overlap_cannot_forge_a_known_stack_pointer(self):
        for opcode, operands in (("str", (R("x10"), M("x10", 8))),
                                  ("stp", (R("x0"), R("x10"), M("x10", 8)))):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")), instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp", 16)),
                instruction(12, "mov", R("x10"), R("sp")),
                audit.Instruction(16, opcode, operands, writeback=True),
                instruction(20, "ldr", R("x11"), M("sp", 8)),
                instruction(24, "ldr", R("x12"), M("x11")),
                instruction(28, "str", R("xzr"), M("sp", 16)),
                instruction(32, "mov", R("x19"), R("xzr")),
                instruction(36, "ldr", R("x13"), M("x12", 8)), instruction(40, "ret"),
            ])
            self.assertIn("unpredictable-store-writeback-overlap", self.reasons(result))
            self.assertEqual(result["classification"], "unknown")

    def test_image_address_writeback_is_observed_without_a_disjointness_assumption(self):
        for offset, post in ((0, 8), (8, 0)):
            result = self.image_graph([
                instruction(12, "adr", R("x10"), I(0x4020)),
                audit.Instruction(16, "ldr", (R("x12"), M("x10", offset)),
                                  writeback=True, post_index=post),
                instruction(20, "ldr", R("x13"), M("x10")), instruction(24, "b", I(256)),
            ])
            self.assertEqual(result["classification"], "unknown")
            addresses = [int(item["address"], 16) for item in result["owned_image_accesses"]]
            self.assertEqual(addresses, [0x4020 + offset, 0x4028])

    def test_image_state_copy_partition_and_register_write_invalidation(self):
        state = audit.State()
        other = state.copy()
        other.set_image("x10", 0x4020)
        self.assertNotEqual(state.fingerprint(), other.fingerprint())
        state.stack_images[8] = 0x4020
        saved = state.copy()
        saved.stack_images[8] = 0x4040
        self.assertNotEqual(state.fingerprint(), saved.fingerprint())
        self.assertEqual(state.stack_images[8], 0x4020)
        other.set("x10", audit.CLEAN)
        self.assertIsNone(other.image("x10"))

    def test_conditional_select_retains_tp_on_either_condition(self):
        for sources in (("x19", "xzr"), ("xzr", "x19")):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")),
                instruction(4, "csel", R("x10"), *(R(name) for name in sources)),
                instruction(8, "ldr", R("x8"), M("x10", 8)),
                instruction(12, "mov", R("x10"), R("xzr")),
                instruction(16, "mov", R("x19"), R("xzr")), instruction(20, "ret"),
            ])
            self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))

    def test_conditional_select_preserves_full_stack_alias_alternatives(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")),
            instruction(4, "mov", R("x10"), R("sp")),
            instruction(8, "add", R("x11"), R("sp"), I(16)),
            instruction(12, "csel", R("x12"), R("x10"), R("x11")),
            instruction(16, "str", R("x19"), M("x12")),
            instruction(20, "ldr", R("x13"), M("x12", 4)), instruction(24, "ret"),
        ])
        self.assertIn("partial-overlap-with-thread-pointer-stack-spill", self.reasons(result))
        self.assertNotIn("unmodelled-stack-address-consumer:csel", self.reasons(result))

    def test_conditional_select_checks_narrowing_and_invalid_registers(self):
        narrow = self.run_graph([
            instruction(0, "mrs", R("x19")),
            instruction(4, "mov", R("x10"), R("sp")),
            instruction(8, "csel", R("w11"), R("w19"), R("w10")), instruction(12, "ret"),
        ])
        self.assertIn("narrowed-thread-pointer", self.reasons(narrow))
        self.assertIn("narrowed-stack-address-unmodelled", self.reasons(narrow))
        for operand in (I(0), R("sp"), R("w19"), R("v1")):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")),
                instruction(4, "csel", R("x11"), R("x19"), operand), instruction(8, "ret"),
            ])
            self.assertEqual(result["classification"], "unknown")

    def test_conditional_select_does_not_forge_a_saved_return(self):
        result = self.run_graph([
            instruction(0, "mrs", R("x19")), instruction(4, "bl", I(32)),
            instruction(8, "ret"),
            instruction(32, "csel", R("x30"), R("x30"), R("xzr")), instruction(36, "ret"),
        ])
        self.assertIn("unproved-return-address", self.reasons(result))

    def test_sxtw_overwrites_destination_instead_of_reading_old_facts(self):
        for source in ("w1", "wzr"):
            for old in ("sp", "x19"):
                result = self.run_graph([
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "mov", R("x0"), R(old)),
                    instruction(8, "sxtw", R("x0"), R(source), reads=("x0", source), writes=("x0",)),
                    instruction(12, "ldr", R("x8"), M("x19", 40)),
                    instruction(16, "mov", R("x19"), R("xzr")), instruction(20, "ret"),
                ])
                self.assertEqual(result["classification"], "guard-only")
                self.assertFalse(result["reasons"])

    def test_sxtw_narrowing_cannot_launder_tp_or_stack_addresses(self):
        for destination in ("x0", "x19"):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")),
                instruction(4, "sxtw", R(destination), R("w19")),
                instruction(8, "ldr", R("x8"), M(destination, 40)), instruction(12, "ret"),
            ])
            self.assertIn("narrowed-thread-pointer", self.reasons(result))
            self.assertIn("opaque-thread-pointer-memory-base", self.reasons(result))
            self.assertFalse(any(row["kind"] == "guard" for row in result["accesses"]))
        for destination in ("x0", "x29"):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")),
                instruction(4, "mov", R("x1"), R("sp")),
                instruction(8, "sxtw", R(destination), R("w1")), instruction(12, "ret"),
            ])
            self.assertIn("narrowed-stack-address-unmodelled", self.reasons(result))

    def test_sxtw_rejects_unmodelled_forms_and_allows_discarded_results(self):
        for operands in ((R("w0"), R("w1")), (R("sp"), R("w1")),
                         (R("x0"), R("x1")), (R("x0"), I(1)), (R("x0"),)):
            result = self.run_graph([
                instruction(0, "mrs", R("x19")),
                instruction(4, "sxtw", *operands), instruction(8, "ret"),
            ])
            self.assertIn("unsupported-sxtw-form", self.reasons(result))
        result = self.run_graph([
            instruction(0, "mrs", R("x19")), instruction(4, "sxtw", R("xzr"), R("w1")),
            instruction(8, "ldr", R("x8"), M("x19", 40)),
            instruction(12, "mov", R("x19"), R("xzr")), instruction(16, "ret"),
        ])
        self.assertEqual(result["classification"], "guard-only")

    def run_graph(
        self,
        body,
        *,
        site=0,
        caller=None,
        max_steps=audit.MAX_STEPS,
        max_contexts=audit.MAX_CONTEXTS,
    ):
        graph = {item.address: item for item in body}
        return audit.Audit(
            graph.get, max_steps=max_steps, max_contexts=max_contexts
        ).site(site, caller=caller)

    def reasons(self, result):
        return {item["reason"] for item in result["reasons"]}

    def test_clean_call_paths_cannot_hide_recursive_origin_or_unknown_branch(self):
        prefix = [
            instruction(0, "mrs", R("x19")),
            instruction(4, "ldr", R("x8"), M("x19", 40)),
            instruction(8, "mov", R("x19"), R("x0")),
        ]
        recursive = self.run_graph(
            prefix + [instruction(12, "bl", I(0)), instruction(16, "ret")]
        )
        self.assertEqual(recursive["classification"], "unknown")
        self.assertIn("callee-depth-limit", self.reasons(recursive))
        indirect = self.run_graph(prefix + [instruction(12, "blr", R("x0"))])
        self.assertEqual(indirect["classification"], "unknown")
        self.assertIn(
            "indirect-call-or-branch-with-unproved-summary", self.reasons(indirect)
        )

    def test_arithmetic_and_generic_lr_writes_invalidate_return_proof(self):
        for operation in (
            instruction(32, "add", R("x30"), R("x30"), I(4)),
            instruction(32, "eor", R("x30"), R("x0"), R("x0"), writes=("x30",)),
        ):
            result = self.run_graph(
                [
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "bl", I(32)),
                    instruction(8, "ldr", R("x8"), M("x19", 40)),
                    instruction(12, "mov", R("x19"), R("x0")),
                    instruction(16, "ret"),
                    operation,
                    instruction(36, "ret"),
                ]
            )
            self.assertEqual(result["classification"], "unknown")
            self.assertIn("unproved-return-address", self.reasons(result))

    def test_unhandled_control_groups_and_authenticated_transfers_never_fall_through(
        self,
    ):
        for opcode in ("braaz", "blraaz", "retaa", "svc", "hlt", "future_jump"):
            result = self.run_graph(
                [
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "ldr", R("x8"), M("x19", 40)),
                    audit.Instruction(8, opcode, (R("x0"),), control_transfer=True),
                    instruction(12, "mov", R("x19"), R("x0")),
                    instruction(16, "ret"),
                ]
            )
            self.assertEqual(result["classification"], "unknown")
            self.assertIn("unhandled-control-transfer:" + opcode, self.reasons(result))

    def test_duplicate_pair_load_cannot_select_a_convenient_saved_return(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "bl", I(32)),
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "mov", R("x19"), R("x0")),
                instruction(16, "ret"),
                instruction(32, "str", R("x30"), M("sp", 8)),
                instruction(36, "ldp", R("x30"), R("lr"), M("sp")),
                instruction(40, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unpredictable-duplicate-load-destination", self.reasons(result))

    def test_return_address_must_be_exact_not_merely_tp_clean(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "bl", I(32)),
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "mov", R("x19"), R("x0")),
                instruction(16, "ret"),
                instruction(32, "mov", R("x30"), R("x0")),
                instruction(36, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unproved-return-address", self.reasons(result))

    def test_full_width_saved_lr_survives_nested_call_but_alias_store_invalidates(self):
        for overwrite in (None, "unknown", "partial"):
            with self.subTest(overwrite=overwrite):
                clobber = instruction(40, "nop")
                if overwrite == "unknown":
                    clobber = instruction(40, "str", R("x0"), M("x1"))
                if overwrite == "partial":
                    clobber = audit.Instruction(
                        40, "str", (R("w0"), M("sp", 4)), width=4
                    )
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "bl", I(32)),
                        instruction(8, "ldr", R("x8"), M("x19", 40)),
                        instruction(12, "mov", R("x19"), R("x0")),
                        instruction(16, "ret"),
                        instruction(32, "str", R("x30"), M("sp")),
                        instruction(36, "bl", I(64)),
                        clobber,
                        instruction(44, "ldr", R("x30"), M("sp")),
                        instruction(48, "ret"),
                        instruction(64, "ret"),
                    ]
                )
                self.assertEqual(
                    result["classification"],
                    "guard-only" if overwrite is None else "unknown",
                )
                if overwrite:
                    self.assertIn("unproved-return-address", self.reasons(result))

    def test_root_return_does_not_hide_tp_in_caller_stack(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp", 16)),
                instruction(12, "mov", R("x19"), R("x0")),
                instruction(16, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn(
            "thread-pointer-stack-return-needs-caller-context", self.reasons(result)
        )

    def test_reentered_mrs_reseeds_tp_after_exact_stack_layout_changes(self):
        graph = {
            item.address: item
            for item in [
                instruction(100, "bl", I(0)),
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x9"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp")),
                instruction(12, "ldr", R("x8"), M("x20")),
                instruction(16, "str", R("xzr"), M("sp")),
                instruction(20, "mov", R("x19"), R("x0")),
                instruction(24, "ldr", R("x9"), M("x8", 8)),
                instruction(28, "mov", R("sp"), R("x29")),
                instruction(32, "b", I(0)),
            ]
        }

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def caller_frame(self, pc):
                return audit.State(fp=-16, aliases={"x20": frozenset({-16})}), {
                    "status": "synthetic"
                }

        result = audit.Audit(Decoder()).site(0, caller=100)
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))
        self.assertTrue(any(x["offset"] == 8 for x in result["accesses"]))

    def test_partition_key_includes_return_pc_registers_and_spills(self):
        state = audit.State()
        for name in ("return_values", "stack_returns"):
            other = state.copy()
            getattr(other, name)["x30" if name == "return_values" else 0] = frozenset(
                {64}
            )
            self.assertNotEqual(state.fingerprint(), other.fingerprint())

    def atomic_graph(self, prefix, suffix, extra=(), cas=False):
        from guest_callee_effects import Atomic32Effects, AtomicPath

        graph = {
            item.address: item
            for item in [
                *prefix,
                *suffix,
                *extra,
                instruction(32, "bti"),
                instruction(52, "ret"),
                instruction(76, "ret"),
                instruction(80, "ret"),
            ]
        }
        effect = Atomic32Effects(
            32,
            48,
            "a" * 64,
            256,
            (
                AtomicPath("lse", 52, ("x0", "x16")),
                AtomicPath("llsc", 76, ("x0", "x15", "x16", "x17")),
            ),
        )
        if cas:
            from guest_callee_effects import Atomic64CompareSwapEffects

            effect = Atomic64CompareSwapEffects(
                32, 52, "b" * 64, 256,
                (AtomicPath("lse", 52, ("x0", "x16")),
                 AtomicPath("llsc-initial-mismatch", 80, ("x0", "x16"), False, False),
                 AtomicPath("llsc-retry", 80, ("x0", "x16", "x17"), True, False)),
                memory_order="acquire-release",
            )

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def atomic32(self, pc):
                return effect if pc == 32 and not cas else None

            def cas64(self, pc):
                return effect if pc == 32 and cas else None

        return audit.Audit(Decoder()).site(0)

    def test_cas_retained_register_is_not_erased_by_other_path(self):
        result = self.atomic_graph(
            [instruction(0, "mrs", R("x17")), instruction(4, "bl", I(32))],
            [instruction(8, "ldr", R("x8"), M("x17", 8)),
             instruction(12, "mov", R("x17"), R("x0")), instruction(16, "ret")],
            cas=True,
        )
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))
        effect = result["callee_effects"][0]
        self.assertEqual(effect["kind"], "exact-atomic-cas64")
        self.assertEqual(len(effect["paths"]), 3)

    def test_cas_compare_payload_and_address_are_independently_checked(self):
        for register, reason in (("x0", "thread-pointer-atomic-compare"),
                                 ("x1", "thread-pointer-atomic-payload"),
                                 ("x2", "non-guard-tls-atomic-access")):
            with self.subTest(register=register):
                result = self.atomic_graph(
                    [instruction(0, "mrs", R(register)), instruction(4, "bl", I(32))],
                    [instruction(8, "ret")], cas=True,
                )
                self.assertIn(reason, self.reasons(result))

    def test_cas_stack_compare_and_payload_do_not_escape_silently(self):
        for register, reason in (("x0", "stack-address-atomic-compare"),
                                 ("x1", "stack-address-atomic-payload")):
            result = self.atomic_graph(
                [instruction(0, "mrs", R("x19")),
                 instruction(4, "mov", R(register), R("sp")), instruction(8, "bl", I(32))],
                [instruction(12, "ret")], cas=True,
            )
            self.assertIn(reason, self.reasons(result))

    def test_cas_ordinary_guard_use_survives_all_explicit_paths(self):
        result = self.atomic_graph(
            [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(32))],
            [instruction(8, "ldr", R("x8"), M("x19", 40)),
             instruction(12, "mov", R("x19"), R("x0")), instruction(16, "ret")],
            cas=True,
        )
        self.assertEqual(result["classification"], "guard-only")

    def test_atomic_effect_retains_lse_registers_and_both_paths(self):
        safe = self.atomic_graph(
            [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(32))],
            [
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "mov", R("x19"), R("x0")),
                instruction(16, "ret"),
            ],
        )
        self.assertEqual(safe["classification"], "guard-only")
        unsafe = self.atomic_graph(
            [
                instruction(0, "mrs", R("x17")),
                instruction(4, "ldr", R("x8"), M("x17", 40)),
                instruction(8, "bl", I(32)),
            ],
            [
                instruction(12, "ldr", R("x8"), M("x17", 8)),
                instruction(16, "mov", R("x17"), R("x0")),
                instruction(20, "ret"),
            ],
        )
        self.assertEqual(unsafe["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(unsafe))

    def test_atomic_effect_checks_payload_and_partial_tp_spills_before_clobbers(self):
        payload = self.atomic_graph(
            [
                instruction(0, "mrs", R("x0")),
                instruction(4, "ldr", R("x8"), M("x0", 40)),
                instruction(8, "bl", I(32)),
            ],
            [instruction(12, "ret")],
        )
        self.assertIn("thread-pointer-atomic-payload", self.reasons(payload))
        spill = self.atomic_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp")),
                instruction(8, "mov", R("x1"), R("sp")),
                instruction(12, "bl", I(32)),
            ],
            [instruction(16, "ret")],
        )
        self.assertIn(
            "partial-overlap-with-thread-pointer-stack-spill", self.reasons(spill)
        )

    def test_atomic_effect_invalidates_saved_return_when_memory_may_alias_it(self):
        prefix = [instruction(0, "mrs", R("x19")), instruction(4, "bl", I(96))]
        suffix = [
            instruction(8, "ldr", R("x8"), M("x19", 40)),
            instruction(12, "mov", R("x19"), R("x0")),
            instruction(16, "ret"),
        ]
        for known in (False, True):
            result = self.atomic_graph(
                prefix,
                suffix,
                [
                    instruction(96, "str", R("x30"), M("sp")),
                    instruction(100, "add", R("x1"), R("sp"), I(16))
                    if known
                    else instruction(100, "nop"),
                    instruction(104, "bl", I(32)),
                    instruction(108, "ldr", R("x30"), M("sp")),
                    instruction(112, "ret"),
                ],
            )
            self.assertEqual(
                result["classification"], "guard-only" if known else "unknown"
            )
            if not known:
                self.assertIn("unproved-return-address", self.reasons(result))

    def test_guard_then_register_clobber_has_no_stale_alias(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "mov", R("x19"), R("x0")),
                instruction(12, "ldr", R("x9"), M("x19", 8)),
                instruction(16, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(len(result["accesses"]), 1)

    def test_direct_outlined_callee_summary_preserves_alias(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "bl", I(32)),
                instruction(8, "mov", R("x19"), R("x0")),
                instruction(12, "ret"),
                instruction(32, "ldr", R("x8"), M("x19", 40)),
                instruction(36, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(
            result["callees"],
            [
                {
                    "call": "0x4",
                    "target": "0x20",
                    "summary": "explicit-body-traversal-requested",
                }
            ],
        )

    def test_signed_word_load_width_uses_memory_access_not_destination(self):
        for opcode in ("ldrsw", "ldursw", "ldpsw"):
            self.assertEqual(audit.memory_width(opcode, "x8"), 4)
        self.assertEqual(audit.memory_width("ldr", "x8"), 8)
        self.assertEqual(audit.memory_width("ldr", "q0"), 16)
        self.assertEqual(audit.memory_width("ldrb", "w8"), 1)
        self.assertEqual(audit.memory_width("ldrh", "w8"), 2)
        body = [
            instruction(0, "mrs", R("x19")),
            audit.Instruction(4, "ldrsw", (R("x8"), M("x19", 40)), width=4),
            instruction(8, "mov", R("x19"), R("x0")),
            instruction(12, "ret"),
        ]
        result = self.run_graph(body)
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))

    def test_ordered_scalar_widths_and_clean_transfers(self):
        for opcode, destination, width in (
            ("ldar", "x8", 8),
            ("ldar", "w8", 4),
            ("ldarb", "w8", 1),
            ("ldarh", "w8", 2),
            ("stlr", "xzr", 8),
            ("stlr", "wzr", 4),
            ("stlrb", "wzr", 1),
            ("stlrh", "wzr", 2),
        ):
            with self.subTest(opcode=opcode, destination=destination):
                self.assertEqual(audit.memory_width(opcode, destination), width)
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "ldr", R("x8"), M("x19", 40)),
                        audit.Instruction(
                            8, opcode, (R(destination), M("x0")), width=width
                        ),
                        instruction(12, "mov", R("x19"), R("x1")),
                        instruction(16, "ret"),
                    ]
                )
                self.assertEqual(result["classification"], "guard-only")

    def test_ordered_scalar_transfers_reject_malformed_forms(self):
        malformed = (
            audit.Instruction(8, "ldar", (R("x8"), M("x0", 8))),
            audit.Instruction(
                8, "ldar", (R("x8"), audit.Operand("mem", base="x0", index="x1"))
            ),
            audit.Instruction(8, "ldar", (R("x8"), M("x0")), writeback=True),
            audit.Instruction(8, "ldar", (R("x8"), M("x0")), post_index=8),
            audit.Instruction(8, "ldar", (R("q0"), M("x0")), width=16),
            audit.Instruction(8, "ldar", (R("x8"), M("w0"))),
            audit.Instruction(8, "ldar", (R("x8"), M("wsp"))),
            audit.Instruction(8, "ldar", (R("x8"), M("xzr"))),
            audit.Instruction(8, "ldar", (R("x8"), M("x0"), I(0))),
            audit.Instruction(8, "ldarb", (R("x8"), M("x0")), width=1),
            audit.Instruction(8, "ldarb", (R("w8"), M("x0")), width=4),
            audit.Instruction(8, "stlrh", (R("wzr"), M("x0")), width=4),
            audit.Instruction(8, "stlr", (R("wzr"), M("x0")), width=8),
        )
        for operation in malformed:
            with self.subTest(operation=operation):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "ldr", R("x8"), M("x19", 40)),
                        operation,
                        instruction(12, "mov", R("x19"), R("x1")),
                        instruction(16, "ret"),
                    ]
                )
                self.assertIn("malformed-ordered-scalar-transfer", self.reasons(result))

    def test_ordered_scalar_guard_width_and_store_payload_are_checked(self):
        for opcode, destination, width in (
            ("ldar", "x8", 8),
            ("ldar", "w8", 4),
            ("ldarb", "w8", 1),
            ("ldarh", "w8", 2),
            ("stlr", "xzr", 8),
        ):
            with self.subTest(opcode=opcode, destination=destination):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "add", R("x20"), R("x19"), I(40)),
                        audit.Instruction(
                            8, opcode, (R(destination), M("x20")), width=width
                        ),
                        instruction(12, "mov", R("x19"), R("x1")),
                        instruction(16, "mov", R("x20"), R("x1")),
                        instruction(20, "ret"),
                    ]
                )
                self.assertEqual(
                    result["classification"],
                    "guard-only" if opcode == "ldar" and width == 8 else "unknown",
                )
        escaped = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "stlr", R("x19"), M("x0")),
            ]
        )
        self.assertIn("thread-pointer-store-escape", self.reasons(escaped))

    def test_ordered_scalar_spills_retain_tp_and_stack_aliases(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "add", R("x0"), R("sp"), I(8)),
                instruction(8, "stlr", R("x19"), M("x0")),
                instruction(12, "mov", R("x19"), R("x1")),
                instruction(16, "ldar", R("x20"), M("x0")),
                instruction(20, "ldr", R("x8"), M("x20", 40)),
                instruction(24, "stlr", R("x1"), M("x0")),
                instruction(28, "mov", R("x20"), R("x1")),
                instruction(32, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        saved_alias = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "add", R("x0"), R("sp"), I(16)),
                instruction(8, "stlr", R("x0"), M("sp")),
                instruction(12, "mov", R("x0"), R("x1")),
                instruction(16, "ldar", R("x2"), M("sp")),
                instruction(20, "stlr", R("x19"), M("x2")),
                instruction(24, "mov", R("x19"), R("x1")),
                instruction(28, "ldar", R("x20"), M("x2")),
                instruction(32, "ldr", R("x8"), M("x20", 40)),
                instruction(36, "stlr", R("x1"), M("x2")),
                instruction(40, "mov", R("x20"), R("x1")),
                instruction(44, "ret"),
            ]
        )
        self.assertEqual(saved_alias["classification"], "guard-only")
        for opcode, register in (("ldarb", "w8"), ("stlrb", "wzr")):
            with self.subTest(opcode=opcode):
                partial = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "str", R("x19"), M("sp")),
                        instruction(8, "add", R("x0"), R("sp"), I(7)),
                        audit.Instruction(
                            12, opcode, (R(register), M("x0")), width=1
                        ),
                    ]
                )
                self.assertIn(
                    "partial-overlap-with-thread-pointer-stack-spill",
                    self.reasons(partial),
                )

    def test_ordered_scalar_saved_lr_requires_full_width_and_nonaliasing(self):
        for clobber in (False, True):
            with self.subTest(clobber=clobber):
                body = [
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "ldr", R("x8"), M("x19", 40)),
                    instruction(8, "mov", R("x19"), R("x0")),
                    instruction(12, "bl", I(64)),
                    instruction(16, "ret"),
                    instruction(64, "stlr", R("lr"), M("sp")),
                    instruction(68, "bl", I(96)),
                    instruction(72, "ldar", R("lr"), M("sp")),
                    instruction(76, "ret"),
                ]
                if clobber:
                    body += [
                        instruction(96, "stlr", R("xzr"), M("x0")),
                        instruction(100, "ret"),
                    ]
                else:
                    body.append(instruction(96, "ret"))
                result = self.run_graph(body)
                self.assertEqual(
                    result["classification"], "unknown" if clobber else "guard-only"
                )
                if clobber:
                    self.assertIn("unproved-return-address", self.reasons(result))

    def test_stack_write_from_unknown_base_is_not_ignored(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "add", R("sp"), R("x0"), I(0)),
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unmodelled-stack-write", self.reasons(result))

    def test_callee_must_restore_stack_before_following_return(self):
        for restored in (False, True):
            with self.subTest(restored=restored):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "bl", I(32)),
                        instruction(8, "mov", R("x19"), R("x0")),
                        instruction(12, "ret"),
                        instruction(32, "sub", R("sp"), R("sp"), I(16)),
                        instruction(36, "ldr", R("x8"), M("x19", 40)),
                        instruction(40, "add", R("sp"), R("sp"), I(16))
                        if restored
                        else instruction(40, "nop"),
                        instruction(44, "ret"),
                    ]
                )
                self.assertEqual(
                    result["classification"], "guard-only" if restored else "unknown"
                )
                self.assertEqual(
                    "callee-stack-not-restored-at-return" in self.reasons(result),
                    not restored,
                )

    def test_nonstandard_return_register_is_not_assumed_to_be_caller(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "bl", I(32)),
                instruction(8, "mov", R("x19"), R("x0")),
                instruction(12, "ret"),
                instruction(32, "ldr", R("x8"), M("x19", 40)),
                instruction(36, "ret", R("x9")),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("unproved-nonstandard-return-register", self.reasons(result))

    def test_diamond_keeps_clean_and_live_paths_separate(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "cbz", R("x0"), I(16)),
                instruction(8, "mov", R("x19"), R("x1")),
                instruction(12, "b", I(20)),
                instruction(16, "nop"),
                instruction(20, "ldr", R("x8"), M("x19", 40)),
                instruction(24, "mov", R("x19"), R("x0")),
                instruction(28, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(result["analysis_strategy"], "bounded-exact-state-partitions")
        self.assertGreater(result["state_partitions"], len(result["accesses"]))

    def test_partitioned_diamond_retains_correlated_spill_and_alias(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "cbz", R("x1"), I(24)),
                instruction(8, "str", R("x19"), M("sp")),
                instruction(12, "mov", R("x0"), R("sp")),
                instruction(16, "b", I(40)),
                instruction(24, "str", R("x19"), M("sp", 8)),
                instruction(28, "add", R("x0"), R("sp"), I(8)),
                instruction(32, "b", I(40)),
                instruction(40, "ldr", R("x20"), M("x0")),
                instruction(44, "ldr", R("x8"), M("x20", 40)),
                instruction(48, "str", R("x1"), M("sp")),
                instruction(52, "str", R("x1"), M("sp", 8)),
                instruction(56, "mov", R("x19"), R("x1")),
                instruction(60, "mov", R("x20"), R("x1")),
                instruction(64, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(result["accesses"][0]["pc"], "0x2c")

    def test_partition_with_one_unsafe_consumer_still_rejects_site(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "cbz", R("x0"), I(20)),
                instruction(12, "mov", R("x19"), R("x1")),
                instruction(16, "b", I(20)),
                instruction(20, "ldr", R("x9"), M("x19", 8)),
                instruction(24, "mov", R("x19"), R("x1")),
                instruction(28, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))

    def test_identical_state_loop_converges_without_erasing_live_state(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "cbz", R("x0"), I(4)),
                instruction(12, "mov", R("x19"), R("x1")),
                instruction(16, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertLess(result["state_partitions"], 10)
        changing = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "add", R("x19"), R("x19"), I(1)),
                instruction(12, "b", I(8)),
            ],
            max_contexts=6,
        )
        self.assertEqual(changing["classification"], "unknown")
        self.assertIn("context-limit", self.reasons(changing))
        self.assertEqual(changing["state_partitions"], 6)

    def test_partition_identity_includes_all_lattices_and_frame_shape(self):
        first = audit.State(registers={"x19": frozenset({0}), "x20": audit.CLEAN})
        same = audit.State(registers={"x20": audit.CLEAN, "x19": frozenset({0})})
        self.assertEqual(first.fingerprint(), same.fingerprint())
        for name in ("registers", "stack", "aliases", "stack_aliases"):
            changed = first.copy()
            getattr(changed, name)["x1" if name in {"registers", "aliases"} else 8] = (
                frozenset({8})
            )
            self.assertNotEqual(first.fingerprint(), changed.fingerprint())
        for attribute in ("sp", "fp"):
            changed = first.copy()
            setattr(changed, attribute, 16)
            self.assertNotEqual(first.fingerprint(), changed.fingerprint())

    def test_default_register_facts_merge_but_clean_spill_slots_do_not(self):
        empty = audit.State()
        for name in ("registers", "aliases", "return_values"):
            changed = empty.copy()
            getattr(changed, name)["x9"] = audit.CLEAN
            self.assertEqual(empty.fingerprint(), changed.fingerprint())
        for name in ("stack", "stack_aliases", "stack_returns"):
            changed = empty.copy()
            getattr(changed, name)[8] = audit.CLEAN
            self.assertNotEqual(empty.fingerprint(), changed.fingerprint())

    def test_clean_register_diamonds_do_not_multiply_equivalent_contexts(self):
        graph = [instruction(0, "mrs", R("x19"))]
        for index in range(8):
            pc = 4 + index * 16
            graph.extend([
                instruction(pc, "cbz", R("x0"), I(pc + 12)),
                # Both paths remain unknown scalars. An exact zero now has
                # distinct semantics and must retain its own partition.
                instruction(pc + 4, "mov", R("x" + str(index)), R("x18")),
                instruction(pc + 8, "b", I(pc + 16)),
                instruction(pc + 12, "nop"),
            ])
        graph.extend([instruction(132, "ldr", R("x8"), M("x19", 40)),
                      instruction(136, "mov", R("x19"), R("xzr")), instruction(140, "ret")])
        result = self.run_graph(graph, max_contexts=40)
        self.assertEqual(result["classification"], "guard-only")
        self.assertNotIn("context-limit", self.reasons(result))

    def test_mrs_return_helper_needs_explicit_verified_caller_context(self):
        graph = [
            instruction(0, "bl", I(32)),
            instruction(4, "ldr", R("x8"), M("x19", 40)),
            instruction(8, "mov", R("x19"), R("x0")),
            instruction(12, "ret"),
            instruction(32, "mrs", R("x19")),
            instruction(36, "ret"),
        ]
        unknown = self.run_graph(graph, site=32)
        self.assertIn("live-pointer-return-needs-caller-context", self.reasons(unknown))
        contextual = self.run_graph(graph, site=32, caller=0)
        self.assertEqual(contextual["classification"], "guard-only")
        self.assertEqual(contextual["caller_context"], "0x0")
        with self.assertRaisesRegex(ValueError, "exact direct BL"):
            self.run_graph(graph, site=32, caller=4)

    def test_rooted_caller_prologue_initializes_frame_and_saved_aliases(self):
        graph = {
            item.address: item
            for item in [
                instruction(0, "sub", R("sp"), R("sp"), I(64)),
                instruction(4, "stp", R("x29"), R("x30"), M("sp", 48)),
                instruction(8, "add", R("x29"), R("sp"), I(48)),
                instruction(12, "bl", I(32)),
                instruction(16, "ldr", R("x8"), M("x19", 40)),
                instruction(20, "mov", R("x19"), R("x0")),
                instruction(24, "add", R("sp"), R("sp"), I(64)),
                instruction(28, "ret"),
                instruction(32, "mrs", R("x19")),
                instruction(36, "ret"),
            ]
        }
        prepared = audit.caller_prologue(graph.get, 0, 12)
        self.assertEqual((prepared.sp, prepared.fp), (-64, -16))
        self.assertEqual(prepared.stack_aliases[-16], audit.CLEAN)

        class RootedDecoder:
            def __call__(self, address):
                return graph.get(address)

            def caller_frame(self, caller):
                return audit.caller_prologue(self, 0, caller), {
                    "status": "synthetic-validated-entry-context",
                    "entry": "0x0",
                }

        result = audit.Audit(RootedDecoder()).site(32, caller=12)
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(result["caller_frame"]["entry"], "0x0")

    def test_caller_prelude_models_only_eight_byte_vector_saves(self):
        save = audit.Instruction(
            0, "stp", (R("d9"), R("d8"), M("sp", -16)), writeback=True, width=8
        )
        state = audit.caller_prologue({0: save}.get, 0, 4)
        self.assertEqual(state.sp, -16)
        self.assertEqual(set(state.stack), {-16, -8})
        self.assertEqual(state.stack_aliases[-16], audit.CLEAN)
        wide = audit.Instruction(
            0, "stp", (R("q9"), R("q8"), M("sp", -32)), writeback=True, width=16
        )
        with self.assertRaisesRegex(audit.MetadataUnavailable, "stack store"):
            audit.caller_prologue({0: wide}.get, 0, 4)

    def test_caller_prelude_accepts_decoder_fp_lr_register_names(self):
        body = {
            0: audit.Instruction(
                0, "stp", (R("fp"), R("lr"), M("sp", -16)), writeback=True
            ),
            4: instruction(4, "mov", R("fp"), R("sp")),
        }
        state = audit.caller_prologue(body.get, 0, 8)
        self.assertEqual(state.sp, -16)
        self.assertEqual(state.fp, -16)
        self.assertEqual(set(state.stack), {-16, -8})

    def test_caller_prelude_rejects_skipped_branches_calls_and_unknown_stores(self):
        for operation in [
            instruction(0, "b", I(4)),
            instruction(0, "bl", I(32)),
            instruction(0, "ldr", R("x29"), M("x0")),
            instruction(0, "str", R("x19"), M("x0")),
            instruction(0, "add", R("sp"), R("x0"), I(8)),
        ]:
            with (
                self.subTest(operation=operation.mnemonic),
                self.assertRaises(audit.MetadataUnavailable),
            ):
                audit.caller_prologue({0: operation}.get, 0, 4)
        with self.assertRaisesRegex(audit.MetadataUnavailable, "bound"):
            audit.caller_prologue({}.get, 0, 4 * (audit.MAX_CALLER_PRELUDE + 1))

    def test_missing_root_proof_is_sticky_even_if_raw_caller_reads_guard(self):
        graph = {
            item.address: item
            for item in [
                instruction(0, "bl", I(32)),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "mov", R("x19"), R("x0")),
                instruction(12, "ret"),
                instruction(32, "mrs", R("x19")),
                instruction(36, "ret"),
            ]
        }

        class UnrootedDecoder:
            def __call__(self, address):
                return graph.get(address)

            def caller_frame(self, caller):
                return None, {"status": "unknown", "reason": "entry unavailable"}

        result = audit.Audit(UnrootedDecoder()).site(32, caller=0)
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("caller-prelude:entry unavailable", self.reasons(result))

    def test_non_guard_offset_is_not_accepted(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 16)),
                instruction(8, "mov", R("x19"), R("x0")),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))

    def test_indirect_escape_and_unknown_consumers_remain_unknown(self):
        for item in [
            instruction(4, "blr", R("x1")),
            instruction(
                4, "eor", R("x8"), R("x19"), R("x0"), reads=("x19",), writes=("x8",)
            ),
        ]:
            with self.subTest(item=item.mnemonic):
                result = self.run_graph([instruction(0, "mrs", R("x19")), item])
                self.assertEqual(result["classification"], "unknown")
                self.assertTrue(result["reasons"])

    def test_pointer_spill_reload_and_guard(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp", 8)),
                instruction(8, "mov", R("x19"), R("x0")),
                instruction(12, "ldr", R("x20"), M("sp", 8)),
                instruction(16, "ldr", R("x8"), M("x20", 40)),
                instruction(20, "str", R("x0"), M("sp", 8)),
                instruction(24, "mov", R("x20"), R("x0")),
                instruction(28, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")

    def test_stack_address_argument_cannot_hide_spilled_pointer_consumer(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp")),
                instruction(12, "add", R("x0"), R("sp"), I(0)),
                instruction(16, "bl", I(64)),
                instruction(20, "str", R("x1"), M("sp")),
                instruction(24, "mov", R("x19"), R("x1")),
                instruction(28, "ret"),
                instruction(64, "ldr", R("x22"), M("x0")),
                instruction(68, "ldr", R("x8"), M("x22")),
                instruction(72, "mov", R("x22"), R("x1")),
                instruction(76, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("non-guard-or-unmodelled-tls-memory-access", self.reasons(result))
        self.assertNotIn(
            "stack-address-materialization-unmodelled", self.reasons(result)
        )
        self.assertNotIn(
            "unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(result)
        )

    def test_preexisting_argument_stack_alias_is_not_assumed_disjoint(self):
        # x0 can point at sp before this MRS. No ADD/MOV from sp is needed
        # between the origin and the hidden load in the callee.
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp")),
                instruction(12, "bl", I(64)),
                instruction(16, "str", R("x1"), M("sp")),
                instruction(20, "mov", R("x19"), R("x1")),
                instruction(24, "ret"),
                instruction(64, "ldr", R("x22"), M("x0")),
                instruction(68, "ldr", R("x8"), M("x22")),
                instruction(72, "mov", R("x22"), R("x1")),
                instruction(76, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertNotIn(
            "stack-address-materialization-unmodelled", self.reasons(result)
        )
        self.assertIn(
            "unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(result)
        )

    def test_wide_frame_address_materialization_is_tracked_but_other_forms_unknown(
        self,
    ):
        forms = [
            instruction(12, "mov", R("x0"), R("sp")),
            instruction(12, "add", R("x0"), R("x29"), I(8)),
            instruction(12, "sub", R("x0"), R("sp"), I(8)),
            instruction(12, "add", R("w0"), R("wsp"), I(0)),
            instruction(
                12, "orr", R("x0"), R("xzr"), R("x29"), reads=("x29",), writes=("x0",)
            ),
        ]
        for form in forms:
            with self.subTest(form=form):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "mov", R("x29"), R("sp")),
                        instruction(8, "ldr", R("x8"), M("x19", 40)),
                        form,
                        instruction(16, "mov", R("x19"), R("x1")),
                        instruction(20, "ret"),
                    ]
                )
                if form.mnemonic in {"mov", "add", "sub"} and form.operands[0] != R(
                    "w0"
                ):
                    self.assertEqual(result["classification"], "guard-only")
                else:
                    self.assertEqual(result["classification"], "unknown")
                    self.assertTrue(self.reasons(result))

    def test_known_stack_alias_copy_and_spill_preserve_tp_consumer(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp", 16)),
                instruction(8, "add", R("x0"), R("sp"), I(16)),
                instruction(12, "mov", R("x1"), R("x0")),
                instruction(16, "str", R("x1"), M("sp", 32)),
                instruction(20, "ldr", R("x2"), M("sp", 32)),
                instruction(24, "ldr", R("x22"), M("x2")),
                instruction(28, "ldr", R("x8"), M("x22", 40)),
                instruction(32, "str", R("x3"), M("sp", 16)),
                instruction(36, "mov", R("x22"), R("x3")),
                instruction(40, "mov", R("x19"), R("x3")),
                instruction(44, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(result["accesses"][0]["pc"], "0x1c")

    def test_partial_alias_spill_rejects_but_disjoint_alias_paths_do_not_merge(self):
        for partial in (True, False):
            with self.subTest(partial=partial):
                body = [
                    instruction(0, "mrs", R("x19")),
                    instruction(4, "ldr", R("x8"), M("x19", 40)),
                    instruction(8, "mov", R("x0"), R("sp")),
                ]
                if partial:
                    body += [
                        instruction(12, "str", R("x0"), M("sp", 16)),
                        audit.Instruction(16, "str", (R("w1"), M("sp", 20)), width=4),
                    ]
                else:
                    body += [
                        instruction(12, "cbz", R("x1"), I(24)),
                        instruction(16, "mov", R("x0"), R("x2")),
                        instruction(20, "b", I(24)),
                        instruction(24, "ldr", R("x3"), M("x0")),
                    ]
                body += [
                    instruction(28, "mov", R("x19"), R("x1")),
                    instruction(32, "ret"),
                ]
                result = self.run_graph(body)
                self.assertEqual(
                    result["classification"], "unknown" if partial else "guard-only"
                )
                if partial:
                    self.assertIn(
                        "partial-overlap-with-thread-pointer-stack-spill",
                        self.reasons(result),
                    )

    def test_narrow_sp_write_and_stack_return_target_stay_unknown(self):
        for operation in (
            instruction(8, "add", R("wsp"), R("wsp"), I(16)),
            instruction(8, "mov", R("x30"), R("sp")),
        ):
            with self.subTest(operation=operation):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "ldr", R("x8"), M("x19", 40)),
                        operation,
                        instruction(12, "mov", R("x19"), R("x0")),
                        instruction(16, "ret"),
                    ]
                )
                self.assertEqual(result["classification"], "unknown")
                self.assertIn(
                    "narrowed-stack-pointer-write-unmodelled"
                    if operation.mnemonic == "add"
                    else "stack-address-used-as-return-target",
                    self.reasons(result),
                )

    def test_frame_pointer_store_escape_stays_unknown(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "mov", R("x29"), R("sp")),
                instruction(8, "ldr", R("x8"), M("x19", 40)),
                instruction(12, "str", R("x29"), M("x0")),
                instruction(16, "mov", R("x19"), R("x1")),
                instruction(20, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("frame-address-store-escape-unmodelled", self.reasons(result))

    def test_stack_deallocation_does_not_silently_erase_spill_taint(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "ldr", R("x8"), M("x19", 40)),
                instruction(8, "str", R("x19"), M("sp")),
                instruction(12, "add", R("sp"), R("sp"), I(16)),
                instruction(16, "mov", R("x19"), R("x1")),
                instruction(20, "ldr", R("x22"), M("x0")),
                instruction(24, "ret"),
            ]
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn(
            "unmodelled-load-may-alias-thread-pointer-stack-spill", self.reasons(result)
        )

    def test_partial_spill_load_or_overwrite_cannot_keep_guard_only(self):
        accesses = [
            audit.Instruction(12, "ldr", (R("w22"), M("sp", 4)), width=4),
            audit.Instruction(12, "str", (R("w0"), M("sp", 4)), width=4),
            audit.Instruction(12, "str", (R("x0"), M("sp", 4)), width=8),
        ]
        for access in accesses:
            with self.subTest(access=access):
                result = self.run_graph(
                    [
                        instruction(0, "mrs", R("x19")),
                        instruction(4, "ldr", R("x8"), M("x19", 40)),
                        instruction(8, "str", R("x19"), M("sp")),
                        access,
                        instruction(16, "str", R("x0"), M("sp")),
                        instruction(20, "mov", R("x19"), R("x0")),
                        instruction(24, "ret"),
                    ]
                )
                self.assertEqual(result["classification"], "unknown")
                self.assertIn(
                    "partial-overlap-with-thread-pointer-stack-spill",
                    self.reasons(result),
                )

    def test_loop_budget_is_explicit(self):
        result = self.run_graph(
            [
                instruction(0, "mrs", R("x19")),
                instruction(4, "add", R("x19"), R("x19"), I(1)),
                instruction(8, "b", I(4)),
            ],
            max_steps=4,
        )
        self.assertEqual(result["classification"], "unknown")
        self.assertIn("instruction-budget", self.reasons(result))


class BindingElfFixture:
    """Synthetic bytes, never executed; no APK addresses or symbol allowlist."""

    def __init__(self):
        self.data = bytearray(0x2000)
        self.data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<H", self.data, 18, 183)
        struct.pack_into("<Q", self.data, 32, 64)
        struct.pack_into("<Q", self.data, 40, 0x1800)
        struct.pack_into("<HHHH", self.data, 54, 56, 3, 64, 4)
        for index, segment in enumerate(
            [
                (1, 5, 0, 0, 0, 0x1000, 0x1000, 0x1000),
                (1, 6, 0x1000, 0x1000, 0, 0x1000, 0x1000, 0x1000),
                (2, 6, 0x1100, 0x1100, 0, 0x100, 0x100, 8),
            ]
        ):
            struct.pack_into("<IIQQQQQQ", self.data, 64 + index * 56, *segment)
        strings = b"\0local_function\0libfixture.so\0"
        self.data[0x1300 : 0x1300 + len(strings)] = strings
        self.tags = [
            (6, 0x1200),
            (11, 24),
            (5, 0x1300),
            (10, len(strings)),
            (14, strings.index(b"libfixture")),
            (23, 0x1400),
            (2, 24),
            (20, 7),
        ]
        self.dynamic()
        struct.pack_into("<IBBHQQ", self.data, 0x1218, 1, 0x12, 0, 1, 0x200, 8)
        struct.pack_into("<QQq", self.data, 0x1400, 0x1500, (1 << 32) | 1026, 0)
        sections = [
            (0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            (0, 1, 6, 0x200, 0x200, 0x100, 0, 0, 4, 0),
            (0, 11, 2, 0x1200, 0x1200, 48, 3, 0, 8, 24),
            (0, 3, 2, 0x1300, 0x1300, len(strings), 0, 0, 1, 0),
        ]
        for index, section in enumerate(sections):
            struct.pack_into("<IIQQQQIIQQ", self.data, 0x1800 + index * 64, *section)
        struct.pack_into("<II", self.data, 0x200, 0xF9401668, 0xD65F03C0)

    def unwind(self, *, second=None):
        # A genuine-format, never-executed header -> CIE -> FDE chain.
        struct.pack_into("<H", self.data, 56, 4)
        count = 2 if second is not None else 1
        struct.pack_into(
            "<IIQQQQQQ",
            self.data,
            64 + 3 * 56,
            0x6474E550,
            4,
            0x1700,
            0x1700,
            0,
            12 + count * 8,
            12 + count * 8,
            4,
        )
        struct.pack_into(
            "<4siI",
            self.data,
            0x1700,
            bytes.fromhex("011b033b"),
            0x1740 - 0x1704,
            count,
        )
        cie_content = bytes(4) + b"\x01zR\0\x01\x78\x1e\x01\x1b" + bytes(3)
        struct.pack_into("<I", self.data, 0x1740, len(cie_content))
        self.data[0x1744 : 0x1744 + len(cie_content)] = cie_content
        for ordinal, (begin, size) in enumerate(
            [(0x200, 0x40)] + ([] if second is None else [second])
        ):
            fde = 0x1760 + ordinal * 0x20
            struct.pack_into(
                "<ii", self.data, 0x170C + ordinal * 8, begin - 0x1700, fde - 0x1700
            )
            struct.pack_into(
                "<IIiiI",
                self.data,
                fde,
                16,
                fde + 4 - 0x1740,
                begin - (fde + 8),
                size,
                0,
            )
        # Remove the sized symbol so the selected caller needs the FDE fallback.
        struct.pack_into("<Q", self.data, 0x1228, 0)

    def dynamic(self):
        self.data[0x1100:0x1200] = bytes(0x100)
        for index, pair in enumerate([*self.tags, (0, 0)]):
            struct.pack_into("<QQ", self.data, 0x1100 + index * 16, *pair)

    def proof(self):
        digest = hashlib.sha256(self.data).hexdigest()
        return {
            "schema": 1,
            "namespace": "nativebridge",
            "snapshot": "post-link-before-open",
            "modules": [
                {
                    "name": "libfixture.so",
                    "elf_sha256": digest,
                    "bindings": [
                        {
                            "relocation": 0x1500,
                            "symbol": "local_function",
                            "version": "",
                            "provider": "libfixture.so",
                            "provider_elf_sha256": digest,
                            "target": 0x200,
                            "kind": "function",
                            "abi": "guest-AAPCS64",
                        }
                    ],
                }
            ],
        }

    def metadata(self, proof=None, bridge=None):
        metadata = audit.ElfBindingMetadata(bytes(self.data), "libfixture.so", bridge)
        if proof is not None:
            metadata.load_proof(proof, "9" * 64)
        return metadata


class CallerFunctionMetadataTests(unittest.TestCase):
    def test_unique_sized_defined_function_entry_is_required(self):
        fixture = BindingElfFixture()
        self.assertEqual(fixture.metadata().caller_function(0x204), (0x200, 8))
        with self.assertRaisesRegex(audit.MetadataUnavailable, "unique sized"):
            fixture.metadata().caller_function(0x208)
        struct.pack_into("<Q", fixture.data, 0x1228, 0)
        with self.assertRaisesRegex(audit.MetadataUnavailable, "unique sized"):
            fixture.metadata().caller_function(0x204)

    def test_overlapping_roots_and_non_executable_symbol_ranges_are_rejected(self):
        fixture = BindingElfFixture()
        struct.pack_into("<Q", fixture.data, 0x1800 + 2 * 64 + 32, 72)
        struct.pack_into("<IBBHQQ", fixture.data, 0x1230, 1, 0x12, 0, 1, 0x200, 12)
        with self.assertRaisesRegex(audit.MetadataUnavailable, "unique sized"):
            fixture.metadata().caller_function(0x204)
        fixture = BindingElfFixture()
        struct.pack_into("<Q", fixture.data, 0x1220, 0x2FC)
        struct.pack_into("<Q", fixture.data, 0x1228, 12)
        with self.assertRaisesRegex(ValueError, "executable symbol section"):
            fixture.metadata().caller_function(0x300)


class UnwindCallerEntryTests(unittest.TestCase):
    def test_checked_fde_supplies_actual_size_and_provenance(self):
        fixture = BindingElfFixture()
        fixture.unwind()
        metadata = fixture.metadata()
        self.assertEqual(metadata.caller_function(0x220), (0x200, 0x40))
        self.assertEqual(
            metadata.caller_function(0x220, with_source=True),
            (0x200, 0x40, "ELF-PT_GNU_EH_FRAME-CIE-FDE"),
        )
        with self.assertRaises(audit.MetadataUnavailable):
            metadata.caller_function(0x240)

    def test_existing_sized_symbol_path_remains_preferred(self):
        fixture = BindingElfFixture()
        fixture.unwind()
        struct.pack_into("<Q", fixture.data, 0x1228, 8)
        self.assertEqual(
            fixture.metadata().caller_function(0x204, with_source=True),
            (0x200, 8, "ELF-defined-sized-STT_FUNC"),
        )

    def test_fde_extent_is_not_inferred_from_next_table_entry(self):
        fixture = BindingElfFixture()
        fixture.unwind(second=(0x280, 0x10))
        with self.assertRaises(audit.MetadataUnavailable):
            fixture.metadata().caller_function(0x250)

    def test_overlapping_distinct_fdes_stay_ambiguous(self):
        fixture = BindingElfFixture()
        fixture.unwind(second=(0x220, 0x20))
        with self.assertRaises(audit.MetadataUnavailable):
            fixture.metadata().caller_function(0x224)

    def test_malformed_header_cie_and_fde_are_rejected(self):
        mutations = [
            (0x1700, "<I", 0, "header encoding"),
            (0x1708, "<I", 1000000, "table bounds"),
            (0x1764, "<I", 0, "invalid unwind FDE"),
            (0x1764, "<I", 4, "extent mismatch"),
            (0x1744, "<I", 1, "invalid unwind CIE"),
            (0x1748, "<B", 4, "CIE version"),
            (0x174E, "<B", 29, "return register"),
            (0x1750, "<B", 0, "FDE pointer encoding"),
            (0x1768, "<i", 0, "function mismatch"),
            (0x176C, "<i", 0, "function mismatch"),
            (0x1770, "<B", 127, "FDE augmentation"),
            (0x176C, "<i", 0x1000, "outside readable"),
        ]
        for address, form, value, message in mutations:
            with self.subTest(address=address, value=value):
                fixture = BindingElfFixture()
                fixture.unwind()
                struct.pack_into(form, fixture.data, address, value)
                with self.assertRaisesRegex(ValueError, message):
                    fixture.metadata().caller_function(0x220)

    def test_unsorted_table_and_header_load_mismatch_are_rejected(self):
        fixture = BindingElfFixture()
        fixture.unwind(second=(0x1F0, 0x10))
        with self.assertRaisesRegex(ValueError, "unsorted"):
            fixture.metadata().caller_function(0x220)
        fixture = BindingElfFixture()
        fixture.unwind()
        struct.pack_into("<Q", fixture.data, 64 + 3 * 56 + 8, 0x1708)
        with self.assertRaisesRegex(ValueError, "header/LOAD"):
            fixture.metadata().caller_function(0x220)


class PltBindingAuditTests(unittest.TestCase):
    def terminal_proof(self, fixture, bridge):
        proof = fixture.proof()
        proof["native_bridge"] = {
            "path": str(bridge.resolve()),
            "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
            "verification": "observed-before-and-after-process",
        }
        proof["modules"][0]["bindings"][0].update(
            provider="libc.so",
            provider_elf_sha256=None,
            target=None,
            abi="fixed/nonreturning-noargs",
            nonreturning=True,
            addend=0,
            relocation_type=1026,
        )
        return proof

    def test_native_terminal_requires_explicit_bridge_identity_and_exact_entry(self):
        with tempfile.TemporaryDirectory() as temporary:
            bridge = Path(temporary) / "bridge.so"
            bridge.write_bytes(b"synthetic bridge identity, not execution evidence")
            fixture = BindingElfFixture()
            proof = self.terminal_proof(fixture, bridge)
            self.assertEqual(
                fixture.metadata(proof).resolve(0x1500)["reason"],
                "native-provider-contract-untrusted",
            )
            self.assertEqual(
                fixture.metadata(proof, bridge).resolve(0x1500)["effect"],
                "nonreturning",
            )
            for field, value in (
                ("addend", 4),
                ("abi", "fixed/no-errno"),
                ("kind", "object"),
                ("target", 0x200),
            ):
                changed = self.terminal_proof(fixture, bridge)
                changed["modules"][0]["bindings"][0][field] = value
                self.assertIsNone(
                    fixture.metadata(changed, bridge).resolve(0x1500).get("effect")
                )
            bridge.write_bytes(b"changed binary")
            with self.assertRaisesRegex(ValueError, "bridge identity"):
                fixture.metadata(proof, bridge)

    def test_native_terminal_import_checks_independent_verneed_name_and_hash(self):
        fixture = BindingElfFixture()
        length = next(value for tag, value in fixture.tags if tag == 10)
        extra = b"libc.so\0LIBC\0"
        fixture.data[0x1300 + length : 0x1300 + length + len(extra)] = extra
        fixture.tags = [
            (tag, length + len(extra) if tag == 10 else value)
            for tag, value in fixture.tags
        ]
        struct.pack_into("<Q", fixture.data, 0x1800 + 3 * 64 + 32, length + len(extra))
        fixture.tags.extend(
            [(1, length), (0x6FFFFFF0, 0x1600), (0x6FFFFFFE, 0x1640), (0x6FFFFFFF, 1)]
        )
        fixture.dynamic()
        struct.pack_into("<HH", fixture.data, 0x1600, 0, 2)
        struct.pack_into("<H", fixture.data, 0x121E, 0)
        struct.pack_into("<Q", fixture.data, 0x1220, 0)
        struct.pack_into("<HHIII", fixture.data, 0x1640, 1, 1, length, 16, 0)
        hash_value = 0
        for byte in b"LIBC":
            hash_value = ((hash_value << 4) + byte) & 0xFFFFFFFF
            high = hash_value & 0xF0000000
            if high:
                hash_value ^= high >> 24
            hash_value &= ~high
        struct.pack_into(
            "<IHHII", fixture.data, 0x1650, hash_value, 0, 2, length + 8, 0
        )
        with tempfile.TemporaryDirectory() as temporary:
            bridge = Path(temporary) / "bridge.so"
            bridge.write_bytes(b"synthetic identity")
            proof = self.terminal_proof(fixture, bridge)
            proof["modules"][0]["bindings"][0]["version"] = "LIBC"
            self.assertEqual(
                fixture.metadata(proof, bridge).resolve(0x1500)["effect"],
                "nonreturning",
            )
            struct.pack_into("<I", fixture.data, 0x1650, 0)
            with self.assertRaisesRegex(ValueError, "name/hash/index"):
                fixture.metadata().resolve(0x1500)

    def test_actual_metadata_and_same_module_production_binding_resolve(self):
        fixture = BindingElfFixture()
        result = fixture.metadata(fixture.proof()).resolve(0x1500)
        self.assertEqual(result["target"], 0x200)
        self.assertEqual(result["reason"], "same-module-production-binding")

    def test_same_module_defined_symbol_does_not_guess_preemption(self):
        fixture = BindingElfFixture()
        self.assertEqual(
            fixture.metadata().resolve(0x1500)["reason"],
            "namespace-binding-proof-missing",
        )
        proof = fixture.proof()
        proof["modules"][0]["bindings"][0].update(
            provider="libpreemptor.so", provider_elf_sha256="a" * 64
        )
        self.assertIsNone(fixture.metadata(proof).resolve(0x1500)["target"])
        self.assertEqual(
            fixture.metadata(proof).resolve(0x1500)["reason"],
            "external-or-preempted-namespace-binding",
        )

    def test_link_only_proof_is_not_actual_art_namespace_evidence(self):
        fixture = BindingElfFixture()
        proof = fixture.proof()
        proof["namespace"] = "ordered-link-only"
        with self.assertRaisesRegex(audit.MetadataUnavailable, "actual NativeBridge"):
            fixture.metadata(proof)

    def test_hash_symbol_and_target_contradictions_fail_closed(self):
        fixture = BindingElfFixture()
        proof = fixture.proof()
        proof["modules"][0]["elf_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "ELF hash mismatch"):
            fixture.metadata(proof)
        for key, value, message in [
            ("symbol", "another", "symbol/version"),
            ("target", 0x204, "target mismatch"),
        ]:
            with self.subTest(key=key):
                proof = fixture.proof()
                proof["modules"][0]["bindings"][0][key] = value
                with self.assertRaisesRegex(ValueError, message):
                    fixture.metadata(proof).resolve(0x1500)

    def test_duplicate_records_and_invalid_provider_hash_are_rejected(self):
        fixture = BindingElfFixture()
        proof = fixture.proof()
        proof["modules"][0]["bindings"] *= 2
        with self.assertRaisesRegex(ValueError, "duplicate binding evidence"):
            fixture.metadata(proof)
        proof = fixture.proof()
        proof["modules"][0]["bindings"][0]["provider_elf_sha256"] = "not-a-hash"
        with self.assertRaisesRegex(ValueError, "provider ELF hash"):
            fixture.metadata(proof)

    def test_malformed_elf_tables_reject_before_namespace_resolution(self):
        mutations = [
            (
                "program-header",
                lambda fixture: struct.pack_into("<H", fixture.data, 56, 129),
            ),
            (
                "symbol/alignment",
                lambda fixture: struct.pack_into(
                    "<Q", fixture.data, 0x1408, (2 << 32) | 1026
                ),
            ),
            (
                "section/LOAD mismatch",
                lambda fixture: struct.pack_into(
                    "<Q", fixture.data, 0x1800 + 2 * 64 + 24, 0x1208
                ),
            ),
        ]
        for message, mutate in mutations:
            with self.subTest(message=message):
                fixture = BindingElfFixture()
                mutate(fixture)
                with self.assertRaisesRegex(ValueError, message):
                    fixture.metadata()
        fixture = BindingElfFixture()
        fixture.tags.append((6, 0x1200))
        fixture.dynamic()
        with self.assertRaisesRegex(ValueError, "duplicate relevant"):
            fixture.metadata()

    def versioned_fixture(self):
        fixture = BindingElfFixture()
        fixture.tags.extend(
            [(0x6FFFFFF0, 0x1600), (0x6FFFFFFC, 0x1640), (0x6FFFFFFD, 1)]
        )
        fixture.dynamic()
        struct.pack_into("<HH", fixture.data, 0x1600, 0, 2)
        struct.pack_into("<HHHHIII", fixture.data, 0x1640, 1, 0, 2, 1, 0, 20, 0)
        struct.pack_into("<II", fixture.data, 0x1654, 1, 0)
        return fixture

    def test_named_definition_requires_exact_production_version(self):
        fixture = self.versioned_fixture()
        proof = fixture.proof()
        binding = proof["modules"][0]["bindings"][0]
        binding["version"] = "local_function"
        self.assertEqual(fixture.metadata(proof).resolve(0x1500)["target"], 0x200)
        binding["version"] = "WRONG"
        with self.assertRaisesRegex(ValueError, "symbol/version"):
            fixture.metadata(proof).resolve(0x1500)

    def test_version_definitions_parse_once_for_all_requested_indices(self):
        metadata = self.versioned_fixture().metadata()
        original_read, reads = metadata.read, []

        def counted_read(address, size):
            reads.append((address, size))
            return original_read(address, size)

        metadata.read = counted_read
        self.assertEqual(metadata.defined_version(2), "local_function")
        first = len(reads)
        self.assertGreater(first, 0)
        for index in (2, 99, 2, 1):
            metadata.defined_version(index)
        self.assertEqual(len(reads), first)

    def test_metadata_snapshots_mutable_input_and_keeps_digest_readonly(self):
        fixture = BindingElfFixture()
        metadata = audit.ElfBindingMetadata(fixture.data, "libfixture.so")
        digest = metadata.elf_sha256
        fixture.data[0] = 0
        self.assertEqual(metadata.data[0], 0x7F)
        self.assertEqual(metadata.elf_sha256, digest)
        with self.assertRaises(AttributeError):
            metadata.data = bytes(fixture.data)
        with self.assertRaises(AttributeError):
            metadata.elf_sha256 = "0" * 64

    def test_version_definition_rejects_bad_chains_and_counts(self):
        for offset, fmt, value, message in [
            (0x1640, "<H", 2, "header"),
            (0x1646, "<H", 0, "header"),
            (0x164C, "<I", 4, "header"),
            (0x1650, "<I", 20, "unterminated version definition"),
            (0x1658, "<I", 8, "unterminated version auxiliary"),
            (0x1654, "<I", 0xFFFFFFFF, "string offset"),
        ]:
            with self.subTest(offset=offset, value=value):
                fixture = self.versioned_fixture()
                struct.pack_into(fmt, fixture.data, offset, value)
                with self.assertRaisesRegex(ValueError, message):
                    fixture.metadata().resolve(0x1500)
        fixture = self.versioned_fixture()
        fixture.tags[-1] = (0x6FFFFFFD, 4097)
        fixture.dynamic()
        with self.assertRaisesRegex(ValueError, "count bound"):
            fixture.metadata().resolve(0x1500)

    def test_base_and_imported_versions_do_not_gain_local_resolution(self):
        fixture = self.versioned_fixture()
        struct.pack_into("<H", fixture.data, 0x1642, 1)
        self.assertIsNone(fixture.metadata(fixture.proof()).resolve(0x1500)["target"])
        fixture = self.versioned_fixture()
        struct.pack_into("<H", fixture.data, 0x121E, 0)
        self.assertEqual(
            fixture.metadata(fixture.proof()).resolve(0x1500)["reason"],
            "imported-symbol-version-unsupported",
        )

    def test_named_versions_sectionless_and_ifunc_remain_unknown(self):
        fixture = BindingElfFixture()
        fixture.tags.append((0x6FFFFFF0, 0x1600))
        fixture.dynamic()
        struct.pack_into("<HH", fixture.data, 0x1600, 0, 2)
        self.assertEqual(
            fixture.metadata(fixture.proof()).resolve(0x1500)["reason"],
            "named-symbol-version-unsupported",
        )
        fixture = BindingElfFixture()
        struct.pack_into("<Q", fixture.data, 40, 0)
        with self.assertRaisesRegex(audit.MetadataUnavailable, "sectionless"):
            fixture.metadata()
        fixture = BindingElfFixture()
        fixture.data[0x121C] = 0x1A
        self.assertIsNone(fixture.metadata(fixture.proof()).resolve(0x1500)["target"])

    def test_standard_stub_clobbers_both_scratch_pointer_aliases(self):
        fixture = BindingElfFixture()
        metadata = fixture.metadata(fixture.proof())
        for register in ("x16", "x17"):
            with self.subTest(register=register):
                body = [
                    instruction(0x40, "mrs", R(register)),
                    instruction(0x44, "ldr", R("x8"), M(register, 40)),
                    instruction(0x48, "bl", I(0x80)),
                    instruction(0x4C, "ret"),
                    instruction(0x80, "adrp", R("x16"), I(0x1000)),
                    instruction(0x84, "ldr", R("x17"), M("x16", 0x500)),
                    instruction(0x88, "add", R("x16"), R("x16"), I(0x500)),
                    instruction(0x8C, "br", R("x17")),
                    instruction(0x200, "ldr", R("x9"), M(register, 16)),
                    instruction(0x204, "ret"),
                ]

                class Graph:
                    def __init__(self, instructions):
                        self.body = {item.address: item for item in instructions}

                    def __call__(self, address):
                        return self.body.get(address)

                    def plt(self, address):
                        return audit.plt_stub(self, address, metadata)

                result = audit.Audit(Graph(body)).site(0x40)
                self.assertEqual(result["classification"], "guard-only")
                self.assertEqual(len(result["accesses"]), 1)

    def test_standard_stub_tail_resolution_preserves_bl_context(self):
        fixture = BindingElfFixture()
        body = [
            instruction(0x40, "mrs", R("x19")),
            instruction(0x44, "bl", I(0x80)),
            instruction(0x48, "mov", R("x19"), R("x0")),
            instruction(0x4C, "ret"),
            instruction(0x80, "adrp", R("x16"), I(0x1000)),
            instruction(0x84, "ldr", R("x17"), M("x16", 0x500)),
            instruction(0x88, "add", R("x16"), R("x16"), I(0x500)),
            instruction(0x8C, "br", R("x17")),
            instruction(0x200, "ldr", R("x8"), M("x19", 40)),
            instruction(0x204, "ret"),
        ]

        class Graph:
            def __init__(self, metadata):
                self.body = {item.address: item for item in body}
                self.metadata = metadata

            def __call__(self, address):
                return self.body.get(address)

            def plt(self, address):
                return audit.plt_stub(self, address, self.metadata)

        result = audit.Audit(Graph(fixture.metadata(fixture.proof()))).site(0x40)
        self.assertEqual(result["classification"], "guard-only")
        self.assertEqual(result["plt_bindings"][0]["target"], 0x200)
        missing = audit.Audit(Graph(fixture.metadata())).site(0x40)
        self.assertEqual(missing["classification"], "unknown")
        self.assertIn(
            "PLT-binding:namespace-binding-proof-missing",
            {item["reason"] for item in missing["reasons"]},
        )


if __name__ == "__main__":
    unittest.main()
