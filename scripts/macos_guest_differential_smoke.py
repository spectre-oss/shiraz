#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run one real JNI corpus on Darwin; compare only with actual bionic evidence.

This script never drives a device, installs an APK or changes app data. It emits
app_process64 argv/artifact metadata for a future project device test runner.
Without a collected reference report the differential gate is NOT performed.
"""

import argparse
import fcntl
import hashlib
import json
import os
import plistlib
import re
import shutil
import signal
import struct
import subprocess
import zipfile
from pathlib import Path, PurePosixPath

import bootstrap_guest_emutls as emutls
from art_smoke import HOST, JARS, environment
from macos_guest_hardened_closure import sign_closure, stage_closure, write_manifest

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/macos/m5/differential"
RESULT_MARKER = "M5_DIFFERENTIAL_RESULT "
STACK_MARKER = "M5_DIFFERENTIAL_STACK_CHILD_READY sha256="
CONTRACTS = {
    "tls_threads": 2,
    "tls_destructors": 6,
    "tls_isolation": True,
    "tls_nested_lifo": True,
    "guard_nonzero": True,
    "cpu_semantics": True,
    "signal_delivery": True,
    "signal_mask_pending": True,
    "signal_errno_preserved": True,
    "format_cases": 9,
}


def digest(path):
    if not path.is_file() or path.stat().st_size > 16 * 1024 * 1024:
        raise ValueError(f"missing/oversized fixture input: {path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def entry_binary_identity(runtime, bridge, *, max_bytes=256 << 20):
    """Primary entry binaries only; this is not a full dyld image inventory."""
    result = {}
    for name, source in (("vm", runtime / "bin/dalvikvm"), ("bridge", bridge)):
        path = source.resolve(strict=True)
        if not path.is_file() or not 0 < path.stat().st_size <= max_bytes:
            raise ValueError("missing/oversized runtime entry binary: " + str(path))
        checksum, size = hashlib.sha256(), 0
        with path.open("rb") as stream:
            while block := stream.read(65536):
                size += len(block)
                if size > max_bytes:
                    raise ValueError("runtime entry binary exceeded hash bound")
                checksum.update(block)
        if not size:
            raise ValueError("empty runtime entry binary")
        result[name] = {
            "path": str(path),
            "bytes": size,
            "sha256": checksum.hexdigest(),
        }
    return result


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def checked(command, env):
    subprocess.run(list(map(str, command)), env=env, check=True, timeout=120)


def corpus(env):
    expected_emutls = {}
    for source, expected in emutls.FILES.items():
        path = emutls.OUT / Path(source).name
        if digest(path) != expected:
            raise ValueError(
                "compiler-rt emutls input is not the pinned unchanged source"
            )
        expected_emutls[source] = expected
    original = ROOT / "build/macos/m5/deps/libm5-differential.so"
    fixture = WORK / "libm5-differential.so"
    source_hash = digest(original)
    with original.open("rb") as stream:
        header = stream.read(20)
    if header != bytes.fromhex("7f454c460201010000000000000000000300b700"):
        raise ValueError("differential fixture must be an AArch64 ELF shared object")
    pending = fixture.with_suffix(".pending")
    shutil.copy2(original, pending)
    if digest(pending) != source_hash:
        raise ValueError("differential ELF copy mismatch")
    pending.replace(fixture)
    collector_original = ROOT / "build/macos/m5/deps/libm5-reference-child.so"
    if not collector_original.is_file():
        raise ValueError(
            "reference collector is not built; run make macos-guest-differential-corpus"
        )
    with collector_original.open("rb") as stream:
        if stream.read(20) != header:
            raise ValueError("reference collector must be an AArch64 ELF shared object")
    collector_hash = digest(collector_original)
    collector = WORK / "libm5-reference-child.so"
    collector_pending = collector.with_suffix(".pending")
    shutil.copy2(collector_original, collector_pending)
    if digest(collector_pending) != collector_hash:
        raise ValueError("reference collector copy mismatch")
    collector_pending.replace(collector)
    classes = WORK / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    checked(
        [
            "javac",
            "--release",
            "8",
            "-d",
            classes,
            ROOT / "tests/darwin/guest/BionicDifferentialFixture.java",
            ROOT / "tests/darwin/guest/BionicReferenceRunner.java",
        ],
        env,
    )
    dex = WORK / "classes.dex"
    checked(
        [
            HOST / "bin/dx",
            "--dex",
            "--min-sdk-version=27",
            "--output=" + str(dex),
            classes,
        ],
        env,
    )
    jar = WORK / "fixture.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        entry = zipfile.ZipInfo("classes.dex")
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
        archive.writestr(entry, dex.read_bytes())
    manifest = {
        "schema": 1,
        "fixture": str(fixture),
        "fixture_sha256": source_hash,
        "jar": str(jar),
        "jar_sha256": digest(jar),
        "dex_sha256": digest(dex),
        "collector": str(collector),
        "collector_sha256": collector_hash,
        "reference_collector_sources_sha256": {
            name: digest(ROOT / "tests/darwin/guest" / name)
            for name in (
                "bionic_reference_child.c",
                "bionic_reference_child.h",
                "BionicReferenceRunner.java",
            )
        },
        "generation_tooling": {
            "scope": "tools observed at corpus generation; build commands are retained separately",
            "clang_version": subprocess.run(
                ["xcrun", "clang", "--version"],
                env=env,
                capture_output=True,
                text=True,
                check=True,
                timeout=30,
            ).stdout.strip(),
            "linker_provenance_sha256": digest(
                ROOT / ".deps/guest-toolchain/provenance.json"
            ),
        },
        "emutls_revision": emutls.REVISION,
        "emutls_source_sha256": expected_emutls,
        "scope": "same first-party ELF/DEX corpus; actual unchanged compiler-rt emutls",
        "reference_performed": False,
    }
    write_json(WORK / "corpus.json", manifest)
    return manifest


def properties(manifest, fixture, jar):
    return [
        "-Dguest.fixture=" + str(fixture),
        "-Dguest.fixture.jar=" + str(jar),
        "-Dguest.fixture.sha256=" + manifest["fixture_sha256"],
        "-Dguest.fixture.jar.sha256=" + manifest["jar_sha256"],
    ]


def reference_plan(manifest, directory):
    stage = PurePosixPath(directory)
    if (
        not stage.is_absolute()
        or ".." in stage.parts
        or re.fullmatch(
            r"/data/local/tmp/shiraz-m5-[A-Za-z0-9][A-Za-z0-9._-]{0,95}", str(stage)
        )
        is None
    ):
        raise ValueError(
            "reference directory must be an owned /data/local/tmp/shiraz-m5-* path"
        )
    library, jar = stage / "libm5-differential.so", stage / "fixture.jar"
    collector = stage / "libm5-reference-child.so"
    base = [
        "/system/bin/app_process64",
        *properties(manifest, library, jar),
        "/system/bin",
        "BionicDifferentialFixture",
    ]
    plan = {
        "schema": 1,
        "performed": False,
        "scope": "project test runner only; no APK installation or app-data mutation",
        "artifacts": [
            {
                "source": manifest["fixture"],
                "destination": str(library),
                "sha256": manifest["fixture_sha256"],
            },
            {
                "source": manifest["jar"],
                "destination": str(jar),
                "sha256": manifest["jar_sha256"],
            },
            {
                "source": manifest["collector"],
                "destination": str(collector),
                "sha256": manifest["collector_sha256"],
            },
        ],
        "collector_argv": [
            "/system/bin/app_process64",
            *properties(manifest, library, jar),
            "-Dguest.reference.collector=" + str(collector),
            "-Dguest.reference.collector.sha256=" + manifest["collector_sha256"],
            "/system/bin",
            "BionicReferenceRunner",
        ],
        "result_marker": "M5_BIONIC_REFERENCE_RESULT ",
        "child_argv_scope": "diagnostic child descriptors only; collect through collector_argv",
        "environment": {"CLASSPATH": str(jar)},
        "run_argv": [*base, "run"],
        "stack_failure_child_argv": [*base, "stack-failure"],
        "required_collection": {
            "collection_method": "native-waitpid",
            "collector_sha256": manifest["collector_sha256"],
            "run": {
                "returncode": 0,
                "raw_wait_status": 0,
                "report": "JSON after M5_DIFFERENTIAL_RESULT marker",
            },
            "stack_failure": {
                "returncode": -int(signal.SIGABRT),
                "ready_fixture_sha256": manifest["fixture_sha256"],
            },
        },
        "note": "native waitpid collector preserves raw child status; actual Android execution is still required",
    }
    write_json(WORK / "reference-command.json", plan)


def validate_report(report, manifest, reference=False):
    if (
        report.get("schema") != 1
        or report.get("fixture_sha256") != manifest["fixture_sha256"]
        or report.get("jar_sha256") != manifest["jar_sha256"]
    ):
        raise ValueError(
            "differential report does not match exact current ELF/DEX corpus"
        )
    native = report.get("native", {})
    if (
        native.get("schema") != 1
        or native.get("status") != "pass"
        or json.dumps(native.get("contracts"), sort_keys=True)
        != json.dumps(CONTRACTS, sort_keys=True)
    ):
        raise ValueError(
            "differential report did not prove all current native contracts"
        )
    if reference:
        platform = report.get("platform", {})
        if (
            platform.get("os") != "Linux"
            or platform.get("vm") != "Dalvik"
            or not isinstance(platform.get("android_sdk"), int)
            or platform["android_sdk"] < 27
        ):
            raise ValueError(
                "reference report is not from actual API27+ Android ART/bionic"
            )


def child(command, env, log):
    command = list(map(str, command))
    timed_out = False
    with log.open("wb") as output:
        process = subprocess.Popen(
            command, env=env, cwd=WORK, stdout=output, stderr=subprocess.STDOUT
        )
        try:
            process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            process.wait()
    if log.stat().st_size > 8 * 1024 * 1024:
        raise ValueError("differential child output exceeds bound")
    text = log.read_text(errors="replace")
    result = {
        "command": command,
        "returncode": process.returncode,
        "timed_out": timed_out,
    }
    write_json(log.with_suffix(".command.json"), result)
    if timed_out:
        raise ValueError("differential child timed out; see " + str(log))
    return result, text


def classify_darwin_stack_failure(result, text, fixture_sha256):
    """Distinguish kernel SIGABRT from ART's documented Darwin exit(1).

    ART's non-Linux fatal handler prints SIGABRT and calls exit(EXIT_FAILURE).
    An exit status alone therefore proves nothing: require the current corpus
    readiness marker and the actual guest stack-check diagnostic, in order,
    and reject contradictory fatal diagnostics. This exception to raw signal
    collection is Darwin-only; reference_result retains its raw -SIGABRT gate.
    """
    if result.get("timed_out", False):
        raise ValueError("stack-corruption child timed out")
    lines = text.splitlines()
    ready = [
        (index, line)
        for index, line in enumerate(lines)
        if line.startswith(STACK_MARKER)
    ]
    if len(ready) != 1 or ready[0][1] != STACK_MARKER + fixture_sha256:
        raise ValueError("stack-corruption child lacks exact current corpus readiness")
    guard_marker = "SHIRAZ_GUEST_FATAL stack guard check failed"
    guards = [
        (index, line)
        for index, line in enumerate(lines)
        if "SHIRAZ_GUEST_FATAL" in line
    ]
    if len(guards) != 1 or guards[0][1] != guard_marker or ready[0][0] >= guards[0][0]:
        raise ValueError(
            "stack-corruption child lacks ordered actual stack-check failure"
        )
    fatals = [
        (index, line) for index, line in enumerate(lines) if "Fatal signal" in line
    ]
    if len(fatals) > 1:
        raise ValueError("stack-corruption child has ambiguous fatal signals")
    fatal_tags = re.findall(r"\bSHIRAZ_[A-Z_]*FATAL\b", text)
    if any(tag != "SHIRAZ_GUEST_FATAL" for tag in fatal_tags) or any(
        "FATAL EXCEPTION" in line for line in lines
    ):
        raise ValueError("stack-corruption child has another runtime fatal failure")
    if fatals:
        fatal = re.fullmatch(r"Fatal signal 6 \(SIGABRT\), code .+", fatals[0][1])
        if fatal is None or guards[0][0] >= fatals[0][0]:
            raise ValueError("stack-corruption child has contradictory fatal signal")
    if result.get("returncode") == -int(signal.SIGABRT):
        return {
            "observation": "kernel-sigabrt",
            "kernel_signal": int(signal.SIGABRT),
            "art_reported_signal": int(signal.SIGABRT) if fatals else None,
        }
    if result.get("returncode") == 1 and fatals:
        return {
            "observation": "art-darwin-handler-exit1",
            "kernel_signal": None,
            "art_reported_signal": int(signal.SIGABRT),
        }
    raise ValueError(
        "stack-corruption child has neither kernel SIGABRT nor proved ART Darwin fatal exit"
    )


def darwin(manifest, mode, identity, env):
    runtime, bridge, closure = HOST, ROOT / "build/macos/libshiraz-guest.so", None
    env = dict(env)
    env.pop("SHIRAZ_GUEST_EGL_SHIM", None)
    env.pop("SHIRAZ_EGL_LIBRARY", None)
    if mode == "hardened":
        closure = stage_closure(ROOT, HOST, WORK, env)
        print(
            "M5_DIFFERENTIAL_HARDENED_PREPARED manifest="
            + str(Path(closure["tree"]) / "closure.json"),
            flush=True,
        )
        if not identity:
            raise ValueError(
                "strict hardened differential ART needs a public signing identity with a real TeamIdentifier"
            )
        sign_closure(
            closure, identity, ROOT / "tests/darwin/guest/jit.entitlements", env
        )
        runtime, bridge = Path(closure["tree"]), Path(closure["bridge"])
        env["ANDROID_ROOT"] = str(runtime)
        env["BOOTCLASSPATH"] = ":".join(
            str(runtime / "framework" / name) for name in JARS
        )
    base = [
        runtime / "bin/dalvikvm",
        "-Xbootclasspath:"
        + ":".join(str(runtime / "framework" / name) for name in JARS),
        "-Xcheck:jni",
        "-Xusejit:false",
        "-Xint",
        "-Xnoimage-dex2oat",
        "-XX:NativeBridge=" + str(bridge),
        "-Xforce-nb-testing",
        "-Djava.library.path=" + str(runtime / "lib64"),
        *properties(manifest, manifest["fixture"], manifest["jar"]),
        "-cp",
        manifest["jar"],
        "BionicDifferentialFixture",
    ]
    entry_identity = entry_binary_identity(runtime, bridge)
    run, text = child([*base, "run"], env, WORK / (mode + ".log"))
    reports = [
        line[len(RESULT_MARKER) :]
        for line in text.splitlines()
        if line.startswith(RESULT_MARKER)
    ]
    if run["returncode"] != 0 or len(reports) != 1:
        raise ValueError(
            "Darwin differential corpus failed; see " + str(WORK / (mode + ".log"))
        )
    report = json.loads(reports[0])
    validate_report(report, manifest)
    if not any(
        int(value) > 0
        for value in re.findall(r"SHIRAZ_GUEST_TPIDR_REWRITTEN sites=(\d+)", text)
    ):
        raise ValueError("Darwin corpus did not prove actual MRS rewriting")
    failed, failure_text = child(
        [*base, "stack-failure"], env, WORK / (mode + "-stack-failure.log")
    )
    termination = classify_darwin_stack_failure(
        failed, failure_text, manifest["fixture_sha256"]
    )
    if entry_binary_identity(runtime, bridge) != entry_identity:
        raise ValueError("runtime entry binaries changed during differential execution")
    collected = {
        "schema": 1,
        "entry_binary_identity": entry_identity,
        "entry_identity_scope": "primary VM and bridge only; not full dyld dependency inventory",
        "run": {**run, "report": report},
        "stack_failure": {
            **failed,
            **termination,
            "ready_fixture_sha256": manifest["fixture_sha256"],
        },
        "native_closure": str(Path(closure["tree"]) / "closure.json")
        if closure
        else None,
    }
    write_json(WORK / (mode + ".result.json"), collected)
    if closure:
        closure.update(execution_verified=True, fixture="differential", returncode=0)
        write_manifest(closure)
    return collected


def validate_collector_signature(signature, entitlements):
    flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
    key = "com.apple.security.cs.allow-jit"
    if flags is None or not int(flags[1], 16) & 0x10000:
        raise ValueError("collector fixture lacks actual hardened runtime flag")
    if set(entitlements) != {key} or entitlements[key] is not True:
        raise ValueError("collector fixture entitlements must be exactly allow-jit")
    return int(flags[1], 16)


def verify_native_collector(path):
    path = path.resolve(strict=True)
    subprocess.run(
        ["codesign", "--verify", "--strict", str(path)], check=True, timeout=30
    )
    display = subprocess.run(
        ["codesign", "--display", "--verbose=4", str(path)],
        capture_output=True,
        text=True,
        check=True,
        timeout=30,
    )
    signature = display.stdout + display.stderr
    shown = subprocess.run(
        ["codesign", "--display", "--entitlements", "-", "--xml", str(path)],
        capture_output=True,
        check=True,
        timeout=30,
    ).stdout
    entitlements = plistlib.loads(shown)
    flags = validate_collector_signature(signature, entitlements)
    write_json(
        WORK / "collector-native-signature.json",
        {
            "path": str(path),
            "sha256": digest(path),
            "signature": signature,
            "flags": flags,
            "entitlements": entitlements,
            "native_fixture_only": True,
            "hardened_art_verified": False,
            "reference_performed": False,
        },
    )
    print(
        "M5_REFERENCE_SIGNATURE_PASS native_fixture_only=1 runtime=1 allow_jit_only=1 hardened_art_verified=0"
    )


def reference_result(path, manifest):
    if path.stat().st_size > 512 * 1024:
        raise ValueError("reference report exceeds bound")
    value = json.loads(path.read_text())
    if not isinstance(value, dict):
        raise TypeError("reference report must be an object")
    expected_collector = manifest.get("collector_sha256")
    if (
        not isinstance(expected_collector, str)
        or len(expected_collector) != 64
        or value.get("collection_method") != "native-waitpid"
        or value.get("collector_sha256") != expected_collector
    ):
        raise ValueError(
            "reference report lacks current native waitpid collector identity"
        )
    run = value.get("run", {})
    if not isinstance(run, dict):
        raise TypeError("reference run must be an object")
    raw_run = run.get("raw_wait_status")
    if type(raw_run) is not int or not 0 <= raw_run <= 65535 or raw_run != 0:
        raise ValueError("reference positive child lacks actual successful wait status")
    if value.get("schema") != 1 or value.get("run", {}).get("returncode") != 0:
        raise ValueError("reference run did not succeed")
    if value.get("run", {}).get("timed_out", False):
        raise ValueError("reference run timed out")
    validate_report(value["run"].get("report", {}), manifest, reference=True)
    failed = value.get("stack_failure", {})
    if not isinstance(failed, dict):
        raise TypeError("reference failure must be an object")
    raw_failure = failed.get("raw_wait_status")
    if (
        type(raw_failure) is not int
        or not 0 <= raw_failure <= 65535
        or raw_failure >> 8 != 0
        or not os.WIFSIGNALED(raw_failure)
        or os.WTERMSIG(raw_failure) != int(signal.SIGABRT)
    ):
        raise ValueError(
            "reference deliberate-corruption child lacks actual SIGABRT wait status"
        )
    if (
        failed.get("returncode") != -int(signal.SIGABRT)
        or failed.get("timed_out", False)
        or failed.get("ready_fixture_sha256") != manifest["fixture_sha256"]
    ):
        raise ValueError("reference deliberate-corruption child was not verified")
    return {"path": str(path), "sha256": digest(path), "collection": value}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("adhoc", "hardened"), default="adhoc")
    parser.add_argument(
        "--identity", default=os.environ.get("SHIRAZ_CODESIGN_IDENTITY")
    )
    parser.add_argument("--reference-result", type=Path)
    parser.add_argument(
        "--reference-directory", default="/data/local/tmp/shiraz-m5-differential"
    )
    parser.add_argument("--emit-reference-only", action="store_true")
    parser.add_argument("--verify-native-collector", type=Path)
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    if args.verify_native_collector:
        verify_native_collector(args.verify_native_collector)
        return
    env = {
        key: value
        for key, value in environment(WORK).items()
        if not key.startswith(("DYLD_", "LD_", "LINKER_", "BIONIC_", "BERBERIS_"))
    }
    manifest = corpus(env)
    reference_plan(manifest, args.reference_directory)
    if args.emit_reference_only:
        print(
            "M5_DIFFERENTIAL_CORPUS_READY reference_performed=0 plan="
            + str(WORK / "reference-command.json")
        )
        return
    actual = darwin(manifest, args.mode, args.identity, env)
    comparison = {
        "schema": 1,
        "mode": args.mode,
        "reference_performed": False,
        "darwin_result": actual,
        "fixture_sha256": manifest["fixture_sha256"],
        "jar_sha256": manifest["jar_sha256"],
        "comparison": "not-performed",
    }
    if args.reference_result:
        comparison.update(
            reference_performed=True,
            comparison="contracts-pass",
            reference=reference_result(
                args.reference_result.resolve(strict=True), manifest
            ),
        )
        # Hardware observations deliberately differ; only semantic contracts
        # are compared, never raw HWCAP bits or host-specific page sizes.
    write_json(WORK / (args.mode + ".comparison.json"), comparison)
    print(
        "M5_DIFFERENTIAL_DARWIN_PASS mode="
        + args.mode
        + " reference_performed="
        + str(int(comparison["reference_performed"]))
    )


if __name__ == "__main__":
    ROOT.joinpath(".deps").mkdir(exist_ok=True)
    with ROOT.joinpath(".deps/runtime-build.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_SH)
        main()
