# SPDX-License-Identifier: GPL-3.0-only
"""Bounded native NWPathMonitor adapter; no reachability traffic or DNS probes."""

import ctypes
import functools
from pathlib import Path


@functools.lru_cache(maxsize=1)
def library():
    path = Path(__file__).resolve().parents[2] / 'build/macos/libshiraz-job-network.dylib'
    native = ctypes.CDLL(str(path))
    native.shiraz_job_network_snapshot.argtypes = (ctypes.POINTER(ctypes.c_uint32), ctypes.c_uint32)
    native.shiraz_job_network_snapshot.restype = ctypes.c_int
    return native


def observe():
    from .job_network import NetworkObservation

    try:
        values = (ctypes.c_uint32 * 3)()
        if library().shiraz_job_network_snapshot(values, 3) != 1:
            return None
        version, status, transports = values
        if version != 1 or status not in (1, 2, 3) or transports & ~15:
            return None
        if status != 1:
            return NetworkObservation(False)
        # Multiple/other transports do not establish one physical bearer. The
        # path may be VPN-backed. Metering and Internet validation stay unknown.
        transport = {1: 'wifi', 2: 'ethernet', 4: 'cellular'}.get(transports)
        return NetworkObservation(True, transport=transport)
    except (OSError, AttributeError):
        return None
