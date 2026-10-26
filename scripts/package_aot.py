# SPDX-License-Identifier: GPL-3.0-only
"""Compile relocatable ART boot/framework/APK artifacts in generated AppDir only."""
import os
import platform
from pathlib import Path
import subprocess


def compile_package(appdir, work, host, jars, prefix):
    framework = appdir / 'usr/art/framework'
    share = appdir / 'usr/share/shiraz'
    isa = {'x86_64': 'x86_64', 'aarch64': 'arm64'}[platform.machine()]
    images = framework / isa
    oat = share / 'oat' / isa
    images.mkdir(parents=True)
    oat.mkdir(parents=True)
    work.mkdir(parents=True, exist_ok=True)
    for name in ('android-data/dalvik-cache', 'cache', 'tmp', 'home'):
        (work / name).mkdir(parents=True, exist_ok=True)
    physical = ':'.join(str(framework / name) for name in jars)
    locations = ['/shiraz/boot/' + name for name in jars]
    env = dict(os.environ, ANDROID_ROOT=str(host), ANDROID_DATA=str(work / 'android-data'),
               XDG_CACHE_HOME=str(work / 'cache'), BOOTCLASSPATH=physical,
               LD_LIBRARY_PATH=str(host / 'lib64') + ':' + str(prefix / 'lib'))
    compiler = str(host / 'bin/dex2oat')
    # Full DWARF dwarfs the executable code for large APKs. Keep compact native
    # backtraces and let ART emit the final ELF; never rewrite its OAT layout.
    common = [compiler, '--runtime-arg', '-Xms64m', '--runtime-arg', '-Xmx512m',
              '--instruction-set=' + isa, '--avoid-storing-invocation',
              '--no-generate-debug-info', '--generate-mini-debug-info']
    # Core collection/string/reflection work is on the activity launch path too.
    # A verify-only boot image leaves that work to interpretation/JIT warmup.
    commands = [('boot', common + ['--compiler-filter=speed', '--base=0x70000000',
                 '--image=' + str(images / 'boot.art'), '--oat-file=' + str(images / 'boot.oat'),
                 *['--dex-file=' + str(framework / name) for name in jars],
                 *['--dex-location=' + name for name in locations]])]
    for name, context in [('shiraz-framework.jar', 'PCL[]'), ('original.apk', 'PCL[shiraz-framework.jar]')]:
        commands.append((Path(name).stem, common + [
            '--runtime-arg', '-Xbootclasspath:' + physical,
            '--runtime-arg', '-Xbootclasspath-locations:' + ':'.join(locations),
            '--boot-image=' + str(framework / 'boot.art'), '--dex-file=' + str(share / name),
            '--dex-location=' + name, '--oat-file=' + str(oat / (Path(name).stem + '.odex')),
            '--compiler-filter=speed', '--host', '--class-loader-context=' + context,
            '--classpath-dir=' + str(share)]))
    for name, command in commands:
        with (work / (name + '.log')).open('w') as log:
            try:
                result = subprocess.run(command, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=600)
            except subprocess.TimeoutExpired as exc:
                raise RuntimeError('AOT compilation timed out: ' + name) from exc
        if result.returncode:
            raise RuntimeError('AOT compilation failed: ' + name + '; inspect ' + str(work))
    expected = set()
    for index, name in enumerate(jars):
        stem = 'boot' if index == 0 else 'boot-' + Path(name).stem
        expected.update(str((images / (stem + suffix)).relative_to(appdir)) for suffix in ('.art', '.oat', '.vdex'))
    for stem in ('shiraz-framework', 'original'):
        expected.update(str((oat / (stem + suffix)).relative_to(appdir)) for suffix in ('.odex', '.vdex'))
    files = []
    for directory in (images, oat):
        for file in sorted(directory.iterdir()):
            if file.suffix not in ('.art', '.oat', '.odex', '.vdex') or not file.is_file():
                raise RuntimeError('Unexpected AOT output: ' + str(file))
            if file.stat().st_size == 0:
                raise RuntimeError('Empty AOT output: ' + str(file))
            if file.suffix in ('.oat', '.odex'):
                with file.open('rb') as stream:
                    if stream.read(4) != b'\x7fELF':
                        raise RuntimeError('AOT code is not ELF: ' + str(file))
            files.append(str(file.relative_to(appdir)))
    if set(files) != expected:
        raise RuntimeError('Incomplete or unexpected AOT artifact set')
    return {'native_debug_info': 'mini', 'boot_filter': 'speed', 'application_filter': 'speed', 'boot_locations': locations,
            'files': files, 'bytes': sum((appdir / name).stat().st_size for name in files)}
