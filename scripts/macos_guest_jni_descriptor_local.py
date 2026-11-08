#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Actual ART descriptor refusals with guest-only builds and immutable host code."""
import argparse
import hashlib
import json
import os
import plistlib
import re
import resource
import signal
import subprocess
from pathlib import Path

from art_smoke import HOST, JARS, environment
from macos_guest_hardened_closure import ALLOW_JIT, output, validate_closure
from native_closure_inventory import validate_native_inventory

ROOT = Path(__file__).resolve().parents[1]
CASES = {"void-parameter": "(V)V", "void-array": "()[V",
         "object-name": "(Ljava.lang.Object;)V", "wide-slot-overflow": "(" + "J" * 128 + ")V",
         "array-rank-overflow": "(" + "[" * 256 + "I)V", "trailing-data": "()Vx"}
PASS = ("M5_ART_JNI_PASS integer12=1 mixed24=1 register=1 upcalls=ellipsis,V,A "
        "objects=1 exceptions=1 threads=4 guest_created=4 attach_cycles=8 tls_dtors=4")


def digest(path):
    with Path(path).open("rb") as stream:
        data = stream.read((256 << 20) + 1)
    if not 0 < len(data) <= 256 << 20:
        raise ValueError("descriptor input byte bound")
    return hashlib.sha256(data).hexdigest()


def validate_signature(signature, entitlements, team, main):
    actual = re.search(r"^TeamIdentifier=(.+)$", signature, re.MULTILINE)
    flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
    if (not team or team in ("not set", "-") or actual is None or actual[1] != team
            or flags is None or not int(flags[1], 16) & 0x10000
            or entitlements != (ALLOW_JIT if main else {})):
        raise ValueError("descriptor runtime requires one real TeamIdentifier and exact hardened entitlements")


def verify_hardened(manifest, env):
    """Read-only inspection of every existing signed image, never signing."""
    if (manifest.get("mode") != "hardened" or manifest.get("signed") is not True
            or manifest.get("hardened_verified") is not True):
        raise ValueError("existing strict hardened positive closure required")
    validate_closure(manifest, env)
    identities = {}
    for item in manifest["images"]:
        path = Path(item["path"])
        if digest(path) != item["signed_sha256"]:
            raise ValueError("signed descriptor runtime image changed")
        output(["/usr/bin/codesign", "--verify", "--strict", path], env)
        signature = output(["/usr/bin/codesign", "--display", "--verbose=4", path], env)
        shown = subprocess.run(["/usr/bin/codesign", "--display", "--entitlements", "-", "--xml", str(path)],
                               env=env, capture_output=True, check=True, timeout=60).stdout
        validate_signature(signature, plistlib.loads(shown) if shown.strip() else {},
                           manifest["team_identifier"], str(path) == manifest["main"])
        identities[str(path.relative_to(ROOT))] = digest(path)
    return identities


