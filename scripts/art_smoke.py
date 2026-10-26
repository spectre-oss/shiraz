#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile a small Java main to DEX and execute it only with pinned host ART."""
import argparse
import fcntl
import hashlib
import json
import os
import platform
import shutil
from pathlib import Path
import subprocess
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'context/art_standalone'
HOST = ART / 'out/host/linux-x86'
JARS = ('core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'okhttp-hostdex.jar', 'bouncycastle-hostdex.jar', 'wolfssljni-hostdex.jar')


def environment(work):
    data = work / 'android-data'
    for directory in (data / 'dalvik-cache', work / 'cache', work / 'tmp', work / 'home'):
        directory.mkdir(parents=True, exist_ok=True)
    return {**os.environ, 'ANDROID_ROOT': str(HOST), 'ANDROID_DATA': str(data), 'XDG_CACHE_HOME': str(work / 'cache'),
            'LD_LIBRARY_PATH': ':'.join(str(x) for x in (HOST / 'lib64', ROOT / '.local/lib')),
            'XDG_DATA_DIRS': str(ROOT / '.local/share') + ':/usr/share',
            'BOOTCLASSPATH': ':'.join(str(HOST / 'framework' / jar) for jar in JARS)}


def compile_aot(work, jar, env, timeout, boot_locations=None):
    compiler = str(HOST / 'bin/dex2oat')
    isa = {'x86_64': 'x86_64', 'aarch64': 'arm64'}[platform.machine()]
    images = work / 'runtime' / isa
    images.mkdir(parents=True, exist_ok=True)
    boot = work / 'runtime/boot.art'  # ART inserts the ISA directory when loading.
    oat = jar.parent / 'oat' / isa / 'smoke.odex'
    oat.parent.mkdir(parents=True, exist_ok=True)
    memory = ['--runtime-arg', '-Xms64m', '--runtime-arg', '-Xmx256m']
    boot_args = [compiler, *memory, '--instruction-set=' + isa, '--compiler-filter=verify',
                 '--base=0x70000000', '--image=' + str(images / 'boot.art'),
                 '--oat-file=' + str(images / 'boot.oat'),
                 *['--dex-file=' + str(HOST / 'framework' / name) for name in JARS]]
    app_args = [compiler, *memory, '--boot-image=' + str(boot), '--dex-file=' + str(jar),
                '--oat-file=' + str(oat), '--instruction-set=' + isa, '--compiler-filter=speed', '--host']
    if boot_locations is not None:
        if len(boot_locations) != len(JARS):
            raise ValueError('One logical location is required for each boot JAR')
        boot_args += ['--dex-location=' + location for location in boot_locations]
        app_args += ['--runtime-arg', '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                     '--runtime-arg', '-Xbootclasspath-locations:' + ':'.join(boot_locations)]
    for stage, command in [('boot', boot_args), ('app', app_args)]:
        with (work / (stage + '-compile.log')).open('w') as log:
            try:
                result = subprocess.run(command, cwd=work, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
            except subprocess.TimeoutExpired as exc:
                raise RuntimeError('AOT ' + stage + ' compiler timed out') from exc
        if result.returncode:
            raise RuntimeError(f'AOT {stage} compiler failed ({result.returncode}); inspect {work.relative_to(ROOT)}/{stage}-compile.log')
    with oat.open('rb') as output:
        if output.read(4) != b'\x7fELF':
            raise RuntimeError('AOT output is not ELF')
    return boot, oat


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--timeout', type=int, default=90)
    parser.add_argument('--aot', action='store_true', help='compile with speed filter and require executable OAT loading')
    parser.add_argument('--program', choices=['core', 'surface'], default='core')
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error('timeout must be positive')
    work = ROOT / 'build' / ('art-smoke' if args.program == 'core' else 'art-surface-smoke')
    if args.aot:
        work = work.with_name(work.name.replace('-smoke', '-aot-smoke'))
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'This ART smoke gate is already running.\n')
    (work / 'result.json').unlink(missing_ok=True)
    vm = HOST / 'bin/dalvikvm64'
    if not vm.exists():
        vm = HOST / 'bin/dalvikvm'
    dx = HOST / 'bin/dx'
    required = [vm, dx, *(HOST / 'framework' / jar for jar in JARS),
                *(HOST / 'lib64' / name for name in ('libart.so', 'libjavacore.so', 'libopenjdk.so', 'libopenjdkjvm.so'))]
    if args.aot:
        required.append(HOST / 'bin/dex2oat')
    if args.program == 'surface':
        required += [ROOT / 'build/libshiraz-jni.so', ROOT / 'build/libandroid.so',
                     ROOT / 'build/shiraz-framework.jar', ROOT / 'build/framework/classes/android/view/Window.class',
                     ROOT / 'build/libshiraz-test-input.so']
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        parser.exit(1, 'Host ART smoke prerequisites missing:\n' + '\n'.join(missing) + '\n')
    classes = work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    env = environment(work)
    if args.program == 'surface':
        sources = [ROOT / 'java/shiraz/runtime/NativeSurface.java', ROOT / 'tests/java/shiraz/NativeSurfaceSmoke.java']
        main_class, marker = 'shiraz.NativeSurfaceSmoke', 'SHIRAZ_JNI_SURFACE_OK'
        env.update(SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software')
    else:
        sources = [ROOT / 'tests/java/shiraz/ArtSmoke.java']
        main_class, marker = 'shiraz.ArtSmoke', 'SHIRAZ_ART_OK:5050'
    compile_path = ['-cp', str(ROOT / 'build/framework/classes')] if args.program == 'surface' else []
    subprocess.run(['javac', '--release', '8', *compile_path, '-d', str(classes), *map(str, sources)], env=env, check=True)
    dex = work / 'smoke.dex'
    subprocess.run([str(dx), '--dex', '--output=' + str(dex), str(classes)], env=env, check=True)
    if not dex.read_bytes().startswith(b'dex\n'):
        parser.exit(1, 'dx did not produce DEX\n')
    jar = work / 'smoke.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        # Stored DEX data aligned to four bytes can be mapped directly by ART.
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    boot = ':'.join(str(HOST / 'framework' / name) for name in JARS)
    runtime = ['-Xusejit:false', '-Xcheck:jni', '-Xcompiler:' + str(HOST / 'bin/dex2oat')]
    oat = None
    if args.aot:
        try:
            image, oat = compile_aot(work, jar, env, args.timeout)
        except RuntimeError as exc:
            parser.exit(1, str(exc) + '\n')
        runtime += ['-Ximage:' + str(image), '-verbose:oat']
    else:
        runtime += ['-Xnoimage-dex2oat', '-Xint']
    classpath = str(jar)
    if args.program == 'surface':
        classpath += ':' + str(ROOT / 'build/shiraz-framework.jar')
    command = [str(vm), '-Xbootclasspath:' + boot, *runtime,
               '-Djava.library.path=' + str(HOST / 'lib64') + ':' + str(ROOT / 'build'),
               '-Djava.io.tmpdir=' + str(work / 'tmp'), '-Duser.home=' + str(work / 'home'), '-cp', classpath, main_class, 'host-runtime']
    try:
        result = subprocess.run(command, cwd=work, env=env, text=True, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        parser.exit(1, 'Host ART smoke timed out\n')
    (work / 'stdout.log').write_text(result.stdout)
    (work / 'stderr.log').write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        parser.exit(1, f'Host ART smoke failed ({result.returncode}); inspect {work.relative_to(ROOT)}/stderr.log\n')
    if not args.aot and 'Compiling dex file:' in result.stderr:
        parser.exit(1, 'Imageless runtime attempted startup AOT compilation; inspect stderr.log\n')
    if args.aot:
        loaded = f'Successfully loaded {oat} with executable: 1'
        current = f'{oat} is kOatUpToDate with filter speed'
        if loaded not in result.stderr or current not in result.stderr:
            parser.exit(1, 'AOT gate did not verify executable speed-filter OAT loading; inspect stderr.log\n')
    evidence = {'mode': 'aot' if args.aot else 'interpreter', 'main_class': main_class,
                'marker': marker, 'exit_status': result.returncode,
                'dex_sha256': hashlib.sha256(dex.read_bytes()).hexdigest(),
                'executable_oat_verified': bool(args.aot)}
    if oat:
        evidence['oat_sha256'] = hashlib.sha256(oat.read_bytes()).hexdigest()
    (work / 'result.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(marker)
    if args.aot:
        print('AOT_ELF_EXECUTION_OK')
    print('Executed DEX with standalone host ART (no Android system image).')


if __name__ == '__main__':
    main()
