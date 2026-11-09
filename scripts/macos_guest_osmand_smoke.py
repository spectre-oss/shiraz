#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Load an unchanged OsmAnd native library through real ART and NativeBridge."""

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, environment
from guest_binding_evidence import augment_binding_report
from macos_guest_hardened_closure import (
    sign_closure,
    sign_staged_adhoc_closure,
    stage_closure,
    validate_closure,
    write_manifest,
)
from native_closure_inventory import validate_native_inventory

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/macos/m5/osmand-jni"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--bridge", type=Path, help="existing alternate bridge; local adhoc runner only")
    parser.add_argument(
        "--core",
        action="store_true",
        help="load all seven libraries and exercise real Core JNI",
    )
    parser.add_argument(
        "--mode", choices=("adhoc", "staged-adhoc", "hardened"), default="adhoc"
    )
    parser.add_argument(
        "--identity",
        default=os.environ.get("SHIRAZ_CODESIGN_IDENTITY"),
        help="public signing identity with a real TeamIdentifier; hardened mode only",
    )
    parser.add_argument(
        "--prepare-only",
        action="store_true",
        help="prepare unsigned private native closure without signing or ART execution",
    )
    args = parser.parse_args()
    if args.bridge and args.mode != "adhoc":
        parser.error("--bridge requires the unchanged local adhoc runner")
    if args.mode != "adhoc" and not args.core:
        parser.error("copied native modes require --core and the seven-library gate")
    if args.prepare_only and args.mode == "adhoc":
        parser.error("--prepare-only requires --mode hardened or staged-adhoc")
    work = WORK if args.mode == "adhoc" else WORK / args.mode
    if args.bridge:
        work = WORK / "alternate-bridge"
    apk = args.apk.resolve(strict=True)
    library = ROOT / "build/macos/m5/design/abi-table/libs/lib/arm64-v8a/libosmand.so"
    native_digests = {}
    with zipfile.ZipFile(apk) as archive:
        for name in (
            "libosmand.so",
            "libc++_shared.so",
            "libQt5Core.so",
            "libQt5Network.so",
            "libQt5Sql.so",
            "libOsmAndCoreWithJNI.so",
            "libandroidx.graphics.path.so",
        ):
            entry = archive.getinfo("lib/arm64-v8a/" + name)
            if not 0 < entry.file_size <= 256 * 1024 * 1024:
                raise SystemExit("invalid or oversized native library")
            sha = hashlib.sha256()
            with archive.open(entry) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    sha.update(chunk)
            digest = sha.hexdigest()
            if (
                hashlib.sha256((library.parent / name).read_bytes()).hexdigest()
                != digest
            ):
                raise SystemExit("library does not match the unchanged APK: " + name)
            native_digests[name] = digest
    digest = native_digests["libosmand.so"]
    work.mkdir(parents=True, exist_ok=True)
    map_file = work / "World_basemap_mini.obf"
    temporary = map_file.with_suffix(".pending")
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo("assets/World_basemap_mini.obf")
        if not 0 < entry.file_size <= 256 * 1024 * 1024:
            raise SystemExit("invalid or oversized basemap")
        sha = hashlib.sha256()
        with archive.open(entry) as stream, temporary.open("wb") as output:
            count = 0
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                count += len(chunk)
                if count > entry.file_size:
                    raise SystemExit("basemap exceeds declared size")
                sha.update(chunk)
                output.write(chunk)
        if count != entry.file_size:
            raise SystemExit("basemap truncated")
        map_digest = sha.hexdigest()
    temporary.replace(map_file)
    env = {
        key: value
        for key, value in environment(work).items()
        if not key.startswith(("DYLD_", "LD_", "LINKER_", "BIONIC_", "BERBERIS_"))
    }
    runtime = HOST
    vm = HOST / "bin/dalvikvm"
    bridge = args.bridge.resolve(strict=True) if args.bridge else ROOT / "build/macos/libshiraz-guest.so"
    observer = ROOT / "build/macos/m5/osmand-host-observer.so"
    native_paths = [ROOT / "build/macos", HOST / "lib64", library.parent]
    closure = None
    env.pop("SHIRAZ_GUEST_EGL_SHIM", None)
    env.pop("SHIRAZ_EGL_LIBRARY", None)
    if args.mode != "adhoc":
        extras = [
            observer,
            ROOT / "build/macos/libEGL_shim.so",
            ROOT / "build/macos/libEGL.dylib",
            ROOT / "build/macos/libGLESv2.dylib",
            ROOT / "build/macos/libandroid.so",
            ROOT / "build/macos/libshiraz-resources.so",
        ]
        closure = stage_closure(ROOT, HOST, work, env, extra_images=extras)
        runtime = Path(closure["tree"])
        vm = Path(closure["main"])
        bridge = Path(closure["bridge"])
        copied = closure["extra_images"]
        observer = Path(copied[str(observer)])
        native_paths = [runtime / "lib64", library.parent]
        env["ANDROID_ROOT"] = str(runtime)
        env["BOOTCLASSPATH"] = ":".join(
            str(runtime / "framework" / name) for name in JARS
        )
        env["SHIRAZ_GUEST_EGL_SHIM"] = copied[str(ROOT / "build/macos/libEGL_shim.so")]
        env["SHIRAZ_EGL_LIBRARY"] = copied[str(ROOT / "build/macos/libEGL.dylib")]
        closure.update(
            purpose="unchanged-osmand-core-jni",
            mode=args.mode,
            hardened_verified=False,
            route_check_only=args.mode == "staged-adhoc",
            guest_elf_sha256=native_digests,
        )
        closure["runtime_routes"].update(
            observer=str(observer),
            guest_elf_directory=str(library.parent),
            **{"java.library.path": ":".join(map(str, native_paths))},
            **{
                key: env[key] for key in ("SHIRAZ_GUEST_EGL_SHIM", "SHIRAZ_EGL_LIBRARY")
            },
        )
        write_manifest(closure)
        label = (
            "M5_OSMAND_CORE_HARDENED_PREPARED"
            if args.mode == "hardened"
            else "M5_OSMAND_CORE_STAGED_ADHOC_PREPARED"
        )
        print(
            label
            + " manifest="
            + str(runtime / "closure.json")
            + " signed=0 execution_verified=0",
            flush=True,
        )
        if args.prepare_only:
            return
        if args.mode == "hardened":
            if not args.identity:
                raise SystemExit(
                    "strict hardened Core ART needs --identity or SHIRAZ_CODESIGN_IDENTITY with a real TeamIdentifier; unsigned closure ready at "
                    + str(runtime / "closure.json")
                )
            sign_closure(
                closure,
                args.identity,
                ROOT / "tests/darwin/guest/jit.entitlements",
                env,
            )
        else:
            sign_staged_adhoc_closure(
                closure, ROOT / "tests/darwin/guest/jit.entitlements", env
            )
    elif args.core:
        env["SHIRAZ_GUEST_EGL_SHIM"] = str(ROOT / "build/macos/libEGL_shim.so")
        env["SHIRAZ_EGL_LIBRARY"] = str(ROOT / "build/macos/libEGL.dylib")
    classes = work / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    for command in (
        [
            "javac",
            "--release",
            "8",
            "-d",
            classes,
            ROOT / "tests/darwin/guest/OsmAndNativeLoad.java",
            ROOT / "tests/darwin/guest/OsmAndCoreNativeRoundTrip.java",
            ROOT / "tests/darwin/guest/AndroidxPathRoundTrip.java",
            ROOT / "tests/darwin/guest/NativeClosureInventory.java",
        ],
        [
            HOST / "bin/dx",
            "--dex",
            "--min-sdk-version=27",
            "--output=" + str(work / "classes.dex"),
            classes,
        ],
    ):
        subprocess.run(list(map(str, command)), env=env, check=True, timeout=120)
    jar = work / "fixture.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        entry = zipfile.ZipInfo("classes.dex")
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
        archive.writestr(entry, (work / "classes.dex").read_bytes())
    command = [
        vm,
        "-Xbootclasspath:"
        + ":".join(str(runtime / "framework" / name) for name in JARS),
        "-Xcheck:jni",
        "-Xusejit:false",
        "-Xint",
        "-Xnoimage-dex2oat",
        "-XX:NativeBridge=" + str(bridge),
        "-Xforce-nb-testing",
        "-Djava.library.path=" + ":".join(map(str, native_paths)),
        "-Dguest.osmand.library=" + str(library),
        "-Dguest.osmand.directory=" + str(library.parent),
        "-Dguest.osmand.core=" + str(args.core).lower(),
        "-Dguest.osmand.map=" + str(map_file),
        "-Dguest.osmand.observer=" + str(observer),
        "-cp",
        ":".join(map(str, [jar, ROOT / "build/shiraz-framework.jar", apk])),
        "OsmAndNativeLoad",
    ]
    if closure:
        command.insert(1, "-Dguest.native.closure=end-of-main")
    binding_raw = work / "bindings.raw.json"
    binding_output = work / "bindings.json"
    binding_raw.unlink(missing_ok=True)
    binding_output.unlink(missing_ok=True)
    env["SHIRAZ_GUEST_BINDING_REPORT"] = str(binding_raw)
    actual_command = list(map(str, command))
    bridge_sha256 = hashlib.sha256(bridge.read_bytes()).hexdigest()
    timed_out = False
    with (
        (work / "stdout.log").open("wb") as output,
        (work / "stderr.log").open("wb") as error,
        subprocess.Popen(
            actual_command, cwd=work, env=env, stdout=output, stderr=error
        ) as process,
    ):
        try:
            returncode = process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            returncode = process.wait()
    result = subprocess.CompletedProcess(
        actual_command,
        returncode,
        stdout=(work / "stdout.log").read_text(errors="replace"),
        stderr=(work / "stderr.log").read_text(errors="replace"),
    )
    (work / "run.log").write_text(result.stdout + result.stderr)
    phases = {}
    onloads = {}
    malformed_evidence = False
    phase_pattern = re.compile(
        r"^SHIRAZ_GUEST_PHASE module=(\S+) parse_ns=(\d+) map_ns=(\d+) "
        r"rewrite_ns=(\d+) bind_ns=(\d+) init_ns=(\d+) "
        r"expected_constructors=(\d+) executed_constructors=(\d+) initialized=([01])$"
    )
    fields = (
        "parse_ns",
        "map_ns",
        "rewrite_ns",
        "bind_ns",
        "init_ns",
        "expected_constructors",
        "executed_constructors",
        "initialized",
    )
    for line in result.stderr.splitlines():
        match = phase_pattern.fullmatch(line)
        if match:
            values = dict(zip(fields, map(int, match.groups()[1:]), strict=True))
            phases[match[1]] = values
            malformed_evidence |= (
                not values["initialized"]
                or values["expected_constructors"] != values["executed_constructors"]
            )
        elif line.startswith("SHIRAZ_GUEST_PHASE "):
            malformed_evidence = True
        match = re.fullmatch(
            r"SHIRAZ_GUEST_JNI_ONLOAD module=(\S+) version=0x([0-9a-fA-F]+)", line
        )
        if match:
            version = int(match[2], 16)
            malformed_evidence |= version not in (
                0x10002,
                0x10004,
                0x10006,
                0x10008,
            ) or (match[1] in onloads and onloads[match[1]] != version)
            onloads[match[1]] = version
        elif line.startswith("SHIRAZ_GUEST_JNI_ONLOAD "):
            malformed_evidence = True
    expected_modules = (
        set(native_digests) if args.core else {"libosmand.so", "libc++_shared.so"}
    )
    expected_onloads = (
        {"libosmand.so", "libQt5Core.so", "libandroidx.graphics.path.so"}
        if args.core
        else {"libosmand.so"}
    )
    malformed_evidence |= (
        set(phases) != expected_modules or set(onloads) != expected_onloads
    )
    binding_evidence = None
    binding_error = None
    try:
        binding_evidence = augment_binding_report(
            binding_raw,
            binding_output,
            native_digests,
            library.parent,
            expected_modules,
            native_bridge=bridge,
            bridge_sha256=bridge_sha256,
            verify_inventory=True,
            verify_all_relocations=True,
        )
    except (OSError, ValueError, TypeError, KeyError) as error:
        binding_error = str(error)
        malformed_evidence = True
    inventory = None
    inventory_error = None
    if closure:
        try:
            inventory = validate_native_inventory(
                result.stdout, closure, [vm, bridge, observer, *(
                    Path(closure["extra_images"][str(ROOT / "build/macos" / name)])
                    for name in ("libandroid.so", "libshiraz-resources.so")
                )]
            )
            (work / "native-closure.log").write_text(inventory["raw_report"])
        except (OSError, ValueError, KeyError, TypeError) as error:
            inventory_error = str(error)
            malformed_evidence = True
        closure.update(
            native_image_inventory=inventory,
            native_image_inventory_error=inventory_error,
        )
        write_manifest(closure)
    (work / "evidence.json").write_text(
        json.dumps(
            {
                "command": list(map(str, command)),
                "returncode": result.returncode,
                "mode": args.mode,
                "native_closure": str(runtime / "closure.json") if closure else None,
                "team_identifier": closure["team_identifier"] if closure else None,
                "route_check_only": args.mode == "staged-adhoc",
                "hardened_verified": False,
                "native_image_inventory": inventory,
                "native_image_inventory_error": inventory_error,
                "timed_out": timed_out,
                "libosmand_sha256": digest,
                "native_dependency_sha256": native_digests,
                "basemap_sha256": map_digest,
                "app_execution": False,
                "core_requested": args.core,
                "library_phase_metrics": phases,
                "jni_onload_versions": onloads,
                "phase_evidence_valid": not malformed_evidence,
                "binding_evidence": binding_evidence,
                "binding_evidence_error": binding_error,
            },
            indent=2,
        )
        + "\n"
    )
    print(result.stdout + result.stderr, end="")
    if (
        result.returncode
        or timed_out
        or malformed_evidence
        or "M5_OSMAND_NATIVE_LOAD_PASS " not in result.stdout
        or "M5_OSMAND_NATIVE_MAP_PASS " not in result.stdout
        or "SHIRAZ_GUEST_ELF_LOADED" not in result.stderr
        or "JNI_OnLoad completed" not in result.stderr
        or (args.core and "M5_OSMAND_CORE_JNI_PASS " not in result.stdout)
        or (args.core and "M5_OSMAND_ALL_LIBRARIES_PASS " not in result.stdout)
        or (args.core and "M5_ANDROIDX_PATH_JNI_PASS " not in result.stdout)
    ):
        raise SystemExit(
            "unchanged OsmAnd native load failed; inspect " + str(work / "run.log")
        )
    if closure:
        validate_closure(closure, env)
        for item in closure["images"]:
            if (
                hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest()
                != item["signed_sha256"]
            ):
                raise SystemExit(
                    "copied native image changed during Core JNI execution"
                )
        closure.update(
            execution_verified=True,
            core_jni_verified=True,
            hardened_verified=args.mode == "hardened",
        )
        write_manifest(closure)
        evidence_path = work / "evidence.json"
        evidence = json.loads(evidence_path.read_text())
        evidence["hardened_verified"] = args.mode == "hardened"
        evidence_path.write_text(json.dumps(evidence, indent=2) + "\n")
    marker = "M5_OSMAND_CORE_MODE_PASS" if args.core else "M5_OSMAND_NATIVE_MODE_PASS"
    print(
        marker
        + " mode="
        + args.mode
        + " hardened_verified="
        + str(int(args.mode == "hardened")),
        flush=True,
    )


if __name__ == "__main__":
    main()
