#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned HTTP/crypto/TLS modules for the Darwin ART tree, entirely in-tree."""
import fcntl
import json
import platform
import shutil
import subprocess

from bootstrap_boot import NETWORK_MODULES, desugar_boot
from bootstrap_darwin import (
    HOST_TAG,
    PREFIX,
    ROOT,
    art_env,
    art_make_command,
    dependency_env,
    refresh_vnodes,
)
from bootstrap_runtime import SOURCES, sources


def run(command, **kwargs):
    subprocess.run(list(map(str, command)), check=True, **kwargs)


def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('macOS arm64 host required')
    (ROOT / '.deps').mkdir(exist_ok=True)
    with (ROOT / '.deps/runtime-build.lock').open('a') as lock:
        try:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit('Another runtime bootstrap owns the build lock') from None
        sources()
        source = ROOT / 'context/wolfssl'
        work = ROOT / 'build/macos/network'
        work.mkdir(parents=True, exist_ok=True)
        env = dependency_env()
        # Same --enable-jni bundle used by the Linux runtime. The generated
        # options.h is also consumed by libwolfssljni, keeping their ABI aligned.
        if not (source / 'configure').exists():
            run(['autoreconf', '-fi'], cwd=source, env=env)
        build = work / 'wolfssl'
        build.mkdir(exist_ok=True)
        stamp = build / 'shiraz-config.json'
        config = {'pin': SOURCES['wolfssl'][1], 'prefix': str(PREFIX), 'flags': env['CFLAGS']}
        if not stamp.exists() or json.loads(stamp.read_text()) != config:
            run([source / 'configure', '--prefix=' + str(PREFIX), '--enable-shared',
                 '--disable-static', '--enable-jni', '--disable-examples', '--disable-crypttests'],
                cwd=build, env=env)
            stamp.write_text(json.dumps(config) + '\n')
        run(['make', '-j2'], cwd=build, env=env)
        run(['make', 'install'], cwd=build, env=env)
        tree = ROOT / 'context/art_standalone'
        run(art_make_command(targets=NETWORK_MODULES, jobs=2), cwd=tree, env=art_env())
        host = tree / 'out/host' / HOST_TAG
        for dylib in (PREFIX / 'lib').glob('libwolfssl*.dylib'):
            if dylib.is_symlink():
                continue
            target = host / 'lib64' / dylib.name
            fresh = target.with_name('.' + target.name + '.fresh')
            shutil.copy2(dylib, fresh)
            fresh.replace(target)
            # Match the ART library's @loader_path rpath rather than baking in
            # this checkout's .local path. Only touch the newly built JNI file.
            run(['install_name_tool', '-change', str(dylib), '@rpath/' + dylib.name,
                 host / 'lib64/libwolfssljni.so'])
        refresh_vnodes(host)
        desugar_boot(ROOT, NETWORK_MODULES, host=tree / 'out/host', host_tag=HOST_TAG,
                     work=work / 'boot')
        print('MACOS_NETWORK_MODULES_BUILT (runtime verification still required)')


if __name__ == '__main__':
    main()
