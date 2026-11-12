#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise PropertyValuesHolder and holder-based animators on OpenJDK and ART."""

import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, NATIVE, environment

ROOT = Path(__file__).resolve().parents[1]
MARKER = 'PROPERTY_VALUES_HOLDER_OK'
work = ROOT / 'build/property-values-holder-smoke'
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
framework = ROOT / 'build/framework/classes'
core = (
    ROOT
    / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
)
classpath = str(framework) + ':' + str(core)
subprocess.run(
    [
        'javac',
        '--release',
        '8',
        '-cp',
        classpath,
        '-d',
        str(classes),
        str(ROOT / 'tests/java/PropertyValuesHolderSmoke.java'),
    ],
    check=True,
)
# macOS JNI libraries are Mach-O files with Android .so names, which OpenJDK cannot load.
commands = (
    []
    if DARWIN
    else [
        (
            'openjdk',
            [
                'java',
                '-Xcheck:jni',
                '-Djava.library.path=' + str(NATIVE),
                '-cp',
                str(classes) + ':' + classpath,
                'PropertyValuesHolderSmoke',
            ],
        )
    ]
)
dex = work / 'classes.dex'
subprocess.run(
    [str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)],
    env=environment(work),
    check=True,
)
jar = work / 'smoke.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    entry = zipfile.ZipInfo('classes.dex')
    padding = (-(30 + len(entry.filename) + 4)) % 4
    entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
    archive.writestr(entry, dex.read_bytes())
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
commands.append(
    (
        'art',
        [
            str(vm),
            '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
            '-Xcheck:jni',
            '-Xusejit:false',
            '-Xint',
            '-Xnoimage-dex2oat',
            '-Djava.library.path=' + str(NATIVE),
            '-cp',
            str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar),
            'PropertyValuesHolderSmoke',
        ],
    )
)
for runtime, command in commands:
    result = subprocess.run(
        command, env=environment(work), capture_output=True, text=True, timeout=60
    )
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or MARKER not in result.stdout.splitlines():
        raise SystemExit(
            f'PropertyValuesHolder smoke failed in {runtime}; inspect {work}/{runtime}-stderr.log'
        )
    print(MARKER + ' ' + runtime)
