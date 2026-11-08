# SPDX-License-Identifier: GPL-3.0-only
"""Report identity checks only; synthetic metadata is not guest execution proof."""

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_binding_evidence import augment_binding_report


class GuestBindingEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.hashes = {}
        self.modules = []
        for name in ("libcaller.so", "libprovider.so"):
            path = self.root / name
            path.write_bytes(name.encode())
            self.hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
            self.modules.append({"name": name, "file": str(path), "bindings": []})
        self.binding = {
            "relocation": 32,
            "relocation_type": 1026,
            "symbol": "call",
            "version": "",
            "provider": "libprovider.so",
            "target": 128,
            "kind": "function",
            "abi": "guest-AAPCS64",
        }
        self.modules[0]["bindings"].append(self.binding)
        self.report = {
            "schema": 1,
            "namespace": "nativebridge",
            "snapshot": "post-link-before-open",
            "modules": self.modules,
        }

    def augment(self, **identity):
        source = self.root / "raw.json"
        output = self.root / "bindings.json"
        source.write_text(json.dumps(self.report))
        augment_binding_report(
            source, output, self.hashes, self.root, self.hashes, **identity
        )
        return json.loads(output.read_text())

    def terminal_binding(self):
        self.binding.update(
            provider="libc.so",
            kind="function",
            abi="fixed/nonreturning-noargs",
            target=None,
            nonreturning=True,
            addend=0,
        )
        bridge = self.root / "bridge.so"
        bridge.write_bytes(b"synthetic binary identity only")
        return {
            "native_bridge": bridge,
            "bridge_sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
        }

    def test_terminal_contract_requires_pre_post_bridge_identity(self):
        identity = self.terminal_binding()
        with self.assertRaisesRegex(ValueError, "bridge identity"):
            self.augment()
        report = self.augment(**identity)
        self.assertEqual(report["native_bridge"]["sha256"], identity["bridge_sha256"])
        self.assertTrue(report["modules"][0]["bindings"][0]["nonreturning"])
        identity["native_bridge"].write_bytes(b"changed after observed execution")
        with self.assertRaisesRegex(ValueError, "changed since"):
            self.augment(**identity)

    def test_terminal_contract_cannot_label_interior_entry_or_object(self):
        for field, value in (
            ("addend", 4),
            ("kind", "object"),
            ("abi", "fixed/no-errno"),
            ("nonreturning", "true"),
        ):
            identity = self.terminal_binding()
            self.binding[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.augment(**identity)

    def test_preempting_guest_provider_identity_is_preserved(self):
        report = self.augment()
        binding = report["modules"][0]["bindings"][0]
        self.assertEqual(binding["provider"], "libprovider.so")
        self.assertEqual(binding["provider_elf_sha256"], self.hashes["libprovider.so"])
        self.assertNotEqual(
            binding["provider_elf_sha256"], report["modules"][0]["elf_sha256"]
        )

    def test_native_binding_cannot_be_relabelled_as_guest_target(self):
        self.binding.update(provider="libc.so", abi="host-explicit-adapter")
        with self.assertRaisesRegex(ValueError, "must not expose a guest target"):
            self.augment()

    def test_changed_elf_rejected_after_runtime(self):
        (self.root / "libprovider.so").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed since APK"):
            self.augment()

    def test_synthetic_namespace_not_accepted_as_actual_bridge(self):
        self.report["namespace"] = "ordered-link-only"
        with self.assertRaisesRegex(ValueError, "actual NativeBridge"):
            self.augment()

    def test_duplicate_relocation_rejected(self):
        self.modules[0]["bindings"].append(dict(self.binding))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.augment()


if __name__ == "__main__":
    unittest.main()
