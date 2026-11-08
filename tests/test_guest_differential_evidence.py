# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic diagnostic classification only, never native execution evidence."""

import hashlib
import json
import signal
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from macos_guest_differential_smoke import (
    CONTRACTS,
    STACK_MARKER,
    classify_darwin_stack_failure,
    entry_binary_identity,
    reference_plan,
    reference_result,
    validate_collector_signature,
)


class DifferentialEvidenceTests(unittest.TestCase):
    fixture_hash = "a" * 64
    jar_hash = "b" * 64

    def setUp(self):
        self.ready = STACK_MARKER + self.fixture_hash
        self.guard = "SHIRAZ_GUEST_FATAL stack guard check failed"
        self.fatal = "Fatal signal 6 (SIGABRT), code 0 (?)"
        self.valid = f"{self.ready}\n{self.guard}\n{self.fatal}"

    def classify(self, code, text, **extra):
        return classify_darwin_stack_failure(
            {"returncode": code, "timed_out": False, **extra}, text, self.fixture_hash
        )

    def test_native_collector_signature_requires_runtime_and_exact_boolean_entitlement(
        self,
    ):
        key = "com.apple.security.cs.allow-jit"
        self.assertEqual(
            validate_collector_signature("flags=0x10002(adhoc,runtime)", {key: True}),
            0x10002,
        )
        for signature, entitlements in (
            ("flags=0x2", {key: True}),
            ("", {key: True}),
            ("flags=0x10000", {key: 1}),
            ("flags=0x10000", {key: False}),
            ("flags=0x10000", {key: True, "extra": True}),
        ):
            with (
                self.subTest(signature=signature, entitlements=entitlements),
                self.assertRaises(ValueError),
            ):
                validate_collector_signature(signature, entitlements)

    def test_entry_identity_hashes_actual_vm_and_bridge_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "bin").mkdir()
            (root / "bin/dalvikvm").write_bytes(b"vm-fixture")
            bridge = root / "bridge.so"
            bridge.write_bytes(b"bridge-fixture")
            before = entry_binary_identity(root, bridge)
            self.assertEqual(
                before["vm"]["sha256"], hashlib.sha256(b"vm-fixture").hexdigest()
            )
            self.assertEqual(before["bridge"]["bytes"], len(b"bridge-fixture"))
            bridge.write_bytes(b"changed-fixture")
            self.assertNotEqual(before, entry_binary_identity(root, bridge))
            with self.assertRaisesRegex(ValueError, "oversized"):
                entry_binary_identity(root, bridge, max_bytes=4)
            bridge.unlink()
            bridge.mkdir()
            with self.assertRaisesRegex(ValueError, "missing/oversized"):
                entry_binary_identity(root, bridge)

    def test_art_darwin_exit1_requires_all_three_ordered_markers(self):
        result = self.classify(1, self.valid)
        self.assertEqual(result["observation"], "art-darwin-handler-exit1")
        self.assertIsNone(result["kernel_signal"])
        self.assertEqual(result["art_reported_signal"], signal.SIGABRT)

    def test_raw_sigabrt_is_distinct_from_art_exit_status(self):
        result = self.classify(-signal.SIGABRT, self.ready + "\n" + self.guard)
        self.assertEqual(result["observation"], "kernel-sigabrt")
        self.assertEqual(result["kernel_signal"], signal.SIGABRT)
        self.assertIsNone(result["art_reported_signal"])

    def test_exit1_without_complete_matching_proof_is_rejected(self):
        cases = {
            "exit alone": "",
            "ready alone": self.ready,
            "ready and guard": self.ready + "\n" + self.guard,
            "ready and fatal": self.ready + "\n" + self.fatal,
            "guard and fatal": self.guard + "\n" + self.fatal,
            "wrong hash": self.valid.replace(self.fixture_hash, "c" * 64),
            "wrong order": f"{self.guard}\n{self.ready}\n{self.fatal}",
            "wrong fatal order": f"{self.ready}\n{self.fatal}\n{self.guard}",
            "other fatal": self.valid + "\nSHIRAZ_GUEST_FATAL unrelated failure",
            "runtime fatal": self.valid + "\nSHIRAZ_RUNTIME_FATAL",
            "other native fatal": self.valid + "\nSHIRAZ_NATIVE_FATAL unrelated",
            "java fatal": self.valid + "\nFATAL EXCEPTION: main",
            "other signal": self.valid + "\nFatal signal 11 (SIGSEGV), code 1 (?)",
            "wrong signal": self.valid.replace("6 (SIGABRT)", "11 (SIGSEGV)"),
            "mismatched signal name": self.valid.replace("SIGABRT", "SIGSEGV"),
            "duplicate ready": self.valid + "\n" + self.ready,
            "duplicate fatal": self.valid + "\n" + self.fatal,
        }
        for name, text in cases.items():
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.classify(1, text)

    def test_other_exit_status_signal_and_timeout_are_rejected(self):
        for code in (0, 2, 134, -signal.SIGSEGV, -signal.SIGKILL):
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.classify(code, self.valid)
        with self.assertRaises(ValueError):
            self.classify(1, self.valid, timed_out=True)

    def test_kernel_abort_with_contradictory_fatal_is_rejected(self):
        with self.assertRaises(ValueError):
            self.classify(
                -signal.SIGABRT, self.valid + "\nFatal signal 11 (SIGSEGV), code 1 (?)"
            )

    def test_android_reference_still_requires_actual_raw_sigabrt(self):
        manifest = {
            "fixture_sha256": self.fixture_hash,
            "jar_sha256": self.jar_hash,
            "collector_sha256": "c" * 64,
        }
        report = {
            "schema": 1,
            **manifest,
            "platform": {"os": "Linux", "vm": "Dalvik", "android_sdk": 35},
            "native": {"schema": 1, "status": "pass", "contracts": CONTRACTS},
        }
        evidence = {
            "schema": 1,
            "collection_method": "native-waitpid",
            "collector_sha256": manifest["collector_sha256"],
            "run": {"returncode": 0, "raw_wait_status": 0, "report": report},
            "stack_failure": {
                "returncode": 1,
                "raw_wait_status": 256,
                "ready_fixture_sha256": self.fixture_hash,
                "observation": "art-darwin-handler-exit1",
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.json"
            path.write_text(json.dumps(evidence))
            with self.assertRaisesRegex(ValueError, "deliberate-corruption child"):
                reference_result(path, manifest)

    def test_reference_plan_requires_native_supervisor_and_scoped_paths(self):
        manifest = {
            "fixture": "source.so",
            "fixture_sha256": self.fixture_hash,
            "jar": "fixture.jar",
            "jar_sha256": self.jar_hash,
            "collector": "collector.so",
            "collector_sha256": "c" * 64,
        }
        stage = "/data/local/tmp/shiraz-m5-fixture"
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch("macos_guest_differential_smoke.WORK", Path(temporary)),
        ):
            reference_plan(manifest, stage)
            plan = json.loads((Path(temporary) / "reference-command.json").read_text())
            self.assertFalse(plan["performed"])
            self.assertEqual(len(plan["artifacts"]), 3)
            self.assertEqual(plan["collector_argv"][0], "/system/bin/app_process64")
            self.assertEqual(plan["collector_argv"][-1], "BionicReferenceRunner")
            self.assertEqual(plan["environment"]["CLASSPATH"], stage + "/fixture.jar")
            self.assertIn(
                "-Dguest.reference.collector.sha256=" + "c" * 64, plan["collector_argv"]
            )
            for invalid in (
                "/data/local/tmp/other",
                stage + "/nested",
                stage + "/../other",
            ):
                with self.subTest(path=invalid), self.assertRaises(ValueError):
                    reference_plan(manifest, invalid)

    def test_reference_requires_wait_status_consistent_with_signal_not_exit134(self):
        manifest = {
            "fixture_sha256": self.fixture_hash,
            "jar_sha256": self.jar_hash,
            "collector_sha256": "c" * 64,
        }
        report = {
            "schema": 1,
            **manifest,
            "platform": {"os": "Linux", "vm": "Dalvik", "android_sdk": 27},
            "native": {"schema": 1, "status": "pass", "contracts": CONTRACTS},
        }
        evidence = {
            "schema": 1,
            "collection_method": "native-waitpid",
            "collector_sha256": manifest["collector_sha256"],
            "run": {"returncode": 0, "raw_wait_status": 0, "report": report},
            "stack_failure": {
                "returncode": -6,
                "raw_wait_status": 6,
                "ready_fixture_sha256": self.fixture_hash,
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.json"
            path.write_text(json.dumps(evidence))
            self.assertEqual(
                reference_result(path, manifest)["collection"]["stack_failure"][
                    "returncode"
                ],
                -6,
            )
            for raw, code in (
                (134 << 8, 134),
                (134 << 8, -6),
                (262, -6),
                (0, -6),
                (-6, -6),
                (True, -6),
            ):
                evidence["stack_failure"].update(raw_wait_status=raw, returncode=code)
                path.write_text(json.dumps(evidence))
                with (
                    self.subTest(raw=raw, code=code),
                    self.assertRaisesRegex(ValueError, "wait status"),
                ):
                    reference_result(path, manifest)
            evidence["stack_failure"].update(raw_wait_status=6, returncode=-6)
            for raw in (6, 128):
                evidence["run"]["raw_wait_status"] = raw
                path.write_text(json.dumps(evidence))
                with self.assertRaisesRegex(ValueError, "positive child"):
                    reference_result(path, manifest)
            evidence["run"]["raw_wait_status"] = 0
            evidence["collector_sha256"] = "d" * 64
            path.write_text(json.dumps(evidence))
            with self.assertRaisesRegex(ValueError, "collector identity"):
                reference_result(path, manifest)


if __name__ == "__main__":
    unittest.main()
