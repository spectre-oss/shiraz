# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic record and conditional analysis checks, not runtime certification."""

import copy
import hashlib
import struct
import tempfile
import unittest
from pathlib import Path

from guest_callee_effects import Atomic32Effects, AtomicPath
from guest_mrs_window import validate_mrs_window_record
from guest_namespace import STRIDE, NamespaceDecoder
from test_guest_tpidr_audit import BindingElfFixture, I, M, R, audit, instruction


def window_record():
    return {
        "profile": 1,
        "before": 4096,
        "after": 256,
        "image_stack_exclusion_profile": 1,
        "verification": "mapped-code-default-accessor",
        "execution_claim": False,
        "sites": [0x200],
    }


class MrsWindowRecordTests(unittest.TestCase):
    def setUp(self):
        self.fixture = BindingElfFixture()
        struct.pack_into("<I", self.fixture.data, 0x200, 0xD53BD053)
        self.metadata = self.fixture.metadata()

    def test_types_profiles_and_claims_fail_closed(self):
        invalid = [
            ("profile", True),
            ("profile", 2),
            ("before", False),
            ("before", -1),
            ("before", 4095),
            ("after", 257),
            ("image_stack_exclusion_profile", True),
            ("image_stack_exclusion_profile", 2),
            ("execution_claim", 0),
            ("execution_claim", True),
            ("verification", "assumed"),
        ]
        for key, value in invalid:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate_mrs_window_record(
                    self.metadata, {**window_record(), key: value}
                )
        for record in (None, {}, {**window_record(), "extra": 1}):
            with self.assertRaises(ValueError):
                validate_mrs_window_record(self.metadata, record)

    def test_sites_require_unique_aligned_executable_file_mrs_words(self):
        for sites in (
            "0x200",
            [True],
            [-4],
            [0x201],
            [1 << 64],
            [0x200, 0x200],
            [0x1500],
            [0x204],
            [0x200] * 131073,
        ):
            with self.subTest(sites=str(sites)[:40]), self.assertRaises(ValueError):
                validate_mrs_window_record(
                    self.metadata, {**window_record(), "sites": sites}
                )
        for word in (
            0xD53BD052,
            0xD53BD05F,
            0xD503201F,
        ):  # Reserved x18, discarded XZR, and NOP are inadmissible.
            struct.pack_into("<I", self.fixture.data, 0x200, word)
            with self.assertRaisesRegex(ValueError, "value-producing"):
                validate_mrs_window_record(self.fixture.metadata(), window_record())
        # BSS may belong to an executable LOAD but is not an instruction file byte.
        begin, end, offset, _, flags = self.metadata.loads[0]
        self.metadata.loads[0] = (begin, end, offset, 0x200, flags)
        with self.assertRaisesRegex(ValueError, "file bytes"):
            validate_mrs_window_record(self.metadata, window_record())

    def test_legacy_window_is_validated_but_does_not_claim_exclusion(self):
        legacy = window_record()
        del legacy["image_stack_exclusion_profile"]
        self.assertIsNone(
            validate_mrs_window_record(
                self.metadata, legacy
            ).image_stack_exclusion_profile
        )
        self.assertEqual(
            validate_mrs_window_record(self.metadata, window_record()).sites,
            frozenset({0x200}),
        )

    def test_record_requires_explicit_matching_bridge_and_complete_parse(self):
        with tempfile.TemporaryDirectory() as directory:
            bridge = Path(directory).resolve() / "synthetic-trust-identity"
            bridge.write_bytes(b"synthetic identity only, not an executable bridge")
            proof = self.fixture.proof()
            proof["modules"][0]["mrs_stack_window"] = window_record()
            proof["native_bridge"] = {
                "path": str(bridge),
                "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                "verification": "observed-before-and-after-process",
            }
            self.assertIsNone(self.fixture.metadata(proof).mrs_window_record)
            metadata = self.fixture.metadata(proof, bridge)
            decoder = object.__new__(audit.ElfDecoder)
            decoder.bindings = metadata
            self.assertEqual(
                decoder.mrs_stack_window(0x200)["image_stack_exclusion_profile"], 1
            )
            self.assertIsNone(decoder.mrs_stack_window(0x204))
            namespace = object.__new__(NamespaceDecoder)
            namespace.names = ["root", "libfixture.so"]
            namespace.decoders = [object(), decoder]
            claim = namespace.mrs_stack_window(STRIDE + 0x200)
            self.assertEqual(claim["source_site"], "0x200")
            self.assertEqual(claim["analysis_site"], hex(STRIDE + 0x200))
            self.assertEqual(claim["module"], "libfixture.so")
            self.assertIsNone(namespace.mrs_stack_window(0x200))
            self.assertIsNone(namespace.mrs_stack_window(2 * STRIDE + 0x200))
            legacy = copy.deepcopy(proof)
            del legacy["modules"][0]["mrs_stack_window"][
                "image_stack_exclusion_profile"
            ]
            decoder.bindings = self.fixture.metadata(legacy, bridge)
            self.assertIsNone(decoder.mrs_stack_window(0x200))
            for change in ("bridge", "elf", "bindings"):
                bad = copy.deepcopy(proof)
                if change == "bridge":
                    bad["native_bridge"]["sha256"] = "0" * 64
                elif change == "elf":
                    bad["modules"][0]["elf_sha256"] = "0" * 64
                else:
                    bad["modules"][0]["bindings"] = "not-a-list"
                target = self.fixture.metadata(bridge=bridge)
                with self.assertRaises(ValueError):
                    target.load_proof(bad, "8" * 64)
                self.assertIsNone(target.mrs_window_record)
            # Failed rechecking cannot retain the previous record.
            with self.assertRaises(ValueError):
                metadata.load_proof(proof, "8" * 64)  # duplicate prior relocations
            self.assertIsNone(metadata.mrs_window_record)


