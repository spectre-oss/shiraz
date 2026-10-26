#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise path geometry, SVG grammar and native rasterization on OpenJDK and actual ART."""
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
work = ROOT / 'build/path-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Path smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes), str(ROOT / 'tests/java/PathSmoke.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/PathFixture.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'PathSmoke'])]
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
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'PathSmoke']))
marker = 'SVG_PATH_GRAMMAR_AND_NATIVE_BOUNDS_OK'
timings = {}
for runtime, command in commands:
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('Path smoke failed in ' + runtime + '; inspect build/path-smoke/' + runtime + '-stderr.log')
    timing = [line.split('=', 1)[1] for line in result.stdout.splitlines() if line.startswith('PATH_50X50_IN_720X1280_MEDIAN_NS=')]
    if len(timing) != 1 or int(timing[0]) <= 0:
        raise SystemExit('Missing path component timing in ' + runtime)
    timings[runtime] = int(timing[0])
    print('PATH_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'native_source_sha256': hashlib.sha256((ROOT / 'native/path.cpp').read_bytes()).hexdigest(), 'component_only': True, 'shape': [50, 50], 'target': [720, 1280], 'warmups': 2, 'samples': 5, 'draws_per_sample': 100, 'median_ns_per_draw': timings}, indent=2) + '\n')
