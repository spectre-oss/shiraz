#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise child drawable-state aggregation on OpenJDK and ART."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/child-drawable-state-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Child drawable state smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', str(classes)+':'+classpath, '-d', str(classes), str(ROOT / 'tests/java/ChildDrawableStateSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'ChildDrawableStateSmoke'])]
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
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'ChildDrawableStateSmoke']))
marker = 'CHILD_DRAWABLE_STATE_OK'
for runtime, command in commands:
    command.insert(1, '-Dshiraz.frameworkResources=' + str(ROOT / 'build/framework-res/framework-res.apk'))
    command.append(str(ROOT / 'build/apk-fixture/fixture.apk'))
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('Child drawable state smoke failed in ' + runtime + '; inspect build/child-drawable-state-smoke/' + runtime + '-stderr.log')
    print('CHILD_DRAWABLE_STATE_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'component_only': True}, indent=2) + '\n')
