# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic bounded extract/table semantics; never native or complete TLS proof."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_jump_table import and32_immediate, recognize_byte_jump_table
from test_guest_jump_table import adr_word
from test_guest_tpidr_audit import I, M, R, audit, instruction

ENTRY = 8
TABLE = 0x2000
BASE = 0x100
DATA = bytes((0, 16, 32, 48))


def words(entry=ENTRY, lsb=4, width=2):
    return (0x53000000 | lsb << 16 | (lsb + width - 1) << 10 | 9 << 5 | 23,
            adr_word(9, (TABLE - ((entry + 4) & ~4095)) >> 12, page=True),
            0x91000000 | 9 << 5 | 9,
            0x12000000 | 28 << 16 | 28 << 10 | 24 << 5 | 21,
            adr_word(10, BASE - (entry + 16)),
            0x38606800 | 23 << 16 | 9 << 5 | 11,
            0x8B000800 | 11 << 16 | 10 << 5 | 10,
            0xD61F0000 | 10 << 5)


def recognize(encoded=None, entry=ENTRY, data=DATA, reader=None):
    encoded = words(entry) if encoded is None else encoded
    graph = {entry + index * 4: value for index, value in enumerate(encoded)}
    return recognize_byte_jump_table(graph.get, entry,
        reader or (lambda address, size: data if address == TABLE and size == len(data) else None))


class ExtractRecognizerTests(unittest.TestCase):
    def test_exact_profile_has_intrinsic_bound_and_no_default(self):
        record = recognize()
        self.assertEqual(record.index_extract, (4, 2))
        self.assertEqual(record.limit, 3)
        self.assertIsNone(record.default_target)
        self.assertEqual(record.body_size, 32)
        self.assertEqual(record.interleaved_and, ("w21", "w24", 0xFFFFFFF1))
        self.assertEqual(record.targets, (256, 320, 384, 448))
        self.assertFalse(record.requires_zero_extended_index)

    def test_each_opcode_and_truncation_rejected_before_table_read(self):
        def forbidden(*args):
            self.fail("invalid profile must not read table")
        masks = (1 << 31, 1 << 31, 1 << 31, 1 << 29, 1 << 31, 1 << 22, 1 << 31, 1)
        for index, mask in enumerate(masks):
            encoded = list(words());encoded[index] ^= mask
            self.assertIsNone(recognize(encoded, reader=forbidden))
        for length in range(8):
            self.assertIsNone(recognize(words()[:length], reader=forbidden))

    def test_working_roles_reserved_masks_and_wrapping_extract_refused(self):
        for replacement in (0x12000000 | 63 << 10 | 24 << 5 | 21,
                            0x12000000 | 28 << 16 | 28 << 10 | 9 << 5 | 21,
                            0x12000000 | 28 << 16 | 28 << 10 | 24 << 5 | 23):
            encoded = list(words());encoded[3] = replacement
            self.assertIsNone(recognize(encoded))
        self.assertIsNone(recognize(words(lsb=8, width=7), data=bytes(128)))
        encoded = list(words());encoded[0] = 0x53000000 | 8 << 16 | 4 << 10 | 9 << 5 | 23
        self.assertIsNone(recognize(encoded))
        self.assertIsNone(recognize(data=b""))

    def test_all_logical_immediate_fields_agree_with_pinned_decoder(self):
        import capstone
        self.assertEqual(capstone.__version__, "5.0.3")
        decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
        decoder.detail = True
        valid = 0
        for rotate in range(64):
            for size in range(64):
                word = 0x12000000 | rotate << 16 | size << 10 | 24 << 5 | 21
                decoded = list(decoder.disasm(word.to_bytes(4, "little"), 0))
                result = and32_immediate(word)
                if decoded:
                    self.assertEqual(decoded[0].mnemonic, "and")
                    self.assertIsNotNone(result)
                    self.assertEqual(result[2], decoded[0].operands[2].imm & 0xFFFFFFFF)
                    valid += 1
                else:
                    self.assertIsNone(result)
        self.assertGreater(valid, 1000)

    def test_minimum_maximum_widths_and_lsr_alias_are_intrinsically_bounded(self):
        for width in range(1, 7):
            record = recognize(words(lsb=32 - width, width=width), data=bytes(1 << width))
            self.assertIsNotNone(record)
            self.assertEqual(record.limit, (1 << width) - 1)
            self.assertEqual(record.index_extract, (32 - width, width))

    def test_real_decoder_and_namespace_handle_absent_default(self):
        from types import SimpleNamespace

        from guest_namespace import STRIDE, NamespaceDecoder
        decoder = object.__new__(audit.ElfDecoder)
        body = bytearray(512)
        for index, value in enumerate(words()):
            body[ENTRY + index * 4:ENTRY + index * 4 + 4] = value.to_bytes(4, "little")
        decoder.data = bytes(body)
        decoder.segments = [(0, len(body), 0)]
        decoder.jump_table_cache = {}
        decoder.bindings = SimpleNamespace(readonly_jump_table_bytes=lambda address, size: DATA if address == TABLE and size == 4 else None)
        record = decoder.byte_jump_table(ENTRY)
        self.assertIsNotNone(record)
        self.assertIsNone(record.default_target)
        namespace = object.__new__(NamespaceDecoder)
        namespace.locate = lambda address: (1, ENTRY, decoder)
        result = namespace.byte_jump_table(STRIDE + ENTRY)
        self.assertIsNone(result.default_target)
        self.assertEqual(result.targets, tuple(STRIDE + target for target in record.targets))
        decoder.jump_table_cache = {}
        decoder.segments = [(ENTRY, ENTRY + 32, ENTRY)]
        self.assertIsNone(decoder.byte_jump_table(ENTRY))


