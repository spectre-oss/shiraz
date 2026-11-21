# SPDX-License-Identifier: GPL-3.0-only
"""Foreground restart supervisor; does not install an OS startup service."""

import json
import signal
import subprocess
import sys
import threading
import time

from .job_host import ownership
from .package_registry import PackageRegistry


def restart_delay(failures):
    return min(30, 1 << min(max(failures - 1, 0), 5))


def supervise(root, output):
    import os

    registry = PackageRegistry(root)
    stop = threading.Event()
    previous = {}
    child = None
    failures = 0
    with ownership(registry, supervisor=True):
        try:
            for signum in (signal.SIGTERM, signal.SIGINT):
                previous[signum] = signal.signal(signum, lambda *_: stop.set())
            while not stop.is_set():
                started = time.monotonic()
                child = subprocess.Popen(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'serve-jobs',
                        '--registry',
                        str(registry.root.resolve()),
                        '--parent-pid',
                        str(os.getpid()),
                    ],
                    stdin=subprocess.DEVNULL,
                    stdout=output,
                    start_new_session=True,
                )
                while child.poll() is None and not stop.wait(0.1):
                    pass
                if stop.is_set():
                    break
                failures = 1 if time.monotonic() - started >= 60 else failures + 1
                delay = restart_delay(failures)
                print(
                    json.dumps(
                        dict(
                            event='restart',
                            service='jobs',
                            exit_status=child.returncode,
                            delay_seconds=delay,
                        )
                    ),
                    file=output,
                    flush=True,
                )
                child = None
                stop.wait(delay)
        finally:
            try:
                if child is not None and child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=5)
            finally:
                for signum, handler in previous.items():
                    signal.signal(signum, handler)
