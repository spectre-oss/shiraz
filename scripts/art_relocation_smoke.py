#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Require executable AOT and physical boot resources after package relocation."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import ROOT, HOST, JARS, environment, compile_aot


def main():
    work = ROOT / 'build/art-relocation-smoke'
    work.mkdir(parents=True, exist_ok=True)
    with (work / '.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        (work / 'result.json').unlink(missing_ok=True)
        for name in ('compiled', 'hidden-build', 'moved package', 'moved again حافظ'):
            path = work / name
            if path.is_symlink():
                raise RuntimeError('Unexpected symlink in relocation test output')
            if path.exists():
                shutil.rmtree(path)
        source = work / 'compiled'
        classes = source / 'classes'
        classes.mkdir(parents=True)
        env = environment(source)
        subprocess.run(['javac', '--release', '8', '-d', str(classes),
                        str(ROOT / 'tests/java/shiraz/BootResourceSmoke.java')], check=True, timeout=30)
        dex = source / 'smoke.dex'
        subprocess.run([str(HOST / 'bin/dx'), '--dex', '--output=' + str(dex), str(classes)],
                       env=env, check=True, timeout=30)
        jar = source / 'smoke.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            entry = zipfile.ZipInfo('classes.dex')
            padding = (-(30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
            archive.writestr(entry, dex.read_bytes())
        locations = ['/shiraz/boot/' + name for name in JARS]
        compile_aot(source, jar, env, 90, boot_locations=locations)
        moved = work / 'moved package'
        moved.mkdir()
        for name in ('runtime', 'oat'):
            shutil.copytree(source / name, moved / name)
        shutil.copy2(jar, moved / 'smoke.jar')
        (moved / 'framework').mkdir()
        for name in JARS:
            shutil.copy2(HOST / 'framework' / name, moved / 'framework' / name)
        source.rename(work / 'hidden-build')  # Original image/OAT paths are absent.
        vm = HOST / 'bin/dalvikvm64'
        if not vm.is_file():
            vm = HOST / 'bin/dalvikvm'
        evidence = []
        for index in range(4):
            if index == 1:
                destination = work / 'moved again حافظ'
                moved.rename(destination)
                moved = destination
            env = environment(work / ('run-' + str(index)))
            boot = ':'.join(str(moved / 'framework' / name) for name in JARS)
            env['BOOTCLASSPATH'] = boot
            logical = list(locations)
            if index == 2:
                logical[0] = '/shiraz/boot/mismatch.jar'
            image_options = (['-Xnoimage-dex2oat', '-Xint'] if index == 3 else
                             ['-Ximage:' + str(moved / 'runtime/boot.art'),
                              '-Xnoimage-dex2oat', '-Xno-dex-file-fallback'])
            command = [str(vm), '-Xbootclasspath:' + boot,
                       '-Xbootclasspath-locations:' + ':'.join(logical),
                       *image_options, '-Xusejit:false',
                       '-Xcheck:jni', '-verbose:oat', '-Xcompiler:' + str(HOST / 'bin/dex2oat'),
                       '-cp', str(moved / 'smoke.jar'), 'shiraz.BootResourceSmoke',
                       str(moved / 'framework' / JARS[0])]
            result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=30)
            (work / ('stdout-' + str(index) + '.log')).write_text(result.stdout)
            (work / ('stderr-' + str(index) + '.log')).write_text(result.stderr)
            marker = 'SHIRAZ_BOOT_RELOCATION_OK' in result.stdout.splitlines()
            if index == 2:
                if result.returncode == 0 or marker or 'Failed to match oat boot class path' not in result.stderr:
                    raise RuntimeError('Mismatched boot image was not rejected')
            elif index == 3:
                if result.returncode or not marker or 'Compiling dex file:' in result.stderr:
                    raise RuntimeError('Imageless physical resource loading failed')
            else:
                oat = moved / 'oat/x86_64/smoke.odex'
                if (result.returncode or not marker or
                    f'Successfully loaded {oat} with executable: 1' not in result.stderr or
                    f'{oat} is kOatUpToDate with filter speed' not in result.stderr or
                    'Compiling dex file:' in result.stderr or 'fall back to imageless' in result.stderr):
                    raise RuntimeError('Relocated AOT/resource execution failed; inspect logs')
            evidence.append({'case': index, 'exit_status': result.returncode, 'resource_marker': marker})
        (work / 'result.json').write_text(json.dumps(evidence, indent=2) + '\n')
        print('ART_BOOT_RELOCATION_OK')


if __name__ == '__main__':
    main()
