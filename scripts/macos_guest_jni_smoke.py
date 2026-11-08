#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run genuine guest ELF JNI through ART, including hardened allow-jit-only mode."""

import argparse
import fcntl
import hashlib
import json
import os
import plistlib
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, environment
from macos_guest_hardened_closure import (
    sign_closure,
    sign_staged_adhoc_closure,
    stage_closure,
    write_manifest,
)
from native_closure_inventory import validate_native_inventory

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/macos/m5/jni"
MARKER = "M5_ART_JNI_PASS "


def run(command, env, cwd=WORK):
    subprocess.run(list(map(str, command)), env=env, cwd=cwd, check=True, timeout=120)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode", choices=("both", "adhoc", "hardened", "staged-adhoc"), default="both"
    )
    parser.add_argument(
        "--identity",
        default=os.environ.get("SHIRAZ_CODESIGN_IDENTITY"),
        help="public codesign identity for one actual TeamIdentifier; never ad-hoc",
    )
    parser.add_argument(
        "--prepare-only",
        action="store_true",
        help="stage strict native closure and manifest without signing or execution",
    )
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    env = environment(WORK)
    env = {
        k: v
        for k, v in env.items()
        if not k.startswith(("DYLD_", "LD_", "LINKER_", "BIONIC_", "BERBERIS_"))
    }
    if args.prepare_only:
        closure = stage_closure(ROOT, HOST, WORK, env)
        label = "M5_ART_HARDENED_PREPARED"
        if args.mode == "staged-adhoc":
            closure.update(
                mode="staged-adhoc", hardened_verified=False, route_check_only=True
            )
            write_manifest(closure)
            label = "M5_ART_STAGED_ADHOC_PREPARED"
        print(
            label
            + " manifest="
            + str(Path(closure["tree"]) / "closure.json")
            + " signed=0 execution_verified=0"
        )
        return
    classes = WORK / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    run(
        [
            "javac",
            "--release",
            "8",
            "-d",
            classes,
            ROOT / "tests/darwin/guest/GuestJniFixture.java",
            ROOT / "tests/darwin/guest/NativeClosureInventory.java",
            ROOT / "tests/darwin/guest/fixture/java/android/graphics/Bitmap.java",
        ],
        env,
    )
    dex = WORK / "classes.dex"
    run(
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
    modes = ("adhoc", "hardened") if args.mode == "both" else (args.mode,)
    for mode in modes:
        io_directory = Path(tempfile.mkdtemp(prefix=f"native-io-{mode}-", dir=WORK))
        io_file = io_directory / "roundtrip.bin"
        vm = HOST / "bin/dalvikvm"
        runtime_host = HOST
        bridge = ROOT / "build/macos/libshiraz-guest.so"
        host_fixture = ROOT / "build/macos/m5/host-fixture.so"
        mode_env = dict(env)
        closure = None
        if mode in ("hardened", "staged-adhoc"):
            closure = stage_closure(ROOT, HOST, WORK, env)
            manifest = Path(closure["tree"]) / "closure.json"
            label = (
                "M5_ART_HARDENED_PREPARED"
                if mode == "hardened"
                else "M5_ART_STAGED_ADHOC_PREPARED"
            )
            print(
                label + " manifest=" + str(manifest) + " signed=0 execution_verified=0",
                flush=True,
            )
            if mode == "hardened":
                if not args.identity:
                    raise SystemExit(
                        "strict hardened ART needs a public --identity or SHIRAZ_CODESIGN_IDENTITY "
                        "with a real TeamIdentifier; unsigned closure is ready at "
                        + str(manifest)
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
            runtime_host = Path(closure["tree"])
            vm = Path(closure["main"])
            bridge = Path(closure["bridge"])
            host_fixture = Path(closure["host_fixture"])
            mode_env.pop("SHIRAZ_GUEST_EGL_SHIM", None)
            mode_env.pop("SHIRAZ_EGL_LIBRARY", None)
            mode_env["ANDROID_ROOT"] = str(runtime_host)
            mode_env["BOOTCLASSPATH"] = ":".join(
                str(runtime_host / "framework" / name) for name in JARS
            )
        signature = subprocess.run(
            ["codesign", "--display", "--verbose=2", str(vm)],
            env=env,
            capture_output=True,
            text=True,
            check=True,
        ).stderr
        print(signature, end="")
        if mode == "hardened":
            flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
            if flags is None or not int(flags[1], 16) & 0x10000:
                raise SystemExit(
                    "hardened gate requires the actual runtime signing flag"
                )
            entitlements = subprocess.run(
                ["codesign", "--display", "--entitlements", "-", "--xml", str(vm)],
                env=env,
                capture_output=True,
                check=True,
            ).stdout
            if plistlib.loads(entitlements) != {
                "com.apple.security.cs.allow-jit": True
            }:
                raise SystemExit("hardened gate permits only allow-jit")
        command = [
            vm,
            "-Xbootclasspath:"
            + ":".join(str(runtime_host / "framework" / name) for name in JARS),
            "-Xcheck:jni",
            "-Xusejit:false",
            "-Xint",
            "-Xnoimage-dex2oat",
            "-XX:NativeBridge=" + str(bridge),
            "-Xforce-nb-testing",
            "-Djava.library.path=" + str(runtime_host / "lib64"),
            "-Dguest.fixture=" + str(ROOT / "build/macos/m5/jni-fixture.so"),
            "-Dguest.host.fixture=" + str(host_fixture),
            "-Dguest.io.file=" + str(io_file),
            "-cp",
            jar,
            "GuestJniFixture",
        ]
        if closure:
            command.insert(1, "-Dguest.native.closure=end-of-main")
        result = subprocess.run(
            list(map(str, command)),
            cwd=WORK,
            env=mode_env,
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
        (WORK / (mode + ".log")).write_text(result.stdout + result.stderr)
        (WORK / (mode + ".command.json")).write_text(
            json.dumps(list(map(str, command)), indent=2) + "\n"
        )
        inventory = None
        inventory_error = None
        if closure:
            try:
                inventory = validate_native_inventory(
                    result.stdout, closure, [vm, bridge, host_fixture]
                )
                (WORK / (mode + ".native-closure.log")).write_text(
                    inventory["raw_report"]
                )
            except (OSError, ValueError, KeyError, TypeError) as error:
                inventory_error = str(error)
            closure.update(
                native_image_inventory=inventory,
                native_image_inventory_error=inventory_error,
            )
            write_manifest(closure)
        evidence = {
            "mode": mode,
            "hardened_verified": False,
            "execution_verified": False,
            "native_image_inventory": inventory,
            "native_image_inventory_error": inventory_error,
            "route_check_only": mode == "staged-adhoc",
            "returncode": result.returncode,
            "native_closure": str(Path(closure["tree"]) / "closure.json")
            if closure
            else None,
            "team_identifier": closure["team_identifier"] if closure else None,
            "signature": signature,
            "sha256": {
                str(path.relative_to(ROOT)): hashlib.sha256(
                    path.read_bytes()
                ).hexdigest()
                for path in (
                    vm,
                    jar,
                    ROOT / "build/macos/m5/jni-fixture.so",
                    bridge,
                    host_fixture,
                )
            },
        }
        (WORK / (mode + ".result.json")).write_text(
            json.dumps(evidence, indent=2) + "\n"
        )
        print(result.stdout, end="")
        print(result.stderr, end="")
        if (
            result.returncode
            or inventory_error
            or MARKER not in result.stdout
            or "SHIRAZ_GUEST_ELF_LOADED constructors=1" not in result.stderr
        ):
            raise SystemExit(
                f"{mode} guest ART JNI failed (exit {result.returncode}); see {WORK / (mode + '.log')}"
            )
        if (
            "M5_ART_BITMAP_PASS shadow_env=1 rgba=1 nested=1 writeback=1"
            not in result.stdout
        ):
            raise SystemExit("guest libjnigraphics JNI/pixel gate failed")
        rewritten = re.search(
            r"SHIRAZ_GUEST_TPIDR_REWRITTEN sites=(\d+)", result.stderr
        )
        if rewritten is None or int(rewritten[1]) == 0:
            raise SystemExit(
                "JNI gate requires genuine stack-protected guest instruction rewriting"
            )
        if any(io_directory.iterdir()):
            raise SystemExit("native I/O fixture left unexpected files")
        io_directory.rmdir()
        if closure:
            closure.update(
                execution_verified=True,
                hardened_verified=mode == "hardened",
                mode=mode,
                route_check_only=mode == "staged-adhoc",
                command=list(map(str, command)),
                returncode=result.returncode,
            )
            write_manifest(closure)
        evidence.update(execution_verified=True, hardened_verified=mode == "hardened")
        (WORK / (mode + ".result.json")).write_text(
            json.dumps(evidence, indent=2) + "\n"
        )
        suffix = (
            " hardened_verified=0 route_check_only=1" if mode == "staged-adhoc" else ""
        )
        print(f"M5_ART_JNI_MODE_PASS {mode}" + suffix)


if __name__ == "__main__":
    ROOT.joinpath(".deps").mkdir(exist_ok=True)
    with ROOT.joinpath(".deps/runtime-build.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_SH)
        main()
