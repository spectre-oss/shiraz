#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run a named Java framework regression on both OpenJDK and pinned ART."""

import argparse
import fcntl
import json
import re
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, NATIVE, environment

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("class_name")
    parser.add_argument("--marker", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", args.class_name):
        parser.error("expected an unqualified Java class name")
    work = ROOT / "build/java-framework-smoke" / args.class_name
    work.mkdir(parents=True, exist_ok=True)
    with (work / ".lock").open("a") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        (work / "result.json").unlink(missing_ok=True)
        classes = work / "classes"
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir()
        core = ROOT / "context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar"
        classpath = str(ROOT / "build/framework/classes") + ":" + str(core)
        source = ROOT / "tests/java" / (args.class_name + ".java")
        subprocess.run(["javac", "--release", "8", "-cp", classpath, "-d", str(classes), str(source)], check=True)
        dex = work / "classes.dex"
        env = environment(work)
        subprocess.run([str(HOST / "bin/dx"), "--dex", "--min-sdk-version=27", "--output=" + str(dex), str(classes)], env=env, check=True)
        jar = work / "smoke.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            entry = zipfile.ZipInfo("classes.dex")
            padding = (-(30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
            archive.writestr(entry, dex.read_bytes())
        vm = HOST / "bin/dalvikvm64"
        if not vm.is_file():
            vm = HOST / "bin/dalvikvm"
        commands = [
            ("openjdk", ["java", "-Xcheck:jni", "-Djava.library.path=" + str(NATIVE / "jvm-native" if DARWIN else NATIVE), "-cp", str(classes) + ":" + classpath, args.class_name]),
            ("art", [str(vm), "-Xbootclasspath:" + ":".join(str(HOST / "framework" / name) for name in JARS), "-Xcheck:jni", "-Xusejit:false", "-Xint", "-Xnoimage-dex2oat", "-Djava.library.path=" + str(NATIVE), "-cp", str(ROOT / "build/shiraz-framework.jar") + ":" + str(jar), args.class_name]),
        ]
        for runtime, command in commands:
            result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=30, check=False)
            (work / (runtime + "-stdout.log")).write_text(result.stdout)
            (work / (runtime + "-stderr.log")).write_text(result.stderr)
            if result.returncode or args.marker not in result.stdout.splitlines():
                raise SystemExit(f"{args.class_name} failed in {runtime}; inspect {work}")
            print(f"{args.marker} {runtime}")
        (work / "result.json").write_text(json.dumps({"runtimes": ["openjdk", "art"], "marker": args.marker, "component_only": True}, indent=2) + "\n")


if __name__ == "__main__":
    main()
