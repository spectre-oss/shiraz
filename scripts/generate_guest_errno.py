#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile pinned bionic errno values and emit a Darwin translation table."""

import fcntl
import hashlib
import json
import re
import shlex
import subprocess
from pathlib import Path

import bootstrap_bionic_headers as headers
from bootstrap_framework_resources import source_files

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'build/macos/m5/guest_errno.hpp'


def verify_headers():
    expected = {}
    for archive in headers.ARCHIVES:
        path = headers.DEPS / 'download' / headers.archive_name(archive)
        headers.verifier(archive)(path)
        for name, data in source_files(path).items():
            if archive == 'libc/kernel/uapi':
                top, _, rest = name.partition('/')
                if top == 'asm-arm64':
                    name = rest
                elif top.startswith('asm-') and top != 'asm-generic':
                    continue
            expected[name] = data
    # Some kernel headers differ only by case. APFS may materialize only one;
    # verify its bytes against the pinned alternatives, then reject any such
    # ambiguity if it occurs in the compiler's actual errno dependency graph.
    groups = {}
    for name in expected:
        groups.setdefault(name.casefold(), []).append(name)
    actual = set()
    for path in headers.SYSROOT.rglob('*'):
        if path.is_symlink():
            raise ValueError('bionic sysroot must not contain symlinks')
        if path.is_file():
            name = path.relative_to(headers.SYSROOT).as_posix()
            key = name.casefold()
            actual.add(key)
            candidates = groups.get(key, [])
            if not any(path.read_bytes() == expected[candidate] for candidate in candidates):
                raise ValueError(f'bionic header differs from pinned source: {name}')
    if actual != set(groups):
        raise ValueError('bionic sysroot inventory differs from pinned source')
    return expected, groups


def main():
    headers.main()
    expected, groups = verify_headers()
    command = ['xcrun', 'clang', '--target=aarch64-linux-android24', '-ffreestanding',
               '-isystem', str(headers.SYSROOT), '-dM', '-E', '-x', 'c', '-include', 'errno.h', '/dev/null']
    dependency_command = [part for part in command if part != '-dM']
    dependency_command.insert(dependency_command.index('-E'), '-M')
    dependencies = shlex.split(subprocess.check_output(dependency_command, text=True).replace('\\\n', ''))[1:]
    checked = {}
    for dependency in dependencies:
        path = Path(dependency)
        if path == Path('/dev/null'):
            continue
        name = path.relative_to(headers.SYSROOT).as_posix()
        if len(groups[name.casefold()]) != 1 or path.read_bytes() != expected[name]:
            raise ValueError(f'ambiguous/modified compiler header dependency: {name}')
        checked[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    macros = subprocess.check_output(command, text=True)
    values = dict(re.findall(r'^#define (E[A-Z0-9_]+) (\w+)$', macros, re.MULTILINE))

    def number(name, seen=()):
        if name in seen or len(seen) > 16:
            raise ValueError('cyclic errno macro')
        value = values[name]
        return int(value) if value.isdecimal() else number(value, (*seen, name))

    resolved = {name: number(name) for name in sorted(values)}
    if len(resolved) < 100 or any(not 0 < value < 4096 for value in resolved.values()):
        raise ValueError('unexpected bionic errno inventory')
    lines = ['// Generated from checksum-verified AOSP bionic headers; local output.',
             '#pragma once', '#include <cerrno>', 'namespace shiraz::guest::bionic {']
    lines += [f'inline constexpr int k{name} = {value};' for name, value in resolved.items()]
    lines += ['struct ErrnoPair { int host, guest; };', 'inline constexpr ErrnoPair errors[] = {']
    for name, value in resolved.items():
        lines += [f'#if defined({name})', f'  {{{name}, {value}}},', '#endif']
    lines += ['};', '} // namespace shiraz::guest::bionic', '']
    text = '\n'.join(lines)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    if not OUTPUT.exists() or OUTPUT.read_text() != text:
        OUTPUT.write_text(text)
    OUTPUT.with_suffix('.json').write_text(json.dumps({
        'bionic_commit': headers.COMMIT,
        'preprocessor_command': command,
        'header_dependencies_sha256': checked,
        'unused_case_collisions': [names for names in groups.values() if len(names) > 1],
        'compiler': subprocess.check_output(['xcrun', 'clang', '--version'], text=True),
        'preprocessor_sha256': hashlib.sha256(macros.encode()).hexdigest(),
        'values': resolved,
    }, indent=2, sort_keys=True) + '\n')
    print(f'Guest errno ABI: {len(resolved)} compiler-derived values; pinned errno dependencies verified')


if __name__ == '__main__':
    (ROOT / '.deps').mkdir(exist_ok=True)
    with (ROOT / '.deps/guest-abi.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        main()
