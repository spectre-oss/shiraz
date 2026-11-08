#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Execute production descriptor logic in guest ELFs using existing host probes."""
import json
import os
import subprocess
from pathlib import Path

import macos_guest_return_auth_local as local

ROOT = Path(__file__).resolve().parents[1]
MARKER = ("M5_ELF_ABI_PASS images=2 constructors=2 integer12=1 "
          "mixed24=1 variadic25=1 callback_va_list=1 closure=1 dyld=0")


def main():
    os.chdir(ROOT)
    work = ROOT / "build/macos/m5/descriptor-runtime"
    work.mkdir(parents=True, exist_ok=True)
    (work / "result.json").unlink(missing_ok=True)
    sdk = subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip()
    probes = {"normal": ROOT / "build/macos/m5/elf-probe",
              "hardened": ROOT / "build/macos/m5/elf-probe-hardened"}
    sources = [ROOT / "include/shiraz/darwin/guest/descriptor.hpp",
               ROOT / "tests/darwin/guest/descriptor_runtime.cpp",
               ROOT / "tests/darwin/guest/fixture.c"]
    linker = ROOT / ".deps/guest-toolchain/bin/ld.lld"
    identities = {str(p): local.digest(p) for p in [*sources, linker, *probes.values()]}
    env = {k: v for k, v in os.environ.items() if not k.startswith(("LD_", "DYLD_"))}
    compiler_version = subprocess.check_output(["xcrun", "clang++", "--version"],
                                               env=env, text=True, timeout=10)
    signature = local.inspect_existing_hardened_signature(probes["hardened"], env)
    compile_common = ["--target=aarch64-linux-android24", "-ffreestanding", "-fPIC",
                      "-fno-stack-protector", "-O2", "-Wall", "-Wextra", "-Werror"]
    fixture = work / "fixture.o"
    subprocess.run(["xcrun", "clang", *compile_common, "-DSHIRAZ_DESCRIPTOR_GATE", "-c",
                    str(sources[2]), "-o", str(fixture)], check=True, env=env, timeout=60)
    runs = []
    for negative in (False, True):
        name = "negative" if negative else "positive"
        obj, elf = work / (name + ".o"), work / (name + ".so")
        command = ["xcrun", "clang++", *compile_common, "-std=c++17", "-fno-exceptions",
                   "-fno-rtti", "-D_LIBCPP_DISABLE_AVAILABILITY", "-Iinclude", "-isystem",
                   sdk + "/usr/include/c++/v1", "-isystem", ".deps/bionic-headers/sysroot/include"]
        if negative:
            command.append("-DSHIRAZ_DESCRIPTOR_FORCE_FAILURE")
        subprocess.run([*command, "-c", str(sources[1]), "-o", str(obj)],
                       check=True, env=env, timeout=60)
        subprocess.run([str(linker), "-shared", "--hash-style=both", "-z", "now", "-z",
                        "max-page-size=16384", "--eh-frame-hdr", "--no-undefined",
                        str(fixture), str(obj), "-o", str(elf)], check=True, env=env, timeout=60)
        elf_hash = local.digest(elf)
        for mode, probe in probes.items():
            run = subprocess.run([str(probe), str(elf)], env=env, capture_output=True,
                                 text=True, timeout=30, check=False)
            log = work / (name + "-" + mode + ".log")
            log.write_text(run.stdout + run.stderr)
            markers = [line for line in run.stdout.splitlines() if line.startswith("M5_ELF_ABI_PASS")]
            if negative:
                if run.returncode != 1 or markers or "relocations/BSS/constructor" not in run.stderr:
                    raise ValueError("forced descriptor failure must reach host constructor assertion")
            elif run.returncode or markers != [MARKER]:
                raise ValueError("runtime descriptor cases failed: " + str(log))
            if local.digest(elf) != elf_hash:
                raise ValueError("descriptor guest artifact changed during execution")
            runs.append({"variant": name, "mode": mode, "returncode": run.returncode,
                         "elf_sha256": elf_hash, "log": str(log)})
    if any(local.digest(Path(p)) != h for p, h in identities.items()):
        raise ValueError("source or pre-existing host probe changed during execution")
    report = {"schema": 1, "scope": "production parser logic in a first-party guest fixture, not JNI/ART integration",
              "cases_per_image": 28, "images_per_positive_run": 2, "runs": runs,
              "source_identities": identities, "sdk": sdk, "compiler_version": compiler_version,
              "existing_hardened_signature": signature,
              "harness_sha256": local.digest(Path(__file__)), "hardware_execution": True,
              "new_host_build": False, "signing_performed": False, "art_execution": False}
    (work / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("M5_DESCRIPTOR_RUNTIME_LOCAL_PASS cases_per_image=28 positive_runs=2 negative_runs=2 signing_performed=0 art_execution=0")


if __name__ == "__main__":
    main()
