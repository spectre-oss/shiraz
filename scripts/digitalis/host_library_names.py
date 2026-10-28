# SPDX-License-Identifier: GPL-3.0-only
"""Give host-only implementation libraries distinct mapped filenames.

Retain the original dependency names as loader aliases. Package symlinks intact;
copying their contents into regular files loses the host/guest distinction.
"""
from pathlib import Path
import sys


def prepare(directory):
    directory = Path(directory)
    alias = directory / 'libnativebridge.so'
    implementation = directory / 'libshiraz-host-bridge.so'
    if alias.is_symlink():
        if alias.readlink() != Path(implementation.name) or not implementation.is_file():
            raise RuntimeError('Unexpected host nativebridge alias in ' + str(directory))
        return
    if not alias.exists():
        return
    if not alias.is_file() or implementation.is_symlink():
        raise RuntimeError('Unexpected host nativebridge file in ' + str(directory))
    alias.replace(implementation)
    alias.symlink_to(implementation.name)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Usage: host_library_names.py HOST_LIBRARY_DIRECTORY [...]')
    for directory in sys.argv[1:]:
        prepare(directory)
