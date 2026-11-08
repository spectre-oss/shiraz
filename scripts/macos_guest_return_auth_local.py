#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Execute guest-only PAC fixtures with immutable pre-existing host probes.

This script never builds a Mach-O executable or signs any artifact. The guest
ELFs are first-party instruction fixtures, not Android/ART/application proof.
"""

import argparse
import hashlib
import json
import os
import plistlib
import struct
import subprocess
from pathlib import Path

from audit_guest_tpidr import ElfBindingMetadata
from guest_return_auth_profile import host_return_auth_profile
from macos_guest_differential_smoke import validate_collector_signature

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_wrappers(path, key):
    data = path.read_bytes()
    metadata = ElfBindingMetadata(data, path.name)
    opcodes = (0xD503233F, 0xD50323BF) if key == "a" else (0xD503237F, 0xD50323FF)
    found = {}
    for index in range(metadata.symbol_count):
        name, info, _, section, value, size = struct.unpack(
            "<IBBHQQ", metadata.read(metadata.tags[6] + index * 24, 24))
        if not section or info & 15 != 2:
            continue
        name = metadata.string(name)
        if name not in {"atomic_effects_capture", "atomic_effects_cas_capture"}:
            continue
        if name in found or size < 12:
            raise ValueError("ambiguous/truncated authenticated wrapper")
        offset = metadata.offset(value, size, executable=True)
        first = struct.unpack_from("<I", data, offset)[0]
        last = struct.unpack_from("<II", data, offset + size - 8)
        if first != opcodes[0] or last != (opcodes[1], 0xD65F03C0):
            raise ValueError("guest wrapper lacks exact matching PAC/AUT/RET body boundaries")
        found[name] = {"entry": hex(value), "size": size, "sign_opcode": hex(first),
                       "authenticate_opcode": hex(last[0])}
    if len(found) != 2:
        raise ValueError("both actual guest capture wrappers required")
    return found


def inspect_existing_hardened_signature(path, env):
    # Verification/display only. There is deliberately no --sign operation.
    subprocess.run(["/usr/bin/codesign", "--verify", "--strict", str(path)],
                   env=env, check=True, capture_output=True, timeout=30)
    display = subprocess.run(["/usr/bin/codesign", "--display", "--verbose=4", str(path)],
                             env=env, check=True, capture_output=True, text=True, timeout=30)
    shown = subprocess.run(["/usr/bin/codesign", "--display", "--entitlements", "-", "--xml", str(path)],
                           env=env, check=True, capture_output=True, timeout=30)
    entitlements = plistlib.loads(shown.stdout)
    flags = validate_collector_signature(display.stdout + display.stderr, entitlements)
    return {"flags": flags, "entitlements": entitlements, "operation": "read-only verify/display",
            "policy_sha256": digest(ROOT / "scripts/macos_guest_differential_smoke.py")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    profile = host_return_auth_profile()
    if not profile or profile["feature_pauth"] != 1 or profile["feature_pauth_lr"] != 0:
        raise ValueError("actual observed legacy SP-only PAuth host required")
    probes = {"normal": ROOT / "build/macos/m5/atomic-effects-probe",
              "prebuilt-hardened": ROOT / "build/macos/m5/atomic-effects-hardened",
              "sanitized": ROOT / "build/macos/m5/atomic-effects-sanitized"}
    for probe in probes.values():
        if not probe.is_file() or not os.access(probe, os.X_OK):
            raise ValueError("pre-existing probe required; this target will not build or sign one: " + str(probe))
    probe_hashes = {name: digest(path) for name, path in probes.items()}
    results = []
    env = {name: value for name, value in os.environ.items() if not name.startswith("DYLD_")}
    env["ASAN_OPTIONS"] = "detect_leaks=0"
    hardened_signature = inspect_existing_hardened_signature(probes["prebuilt-hardened"], env)
    for key in ("a", "b"):
        directory = (args.directory / key).resolve(strict=True)
        elf = directory / "libatomic-effects.so"
        wrappers, elf_hash = verify_wrappers(elf, key), digest(elf)
        for mode, probe in probes.items():
            if digest(probe) != probe_hashes[mode]:
                raise ValueError("host probe changed before execution")
            run = subprocess.run([str(probe), str(directory)], env=env,
                                 capture_output=True, text=True, timeout=60, check=False)
            log = args.output.with_name(args.output.stem + "-" + key + "-" + mode + ".log")
            log.write_text(run.stdout + run.stderr)
            if digest(probe) != probe_hashes[mode] or digest(elf) != elf_hash:
                raise ValueError("probe/guest artifact changed during execution")
            markers = [line for line in run.stdout.splitlines() if line.startswith("M5_ATOMIC_EFFECTS_PASS ")]
            if run.returncode or len(markers) != 1 or "cases=10" not in markers[0] or "cas_cases=36" not in markers[0]:
                raise ValueError("actual authenticated guest wrapper execution failed: " + str(log))
            results.append({"key": key, "mode": mode, "guest_elf_sha256": elf_hash,
                            "probe_sha256": probe_hashes[mode], "wrappers": wrappers,
                            "returncode": run.returncode, "marker": markers[0], "log": str(log)})
    report = {"schema": 1, "scope": "first-party guest return-instruction fixture; not ART/APK or cryptographic security proof",
              "profile": profile, "new_host_build": False, "signing_performed": False,
              "hardened_art_verified": False, "hardware_execution": True,
              "hardened_signature": hardened_signature,
              "assembly_sha256": digest(ROOT / "tests/darwin/guest/atomic_effects.S"),
              "harness_sha256": digest(Path(__file__)), "runs": results}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print("M5_RETURN_AUTH_LOCAL_PASS keys=2 runs=6 cases_per_run=46 new_host_build=0 signing_performed=0 art_execution=0 app_execution=0")


if __name__ == "__main__":
    main()
