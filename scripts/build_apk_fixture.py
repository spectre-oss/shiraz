#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build an unsigned stock-SDK fixture APK. Downloads/tools stay local-only."""
import hashlib
import http.client
import os
import platform
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import wave
import zipfile
from pathlib import Path

from fixture_images import prepare

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.host_layout import host_out_dir  # noqa: E402

TOOLS = ROOT / '.deps/android-sdk'
DOWNLOADS = {
    'platform-35_r02.zip': (
        'https://dl.google.com/android/repository/platform-35_r02.zip',
        '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
    'aapt2-linux.jar': (
        'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.9.3-12782657/aapt2-8.9.3-12782657-linux.jar',
        '8c73ebe54ab56b2d64ad499afb10f1864112b3003218befc98cd604b80c71399'),
    # Universal x86_64+arm64 Mach-O; same aapt2 build as the Linux jar.
    'aapt2-osx.jar': (
        'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.9.3-12782657/aapt2-8.9.3-12782657-osx.jar',
        '243fc5ce0adf8e26cb211b70c9d6ca41dd27844950f020788549e298f8a0f421'),
}
# Google's edge mirror serves the same paths (dl.google.com returns 404 from some
# networks); the pinned sha256 is what is trusted, not the host.
GOOGLE_PRIMARY = 'https://dl.google.com/'
GOOGLE_MIRROR = 'https://redirector.gvt1.com/edgedl/'
DOWNLOAD_LIMITS = {'platform-35_r02.zip': 128 * 1024 * 1024}
DEFAULT_DOWNLOAD_LIMIT = 16 * 1024 * 1024


def candidate_urls(url):
    """The pinned URL, then its Google edge-mirror equivalent."""
    return [url] + ([GOOGLE_MIRROR + url[len(GOOGLE_PRIMARY):]] if url.startswith(GOOGLE_PRIMARY) else [])


def _check(path, checksum, limit, verify):
    """Raise ValueError unless path is within limit and matches checksum and verify."""
    size = path.stat().st_size
    if size > limit:
        raise ValueError(f'{size} bytes exceeds the {limit}-byte limit')
    if checksum is not None and hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
        raise ValueError('checksum mismatch')
    if verify is not None:
        verify(path)


def _transfer(candidate, temporary, limit, timeout):
    """Stream candidate into temporary; raise ValueError on an oversized or short body."""
    total = 0
    with urllib.request.urlopen(candidate, timeout=timeout) as source, temporary.open('wb') as target:
        declared = source.headers.get('Content-Length')
        declared = int(declared) if declared is not None and declared.strip().isdigit() else None
        if declared is not None and declared > limit:
            raise ValueError(f'declared {declared} bytes exceeds the {limit}-byte limit')
        while chunk := source.read(1024 * 1024):
            total += len(chunk)
            if total > limit:
                raise ValueError(f'body exceeds the {limit}-byte limit')
            target.write(chunk)
    # http.client returns a short read at EOF instead of raising IncompleteRead.
    if declared is not None and total != declared:
        raise ValueError(f'truncated body: {total} of {declared} bytes')


def fetch(path, url, checksum, limit, timeout=30, verify=None):
    """Download to a temporary name, verify size, sha256 and verify(path), then rename into place.

    Every candidate URL is tried in order; a failed transfer or a rejected body moves
    on to the next one, so the pinned checksum (or verify) alone decides what is
    accepted. A cached file that no longer verifies is discarded and fetched again.
    verify, when given, raises ValueError for unacceptable content.
    """
    path = Path(path)
    if path.exists():
        try:
            _check(path, checksum, limit, verify)
            return
        except ValueError as error:
            print(f'{path.name}: cached download rejected ({error}); fetching it again', file=sys.stderr)
            path.unlink()
    temporary = path.with_name(path.name + '.download')
    errors = []
    try:
        for candidate in candidate_urls(url):
            try:
                _transfer(candidate, temporary, limit, timeout)
                _check(temporary, checksum, limit, verify)
            except (OSError, http.client.HTTPException, ValueError) as error:
                # URLError, HTTPError, ssl.SSLError and TimeoutError are OSError.
                if isinstance(error, urllib.error.HTTPError):
                    error.close()
                errors.append(f'{candidate}: {error}')
                continue
            temporary.replace(path)
            return
    finally:
        temporary.unlink(missing_ok=True)
    raise SystemExit(f'{path.name}: download failed: ' + '; '.join(errors))


def aapt2_jar(system=None):
    """DOWNLOADS key of the aapt2 jar that runs on this build host."""
    return 'aapt2-osx.jar' if (system or platform.system()) == 'Darwin' else 'aapt2-linux.jar'


def install_aapt2(cache):
    """Install a host compiler; Linux ARM requires an explicit native tool.

    Google's Linux Maven artifact is x86_64 only. CI uses Ubuntu's native aapt
    package on ARM64 rather than executing it through an emulator.
    """
    override = os.environ.get('SHIRAZ_AAPT2')
    system, machine = platform.system(), platform.machine().lower()
    target = cache / 'aapt2'
    if override:
        source = Path(override)
        if not source.is_absolute() or not source.is_file():
            raise ValueError('SHIRAZ_AAPT2 must name an existing absolute executable path')
        if source.stat().st_size > 32 * 1024 * 1024:
            raise ValueError('compiler exceeds size limit')
        if system != 'Linux':
            raise ValueError('SHIRAZ_AAPT2 overrides are supported only on Linux')
        with source.open('rb') as stream:
            header = stream.read(20)
        expected = {'aarch64': 183, 'arm64': 183, 'x86_64': 62, 'amd64': 62}.get(machine)
        if (expected is None or len(header) != 20 or header[:6] != b'\x7fELF\x02\x01'
                or int.from_bytes(header[18:20], 'little') != expected):
            raise ValueError('SHIRAZ_AAPT2 must be a native little-endian 64-bit ELF executable')
        version = subprocess.run([str(source), 'version'], check=True, capture_output=True,
                                 text=True, timeout=10).stdout.strip()
        if not version.startswith('Android Asset Packaging Tool (aapt)'):
            raise ValueError('SHIRAZ_AAPT2 did not identify itself as aapt2')
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        target.chmod(0o755)
        return target, {'source': str(source), 'version': version,
                        'binary_sha256': hashlib.sha256(target.read_bytes()).hexdigest()}
    if system == 'Linux' and machine not in ('x86_64', 'amd64'):
        raise ValueError('Google aapt2 is x86_64-only on Linux; set SHIRAZ_AAPT2 to a native compiler')
    name = aapt2_jar()
    url, checksum = DOWNLOADS[name]
    archive_path = cache / name
    fetch(archive_path, url, checksum, DEFAULT_DOWNLOAD_LIMIT)
    with zipfile.ZipFile(archive_path) as archive:
        if archive.getinfo('aapt2').file_size > 32 * 1024 * 1024:
            raise ValueError('compiler exceeds size limit')
        target.write_bytes(archive.read('aapt2'))
    target.chmod(0o755)
    return target, {'source': url, 'archive_sha256': checksum,
                    'binary_sha256': hashlib.sha256(target.read_bytes()).hexdigest()}


def tools():
    TOOLS.mkdir(parents=True, exist_ok=True)
    for name, (url, checksum) in DOWNLOADS.items():
        if name.startswith('aapt2-'):
            continue
        fetch(TOOLS / name, url, checksum, DOWNLOAD_LIMITS.get(name, DEFAULT_DOWNLOAD_LIMIT))
    with zipfile.ZipFile(TOOLS / 'platform-35_r02.zip') as archive:
        entries = [name for name in archive.namelist() if name.endswith('/android.jar')]
        if len(entries) != 1:
            raise SystemExit('Unexpected SDK archive layout')
        (TOOLS / 'android.jar').write_bytes(archive.read(entries[0]))
    install_aapt2(TOOLS)


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
    dx = host_out_dir(ROOT / 'context/art_standalone') / 'bin/dx'
    subprocess.run([str(dx), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], check=True)
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex, 'classes.dex')
    print(apk.relative_to(ROOT))


if __name__ == '__main__':
    main()
