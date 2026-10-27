#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Reuse the AppImage's private loader, libraries and Python for the web deb."""

import argparse
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("appdir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    appdir = args.appdir.resolve()
    installer = appdir / "usr/share/shiraz/installer"
    libraries = appdir / "usr/lib"
    stdlibs = sorted((installer / "lib").glob("python3.*"))
    if len(stdlibs) != 1 or not (libraries / "ld-linux-x86-64.so.2").is_file():
        parser.error("AppDir has no private loader or a unique Python standard library")
    if args.output.exists():
        shutil.rmtree(args.output)
    (args.output / "bin").mkdir(parents=True)
    shutil.copy2(installer / "python", args.output / "bin/python3")
    shutil.copytree(libraries, args.output / "lib", symlinks=True)
    shutil.copytree(stdlibs[0], args.output / "lib" / stdlibs[0].name,
                    symlinks=True)
    shutil.copy2(appdir / "usr/share/shiraz/cacerts.jks", args.output / "cacerts")
    if not (args.output / "lib" / stdlibs[0].name / "LICENSE.txt").is_file():
        parser.error("Python license is missing from AppDir")


if __name__ == "__main__":
    main()
