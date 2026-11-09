# SPDX-License-Identifier: GPL-3.0-only
"""Attach verified ELF identities to bounded production Loader binding reports."""

import hashlib
import json
import struct
from pathlib import Path

from guest_allocator_contract import managed_malloc_binding
from guest_cached_self_record import validate_cached_self_binding
from guest_memory_contract import byte_memory_binding
from guest_native_target import validate_native_target

MAX_REPORT = 64 * 1024 * 1024
MAX_BINDINGS = 200000
GUEST_ABIS = {"guest-AAPCS64", "guest-local-AAPCS64"}


def bounded_read(path, limit):
    size = path.stat().st_size
    if not 0 < size <= limit:
        raise ValueError("binding evidence byte bound")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) != size:
        raise ValueError("binding evidence changed size during bounded read")
    return data


def verify_relocation_inventory(metadata, bindings, *, absolute=False):
    """Require exact named GOT/PLT or ABS64 tuples, independently of JSON."""
    expected = {}
    source = ({slot: row for slot, row in metadata.all_relocations.items() if row[2] == 257}
              if absolute else metadata.relocations)
    for slot, (index, addend, kind) in source.items():
        if not absolute and not index:  # Legacy named GOT/PLT excludes symbol zero.
            continue
        name, info, _, section, _, _ = struct.unpack(
            "<IBBHQQ", metadata.read(metadata.tags[6] + index * 24, 24))
        version = ""
        if 0x6FFFFFF0 in metadata.tags:
            number = struct.unpack("<H", metadata.read(metadata.tags[0x6FFFFFF0] + index * 2, 2))[0] & 0x7FFF
            if number > 1:
                if not 0 < section < 0xFF00:
                    requirement = metadata.needed_version(number)
                    version = requirement[0] if requirement else None
                else:
                    version = metadata.defined_version(number)
                if version is None:
                    raise ValueError("binding inventory symbol version unavailable")
        expected[slot] = (metadata.string(name), version, kind, addend, info >> 4, section == 0)
    actual = {binding["relocation"]: binding for binding in bindings}
    if len(actual) != len(bindings) or set(actual) != set(expected):
        raise ValueError("binding report lacks exact ELF relocation inventory")
    for slot, (name, version, kind, addend, strength, undefined) in expected.items():
        binding = actual[slot]
        if absolute and (type(binding.get("symbol_index")) is not int or
                         binding["symbol_index"] != source[slot][0]):
            raise ValueError("absolute binding symbol index disagrees with ELF metadata")
        if (binding["symbol"], binding["version"], binding["relocation_type"], binding.get("addend")) != (
            name, version, kind, addend
        ) or type(binding.get("addend")) is not int:
            raise ValueError("binding report relocation disagrees with ELF metadata")
        if binding["abi"] in {"", "unrecorded"}:
            raise ValueError("binding report contains unrecorded adapter")
        if not binding["provider"] and not (
            undefined and strength == 2 and binding["abi"] == "unresolved-weak-null"
            and binding["target"] is None
        ):
            raise ValueError("binding report lacks selected provider")
    return len(expected)


def verify_complete_relocations(metadata, inventory):
    """Check every applied tuple; producer read-back is a historical observation."""
    if (not isinstance(inventory, dict) or type(inventory.get("profile")) is not int or
            inventory["profile"] != 1 or
            inventory.get("verification") != "read-back-after-relocate-before-seal" or
            type(inventory.get("verified_count")) is not int or
            not isinstance(inventory.get("records"), list)):
        raise ValueError("complete relocation verification profile required")
    rows = inventory["records"]
    if len(rows) > MAX_BINDINGS or inventory["verified_count"] != len(rows):
        raise ValueError("complete relocation verified count/bound")
    actual = {}
    for row in rows:
        if (not isinstance(row, dict) or set(row) != {"relocation", "relocation_type", "symbol_index", "addend"} or
                any(type(row.get(key)) is not int for key in row) or
                not 0 <= row["relocation"] < 1 << 64 or
                not -(1 << 63) <= row["addend"] < 1 << 63 or
                not 0 <= row["symbol_index"] < 1 << 32 or
                row["relocation_type"] not in (257, 1025, 1026, 1027) or
                row["relocation"] in actual):
            raise ValueError("invalid complete relocation tuple")
        actual[row["relocation"]] = (row["symbol_index"], row["addend"], row["relocation_type"])
    if actual != metadata.all_relocations:
        raise ValueError("complete relocation inventory disagrees with ELF")
    return len(rows)


