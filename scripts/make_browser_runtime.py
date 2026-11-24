#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a trimmed, read-only aarch64 Shiraz runtime disk image for unkernel in the browser."""

from __future__ import annotations

import argparse
import hashlib
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]

# Desktop-only runtime payloads not used by the browser software/headless unkernel runner.
# Dropping libLLVM/libgallium/swrast/libpython shrinks the unpacked runtime by ~182 MiB.
DROP_EXACT = {
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

DROP_PREFIXES = (
    'usr/android-lib/',
    'usr/share/shiraz/installer/',
    'usr/lib/libLLVM',
    'usr/lib/libgallium',
    'usr/lib/libpython',
    'usr/lib/dri/',
    'usr/lib/pipewire-0.3/',
    'usr/lib/spa-0.2/',
    'usr/share/pipewire/',
)

DROP_LIB_SUFFIXES = ('_dri.so',)


def should_drop(relative: str) -> bool:
    if relative in DROP_EXACT:
        return True
    if any(relative.startswith(prefix) for prefix in DROP_PREFIXES):
        return True
    if relative.startswith('usr/lib/') and any(
        relative.endswith(suffix) for suffix in DROP_LIB_SUFFIXES
    ):
        return True
    return False


def stage_tree(source: Path, dest: Path) -> int:
    """Copy a Shiraz AppDir or runtime-template tarball into dest, stripping unused files."""
    if source.is_file():
        with tarfile.open(source, 'r:*') as archive:
            archive.extractall(dest, filter='data')
    elif source.is_dir():
        for entry in sorted(source.rglob('*')):
            relative = entry.relative_to(source).as_posix()
            if should_drop(relative):
                continue
            target = dest / relative
            if entry.is_symlink():
                target.parent.mkdir(parents=True, exist_ok=True)
                os.symlink(os.readlink(entry), target)
            elif entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            elif entry.is_file():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(entry, target)
    else:
        raise FileNotFoundError(f'Runtime source does not exist: {source}')

    # If extracted from a tarball, still prune dropped paths.
    if source.is_file():
        for entry in sorted(dest.rglob('*'), reverse=True):
            relative = entry.relative_to(dest).as_posix()
            if should_drop(relative):
                if entry.is_dir() and not entry.is_symlink():
                    shutil.rmtree(entry, ignore_errors=True)
                else:
                    entry.unlink(missing_ok=True)

    required = [
        dest / 'usr/bin/shiraz-app',
        dest / 'usr/art/bin/dalvikvm',
        dest / 'usr/lib/ld-linux-aarch64.so.1',
        dest / 'usr/share/shiraz/shiraz-framework.jar',
        dest / 'usr/share/shiraz/framework-res.apk',
    ]
    missing = [str(p.relative_to(dest)) for p in required if not p.is_file()]
    if missing:
        raise RuntimeError('Trimmed aarch64 runtime missing required files: ' + ', '.join(missing))

    total_bytes = 0
    for entry in dest.rglob('*'):
        if entry.is_file() and not entry.is_symlink():
            total_bytes += entry.stat().st_size
    return total_bytes


def verify_aarch64_elfs(root: Path) -> None:
    sys.path.insert(0, str(ROOT / 'scripts'))
    from build_appimage import elf, require_architecture

    for entry in root.rglob('*'):
        if entry.is_file() and not entry.is_symlink() and elf(entry):
            require_architecture(entry, 'aarch64')


def build_erofs(staged: Path, output: Path) -> None:
    mkfs = shutil.which('mkfs.erofs')
    if not mkfs:
        raise RuntimeError('mkfs.erofs not found; install erofs-utils or use --format ext4')
    temp = output.with_suffix(output.suffix + '.tmp')
    temp.unlink(missing_ok=True)
    subprocess.run(
        [mkfs, '-zlz4hc', '-L', 'shirazrt', '-T', '0', str(temp), str(staged)],
        check=True,
    )
    temp.replace(output)


def build_ext4(staged: Path, output: Path, total_bytes: int) -> None:
    mke2fs = shutil.which('mke2fs') or shutil.which('mkfs.ext4')
    if not mke2fs:
        raise RuntimeError('mke2fs/mkfs.ext4 not found; install e2fsprogs')
    # Add ~18% headroom + 12 MiB for ext4 metadata and inodes; align to 4 MiB.
    size_mib = max(32, math.ceil((total_bytes * 1.18 + 12 * 1024 * 1024) / (4 * 1024 * 1024)) * 4)
    temp = output.with_suffix(output.suffix + '.tmp')
    temp.unlink(missing_ok=True)
    subprocess.run(
        [
            mke2fs,
            '-t',
            'ext4',
            '-b',
            '4096',
            '-O',
            '^has_journal',
            '-L',
            'shirazrt',
            '-d',
            str(staged),
            str(temp),
            f'{size_mib}M',
        ],
        check=True,
    )
    temp.replace(output)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        'source',
        type=Path,
        help='aarch64 Shiraz AppDir or runtime-template.tar.gz',
    )
    parser.add_argument(
        'output',
        type=Path,
        help='Output image path (.erofs or .img)',
    )
    parser.add_argument(
        '--format',
        choices=('auto', 'erofs', 'ext4'),
        default='auto',
        help='Filesystem format (auto chooses from output suffix)',
    )
    parser.add_argument(
        '--skip-arch-check',
        action='store_true',
        help='Skip ELF aarch64 verification (for unit test fixtures only)',
    )
    args = parser.parse_args(argv)

    fmt = args.format
    if fmt == 'auto':
        fmt = 'erofs' if args.output.suffix == '.erofs' else 'ext4'

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='shiraz-browser-rt-') as tmp:
        staged = Path(tmp) / 'runtime'
        staged.mkdir()
        total_bytes = stage_tree(args.source.resolve(), staged)
        if not args.skip_arch_check:
            verify_aarch64_elfs(staged)
        if fmt == 'erofs':
            build_erofs(staged, args.output)
        else:
            build_ext4(staged, args.output, total_bytes)

    digest = sha256_file(args.output)
    sha_path = Path(str(args.output) + '.sha256')
    sha_path.write_text(f'{digest}  {args.output.name}\n', encoding='utf-8')
    print(f'BROWSER_RUNTIME_READY {args.output} ({args.output.stat().st_size} bytes, {fmt})')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
