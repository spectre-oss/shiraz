# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic metadata checks only; these are not guest ELF execution evidence."""

import copy
import hashlib
import struct
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_cross_binding import MAX_SYMBOLS, VERSYM, verify_guest_target


class Metadata:
    """Small first-party bounded parser-interface double, never an ELF loader."""

    def __init__(self, name, symbols):
        self.name = name
        self.data = name.encode()
        self.tags = {6: 0x100}
        self.symbol_count = len(symbols)
        self.memory = {}
        self.strings = {0: "", 1: "function", 2: "other"}
        self.sections = [(0,) * 10, (0, 1, 4, 0x400, 0, 0x100, 0, 0, 4, 0)]
        self.versions = {}
        self.requirements = {}
        self.relocations = {}
        self.proof = {}
        self.initial = {
            "target": None,
            "slot": "0x80",
            "symbol": "function",
            "relocation_type": 1026,
            "reason": "external-or-preempted-namespace-binding",
        }
        for index, symbol in enumerate(symbols):
            self.memory[0x100 + index * 24] = struct.pack("<IBBHQQ", *symbol)

    def read(self, address, size):
        result = self.memory.get(address)
        if result is None or len(result) != size:
            raise ValueError("synthetic bounded read failed")
        return result

    def string(self, index):
        return self.strings[index]

    def offset(self, address, size, executable=False):
        if not executable or not 0x400 <= address or address + size > 0x500:
            raise ValueError("synthetic executable LOAD bound")
        return address

    def defined_version(self, index):
        return self.versions.get(index)

    def needed_version(self, index):
        return self.requirements.get(index)

    def resolve(self, slot):
        if slot not in self.relocations:
            raise ValueError("synthetic missing relocation")
        proof = self.proof[slot]
        if proof["symbol"] != self.initial["symbol"]:
            raise ValueError("binding evidence symbol/version mismatch")
        return dict(self.initial)

    def versym(self, values):
        self.tags[VERSYM] = 0x300
        for index, value in enumerate(values):
            self.memory[0x300 + index * 2] = struct.pack("<H", value)


