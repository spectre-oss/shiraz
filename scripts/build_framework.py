#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the initial Android API subset and host activity entry point to DEX."""

import fcntl
import re
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.android_attributes import ANDROID_ATTRIBUTE_NAMES  # noqa: E402
from shiraz.host_layout import host_out_dir  # noqa: E402


def main():
    # out/host/linux-x86 on Linux, out/host/darwin-x86 on macOS.
    dx = host_out_dir(ROOT / 'context/art_standalone') / 'bin/dx'
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
    generated.write_text(
        '// Generated from VERSION.\npackage shiraz.runtime;\n'
        'public final class RuntimeVersion { private RuntimeVersion() {} '
        'public static String get() { return "' + version + '"; } }\n'
    )
    attributes = generated.with_name('FrameworkAttributeNames.java')
    cases = []
    for ident, name in sorted(ANDROID_ATTRIBUTE_NAMES.items()):
        if ident >> 24 != 1 or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', name):
            raise SystemExit('Invalid framework attribute name fact')
        cases.append(f'case 0x{ident:08x}: return "{name}";')
    attributes.write_text(
        '// Generated from shiraz.android_attributes; see that table for provenance.\n'
        'package shiraz.runtime;\nfinal class FrameworkAttributeNames {\n'
        'private FrameworkAttributeNames() {}\n'
        'static String name(int id) { switch(id) {\n'
        + '\n'.join(cases)
        + '\ndefault: return null; } }\n}\n'
    )
    all_sources = sorted((ROOT / 'java').rglob('*.java')) + [generated, attributes]
    java9_shim_rel = {
        Path('java/java/lang/ref/Cleaner.java'),
        Path('java/java/util/concurrent/Flow.java'),
        Path('java/android/system/SystemCleaner.java'),
        Path('java/android/service/controls/ControlsProviderService.java'),
    }
    sources = [
        p
        for p in all_sources
        if not (p.is_relative_to(ROOT) and p.relative_to(ROOT) in java9_shim_rel)
    ]
    java9_sources = [
        p for p in all_sources if p.is_relative_to(ROOT) and p.relative_to(ROOT) in java9_shim_rel
    ]
    java_lib_base = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES'
    core_oj = java_lib_base / 'core-oj-hostdex_intermediates/classes.jar'
    core_classes = java_lib_base / 'core-libart-hostdex_intermediates/classes.jar'
    if not core_oj.is_file() or not core_classes.is_file():
        raise SystemExit('Host ART Java interfaces are missing. Run make runtime-art.')
    subprocess.run(
        [
            'javac',
            '--release',
            '8',
            '-Xlint:-options',
            '-cp',
            str(core_classes),
            '-d',
            str(classes),
            *map(str, sources),
        ],
        check=True,
    )
    if java9_sources:
        subprocess.run(
            [
                'javac',
                '-source',
                '8',
                '-target',
                '8',
                '-Xlint:-options',
                '-bootclasspath',
                f'{core_oj}:{core_classes}:{classes}',
                '-d',
                str(classes),
                *map(str, java9_sources),
            ],
            check=True,
        )
    icu_overlay_src = ROOT / 'optional-java/icu-inner-overlay'
    if icu_overlay_src.is_dir():
        icu_sources = sorted(icu_overlay_src.rglob('*.java'))
        if icu_sources:
            icu_classes = build / 'icu-overlay-classes'
            if icu_classes.exists():
                shutil.rmtree(icu_classes)
            icu_classes.mkdir(parents=True, exist_ok=True)
            subprocess.run(
                [
                    'javac',
                    '--release',
                    '8',
                    '-Xlint:-options',
                    '-cp',
                    str(classes),
                    '-d',
                    str(icu_classes),
                    *map(str, icu_sources),
                ],
                check=True,
            )
            for inner_cls in sorted(icu_classes.rglob('*$*.class')):
                dest = classes / inner_cls.relative_to(icu_classes)
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(inner_cls, dest)
    dex_dir = build / 'dex'
    if dex_dir.exists():
        shutil.rmtree(dex_dir)
    dex_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            str(dx),
            '--dex',
            '--core-library',
            '--multi-dex',
            '--min-sdk-version=27',
            '--output=' + str(dex_dir),
            str(classes),
        ],
        check=True,
    )
    dex_files = sorted(
        dex_dir.glob('classes*.dex'),
        key=lambda p: 1 if p.name == 'classes.dex' else int(p.stem[7:]),
    )
    if not dex_files:
        raise SystemExit('dx produced no framework DEX files')
    shutil.copyfile(dex_files[0], build / 'classes.dex')
    output = ROOT / 'build/shiraz-framework.jar'
    temporary = output.with_suffix('.jar.tmp')
    with zipfile.ZipFile(temporary, 'w') as archive:
        # ART maps stored DEX directly only when each local-file payload is 4-byte aligned.
        # Leave the original APK untouched; this archive contains our own framework.
        for dex in dex_files:
            entry = zipfile.ZipInfo(dex.name)
            entry.compress_type = zipfile.ZIP_STORED
            offset = archive.fp.tell()
            padding = (-(offset + 30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
            archive.writestr(entry, dex.read_bytes())
    temporary.replace(output)


if __name__ == '__main__':
    (ROOT / 'build').mkdir(exist_ok=True)
    with (ROOT / 'build/.framework.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        main()