class ExtractFlowTests(unittest.TestCase):
    def report(self, *, seed=None, data=DATA, separation=True, skip_extract=False, encoded=None, masked_slot=17):
        record = recognize(encoded, data=data)
        state = seed or audit.State(sp=-128, constants={"x24": 31})
        lsb, width = record.index_extract
        extract = instruction(8, "lsr" if lsb + width == 32 else "ubfx",
                              R("w" + record.index_register[1:]), R(record.index_source), I(lsb),
                              *((I(width),) if lsb + width != 32 else ()))
        body = [instruction(0, "mrs", R("x19")), instruction(4, "str", R("x19"), M("sp")),
                extract,
                instruction(12, "adrp", R("x9"), I(TABLE)),
                instruction(16, "add", R("x9"), R("x9"), I(0)),
                instruction(20, "and", R("w21"), R("w24"), I(0xFFFFFFF1)),
                instruction(24, "adr", R("x10"), I(BASE)),
                instruction(28, "ldrb", R("w11"), audit.Operand("mem", base="x9", index="x23")),
                instruction(32, "add", R("x10"), R("x10"), audit.Operand("reg", "x11", shift="lsl", shift_amount=2)),
                instruction(36, "br", R("x10"))]
        graph = {item.address: item for item in body}
        if skip_extract:
            graph[8] = instruction(8, "b", I(12))
        for target in record.targets:
            for item in [instruction(target, "add", R("x0"), R("sp"), audit.Operand("reg", "w21", extend="uxtw")),
                         instruction(target + 4, "str", R("x19"), M("x0")),
                         instruction(target + 8, "ldr", R("x8"), M("sp", masked_slot)),
                         instruction(target + 12, "ldr", R("x8"), M("x8", 40)),
                         instruction(target + 16, "str", R("xzr"), M("x0")),
                         instruction(target + 20, "str", R("xzr"), M("sp")),
                         instruction(target + 24, "mov", R("x19"), R("xzr")),
                         instruction(target + 28, "add", R("sp"), R("sp"), I(128)),
                         instruction(target + 32, "ret")]:
                graph[item.address] = item
        visited = []

        class Decoder:
            def __call__(self, pc):
                visited.append(pc)
                return graph.get(pc)

            def byte_jump_table(self, pc):
                return record if pc == ENTRY else None

            def caller_frame(self, pc):
                return state.copy(), {"status": "synthetic-frame"}

            def mrs_stack_window(self, pc):
                return {"before": 4096, "after": 256} if separation else None

            def owned_image_data(self, address, size):
                return address == TABLE and size == len(data)

        report = audit.Audit(Decoder()).site(0, entry_context=True)
        return report, visited

    def test_all_targets_mask_value_and_guard_reload(self):
        report, visited = self.report()
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertTrue({256, 320, 384, 448} <= set(visited))
        self.assertEqual(report["bounded_jump_tables"][0]["index_extract"], (4, 2))

    def test_known_extract_index_selects_only_its_entry(self):
        seed = audit.State(sp=-128, constants={"x24": 31, "x9": 0x20})
        report, visited = self.report(seed=seed)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertIn(384, visited)
        self.assertFalse({256, 320, 448} & set(visited))

    def test_every_source_pointer_control_and_signature_fact_refuses(self):
        for source in ("x9", "x24"):
            for domain, value in (("registers", frozenset({0})), ("aliases", frozenset({-8})),
                                  ("return_values", frozenset({444})), ("image_addresses", 8192),
                                  ("return_signatures", audit.ReturnSignature(frozenset({444}), -128, "A"))):
                seed = audit.State(sp=-128, constants={"x24": 31})
                getattr(seed, domain)[source] = value
                report, _ = self.report(seed=seed)
                self.assertEqual(report["classification"], "unknown")
                self.assertIn("jump-table-extract-or-mask-has-pointer-or-control-fact", {r["reason"] for r in report["reasons"]})

    def test_old_destination_facts_cleared_and_nzcv_preserved(self):
        seed = audit.State(sp=-128, registers={"x21": frozenset({0})}, constants={"x24": 31})
        report, _ = self.report(seed=seed)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        seed.registers["nzcv"] = frozenset({0})
        report, _ = self.report(seed=seed)
        self.assertIn("live-pointer-return-needs-caller-context", {r["reason"] for r in report["reasons"]})

    def test_read_separation_and_entry_bound_cannot_be_skipped(self):
        for arguments in ({"separation": False}, {"skip_extract": True}):
            report, _ = self.report(**arguments)
            self.assertEqual(report["classification"], "unknown")
            self.assertEqual(report["bounded_jump_tables"], [])

    def test_duplicate_targets_retain_distinct_index_contexts(self):
        report, visited = self.report(data=bytes(4))
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(visited.count(BASE), 4)

    def test_w_inputs_ignore_upper_bits(self):
        seed = audit.State(sp=-128, constants={"x24": 0xFFFFFFFF0000001F, "x9": 0xFFFFFFFF00000020})
        report, visited = self.report(seed=seed)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertIn(384, visited)

    def test_mask_source_destination_can_match(self):
        encoded = list(words());encoded[3] = (encoded[3] & ~(31 << 5)) | 21 << 5
        seed = audit.State(sp=-128, constants={"x21": 31})
        report, _ = self.report(seed=seed, encoded=encoded)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])

    def test_extract_uses_old_source_before_mask_destination_overwrites_it(self):
        encoded = list(words());encoded[0] = (encoded[0] & ~(31 << 5)) | 21 << 5
        encoded[3] = 0x12000000 | 27 << 16 | 30 << 10 | 24 << 5 | 21
        seed = audit.State(sp=-128, constants={"x21": 31, "x24": 31})
        report, visited = self.report(seed=seed, encoded=encoded, masked_slot=15)
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertIn(320, visited)
        self.assertFalse({256, 384, 448} & set(visited))

    def test_lsr_alias_is_recognized_from_its_real_decoder_mnemonic(self):
        seed = audit.State(sp=-128, constants={"x24": 31, "x9": 0x80000000})
        report, _ = self.report(seed=seed, data=bytes(64), encoded=words(lsb=26, width=6))
        self.assertEqual(report["classification"], "guard-only", report["reasons"])
        self.assertEqual(report["bounded_jump_tables"][0]["bytes"], 64)
