#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise GPU Canvas and automatic software fallback on OpenJDK and ART."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/gpu-canvas-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('GPU Canvas smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes), str(ROOT / 'tests/java/shiraz/GpuCanvasSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'shiraz.GpuCanvasSmoke'])]
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
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'shiraz.GpuCanvasSmoke']))
marker = 'GPU_CANVAS_OK'
replay = work / 'resize.txt'
replay.write_text('1 resize 192 160\n2 resize 160 192\n')
results = []
for backend, settings in [('gpu', {'SDL_VIDEODRIVER': 'offscreen', 'EXPECT_CANVAS': 'gpu'}),
                          ('fallback', {'SDL_VIDEODRIVER': 'dummy', 'EXPECT_CANVAS': 'software'}),
                          ('forced', {'SDL_VIDEODRIVER': 'offscreen', 'SHIRAZ_CANVAS': 'software', 'EXPECT_CANVAS': 'software'}),
                          ('cpu-driver', {'SDL_VIDEODRIVER': 'offscreen', 'LIBGL_ALWAYS_SOFTWARE': '1', 'EXPECT_CANVAS': 'software'})]:
    for runtime, command in commands:
        label = backend + '-' + runtime
        env = dict(environment(work), SHIRAZ_INPUT_SCRIPT=str(replay), **settings)
        result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
        (work / (label + '-stdout.log')).write_text(result.stdout)
        (work / (label + '-stderr.log')).write_text(result.stderr)
        if result.returncode or marker not in result.stdout.splitlines() or 'WARNING in native method' in result.stderr:
            raise SystemExit('GPU Canvas smoke failed: ' + label + '; inspect build/gpu-canvas-smoke')
        print('GPU_CANVAS_OK ' + label)
        results.append(label)
(work / 'result.json').write_text(json.dumps({'cases': results, 'component_only': True}, indent=2) + '\n')
