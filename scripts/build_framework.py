#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the initial Android API subset and host activity entry point to DEX."""
from pathlib import Path
import fcntl
import re
import shutil
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    dx = ROOT / 'context/art_standalone/out/host/linux-x86/bin/dx'
    if not dx.is_file():
        raise SystemExit('Host dx is missing. Run make runtime-art before building the framework.')
    build = ROOT / 'build/framework'
    classes = build / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    version = (ROOT / 'VERSION').read_text().strip()
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        raise SystemExit('Invalid Shiraz VERSION')
    generated = build / 'generated/shiraz/runtime/RuntimeVersion.java'
    generated.parent.mkdir(parents=True, exist_ok=True)
    generated.write_text('// Generated from VERSION.\npackage shiraz.runtime;\n'
                         'public final class RuntimeVersion { private RuntimeVersion() {} '
                         'public static String get() { return "' + version + '"; } }\n')
    sources = sorted((ROOT / 'java').rglob('*.java')) + [generated]
    core_classes = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    if not core_classes.is_file():
        raise SystemExit('Host ART Java interfaces are missing. Run make runtime-art.')
    subprocess.run(['javac', '--release', '8', '-cp', str(core_classes), '-d', str(classes), *map(str, sources)], check=True)
    dex = build / 'classes.dex'
    subprocess.run([str(dx), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True)
    output = ROOT / 'build/shiraz-framework.jar'
    temporary = output.with_suffix('.jar.tmp')
    with zipfile.ZipFile(temporary, 'w') as archive:
        # ART maps stored DEX directly only when its local-file payload is aligned.
        # Leave the original APK untouched; this archive contains our own framework.
        entry = zipfile.ZipInfo('classes.dex')
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    temporary.replace(output)


if __name__ == '__main__':
    (ROOT / 'build').mkdir(exist_ok=True)
    with (ROOT / 'build/.framework.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        main()
