#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the small local-only APK asset fixture for guest ABI checks."""

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

output = Path("build/macos/m5/guest-asset-fixture.apk")
output.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(output, "w") as archive:
    archive.writestr("assets/stored.txt", b"0123456789", compress_type=ZIP_STORED)
    archive.writestr("assets/compressed.txt", b"0123456789", compress_type=ZIP_DEFLATED)