class GuestCrossBindingTests(unittest.TestCase):
    def test_ambiguity_stops_before_scanning_all_duplicate_exports(self):
        self.provider = Metadata("libprovider.so", [(0, 0, 0, 0, 0, 0)] +
                                 [(1, 0x12, 0, 1, 0x400, 16)] * 100)
        with patch.object(self.provider, "read", wraps=self.provider.read) as reads:
            result = verify_guest_target(self.importer, 0x80, self.provider)
        self.assertIsNone(result["target"])
        self.assertEqual(result["reason"], "cross-binding-definition-missing-or-ambiguous")
        self.assertLessEqual(reads.call_count, 3)

    def test_provider_hash_work_is_cached_only_for_identical_immutable_bytes(self):
        sha256 = hashlib.sha256
        with patch("guest_cross_binding.hashlib.sha256", wraps=sha256) as digest:
            verify_guest_target(self.importer, 0x80, self.provider)
            verify_guest_target(self.importer, 0x80, self.provider)
            self.assertEqual(digest.call_count, 1)
            self.provider.data += b"changed"
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                verify_guest_target(self.importer, 0x80, self.provider)
            self.assertEqual(digest.call_count, 2)

    def setUp(self):
        null = (0, 0, 0, 0, 0, 0)
        self.importer = Metadata("libcaller.so", [null, (1, 0x12, 0, 0, 0, 0)])
        self.provider = Metadata("libprovider.so", [null, (1, 0x12, 0, 1, 0x400, 16)])
        self.importer.relocations[0x80] = (1, 0, 1026)
        self.binding = {
            "symbol": "function", "version": "", "provider": self.provider.name,
            "target": 0x400, "provider_elf_sha256": hashlib.sha256(self.provider.data).hexdigest(),
            "kind": "function", "abi": "guest-AAPCS64", "addend": 0,
            "relocation_type": 1026,
        }
        self.importer.proof[0x80] = self.binding

    def verify(self):
        return verify_guest_target(self.importer, 0x80, self.provider)

    def test_exact_exported_definition_yields_only_relative_edge(self):
        result = self.verify()
        self.assertEqual(result["target"], 0x400)
        self.assertEqual(result["provider"], self.provider.name)
        self.assertEqual(result["reason"], "cross-module-production-binding")
        self.assertNotIn("effect", result)

    def test_existing_refusal_or_same_module_result_is_not_reinterpreted(self):
        for reason in ("same-module-production-binding", "namespace-binding-proof-missing",
                       "native-provider-contract-untrusted"):
            self.importer.initial["reason"] = reason
            self.assertEqual(self.verify(), self.importer.initial)

    def test_provider_name_and_byte_hash_must_match_actual_snapshot(self):
        self.provider.name = "another.so"
        with self.assertRaisesRegex(ValueError, "provider name"):
            self.verify()
        self.provider.name = "libprovider.so"
        self.provider.data += b"changed"
        with self.assertRaisesRegex(ValueError, "ELF hash"):
            self.verify()

    def test_native_addresses_and_nonfunction_bindings_cannot_be_guest_edges(self):
        for field, value in (("target", None), ("provider_elf_sha256", None),
                             ("kind", "object"), ("abi", "fixed/no-errno"),
                             ("abi", "guest-local-AAPCS64"), ("nonreturning", True)):
            with self.subTest(field=field, value=value):
                saved = copy.deepcopy(self.binding)
                self.binding[field] = value
                self.assertIsNone(self.verify()["target"])
                self.binding.clear()
                self.binding.update(saved)

    def test_relocation_and_addend_evidence_is_exact_and_type_strict(self):
        for field, value in (("relocation_type", 1025), ("relocation_type", True),
                             ("addend", 1), ("addend", False)):
            with self.subTest(field=field, value=value):
                previous = self.binding[field]
                self.binding[field] = value
                with self.assertRaisesRegex(ValueError, "relocation evidence"):
                    self.verify()
                self.binding[field] = previous
        for addend, kind in ((4, 1026), (0, 257)):
            self.importer.relocations[0x80] = (1, addend, kind)
            self.binding.update(addend=addend, relocation_type=kind)
            self.assertIsNone(self.verify()["target"])

    def test_source_symbol_type_binding_and_visibility_are_not_guessed(self):
        for info, visibility in ((0x11, 0), (0x02, 0), (0x12, 2), (0x12, 3)):
            self.importer.memory[0x118] = struct.pack("<IBBHQQ", 1, info, visibility, 0, 0, 0)
            self.assertIsNone(self.verify()["target"])

    def test_target_must_equal_export_start_not_an_interior_or_arbitrary_address(self):
        self.binding["target"] = 0x404
        with self.assertRaisesRegex(ValueError, "exported definition"):
            self.verify()
        for invalid in (True, -1, 1 << 64, 0x401):
            self.binding["target"] = invalid
            with self.assertRaises(ValueError):
                self.verify()

    def test_export_visibility_kind_and_executable_section_are_checked(self):
        for info, visibility, section in ((0x11, 0, 1), (0x02, 0, 1), (0x12, 2, 1),
                                           (0x12, 0, 0), (0x1A, 0, 1)):
            self.provider.memory[0x118] = struct.pack("<IBBHQQ", 1, info, visibility, section, 0x400, 16)
            self.assertIsNone(self.verify()["target"])
        self.provider.memory[0x118] = struct.pack("<IBBHQQ", 1, 0x12, 3, 1, 0x400, 16)
        self.assertEqual(self.verify()["target"], 0x400)
        self.provider.sections[1] = (0, 1, 0, 0x400, 0, 0x100, 0, 0, 4, 0)
        self.assertIsNone(self.verify()["target"])

    def test_ambiguous_exported_definitions_are_not_selected_by_report_target(self):
        self.provider.symbol_count = 3
        self.provider.memory[0x130] = struct.pack("<IBBHQQ", 1, 0x22, 0, 1, 0x404, 16)
        self.assertIsNone(self.verify()["target"])

    def test_default_version_rejects_hidden_and_local_but_accepts_default_definition(self):
        self.provider.versions[2] = "VERSION_2"
        for version, expected in ((0, None), (0x8002, None), (2, 0x400), (1, 0x400)):
            self.provider.versym((0, version))
            self.assertEqual(self.verify()["target"], expected)

    def test_named_version_requires_exact_definition_and_required_provider(self):
        self.binding["version"] = "VERSION_2"
        self.importer.versym((0, 2))
        self.importer.requirements[2] = ("VERSION_2", self.provider.name)
        self.provider.versions[2] = "VERSION_2"
        self.provider.versym((0, 0x8002))
        self.assertEqual(self.verify()["target"], 0x400)
        self.provider.versions[2] = "WRONG"
        self.assertIsNone(self.verify()["target"])
        self.provider.versions[2] = "VERSION_2"
        self.importer.requirements[2] = ("VERSION_2", "another.so")
        self.assertIsNone(self.verify()["target"])
        self.importer.requirements[2] = ("VERSION_2", self.provider.name)
        self.provider.tags.pop(VERSYM)
        self.assertIsNone(self.verify()["target"])

    def test_provider_count_section_and_file_backing_bounds_fail_closed(self):
        for count in (0, True, MAX_SYMBOLS + 1):
            self.provider.symbol_count = count
            with self.assertRaisesRegex(ValueError, "symbol count"):
                self.verify()
        self.provider.symbol_count = 2
        self.provider.memory[0x118] = struct.pack("<IBBHQQ", 1, 0x12, 0, 8, 0x400, 16)
        with self.assertRaisesRegex(ValueError, "section index"):
            self.verify()
        self.provider.memory[0x118] = struct.pack("<IBBHQQ", 1, 0x12, 0, 1, 0x4FC, 16)
        self.provider.offset = lambda *_args, **_kwargs: (_ for _ in ()).throw(ValueError("not file-backed"))
        with self.assertRaisesRegex(ValueError, "file-backed"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
