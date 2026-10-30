#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build real language-split resources and test lookup, assets and ownership."""
import argparse
import fcntl
import os
from pathlib import Path
import shlex
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--art', action='store_true', help='also test with standalone ART')
    parser.add_argument('--loader', default=os.environ.get('SHIRAZ_RUNTIME_LOADER'))
    args = parser.parse_args()
    work = ROOT / 'build/split-resources-smoke'
    work.mkdir(exist_ok=True)
    with (work / '.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run(work, args)


def run(work, args):
    tools = ROOT / '.deps/android-sdk'
    aapt = tools / 'aapt2'
    res = work / 'res'
    if res.exists():
        shutil.rmtree(res)
    for suffix, label, color in [('', 'base', '#ffabcdef'), ('-fr', 'split', '#ff123456')]:
        for folder in ('values', 'color', 'raw'):
            (res / (folder + suffix)).mkdir(parents=True)
        (res / ('values' + suffix) / 'strings.xml').write_text(
            '<resources><string name="label">' + label + '</string></resources>')
        (res / ('color' + suffix) / 'state.xml').write_text(
            '<selector xmlns:android="http://schemas.android.com/apk/res/android">'
            '<item android:color="' + color + '"/></selector>')
        (res / ('raw' + suffix) / 'payload.txt').write_text(label + ' raw')
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                        'package="org.shiraz.splitfixture"><uses-sdk android:minSdkVersion="27"/>'
                        '<application/></manifest>')
    compiled, base, split = (work / name for name in ('compiled.zip', 'base.apk', 'fr.apk'))
    subprocess.run([str(aapt), 'compile', '--dir', str(res), '-o', str(compiled)], check=True)
    subprocess.run([str(aapt), 'link', '-o', str(base), '--manifest', str(manifest),
                    '-I', str(tools / 'android.jar'), '--split', str(split) + ':fr',
                    '-0', 'txt', str(compiled)], check=True)
    for apk, label in ((base, 'base'), (split, 'split')):
        with zipfile.ZipFile(apk, 'a') as archive:
            for name in (label + '.txt', 'common.txt', 'dir/' + label + '.txt'):
                archive.writestr('assets/' + name, label + ' asset')
            if label == 'split':
                archive.writestr('assets/compressed.txt', 'compressed split', compress_type=zipfile.ZIP_DEFLATED)
    classes = work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    framework = ROOT / 'build/framework/classes'
    core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    classpath = str(framework) + ':' + str(core)
    subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes),
                    str(ROOT / 'tests/java/SplitResourcesSmoke.java')], check=True)
    properties = ['-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'),
                  '-Dshiraz.frameworkResources=' + str(ROOT / 'build/framework-res/framework-res.apk')]
    java = shlex.split(os.environ.get('SHIRAZ_TEST_JAVA', 'java'))
    commands = [('openjdk', java + properties + ['-cp', str(classes) + ':' + classpath])]
    env = environment(work)
    if args.art:
        dex = work / 'classes.dex'
        subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27',
                        '--output=' + str(dex), str(classes)], check=True)
        jar = work / 'test.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            entry = zipfile.ZipInfo('classes.dex')
            padding = (-(30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
            archive.writestr(entry, dex.read_bytes())
        command = [str(HOST / 'bin/dalvikvm'), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                   '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat', *properties,
                   '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar)]
        if args.loader:
            command = [args.loader, '--library-path', env['LD_LIBRARY_PATH'], *command]
        commands.append(('art', command))
    for runtime, command in commands:
        result = subprocess.run(command + ['SplitResourcesSmoke', str(base), str(split)],
                                env=env, capture_output=True, text=True, timeout=60)
        (work / (runtime + '-stdout.log')).write_text(result.stdout)
        (work / (runtime + '-stderr.log')).write_text(result.stderr)
        if result.returncode or 'SPLIT_RESOURCES_ASSETS_OWNERSHIP_OK' not in result.stdout.splitlines():
            raise SystemExit('Split resource test failed in ' + runtime + '; inspect build/split-resources-smoke/' + runtime + '-stderr.log')
        print('SPLIT_RESOURCES_ASSETS_OWNERSHIP_OK ' + runtime)


if __name__ == '__main__':
    main()
