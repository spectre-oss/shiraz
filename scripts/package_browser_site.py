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
    for architecture in ('x86_64', 'aarch64'):
        name = f'Shiraz-Browser-Base-{version}-{architecture}.AppImage'
        base = ROOT / 'dist' / name
        digest, named = base.with_suffix('.AppImage.sha256').read_text().strip().split(None, 1)
        if named != name or hashlib.sha256(base.read_bytes()).hexdigest() != digest:
            raise RuntimeError('browser base checksum mismatch: ' + architecture)
        from build_appimage import require_architecture
        require_architecture(base, architecture)
        location = 'base/' + name
        shutil.copy2(base, site / location)
        bases[architecture] = dict(base_file=location, base_sha256=digest, base_size=base.stat().st_size)
    manifest = {
        'version': version, 'architectures': bases,
        **bases['x86_64'],
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
