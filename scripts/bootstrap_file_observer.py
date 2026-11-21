#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned MIT libinotify-kqueue in the project prefix for macOS."""

import hashlib
import platform
import subprocess
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REVISION = 'cc614f659a6a8ca3529328fe278bfc303c35cd5c'
URL = f'https://codeload.github.com/libinotify-kqueue/libinotify-kqueue/tar.gz/{REVISION}'
SHA256 = '98fdacb3914575ba74973bc65a46b545bb5c6173e3a8530448cfcfb4d3156d67'


def main():
    if platform.system() != 'Darwin':
        raise SystemExit('libinotify-kqueue bootstrap requires macOS')
    directory = ROOT / '.deps/file-observer'
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / f'{REVISION}.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(URL, timeout=45) as response:
            archive.write_bytes(response.read(2 * 1024 * 1024))
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('File observer source hash mismatch')
    source = directory / f'libinotify-kqueue-{REVISION}'
    if not source.is_dir():
        with tarfile.open(archive) as tar:
            tar.extractall(directory, filter='data')
    subprocess.run(['autoreconf', '-fvi'], cwd=source, check=True)
    subprocess.run(
        [
            './configure',
            '--prefix=' + str(ROOT / '.local'),
            '--disable-shared',
            '--enable-static',
            '--with-pic',
        ],
        cwd=source,
        check=True,
    )
    subprocess.run(['make', '-j4'], cwd=source, check=True)
    subprocess.run(['make', 'install'], cwd=source, check=True)
    destination = ROOT / '.local/share/shiraz/file-observer'
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'LICENSE').write_bytes((source / 'LICENSE').read_bytes())
    (destination / 'SOURCE.txt').write_text(
        f'libinotify-kqueue {REVISION}\n{URL}\nSHA-256: {SHA256}\n'
        'License: MIT; original notice in LICENSE.\n'
        'macOS: no access/open/close events; move pairs only within watched directory.\n'
    )


if __name__ == '__main__':
    main()
