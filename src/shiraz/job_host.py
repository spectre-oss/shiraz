# SPDX-License-Identifier: GPL-3.0-only
"""Foreground background-job host suitable for an external service supervisor."""

import fcntl
import json
import os
import signal
import stat
import threading
from contextlib import contextmanager

from .package_registry import PackageRegistry
from .service_broker import ServiceBroker


@contextmanager
def ownership(registry, *, supervisor=False):
    name = 'job-supervisor.lock' if supervisor else 'job-host.lock'
    descriptor = os.open(
        registry.root / name, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600
    )
    try:
        info = os.fstat(descriptor)
        if (
            not stat.S_ISREG(info.st_mode)
            or info.st_mode & 0o077
            or info.st_uid != os.getuid()
            or info.st_nlink != 1
        ):
            raise PermissionError('Unsafe job host lock')
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError(
                'Job supervisor already running for this registry'
                if supervisor
                else 'Job host already running for this registry'
            ) from None
        yield
    finally:
        os.close(descriptor)  # Never unlink: contenders must use the same inode.


def serve(root, output, *, parent_pid=None):
    if parent_pid is not None and (type(parent_pid) is not int or parent_pid <= 1):
        raise ValueError('Invalid job supervisor PID')
    registry = PackageRegistry(root)
    stop = threading.Event()
    previous = {}
    with ownership(registry):
        try:
            for signum in (signal.SIGTERM, signal.SIGINT):
                previous[signum] = signal.signal(signum, lambda *_: stop.set())
            with ServiceBroker(registry) as broker:
                scheduler = broker.start_job_scheduler()
                print(
                    json.dumps(dict(event='ready', service='jobs', pid=os.getpid())),
                    file=output,
                    flush=True,
                )
                while not stop.wait(0.5):
                    if parent_pid is not None and os.getppid() != parent_pid:
                        break
                    if not scheduler.thread.is_alive():
                        raise RuntimeError('Job scheduler stopped unexpectedly')
            print(json.dumps(dict(event='stopped', service='jobs')), file=output, flush=True)
        finally:
            for signum, handler in previous.items():
                signal.signal(signum, handler)
