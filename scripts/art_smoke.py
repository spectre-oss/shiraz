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
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.host_layout import host_out_dir  # noqa: E402
DARWIN = platform.system() == 'Darwin'
ART = ROOT / 'context/art_standalone'
HOST = host_out_dir(ART)  # out/host/linux-x86, or out/host/darwin-x86 on macOS
JARS = ('core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'okhttp-hostdex.jar', 'bouncycastle-hostdex.jar', 'wolfssljni-hostdex.jar')
# Shiraz JNI libraries: build/ on Linux, build/macos/ (Mach-O, .so names) on macOS.
NATIVE = ROOT / 'build/macos' if DARWIN else ROOT / 'build'


def environment(work):
    data = work / 'android-data'
    for directory in (data / 'dalvik-cache', work / 'cache', work / 'tmp', work / 'home'):
        directory.mkdir(parents=True, exist_ok=True)
    env = {**os.environ, 'ANDROID_ROOT': str(HOST), 'ANDROID_DATA': str(data), 'XDG_CACHE_HOME': str(work / 'cache'),
           'LD_LIBRARY_PATH': ':'.join(str(x) for x in (HOST / 'lib64', ROOT / '.local/lib')),
           'XDG_DATA_DIRS': str(ROOT / '.local/share') + ':/usr/share',
           'BOOTCLASSPATH': ':'.join(str(HOST / 'framework' / jar) for jar in JARS)}
    if DARWIN:
        # dyld resolves ART and Shiraz libraries through @rpath and absolute
        # java.library.path entries; no search-path variables (and no DYLD_*).
        del env['LD_LIBRARY_PATH']
    return env


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
    parser.add_argument('--windowed', action='store_true',
                        help='macOS: present the surface program in a real Cocoa window on the Metal renderer')
    parser.add_argument('--window-hold', action='store_true',
                        help='with --windowed: keep the prepared-display window up for 600 frames (screenshots)')
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error('timeout must be positive')
    if DARWIN and args.aot:
        parser.error('--aot needs dex2oat, which is not built on macOS yet (interpreter only)')
    if args.windowed and (not DARWIN or args.program != 'surface'):
        parser.error('--windowed applies to --program surface on macOS only')
    if args.window_hold and not args.windowed:
        parser.error('--window-hold requires --windowed')
    work = ROOT / 'build' / ('art-smoke' if args.program == 'core' else 'art-surface-smoke')
    if DARWIN:
        work = ROOT / 'build/macos' / (work.name.replace('-smoke', '-window-smoke') if args.windowed else work.name)
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
    if DARWIN or not vm.exists():
        vm = HOST / 'bin/dalvikvm'  # the darwin-arm64 build ships one 64-bit dalvikvm
    dx = HOST / 'bin/dx'
    required = [vm, dx, *(HOST / 'framework' / jar for jar in JARS),
                *(HOST / 'lib64' / name for name in ('libart.so', 'libjavacore.so', 'libopenjdk.so', 'libopenjdkjvm.so'))]
    if args.aot:
        required.append(HOST / 'bin/dex2oat')
    if args.program == 'surface':
        required += [NATIVE / 'libshiraz-jni.so', NATIVE / 'libandroid.so',
                     ROOT / 'build/shiraz-framework.jar', ROOT / 'build/framework/classes/android/view/Window.class',
                     NATIVE / 'libshiraz-test-input.so']
        if DARWIN:
            required.append(NATIVE / 'libshiraz-gpu.so')
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
        if args.windowed:
            # Real Cocoa video driver; the run must report the Metal renderer.
            for name in ('SDL_VIDEODRIVER', 'SHIRAZ_HOST_SDL_VIDEODRIVER', 'SHIRAZ_HOST_SDL_RENDER_DRIVER'):
                env.pop(name, None)
            env['SDL_RENDER_DRIVER'] = 'metal'
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
    if DARWIN:
        runtime = ['-Xusejit:false', '-Xcheck:jni']  # no dex2oat on macOS yet
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
               '-Djava.library.path=' + str(HOST / 'lib64') + ':' + str(NATIVE),
               '-Djava.io.tmpdir=' + str(work / 'tmp'), '-Duser.home=' + str(work / 'home'), '-cp', classpath, main_class, 'host-runtime']
    if DARWIN and args.program == 'surface':
        command.append('darwin-main-thread')
        if args.window_hold:
            command.append('darwin-window-hold')
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
    renderer = None
    if DARWIN and args.program == 'surface':
        # ART log lines may share a line with native stderr; search, do not anchor.
        lines = result.stderr.splitlines()
        renderers = sorted({line.split('SHIRAZ_PRESENTATION renderer=', 1)[1].split()[0] for line in lines if 'SHIRAZ_PRESENTATION renderer=' in line})
        checks = [line[line.index('SHIRAZ_MAIN_THREAD '):].split() for line in lines if 'SHIRAZ_MAIN_THREAD ' in line]
        accepted = [fields[2] for fields in checks if fields[1] == 'pthread_main_np=1']
        rejected = [fields[2] for fields in checks if fields[1] != 'pthread_main_np=1']
        # The program calls prepareDisplay once from a worker thread on purpose.
        if rejected != ['entry=prepareDisplay'] or 'entry=prepareDisplay' not in accepted or 'entry=presentDispatch' not in accepted:
            parser.exit(1, f'Main-thread checks unexpected (accepted={accepted}, rejected={rejected}); inspect stderr.log\n')
        if not any(line.startswith('SHIRAZ_DARWIN_PREPARED_DISPLAY ') for line in result.stdout.splitlines()):
            parser.exit(1, 'Prepared-display presentation marker missing; inspect stdout.log\n')
        if not renderers:
            parser.exit(1, 'No SHIRAZ_PRESENTATION renderer line; inspect stderr.log\n')
        if args.windowed and renderers != ['metal']:
            parser.exit(1, f'Windowed run did not use the Metal renderer ({renderers}); inspect stderr.log\n')
        renderer = ','.join(renderers)
    if args.aot:
        loaded = f'Successfully loaded {oat} with executable: 1'
        current = f'{oat} is kOatUpToDate with filter speed'
        if loaded not in result.stderr or current not in result.stderr:
            parser.exit(1, 'AOT gate did not verify executable speed-filter OAT loading; inspect stderr.log\n')
    evidence = {'mode': 'aot' if args.aot else 'interpreter', 'main_class': main_class,
                'marker': marker, 'exit_status': result.returncode,
                'dex_sha256': hashlib.sha256(dex.read_bytes()).hexdigest(),
                'executable_oat_verified': bool(args.aot)}
    if DARWIN:
        evidence.update(host=HOST.name, vm=str(vm.relative_to(ROOT)), windowed=args.windowed, renderer=renderer)
        if args.program == 'surface':
            evidence.update(main_thread_accepted=accepted, main_thread_rejected=rejected)
    if oat:
        evidence['oat_sha256'] = hashlib.sha256(oat.read_bytes()).hexdigest()
    (work / 'result.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(marker)
    if args.aot:
        print('AOT_ELF_EXECUTION_OK')
    print('Executed DEX with standalone host ART (no Android system image).')


if __name__ == '__main__':
    main()
