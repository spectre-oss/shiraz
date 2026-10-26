#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Package the built browser assembler for Pages and GitHub Releases."""
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]


def main():
    version = (ROOT / 'VERSION').read_text().strip()
    base_name = f'Shiraz-Browser-Base-{version}-x86_64.AppImage'
    base = ROOT / 'dist' / base_name
    checksum = base.with_suffix('.AppImage.sha256')
    digest, named = checksum.read_text().strip().split(None, 1)
    if named != base_name or hashlib.sha256(base.read_bytes()).hexdigest() != digest:
        raise RuntimeError('browser base checksum mismatch')
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
    site.mkdir(parents=True)
    for name, source in sources.items():
        target = site / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    (site / '.nojekyll').touch()
    base_location = 'base/' + base_name
    (site / 'base').mkdir()
    shutil.copy2(base, site / base_location)
    manifest = {
        'version': version,
        'base_file': base_location,
        'base_sha256': digest,
        'base_size': base.stat().st_size,
        'release_url': f'https://github.com/spectre-oss/shiraz/releases/tag/v{version}',
    }
    (site / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n')
    bundle = ROOT / 'dist' / f'Shiraz-Browser-Builder-{version}.zip'
    with ZipFile(bundle, 'w', ZIP_DEFLATED, compresslevel=9) as archive:
        for name, source in sources.items():
            archive.write(source, name)
    print(f'PAGES_SITE_READY {site}')
    print(f'BUILDER_RELEASE_READY {bundle}')


if __name__ == '__main__':
    main()