class MrsWindowReadTests(unittest.TestCase):
    def run_graph(
        self,
        *,
        profile=True,
        spill=0,
        pair=False,
        end=0x8008,
        store=False,
        indexed=False,
        unknown_base=False,
        literal=None,
        entry_sp=0,
        extra_return=None,
    ):
        body = [
            instruction(0, "mrs", R("x19")),
            instruction(4, "str", R("x19"), M("sp", spill)),
            instruction(8, "adrp", R("x8"), I(0x8000)),
            instruction(
                12,
                "str" if store else "ldp" if pair else "ldr",
                *((R("x0"), R("x1")) if pair else (R("x0"),)),
                audit.Operand(
                    "mem",
                    base="x2" if unknown_base else "x8",
                    index="x2" if indexed else "",
                ),
            ),
            instruction(16, "ldr", R("x19"), M("sp", spill)),
            instruction(20, "ldr", R("x0"), M("x19", 40)),
            instruction(24, "mov", R("x19"), R("xzr")),
            instruction(28, "str", R("xzr"), M("sp", spill)),
            instruction(32, "ret"),
        ]
        if literal is not None:
            body[3] = instruction(12, "ldr", R("x0"), I(literal))
        graph = {item.address: item for item in body}

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def mrs_stack_window(self, site):
                return (
                    {
                        "profile": 1,
                        "before": 4096,
                        "after": 256,
                        "image_stack_exclusion_profile": 1,
                        "trust": "synthetic-test-contract",
                    }
                    if profile
                    else None
                )

            def owned_image_data(self, address, width):
                return 0x8000 <= address and address + width <= end

            def caller_frame(self, pc):
                returns = (
                    {}
                    if extra_return is None
                    else {entry_sp + extra_return: frozenset({1234})}
                )
                return audit.State(sp=entry_sp, stack_returns=returns), {
                    "status": "synthetic-entry-context"
                }

        return audit.Audit(Decoder()).site(
            0, entry_context=bool(entry_sp or extra_return)
        )

    def test_only_trusted_complete_owned_read_span_can_waive_alias(self):
        accepted = self.run_graph()
        self.assertEqual(accepted["classification"], "guard-only")
        self.assertEqual(len(accepted["conditional_image_stack_separation"]), 1)
        for options in (
            {"profile": False},
            {"pair": True},
            {"indexed": True},
            {"unknown_base": True},
            {"end": 0x8004},
        ):
            with self.subTest(options=options):
                report = self.run_graph(**options)
                self.assertEqual(report["classification"], "unknown")
                self.assertEqual(report["conditional_image_stack_separation"], [])

    def test_literal_reads_require_the_entire_owned_span(self):
        self.assertEqual(self.run_graph(literal=0x8000)["classification"], "guard-only")
        report = self.run_graph(literal=0x8004)
        self.assertEqual(report["classification"], "unknown")
        self.assertFalse(report["conditional_image_stack_separation"])
        self.assertIn(
            "unmodelled-literal-load-may-alias-thread-pointer-stack-spill",
            {item["reason"] for item in report["reasons"]},
        )

    def test_first_anchor_and_all_protected_facts_must_fit(self):
        for offset in (-4096, 248):
            self.assertTrue(
                self.run_graph(spill=offset)["conditional_image_stack_separation"]
            )
        for options in (
            {"spill": -4104},
            {"spill": 256},
            {"spill": 256, "entry_sp": -48},
            {"extra_return": 300},
        ):
            with self.subTest(options=options):
                report = self.run_graph(**options)
                self.assertFalse(report["conditional_image_stack_separation"])
                self.assertEqual(report["classification"], "unknown")

    def test_image_store_is_not_a_read_exemption(self):
        report = self.run_graph(store=True)
        self.assertEqual(report["classification"], "unknown")
        self.assertFalse(report["conditional_image_stack_separation"])
        self.assertIn(
            "unmodelled-store-may-clobber-thread-pointer-stack-spill",
            {item["reason"] for item in report["reasons"]},
        )

    def test_atomic_flag_read_does_not_waive_unknown_rmw_target(self):
        graph = {
            item.address: item
            for item in [
                instruction(0, "mrs", R("x19")),
                instruction(4, "str", R("x19"), M("sp")),
                instruction(8, "bl", I(64)),
                instruction(12, "ret"),
                instruction(64, "bti"),
                instruction(104, "ret"),
            ]
        }

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def mrs_stack_window(self, site):
                return {
                    "before": 4096,
                    "after": 256,
                    "image_stack_exclusion_profile": 1,
                }

            def owned_image_data(self, address, width):
                return address == 0x8000 and width == 1

            def atomic32(self, pc):
                return (
                    Atomic32Effects(
                        64,
                        48,
                        "0" * 64,
                        0x8000,
                        (AtomicPath("synthetic", 104, ("x0",)),),
                    )
                    if pc == 64
                    else None
                )

        report = audit.Audit(Decoder()).site(0)
        reasons = {item["reason"] for item in report["reasons"]}
        self.assertNotIn(
            "atomic-flag-load-may-alias-thread-pointer-stack-spill", reasons
        )
        self.assertIn("unmodelled-load-may-alias-thread-pointer-stack-spill", reasons)
        self.assertEqual(report["classification"], "unknown")


if __name__ == "__main__":
    unittest.main()
