#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Load a freestanding JNI ELF through the Bionic linker in actual host ART."""
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from art_smoke import ROOT, HOST, JARS, environment

work = ROOT / 'build/bionic-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
classes.mkdir(exist_ok=True)
libs = work / 'libs'
libs.mkdir(exist_ok=True)
java_home = Path(shutil.which('javac')).resolve().parent.parent
library = libs / 'libshiraz-bionic-probe.so'
subprocess.run(['cc', '-shared', '-fPIC', '-nostdlib', '-Wl,-z,defs',
                '-I' + str(java_home / 'include'), '-I' + str(java_home / 'include/linux'),
                str(ROOT / 'tests/bionic_load.c'), '-o', str(library)], check=True)
subprocess.run(['javac', '--release', '8', '-d', str(classes),
                str(ROOT / 'tests/java/BionicLoadSmoke.java')], check=True)
env = environment(work)
env['BIONIC_LD_LIBRARY_PATH'] = str(libs)
# No missing-symbol substitutions: all linking and constructors must succeed.
for key in list(env):
    if key.startswith('LINKER_'):
        del env[key]
jar = work / 'smoke.jar'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27',
                '--output=' + str(jar), str(classes)], env=env, check=True)
vm = HOST / 'bin/dalvikvm64'
if not vm.exists():
    vm = HOST / 'bin/dalvikvm'
command = [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
           '-Xcheck:jni', '-Xint', '-Xnoimage-dex2oat', '-Djava.library.path=' + str(libs),
           '-cp', str(jar), 'BionicLoadSmoke']
with (work / 'stdout.log').open('w') as out, (work / 'stderr.log').open('w') as err:
    result = subprocess.run(command, env=env, stdout=out, stderr=err, timeout=30)
marker = 'BIONIC_ART_LOAD_AND_CONSTRUCTOR_OK'
if result.returncode or marker not in (work / 'stdout.log').read_text().splitlines():
    raise SystemExit(f'Bionic ART smoke failed ({result.returncode}); inspect {work}/stderr.log')
(work / 'result.json').write_text(json.dumps({'marker': marker, 'exit_code': result.returncode,
    'bridge_sha256': hashlib.sha256((ROOT / '.local/lib/libdl_bio.so').read_bytes()).hexdigest()}, indent=2) + '\n')
print(marker)