def _validate_binding_rows(bindings, hashes, names, bridge_identity, *, absolute=False):
    seen = set()
    for binding in bindings:
        if not isinstance(binding, dict):
            raise TypeError("binding report row must be an object")
        relocation, target = binding.get("relocation"), binding.get("target")
        if (
            type(relocation) is not int
            or not 0 <= relocation < 2**64
            or relocation in seen
            or (
                target is not None
                and (type(target) is not int or not 0 <= target < 2**64)
            )
            or type(binding.get("relocation_type")) is not int
            or binding.get("relocation_type") not in ((257,) if absolute else (1025, 1026))
            or binding.get("kind") not in ("function", "object", "unknown")
            or any(
                not isinstance(binding.get(key), str) or len(binding[key]) > 4096
                for key in ("symbol", "version", "provider", "abi")
            )
        ):
            raise ValueError("malformed or duplicate binding report relocation")
        if type(binding.get("nonreturning", False)) is not bool:
            raise ValueError("invalid nonreturning binding contract")
        if absolute:
            if (binding.get("usage") != "absolute-address" or
                    type(binding.get("symbol_index")) is not int or
                    not 0 <= binding["symbol_index"] < 1 << 32 or
                    type(binding.get("current_slot_matches_link")) is not bool or
                    "native_target" in binding or "cached_self" in binding):
                raise ValueError("absolute binding address snapshot required")
        else:
            if byte_memory_binding(binding) and bridge_identity is None:
                raise ValueError("byte memory contract requires bridge identity")
            native_target = validate_native_target(binding)
            validate_cached_self_binding(binding)
            if "cached_self" in binding and bridge_identity is None:
                raise ValueError("cached pthread self record requires bridge identity")
            if managed_malloc_binding(binding) and bridge_identity is None:
                raise ValueError("managed malloc contract requires bridge identity")
            if native_target is not None:
                if bridge_identity is None:
                    raise ValueError("native target identity requires bridge identity")
                binding["native_target"] = native_target
            if binding.get("nonreturning", False) and (
                bridge_identity is None
                or binding["kind"] != "function"
                or binding["abi"] != "fixed/nonreturning-noargs"
                or target is not None
                or type(binding.get("addend")) is not int
                or binding["addend"] != 0
            ):
                raise ValueError(
                    "nonreturning contract requires exact native entry and bridge identity"
                )
        seen.add(relocation)
        provider = binding["provider"]
        provider_hash = hashes.get(provider) if provider in names else None
        if target is not None and (
            provider_hash is None or binding["abi"] not in GUEST_ABIS
        ):
            raise ValueError(
                "native/unknown binding must not expose a guest target"
            )
        binding["provider_elf_sha256"] = provider_hash


