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


def install_runtime(site, source, version):
    from build_appimage import require_architecture
    bases = {}
    for arch in ('x86_64', 'aarch64'):
        name = f'Shiraz-Browser-Base-{version}-{arch}.AppImage'
        path = source / name
        if not path.exists():
            continue
        digest, named = path.with_suffix('.AppImage.sha256').read_text().strip().split(None, 1)
        if named != name or not re.fullmatch('[0-9a-f]{64}', digest):
            raise RuntimeError('Invalid runtime checksum file')
        with path.open('rb') as stream:
            actual = hashlib.file_digest(stream, 'sha256').hexdigest()
        if actual != digest:
            raise RuntimeError('Published runtime checksum mismatch')
        require_architecture(path, arch)
        (site / 'base').mkdir(exist_ok=True)
        shutil.copy2(path, site / 'base' / name)
        bases[arch] = dict(base_file='base/' + name, base_sha256=digest, base_size=path.stat().st_size)
    if 'x86_64' not in bases:
        raise RuntimeError('Published x86_64 runtime missing')
    manifest = dict(version=version, architectures=bases, **bases['x86_64'],
                    release_url=f'https://github.com/spectre-oss/shiraz/releases/tag/v{version}')
    (site / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (site / '.nojekyll').touch()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, default=Path('landing/build'))
    args = parser.parse_args()
    if not (args.site / 'index.html').is_file():
        raise RuntimeError('Build the SvelteKit site first')
    releases = json.loads(subprocess.check_output([
        'gh', 'api', 'repos/spectre-oss/shiraz/releases?per_page=20']))
    release = select_release(releases)
    with tempfile.TemporaryDirectory(prefix='shiraz-pages-runtime-') as tmp:
        subprocess.run(['gh', 'release', 'download', release['tag_name'], '--repo',
                        'spectre-oss/shiraz', '--pattern', 'Shiraz-Browser-Base-*.AppImage*',
                        '--dir', tmp], check=True)
        install_runtime(args.site, Path(tmp), release['tag_name'][1:])
    print('PAGES_RUNTIME_READY', release['tag_name'])


if __name__ == '__main__':
    main()
