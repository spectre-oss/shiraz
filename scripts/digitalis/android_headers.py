# SPDX-License-Identifier: GPL-3.0-only
"""Fetch the missing AOSP NDK headers for optional host ABI proxies."""
import base64
import hashlib
from pathlib import Path
import urllib.request

# Apache-2.0 AOSP headers, preserved verbatim with their original notices.
BASE = 'https://android.googlesource.com/platform/frameworks/native/+/android-15.0.0_r1/libs/'
HEADERS = {
    'rect.h': ('arect', '38c24c9f3e08b8e5a577e32d0a17fa55c3a50b5820128b2663972704b4c07706'),
    'data_space.h': ('nativewindow', '75e7d509baf6b12e609b70757f451939db2b66cb3281069eafeb19ffe406177b'),
    'hardware_buffer.h': ('nativewindow', '6be4cfe954a02ec0466754633639c3cf89bac2b6d4e893add4ac84d330b1d074'),
    'native_window.h': ('nativewindow', '979577126ba69e8714b7b2409c181fbe6ed879e76774ffa70b954bbf043ebe8e'),
}

def prepare(directory):
    directory = Path(directory)
    target = directory/'android'
    target.mkdir(parents=True, exist_ok=True)
    for name, (folder, digest) in HEADERS.items():
        path = target/name
        if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest:
            continue
        with urllib.request.urlopen(BASE+folder+'/include/android/'+name+'?format=TEXT', timeout=30) as response:
            encoded = response.read(1024*1024+1)
        if len(encoded) > 1024*1024:
            raise RuntimeError('Oversized Android header: '+name)
        data = base64.b64decode(encoded, validate=True)
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError('Android header checksum mismatch: '+name)
        temporary = path.with_suffix('.tmp')
        temporary.write_bytes(data)
        temporary.replace(path)
    return directory
