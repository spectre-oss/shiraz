#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise guest C++ exceptions with the unchanged APK's libc++ implementation."""

import argparse
import hashlib
import json
import os
import plistlib
import re
import shutil
import signal
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/macos/m5/cxx-exception"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked(command, env, log=None):
    command = list(map(str, command))
    result = subprocess.run(
        command, env=env, capture_output=True, check=False, timeout=120
    )
    if log is not None:
        log.write_bytes(result.stdout + result.stderr)
        log.with_suffix(".json").write_text(
            json.dumps({"command": command, "returncode": result.returncode}, indent=2)
            + "\n"
        )
    if result.returncode:
        print((result.stdout + result.stderr).decode(errors="replace"), file=sys.stderr)
        raise SystemExit(f"command failed ({result.returncode}): " + " ".join(command))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--object", type=Path, required=True)
    parser.add_argument("--probe", type=Path, required=True)
    parser.add_argument("--linker", type=Path, required=True)
    parser.add_argument("--unwind-archive", type=Path)
    parser.add_argument("--link-provider", action="append", type=Path, default=[])
    parser.add_argument("--coverage", choices=("full", "basic"), default="full")
    parser.add_argument("--mode", choices=("both", "adhoc", "hardened"), default="both")
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)
    fixture_object = args.object.resolve(strict=True)
    original_probe = args.probe.resolve(strict=True)
    linker = args.linker.resolve(strict=True)
    unwind_archive = (
        args.unwind_archive.resolve(strict=True) if args.unwind_archive else None
    )
    link_providers = [path.resolve(strict=True) for path in args.link_provider]
    work = WORK / args.coverage
    work.mkdir(parents=True, exist_ok=True)
    libraries = work / "unchanged-apk-libs"
    libraries.mkdir(exist_ok=True)
    library = libraries / "libc++_shared.so"
    pending = library.with_suffix(".pending")
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo("lib/arm64-v8a/libc++_shared.so")
        if not 0 < entry.file_size <= 256 * 1024 * 1024:
            raise SystemExit("invalid or oversized APK libc++")
        sha = hashlib.sha256()
        with archive.open(entry) as source, pending.open("wb") as destination:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                sha.update(block)
                destination.write(block)
    if digest(pending) != sha.hexdigest():
        raise SystemExit("APK libc++ extraction hash mismatch")
    pending.replace(library)
    env = {
        key: value
        for key, value in os.environ.items()
        if not key.startswith(("LD_", "DYLD_"))
    }
    fixture = work / "libcxx-exception-fixture.so"
    fixture_pending = fixture.with_suffix(".pending")
    link_command = [
        linker,
        "-shared",
        "--hash-style=both",
        "-z",
        "now",
        "-z",
        "max-page-size=16384",
        "--eh-frame-hdr",
        "--no-undefined",
        "-soname",
        fixture.name,
        fixture_object,
        *([unwind_archive] if unwind_archive else []),
        library,
        *link_providers,
        "-o",
        fixture_pending,
    ]
    link_result = checked(link_command, env, work / "link.log")
    fixture_pending.replace(fixture)
    (work / "link.log").write_bytes(link_result.stdout + link_result.stderr)
    modes = ("adhoc", "hardened") if args.mode == "both" else (args.mode,)
    all_passed = True
    for mode in modes:
        probe = original_probe
        if mode == "hardened":
            probe = work / "cxx-exception-probe-hardened"
            probe_pending = probe.with_suffix(".pending")
            shutil.copy2(original_probe, probe_pending)
            probe_pending.replace(probe)
            checked(
                [
                    "codesign",
                    "--force",
                    "--sign",
                    "-",
                    "--options",
                    "runtime",
                    "--entitlements",
                    ROOT / "tests/darwin/guest/jit.entitlements",
                    probe,
                ],
                env,
            )
            checked(["codesign", "--verify", "--strict", probe], env)
        signature = checked(
            ["codesign", "--display", "--verbose=2", probe], env
        ).stderr.decode()
        entitlements = {}
        if mode == "hardened":
            flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
            if flags is None or not int(flags[1], 16) & 0x10000:
                raise SystemExit(
                    "hardened exception gate requires runtime signing flag"
                )
            entitlements = plistlib.loads(
                checked(
                    ["codesign", "--display", "--entitlements", "-", "--xml", probe],
                    env,
                ).stdout
            )
            if entitlements != {"com.apple.security.cs.allow-jit": True}:
                raise SystemExit("hardened exception gate permits only allow-jit")
        command = list(map(str, [probe, work, libraries]))
        if args.coverage == "basic":
            command.append("basic")
        timed_out = False
        try:
            result = subprocess.run(
                command, env=env, capture_output=True, check=False, timeout=60
            )
            stdout, stderr, returncode = result.stdout, result.stderr, result.returncode
        except subprocess.TimeoutExpired as failure:
            # subprocess.run kills and reaps its owned child on timeout.
            timed_out = True
            stdout, stderr, returncode = (
                failure.stdout or b"",
                failure.stderr or b"",
                None,
            )
        (work / f"{mode}.log").write_bytes(stdout + stderr)
        marker = (
            b"M5_CXX_EXCEPTION_PASS "
            if args.coverage == "full"
            else b"M5_CXX_EXCEPTION_BASIC_PASS "
        )
        passed = returncode == 0 and not timed_out and marker in stdout
        positive_passed = passed
        escape_evidence = None
        if args.coverage == "full" and positive_passed:
            escape_command = list(map(str, [probe, work, libraries, "escape"]))
            escape_timed_out = False
            try:
                escaped = subprocess.run(
                    escape_command,
                    env=env,
                    capture_output=True,
                    check=False,
                    timeout=60,
                )
                escape_stdout, escape_stderr, escape_returncode = (
                    escaped.stdout,
                    escaped.stderr,
                    escaped.returncode,
                )
            except subprocess.TimeoutExpired as failure:
                escape_timed_out = True
                escape_stdout, escape_stderr, escape_returncode = (
                    failure.stdout or b"",
                    failure.stderr or b"",
                    None,
                )
            escape_output = escape_stdout + escape_stderr
            (work / f"{mode}-escape.log").write_bytes(escape_output)
            entered = b"M5_CXX_EXCEPTION_ESCAPE_ENTER" in escape_stdout.splitlines()
            host_caught = b"M5_CXX_EXCEPTION_HOST_CAUGHT" in escape_output
            host_returned = b"M5_CXX_EXCEPTION_HOST_RETURNED" in escape_output
            escape_passed = (
                escape_returncode == -signal.SIGABRT
                and not escape_timed_out
                and entered
                and marker in escape_stdout
                and not host_caught
                and not host_returned
            )
            escape_evidence = {
                "command": escape_command,
                "returncode": escape_returncode,
                "timed_out": escape_timed_out,
                "entered": entered,
                "positive_cases_passed_in_child": marker in escape_stdout,
                "host_caught": host_caught,
                "host_returned": host_returned,
                "required_signal": "SIGABRT",
                "passed": escape_passed,
            }
            (work / f"{mode}-escape.json").write_text(
                json.dumps(escape_evidence, indent=2) + "\n"
            )
            print(escape_output.decode(errors="replace"), end="")
            passed &= escape_passed
        evidence = {
            "command": command,
            "link_command": list(map(str, link_command)),
            "returncode": returncode,
            "timed_out": timed_out,
            "passed": passed,
            "positive_cases_passed": positive_passed,
            "host_boundary_escape": escape_evidence,
            "coverage": args.coverage,
            "full_raii_rethrow_gate_passed": passed and args.coverage == "full",
            "signature": signature,
            "entitlements": entitlements,
            "apk_libcxx_member": entry.filename,
            "apk_libcxx_sha256": sha.hexdigest(),
            "loaded_libcxx_sha256": digest(library),
            "fixture_object_sha256": digest(fixture_object),
            "unwind_archive": str(unwind_archive) if unwind_archive else None,
            "unwind_archive_sha256": digest(unwind_archive) if unwind_archive else None,
            "link_only_provider_hashes": {
                str(path): digest(path) for path in link_providers
            },
            "fixture_sha256": digest(fixture),
            "probe_sha256": digest(probe),
            "app_execution": False,
            "host_exception_crossing": False,
        }
        (work / f"{mode}.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print((stdout + stderr).decode(errors="replace"), end="")
        all_passed &= passed
    if not all_passed:
        raise SystemExit("guest C++ exception gate failed; inspect " + str(work))


if __name__ == "__main__":
    main()
