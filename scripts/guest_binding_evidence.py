# SPDX-License-Identifier: GPL-3.0-only
"""Attach verified ELF identities to bounded production Loader binding reports."""

import hashlib
import json
from pathlib import Path

MAX_REPORT = 64 * 1024 * 1024
MAX_BINDINGS = 200000
GUEST_ABIS = {"guest-AAPCS64", "guest-local-AAPCS64"}


def augment_binding_report(
    raw_path,
    output,
    hashes,
    library_directory,
    expected_names,
    *,
    native_bridge=None,
    bridge_sha256=None,
):
    """Reject stale/foreign paths and preserve unknown/native-provider bindings."""
    raw_path, output = Path(raw_path), Path(output)
    if not 0 < raw_path.stat().st_size <= MAX_REPORT:
        raise ValueError("binding report size limit")
    raw = raw_path.read_bytes()
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
        if not 0 < actual.stat().st_size <= 256 * 1024 * 1024:
            raise ValueError("binding report ELF size limit")
        if hashlib.sha256(actual.read_bytes()).hexdigest() != hashes[name]:
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
        actual_bridge_hash = hashlib.sha256(native_bridge.read_bytes()).hexdigest()
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
