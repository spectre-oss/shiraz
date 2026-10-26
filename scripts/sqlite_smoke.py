#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run the native SQLite boundary checks on both OpenJDK and standalone ART."""
import json
import fcntl
from pathlib import Path
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/sqlite-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('SQLite smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
classes.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
subprocess.run(['javac', '--release', '8', '-cp', str(framework), '-d', str(classes), str(ROOT / 'tests/java/NativeSqliteSmoke.java')], check=True)
marker = 'NATIVE_SQLITE_TYPED_PERSISTENCE_AND_OWNERSHIP_OK'
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + str(framework), 'NativeSqliteSmoke'])]
dex = work / 'classes.dex'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True)
jar = work / 'smoke.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    archive.write(dex, 'classes.dex')
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
commands.append(('art', [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                '-Xcheck:jni', '-Xusejit:false', '-Xint', '-Xnoimage-dex2oat', '-Djava.library.path=' + str(ROOT / 'build'),
                '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'NativeSqliteSmoke']))
for runtime, command in commands:
    result = subprocess.run([*command, str(work / runtime)], env=environment(work), capture_output=True, text=True, timeout=45)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode != 0 or marker not in result.stdout.splitlines():
        raise SystemExit('SQLite boundary failed in ' + runtime + '; inspect build/sqlite-smoke/' + runtime + '-stderr.log')
    print('SQLITE_BOUNDARY_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker}, indent=2) + '\n')
