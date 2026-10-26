#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Finalize a Go/WASM AppDir tarball into a Linux AppImage."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('assembled', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT/'dist').resolve()) or output.suffix != '.AppImage':
        parser.error('output must be a .AppImage under dist/')
    tools = ROOT/'.deps/appimage-tools/x86_64'
    if not all((tools/name).is_file() for name in ('appimagetool', 'runtime')):
        parser.error('packaging tools missing; run make packaging-tools and make appimage once')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='shiraz-wasm-finalize-', dir=ROOT/'build') as temporary:
        appdir = Path(temporary)/'AppDir'
        appdir.mkdir()
        with tarfile.open(args.assembled, 'r:gz') as archive:
            for member in archive:
                name = Path(member.name)
                if name.is_absolute() or '..' in name.parts or member.islnk() or member.isdev():
                    raise ValueError('unsafe AppDir archive entry')
                if member.issym():
                    target = os.path.normpath(os.path.join(os.path.dirname(member.name), member.linkname))
                    if Path(member.linkname).is_absolute() or target == '..' or target.startswith('../'):
                        raise ValueError('unsafe AppDir symlink')
                archive.extract(member, appdir, filter='data')
        if not (appdir/'AppRun').is_file() or not (appdir/'usr/share/shiraz/original.apk').is_file():
            raise ValueError('incomplete AppDir')
        temp_output = output.with_suffix('.AppImage.tmp')
        env = dict(os.environ, ARCH='x86_64', APPIMAGE_EXTRACT_AND_RUN='1', VERSION=(ROOT/'VERSION').read_text().strip())
        subprocess.run([str(tools/'appimagetool'), '--runtime-file', str(tools/'runtime'), str(appdir), str(temp_output)], check=True, env=env)
        temp_output.chmod(0o755)
        temp_output.replace(output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.AppImage.sha256').write_text(f'{digest}  {output.name}\n')
    print(output)


if __name__ == '__main__':
    main()
