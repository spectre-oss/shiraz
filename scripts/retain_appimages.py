#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Retain the latest three numeric Shiraz versions per app in a release directory."""
import argparse
import re
from pathlib import Path

PATTERN = re.compile(r'(.+)-Shiraz-(\d+)\.(\d+)\.(\d+)-(x86_64|aarch64)\.AppImage')


def prune(directory, keep=3, apply=False):
    if keep < 1:
        raise ValueError('At least one release must be retained')
    groups = {}
    # Fixed release directory only: previews and unrelated files are not releases.
    for path in directory.iterdir():
        match = PATTERN.fullmatch(path.name)
        if match and path.is_file() and not path.is_symlink():
            version = tuple(map(int, match.group(2, 3, 4)))
            groups.setdefault(match[1], {}).setdefault(version, []).append(path)
    removed, total = [], 0
    for versions in groups.values():
        for version in sorted(versions, reverse=True)[keep:]:
            for image in versions[version]:
                for path in (image, image.with_suffix('.AppImage.sha256')):
                    if path.is_file() and not path.is_symlink():
                        total += path.stat().st_size
                        removed.append(path.name)
                        if apply:
                            path.unlink()
    return sorted(removed), total


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    import fcntl
    with (root/'build/appimage/.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        names, size = prune(root/'dist', apply=args.apply)
    for name in names:
        print(('REMOVED ' if args.apply else 'WOULD_REMOVE ') + name)
    print(f'{len(names)} files; {size} bytes; keep=3 versions per app')
