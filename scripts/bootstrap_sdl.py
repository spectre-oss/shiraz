#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a pinned SDL2 release locally without installing system packages."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = '2.32.10'
SHA256 = '5f5993c530f084535c65a6879e9b26ad441169b3e25d789d83287040a9ca5165'


def main():
    deps = ROOT / '.deps'
    deps.mkdir(exist_ok=True)
    archive = deps / f'SDL2-{VERSION}.tar.gz'
    if not archive.exists():
        urllib.request.urlretrieve(f'https://www.libsdl.org/release/SDL2-{VERSION}.tar.gz', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('SDL2 source checksum mismatch; remove the local archive and retry')
    source = deps / f'SDL2-{VERSION}'
    if not source.exists():
        with tarfile.open(archive) as tar:
            tar.extractall(deps, filter='data')
    patch = ROOT / 'patches/sdl-offscreen-resize.patch'
    patch_hash = hashlib.sha256(patch.read_bytes()).hexdigest()
    applied = subprocess.run(['patch', '-p1', '--forward', '--dry-run', '-i', str(patch)], cwd=source, capture_output=True)
    if applied.returncode == 0:
        subprocess.run(['patch', '-p1', '--forward', '-i', str(patch)], cwd=source, check=True)
    elif subprocess.run(['patch', '-p1', '--reverse', '--dry-run', '-i', str(patch)], cwd=source, capture_output=True).returncode:
        raise SystemExit('SDL offscreen resize patch does not match source')
    build = ROOT / 'build' / 'sdl2'
    build.mkdir(parents=True, exist_ok=True)
    configure = [str(source / 'configure'), '--prefix=' + str(ROOT / '.local'),
                 '--disable-shared', '--enable-static', '--with-pic', '--disable-video-vulkan',
                 '--enable-video-opengl', '--disable-audio', '--disable-haptic',
                 '--disable-joystick', '--disable-sensor', '--disable-libdecor']
    stamp = build / '.shiraz-config.json'
    expected = json.dumps({'archive_sha256': SHA256, 'patch_sha256': patch_hash, 'configure': configure}, sort_keys=True)
    if (build / 'Makefile').exists() and (not stamp.exists() or stamp.read_text() != expected):
        subprocess.run(['make', 'clean'], cwd=build, check=True)
    subprocess.run(configure, cwd=build, check=True)
    if os.environ.get('SHIRAZ_REQUIRE_WAYLAND') == '1':
        config = (build / 'include/SDL_config.h').read_text()
        if '#define SDL_VIDEO_DRIVER_WAYLAND 1' not in config:
            raise SystemExit('Wayland is required but SDL could not enable it; check wayland-scanner and Wayland pkg-config dependencies')
    stamp.write_text(expected)
    subprocess.run(['make', '-j' + str(min(os.cpu_count() or 2, 8))], cwd=build, check=True)
    subprocess.run(['make', 'install'], cwd=build, check=True)


if __name__ == '__main__':
    main()
