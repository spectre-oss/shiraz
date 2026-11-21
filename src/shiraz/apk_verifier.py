# SPDX-License-Identifier: GPL-3.0-only
"""Verify frozen APK bytes using pinned upstream apksig, without granting trust."""

import fcntl
import hashlib
import os
import re
import stat
import subprocess
import tempfile
from pathlib import Path

from .apk import FormatError

ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / '.deps/android-sdk/apksig-8.9.3.jar'
JAR_SHA256 = 'c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89'
URL = (
    'https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.9.3/apksig-8.9.3.jar'
)
SOURCE = ROOT / 'scripts/java/VerifyApk.java'


def verify_signatures(apk):
    """Return verified content and current-signer digests; these are not an allowlist."""
    if not JAR.is_file() or JAR.stat().st_size > 4 * 1024 * 1024:
        raise FormatError('Pinned apksig missing; run make apksig-deps')
    if hashlib.sha256(JAR.read_bytes()).hexdigest() != JAR_SHA256:
        raise FormatError('Pinned apksig checksum mismatch')
    work = ROOT / 'build/apk-verifier'
    work.mkdir(parents=True, exist_ok=True)
    # Private snapshot ties the returned certificate identity to exactly these bytes.
    with tempfile.TemporaryDirectory(dir=work) as directory:
        snapshot = Path(directory) / 'input.apk'
        digest = hashlib.sha256()
        if not Path(apk).is_file():
            raise FormatError('APK verification requires a regular file')
        descriptor = os.open(apk, os.O_RDONLY | os.O_NONBLOCK)
        with os.fdopen(descriptor, 'rb') as source, snapshot.open('wb') as out:
            if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
                raise FormatError('APK verification requires a regular file')
            remaining = 1024 * 1024 * 1024
            while chunk := source.read(min(1024 * 1024, remaining + 1)):
                remaining -= len(chunk)
                if remaining < 0:
                    raise FormatError('APK verification size limit')
                digest.update(chunk)
                out.write(chunk)
        with (work / '.lock').open('a') as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            compiled = work / 'VerifyApk.class'
            if not compiled.is_file() or compiled.stat().st_mtime_ns < SOURCE.stat().st_mtime_ns:
                subprocess.run(
                    ['javac', '--release', '8', '-cp', str(JAR), '-d', str(work), str(SOURCE)],
                    check=True,
                    capture_output=True,
                    timeout=60,
                )
        result = subprocess.run(
            ['java', '-Xmx256m', '-cp', str(work) + ':' + str(JAR), 'VerifyApk', str(snapshot)],
            capture_output=True,
            timeout=60,
            check=False,
        )
        lines = result.stdout.decode('ascii', errors='replace').splitlines()
        if (
            result.returncode
            or not 1 <= len(lines) <= 16
            or any(not re.fullmatch('[0-9a-f]{64}', line) for line in lines)
        ):
            raise FormatError('APK signature verification failed')
        return {'apk_sha256': digest.hexdigest(), 'verified_signers_sha256': sorted(set(lines))}
