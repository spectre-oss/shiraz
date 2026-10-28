#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Reject local artifacts and obvious credentials in the staged snapshot."""
import re
import subprocess
from pathlib import PurePosixPath

FORBIDDEN_DIRS = {'context', '.deps', '.local', 'build', 'dist', 'data', 'runs', 'docs', 'doc', 'secrets', 'credentials', '__pycache__'}
PUBLIC_DOCS = {'docs/README.md', 'docs/DEVELOPMENT.md', 'docs/BUILDER.md',
               'docs/WEB.md', 'docs/AI_CONTRIBUTING.md'}
FORBIDDEN_SUFFIXES = {'.apk', '.apks', '.aab', '.dex', '.jar', '.class', '.so', '.a', '.o', '.elf', '.appimage', '.exe', '.bin', '.zip', '.gz', '.xz', '.log', '.sqlite', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.pyc'}
SECRET = re.compile(rb'-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|(?:AKIA|ASIA)[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-[A-Za-z0-9_-]{32,}|(?i:(?:api[_-]?key|access[_-]?token|password|client[_-]?secret)\s*[=:]\s*["\x27]?)[A-Za-z0-9_+/=-]{16,}')


def unsafe(path, data, mode='100644'):
    p = PurePosixPath(path)
    if mode not in ('100644', '100755'):
        return 'symlink/submodule or non-regular entry'
    if (path not in PUBLIC_DOCS and any(part.lower() in FORBIDDEN_DIRS for part in p.parts)) or p.name.startswith('.env'):
        return 'private/local-only path'
    if p.suffix.lower() in FORBIDDEN_SUFFIXES or '.so.' in p.name:
        return 'binary, credential, or generated artifact extension'
    if len(data) > 1024 * 1024 or b'\0' in data or data.startswith((b'\x7fELF', b'PK\x03\x04')):
        return 'binary or oversized file'
    if SECRET.search(data):
        return 'possible credential (contents withheld)'
    return None


def main():
    index = subprocess.check_output(['git', 'ls-files', '--stage', '-z'])
    errors = []
    for item in index.split(b'\0'):
        if not item:
            continue
        meta, raw_path = item.split(b'\t', 1)
        mode, oid, stage = meta.decode().split()
        path = raw_path.decode('utf-8', 'surrogateescape')
        if stage != '0':
            errors.append((path, 'unresolved conflict'))
            continue
        if mode != '160000' and int(subprocess.check_output(['git', 'cat-file', '-s', oid])) > 1024 * 1024:
            errors.append((path, 'oversized file'))
            continue
        data = subprocess.check_output(['git', 'cat-file', 'blob', oid]) if mode != '160000' else b''
        reason = unsafe(path, data, mode)
        if reason:
            errors.append((path, reason))
    for path, reason in errors:
        print(f'Blocked: {path}: {reason}')
    raise SystemExit(bool(errors))


if __name__ == '__main__':
    main()
