#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run the math/thread/media fixture through Digitalis, in a private namespace."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--guest-system', type=Path, required=True,
                        help='Pinned Digitalis guest support directory containing bin/ and lib64/')
    args = parser.parse_args()
    system = args.guest_system.resolve()
    if not (system/'bin/arm64/app_process64').is_file():
        raise SystemExit('Missing Digitalis guest app_process64')
    work = ROOT/'build/digitalis-platform-smoke'
    work.mkdir(parents=True, exist_ok=True)
    bridge = ROOT/'build/digitalis-host'
    art = ROOT/'context/art_standalone'
    compiler = os.environ.get('DIGITALIS_CXX') or shutil.which('clang++') or '/usr/lib/llvm21/bin/clang++'
    subprocess.run([compiler, '-std=c++17', str(ROOT/'tests/native/digitalis_bridge_runner.cpp'),
                    '-I'+str(art/'system/core/libnativebridge/include'),
                    '-I'+str(art/'libnativehelper/include/nativehelper'), '-ldl',
                    '-o', str(work/'runner')], check=True)
    subprocess.run([compiler, '--target=aarch64-linux-android28', '-x', 'c', '-fno-builtin',
                    '-ffreestanding', '-fPIC', '-shared', '-nostdlib', '-fuse-ld=lld',
                    '-Wl,--no-undefined', str(ROOT/'tests/native/digitalis_platform.c'),
                    '-L'+str(system/'lib64/arm64'), '-lc', '-lm', '-lmediandk',
                    '-o', str(work/'libfixture.so')], check=True)
    command = ['bwrap', '--unshare-all', '--die-with-parent', '--new-session']
    for path in ('/usr', '/lib', '/lib64'):
        command += ['--ro-bind', path, path]
    command += ['--proc', '/proc', '--dev', '/dev', '--tmpfs', '/tmp',
                '--ro-bind', str(ROOT), str(ROOT), '--ro-bind', str(system), '/system',
                '--bind', str(work), str(work), '--chdir', str(work), '--clearenv',
                '--setenv', 'PATH', '/usr/bin', '--setenv', 'HOME', '/tmp',
                '--setenv', 'LD_LIBRARY_PATH', ':'.join(map(str, (
                    bridge, art/'out/host/linux-x86/lib64', ROOT/'.local/lib', ROOT/'build'))),
                str(work/'runner'), str(bridge/'libberberis_arm64.so'), str(work/'libfixture.so')]
    subprocess.run(command, check=True, timeout=45)

if __name__ == '__main__':
    main()
