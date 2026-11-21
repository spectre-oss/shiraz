# SPDX-License-Identifier: GPL-3.0-only
"""Kernel-issued boot identity, shared by independent host processes."""

import functools
import subprocess
import sys
import uuid
from pathlib import Path


@functools.lru_cache(maxsize=1)
def current():
    if sys.platform.startswith('linux'):
        value = Path('/proc/sys/kernel/random/boot_id').read_text(encoding='ascii').strip()
    elif sys.platform == 'darwin':
        value = subprocess.run(
            ['/usr/sbin/sysctl', '-n', 'kern.bootsessionuuid'],
            capture_output=True,
            text=True,
            check=True,
            timeout=5,
        ).stdout.strip()
    else:
        raise RuntimeError('Shared jobs require a supported kernel boot identity')
    parsed = uuid.UUID(value)
    if str(parsed) != value.lower() or parsed.int == 0:
        raise ValueError('Invalid kernel boot identity')
    return str(parsed)
