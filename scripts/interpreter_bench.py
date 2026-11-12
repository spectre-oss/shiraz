#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Time deterministic interpreter workloads (tests/java/InterpreterBench.java) on host ART.

Runs the benchmark --runs times and prints the median milliseconds of each workload.
The per-workload check values must agree across runs; a mismatch fails the run.
"""

import argparse
import shutil
import statistics
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]


def build(work):
    classes = work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    source = ROOT / 'tests/java/InterpreterBench.java'
    subprocess.run(['javac', '--release', '8', '-d', str(classes), str(source)], check=True)
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
    jar = work / 'bench.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', type=int, default=3)
    parser.add_argument('--scale', type=int, default=1)
    args = parser.parse_args()
    work = ROOT / 'build/interpreter-bench'
    jar = build(work)
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    times, checks = {}, {}
    for run in range(args.runs):
        result = subprocess.run(
            [
                str(vm),
                '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                '-Xusejit:false',
                '-Xint',
                '-Xnoimage-dex2oat',
                '-cp',
                str(jar),
                'InterpreterBench',
                str(args.scale),
            ],
            env=environment(work),
            capture_output=True,
            text=True,
            timeout=600,
        )
        (work / f'run{run}-stderr.log').write_text(result.stderr)
        lines = [line.split() for line in result.stdout.splitlines()]
        lines = [fields for fields in lines if fields[:1] == ['INTERPRETER_BENCH']]
        if result.returncode or not lines or lines[-1][1] != 'total':
            raise SystemExit(f'Interpreter bench failed; inspect {work}/run{run}-stderr.log')
        for _, name, ms, check in lines:
            times.setdefault(name, []).append(int(ms.split('=')[1]))
            if checks.setdefault(name, check) != check:
                raise SystemExit(f'Interpreter bench {name} changed result: {check}')
    for name, values in times.items():
        print(f'INTERPRETER_BENCH {name} median_ms={statistics.median(values):g} runs={values}')


if __name__ == '__main__':
    main()
