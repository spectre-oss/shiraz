#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Package the built browser assembler (AppImage & Flatpak bases) for Pages and GitHub Releases."""

import hashlib
import json
from pathlib import Path
import shutil
import struct
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
MAX_PAGES_SITE_BYTES = 850 * 1024 * 1024  # Leave safety margin under GitHub Pages' 1 GB limit.


def verify_flatpak_base(path: Path, expected_arch: str) -> None:
    raw_head = path.read_bytes()
    if len(raw_head) < 16 or raw_head[:8] != b'SHIRAZFP':
        raise RuntimeError(f'Invalid Flatpak base header: {path.name}')
    version, index_len = struct.unpack_from('<II', raw_head, 8)
    if version != 1 or index_len == 0 or 16 + index_len > len(raw_head):
        raise RuntimeError(f'Corrupt Flatpak base index: {path.name}')
    index = json.loads(raw_head[16 : 16 + index_len].decode('utf-8'))
    if index.get('architecture') != expected_arch:
        raise RuntimeError(f'Flatpak base architecture mismatch: {path.name}')


def main():
    version = (ROOT / 'VERSION').read_text().strip()
    web = ROOT / 'go-wasm'
    sources = {
        'index.html': web / 'demo.html',
        'shiraz-assemble.wasm': web / 'shiraz-assemble.wasm',
        'wasm_exec.js': web / 'wasm_exec.js',
        'fonts/Manrope-wght.ttf': web / 'fonts/Manrope-wght.ttf',
        'fonts/OFL.txt': web / 'fonts/OFL.txt',
        'fonts/README.md': web / 'fonts/README.md',
    }
    site = ROOT / 'build/pages-site'
    if site.exists():
        shutil.rmtree(site)
    frontend = ROOT / 'landing/build'
    if not (frontend / 'index.html').is_file():
        raise RuntimeError('Build the SvelteKit frontend first: make landing-build')
    shutil.copytree(frontend, site)
    for name, source in sources.items():
        if name == 'index.html':
            continue  # Pages uses SvelteKit; the offline ZIP retains the standalone builder.
        target = site / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    (site / '.nojekyll').touch()
    (site / 'base').mkdir()
    bases = {}
    from build_appimage import require_architecture

    for architecture in ('x86_64', 'aarch64'):
        name = f'Shiraz-Browser-Base-{version}-{architecture}.AppImage'
        base = ROOT / 'dist' / name
        digest, named = base.with_suffix('.AppImage.sha256').read_text().strip().split(None, 1)
        if named != name or hashlib.sha256(base.read_bytes()).hexdigest() != digest:
            raise RuntimeError('browser base checksum mismatch: ' + architecture)
        require_architecture(base, architecture)
        location = 'base/' + name
        shutil.copy2(base, site / location)
        appimage_entry = dict(base_file=location, base_sha256=digest, base_size=base.stat().st_size)
        formats = {'appimage': appimage_entry}

        fp_name = f'Shiraz-Flatpak-Base-{version}-{architecture}.flatpak-base'
        fp_base = ROOT / 'dist' / fp_name
        if fp_base.is_file():
            fp_digest, fp_named = (
                fp_base.with_suffix('.flatpak-base.sha256').read_text().strip().split(None, 1)
            )
            if fp_named != fp_name or hashlib.sha256(fp_base.read_bytes()).hexdigest() != fp_digest:
                raise RuntimeError('Flatpak base checksum mismatch: ' + architecture)
            verify_flatpak_base(fp_base, architecture)
            fp_location = 'base/' + fp_name
            shutil.copy2(fp_base, site / fp_location)
            formats['flatpak'] = dict(
                base_file=fp_location, base_sha256=fp_digest, base_size=fp_base.stat().st_size
            )

        bases[architecture] = {**appimage_entry, 'formats': formats}

    browser_runtime = None
    for ext, fmt in (('.erofs', 'erofs'), ('.img', 'ext4')):
        rt_name = f'Shiraz-Browser-Runtime-{version}-aarch64{ext}'
        rt_path = ROOT / 'dist' / rt_name
        if rt_path.is_file():
            rt_sha_file = Path(str(rt_path) + '.sha256')
            rt_digest, rt_named = rt_sha_file.read_text().strip().split(None, 1)
            if rt_named != rt_name or hashlib.sha256(rt_path.read_bytes()).hexdigest() != rt_digest:
                raise RuntimeError('browser runtime checksum mismatch: ' + rt_name)
            rt_location = 'base/' + rt_name
            shutil.copy2(rt_path, site / rt_location)
            browser_runtime = dict(
                file=rt_location,
                sha256=rt_digest,
                size=rt_path.stat().st_size,
                format=fmt,
                architecture='aarch64',
            )
            break

    manifest = {
        'version': version,
        'architectures': bases,
        'base_file': bases['x86_64']['base_file'],
        'base_sha256': bases['x86_64']['base_sha256'],
        'base_size': bases['x86_64']['base_size'],
        'release_url': f'https://github.com/spectre-oss/shiraz/releases/tag/v{version}',
    }
    if browser_runtime is not None:
        manifest['browser_runtime'] = browser_runtime
    (site / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n')

    total_site_bytes = sum(p.stat().st_size for p in site.rglob('*') if p.is_file())
    if total_site_bytes > MAX_PAGES_SITE_BYTES:
        raise RuntimeError(
            f'Pages site ({total_site_bytes} bytes) exceeds budget ({MAX_PAGES_SITE_BYTES} bytes)'
        )

    bundle = ROOT / 'dist' / f'Shiraz-Browser-Builder-{version}.zip'
    with ZipFile(bundle, 'w', ZIP_DEFLATED, compresslevel=9) as archive:
        for name, source in sources.items():
            archive.write(source, name)
    print(f'PAGES_SITE_READY {site} ({total_site_bytes} bytes)')
    print(f'BUILDER_RELEASE_READY {bundle}')


if __name__ == '__main__':
    main()
