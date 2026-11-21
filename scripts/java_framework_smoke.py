#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run a named Java framework regression on both OpenJDK and pinned ART."""

import argparse
import fcntl
import hashlib
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
    parser.add_argument("--runtime", choices=("both", "openjdk", "art"), default="both")
    parser.add_argument(
        "--timeout", type=int, default=30, help="per-runtime timeout in seconds (1..3600)"
    )
    parser.add_argument("--framework-res", type=Path, help="framework resource APK for UI tests")
    args = parser.parse_args()
    if not 1 <= args.timeout <= 3600:
        parser.error("timeout must be between 1 and 3600 seconds")
    if not re.fullmatch(
        r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*", args.class_name
    ):
        parser.error("expected a Java class name")
    work = ROOT / "build/java-framework-smoke" / args.class_name
    work.mkdir(parents=True, exist_ok=True)
    with (work / ".lock").open("a") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        (work / "result.json").unlink(missing_ok=True)
        classes = work / "classes"
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir()
        core = (
            ROOT
            / "context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar"
        )
        # Share the build lock only while copying; the guest owns its snapshot
        # for the entire run, including long real-time scheduling acceptance.
        framework_classes = work / "framework-classes"
        framework_jar = work / "framework.jar"
        with (ROOT / "build/.framework.lock").open("a") as framework_lock:
            fcntl.flock(framework_lock.fileno(), fcntl.LOCK_SH)
            if framework_classes.exists():
                shutil.rmtree(framework_classes)
            shutil.copytree(ROOT / "build/framework/classes", framework_classes)
            shutil.copyfile(ROOT / "build/shiraz-framework.jar", framework_jar)
        class_digest = hashlib.sha256()
        for path in sorted(framework_classes.rglob("*.class")):
            class_digest.update(path.relative_to(framework_classes).as_posix().encode() + b"\0")
            class_digest.update(hashlib.sha256(path.read_bytes()).digest())
        snapshot = {
            "art_jar_sha256": hashlib.sha256(framework_jar.read_bytes()).hexdigest(),
            "jvm_classes_sha256": class_digest.hexdigest(),
        }
        resource_args = []
        if args.framework_res is not None:
            resources = work / "framework-res.apk"
            shutil.copyfile(args.framework_res, resources)
            snapshot['resources_sha256'] = hashlib.sha256(resources.read_bytes()).hexdigest()
            resource_args = ['-Dshiraz.frameworkResources=' + str(resources)]
        (work / "framework-snapshot.json").write_text(json.dumps(snapshot, indent=2) + "\n")
        classpath = str(framework_classes) + ":" + str(core)
        source = ROOT / "tests/java" / (args.class_name.rsplit(".", 1)[-1] + ".java")
        subprocess.run(
            ["javac", "--release", "8", "-cp", classpath, "-d", str(classes), str(source)],
            check=True,
        )
        dex = work / "classes.dex"
        env = environment(work)
        subprocess.run(
            [
                str(HOST / "bin/dx"),
                "--dex",
                "--min-sdk-version=27",
                "--output=" + str(dex),
                str(classes),
            ],
            env=env,
            check=True,
        )
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
            (
                "openjdk",
                [
                    "java",
                    "-Xcheck:jni",
                    "-Djava.library.path=" + str(NATIVE / "jvm-native" if DARWIN else NATIVE),
                    "-cp",
                    str(classes) + ":" + classpath,
                    args.class_name,
                ],
            ),
            (
                "art",
                [
                    str(vm),
                    "-Xbootclasspath:" + ":".join(str(HOST / "framework" / name) for name in JARS),
                    "-Xcheck:jni",
                    "-Xusejit:false",
                    "-Xint",
                    "-Xnoimage-dex2oat",
                    "-Djava.library.path=" + str(NATIVE) + ":" + str(HOST / "lib64"),
                    "-cp",
                    str(framework_jar) + ":" + str(jar),
                    args.class_name,
                ],
            ),
        ]
        selected = [
            (runtime, command)
            for runtime, command in commands
            if args.runtime == "both" or runtime == args.runtime
        ]
        for runtime, command in selected:
            command[1:1] = resource_args
            stdout = work / (runtime + "-stdout.log")
            stderr = work / (runtime + "-stderr.log")
            with stdout.open("w") as out, stderr.open("w") as err:
                try:
                    result = subprocess.run(
                        command, env=env, stdout=out, stderr=err, timeout=args.timeout, check=False
                    )
                except subprocess.TimeoutExpired:
                    raise SystemExit(
                        f"{args.class_name} timed out in {runtime}; inspect {work}"
                    ) from None
            if (
                result.returncode
                or args.marker not in stdout.read_text(errors="replace").splitlines()
            ):
                raise SystemExit(f"{args.class_name} failed in {runtime}; inspect {work}")
            print(f"{args.marker} {runtime}", flush=True)
        (work / "result.json").write_text(
            json.dumps(
                {
                    "runtimes": [runtime for runtime, _ in selected],
                    "marker": args.marker,
                    "component_only": True,
                    "framework": snapshot,
                },
                indent=2,
            )
            + "\n"
        )


if __name__ == "__main__":
    main()
