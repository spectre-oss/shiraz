#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise bitmap sampling and native argument validation on OpenJDK and actual ART."""
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/bitmap-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Bitmap smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes), str(ROOT / 'tests/java/BitmapSmoke.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/BitmapFixture.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'BitmapSmoke'])]
dex = work / 'classes.dex'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], env=environment(work), check=True)
jar = work / 'smoke.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    entry = zipfile.ZipInfo('classes.dex')
    padding = (-(30 + len(entry.filename) + 4)) % 4
    entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
    archive.writestr(entry, dex.read_bytes())
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
commands.append(('art', [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                        '-Xcheck:jni', '-Xusejit:false', '-Xint', '-Xnoimage-dex2oat', '-Xcompiler:' + str(HOST / 'bin/dex2oat'),
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'BitmapSmoke']))
marker = 'BITMAP_NATIVE_BOUNDS_FILTERING_AND_ALIASING_OK'
timings = {}
sparse_timings = {}
identity_timings = {}
for runtime, command in commands:
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('Bitmap smoke failed in ' + runtime + '; inspect build/bitmap-smoke/' + runtime + '-stderr.log')
    timing = [line.split('=', 1)[1] for line in result.stdout.splitlines() if line.startswith('BITMAP_FILTER_720X1280_MEDIAN_NS=')]
    if len(timing) != 1 or int(timing[0]) <= 0:
        raise SystemExit('Missing native bitmap timing evidence in ' + runtime)
    timings[runtime] = int(timing[0])
    sparse = [line.split('=', 1)[1] for line in result.stdout.splitlines() if line.startswith('BITMAP_LARGE_CLIPPED_MEDIAN_NS=')]
    if len(sparse) != 1 or int(sparse[0]) <= 0:
        raise SystemExit('Missing clipped bitmap timing evidence in ' + runtime)
    sparse_timings[runtime] = int(sparse[0])
    identity = [line.split('=', 1)[1] for line in result.stdout.splitlines() if line.startswith('BITMAP_IDENTITY_MEDIAN_NS=')]
    if len(identity) != 1 or int(identity[0]) <= 0:
        raise SystemExit('Missing identity bitmap timing evidence in ' + runtime)
    identity_timings[runtime] = int(identity[0])
    print('BITMAP_SAMPLING_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'native_source_sha256': hashlib.sha256((ROOT / 'native/bitmap.cpp').read_bytes()).hexdigest(), 'benchmark': {'component_only': True, 'destination': [720, 1280], 'source': [256, 256], 'warmups': 2, 'samples': 5, 'median_ns': timings}, 'clipped_benchmark': {'component_only': True, 'bitmap': [2048, 2048], 'clip': [16, 16], 'warmups': 2, 'samples': 9, 'median_ns': sparse_timings}, 'identity_benchmark': {'component_only': True, 'bitmap': [720, 1280], 'warmups': 2, 'samples': 9, 'median_ns': identity_timings}}, indent=2) + '\n')
