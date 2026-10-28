#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify ART alternate-signal-stack ownership on native and managed threads."""
import fcntl
import os
from pathlib import Path
import subprocess
import zipfile
from art_smoke import ROOT, HOST, environment

work = ROOT / 'build/art-alt-stack-smoke'
work.mkdir(parents=True, exist_ok=True)
lock = (work / '.lock').open('a')
fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
classes = work / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '--release', '8', '-d', str(classes),
                str(ROOT / 'tests/java/AltSignalStackSmoke.java')], check=True)
env = environment(work)
dex = work / 'classes.dex'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27',
                '--output=' + str(dex), str(classes)], env=env, check=True)
jar = work / 'test.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    archive.write(dex, 'classes.dex')
vm = HOST / 'bin/dalvikvm64'
if not vm.exists():
    vm = HOST / 'bin/dalvikvm'
command = [str(vm), '-Xbootclasspath:' + env['BOOTCLASSPATH'], '-Xcheck:jni',
           '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat',
           '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(jar), 'AltSignalStackSmoke']
if loader := os.environ.get('SHIRAZ_RUNTIME_LOADER'):
    libraries = env.pop('LD_LIBRARY_PATH') + ':' + str(Path(loader).parent)
    command = [loader, '--library-path', libraries, *command]
result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
(work / 'stdout.log').write_text(result.stdout)
(work / 'stderr.log').write_text(result.stderr)
marker = 'ART_ALT_SIGNAL_STACK_OWNERSHIP_OK'
if result.returncode or marker not in result.stdout.splitlines():
    raise SystemExit('ART alternate-stack test failed; inspect build/art-alt-stack-smoke logs')
print(marker)
