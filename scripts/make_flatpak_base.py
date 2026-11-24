#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a reusable Shiraz Flatpak runtime base (.flatpak-base) for browser-side WASM assembly."""

import argparse
import hashlib
from pathlib import Path
import platform
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))

from build_appimage import elf, require_architecture  # noqa: E402
from ostree_bundle import (  # noqa: E402
    DEFAULT_RUNTIME_BRANCH,
    DEFAULT_RUNTIME_REPO,
    build_flatpak_base_from_files_dir,
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', type=Path, help='No-AOT Flatpak stage directory (contains files/)')
    parser.add_argument('output', type=Path, help='Output path under dist/ ending in .flatpak-base')
    parser.add_argument('--runtime-branch', default=DEFAULT_RUNTIME_BRANCH)
    parser.add_argument('--runtime-repo', default=DEFAULT_RUNTIME_REPO)
    parser.add_argument('--work-dir', type=Path, help='Override temporary working directory')
    args = parser.parse_args()

    output = args.output.resolve()
    if not output.is_relative_to((ROOT / 'dist').resolve()) or output.suffix != '.flatpak-base':
        parser.error('output must be a .flatpak-base file under dist/')
    architecture = platform.machine()
    if architecture not in ('x86_64', 'aarch64'):
        parser.error('unsupported build architecture')

    files_src = (args.stage / 'files').resolve()
    if not (files_src / 'bin/shiraz-flatpak-run').is_file():
        parser.error('stage directory is not a Shiraz Flatpak staging tree')
    launch_props = files_src / 'usr/share/shiraz/launch.properties'
    if not launch_props.is_file() or 'aot=false' not in launch_props.read_text(encoding='utf-8'):
        parser.error('Flatpak base source must be built with AOT=0')

    version = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
    output.parent.mkdir(parents=True, exist_ok=True)
    work_root = args.work_dir.resolve() if args.work_dir else ROOT / 'build'
    work_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='shiraz-flatpak-base-', dir=work_root) as tmp:
        staged_files = Path(tmp) / 'files'
        shutil.copytree(files_src, staged_files, symlinks=True)
        # Drop APK-specific trees before scanning and hashing.
        for drop in (
            staged_files / 'share',
            staged_files / 'usr/android-lib',
            staged_files / 'usr/share/shiraz/original.apk',
            staged_files / 'usr/share/shiraz/launch.properties',
            staged_files / 'usr/share/shiraz/application.properties',
            staged_files / 'usr/share/shiraz/bundle.json',
            staged_files / 'usr/share/shiraz/files.sha256.json',
            staged_files / 'usr/share/shiraz/licenses/sources/context_osmand_LICENSE',
        ):
            if drop.is_symlink() or drop.is_file():
                drop.unlink()
            elif drop.is_dir():
                shutil.rmtree(drop)
        for binary in staged_files.rglob('*'):
            if binary.is_file() and not binary.is_symlink() and elf(binary):
                require_architecture(binary, architecture)

        base_bytes = build_flatpak_base_from_files_dir(
            files_dir=staged_files,
            version=version,
            architecture=architecture,
            runtime_branch=args.runtime_branch,
            runtime_repo=args.runtime_repo,
            compress=True,
        )
        temp_output = output.with_suffix('.flatpak-base.tmp')
        temp_output.write_bytes(base_bytes)
        temp_output.replace(output)

    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.flatpak-base.sha256').write_text(
        f'{digest}  {output.name}\n', encoding='utf-8'
    )
    print(f'FLATPAK_BASE_READY {output} ({len(base_bytes)} bytes)')


if __name__ == '__main__':
    main()
