#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Bootstrap pinned in-tree KhronosGroup MoltenVK v1.4.2 for macOS Vulkan-over-Metal.

Downloads (if absent) and verifies the upstream MoltenVK-macos.tar archive under
.deps/, extracts libMoltenVK.dylib into .local/lib/libMoltenVK.dylib using an
atomic rename (never overwriting a Mach-O dylib in place), records Apache-2.0
license and provenance files under .local/share/licenses/moltenvk/, and stamps
build/macos/deps/moltenvk.stamp.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / '.deps'
LOCAL_LIB = ROOT / '.local/lib'
LICENSE_DIR = ROOT / '.local/share/licenses/moltenvk'
STAMP = ROOT / 'build/macos/deps/moltenvk.stamp'

MOLTENVK_VERSION = '1.4.2'
MOLTENVK_URL = f'https://github.com/KhronosGroup/MoltenVK/releases/download/v{MOLTENVK_VERSION}/MoltenVK-macos.tar'
MOLTENVK_ARCHIVE = DEPS / f'MoltenVK-v{MOLTENVK_VERSION}-macos.tar'
MOLTENVK_SHA256 = 'f95765a6229cb7b915990a2890ce12ebe36a730b021545d3d52ae69ce4c4024e'
ARCHIVE_DYLIB_MEMBER = 'MoltenVK/MoltenVK/dynamic/dylib/macOS/libMoltenVK.dylib'
ARCHIVE_ICD_MEMBER = 'MoltenVK/MoltenVK/dynamic/dylib/macOS/MoltenVK_icd.json'
ARCHIVE_LICENSE_MEMBER = 'MoltenVK/LICENSE'

INSTALLED_DYLIB = LOCAL_LIB / 'libMoltenVK.dylib'
INSTALLED_ICD_JSON = ROOT / '.local/share/vulkan/icd.d/MoltenVK_icd.json'


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def ensure_archive() -> Path:
    DEPS.mkdir(parents=True, exist_ok=True)
    if MOLTENVK_ARCHIVE.is_file():
        actual = sha256_file(MOLTENVK_ARCHIVE)
        if actual == MOLTENVK_SHA256:
            return MOLTENVK_ARCHIVE
        raise RuntimeError(
            f'Checksum mismatch for existing {MOLTENVK_ARCHIVE.name}: '
            f'expected {MOLTENVK_SHA256}, got {actual}'
        )
    tmp = MOLTENVK_ARCHIVE.with_suffix('.tar.download')
    try:
        with urllib.request.urlopen(MOLTENVK_URL, timeout=120) as resp, tmp.open('wb') as out:
            shutil.copyfileobj(resp, out)
        actual = sha256_file(tmp)
        if actual != MOLTENVK_SHA256:
            raise RuntimeError(
                f'Checksum mismatch for downloaded {MOLTENVK_URL}: '
                f'expected {MOLTENVK_SHA256}, got {actual}'
            )
        tmp.replace(MOLTENVK_ARCHIVE)
    finally:
        tmp.unlink(missing_ok=True)
    return MOLTENVK_ARCHIVE


