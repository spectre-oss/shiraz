#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify synthetic preference durability across independent OpenJDK/ART VMs."""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    (ROOT / "build").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="preferences-process-", dir=ROOT / "build") as directory:
        for phase in ("write", "read"):
            env = {**os.environ, "SHIRAZ_PREF_TEST_ROOT": directory, "SHIRAZ_PREF_TEST_PHASE": phase}
            subprocess.run(
                [sys.executable, str(ROOT / "scripts/java_framework_smoke.py"),
                 "PreferencesProcessSmoke", "--marker", "PREFERENCES_PROCESS_DURABLE_OK"],
                cwd=ROOT, env=env, check=True,
            )
    print("PREFERENCES_VM_RESTART_OK synthetic_only=1")


if __name__ == "__main__":
    main()
