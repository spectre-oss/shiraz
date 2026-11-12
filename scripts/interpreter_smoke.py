#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check switch-interpreter fast paths (field access, invokes) on every heap GC.

InterpreterFieldSmoke must print its fixed OpenJDK-derived marker. InterpreterInvokeSmoke
prints a digest that is compared with the same program run on the host OpenJDK.
"""

import argparse
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]
FIELD_MARKER = 'INTERPRETER_FIELD_OK sum=-4587696900259082784'
PROGRAMS = {'field': 'InterpreterFieldSmoke', 'invoke': 'InterpreterInvokeSmoke'}


def dex_jar(work, classes):
    dex = work / 'classes.dex'
    subprocess.run(
        [
            str(HOST / 'bin/dx'),
            '--dex',
            '--min-sdk-version=27',
            '--output=' + str(dex),
            str(classes),
        ],
        env=environment(work),
        check=True,
    )
    jar = work / 'smoke.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    return jar


def expected_marker(program, classes):
    if program == 'InterpreterFieldSmoke':
        return FIELD_MARKER
    result = subprocess.run(
        ['java', '-Xss8m', '-cp', str(classes), program],
        capture_output=True,
        text=True,
        timeout=300,
        check=True,
    )
    markers = [line for line in result.stdout.splitlines() if line.startswith('INTERPRETER_')]
    if len(markers) != 1:
        raise SystemExit('OpenJDK did not print one ' + program + ' marker')
    return markers[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--program', choices=sorted(PROGRAMS), action='append')
    args = parser.parse_args()
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    for key in args.program or sorted(PROGRAMS):
        program = PROGRAMS[key]
        work = ROOT / 'build/interpreter-smoke' / key
        classes = work / 'classes'
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir(parents=True)
        source = ROOT / 'tests/java' / (program + '.java')
        subprocess.run(['javac', '--release', '8', '-d', str(classes), str(source)], check=True)
        marker = expected_marker(program, classes)
        jar = dex_jar(work, classes)
        # Moving collectors relocate objects between rounds; cached state must not hold addresses.
        for gc in ('CMS', 'MS', 'SS', 'GSS'):
            result = subprocess.run(
                [
                    str(vm),
                    '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                    '-Xusejit:false',
                    '-Xint',
                    '-Xnoimage-dex2oat',
                    '-Xcheck:jni',
                    '-Xgc:' + gc,
                    '-cp',
                    str(jar),
                    program,
                ],
                env=environment(work),
                capture_output=True,
                text=True,
                timeout=600,
            )
            (work / (gc + '-stdout.log')).write_text(result.stdout)
            (work / (gc + '-stderr.log')).write_text(result.stderr)
            if result.returncode or marker not in result.stdout.splitlines():
                raise SystemExit(
                    f'{program} failed with -Xgc:{gc}; inspect {work}/{gc}-stdout.log and -stderr.log'
                )
            print(marker + ' gc=' + gc)


if __name__ == '__main__':
    main()
