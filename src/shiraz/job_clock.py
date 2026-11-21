# SPDX-License-Identifier: GPL-3.0-only
"""Host elapsed clock matching SystemClock JNI; no wall-clock fallback.

Elapsed timestamps are comparable across processes in one kernel boot only.
Wall time is retained for persisted-job restoration across a different boot.
This module does not itself change existing registry scheduling semantics.
"""

import sys
import time
from dataclasses import dataclass

from . import boot_identity
from .job_definition import LONG_MAX, integer


def elapsed_ms():
    # Keep aligned with native/host_process.cpp, not time.monotonic_ns(): the
    # latter uses mach_absolute_time (which excludes suspend) on Darwin.
    if sys.platform.startswith('linux'):
        name = 'CLOCK_BOOTTIME'
    elif sys.platform == 'darwin':
        name = 'CLOCK_MONOTONIC'
    else:
        raise RuntimeError('Shared jobs require a supported elapsed clock')
    clock = getattr(time, name, None)
    if clock is None:
        raise RuntimeError('Required elapsed clock is unavailable')
    nanos = time.clock_gettime_ns(clock)
    if type(nanos) is not int or nanos < 0:
        raise ValueError('Invalid host elapsed clock')
    return integer(nanos // 1000000, 0, LONG_MAX)


@dataclass(frozen=True)
class Sample:
    boot_id: str
    wall_ms: int
    elapsed_ms: int
    uncertainty_ms: int

    def restore_target(self, wall_target_ms):
        """Translate a persisted wall target once at a boot/migration boundary.

        Already overdue work becomes eligible now. Same-boot dispatch must use
        stored elapsed targets directly, never retranslate after clock changes.
        """
        integer(wall_target_ms, 0, LONG_MAX)
        return min(LONG_MAX, self.elapsed_ms + max(0, wall_target_ms - self.wall_ms))


def sample():
    """Bound wall/elapsed pairing error; reject prolonged pauses or bad clocks."""
    identity = boot_identity.current()
    for _ in range(3):
        before = elapsed_ms()
        wall_nanos = time.time_ns()
        if type(wall_nanos) is not int or wall_nanos < 0:
            raise ValueError('Invalid host wall clock')
        wall = integer(wall_nanos // 1000000, 0, LONG_MAX)
        after = elapsed_ms()
        if after < before:
            raise RuntimeError('Elapsed clock moved backwards')
        if after - before <= 100:
            return Sample(identity, wall, after, after - before)
    raise RuntimeError('Cannot obtain bounded job clock sample')
