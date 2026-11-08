#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the unsigned real-device capture fixture with the pinned Android toolchain."""
import shutil
import subprocess
from pathlib import Path

from build_api_probe import append_dex
from build_apk_fixture import TOOLS, tools

from shiraz.host_layout import host_out_dir

ROOT = Path(__file__).resolve().parents[1]


def main():
    tools()
    work = ROOT / "build/media-probe"
    classes = work / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    source = ROOT / "tests/media-probe"
    apk = work / "probe.apk"
    subprocess.run([str(TOOLS / "aapt2"), "link", "-o", str(apk), "--manifest", str(source / "AndroidManifest.xml"), "-I", str(TOOLS / "android.jar")], check=True)
    subprocess.run(["javac", "--release", "8", "-cp", str(TOOLS / "android.jar"), "-d", str(classes), str(source / "ProbeActivity.java")], check=True)
    dex = work / "classes.dex"
    subprocess.run([str(host_out_dir(ROOT / "context/art_standalone") / "bin/dx"), "--dex", "--min-sdk-version=27", "--output=" + str(dex), str(classes)], check=True)
    append_dex(apk, dex)
    print(apk.relative_to(ROOT))


if __name__ == "__main__":
    main()
