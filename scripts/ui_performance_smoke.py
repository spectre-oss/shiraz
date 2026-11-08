#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run existing outline/text correctness fixtures against the performance changes."""
import json
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/ui-performance-smoke'


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    classes = WORK / 'classes'
    classes.mkdir(exist_ok=True)
    native = ROOT / ('build/macos' if DARWIN else 'build')
    jvm_native = native
    if DARWIN:
        jvm_native = WORK / 'host-native'
        jvm_native.mkdir(exist_ok=True)
        for library in native.glob('lib*.so'):
            alias = jvm_native / (library.stem + '.dylib')
            if not alias.exists():
                alias.symlink_to(library)
    core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    cp = ':'.join(map(str, (classes, ROOT / 'build/framework/classes', core)))
    fixtures = ['OutlineFixture', 'TextViewFixture']
    mains = ['OutlineSmoke', 'TextViewSmoke', 'TextGeometryBoundariesSmoke', 'BidiTextSmoke']
    subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-cp', str(ROOT / '.deps/android-sdk/android.jar'),
                    '-d', str(classes), *[str(ROOT / 'tests/apk/src/org/shiraz/fixture' / (name + '.java')) for name in fixtures]], check=True, timeout=60)
    subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-cp', cp, '-d', str(classes),
                    *[str(ROOT / 'tests/java' / (name + '.java')) for name in mains]], check=True, timeout=60)
    dex = WORK / 'classes.dex'
    subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True, timeout=60)
    jar = WORK / 'smoke.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        entry.extra = struct.pack('<HH', 0xffff, (-(30 + len(entry.filename) + 4)) % 4)
        entry.extra += b'\0' * ((-(30 + len(entry.filename) + 4)) % 4)
        archive.writestr(entry, dex.read_bytes())
    vm = HOST / 'bin/dalvikvm64'
    if not vm.exists():
        vm = HOST / 'bin/dalvikvm'
    settings = ['-Dshiraz.frameworkResources=' + str(ROOT / 'build/framework-res/framework-res.apk')]
    results = []
    failures = []
    for name in mains:
        commands = {
            'jvm': ['java', '-Xcheck:jni', *settings, '-Djava.library.path=' + str(jvm_native), '-cp', cp, name],
            'art': [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / item) for item in JARS), '-Xint',
                    '-Xusejit:false', '-Xcheck:jni', '-Xnoimage-dex2oat', *settings, '-Djava.library.path=' + str(native),
                    '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), name],
        }
        for runtime, command in commands.items():
            result = subprocess.run(command, check=False, env=environment(WORK), capture_output=True, text=True, timeout=120)
            log = WORK / (name + '-' + runtime + '.log')
            log.write_text(result.stdout + result.stderr)
            if result.returncode:
                failures.append(f'{name}/{runtime}: {log}')
                results.append({'suite': name, 'runtime': runtime, 'passed': False, 'log': str(log)})
                print(name, runtime, 'FAIL')
                continue
            results.append({'suite': name, 'runtime': runtime, 'stdout': result.stdout})
            print(name, runtime, 'PASS')
    (WORK / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
    if failures:
        raise SystemExit('Component failures: ' + '; '.join(failures))


if __name__ == '__main__':
    main()
