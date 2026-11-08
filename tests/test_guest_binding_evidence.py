# SPDX-License-Identifier: GPL-3.0-only
"""Report identity checks only; synthetic metadata is not guest execution proof."""

import hashlib
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_binding_evidence import (
    augment_binding_report,
    bounded_read,
    verify_relocation_inventory,
)
from test_guest_allocator_contract import malloc_binding
from test_guest_native_target import self_binding
from test_guest_tpidr_audit import BindingElfFixture


class GuestBindingEvidenceTests(unittest.TestCase):
    def test_bounded_read_refuses_growth_and_truncation_between_stat_and_read(self):
        for actual in (b"", b"123", b"12345", b"123456789"):
            path = MagicMock()
            path.stat.return_value.st_size = 4
            stream = path.open.return_value.__enter__.return_value
            stream.read.return_value = actual
            with self.assertRaisesRegex(ValueError, "changed size"):
                bounded_read(path, 8)
            stream.read.assert_called_once_with(9)
        for size in (0, 9):
            path = MagicMock()
            path.stat.return_value.st_size = size
            with self.assertRaisesRegex(ValueError, "byte bound"):
                bounded_read(path, 8)
            path.open.assert_not_called()

    def test_only_undefined_weak_symbols_can_have_no_provider(self):
        fixture = BindingElfFixture()
        binding = {"relocation": 0x1500, "relocation_type": 1026, "addend": 0,
                   "symbol": "local_function", "version": "", "provider": "",
                   "target": None, "abi": "unresolved-weak-null"}
        for info, section, accepted in ((0x22, 0, True), (0x12, 0, False), (0x22, 1, False)):
            struct.pack_into("<IBBHQQ", fixture.data, 0x1218, 1, info, 0, section, 0x200, 8)
            if accepted:
                self.assertEqual(verify_relocation_inventory(fixture.metadata(), [binding]), 1)
            else:
                with self.assertRaisesRegex(ValueError, "provider"):
                    verify_relocation_inventory(fixture.metadata(), [binding])

    def test_inventory_requires_every_real_relocation_and_exact_metadata(self):
        fixture = BindingElfFixture()
        metadata = fixture.metadata()
        binding = {"relocation": 0x1500, "relocation_type": 1026, "addend": 0,
                   "symbol": "local_function", "version": "", "provider": "libfixture.so",
                   "target": 0x200, "abi": "guest-AAPCS64"}
        self.assertEqual(verify_relocation_inventory(metadata, [binding]), 1)
        for rows in ([], [binding, binding], [{**binding, "relocation": 0x1508}]):
            with self.assertRaisesRegex(ValueError, "inventory"):
                verify_relocation_inventory(metadata, rows)
        for field, value in (("symbol", "other"), ("version", "OTHER"),
                             ("relocation_type", 1025), ("addend", 1), ("addend", False)):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "metadata"):
                verify_relocation_inventory(metadata, [{**binding, field: value}])
        for field, value in (("abi", "unrecorded"), ("provider", "")):
            with self.assertRaises(ValueError):
                verify_relocation_inventory(metadata, [{**binding, field: value}])

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

    def test_managed_malloc_contract_requires_bridge_identity_and_exact_row(self):
        identity = self.terminal_binding()
        self.binding.clear()
        self.binding.update(malloc_binding(), relocation=32)
        with self.assertRaisesRegex(ValueError, "managed malloc contract requires bridge identity"):
            self.augment()
        report = self.augment(**identity)
        binding = report["modules"][0]["bindings"][0]
        self.assertEqual(binding["abi"], "heap/managed-malloc-v1")
        self.assertIsNone(binding["target"])
        self.assertIsNone(binding["provider_elf_sha256"])
        self.binding["addend"] = 4
        with self.assertRaisesRegex(ValueError, "managed malloc contract requires exact native binding"):
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

    def test_native_identity_requires_bridge_identity_and_preserves_false_claims(self):
        identity = self.terminal_binding()
        self.binding.clear()
        self.binding.update(self_binding(), relocation=32)
        with self.assertRaisesRegex(ValueError, "requires bridge identity"):
            self.augment()
        report = self.augment(**identity)
        target = report["modules"][0]["bindings"][0]["native_target"]
        self.assertEqual(target["image"], "/usr/lib/system/libsystem_pthread.dylib")
        self.assertEqual(target["mapped_prefix_sha256"], hashlib.sha256(
            bytes.fromhex(self.binding["native_target"]["prefix_hex"])).hexdigest())
        self.assertIs(target["execution_claim"], False)
        self.assertIs(target["semantic_effects_verified"], False)
        self.binding["native_target"] = target
        self.binding["native_target"]["prefix_hex"] = "00" * 64
        with self.assertRaisesRegex(ValueError, "digest"):
            self.augment(**identity)

    def test_native_identity_cannot_be_attached_to_another_adapter(self):
        identity = self.terminal_binding()
        self.binding["native_target"] = self_binding()["native_target"]
        with self.assertRaisesRegex(ValueError, "exact pthread self binding"):
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
