#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check switch-interpreter field access (cached fast path and fallback) on every heap GC."""

import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]
MARKER = 'INTERPRETER_FIELD_OK sum=-4587696900259082784'
work = ROOT / 'build/interpreter-field-smoke'
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
source = ROOT / 'tests/java/InterpreterFieldSmoke.java'
subprocess.run(['javac', '--release', '8', '-d', str(classes), str(source)], check=True)
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
# Moving collectors relocate objects between rounds; the cache keys field offsets, not addresses.
for gc in ('CMS', 'MS', 'SS', 'GSS'):
    result = subprocess.run(
        [
            str(vm),
            '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
            '-Xusejit:false',
            '-Xint',
            '-Xnoimage-dex2oat',
            '-Xcheck:jni',
            '-Xgc:' + gc,
            '-cp',
            str(jar),
            'InterpreterFieldSmoke',
        ],
        env=environment(work),
        capture_output=True,
        text=True,
        timeout=300,
    )
    (work / (gc + '-stdout.log')).write_text(result.stdout)
    (work / (gc + '-stderr.log')).write_text(result.stderr)
    if result.returncode or MARKER not in result.stdout:
        raise SystemExit(
            'Interpreter field smoke failed with -Xgc:'
            + gc
            + '; inspect build/interpreter-field-smoke/'
            + gc
            + '-stderr.log'
        )
    print(MARKER + ' gc=' + gc)
