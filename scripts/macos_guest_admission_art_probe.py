#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run a single guest JNI load in a disposable, confined ART process."""

import argparse
import resource
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, ROOT, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--fixture", action="store_true")
    args = parser.parse_args()
    library = args.library.resolve(strict=True)
    apk = args.apk.resolve(strict=True) if args.apk else None
    parent = ROOT / "build/macos/m5/isolated-art"
    parent.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="run-", dir=parent))
    base = environment(work)
    allow = {"ANDROID_ROOT", "ANDROID_DATA", "BOOTCLASSPATH", "XDG_CACHE_HOME"}
    env = {key: value for key, value in base.items() if key in allow}
    env.update(
        PATH="/usr/bin:/bin:/usr/sbin:/sbin", HOME=str(work / "home"), TMPDIR=str(work / "tmp")
    )
    classes = work / "classes"
    classes.mkdir()
    subprocess.run(
        [
            "javac",
            "--release",
            "8",
            "-d",
            str(classes),
            str(ROOT / "tests/darwin/guest/IsolatedNativeLoad.java"),
        ],
        env=env,
        check=True,
        timeout=60,
    )
    dex = work / "classes.dex"
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
        timeout=60,
    )
    jar = work / "harness.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        entry = zipfile.ZipInfo("classes.dex")
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
        archive.writestr(entry, dex.read_bytes())
    classpath = str(jar)
    if apk:
        framework = ROOT / "build/shiraz-framework.jar"
        if not framework.is_file():
            raise SystemExit("Shiraz framework DEX is missing")
        classpath += ":" + str(framework) + ":" + str(apk)
    command = [
        str(HOST / "bin/dalvikvm"),
        "-Xbootclasspath:" + ":".join(str(HOST / "framework" / name) for name in JARS),
        "-Xcheck:jni",
        "-Xusejit:false",
        "-Xint",
        "-Xnoimage-dex2oat",
        "-XX:NativeBridge=" + str(ROOT / "build/macos/m5/libshiraz-guest-isolated-probe.so"),
        "-Xforce-nb-testing",
        "-Djava.library.path=" + str(HOST / "lib64"),
        "-cp",
        classpath,
        "IsolatedNativeLoad",
        str(library),
        "fixture" if args.fixture else "target",
    ]

    def no_core():
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))

    try:
        result = subprocess.run(
            command,
            cwd=work,
            env=env,
            capture_output=True,
            text=True,
            errors="replace",
            timeout=30,
            preexec_fn=no_core,
        )
        output = result.stdout + result.stderr
        (work / "result.log").write_text(output)
        print(
            "M5_ISOLATED_ART_RESULT returncode="
            + str(result.returncode)
            + " log="
            + str(work / "result.log")
        )
        print(output[-16000:])
        expected = "M5_ISOLATED_ART_LOAD_PASS fixture=" + str(args.fixture).lower()
        if (
            result.returncode
            or expected not in output
            or "SHIRAZ_ISOLATED_SANDBOX_ACTIVE" not in output
            or "SHIRAZ_GUEST_JNI_ONLOAD" not in output
        ):
            raise SystemExit(1)
    except subprocess.TimeoutExpired:
        print("M5_ISOLATED_ART_TIMEOUT log_dir=" + str(work))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
