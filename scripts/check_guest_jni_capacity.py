#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run experimental JNI capacity checks in disposable processes."""

import resource
import signal
import subprocess
import sys


def no_core():
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: check_guest_jni_capacity.py PROBE")
    for mode in (
        "stack",
        "bad-stack",
        "methods",
        "onload",
        "onunload",
        "generic",
        "bad-env",
        "duplicate",
        "other",
    ):
        result = subprocess.run(
            [sys.argv[1], mode], capture_output=True, text=True, timeout=15, preexec_fn=no_core
        )
        expected = 0 if mode in ("stack", "bad-stack", "methods", "onload") else -signal.SIGABRT
        if result.returncode != expected:
            raise SystemExit(f"{mode}: unexpected exit {result.returncode}: {result.stderr}")
        if expected:
            reason = {
                "onunload": "foreign identity capacity exhausted",
                "generic": "foreign identity capacity exhausted",
                "bad-env": "foreign identity capacity exhausted",
                "duplicate": "duplicate guest identity",
                "other": "other setup failure",
            }[mode]
            if "SHIRAZ_GUEST_FATAL" not in result.stderr or reason not in result.stderr:
                raise SystemExit(f"{mode}: missing expected fail-closed diagnostic")
    print(
        "M5_JNI_CAPACITY_PASS methods=1 onload=1 unload_refused=1 invalid_env_refused=1 other_refused=1"
    )


if __name__ == "__main__":
    main()
