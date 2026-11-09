# SPDX-License-Identifier: GPL-3.0-only
"""Independent complete relocation tuple checks; synthetic identities, no execution."""
import copy
import hashlib
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_binding_evidence import (
    augment_binding_report,
    verify_complete_relocations,
    verify_relocation_inventory,
)
from test_guest_tpidr_audit import BindingElfFixture


def fixture():
    value = BindingElfFixture()
    value.tags = [(tag, 96 if tag == 2 else address) for tag, address in value.tags]
    value.dynamic()
    for ordinal, kind in enumerate((1026, 1025, 257, 1027)):
        struct.pack_into("<QQq", value.data, 0x1400 + ordinal * 24, 0x1500 + ordinal * 8,
                         kind | ((0 if kind == 1027 else 1) << 32), 0)
    return value


def inventory(metadata):
    return {"profile": 1, "verification": "read-back-after-relocate-before-seal",
            "verified_count": len(metadata.all_relocations),
            "records": [{"relocation": slot, "relocation_type": kind,
                         "symbol_index": index, "addend": addend}
                        for slot, (index, addend, kind) in metadata.all_relocations.items()]}


def bindings(value):
    first = value.proof()["modules"][0]["bindings"][0]
    first.update(relocation_type=1026, addend=0)
    named = [first, {**first, "relocation": 0x1508, "relocation_type": 1025}]
    absolute = [{**first, "relocation": 0x1510, "relocation_type": 257,
                 "symbol_index": 1, "usage": "absolute-address", "current_slot_matches_link": False}]
    return named, absolute


class CompleteRelocationTests(unittest.TestCase):
    def test_exact_four_kind_inventory_and_absolute_binding(self):
        value = fixture();metadata = value.metadata()
        self.assertEqual(verify_complete_relocations(metadata, inventory(metadata)), 4)
        named, absolute = bindings(value)
        self.assertEqual(verify_relocation_inventory(metadata, named), 2)
        self.assertEqual(verify_relocation_inventory(metadata, absolute, absolute=True), 1)
        # A later writable-data change does not erase historical selected binding.
        self.assertFalse(absolute[0]["current_slot_matches_link"])

    def test_missing_duplicate_foreign_and_changed_tuples_refuse(self):
        metadata = fixture().metadata();original = inventory(metadata)
        mutations = []
        dropped = copy.deepcopy(original);dropped["records"].pop();dropped["verified_count"] -= 1;mutations.append(dropped)
        duplicate = copy.deepcopy(original);duplicate["records"].append(duplicate["records"][0]);duplicate["verified_count"] += 1;mutations.append(duplicate)
        for key, replacement in (("relocation", 0x1600), ("relocation_type", 1025),
                                 ("symbol_index", 2), ("addend", 8), ("addend", False),
                                 ("relocation", True), ("symbol_index", 1.0)):
            changed = copy.deepcopy(original);changed["records"][2][key] = replacement;mutations.append(changed)
        for value in mutations:
            with self.assertRaises(ValueError):
                verify_complete_relocations(metadata, value)

    def test_verified_profile_and_empty_inventory_require_explicit_success(self):
        metadata = fixture().metadata();original = inventory(metadata)
        for key, replacement in (("profile", True), ("profile", 2), ("verification", "post-init"),
                                 ("verified_count", True), ("verified_count", 3)):
            changed = copy.deepcopy(original);changed[key] = replacement
            with self.assertRaises(ValueError):verify_complete_relocations(metadata, changed)
        metadata.all_relocations.clear()
        self.assertEqual(verify_complete_relocations(metadata, inventory(metadata)), 0)
        with self.assertRaises(ValueError):verify_complete_relocations(metadata, None)

    def test_absolute_binding_tuple_cannot_be_omitted_or_changed(self):
        value = fixture();metadata = value.metadata();_, absolute = bindings(value)
        for rows in ([], absolute * 2, [{**absolute[0], "symbol_index": True}],
                     [{**absolute[0], "symbol_index": 0}], [{**absolute[0], "addend": 4}],
                     [{**absolute[0], "abi": "unrecorded"}]):
            with self.assertRaises(ValueError):verify_relocation_inventory(metadata, rows, absolute=True)

    def test_full_gate_requires_all_arrays_stages_identity_and_metadata(self):
        value = fixture();metadata = value.metadata()
        named, absolute = bindings(value)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);library = root / "libfixture.so";library.write_bytes(value.data)
            digest = hashlib.sha256(value.data).hexdigest()
            report = value.proof();module = report["modules"][0]
            module.update(file=str(library), bindings=named, absolute_bindings=absolute,
                          absolute_binding_snapshot="read-back-after-relocate-before-seal",
                          relocation_inventory=inventory(metadata))
            bridge = root / "bridge.so";bridge.write_bytes(b"synthetic identity only")
            bridge_hash = hashlib.sha256(bridge.read_bytes()).hexdigest()
            raw, output = root / "raw.json", root / "verified.json"

            def augment(current=report, identity=True):
                raw.write_text(json.dumps(current))
                return augment_binding_report(raw, output, {library.name: digest}, root,
                    {library.name}, verify_inventory=True, verify_all_relocations=True,
                    **({"native_bridge": bridge, "bridge_sha256": bridge_hash} if identity else {}))

            result = augment()
            self.assertEqual((result["bindings"], result["absolute_bindings"], result["verified_relocations"]), (2, 1, 4))
            with self.assertRaisesRegex(ValueError, "bridge identity"):augment(identity=False)
            for field in ("absolute_bindings", "absolute_binding_snapshot", "relocation_inventory"):
                changed = copy.deepcopy(report);del changed["modules"][0][field]
                with self.assertRaises(ValueError):augment(changed)
            changed = copy.deepcopy(report);changed["modules"][0]["absolute_bindings"] = []
            with self.assertRaises(ValueError):augment(changed)
            changed = copy.deepcopy(report);changed["modules"][0]["absolute_bindings"][0]["current_slot_matches_link"] = 0
            with self.assertRaisesRegex(ValueError, "address snapshot"):augment(changed)
            changed = copy.deepcopy(report);changed["modules"][0]["absolute_bindings"][0]["native_target"] = {}
            with self.assertRaisesRegex(ValueError, "address snapshot"):augment(changed)
            for malformed in (None, 7, "row"):
                changed = copy.deepcopy(report);changed["modules"][0]["absolute_bindings"] = [malformed]
                with self.assertRaisesRegex(TypeError, "row must be an object"):augment(changed)

