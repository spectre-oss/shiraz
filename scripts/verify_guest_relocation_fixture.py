#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify the executed first-party mutable-pointer fixture, never APK acceptance."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

from audit_guest_tpidr import ElfBindingMetadata
from guest_binding_evidence import (
    MAX_REPORT,
    _validate_binding_rows,
    bounded_read,
    verify_complete_relocations,
    verify_relocation_inventory,
)


def verify(library, report_path):
    library = Path(library).resolve(strict=True)
    data = bounded_read(library, 256 << 20)
    report = json.loads(bounded_read(Path(report_path), MAX_REPORT))
    if (not isinstance(report, dict) or report.get("schema") != 1 or
            report.get("namespace") != "nativebridge" or
            report.get("snapshot") != "post-link-before-open" or
            type(report.get("constructors_executed")) is not int or report["constructors_executed"] != 1 or
            report.get("report_executes_guest_code") is not False or
            not isinstance(report.get("modules"), list) or len(report["modules"]) != 1):
        raise ValueError("executed mutable fixture report profile required")
    module = report["modules"][0]
    if (not isinstance(module, dict) or module.get("name") != "libshiraz_fixture.so" or
            Path(module["file"]).resolve(strict=True) != library or
            module.get("absolute_binding_snapshot") != "read-back-after-relocate-before-seal"):
        raise ValueError("mutable fixture identity/stage mismatch")
    metadata = ElfBindingMetadata(data, library.name)
    hashes = {module["name"]: hashlib.sha256(data).hexdigest()}
    for absolute, key in ((False, "bindings"), (True, "absolute_bindings")):
        if not isinstance(module.get(key), list):
            raise TypeError("fixture binding list required")
        _validate_binding_rows(module[key], hashes, set(hashes), None, absolute=absolute)
        verify_relocation_inventory(metadata, module[key], absolute=absolute)
    count = verify_complete_relocations(metadata, module.get("relocation_inventory"))
    if {row[2] for row in metadata.all_relocations.values()} != {257, 1025, 1026, 1027}:
        raise ValueError("fixture must cover all four relocation kinds")
    symbols = {}
    for index in range(metadata.symbol_count):
        name, _, _, section, address, _ = struct.unpack("<IBBHQQ", metadata.read(metadata.tags[6] + index * 24, 24))
        if section:
            symbols[metadata.string(name)] = address
    slot = symbols["guest_pointer"]
    selected = next(row for row in module["absolute_bindings"] if row["relocation"] == slot)
    if (selected["current_slot_matches_link"] is not False or
            selected["target"] != symbols["guest_state"] or selected["provider"] != module["name"]):
        raise ValueError("historical ABS64 binding lost after writable pointer mutation")
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        count = verify(args.library, args.report)
    except (OSError, ValueError, TypeError, KeyError, StopIteration) as error:
        parser.exit(1, f"M5_RELOCATION_EVIDENCE_FAIL {error}\n")
    print(f"M5_RELOCATION_EVIDENCE_PASS slots={count} kinds=4 mutated_abs_retained=1 fixture_only=1 app_execution=0")


if __name__ == "__main__":
    main()
