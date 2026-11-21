#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the pinned eSpeak source into the private macOS prefix."""

import fcntl
import subprocess
from pathlib import Path

from bootstrap_runtime import SOURCES

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'context/espeak-ng'
BUILD = ROOT / '.deps/darwin-speech'


def main():
    BUILD.mkdir(parents=True, exist_ok=True)
    with (BUILD / '.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        revision = subprocess.check_output(
            ['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True
        ).strip()
        if revision != SOURCES['espeak-ng'][1]:
            raise SystemExit('Speech source differs from pinned revision; run make runtime-sources')
        subprocess.run(['git', '-C', str(SOURCE), 'diff', '--quiet', 'HEAD'], check=True)
        subprocess.run(
            [
                'cmake',
                '-S',
                str(SOURCE),
                '-B',
                str(BUILD),
                '-DCMAKE_INSTALL_PREFIX=' + str(ROOT / '.local'),
                '-DCMAKE_BUILD_TYPE=Release',
                '-DBUILD_SHARED_LIBS=OFF',
                '-DBUILD_TESTING=OFF',
                '-DUSE_ASYNC=OFF',
                '-DUSE_LIBPCAUDIO=OFF',
                '-DUSE_LIBSONIC=OFF',
                '-DUSE_MBROLA=OFF',
                '-DESPEAK_BUILD_MANPAGES=OFF',
            ],
            check=True,
        )
        subprocess.run(['cmake', '--build', str(BUILD), '--parallel', '2'], check=True)
        subprocess.run(['cmake', '--install', str(BUILD)], check=True)


if __name__ == '__main__':
    main()
