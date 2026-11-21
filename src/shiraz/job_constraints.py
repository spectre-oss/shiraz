# SPDX-License-Identifier: GPL-3.0-only
"""Host measurements for job constraints; unknown is never treated as satisfied.

Storage policy uses 5 percent capped at 500 MiB, following the AOSP default
shape. Android settings overrides and volume-specific hysteresis are not yet
implemented. The broker uses these observations for constrained job dispatch.
"""

import os
from dataclasses import dataclass
from pathlib import Path

from .job_definition import LONG_MAX, integer

LOW_PERCENT = 5
MAX_LOW_BYTES = 500 * 1024 * 1024


@dataclass(frozen=True)
class StorageObservation:
    measured_path: Path
    total_bytes: int
    available_bytes: int
    low_threshold_bytes: int

    @property
    def not_low(self):
        return self.available_bytes > self.low_threshold_bytes


def observe_storage(data_path):
    """Measure the actual volume, using an existing ancestor before app creation.

    f_bavail excludes space unavailable to the current unprivileged process.
    Missing/denied/unusable measurements return None, not a fabricated capacity.
    No directory creation, disk allocation or network activity is performed.
    """
    try:
        path = Path(data_path).resolve()
        while True:
            try:
                info = os.statvfs(path)
                break
            except FileNotFoundError:
                parent = path.parent
                if parent == path:
                    return None
                path = parent
        values = (info.f_frsize, info.f_blocks, info.f_bavail)
        if any(type(value) is not int for value in values):
            return None
        block_size, blocks, available = values
        if block_size <= 0 or blocks <= 0 or available < 0 or available > blocks:
            return None
        total = block_size * blocks
        free = block_size * available
        return StorageObservation(path, total, free, min(total * LOW_PERCENT // 100, MAX_LOW_BYTES))
    except (OSError, RuntimeError):
        return None


def deadline_expired(record, now_ms):
    """First-attempt override deadline, evaluated from host-owned timestamps.

    Retry attempts never inherit the initial override, even across host restart.
    Keep the submitted definition unchanged for queries and future replacement.
    """
    integer(now_ms, 0, LONG_MAX)
    attempts = integer(record['attempts'], 0, (1 << 31) - 1)
    deadline = record['deadline_elapsed_ms']
    return attempts == 0 and deadline is not None and now_ms >= integer(deadline, 0, LONG_MAX)


def satisfied(definition, package, *, override_deadline_expired=False):
    """Check represented constraints against this runtime's app-data volume.

    The authenticated host supplies deadline state; it is never a guest field.
    Callers must supply the authenticated package, never guest paths.
    """
    if type(override_deadline_expired) is not bool:
        raise ValueError('Invalid deadline eligibility')
    if override_deadline_expired or not definition.get('requires_storage_not_low', False):
        return True
    root = Path(__file__).resolve().parents[2]
    observation = observe_storage(root / 'data/apps' / package)
    return observation is not None and observation.not_low