def augment_binding_report(
    raw_path,
    output,
    hashes,
    library_directory,
    expected_names,
    *,
    native_bridge=None,
    bridge_sha256=None,
    verify_inventory=False,
    verify_all_relocations=False,
):
    """Reject stale/foreign paths and preserve unknown/native-provider bindings."""
    raw_path, output = Path(raw_path), Path(output)
    raw = bounded_read(raw_path, MAX_REPORT)
    report = json.loads(raw)
    if (
        not isinstance(report, dict)
        or report.get("schema") != 1
        or report.get("namespace") != "nativebridge"
        or report.get("snapshot") != "post-link-before-open"
        or not isinstance(report.get("modules"), list)
        or not 1 <= len(report["modules"]) <= 128
        or not all(isinstance(module, dict) for module in report["modules"])
    ):
        raise ValueError("requires actual NativeBridge post-link binding report")
    names = [module.get("name") for module in report["modules"]]
    if len(names) != len(set(names)) or set(names) != set(expected_names):
        raise ValueError("binding report library set disagrees with real JNI gate")
    library_directory = Path(library_directory).resolve(strict=True)
    for module in report["modules"]:
        name = module["name"]
        if Path(name).name != name or name not in hashes:
            raise ValueError("unrecognized binding report ELF identity")
        actual = Path(module["file"]).resolve(strict=True)
        expected = (library_directory / name).resolve(strict=True)
        if actual != expected or actual.parent != library_directory:
            raise ValueError("binding report ELF file escapes verified library set")
        if hashlib.sha256(bounded_read(actual, 256 << 20)).hexdigest() != hashes[name]:
            raise ValueError("binding report ELF changed since APK verification")
        module["elf_sha256"] = hashes[name]
    bridge_identity = None
    if native_bridge is not None:
        native_bridge = Path(native_bridge).resolve(strict=True)
        if (
            not native_bridge.is_file()
            or not 0 < native_bridge.stat().st_size <= 256 << 20
        ):
            raise ValueError("native bridge identity size bound")
        actual_bridge_hash = hashlib.sha256(bounded_read(native_bridge, 256 << 20)).hexdigest()
        if not isinstance(bridge_sha256, str) or actual_bridge_hash != bridge_sha256:
            raise ValueError("native bridge changed since pre-execution identity")
        bridge_identity = {
            "path": str(native_bridge),
            "sha256": actual_bridge_hash,
            "verification": "observed-before-and-after-process",
        }
    elif bridge_sha256 is not None:
        raise ValueError("native bridge path required for identity")
    total = 0
    total_absolute = 0
    total_relocations = 0
    for module in report["modules"]:
        bindings = module.get("bindings")
        if not isinstance(bindings, list):
            raise TypeError("binding report relocation list required")
        total += len(bindings)
        if total > MAX_BINDINGS:
            raise ValueError("binding report relocation limit")
        _validate_binding_rows(bindings, hashes, names, bridge_identity)
        absolute = module.get("absolute_bindings")
        if verify_all_relocations or absolute is not None:
            if (not isinstance(absolute, list) or len(absolute) > MAX_BINDINGS or
                    module.get("absolute_binding_snapshot") != "read-back-after-relocate-before-seal"):
                raise ValueError("absolute binding inventory/stage required")
            total_absolute += len(absolute)
            if total_absolute > MAX_BINDINGS:
                raise ValueError("absolute binding report relocation limit")
            _validate_binding_rows(absolute, hashes, names, bridge_identity, absolute=True)
        if verify_all_relocations and bridge_identity is None:
            raise ValueError("complete relocation verification requires bridge identity")
        if verify_inventory or verify_all_relocations or "mrs_stack_window" in module:
            from audit_guest_tpidr import ElfBindingMetadata
            from guest_mrs_window import validate_mrs_window_record

            metadata = ElfBindingMetadata(bounded_read(library_directory / module["name"], 256 << 20), module["name"])
            if metadata.elf_sha256 != hashes[module["name"]]:
                raise ValueError("binding inventory ELF changed since APK verification")
            if "mrs_stack_window" in module:
                validate_mrs_window_record(metadata, module["mrs_stack_window"])
            if verify_inventory:
                verify_relocation_inventory(metadata, bindings)
            if verify_all_relocations:
                total_relocations += verify_complete_relocations(metadata, module.get("relocation_inventory"))
                if total_relocations > MAX_BINDINGS:
                    raise ValueError("complete relocation report limit")
                verify_relocation_inventory(metadata, absolute, absolute=True)
    if verify_inventory:
        report["relocation_inventory_verification"] = "exact-named-GLOB_DAT-JUMP_SLOT-ELF-metadata"
    if verify_all_relocations:
        report["complete_relocation_inventory_verification"] = "exact-all-RELA-and-ABS64-bindings-ELF-metadata"
    if bridge_identity is not None:
        report["native_bridge"] = bridge_identity
    report["source_report_sha256"] = hashlib.sha256(raw).hexdigest()
    report["identity_verification"] = (
        "unchanged-apk-members-and-exact-loaded-file-paths"
    )
    encoded = (json.dumps(report, indent=2) + "\n").encode()
    if len(encoded) > MAX_REPORT:
        raise ValueError("augmented binding report size limit")
    pending = output.with_suffix(output.suffix + ".pending")
    pending.write_bytes(encoded)
    pending.replace(output)
    return {
        "path": str(output),
        "sha256": hashlib.sha256(encoded).hexdigest(),
        "bindings": total,
        "absolute_bindings": total_absolute,
        "verified_relocations": total_relocations if verify_all_relocations else None,
    }
