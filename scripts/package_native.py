#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Copy bounded architecture-matched ELF payloads unchanged into a separate Bionic search path."""
import hashlib
from pathlib import Path
import re
import stat
import struct
import zipfile
from shiraz.elf import inspect_elf
from shiraz.apk import MAX_NATIVE_ENTRY

RESERVED = {'libc.so', 'libdl.so', 'libm.so', 'liblog.so', 'libandroid.so', 'libjnigraphics.so',
            'libEGL.so', 'libGLESv2.so', 'libGLESv3.so', 'libstdc++.so'}

def extract_native(apk, destination, architecture='x86_64'):
    targets = {'x86_64': ('x86_64', 62), 'aarch64': ('arm64-v8a', 183)}
    if architecture not in targets:
        raise ValueError('Unsupported native payload architecture')
    abi, machine = targets[architecture]
    prefix = 'lib/' + abi + '/'
    destination = Path(destination)
    if destination.exists():
        raise ValueError('Native library destination must be new')
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        if len(entries) > 100000:
            raise ValueError('Too many APK entries')
        selected = [entry for entry in entries if entry.filename.startswith(prefix) and not entry.is_dir()]
        if len(selected) > 64 or sum(e.file_size for e in selected) > 512 * 1024 * 1024:
            raise ValueError('APK native payload exceeds package budget')
        names = set()
        for entry in selected:
            name = entry.filename.removeprefix(prefix)
            if not re.fullmatch(r'lib[A-Za-z0-9_+.-]+\.so', name) or name in names or name in RESERVED:
                raise ValueError('Unsafe, duplicate or reserved Android native library name')
            if entry.flag_bits & 1 or entry.file_size > MAX_NATIVE_ENTRY or stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError('Unsupported Android native library entry')
            names.add(name)
        destination.mkdir(parents=True)
        result = []
        for entry in selected:
            data = archive.read(entry)
            if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<HH', data, 16) != (3, machine):
                raise ValueError('Native payload must be little-endian ' + architecture + ' ET_DYN ELF')
            inspect_elf(data)
            name = entry.filename.removeprefix(prefix)
            (destination / name).write_bytes(data)
            result.append({'name': name, 'apk_entry': entry.filename, 'bytes': len(data),
                           'sha256': hashlib.sha256(data).hexdigest()})
        return result
