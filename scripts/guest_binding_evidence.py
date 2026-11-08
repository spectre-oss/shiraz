# SPDX-License-Identifier: GPL-3.0-only
"""Attach verified ELF identities to bounded production Loader binding reports."""

import hashlib
import json
import struct
from pathlib import Path

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


def verify_relocation_inventory(metadata, bindings):
    """Require exact named GLOB_DAT/JUMP_SLOT coverage, independently of JSON."""
    expected = {}
    for slot, (index, addend, kind) in metadata.relocations.items():
        if not index:  # The native evidence format excludes symbol zero.
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
):
    """Reject stale/foreign paths and preserve unknown/native-provider bindings."""
    raw_path, output = Path(raw_path), Path(output)
    raw = bounded_read(raw_path, MAX_REPORT)
    report = json.loads(raw)
    if (
        report.get("schema") != 1
        or report.get("namespace") != "nativebridge"
        or report.get("snapshot") != "post-link-before-open"
        or not isinstance(report.get("modules"), list)
        or not 1 <= len(report["modules"]) <= 128
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
    for module in report["modules"]:
        bindings = module.get("bindings")
        if not isinstance(bindings, list):
            raise TypeError("binding report relocation list required")
        total += len(bindings)
        if total > MAX_BINDINGS:
            raise ValueError("binding report relocation limit")
        seen = set()
        for binding in bindings:
            relocation, target = binding.get("relocation"), binding.get("target")
            if (
                type(relocation) is not int
                or not 0 <= relocation < 2**64
                or relocation in seen
                or (
                    target is not None
                    and (type(target) is not int or not 0 <= target < 2**64)
                )
                or binding.get("relocation_type") not in (1025, 1026)
                or binding.get("kind") not in ("function", "object", "unknown")
                or any(
                    not isinstance(binding.get(key), str) or len(binding[key]) > 4096
                    for key in ("symbol", "version", "provider", "abi")
                )
            ):
                raise ValueError("malformed or duplicate binding report relocation")
            if type(binding.get("nonreturning", False)) is not bool:
                raise ValueError("invalid nonreturning binding contract")
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
        if verify_inventory:
            from audit_guest_tpidr import ElfBindingMetadata

            metadata = ElfBindingMetadata(bounded_read(library_directory / module["name"], 256 << 20), module["name"])
            if metadata.elf_sha256 != hashes[module["name"]]:
                raise ValueError("binding inventory ELF changed since APK verification")
            verify_relocation_inventory(metadata, bindings)
    if verify_inventory:
        report["relocation_inventory_verification"] = "exact-named-GLOB_DAT-JUMP_SLOT-ELF-metadata"
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
    }
