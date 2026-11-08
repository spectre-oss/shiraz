#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fetch pinned AOSP bionic libc headers into .deps/bionic-headers/sysroot.

The macOS guest-library work (docs/macos_final_goal.md, M5) needs the real
bionic arm64 constants and struct layouts, not values recalled from memory.
Homebrew clang compiles against these headers with --target=aarch64-linux-android
and no NDK, so scripts/bionic_abi_gen.py can read the values from the compiler.

Nothing here is committed: the headers stay in .deps (local-only), next to
bionic's NOTICE file and a provenance record. Gitiles archives are not
byte-stable, so each archive is pinned by a hash of its validated file names and
contents, as scripts/bootstrap_framework_resources.py does.
"""
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
sys.path.insert(0, str(ROOT / 'src'))
from build_apk_fixture import fetch  # noqa: E402
from bootstrap_framework_resources import source_files, source_content_sha256  # noqa: E402

TAG = 'android-14.0.0_r1'
COMMIT = 'e4df46f213d8d363017dce7ded5d1f2159614425'
BASE = 'https://android.googlesource.com/platform/bionic/+archive/' + COMMIT + '/'
# path in the bionic tree -> (content sha256, file count)
ARCHIVES = {
    'libc/include': ('a3abb82d366e72342ef42f54410f872a3ea68feb7d3dc5ee7ebfc567232dfdf7', 225),
    'libc/kernel/uapi': ('bd0456fbebb131757795ff55c84cfca97527a9577748d63a4bf273d15b3b0dcc', 1091),
    'libc/kernel/android/uapi': ('83f670e2784fb673c144d79b2866be6b6c6dde6e7739dbe9a729339120815b7a', 2),
}
NOTICE_URL = 'https://android.googlesource.com/platform/bionic/+/' + COMMIT + '/libc/NOTICE?format=TEXT'
NOTICE_SHA256 = '67128fa0d11c374f0627a7166d8e0945351c101db1e72922acb161677c6764ed'
DEPS = ROOT / '.deps/bionic-headers'
SYSROOT = DEPS / 'sysroot/include'
STAMP = DEPS / 'provenance.json'


def archive_name(path):
    return path.replace('/', '_') + '.tar.gz'


def verifier(path):
    checksum, count = ARCHIVES[path]

    def verify(archive):
        try:
            files = source_files(archive)
        except Exception as error:  # tarfile, EOF and zlib errors
            raise ValueError(f'not a valid header archive: {error}') from error
        if len(files) != count or source_content_sha256(files) != checksum:
            raise ValueError(f'{path}: content checksum mismatch')
    return verify


def verify_notice(path):
    import base64
    data = base64.b64decode(path.read_bytes(), validate=True)
    if hashlib.sha256(data).hexdigest() != NOTICE_SHA256:
        raise ValueError('bionic NOTICE checksum mismatch')


def write_tree(files, target, rename=None):
    for name, data in files.items():
        relative = rename(name) if rename else name
        if relative is None:
            continue
        out = target / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(data)


def main():
    if STAMP.exists():
        stamp = json.loads(STAMP.read_text())
        if stamp.get('commit') == COMMIT and SYSROOT.is_dir():
            print(f'bionic headers {TAG} already in {SYSROOT.relative_to(ROOT)}')
            return
    download = DEPS / 'download'
    download.mkdir(parents=True, exist_ok=True)
    for path in ARCHIVES:
        fetch(download / archive_name(path), BASE + path + '.tar.gz', None, 16 * 1024 * 1024, verify=verifier(path))
    notice = download / 'NOTICE.b64'
    fetch(notice, NOTICE_URL, None, 1024 * 1024, verify=verify_notice)
    import base64
    staging = DEPS / 'sysroot.pending'
    if staging.exists():
        shutil.rmtree(staging)
    include = staging / 'include'
    write_tree(source_files(download / archive_name('libc/include')), include)

    # Kernel UAPI: arch-neutral directories as they are, asm-arm64/asm as <asm/...>.
    # Other architectures' asm trees are not needed for arm64-v8a.
    def uapi(name):
        top, _, rest = name.partition('/')
        if top == 'asm-arm64':
            return rest
        return None if top.startswith('asm-') and top != 'asm-generic' else name
    write_tree(source_files(download / archive_name('libc/kernel/uapi')), include, uapi)
    write_tree(source_files(download / archive_name('libc/kernel/android/uapi')), include)
    (staging / 'NOTICE').write_bytes(base64.b64decode(notice.read_bytes()))
    if (DEPS / 'sysroot').exists():
        shutil.rmtree(DEPS / 'sysroot')
    staging.replace(DEPS / 'sysroot')
    STAMP.write_text(json.dumps({
        'source': 'https://android.googlesource.com/platform/bionic', 'tag': TAG, 'commit': COMMIT,
        'archives': {path: {'content_sha256': checksum, 'files': count} for path, (checksum, count) in ARCHIVES.items()},
        'notice_sha256': NOTICE_SHA256,
        'license': 'bionic libc/NOTICE (BSD-style and Apache-2.0 notices); kept beside the headers, not committed',
    }, indent=2, sort_keys=True) + '\n')
    print(f'bionic headers {TAG} ({COMMIT[:12]}) in {SYSROOT.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
