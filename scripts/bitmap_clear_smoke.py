#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check bitmap replacement semantics and component timings on host JVM and ART."""
import json
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/bitmap-clear-smoke'


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    classes = WORK / 'classes'
    classes.mkdir(exist_ok=True)
    core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    cp = str(ROOT / 'build/framework/classes') + ':' + str(core)
    subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tests/java/BitmapClearSmoke.java')], check=True, timeout=60)
    dex = WORK / 'classes.dex'
    subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True, timeout=60)
    jar = WORK / 'smoke.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    native = ROOT / ('build/macos' if DARWIN else 'build')
    jvm_native = native
    if DARWIN:
        jvm_native = WORK / 'host-native'
        jvm_native.mkdir(exist_ok=True)
        for library in native.glob('lib*.so'):
            alias = jvm_native / (library.stem + '.dylib')
            if not alias.exists():
                alias.symlink_to(library)
    env = environment(WORK)
    vm = HOST / 'bin/dalvikvm64'
    if not vm.exists():
        vm = HOST / 'bin/dalvikvm'
    commands = {
        'jvm': ['java', '-Xcheck:jni', '-Djava.library.path=' + str(jvm_native), '-cp', str(classes) + ':' + cp, 'BitmapClearSmoke'],
        'art': [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                '-Xint', '-Xusejit:false', '-Xcheck:jni', '-Xnoimage-dex2oat', '-Djava.library.path=' + str(native),
                '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'BitmapClearSmoke'],
    }
    results = {}
    for name, command in commands.items():
        result = subprocess.run(command, check=False, env=env, capture_output=True, text=True, timeout=60)
        (WORK / (name + '.log')).write_text(result.stdout + result.stderr)
        if result.returncode or 'BITMAP_CLEAR_OK' not in result.stdout.splitlines():
            raise SystemExit(f'{name} clear smoke failed; inspect {WORK / (name + ".log")}')
        results[name] = {line.split('=')[0]: int(line.split('=')[1]) for line in result.stdout.splitlines() if '_AVERAGE_NS=' in line}
    (WORK / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
