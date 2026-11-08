# SPDX-License-Identifier: GPL-3.0-only
"""Read public host capabilities for a bounded symbolic return-token model.

This never signs code, reads authentication keys, or executes PAC instructions.
Unknown capabilities must not be treated as the legacy SP-only profile.
"""

import functools
import platform
import subprocess
import sys


@functools.lru_cache(maxsize=1)
def host_return_auth_profile():
    if sys.platform != "darwin" or platform.machine() != "arm64":
        return None
    values = []
    for name in ("hw.optional.arm.FEAT_PAuth", "hw.optional.arm.FEAT_PAuth_LR"):
        try:
            result = subprocess.run(["/usr/sbin/sysctl", "-n", name], capture_output=True,
                                    text=True, timeout=5, check=False)
        except (OSError, subprocess.TimeoutExpired):
            return None
        if result.returncode or result.stdout.strip() not in {"0", "1"}:
            return None
        values.append(int(result.stdout.strip()))
    return {"source": "observed-host-sysctl", "tool": "/usr/sbin/sysctl", "machine": "arm64",
            "feature_pauth": values[0], "feature_pauth_lr": values[1],
            "scope": "symbolic matching-SP return tokens only; no cryptographic or hardware execution proof"}
