#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify macOS removes an actual JNI power assertion after owned-process SIGKILL."""

import fcntl
import json
import re
import signal
import subprocess
import time
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, NATIVE, environment

ROOT = Path(__file__).resolve().parents[1]


def assertions(pid):
    result = subprocess.run(
        ['pmset', '-g', 'assertions'], capture_output=True, timeout=5, check=True
    )
    pattern = re.compile(r'^\s*pid\s*' + str(pid) + r'\(')
    return [
        line.strip()
        for line in result.stdout.decode('utf-8', errors='replace').splitlines()
        if pattern.search(line) and '"Shiraz background work"' in line
    ]


def main():
    if not DARWIN:
        raise SystemExit('Native power death smoke requires macOS')
    work = ROOT / 'build/java-framework-smoke/NativePowerSmoke'
    with (work / '.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        evidence = work / 'death-result.json'
        evidence.unlink(missing_ok=True)
        vm = HOST / 'bin/dalvikvm64'
        if not vm.exists():
            vm = HOST / 'bin/dalvikvm'
        commands = [
            (
                'openjdk',
                [
                    'java',
                    '-Xcheck:jni',
                    '-Djava.library.path=' + str(NATIVE / 'jvm-native'),
                    '-cp',
                    str(work / 'classes') + ':' + str(work / 'framework-classes'),
                    'NativePowerSmoke',
                    'hold',
                ],
            ),
            (
                'art',
                [
                    str(vm),
                    '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                    '-Xcheck:jni',
                    '-Xusejit:false',
                    '-Xint',
                    '-Xnoimage-dex2oat',
                    '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
                    '-cp',
                    str(work / 'framework.jar') + ':' + str(work / 'smoke.jar'),
                    'NativePowerSmoke',
                    'hold',
                ],
            ),
        ]
        results = []
        for runtime, command in commands:
            stdout = work / (runtime + '-death-stdout.log')
            stderr = work / (runtime + '-death-stderr.log')
            with stdout.open('w') as out, stderr.open('w') as err:
                child = subprocess.Popen(
                    command, env=environment(work), stdin=subprocess.PIPE, stdout=out, stderr=err
                )
                try:
                    deadline = time.monotonic() + 15
                    while (
                        'NATIVE_POWER_HELD' not in stdout.read_text(errors='replace').splitlines()
                    ):
                        if child.poll() is not None or time.monotonic() >= deadline:
                            raise RuntimeError(
                                f'{runtime} did not acquire assertion; inspect {stderr}'
                            )
                        time.sleep(0.05)
                    deadline = time.monotonic() + 5
                    held = assertions(child.pid)
                    while not held and time.monotonic() < deadline:
                        time.sleep(0.05)
                        held = assertions(child.pid)
                    if not held:
                        raise RuntimeError(f'{runtime} assertion absent from host power manager')
                    child.kill()
                    status = child.wait(timeout=5)
                    if status != -signal.SIGKILL:
                        raise RuntimeError(f'{runtime} unexpected kill status {status}')
                    deadline = time.monotonic() + 5
                    remaining = assertions(child.pid)
                    while remaining and time.monotonic() < deadline:
                        time.sleep(0.05)
                        remaining = assertions(child.pid)
                    if remaining:
                        raise RuntimeError(f'{runtime} retained assertion after process death')
                    results.append(
                        dict(
                            runtime=runtime,
                            pid=child.pid,
                            before=held,
                            after=remaining,
                            exit_status=status,
                        )
                    )
                    print('NATIVE_POWER_DEATH_CLEANUP_OK ' + runtime, flush=True)
                finally:
                    if child.poll() is None:
                        child.kill()
                    child.wait(timeout=5)
                    child.stdin.close()
        evidence.write_text(json.dumps(results, indent=2) + '\n')


if __name__ == '__main__':
    main()
