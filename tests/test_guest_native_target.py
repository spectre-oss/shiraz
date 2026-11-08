# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic identity validation is not native execution or effect proof."""

import copy
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_native_target import SELF_ABI, SELF_IMAGE, validate_native_target
from test_guest_tpidr_audit import BindingElfFixture


def self_binding():
    return {
        "symbol": "pthread_self", "version": "LIBC", "provider": "libc.so",
        "abi": SELF_ABI, "kind": "function", "target": None,
        "provider_elf_sha256": None, "nonreturning": False,
        "relocation": 0x1500, "relocation_type": 1026, "addend": 0,
        "native_target": {
            "profile": 1, "verification": "resolved-system-image-prefix",
            "image": SELF_IMAGE, "image_uuid": "1234567890abcdef" * 2,
            "symbol": "pthread_self", "entry_offset": 0x200,
            "prefix_hex": "01234567" * 16,
            "execution_claim": False, "semantic_effects_verified": False,
        },
    }


def window_binding(entry_offset=0x200, cold_offset=0x800):
    binding = self_binding()
    record = binding["native_target"]
    code = bytearray(256)
    code[:64] = bytes.fromhex(record["prefix_hex"])
    delta = cold_offset - (entry_offset + 72)
    assert delta % 4 == 0 and -(1 << 27) <= delta < (1 << 27)
    struct.pack_into("<I", code, 72, 0x94000000 | ((delta // 4) & ((1 << 26) - 1)))
    record.update({
        "profile": 2, "verification": "resolved-system-image-code-windows",
        "entry_offset": entry_offset, "cold_call_offset": 72,
        "complete_control_flow_verified": False,
        "code_windows": [
            {"role": "entry", "symbol": "pthread_self", "entry_offset": entry_offset,
             "byte_count": 256, "prefix_hex": code.hex()},
            {"role": "cold-error", "symbol": "pthread_self.cold.1", "entry_offset": cold_offset,
             "byte_count": 128, "prefix_hex": "89abcdef" * 32},
        ],
    })
    return binding


def self_fixture():
    fixture = BindingElfFixture()
    fixture.data[0x1301:0x130E] = b"pthread_self\0"
    length = next(value for tag, value in fixture.tags if tag == 10)
    extra = b"libc.so\0LIBC\0"
    fixture.data[0x1300 + length:0x1300 + length + len(extra)] = extra
    fixture.tags = [(tag, length + len(extra) if tag == 10 else value)
                    for tag, value in fixture.tags]
    struct.pack_into("<Q", fixture.data, 0x1800 + 3 * 64 + 32, length + len(extra))
    fixture.tags.extend([(1, length), (0x6FFFFFF0, 0x1600),
                         (0x6FFFFFFE, 0x1640), (0x6FFFFFFF, 1)])
    fixture.dynamic()
    struct.pack_into("<HH", fixture.data, 0x1600, 0, 2)
    struct.pack_into("<H", fixture.data, 0x121E, 0)
    struct.pack_into("<Q", fixture.data, 0x1220, 0)
    struct.pack_into("<HHIII", fixture.data, 0x1640, 1, 1, length, 16, 0)
    # ELF hash of LIBC, independently checked by the metadata reader.
    struct.pack_into("<IHHII", fixture.data, 0x1650, 0x50D63, 0, 2, length + 8, 0)
    return fixture


class NativeTargetTests(unittest.TestCase):
    def test_code_windows_validate_forward_and_backward_edges_without_effect_grants(self):
        for binding in (window_binding(), window_binding(0x800, 0x200)):
            original = copy.deepcopy(binding)
            result = validate_native_target(binding)
            self.assertEqual(binding, original)
            self.assertIsNot(result["code_windows"], binding["native_target"]["code_windows"])
            self.assertIs(result["execution_claim"], False)
            self.assertIs(result["semantic_effects_verified"], False)
            self.assertIs(result["complete_control_flow_verified"], False)
            for window in result["code_windows"]:
                self.assertEqual(window["mapped_window_sha256"], hashlib.sha256(
                    bytes.fromhex(window["prefix_hex"])).hexdigest())
            binding["native_target"] = result
            self.assertEqual(validate_native_target(binding), result)
            result["code_windows"][0]["mapped_window_sha256"] = "0" * 64
            with self.assertRaisesRegex(ValueError, "digest"):
                validate_native_target(binding)

    def test_code_window_schema_sizes_offsets_roles_and_claims_are_strict(self):
        for field, value in (
            ("profile", 3), ("cold_call_offset", True), ("cold_call_offset", 68),
            ("complete_control_flow_verified", True), ("complete_control_flow_verified", 0),
            ("semantic_effects_verified", True), ("execution_claim", True),
            ("verification", "resolved-system-image-prefix"),
            ("code_windows", None), ("code_windows", []), ("code_windows", {}),
        ):
            binding = window_binding()
            binding["native_target"][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_native_target(binding)
        for index in (0, 1):
            for field, value in (
                ("role", "other"), ("symbol", "other"), ("byte_count", True),
                ("byte_count", 64), ("byte_count", 1 << 64),
                ("entry_offset", True), ("entry_offset", -4), ("entry_offset", 3),
                ("entry_offset", (1 << 64) - 4),
                ("prefix_hex", "a" * 126), ("prefix_hex", "G" * 512),
                ("prefix_hex", None), ("mapped_window_sha256", "0" * 64),
                ("extra", False),
            ):
                binding = window_binding()
                binding["native_target"]["code_windows"][index][field] = value
                with self.subTest(index=index, field=field, value=value), self.assertRaises(ValueError):
                    validate_native_target(binding)
            binding = window_binding()
            binding["native_target"]["code_windows"][index] = None
            with self.assertRaisesRegex(ValueError, "schema"):
                validate_native_target(binding)
        for windows in (
            [window_binding()["native_target"]["code_windows"][0]] * 2,
            window_binding()["native_target"]["code_windows"][::-1],
            window_binding()["native_target"]["code_windows"] * 2,
        ):
            binding = window_binding()
            binding["native_target"]["code_windows"] = windows
            with self.assertRaises(ValueError):
                validate_native_target(binding)

    def test_code_window_prefix_overlap_and_branch_targets_are_independently_checked(self):
        for mutate in (
            lambda r: r["code_windows"][0].update(entry_offset=0x204),
            lambda r: r["code_windows"][0].update(prefix_hex="00" + r["code_windows"][0]["prefix_hex"][2:]),
            lambda r: r["code_windows"][1].update(entry_offset=0x280),
            lambda r: r["code_windows"][1].update(entry_offset=0x804),
            lambda r: r["code_windows"][0].update(prefix_hex=(
                r["code_windows"][0]["prefix_hex"][:144] + "00000014" +
                r["code_windows"][0]["prefix_hex"][152:])),
        ):
            binding = window_binding()
            mutate(binding["native_target"])
            with self.assertRaises(ValueError):
                validate_native_target(binding)
        # These encoded branches must not wrap uintptr_t to reach an otherwise
        # valid bounded cold window at the opposite end of the address space.
        for entry_offset, cold_offset, instruction in (
            (0, (1 << 64) - 128, 0x97FFFFCE),
            ((1 << 64) - 256, 0, 0x9400002E),
        ):
            binding = window_binding()
            record = binding["native_target"]
            record["entry_offset"] = entry_offset
            record["code_windows"][0]["entry_offset"] = entry_offset
            record["code_windows"][1]["entry_offset"] = cold_offset
            code = bytearray.fromhex(record["code_windows"][0]["prefix_hex"])
            struct.pack_into("<I", code, 72, instruction)
            record["code_windows"][0]["prefix_hex"] = code.hex()
            with self.assertRaisesRegex(ValueError, "direct edge"):
                validate_native_target(binding)

    def test_code_windows_remain_identity_only_with_trusted_bridge_and_valid_import(self):
        fixture = self_fixture()
        with tempfile.TemporaryDirectory() as temporary:
            bridge = Path(temporary) / "bridge.so"
            bridge.write_bytes(b"synthetic first-party window identity only")
            proof = fixture.proof()
            proof["modules"][0]["bindings"] = [window_binding()]
            proof["native_bridge"] = {
                "path": str(bridge.resolve()),
                "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                "verification": "observed-before-and-after-process",
            }
            result = fixture.metadata(proof).resolve(0x1500)
            self.assertEqual(result["reason"], "native-target-identity-untrusted")
            result = fixture.metadata(proof, bridge).resolve(0x1500)
            self.assertEqual(result["reason"], "native-target-identity-only-no-effect-contract")
            self.assertIsNone(result["target"])
            self.assertNotIn("effect", result)
            self.assertFalse(result["native_target"]["semantic_effects_verified"])
            self.assertFalse(result["native_target"]["complete_control_flow_verified"])
            for field in ("execution_claim", "semantic_effects_verified", "complete_control_flow_verified"):
                changed = copy.deepcopy(proof)
                changed["modules"][0]["bindings"][0]["native_target"][field] = True
                with self.subTest(field=field), self.assertRaises(ValueError):
                    fixture.metadata(changed, bridge)

    def test_identity_digest_is_derived_without_an_effect_or_execution_grant(self):
        binding = self_binding()
        result = validate_native_target(binding)
        self.assertEqual(result["mapped_prefix_sha256"], hashlib.sha256(
            bytes.fromhex(binding["native_target"]["prefix_hex"])).hexdigest())
        self.assertNotIn("mapped_prefix_sha256", binding["native_target"])
        self.assertIs(result["execution_claim"], False)
        self.assertIs(result["semantic_effects_verified"], False)
        self.assertIsNone(validate_native_target({"abi": "fixed/no-errno"}))
        binding["native_target"] = result
        self.assertEqual(validate_native_target(binding), result)
        binding["version"] = ""
        self.assertEqual(validate_native_target(binding), result)
        binding["native_target"]["mapped_prefix_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "digest"):
            validate_native_target(binding)

    def test_identity_requires_exact_binding_and_zero_addend(self):
        for field, value in (
            ("symbol", "other"), ("version", "OTHER"), ("provider", "other.so"),
            ("kind", "object"), ("target", 0), ("provider_elf_sha256", "a" * 64),
            ("nonreturning", True), ("nonreturning", 0), ("addend", True),
            ("addend", 4), ("relocation_type", True), ("relocation_type", 257),
            ("abi", "fixed/no-errno"),
        ):
            binding = self_binding()
            binding[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_native_target(binding)
        for value in (None, {}, [], {**self_binding()["native_target"], "extra": 1}):
            binding = self_binding()
            binding["native_target"] = value
            with self.assertRaisesRegex(ValueError, "schema"):
                validate_native_target(binding)
        binding = self_binding()
        del binding["native_target"]
        with self.assertRaisesRegex(ValueError, "schema"):
            validate_native_target(binding)

    def test_identity_types_bounds_paths_and_claims_are_strict(self):
        for field, value in (
            ("profile", True), ("profile", 2), ("verification", "assumed"),
            ("image", "/tmp/libsystem_pthread.dylib"),
            ("image", "/usr/lib/system/../system/libsystem_pthread.dylib"),
            ("symbol", "_pthread_self"), ("entry_offset", False),
            ("entry_offset", -4), ("entry_offset", 3), ("entry_offset", 1 << 64),
            ("entry_offset", (1 << 64) - 4), ("image_uuid", "0" * 32),
            ("image_uuid", "A" * 32), ("image_uuid", "a" * 31),
            ("image_uuid", 1), ("prefix_hex", "0" * 126),
            ("prefix_hex", "A" * 128), ("prefix_hex", "z" * 128),
            ("execution_claim", 0), ("execution_claim", True),
            ("semantic_effects_verified", 0), ("semantic_effects_verified", True),
        ):
            binding = self_binding()
            binding["native_target"][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_native_target(binding)

    def test_metadata_requires_independent_import_identity_and_never_grants_effect(self):
        fixture = self_fixture()
        with tempfile.TemporaryDirectory() as temporary:
            bridge = Path(temporary) / "bridge.so"
            bridge.write_bytes(b"synthetic first-party identity only")
            proof = fixture.proof()
            proof["modules"][0]["bindings"] = [self_binding()]
            proof["native_bridge"] = {
                "path": str(bridge.resolve()),
                "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                "verification": "observed-before-and-after-process",
            }
            result = fixture.metadata(proof).resolve(0x1500)
            self.assertEqual(result["reason"], "native-target-identity-untrusted")
            result = fixture.metadata(proof, bridge).resolve(0x1500)
            self.assertEqual(result["reason"], "native-target-identity-only-no-effect-contract")
            self.assertIsNone(result["target"])
            self.assertNotIn("effect", result)
            self.assertFalse(result["native_target"]["semantic_effects_verified"])
            for field, value in (("prefix_hex", "A" * 128), ("execution_claim", True)):
                changed = copy.deepcopy(proof)
                changed["modules"][0]["bindings"][0]["native_target"][field] = value
                with self.assertRaises(ValueError):
                    fixture.metadata(changed, bridge)
            # The valid report cannot override a changed source import version.
            fixture.data[0x1300 + next(v for t, v in fixture.tags if t == 10) - 5] = ord("X")
            changed = copy.deepcopy(proof)
            changed["modules"][0]["elf_sha256"] = hashlib.sha256(fixture.data).hexdigest()
            with self.assertRaises(ValueError):
                fixture.metadata(changed, bridge).resolve(0x1500)


if __name__ == "__main__":
    unittest.main()
