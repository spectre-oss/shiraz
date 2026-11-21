#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fetch the pinned Apache-2.0 apksig binary from Google's official Maven repository."""

import hashlib
import os
import sys
import tempfile
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from shiraz.apk_verifier import JAR, JAR_SHA256, URL  # noqa: E402


def main():
    if JAR.is_file() and hashlib.sha256(JAR.read_bytes()).hexdigest() == JAR_SHA256:
        return
    JAR.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(URL, timeout=30) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != JAR_SHA256:
        raise SystemExit('apksig checksum/size mismatch')
    with tempfile.NamedTemporaryFile(dir=JAR.parent, delete=False) as out:
        temporary = Path(out.name)
        out.write(data)
    try:
        os.replace(temporary, JAR)
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
