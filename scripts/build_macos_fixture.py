#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the pure-Java macOS fixture APKs with the host aapt2 and dx.

--fixture minimal (default, tests/macos-apk -> build/macos-apk/minimal.apk):
one Activity whose content is a solid View, no resources beyond the manifest.
--fixture graphics (tests/macos-apk-graphics -> build/macos-apk/graphics.apk):
text, paths and PNG/WebP/JPEG assets generated at build time by
scripts/macos_graphics_images.py, so no image binary is kept in git.
--fixture gles (tests/macos-apk-gles -> build/macos-apk/gles.apk): ES 2/3 and
ES 1 GLSurfaceViews beside a coloured View and a TextView; no assets.
--fixture gles-anim (tests/macos-apk-gles-anim -> build/macos-apk/gles-anim.apk):
M4b continuous ES 2 animation encoding the frame id in its colour; no assets.
--fixture framerate (tests/macos-apk-framerate -> build/macos-apk/framerate.apk):
an unpaced Canvas View that redraws every frame and logs SHIRAZ_FRAMERATE; no assets.
Every fixture has no lib/ directory and an uncompressed, 4-byte aligned classes.dex.
The result is checked with shiraz.apk.inspect_apk before it is moved into
place, so a malformed or native-code APK is never published.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from build_apk_fixture import TOOLS, aapt2_jar, tools  # noqa: E402
from shiraz.apk import inspect_apk  # noqa: E402
from shiraz.host_layout import host_out_dir  # noqa: E402

ART = ROOT / 'context/art_standalone'


def graphics_assets(directory):
    # Imported here so the minimal fixture build does not depend on it.
    import macos_graphics_images

    return macos_graphics_images.write(directory)


# name: (source tree, default output, work directory, asset generator or None)
FIXTURES = {
    'minimal': (
        ROOT / 'tests/macos-apk',
        ROOT / 'build/macos-apk/minimal.apk',
        ROOT / 'build/macos-apk/work',
        None,
    ),
    'graphics': (
        ROOT / 'tests/macos-apk-graphics',
        ROOT / 'build/macos-apk/graphics.apk',
        ROOT / 'build/macos-apk/graphics-work',
        graphics_assets,
    ),
    'gles': (
        ROOT / 'tests/macos-apk-gles',
        ROOT / 'build/macos-apk/gles.apk',
        ROOT / 'build/macos-apk/gles-work',
        None,
    ),
    'gles-anim': (
        ROOT / 'tests/macos-apk-gles-anim',
        ROOT / 'build/macos-apk/gles-anim.apk',
        ROOT / 'build/macos-apk/gles-anim-work',
        None,
    ),
    'framerate': (
        ROOT / 'tests/macos-apk-framerate',
        ROOT / 'build/macos-apk/framerate.apk',
        ROOT / 'build/macos-apk/framerate-work',
        None,
    ),
}


def append_aligned_dex(apk, dex):
    """Store classes.dex uncompressed with its data 4-byte aligned (mmap-able by ART)."""
    with zipfile.ZipFile(apk, 'a') as archive:
        entry = zipfile.ZipInfo('classes.dex', date_time=(1980, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_STORED
        padding = (-(archive.fp.tell() + 30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    with zipfile.ZipFile(apk) as archive:
        info = archive.getinfo('classes.dex')
        with apk.open('rb') as stream:
            stream.seek(info.header_offset + 26)
            name_length, extra_length = struct.unpack('<HH', stream.read(4))
        offset = info.header_offset + 30 + name_length + extra_length
        if info.compress_type != zipfile.ZIP_STORED or offset % 4:
            raise SystemExit('classes.dex is not stored and 4-byte aligned')
    return offset


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', choices=sorted(FIXTURES), default='minimal')
    parser.add_argument('--output', type=Path, help='default: build/macos-apk/<fixture>.apk')
    args = parser.parse_args()
    source, default_output, work, generate_assets = FIXTURES[args.fixture]
    args.output = args.output or default_output
    tools()
    dx = host_out_dir(ART) / 'bin/dx'
    if not dx.is_file():
        raise SystemExit(f'missing host dx: {dx} (build ART first)')
    if work.exists():
        shutil.rmtree(work)
    classes = work / 'classes'
    classes.mkdir(parents=True)
    android_jar = TOOLS / 'android.jar'
    subprocess.run(
        [
            'javac',
            '--release',
            '8',
            '-Xlint:-options',
            '-cp',
            str(android_jar),
            '-d',
            str(classes),
            *map(str, sorted(source.rglob('*.java'))),
        ],
        check=True,
        timeout=300,
    )
    dex = work / 'classes.dex'
    # dx is a JVM tool; keep its temporary files in the work tree.
    env = {**os.environ, 'TMPDIR': str(work)}
    subprocess.run(
        [str(dx), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)],
        check=True,
        timeout=300,
        env=env,
    )
    pending = work / (args.fixture + '.pending.apk')
    assets = []
    if generate_assets:
        asset_dir = work / 'assets'
        assets = [path.name for path in generate_assets(asset_dir)]
    subprocess.run(
        [
            str(TOOLS / 'aapt2'),
            'link',
            '-o',
            str(pending),
            '--manifest',
            str(source / 'AndroidManifest.xml'),
            '-I',
            str(android_jar),
            *(['-A', str(asset_dir)] if assets else []),
        ],
        check=True,
        timeout=120,
    )
    dex_offset = append_aligned_dex(pending, dex)
    report = inspect_apk(pending)
    with zipfile.ZipFile(pending) as archive:
        names = archive.namelist()
    if any(name.startswith('lib/') for name in names) or report['native_libraries']:
        raise SystemExit('macOS fixture must not contain lib/ native code')
    if [item['name'] for item in report['dex']] != ['classes.dex']:
        raise SystemExit('macOS fixture must contain exactly one classes.dex')
    missing = [name for name in assets if 'assets/' + name not in names]
    if missing:
        raise SystemExit('macOS fixture is missing generated assets: ' + ', '.join(missing))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    pending.replace(args.output)
    data = args.output.read_bytes()
    summary = {
        'apk': str(args.output.relative_to(ROOT))
        if args.output.is_relative_to(ROOT)
        else str(args.output),
        'bytes': len(data),
        'sha256': hashlib.sha256(data).hexdigest(),
        'entries': names,
        'classes_dex_offset': dex_offset,
        'aapt2_jar': aapt2_jar(),
        'dx': str(dx.relative_to(ROOT)),
        'package': report['manifest'].get('package'),
        'dex': report['dex'],
        'native_abis': report['native_abis'],
    }
    (args.output.parent / (args.output.stem + '.json')).write_text(
        json.dumps(summary, indent=2, sort_keys=True) + '\n'
    )
    print(json.dumps(summary, sort_keys=True))


if __name__ == '__main__':
    main()
