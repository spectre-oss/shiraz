#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fetch the pinned, licensed Manrope font for the static browser demo."""
import hashlib
from pathlib import Path
import urllib.request

FONT = Path(__file__).resolve().parent / 'fonts/Manrope-wght.ttf'
URL = ('https://raw.githubusercontent.com/google/fonts/'
       'e7db6ca603fd4862010383ff17ffd6cc4ffac1c0/'
       'ofl/manrope/Manrope%5Bwght%5D.ttf')
SHA256 = '3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6'


def main():
    if FONT.is_file() and hashlib.sha256(FONT.read_bytes()).hexdigest() == SHA256:
        return
    FONT.parent.mkdir(parents=True, exist_ok=True)
    temporary = FONT.with_suffix('.download')
    try:
        with urllib.request.urlopen(URL, timeout=60) as response:
            data = response.read(1024 * 1024 + 1)
        if len(data) > 1024 * 1024 or hashlib.sha256(data).hexdigest() != SHA256:
            raise RuntimeError('Manrope font download failed checksum or size validation')
        temporary.write_bytes(data)
        temporary.replace(FONT)
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
