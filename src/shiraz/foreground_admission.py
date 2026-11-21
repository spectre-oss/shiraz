# SPDX-License-Identifier: GPL-3.0-only
"""Serialize foreground admission with standalone persisted job ownership."""

import time
from contextlib import contextmanager


@contextmanager
def reserve(broker, package, sha256, process, *, timeout=60):
    key = (package, sha256, process)
    token = object()
    with broker.lock:
        if broker.closed or package in broker.quiesced:
            raise RuntimeError('Foreground broker closed or package quiesced')
        if key in broker.foreground_admissions:
            raise RuntimeError('Foreground process admission already pending')
        running = []
        for dispatch in broker.job_dispatches:
            service = dispatch.worker.service
            name = service.get('process') or service['package']
            if name.startswith(':'):
                name = service['package'] + name
            if (service['package'], service['apk_sha256'], name) == key:
                running.append(dispatch)
        broker.foreground_admissions[key] = token
    try:
        # Never hold broker/host locks while waiting: finalizers and callbacks need them.
        # Explicit foreground demand cooperatively stops the conflicting job.
        # Only recorded completion/cleanup releases its claim and admits the app.
        deadline = time.monotonic() + timeout
        for dispatch in running:
            dispatch.stop_for_foreground()
            dispatch.drain(max(0, deadline - time.monotonic()))
        yield
    finally:
        with broker.lock:
            if broker.foreground_admissions.get(key) is token:
                del broker.foreground_admissions[key]
