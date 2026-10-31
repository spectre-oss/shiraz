#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a reusable Shiraz AppImage base for browser-side APK payload append."""
import argparse
import hashlib
import os
import platform
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('template', type=Path)
    parser.add_argument('launcher', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT/'dist').resolve()) or output.suffix != '.AppImage':
        parser.error('output must be an AppImage under dist/')
    architecture = platform.machine()
    if architecture not in ('x86_64', 'aarch64'):
        parser.error('unsupported build architecture')
    from build_appimage import elf, require_architecture
    require_architecture(args.launcher, architecture)
    tools = ROOT/'.deps/appimage-tools'/architecture
    if not all((tools/name).is_file() for name in ('appimagetool', 'runtime')):
        parser.error('packaging tools missing')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='shiraz-browser-base-', dir=ROOT/'build') as temporary:
        appdir = Path(temporary)/'AppDir'
        appdir.mkdir()
        with tarfile.open(args.template, 'r:gz') as archive:
            archive.extractall(appdir, filter='data')
        for binary in appdir.rglob('*'):
            if binary.is_file() and elf(binary):
                require_architecture(binary, architecture)
        shutil.copy2(args.launcher, appdir/'usr/bin/shiraz-payload-launcher')
        (appdir/'AppRun').write_text('#!/bin/sh\nappdir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1\nexec "$appdir/usr/bin/shiraz-payload-launcher" "$@"\n')
        (appdir/'AppRun').chmod(0o755)
        (appdir/'.DirIcon').symlink_to('shiraz-icon.svg')
        (appdir/'shiraz.desktop').write_text('[Desktop Entry]\nType=Application\nVersion=1.5\nName=Shiraz App\nExec=AppRun\nIcon=shiraz-icon\nCategories=Utility;\nTerminal=false\n')
        temp_output = output.with_suffix('.AppImage.tmp')
        env = dict(os.environ, ARCH=architecture, APPIMAGE_EXTRACT_AND_RUN='1', VERSION=(ROOT/'VERSION').read_text().strip())
        subprocess.run([str(tools/'appimagetool'), '--runtime-file', str(tools/'runtime'), str(appdir), str(temp_output)], check=True, env=env)
        temp_output.chmod(0o755)
        temp_output.replace(output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.AppImage.sha256').write_text(f'{digest}  {output.name}\n')
    print(output)


if __name__ == '__main__':
    main()
