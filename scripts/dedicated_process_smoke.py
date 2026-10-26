#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise dedicated APK process exit on OpenJDK and ART."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/dedicated-process-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Dedicated process smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', str(classes)+':'+classpath, '-d', str(classes), str(ROOT / 'tests/java/DedicatedProcessSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'DedicatedProcessSmoke'])]
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
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'DedicatedProcessSmoke']))
for runtime, command in commands:
    for mode in ('success', 'body', 'cleanup', 'both'):
        result = subprocess.run(command + [mode], env=environment(work), capture_output=True, text=True, timeout=15)
        (work / (runtime + '-' + mode + '-stdout.log')).write_text(result.stdout)
        (work / (runtime + '-' + mode + '-stderr.log')).write_text(result.stderr)
        expected = 0 if mode == 'success' else 10
        assert result.returncode == expected, (runtime, mode, result.returncode)
        assert 'BODY_RAN' in result.stdout and 'CLEANUP_RAN' in result.stdout
        assert ('SHIRAZ_RUNTIME_CLEANUP_COMPLETE' in result.stdout) == (mode == 'success')
        assert ('SHIRAZ_RUNTIME_FATAL' in result.stderr) == (mode != 'success')
        assert 'APP_HANDLER_RETURNED' not in result.stderr
        if mode in ('body', 'both'):
            assert 'java.lang.IllegalStateException: PRIMARY_FAILURE' in result.stderr
        if mode == 'both':
            assert 'Suppressed: java.lang.IllegalArgumentException: CLEANUP_FAILURE' in result.stderr
        if mode == 'cleanup':
            assert 'java.lang.IllegalArgumentException: CLEANUP_FAILURE' in result.stderr
    print('DEDICATED_PROCESS_EXIT_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'cases': ['success', 'body', 'cleanup', 'both'], 'component_only': True}, indent=2) + '\n')
