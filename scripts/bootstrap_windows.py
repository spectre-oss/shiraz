#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Bootstrap pinned Windows (UCRT64 / Clang-UCRT64) ART dependencies and build plan.

Mirroring scripts/bootstrap_darwin.py, dependencies come from checksum-verified
upstream release tarballs under .deps, build under build/windows/deps, and install
into .local/windows. Building actual Win32 ART binaries requires a Windows x64 or
ARM64 host with MSYS2 Clang-UCRT64/MinGW-w64; running off-Windows fails explicitly
for build actions while supporting `plan` and `doctor` diagnostics everywhere.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / '.deps'
BUILD = ROOT / 'build/windows/deps'
PREFIX = ROOT / '.local/windows'
HOST_TAG = 'windows-x86'

# Checksum-verified upstream dependency pins (shared with scripts/bootstrap_darwin.py).
PINNED_DEPS = {
    'icu4c': {
        'version': '78.3',
        'url': 'https://github.com/unicode-org/icu/releases/download/release-78.3/icu4c-78.3-sources.tgz',
        'sha256': '3a2e7a47604ba702f345878308e6fefeca612ee895cf4a5f222e7955fabfe0c0',
        'outputs': ['lib/libicudata.dll.a', 'lib/libicuuc.dll.a', 'lib/libicui18n.dll.a'],
    },
    'lz4': {
        'version': '1.10.0',
        'url': 'https://github.com/lz4/lz4/releases/download/v1.10.0/lz4-1.10.0.tar.gz',
        'sha256': '537512904744b35e232912055ccf8ec66d768639ff3abe5788d90d792ec5f48b',
        'outputs': ['lib/liblz4.a'],
    },
    'xz': {
        'version': '5.8.4',
        'url': 'https://github.com/tukaani-project/xz/releases/download/v5.8.4/xz-5.8.4.tar.gz',
        'sha256': '0014c7886930454fe8bd4228665b51af55eeae560ea135c9c4cd33f55b2591d9',
        'outputs': ['lib/liblzma.a'],
    },
    'libpng': {
        'version': '1.6.59',
        'url': 'https://downloads.sourceforge.net/project/libpng/libpng16/1.6.59/libpng-1.6.59.tar.xz',
        'sha256': 'd80dd2a38a37f803cb9b6ac7b14bd6e74ddc3b654780a8380bdf93523fdb4389',
        'outputs': ['lib/libpng16.a'],
    },
}

ART_WINDOWS_TARGETS = (
    'dalvikvm.exe',
    'libart.dll',
    'libartbase.dll',
    'libdexfile.dll',
    'libopenjdkjvm.dll',
    'libjavacore.dll',
    'libopenjdk.dll',
    'libnativehelper.dll',
    'libandroidfw.dll',
    'core-oj-hostdex.jar',
    'core-libart-hostdex.jar',
)

WIN32_ART_ADAPTATIONS = (
    {
        'area': 'memory',
        'component': 'art/libartbase/base/mem_map.cc',
        'strategy': 'VirtualAlloc(MEM_RESERVE|MEM_COMMIT) + VirtualProtect W^X + 64 KiB granularity alignment',
    },
    {
        'area': 'fault_handling',
        'component': 'art/runtime/fault_handler.cc',
        'strategy': 'AddVectoredExceptionHandler for STATUS_ACCESS_VIOLATION null-check / stack-overflow traps',
    },
    {
        'area': 'threads_tls',
        'component': 'art/runtime/thread.cc',
        'strategy': 'Win32 TLS / FlsAlloc + SuspendThread/GetThreadContext safepoint checkpoints',
    },
    {
        'area': 'jit_wx',
        'component': 'art/runtime/jit/jit_code_cache.cc',
        'strategy': 'VirtualAlloc(PAGE_READWRITE) -> VirtualProtect(PAGE_EXECUTE_READ) + FlushInstructionCache',
    },
    {
        'area': 'native_bridge',
        'component': 'native/win32/guest/native_bridge.cpp',
        'strategy': 'Win32 ART NativeBridge v3 + Guest ELF64 loader + SysV x86_64 <-> Win64 machine-code ABI thunks',
    },
)

WIN32_ART_PATCHES = (
    {
        'path': 'patches/art-win32-build.patch',
        'sha256': 'a6b48fdfb096680e76d6c353fb3037a9924a48e9bdaa7e18bffa473dfc4da98d',
        'scope': 'HOST_windows-x86_64 combo Makefile and Win32 MemMap/W^X/VEH compatibility header',
    },
)

REQUIRED_TOOLS = ('clang++', 'lld', 'cmake', 'ninja', 'git', 'javac')


def host_is_windows(system_name: str | None = None) -> bool:
    name = (system_name or platform.system()).lower()
    return name.startswith('win') or name.startswith('msys') or name.startswith('mingw')


def detect_toolchain(which_fn=shutil.which) -> dict:
    found = {}
    missing = []
    for tool_name in REQUIRED_TOOLS:
        resolved = which_fn(tool_name)
        found[tool_name] = resolved
        if not resolved:
            missing.append(tool_name)
    # Accept g++ as fallback compiler when clang++ is absent on UCRT64.
    if not found['clang++']:
        gpp = which_fn('g++')
        if gpp:
            found['g++'] = gpp
            missing = [m for m in missing if m != 'clang++']
    return {
        'host_os': platform.system(),
        'host_arch': platform.machine(),
        'windows_host': host_is_windows(),
        'msystem': os.environ.get('MSYSTEM', ''),
        'tools': found,
        'missing_tools': missing,
        'ready_for_build': host_is_windows() and not missing,
    }


