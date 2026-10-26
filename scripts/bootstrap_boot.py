#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Desugar pinned ART boot classes while retaining their resources and notices."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
R8_VERSION = '8.9.35'
R8_SHA256 = 'e93ac1c06e99ae5dbdcc835bd97bae406f2b4140be3daa523ef71e5a548927fc'
R8_URL = f'https://storage.googleapis.com/r8-releases/raw/{R8_VERSION}/r8.jar'
MODULES = ('core-oj-hostdex', 'core-libart-hostdex')
NETWORK_MODULES = ('okhttp-hostdex', 'bouncycastle-hostdex', 'wolfssljni-hostdex')


def compiler(root):
    directory = root / '.deps'
    directory.mkdir(exist_ok=True)
    destination = directory / f'r8-{R8_VERSION}.jar'
    if not destination.exists():
        with tempfile.NamedTemporaryFile(dir=directory, delete=False) as stream:
            temporary = Path(stream.name)
        try:
            with urllib.request.urlopen(R8_URL, timeout=60) as source, temporary.open('wb') as output:
                while block := source.read(1024 * 1024):
                    output.write(block)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != R8_SHA256:
                raise SystemExit('R8 compiler download checksum mismatch')
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)
    if hashlib.sha256(destination.read_bytes()).hexdigest() != R8_SHA256:
        raise SystemExit('R8 compiler checksum mismatch; refusing modified build tool')
    return destination


def boot_archive(classes, dex_archive, destination):
    """Deterministic resources plus stored, four-byte-aligned DEX for ART mmap."""
    with zipfile.ZipFile(classes) as original, zipfile.ZipFile(dex_archive) as generated:
        names = generated.namelist()
        # These pinned boot modules must each fit one DEX. Fail instead of silently
        # dropping additional classes if a future dependency changes that contract.
        if names != ['classes.dex']:
            raise RuntimeError('Unexpected boot DEX entries: ' + repr(names))
        dex = generated.read('classes.dex')
        if not dex.startswith(b'dex\n038\0'):
            raise RuntimeError('Expected Android O compatible DEX version 038')
        resources = {}
        for entry in original.infolist():
            if entry.is_dir() or entry.filename.endswith('.class'):
                continue
            if entry.filename in resources or entry.filename.endswith('.dex'):
                raise RuntimeError('Ambiguous boot resource: ' + entry.filename)
            resources[entry.filename] = original.read(entry)
        with zipfile.ZipFile(destination, 'w') as output:
            for name, data in sorted(resources.items()):
                entry = zipfile.ZipInfo(name)
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                output.writestr(entry, data)
            entry = zipfile.ZipInfo('classes.dex')
            entry.external_attr = 0o100644 << 16
            padding = (-(output.fp.tell() + 30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
            output.writestr(entry, dex)


def desugar_boot(root=ROOT, modules=MODULES):
    # Caller owns .deps/runtime-build.lock. Never run alongside an ART make.
    host = root / 'context/art_standalone/out/host'
    intermediates = host / 'common/obj/JAVA_LIBRARIES'
    all_classes = intermediates / 'core-all-hostdex_intermediates/classes.jar'
    inputs = {module: intermediates / (module + '_intermediates/classes.jar') for module in modules}
    if not all(path.is_file() for path in [all_classes, *inputs.values()]):
        raise SystemExit('ART boot classes are missing. Run make runtime-art first.')
    tool = compiler(root)
    work = root / 'build/runtime-boot'
    work.mkdir(parents=True, exist_ok=True)
    manifest = {'compiler': R8_VERSION, 'compiler_sha256': R8_SHA256, 'min_api': 26, 'classpath_sha256': hashlib.sha256(all_classes.read_bytes()).hexdigest(), 'modules': {}}
    # Prepare both archives before replacing either installed module. A failed
    # compiler cannot publish a partially generated archive.
    with tempfile.TemporaryDirectory(dir=work) as temporary:
        staged = Path(temporary)
        for module, classes in inputs.items():
            dex = staged / (module + '-dex.jar')
            subprocess.run(['java', '-Xmx1024m', '-cp', str(tool), 'com.android.tools.r8.D8',
                            '--min-api', '26', '--classpath', str(all_classes),
                            '--output', str(dex), str(classes)], check=True)
            output = staged / (module + '.jar')
            boot_archive(classes, dex, output)
            manifest['modules'][module] = {'input_sha256': hashlib.sha256(classes.read_bytes()).hexdigest(),
                                            'output_sha256': hashlib.sha256(output.read_bytes()).hexdigest()}
        for module in inputs:
            output = staged / (module + '.jar')
            destination = host / 'linux-x86/framework' / output.name
            if not destination.exists() or destination.read_bytes() != output.read_bytes():
                os.replace(output, destination)
    (work / ('result.json' if modules == MODULES else 'network-result.json')).write_text(json.dumps(manifest, indent=2) + '\n')
