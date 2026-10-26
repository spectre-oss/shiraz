#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build an unsigned stock-SDK fixture APK. Downloads/tools stay local-only."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile
import wave
from fixture_images import prepare

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.deps/android-sdk'
DOWNLOADS = {
    'platform-35_r02.zip': (
        'https://dl.google.com/android/repository/platform-35_r02.zip',
        '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
    'aapt2-linux.jar': (
        'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.9.3-12782657/aapt2-8.9.3-12782657-linux.jar',
        '8c73ebe54ab56b2d64ad499afb10f1864112b3003218befc98cd604b80c71399'),
}


def tools():
    TOOLS.mkdir(parents=True, exist_ok=True)
    for name, (url, checksum) in DOWNLOADS.items():
        archive = TOOLS / name
        if not archive.exists():
            urllib.request.urlretrieve(url, archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum:
            raise SystemExit('SDK download checksum mismatch: ' + name)
    with zipfile.ZipFile(TOOLS / 'platform-35_r02.zip') as archive:
        entries = [name for name in archive.namelist() if name.endswith('/android.jar')]
        if len(entries) != 1:
            raise SystemExit('Unexpected SDK archive layout')
        (TOOLS / 'android.jar').write_bytes(archive.read(entries[0]))
    with zipfile.ZipFile(TOOLS / 'aapt2-linux.jar') as archive:
        (TOOLS / 'aapt2').write_bytes(archive.read('aapt2'))
    (TOOLS / 'aapt2').chmod(0o755)


def main():
    tools()
    build = ROOT / 'build/apk-fixture'
    classes = build / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    generated = build / 'generated'
    if generated.exists():
        shutil.rmtree(generated)
    generated.mkdir()
    compiled = build / 'resources.zip'
    assets = build / 'assets'
    if assets.exists():
        shutil.rmtree(assets)
    shutil.copytree(ROOT / 'tests/apk/assets', assets)
    # Generated compressed asset larger than the old whole-buffer limit.
    import zlib
    block = bytes((i * 31 + (i >> 8)) & 255 for i in range(8192))
    checksum = 0
    with (assets / 'large-stream.dat').open('wb') as output:
        for _ in range(2049):
            output.write(block)
            checksum = zlib.crc32(block, checksum)
    (assets / 'large-stream-crc.txt').write_text(str(checksum & 0xffffffff))
    resource_source = build / 'resource-source'
    if resource_source.exists():
        shutil.rmtree(resource_source)
    shutil.copytree(ROOT / 'tests/apk/res', resource_source)
    prepare(resource_source, assets)
    subprocess.run([str(TOOLS / 'aapt2'), 'compile', '--dir', str(resource_source), '-o', str(compiled)], check=True)
    with wave.open(str(assets / 'effect.wav'), 'wb') as sound:
        sound.setparams((1, 2, 48000, 4800, 'NONE', 'not compressed'))
        sound.writeframes(b'\x00\x20' * 4800)
    apk = build / 'fixture.apk'
    subprocess.run([str(TOOLS / 'aapt2'), 'link', '-o', str(apk), '--manifest', str(ROOT / 'tests/apk/AndroidManifest.xml'),
                    '-I', str(TOOLS / 'android.jar'), '--version-code', '1', '-A', str(assets), '-0', 'txt', '-0', 'wav',
                    '--java', str(generated), str(compiled)], check=True)
    sources = sorted((ROOT / 'tests/apk/src').rglob('*.java')) + sorted(generated.rglob('*.java'))
    subprocess.run(['javac', '--release', '8', '-cp', str(TOOLS / 'android.jar'), '-d', str(classes), *map(str, sources)], check=True)
    dex = build / 'classes.dex'
    dx = ROOT / 'context/art_standalone/out/host/linux-x86/bin/dx'
    subprocess.run([str(dx), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True)
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex, 'classes.dex')
    print(apk.relative_to(ROOT))


if __name__ == '__main__':
    main()
