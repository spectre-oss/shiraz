#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Create a reusable Shiraz runtime tarball from a no-AOT AppDir."""
import argparse
from pathlib import Path
import tarfile

EXCLUDE = {
    'usr/share/shiraz/original.apk',
    'usr/share/shiraz/launch.properties',
    'usr/share/shiraz/application.properties',
    'usr/share/shiraz/bundle.json',
    'usr/share/shiraz/files.sha256.json',
    'shiraz-icon.png',
    '.DirIcon',
    'shiraz.desktop',
    'usr/share/shiraz/licenses/sources/context_osmand_LICENSE',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('appdir', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    appdir = args.appdir.resolve()
    if not (appdir/'AppRun').is_file():
        parser.error('input is not a Shiraz AppDir')
    if 'aot=false' not in (appdir/'usr/share/shiraz/launch.properties').read_text():
        parser.error('template source must be built with AOT=0')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, 'w:gz') as archive:
        for entry in sorted(appdir.rglob('*')):
            relative = entry.relative_to(appdir).as_posix()
            if relative in EXCLUDE or relative.startswith('usr/android-lib/'):
                continue
            if relative.startswith('usr/share/shiraz/installer/'):
                continue
            archive.add(entry, arcname=relative, recursive=False)
        fallback = Path(__file__).resolve().parents[1]/'packaging/shiraz-app.svg'
        archive.add(fallback, arcname='shiraz-icon.svg')
    print(args.output)


if __name__ == '__main__':
    main()
