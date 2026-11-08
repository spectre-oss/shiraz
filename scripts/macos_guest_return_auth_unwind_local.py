#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Guest-only PAC exception fixtures; reuse host probes without building/signing."""

import argparse
import json
import os
import resource
import struct
import subprocess
import zipfile
from pathlib import Path

import macos_guest_return_auth_local as local
from audit_guest_tpidr import ElfBindingMetadata
from guest_return_auth_profile import host_return_auth_profile

ROOT = Path(__file__).resolve().parents[1]
NAMES = {"guest_exception_nested", "guest_exception_rethrow",
         "guest_exception_double", "guest_exception_catch_all"}
MARKER = ("M5_CXX_EXCEPTION_PASS cases=4 real_abi_bindings=8 nested_frames=3 "
          "raii_cleanup=1 rethrow=1 catch_selection=1 catch_all=1 "
          "host_exception_crossing=0 app_execution=0")


def no_child_core():
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def authenticated_entries(path, key):
    data = local.bounded_bytes(path)
    elf = ElfBindingMetadata(data, path.name)
    sign, auth = (0xD503233F, 0xD50323BF) if key == "a" else (0xD503237F, 0xD50323FF)
    found = {}
    for index in range(elf.symbol_count):
        name, info, _, section, address, size = struct.unpack(
            "<IBBHQQ", elf.read(elf.tags[6] + index * 24, 24))
        name = elf.string(name)
        if name not in NAMES or not section or info & 15 != 2:
            continue
        if name in found or size < 12 or size % 4:
            raise ValueError("ambiguous/truncated PAC exception entry")
        offset = elf.offset(address, size, executable=True)
        words = struct.unpack_from("<" + "I" * (size // 4), data, offset)
        returns = [i for i, word in enumerate(words) if word == 0xD65F03C0]
        if words[0] != sign or not returns or any(i == 0 or words[i - 1] != auth for i in returns):
            raise ValueError("exception entry lacks matching compiler PAC/AUT return paths")
        found[name] = {"entry": hex(address), "size": size, "returns": len(returns),
                       "sign_opcode": hex(sign), "authenticate_opcode": hex(auth)}
    if set(found) != NAMES:
        raise ValueError("all four authenticated exception test entries required")
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    os.chdir(ROOT)
    work = ROOT / "build/macos/m5/return-auth-unwind"
    work.mkdir(parents=True, exist_ok=True)
    (work / "result.json").unlink(missing_ok=True)
    profile = host_return_auth_profile()
    if not profile or profile["feature_pauth"] != 1 or profile["feature_pauth_lr"] != 0:
        raise ValueError("requires observed legacy PAuth host profile")
    source = ROOT / "tests/darwin/guest/deps/cxx_exception.cpp"
    library_dir = ROOT / "build/macos/m5/cxx-exception/full/unchanged-apk-libs"
    library = library_dir / "libc++_shared.so"
    with zipfile.ZipFile(args.apk) as apk:
        if apk.namelist().count("lib/arm64-v8a/libc++_shared.so") != 1:
            raise ValueError("unique APK libc++ entry required")
        entry = apk.getinfo("lib/arm64-v8a/libc++_shared.so")
        if entry.file_size > 64 << 20 or apk.read(entry) != local.bounded_bytes(library):
            raise ValueError("pre-existing libc++ must equal unchanged APK bytes")
    probes = {"normal": ROOT / "build/macos/m5/cxx-exception-probe",
              "hardened": ROOT / "build/macos/m5/cxx-exception/full/cxx-exception-probe-hardened"}
    linker = ROOT / ".deps/guest-toolchain/bin/ld.lld"
    archive = ROOT / ".deps/guest-libunwind/lib/libunwind.a"
    providers = [ROOT / ("build/macos/m5/deps/" + name) for name in ("libc-link.so", "libdl-link.so")]
    owned = [source, library, linker, archive, *providers, *probes.values()]
    identities = {str(p): local.digest(p) for p in owned}
    env = {k: v for k, v in os.environ.items() if not k.startswith(("LD_", "DYLD_"))}
    signature = local.inspect_existing_hardened_signature(probes["hardened"], env)
    runs = []
    for key, flag in (("a", "pac-ret+leaf"), ("b", "pac-ret+leaf+b-key")):
        directory = work / key
        directory.mkdir(exist_ok=True)
        obj, elf = directory / "fixture.o", directory / "libcxx-exception-fixture.so"
        commands = [
            ["xcrun", "clang++", "--target=aarch64-linux-android24", "-ffreestanding", "-fPIC",
             "-funwind-tables", "-fexceptions", "-frtti", "-fno-stack-protector",
             "-fno-omit-frame-pointer", "-mno-outline-atomics", "-O1",
             "-mbranch-protection=" + flag, "-c", str(source), "-o", str(obj)],
            [str(linker), "-shared", "--hash-style=both", "-z", "now", "-z", "max-page-size=16384",
             "--eh-frame-hdr", "--no-undefined", "-soname", elf.name, str(obj), str(archive),
             str(library), *map(str, providers), "-o", str(elf)],
        ]
        for command in commands:
            subprocess.run(command, env=env, check=True, capture_output=True, timeout=60)
        entries = authenticated_entries(elf, key)
        elf_hash = local.digest(elf)
        for mode, probe in probes.items():
            run = subprocess.run([str(probe), str(directory), str(library_dir)],
                                 env=env, capture_output=True, text=True, timeout=45,
                                 check=False, preexec_fn=no_child_core)
            log = directory / (mode + ".log")
            log.write_text(run.stdout + run.stderr)
            markers = [line for line in run.stdout.splitlines() if line.startswith("M5_CXX_EXCEPTION_PASS")]
            if run.returncode or markers != [MARKER] or local.digest(elf) != elf_hash:
                raise ValueError("authenticated guest exception gate failed: " + str(log))
            runs.append({"key": key, "mode": mode, "elf_sha256": elf_hash,
                         "entries": entries, "marker": MARKER, "log": str(log)})
    if any(local.digest(p) != value for p, value in ((Path(p), h) for p, h in identities.items())):
        raise ValueError("source/dependency/host probe changed during execution")
    report = {"schema": 1, "scope": "first-party PAC C++ exception frames; not ART/APK acceptance",
              "profile": profile, "source_identities": identities, "runs": runs,
              "existing_hardened_signature": signature, "hardware_execution": True,
              "signing_performed": False, "new_host_build": False, "hardened_art_verified": False,
              "authentication_failure_execution_verified": False, "harness_sha256": local.digest(Path(__file__))}
    (work / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("M5_RETURN_AUTH_UNWIND_LOCAL_PASS keys=2 runs=4 cases_per_run=4 signing_performed=0 art_execution=0")


if __name__ == "__main__":
    main()
