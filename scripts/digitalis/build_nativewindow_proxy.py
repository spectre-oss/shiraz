#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build individual ABI proxies against an existing Digitalis build."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kind', choices=['nativewindow', 'egl', 'gles1', 'libc'], default='nativewindow')
    parser.add_argument('--library-dir', type=Path, default=ROOT/'build/digitalis-host')
    parser.add_argument('--android-library-dir', type=Path, default=ROOT/'build')
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-17-openjdk-amd64')))
    args = parser.parse_args()
    work = ROOT/'build/digitalis-host'
    manifest = json.loads((work/'manifest.json').read_text())
    original = Path(manifest['includes'][0]).parents[2]
    includes = [ROOT/Path(p).relative_to(original) for p in manifest['includes']]
    includes += [ROOT/'include', ROOT/'context/art_standalone/libbase/include', ROOT/'context/art_standalone/system/core/include', ROOT/'context/art_standalone/system/core/liblog/include', args.java_home/'include', args.java_home/'include/linux']
    includes += list((ROOT/'context/digitalis-native-bridge-support').rglob('include'))
    command = [os.environ.get('DIGITALIS_CXX', 'clang++'), '-std=gnu++23', '-shared', '-fPIC', '-O1', '-include', 'string', '-DANDROID', '-DBERBERIS_GUEST_LP64=1', '-DNATIVE_BRIDGE_GUEST_ARCH_ARM64']
    command += ['-I'+str(p) for p in includes]
    names = {'nativewindow': 'nativewindow', 'egl': 'EGL', 'gles1': 'GLESv1_CM', 'libc': 'c'}
    if args.kind == 'gles1':
        header = Path('/usr/include/GLES/gl.h').read_text()
        functions = sorted(set(re.findall(r'GL_APIENTRY\s+(gl\w+)\s*\(', header)))
        if len(functions) < 100:
            raise SystemExit('Incomplete GLES1 declarations')
        (work/'digitalis-gles1.inc').write_text(''.join('ENTRY('+name+')\n' for name in functions))
    command += ['-I'+str(work), str(ROOT/('native/digitalis_'+args.kind+'_proxy.cpp')), '-L'+str(args.library_dir), '-L'+str(args.android_library_dir), '-lberberis_arm64']
    if args.kind == 'nativewindow':
        command += ['-landroid']
    command += ['-o', str(work/('libberberis_proxy_lib'+names[args.kind]+'.so'))]
    subprocess.run(command, check=True)
if __name__ == '__main__':
    main()
