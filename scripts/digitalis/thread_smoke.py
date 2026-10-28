#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build and run an ARM64 thread fixture through standalone ART and Digitalis."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--art-home', type=Path, default=ROOT/'context/art_standalone/out/host/linux-x86')
    parser.add_argument('--bridge', type=Path, default=ROOT/'build/digitalis-host/libberberis_arm64.so')
    parser.add_argument('--guest-libraries', type=Path, default=Path('/system/lib64/arm64'))
    parser.add_argument('--library-path', default='')
    parser.add_argument('--loader', default=os.environ.get('SHIRAZ_RUNTIME_LOADER'))
    parser.add_argument('--cc', default=os.environ.get('DIGITALIS_CC', 'clang'))
    parser.add_argument('--dx', type=Path)
    args = parser.parse_args()
    work = ROOT/'build/digitalis-threads'
    classes = work/'classes'
    classes.mkdir(parents=True, exist_ok=True)
    (work/'android-data').mkdir(exist_ok=True)
    library = work/'libthread-fixture.so'
    subprocess.run([args.cc, '--target=aarch64-linux-android28', '-fno-emulated-tls',
                    '-fPIC', '-shared', '-nostdlib', '-fuse-ld=lld', '-Wl,--no-undefined',
                    str(ROOT/'tests/native/digitalis_threads.c'), '-L'+str(args.guest_libraries),
                    '-lc', '-o', str(library)], check=True)
    subprocess.run(['javac', '--release', '8', '-d', str(classes),
                    str(ROOT/'tests/java/ThreadSmoke.java')], check=True)
    subprocess.run([str(args.dx or args.art_home/'bin/dx'), '--dex', '--min-sdk-version=27',
                    '--output='+str(work/'classes.dex'), str(classes)], check=True)
    with zipfile.ZipFile(work/'test.jar', 'w') as jar:
        jar.write(work/'classes.dex', 'classes.dex')
    jars = ('core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'okhttp-hostdex.jar',
            'bouncycastle-hostdex.jar', 'wolfssljni-hostdex.jar')
    boot = ':'.join(str(args.art_home/'framework'/name) for name in jars)
    search = ':'.join(filter(None, (str(args.bridge.parent), args.library_path,
                                   str(args.art_home/'lib64'), str(ROOT/'.local/lib'), str(ROOT/'build'))))
    environment = dict(os.environ, ANDROID_ROOT=str(args.art_home),
                       ANDROID_DATA=str(work/'android-data'), BOOTCLASSPATH=boot,
                       BERBERIS_MODE='interpret-only', SHIRAZ_GUEST_STACK_ENVIRONMENT='1',
                       BERBERIS_GUEST_LD_LIBRARY_PATH=str(work), LD_LIBRARY_PATH=search)
    command = [str(args.art_home/'bin/dalvikvm'), '-Xbootclasspath:'+boot,
               '-XX:NativeBridge='+args.bridge.name, '-Xforce-nb-testing', '-Xusejit:false',
               '-Xint', '-Xnoimage-dex2oat', '-Djava.library.path='+search,
               '-cp', str(work/'test.jar'), 'shiraz.ThreadSmoke', str(library)]
    if args.loader:
        command = [args.loader, '--library-path', search, *command]
        environment.pop('LD_LIBRARY_PATH')
    subprocess.run(command, env=environment, check=True, timeout=90)

if __name__ == '__main__':
    main()
