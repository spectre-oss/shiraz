#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Populate a static site from published runtime assets, independently of native CI."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from package_browser_site import MAX_PAGES_SITE_BYTES, verify_flatpak_base


def select_release(releases):
    candidates = []
    for release in releases:
        match = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)', release['tag_name'])
        if release.get('draft') or not match:
            continue
        version = release['tag_name'][1:]
        names = {asset['name'] for asset in release.get('assets', [])}
        name = f'Shiraz-Browser-Base-{version}-x86_64.AppImage'
        if {name, name + '.sha256'} <= names:
            candidates.append((tuple(map(int, match.groups())), release))
    if not candidates:
        raise RuntimeError('No published browser runtime found')
    return max(candidates, key=lambda entry: entry[0])[1]


def _verify_sha256_file(path: Path, suffix: str) -> str:
    digest, named = path.with_suffix(suffix + '.sha256').read_text().strip().split(None, 1)
    if named != path.name or not re.fullmatch('[0-9a-f]{64}', digest):
        raise RuntimeError('Invalid runtime checksum file: ' + path.name)
    with path.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != digest:
        raise RuntimeError('Published runtime checksum mismatch: ' + path.name)
    return digest


def install_runtime(site, source, version):
    from build_appimage import require_architecture

    bases = {}
    for arch in ('x86_64', 'aarch64'):
        name = f'Shiraz-Browser-Base-{version}-{arch}.AppImage'
        path = source / name
        if not path.exists():
            continue
        digest = _verify_sha256_file(path, '.AppImage')
        require_architecture(path, arch)
        (site / 'base').mkdir(exist_ok=True)
        shutil.copy2(path, site / 'base' / name)
        appimage_entry = dict(
            base_file='base/' + name, base_sha256=digest, base_size=path.stat().st_size
        )
        formats = {'appimage': appimage_entry}

        fp_name = f'Shiraz-Flatpak-Base-{version}-{arch}.flatpak-base'
        fp_path = source / fp_name
        if fp_path.exists():
            fp_digest = _verify_sha256_file(fp_path, '.flatpak-base')
            verify_flatpak_base(fp_path, arch)
            shutil.copy2(fp_path, site / 'base' / fp_name)
            formats['flatpak'] = dict(
                base_file='base/' + fp_name,
                base_sha256=fp_digest,
                base_size=fp_path.stat().st_size,
            )

        bases[arch] = {**appimage_entry, 'formats': formats}

    if 'x86_64' not in bases:
        raise RuntimeError('Published x86_64 runtime missing')

    browser_runtime = None
    from make_browser_runtime import verify_unkernel_erofs

    for ext, fmt in (('.erofs', 'erofs'), ('.img', 'ext4')):
        rt_name = f'Shiraz-Browser-Runtime-{version}-aarch64{ext}'
        rt_path = source / rt_name
        if rt_path.exists():
            rt_digest = _verify_sha256_file(rt_path, ext)
            if fmt == 'erofs':
                try:
                    verify_unkernel_erofs(rt_path)
                except RuntimeError as exc:
                    print(
                        f'Skipping incompatible browser EROFS ({exc}); falling back to AppImage base.'
                    )
                    continue
            (site / 'base').mkdir(exist_ok=True)
            shutil.copy2(rt_path, site / 'base' / rt_name)
            parts = tuple(int(part) for part in version.split('.'))
            features = ['fb0', 'aot'] if parts >= (0, 367, 0) else []
            browser_runtime = dict(
                file='base/' + rt_name,
                sha256=rt_digest,
                size=rt_path.stat().st_size,
                format=fmt,
                architecture='aarch64',
                features=features,
            )
            break

    manifest = dict(
        version=version,
        architectures=bases,
        base_file=bases['x86_64']['base_file'],
        base_sha256=bases['x86_64']['base_sha256'],
        base_size=bases['x86_64']['base_size'],
        release_url=f'https://github.com/spectre-oss/shiraz/releases/tag/v{version}',
    )
    if browser_runtime is not None:
        manifest['browser_runtime'] = browser_runtime
    (site / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (site / '.nojekyll').touch()
    total_site_bytes = sum(p.stat().st_size for p in site.rglob('*') if p.is_file())
    if total_site_bytes > MAX_PAGES_SITE_BYTES:
        raise RuntimeError(
            f'Pages site ({total_site_bytes} bytes) exceeds budget ({MAX_PAGES_SITE_BYTES} bytes)'
        )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, default=Path('landing/build'))
    args = parser.parse_args()
    if not (args.site / 'index.html').is_file():
        raise RuntimeError('Build the SvelteKit site first')
    releases = json.loads(
        subprocess.check_output(['gh', 'api', 'repos/spectre-oss/shiraz/releases?per_page=20'])
    )
    release = select_release(releases)
    asset_names = {asset['name'] for asset in release.get('assets', [])}
    with tempfile.TemporaryDirectory(prefix='shiraz-pages-runtime-') as tmp:
        subprocess.run(
            [
                'gh',
                'release',
                'download',
                release['tag_name'],
                '--repo',
                'spectre-oss/shiraz',
                '--pattern',
                'Shiraz-Browser-Base-*.AppImage*',
                '--dir',
                tmp,
            ],
            check=True,
        )
        if any(name.startswith('Shiraz-Flatpak-Base-') for name in asset_names):
            subprocess.run(
                [
                    'gh',
                    'release',
                    'download',
                    release['tag_name'],
                    '--repo',
                    'spectre-oss/shiraz',
                    '--pattern',
                    'Shiraz-Flatpak-Base-*.flatpak-base*',
                    '--dir',
                    tmp,
                ],
                check=True,
            )
        if any(name.startswith('Shiraz-Browser-Runtime-') for name in asset_names):
            subprocess.run(
                [
                    'gh',
                    'release',
                    'download',
                    release['tag_name'],
                    '--repo',
                    'spectre-oss/shiraz',
                    '--pattern',
                    'Shiraz-Browser-Runtime-*',
                    '--dir',
                    tmp,
                ],
                check=True,
            )
        install_runtime(args.site, Path(tmp), release['tag_name'][1:])
    print('PAGES_RUNTIME_READY', release['tag_name'])


if __name__ == '__main__':
    main()
