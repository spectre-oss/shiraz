# SPDX-License-Identifier: GPL-3.0-only
"""Launcher-owned, read-only cache metadata capability for the Darwin guest."""

import os
import struct
import subprocess
import sys
import tempfile
from contextlib import contextmanager

from .apk import FormatError

CATALOG_ENV = "SHIRAZ_GUEST_CACHE_CATALOG_FD"
MAX_CATALOG = 8 * 1024 * 1024


@contextmanager
def cache_catalog_fd(root, directory):
    result = subprocess.run(
        [
            sys.executable,
            "-I",
            str(root / "scripts/darwin_cache_mapping_catalog.py"),
            "--with-paths",
        ],
        capture_output=True,
        timeout=30,
        check=False,
    )
    data = result.stdout
    if (
        result.returncode
        or len(data) < 28
        or len(data) > MAX_CATALOG
        or data[:8] != b"SHZMAP2\0"
        or not 0 < struct.unpack_from("<I", data, 24)[0] <= 4096
    ):
        raise FormatError("Cannot prepare Darwin guest cache mapping metadata")
    fd = None
    name = None
    try:
        with tempfile.NamedTemporaryFile(
            dir=directory, prefix="guest-cache-", delete=False
        ) as temporary:
            name = temporary.name
            temporary.write(data)
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        os.unlink(name)
        name = None
        yield fd
    finally:
        if fd is not None:
            os.close(fd)
        if name is not None:
            os.unlink(name)
