#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a stock-SDK Android API probe; optional NDK payloads stay in build/."""
import argparse
import hashlib
import json
import platform
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

from bootstrap_boot import compiler
from build_apk_fixture import TOOLS, tools
from probe_tls_fixtures import generate as tls_fixtures

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))

SOURCE = ROOT / 'tests/api-probe'


def catalog():
    entries = json.loads((SOURCE / 'catalog.json').read_text())
    ids = set()
    for entry in entries:
        if set(entry) != {'id', 'category', 'expected', 'mode'}:
            raise ValueError('invalid catalog fields')
        if entry['id'] in ids or entry['mode'] not in ('automatic', 'manual'):
            raise ValueError('duplicate id or invalid mode')
        ids.add(entry['id'])
    return entries


def generated_sources(directory):
    def quote(value):
        return json.dumps(value, ensure_ascii=True)
    fingerprint = hashlib.sha256()
    for path in sorted(SOURCE.rglob('*')):
        if path.is_file():
            fingerprint.update(path.relative_to(SOURCE).as_posix().encode())
            fingerprint.update(b'\0')
            fingerprint.update(path.read_bytes())
            fingerprint.update(b'\0')
    entries = ',\n'.join('new ProbeCase(' + ','.join(quote(e[k]) for k in ('id', 'category', 'expected', 'mode')) + ')'
                          for e in catalog())
    directory.mkdir(parents=True, exist_ok=True)
    (directory / 'ProbeCatalog.java').write_text(
        'package org.shiraz.apiprobe;\nfinal class ProbeCatalog {\n'
        'static final ProbeCase[] CASES = {\n' + entries + '\n};\n}\n')
    (directory / 'BuildConfig.java').write_text(
        'package org.shiraz.apiprobe;\nfinal class BuildConfig { static final String VERSION = '
        + quote((ROOT / 'VERSION').read_text().strip()) + '; static final String FINGERPRINT = '
        + quote(fingerprint.hexdigest()) + '; }\n')


def append_dex(apk, dex):
    with zipfile.ZipFile(apk, 'a') as archive:
        entry = zipfile.ZipInfo('classes.dex', date_time=(1980, 1, 1, 0, 0, 0))
        padding = (-(archive.fp.tell() + 30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())


def native_libraries(ndk, work):
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    binaries = ndk / 'toolchains/llvm/prebuilt' / host / 'bin'
    result = []
    for abi, triple in [('x86_64', 'x86_64-linux-android'), ('arm64-v8a', 'aarch64-linux-android')]:
        compiler = binaries / (triple + '27-clang')
        if not compiler.is_file():
            raise SystemExit(f'NDK compiler missing: {compiler}')
        target = work / abi / 'libshiraz_api_probe.so'
        target.parent.mkdir()
        subprocess.run([str(compiler), '-shared', '-fPIC', '-O2', '-Wall', '-Wextra', '-Werror',
                        '-Wl,-z,max-page-size=16384', str(SOURCE / 'native_probe.c'), '-o', str(target),
                        '-landroid', '-ljnigraphics', '-ldl'], check=True, timeout=120)
        result.append((target, f'lib/{abi}/{target.name}'))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, help='Existing local Android NDK; builds x86_64 and arm64-v8a JNI probes')
    args = parser.parse_args()
    tools()
    work = ROOT / 'build/android-api-probe'
    work.mkdir(parents=True, exist_ok=True)
    scratch = work / 'work'
    if scratch.exists():
        shutil.rmtree(scratch)
    scratch.mkdir()
    assets = scratch / 'assets'
    shutil.copytree(SOURCE / 'assets', assets)
    tls_fixtures(assets)
    classes, generated = scratch / 'classes', scratch / 'generated'
    classes.mkdir()
    generated_sources(generated)
    resources, pending = scratch / 'resources.zip', scratch / 'pending.apk'
    subprocess.run([str(TOOLS / 'aapt2'), 'compile', '--dir', str(SOURCE / 'res'), '-o', str(resources)], check=True, timeout=120)
    subprocess.run([str(TOOLS / 'aapt2'), 'link', '-o', str(pending), '--manifest', str(SOURCE / 'AndroidManifest.xml'),
                    '-I', str(TOOLS / 'android.jar'), '--version-code', '1', '--version-name', (ROOT / 'VERSION').read_text().strip(),
                    '--java', str(generated), '-A', str(assets), str(resources)], check=True, timeout=120)
    subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-cp', str(TOOLS / 'android.jar'), '-d', str(classes),
                    *map(str, sorted(SOURCE.glob('*.java')) + sorted(generated.rglob('*.java')))], check=True, timeout=120)
    program = scratch / 'classes.jar'
    with zipfile.ZipFile(program, 'w') as archive:
        for path in sorted(classes.rglob('*.class')):
            archive.write(path, path.relative_to(classes).as_posix())
    dex_dir = scratch / 'dex'
    dex_dir.mkdir()
    subprocess.run(['java', '-cp', str(compiler(ROOT)), 'com.android.tools.r8.D8', '--min-api', '27',
                    '--lib', str(TOOLS / 'android.jar'), '--output', str(dex_dir), str(program)], check=True, timeout=120)
    if sorted(p.name for p in dex_dir.iterdir()) != ['classes.dex']:
        raise SystemExit('Probe must fit one DEX; refusing partial output')
    dex = dex_dir / 'classes.dex'
    append_dex(pending, dex)
    if args.ndk:
        with zipfile.ZipFile(pending, 'a') as archive:
            for path, name in native_libraries(args.ndk.resolve(), scratch):
                archive.write(path, name)
    apk = work / 'api-probe-unsigned.apk'
    pending.replace(apk)
    print(f'API_PROBE_APK {apk}\nCATALOG {len(catalog())} cases; JNI payloads: {bool(args.ndk)}; unsigned')


if __name__ == '__main__':
    main()