def no_child_core():
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def validate_result(stdout, stderr, returncode, positive):
    combined = stdout + stderr
    markers = [line for line in combined.splitlines() if line.startswith("M5_ART_JNI_PASS")]
    if positive:
        if returncode or markers != [PASS] or "SHIRAZ_GUEST_FATAL" in combined:
            raise ValueError("fresh positive JNI fixture must pass the complete ART suite")
    elif (returncode not in (1, -signal.SIGABRT) or markers
          or combined.splitlines().count("SHIRAZ_GUEST_FATAL invalid guest JNI method descriptor") != 1
          or not re.search(r"^Fatal signal 6 \(SIGABRT\)", combined, re.MULTILINE)):
        raise ValueError("malformed descriptor must reach the explicit JNI refusal and ART abort report")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("adhoc", "hardened"), default="adhoc")
    args = parser.parse_args()
    os.chdir(ROOT)
    work = ROOT / "build/macos/m5/jni-descriptor"
    if args.mode == "hardened":
        work /= "hardened"
    work.mkdir(parents=True, exist_ok=True)
    (work / "result.json").unlink(missing_ok=True)
    baseline = ROOT / "build/macos/m5/jni"
    evidence = json.loads((baseline / (args.mode + ".result.json")).read_text())
    identities = evidence["sha256"]
    if any(digest(ROOT / name) != value for name, value in identities.items()):
        raise ValueError("existing positive JNI evidence no longer matches host/fixture artifacts")
    vm = HOST / "bin/dalvikvm"
    bridge = ROOT / "build/macos/libshiraz-guest.so"
    host_fixture = ROOT / "build/macos/m5/host-fixture.so"
    jar = baseline / "fixture.jar"
    manifest = None
    env = {k: v for k, v in environment(work).items()
           if not k.startswith(("LD_", "DYLD_", "LINKER_", "BIONIC_", "BERBERIS_"))}
    if args.mode == "hardened":
        env.pop("SHIRAZ_GUEST_EGL_SHIM", None)
        env.pop("SHIRAZ_EGL_LIBRARY", None)
        manifest_path = Path(evidence["native_closure"])
        manifest_hash = digest(manifest_path)
        manifest = json.loads(manifest_path.read_text())
        identities = {**identities, **verify_hardened(manifest, env)}
        vm, bridge, host_fixture = (Path(manifest[key]) for key in ("main", "bridge", "host_fixture"))
        runtime_host = Path(manifest["tree"])
        env["ANDROID_ROOT"] = str(runtime_host)
        env["BOOTCLASSPATH"] = ":".join(str(runtime_host / "framework" / name) for name in JARS)
    else:
        runtime_host = HOST
    boot_hashes = {str(runtime_host / "framework" / name): digest(runtime_host / "framework" / name)
                   for name in JARS}
    expected_paths = [vm, bridge, host_fixture, jar, ROOT / "build/macos/m5/jni-fixture.so"]
    if set(evidence["sha256"]) != {str(p.relative_to(ROOT)) for p in expected_paths}:
        raise ValueError("positive JNI evidence must identify the exact required runtime artifacts")
    if (evidence["execution_verified"] is not True or evidence["returncode"] != 0
            or evidence["mode"] != args.mode or evidence.get("route_check_only", False)
            or (manifest is not None and evidence["hardened_verified"] is not True)):
        raise ValueError("current existing ART positive evidence required")
    if any(digest(ROOT / name) != value for name, value in identities.items()):
        raise ValueError("existing positive JNI evidence no longer matches host/fixture artifacts")
    original_command = [str(vm), "-Xbootclasspath:" + ":".join(str(runtime_host / "framework" / name) for name in JARS),
                        "-Xcheck:jni", "-Xusejit:false", "-Xint", "-Xnoimage-dex2oat",
                        "-XX:NativeBridge=" + str(bridge), "-Xforce-nb-testing",
                        "-Djava.library.path=" + str(runtime_host / "lib64"),
                        "-Dguest.fixture=", "-Dguest.io.file=", "-Dguest.host.fixture=" + str(host_fixture),
                        "-cp", str(jar), "GuestJniFixture"]
    if manifest is not None:
        original_command.insert(1, "-Dguest.native.closure=end-of-main")
    source = ROOT / "tests/darwin/guest/jni_fixture.c"
    source_hash = digest(source)
    linker = ROOT / ".deps/guest-toolchain/bin/ld.lld"
    providers = [ROOT / ("build/macos/m5/deps/" + name) for name in ("libc-link.so", "libjnigraphics-link.so")]
    emutls = ROOT / "build/macos/m5/deps/emutls.o"
    dependency_hashes = {str(p): digest(p) for p in [linker, emutls, *providers]}
    runs = []
    for name, signature in {"positive": None, **CASES}.items():
        directory = work / name
        directory.mkdir(exist_ok=True)
        obj, elf = directory / "fixture.o", directory / "fixture.so"
        command = ["xcrun", "clang", "--target=aarch64-linux-android24",
                   "-Icontext/art_standalone/libnativehelper/include/nativehelper", "-isystem",
                   ".deps/bionic-headers/sysroot/include", "-ffreestanding", "-fPIC", "-funwind-tables",
                   "-fstack-protector-all", "-O2", "-Wall", "-Wextra", "-Werror"]
        if signature is not None:
            command.append("-DSHIRAZ_REGISTERED_SIGNATURE=" + json.dumps(signature))
        subprocess.run([*command, "-c", str(source), "-o", str(obj)], check=True, env=env, timeout=60)
        subprocess.run([str(linker), "-shared", "--hash-style=both", "--eh-frame-hdr", "--no-undefined",
                        "-z", "now", "-z", "max-page-size=16384", "-soname", "libshiraz_jni_fixture.so",
                        str(obj), str(emutls), *map(str, providers), "-o", str(elf)], check=True, env=env, timeout=60)
        command = ["-Dguest.fixture=" + str(elf) if item.startswith("-Dguest.fixture=")
                   else "-Dguest.io.file=" + str(directory / "roundtrip.bin")
                   if item.startswith("-Dguest.io.file=") else item for item in original_command]
        elf_hash = digest(elf)
        run = subprocess.run(command, cwd=directory, env=env, capture_output=True, text=True,
                             timeout=45, check=False, preexec_fn=no_child_core)
        log = directory / "run.log"
        log.write_text(run.stdout + run.stderr)
        validate_result(run.stdout, run.stderr, run.returncode, name == "positive")
        inventory = (validate_native_inventory(run.stdout, manifest, [vm, bridge, host_fixture])
                     if manifest is not None and name == "positive" else None)
        if digest(elf) != elf_hash:
            raise ValueError("guest fixture changed during ART execution")
        runs.append({"case": name, "signature": signature, "elf_sha256": elf_hash,
                     "returncode": run.returncode, "log": str(log), "command": command,
                     "native_image_inventory": inventory})
    if manifest is not None and (
        digest(manifest_path) != manifest_hash or verify_hardened(manifest, env) != {
            str(Path(item["path"]).relative_to(ROOT)): item["signed_sha256"] for item in manifest["images"]}
    ):
        raise ValueError("hardened closure changed during descriptor execution")
    if (digest(source) != source_hash
        or any(digest(ROOT / p) != h for p, h in identities.items())
        or any(digest(Path(p)) != h for p, h in boot_hashes.items())
        or any(digest(Path(p)) != h for p, h in dependency_hashes.items())):
        raise ValueError("source/dependency/host artifact changed during execution")
    report = {"schema": 1, "scope": "actual ART shadow RegisterNatives descriptor validation",
              "mode": args.mode,
              "runs": runs, "source_sha256": source_hash, "baseline_identities": identities,
              "boot_classpath_sha256": boot_hashes,
              "guest_link_dependencies": dependency_hashes, "harness_sha256": digest(Path(__file__)),
              "signing_performed": False, "new_host_build": False, "hardened_art_verified": manifest is not None,
              "negative_inventory_scope": (
                  "abort paths do not reach end-of-main; closure signatures and hashes verified before/after"
                  if manifest is not None else
                  "abort paths do not reach end-of-main; required host artifact hashes verified before/after"),
              "negative_status_scope": "ART reports SIGABRT and may translate it to process exit 1; no raw wait-status claim"}
    (work / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("M5_ART_JNI_DESCRIPTOR_LOCAL_PASS positive=1 explicit_refusals=6 new_host_build=0 signing_performed=0")


if __name__ == "__main__":
    main()
