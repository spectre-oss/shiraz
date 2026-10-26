#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check desugared boot callbacks against OpenJDK and actual standalone ART."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS

ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/boot-smoke'
work.mkdir(parents=True, exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Boot smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
subprocess.run(['javac', '--release', '8', '-d', str(classes), str(ROOT / 'tests/java/BootLambdaSmoke.java')], check=True)
marker = 'BOOT_LAMBDA_COMPARATORS_FUNCTIONS_STREAMS_OK'
commands = [('openjdk', ['java', '-Xcheck:jni', '-cp', str(classes), 'BootLambdaSmoke'])]
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
                '-Djava.library.path=' + str(HOST / 'lib64'), '-cp', str(jar), 'BootLambdaSmoke']))
for runtime, command in commands:
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=45)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('Boot lambda checks failed in ' + runtime + '; inspect build/boot-smoke/' + runtime + '-stderr.log')
    print('BOOT_LAMBDA_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker}, indent=2) + '\n')
