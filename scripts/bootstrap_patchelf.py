#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Provide a native patchelf for packaging without installing system packages."""
import fcntl
import hashlib
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.18.0'
SHA256 = '1952b2a782ba576279c211ee942e341748fdb44997f704dd53def46cd055470b'


def main():
    prefix = ROOT / '.local'
    if shutil.which('patchelf') or (prefix / 'bin/patchelf').is_file():
        return
    deps = ROOT / '.deps'
    deps.mkdir(exist_ok=True)
    with (deps / 'patchelf-build.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        if (prefix / 'bin/patchelf').is_file():
            return
        archive = deps / f'patchelf-{VERSION}.tar.bz2'
        if not archive.exists():
            urllib.request.urlretrieve(
                f'https://github.com/NixOS/patchelf/releases/download/{VERSION}/{archive.name}', archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
            raise SystemExit('patchelf source checksum mismatch')
        source = deps / f'patchelf-{VERSION}'
        if not source.exists():
            with tarfile.open(archive) as tar:
                tar.extractall(deps, filter='data')
        build = ROOT / 'build/patchelf'
        build.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(source / 'configure'), '--prefix=' + str(prefix)], cwd=build, check=True)
        subprocess.run(['make', '-j2'], cwd=build, check=True)
        subprocess.run(['make', 'install'], cwd=build, check=True)


if __name__ == '__main__':
    main()