def install_moltenvk(force: bool = False) -> dict[str, str]:
    if platform.system() != 'Darwin':
        raise RuntimeError('scripts/bootstrap_vulkan_darwin.py is macOS-only')
    if not force and STAMP.is_file() and INSTALLED_DYLIB.is_file():
        try:
            recorded = json.loads(STAMP.read_text(encoding='utf-8'))
            if (
                recorded.get('version') == MOLTENVK_VERSION
                and recorded.get('archive_sha256') == MOLTENVK_SHA256
                and recorded.get('dylib_sha256') == sha256_file(INSTALLED_DYLIB)
            ):
                return recorded
        except (OSError, ValueError):
            pass

    archive = ensure_archive()
    LOCAL_LIB.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)
    INSTALLED_ICD_JSON.parent.mkdir(parents=True, exist_ok=True)
    STAMP.parent.mkdir(parents=True, exist_ok=True)

    tmp_dylib = LOCAL_LIB / 'libMoltenVK.dylib.tmp'
    try:
        with tarfile.open(archive, 'r:*') as tar:
            dylib_member = tar.getmember(ARCHIVE_DYLIB_MEMBER)
            if not dylib_member.isfile() or dylib_member.size <= 0 or dylib_member.size > 128 << 20:
                raise RuntimeError(f'Unexpected MoltenVK dylib entry: {ARCHIVE_DYLIB_MEMBER}')
            src = tar.extractfile(dylib_member)
            if src is None:
                raise RuntimeError(f'Cannot read {ARCHIVE_DYLIB_MEMBER}')
            with src, tmp_dylib.open('wb') as dst:
                shutil.copyfileobj(src, dst)
            os.chmod(tmp_dylib, 0o755)

            lic_member = tar.getmember(ARCHIVE_LICENSE_MEMBER)
            lic_src = tar.extractfile(lic_member)
            if lic_src is None:
                raise RuntimeError(f'Cannot read {ARCHIVE_LICENSE_MEMBER}')
            with lic_src:
                (LICENSE_DIR / 'LICENSE').write_bytes(lic_src.read())

        # Verify Mach-O arm64 slice and ensure valid ad-hoc signature before atomic swap.
        archs = subprocess.check_output(['lipo', '-archs', str(tmp_dylib)], text=True).split()
        if 'arm64' not in archs:
            raise RuntimeError(f'libMoltenVK.dylib missing arm64 slice (found: {archs})')
        subprocess.run(
            ['codesign', '--force', '--sign', '-', str(tmp_dylib)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.run(
            ['codesign', '--verify', '--strict', str(tmp_dylib)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        dylib_sha = sha256_file(tmp_dylib)
        tmp_dylib.replace(INSTALLED_DYLIB)
    finally:
        tmp_dylib.unlink(missing_ok=True)

    icd_payload = {
        'file_format_version': '1.0.0',
        'ICD': {
            'library_path': str(INSTALLED_DYLIB.resolve()),
            'api_version': '1.4.0',
            'is_portability_driver': True,
        },
    }
    INSTALLED_ICD_JSON.write_text(json.dumps(icd_payload, indent=2) + '\n', encoding='utf-8')

    provenance = {
        'component': 'MoltenVK',
        'version': MOLTENVK_VERSION,
        'license': 'Apache-2.0',
        'url': MOLTENVK_URL,
        'archive_sha256': MOLTENVK_SHA256,
        'dylib_path': str(INSTALLED_DYLIB.relative_to(ROOT)),
        'dylib_sha256': dylib_sha,
    }
    (LICENSE_DIR / 'PROVENANCE').write_text(
        json.dumps(provenance, indent=2, sort_keys=True) + '\n', encoding='utf-8'
    )
    STAMP.write_text(json.dumps(provenance, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return provenance


def check_moltenvk() -> dict[str, str]:
    if not STAMP.is_file() or not INSTALLED_DYLIB.is_file():
        raise RuntimeError(
            'In-tree MoltenVK is not installed; run `python3 scripts/bootstrap_vulkan_darwin.py`.'
        )
    recorded = json.loads(STAMP.read_text(encoding='utf-8'))
    actual = sha256_file(INSTALLED_DYLIB)
    if recorded.get('dylib_sha256') != actual:
        raise RuntimeError(
            f'Installed libMoltenVK.dylib SHA-256 mismatch: expected {recorded.get("dylib_sha256")}, got {actual}'
        )
    return recorded


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='Bootstrap pinned in-tree MoltenVK on macOS.')
    parser.add_argument('action', nargs='?', choices=('install', 'check'), default='install')
    parser.add_argument('--force', action='store_true')
    args = parser.parse_args(argv)
    result = check_moltenvk() if args.action == 'check' else install_moltenvk(force=args.force)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