def build_plan(arch: str | None = None) -> dict:
    raw_arch = (arch or platform.machine() or 'x86_64').lower()
    if raw_arch in ('amd64', 'x86_64', 'x64'):
        target_arch = 'x86_64'
        abi = 'x86_64'
        triplet = 'x86_64-w64-windows-gnu'
    elif raw_arch in ('arm64', 'aarch64'):
        target_arch = 'aarch64'
        abi = 'arm64-v8a'
        triplet = 'aarch64-w64-windows-gnu'
    else:
        raise ValueError(f'Unsupported Windows target architecture: {raw_arch}')
    return {
        'schemaVersion': 1,
        'host_tag': HOST_TAG,
        'target_arch': target_arch,
        'android_abi': abi,
        'triplet': triplet,
        'page_size': 4096,
        'allocation_granularity': 65536,
        'prefix': str(PREFIX.relative_to(ROOT)),
        'build_dir': str(BUILD.relative_to(ROOT)),
        'dependencies': PINNED_DEPS,
        'patches': list(WIN32_ART_PATCHES),
        'art_targets': list(ART_WINDOWS_TARGETS),
        'adaptations': list(WIN32_ART_ADAPTATIONS),
    }


def verify_patches(root: Path = ROOT) -> list[dict]:
    verified = []
    for entry in WIN32_ART_PATCHES:
        patch_path = root / entry['path']
        if not patch_path.is_file():
            raise RuntimeError(f'Missing pinned Win32 ART patch: {entry["path"]}')
        verify_sha256(patch_path, entry['sha256'])
        verified.append(dict(entry))
    return verified


def verify_sha256(path: Path, expected: str) -> None:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    actual = digest.hexdigest()
    if actual != expected:
        raise RuntimeError(f'SHA-256 mismatch for {path.name}: expected {expected}, got {actual}')


def fetch_dependencies(deps_dir: Path = DEPS) -> dict[str, Path]:
    deps_dir.mkdir(parents=True, exist_ok=True)
    fetched = {}
    for name, meta in PINNED_DEPS.items():
        filename = meta['url'].rsplit('/', 1)[-1]
        dest = deps_dir / filename
        if not dest.is_file():
            tmp = dest.with_suffix(dest.suffix + '.download')
            try:
                with urllib.request.urlopen(meta['url'], timeout=60) as resp, tmp.open('wb') as out:
                    shutil.copyfileobj(resp, out)
                verify_sha256(tmp, meta['sha256'])
                tmp.replace(dest)
            finally:
                tmp.unlink(missing_ok=True)
        else:
            verify_sha256(dest, meta['sha256'])
        fetched[name] = dest
    return fetched


def require_windows_build_host() -> dict:
    info = detect_toolchain()
    if not info['windows_host']:
        raise RuntimeError(
            f'Windows ART build requires a Windows UCRT64/MinGW-w64 host (current host: {info["host_os"]}). '
            'Run `python3 scripts/bootstrap_windows.py plan` or `doctor` for cross-platform diagnostics.'
        )
    if info['missing_tools']:
        raise RuntimeError(
            'Missing required MSYS2 UCRT64 build tools: ' + ', '.join(info['missing_tools'])
        )
    return info


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)

    plan_cmd = sub.add_parser('plan', help='Print the pinned Windows ART build plan as JSON')
    plan_cmd.add_argument('--arch', help='Target Windows architecture (x86_64 or arm64)')
    plan_cmd.add_argument('--output', type=Path, help='Optional path to write plan JSON')

    sub.add_parser('doctor', help='Check Windows UCRT64/Clang toolchain readiness')
    sub.add_parser('check-patches', help='Verify pinned patches/art-win32-*.patch SHA-256 digests')
    sub.add_parser('fetch-deps', help='Download and verify pinned Windows ART dependency tarballs')
    sub.add_parser('art', help='Run pinned Windows ART build (requires Windows UCRT64 host)')

    args = parser.parse_args(argv)
    if args.command == 'plan':
        plan = build_plan(args.arch)
        rendered = json.dumps(plan, indent=2) + '\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding='utf-8')
        else:
            sys.stdout.write(rendered)
        return 0

    if args.command == 'doctor':
        sys.stdout.write(json.dumps(detect_toolchain(), indent=2) + '\n')
        return 0

    if args.command == 'check-patches':
        verified = verify_patches()
        for item in verified:
            print(f'VERIFIED {item["path"]} ({item["sha256"][:12]})')
        return 0

    if args.command == 'fetch-deps':
        fetched = fetch_dependencies()
        for name, path in fetched.items():
            print(f'VERIFIED {name}: {path}')
        return 0

    if args.command == 'art':
        try:
            require_windows_build_host()
        except RuntimeError as error:
            print(f'bootstrap_windows: {error}', file=sys.stderr)
            return 2
        art_checkout = ROOT / 'context/art_standalone'
        if not art_checkout.is_dir():
            print(
                'bootstrap_windows: missing context/art_standalone; run `make runtime-sources` first',
                file=sys.stderr,
            )
            return 2
        # Write the verified plan stamp before delegating to the Win32 ART patch/build tree.
        BUILD.mkdir(parents=True, exist_ok=True)
        plan_path = BUILD / 'win32-art-plan.json'
        plan_path.write_text(json.dumps(build_plan(), indent=2) + '\n', encoding='utf-8')
        print(
            'bootstrap_windows: Win32 ART patch application and compilation of libart.dll '
            f'require the pinned patches/art-win32-*.patch chain (plan written to {plan_path}).',
            file=sys.stderr,
        )
        return 2

    return 1


if __name__ == '__main__':
    raise SystemExit(main())
