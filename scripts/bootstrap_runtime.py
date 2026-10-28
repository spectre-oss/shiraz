#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned host dependencies in-tree; no root install or Android image."""
import argparse
import fcntl
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREFIX = ROOT / '.local'
SOURCES = {
    'espeak-ng': ('https://github.com/espeak-ng/espeak-ng.git', '4870adfa25b1a32b4361592f1be8a40337c58d6c'),
    'wolfssl': ('https://github.com/wolfSSL/wolfssl.git', '3c5eead44904df64e6a5a1f4ebdce377d35a849a'),
    'art_standalone': ('https://gitlab.com/android_translation_layer/art_standalone.git', 'a1edfc40e8cea3644d80e4e776d3fa7fc397a1c2'),
    'bionic_translation': ('https://gitlab.com/android_translation_layer/bionic_translation.git', '6253dec7f24a57a94039a088cb436d880e92e3be'),
}
# Narrow host-port fixes; original AOSP license notices remain in dependency sources.
PATCHES = {
    'bionic_translation': [
        ('bionic-dladdr-path.patch', {
            'linker/dlfcn.c': '86372bbb7474dd14aef781e948a20f8a51afd405cb02fe5b436fb32d3c004cd7',
        }),
        ('bionic-dirent-storage.patch', {
            'libc/libc.c': '96ffe75851d8d9a557c62057b252fb49edd09a74e5bfc69ad689eed87bebe949',
        }),
        ('bionic-art-sigchain.patch', {
            'libc/libc.c': 'df58c0aead9961d92549a2696fd9aec8f66f51e64bb203e8f6a90bf159a4cb15',
        }),
        ('bionic-xsi-strerror-r.patch', {
            'libc/libc-misc.c': '008af6c6f9250eb879a954193897f82a2f0b256d1a7d7e673bcf74bb09f71312',
        }),
        ('bionic-checked-memchr.patch', {
            'libc/libc-misc.c': 'd410c2c8ac872ef58bd4437ccf0d3ebf82a309bc0e9952263b2e0db8997c0204',
        }),
        ('bionic-app-home.patch', {
            'libc/libc-misc.c': '966d22748fce7bc4c9a7a652ce6ccb62491a85e8e6f8f74ee3df2f0cd953cd15',
        }),
        ('bionic-host-debug-init.patch', {
            'linker/linker.c': '120cbd7b332b69ca1a937dd269438dacbe520f204374fc2838081e6006a4f9f7',
        }),
    ],
    'art_standalone': [
        ('art-dx-host-bash.patch', {
            'dalvik/dx/etc/dx': '3cf9906026221053b3ed99018f75939462a175d126dcc87924e6a24fc3278147',
        }),
        ('art-boot-resource-paths.patch', {
            'art/runtime/native/java_lang_VMClassLoader.cc': '8a15f9aea6260e662ed9cf09259a19411e0a923a728cd765c98f3fa037d1e088',
        }),
        ('art-imageless-dex-load.patch', {
            'art/runtime/oat_file_assistant.cc': '386decefc023334528af89dfbc43f10307c5cbd2f2ba766aae94b46777bdaad0',
        }),
        ('art-short-apk-path.patch', {
            'art/runtime/oat_file_assistant.cc': '34e52ba3ee39b00126ed72e04b767cf1188e2ca29e8d8941fbfab61872903b99',
        }),
        ('art-in-memory-dex-cookie.patch', {
            'libcore/dalvik/src/main/java/dalvik/system/DexFile.java': '0b6c3f76f11ac3c505027b20478bd300768c1d8fe2177c6b14f8c021a504f42a',
        }),
        ('art-linkage-diagnostics.patch', {
            'art/runtime/thread.cc': '2df7108d93021291d88116eb07694d39d0adb23b704ba7884c6c5bfc77d6c31b',
        }),
        ('art-managed-diagnostics.patch', {
            'art/runtime/interpreter/interpreter.cc': '6e52434cf9d8ee90abe781934433abb19d3122ae4e44fa50db0b1edf88453f2a',
        }),
        ('art-reflection-diagnostics.patch', {
            'art/runtime/reflection.cc': '1197d545b93db726a972043324b582a8ed9cb1ff610864ed174de60d3b846342',
        }),
        ('art-native-bridge-load-error.patch', {
            'art/runtime/jni/java_vm_ext.cc': '0c07a835e1ffdbf35843852f211e3666bbc5daf7a49eb24c239691bfaeb648e4',
        }),
        ('art-low4g-fallback.patch', {
            'art/libartbase/base/mem_map.cc': '34c6948b8fab5418d0f53fed77d55e44902792c7730a5b6f477fd8bb01250bee',
        }),
        ('art-host-trust-store.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLTrustManager.java': '962f58b73551dfa46f315958aeee457a5a4b39b3abab73b7a6608fac37f4185e',
        }),
        ('art-tls-cache-lifetime.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLAuthStore.java': 'c21a8c88b6a8d8af08d29f574fc80339581045089d0bf76a024cd706ef04efd2',
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLImplementSSLSession.java': 'ac4558a6df6c22bf909f9399660a6679b31889e434fe42d6279ce4f53d5a446b',
        }),
        ('art-tls-stream-lifetime.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLSocket.java': '06338beb576fff593cc422c1600aa852fafecde8d3f64d400cfe3ba4122d34e9',
        }),
        ('art-host-unload.patch', {
            'libcore/luni/src/main/native/Register.cpp':
                '1baba7f2fb38514ba57955287d7a28ca9478a985d3fd9bce411111837d36b9de',
        }),
        ('art-xml-entity-limits.patch', {
            'libcore/xml/src/main/java/org/kxml2/io/KXmlParser.java': '918eb49d67890adbe7b2ab843e9076a91de6b2e4cfde809110e42ed4452aa161',
        }),
        ('art-resource-lifetime.patch', {
            'libandroidfw/Android.mk': 'b50d13d2dfb926c6725cc5b1961807ae6aeaa796e0d0a54faf3e81d4b0808c34',
            'libandroidfw/androidfw_c_api.cpp': 'e908c08beae395e967633e611e2708b5e1903f93e1a7e6812dc62801c0332391',
            'libandroidfw/include/androidfw/androidfw_c_api.h': '4bbc4c38843b0c03ab12ced7ed600ac3fff7f2e3b4bb37a5605840efad3d5a5e',
        }),
        ('art-resource-size-configurations.patch', {
            'libandroidfw/androidfw_c_api.cpp': 'f87d482e950e560af8875ce58072e894f6225967fe7b55f764292b83d2fd996f',
            'libandroidfw/include/androidfw/androidfw_c_api.h': 'cd8dad2be0628312e1afb98093f4df828647ba88503dde093962957acdf23d77',
        }),
        ('art-resource-identifier-status.patch', {
            'libandroidfw/androidfw_c_api.cpp': '9754786dfa69284d3c3f9fa6f046b7b7b4f3961edb027c4025817576097218b0',
            'libandroidfw/include/androidfw/androidfw_c_api.h': 'e395f966983da83a84750d3ec03f08dfe93133a6128c7caaf06a6408e51c2473',
        }),
        ('art-resource-private-attribute-io.patch', {
            'libandroidfw/AssetManager2.cpp': 'e860b7656deb7cc7b9ee98587e15d8b363e405e58d03f1e66532bef5e5212402',
        }),
        ('art-liblog-c-compat.patch', {
            'system/core/liblog/fake_log_device.c': '244a9b7c548aa032dae9834308e58cd3c74288c00d1dd939e33cc7e18234bb7f',
        }),
    ],
}
BSD_VERSION = '0.12.2'
BSD_SHA256 = 'b88cc9163d0c652aaf39a99991d974ddba1c3a9711db8f1b5838af2a14731014'


def run(argv, cwd=ROOT, env=None):
    if argv[0] == "make":
        bash = shutil.which("bash")
        if not bash:
            raise RuntimeError("Bash is required to build the runtime")
        argv = [*argv, "SHELL=" + bash]
    subprocess.run([str(x) for x in argv], cwd=cwd, env=env, check=True)


def sources():
    for name, (url, revision) in SOURCES.items():
        directory = ROOT / 'context' / name
        if not directory.exists():
            directory.mkdir(parents=True)
            run(['git', 'init', '-q', directory])
            run(['git', 'remote', 'add', 'origin', url], cwd=directory)
            run(['git', 'fetch', '--depth', '1', 'origin', revision], cwd=directory)
            run(['git', 'checkout', '--detach', 'FETCH_HEAD'], cwd=directory)
        actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=directory, text=True).strip()
        if actual != revision:
            raise SystemExit(f'{name}: local revision differs from pin; refusing to overwrite')
        dirty = subprocess.check_output(['git', 'diff', '--no-ext-diff', '--name-only', 'HEAD'], cwd=directory, text=True)
        patches = PATCHES.get(name, [])
        expected_files = {file: checksum for _, files in patches for file, checksum in files.items()}
        changed = set(dirty.splitlines())
        if not changed.issubset(expected_files):
            raise SystemExit(f'{name}: unrecognized tracked changes; refusing to build')
        def digest(file):
            path = directory / file
            return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None

        current = {file: digest(file) for file in expected_files}
        if current == expected_files:
            continue  # Final recipe, including files superseded by overlapping patches.

        # Independent patches may have been installed in any order (new fixes can be
        # prepended). Overlapping patches form a chain whose complete intermediate
        # snapshots must match before any further mutation is permitted.
        components = []
        for index, (_, files) in enumerate(patches):
            joined = {index}
            touched = set(files)
            remaining = []
            for indices, paths in components:
                if touched.intersection(paths):
                    joined.update(indices)
                    touched.update(paths)
                else:
                    remaining.append((indices, paths))
            components = remaining + [(joined, touched)]
        pending = set()
        for indices, paths in components:
            state = {}
            for file in paths:
                original = subprocess.run(['git', 'show', f'HEAD:{file}'], cwd=directory,
                                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                state[file] = hashlib.sha256(original.stdout).hexdigest() if original.returncode == 0 else None
            ordered = sorted(indices)
            snapshots = [dict(state)]
            for index in ordered:
                state.update(patches[index][1])
                snapshots.append(dict(state))
            matches = [step for step, snapshot in enumerate(snapshots)
                       if all(current[file] == checksum for file, checksum in snapshot.items())]
            if not matches:
                raise SystemExit(f'{name}: modified or partially applied dependency patch chain')
            pending.update(ordered[max(matches):])
        for index, (patch, files) in enumerate(patches):
            if index not in pending:
                continue
            run(['git', 'apply', '--check', ROOT / 'patches' / patch], cwd=directory)
            run(['git', 'apply', ROOT / 'patches' / patch], cwd=directory)
            for file, checksum in files.items():
                if digest(file) != checksum:
                    raise SystemExit(f'{name}: patched source differs from recorded checksum: {file}')
        for file, checksum in expected_files.items():
            if digest(file) != checksum:
                raise SystemExit(f'{name}: patched source differs from recorded checksum: {file}')



def build_bsd(env):
    deps = ROOT / '.deps'
    deps.mkdir(exist_ok=True)
    archive = deps / f'libbsd-{BSD_VERSION}.tar.xz'
    if not archive.exists():
        urllib.request.urlretrieve(f'https://libbsd.freedesktop.org/releases/libbsd-{BSD_VERSION}.tar.xz', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != BSD_SHA256:
        raise SystemExit('libbsd source checksum mismatch')
    source = deps / f'libbsd-{BSD_VERSION}'
    if not source.exists():
        with tarfile.open(archive) as tar:
            tar.extractall(deps, filter='data')
    build = ROOT / 'build' / 'libbsd'
    build.mkdir(parents=True, exist_ok=True)
    run([source / 'configure', '--prefix=' + str(PREFIX), '--enable-shared', '--enable-static', '--with-pic'], cwd=build, env=env)
    run(['make', '-j2'], cwd=build, env=env)
    run(['make', 'install'], cwd=build, env=env)


def build_wolfssl(env):
    source = ROOT / 'context/wolfssl'
    if not (source / 'configure').is_file():
        run(['autoreconf', '-fi'], cwd=source, env=env)
    build = ROOT / 'build/wolfssl'
    build.mkdir(parents=True, exist_ok=True)
    run([source / 'configure', '--prefix=' + str(PREFIX), '--enable-shared', '--disable-static',
         '--enable-jni', '--disable-examples', '--disable-crypttests'], cwd=build, env=env)
    run(['make', '-j2'], cwd=build, env=env)
    run(['make', 'install'], cwd=build, env=env)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['sources', 'bridges', 'art', 'resources', 'xml', 'network', 'speech', 'memory'], default='bridges', nargs='?')
    args = parser.parse_args()
    (ROOT / '.deps').mkdir(exist_ok=True)
    lock = (ROOT / '.deps/runtime-build.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'Another Shiraz runtime bootstrap owns the build lock. Wait for it to finish.\n')
    sources()
    if args.stage == 'sources':
        return
    env = dict(os.environ)
    for name, suffix in [('PKG_CONFIG_PATH', 'lib/pkgconfig'), ('LIBRARY_PATH', 'lib'),
                         ('LD_LIBRARY_PATH', 'lib'), ('CPATH', 'include')]:
        env[name] = str(PREFIX / suffix) + (':' + env[name] if env.get(name) else '')
    if args.stage == 'memory':
        run(['make', '-j2', 'libartbase', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        return
    if args.stage == 'speech':
        source = ROOT / 'context/espeak-ng'
        if not (source / 'configure').is_file():
            run(['sh', 'autogen.sh'], cwd=source, env=env)
        # Upstream phoneme generation assumes an in-source build.
        build = source
        run([source / 'configure', '--prefix=' + str(PREFIX), '--without-pcaudiolib', '--without-sonic', '--without-mbrola', '--without-async', '--disable-static'], cwd=build, env=env)
        run(['make', '-j2'], cwd=build, env=env)
        run(['make', 'install'], cwd=build, env=env)
        return
    if args.stage == 'network':
        from bootstrap_boot import desugar_boot, NETWORK_MODULES
        build_wolfssl(env)
        run(['make', '-j2', *NETWORK_MODULES, '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        desugar_boot(ROOT, NETWORK_MODULES)
        return
    if args.stage == 'xml':
        if not (ROOT / 'context/art_standalone/out/host/linux-x86/framework/core-libart-hostdex.jar').is_file():
            raise SystemExit('Host ART Java runtime is missing. Run make runtime-art first.')
        run(['make', '-j2', 'core-libart-hostdex', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        from bootstrap_boot import desugar_boot
        desugar_boot(ROOT)
        return
    build_bsd(env)
    build = ROOT / 'build' / 'bionic'
    setup = ['meson', 'setup']
    if (build / 'meson-private/coredata.dat').exists():
        setup.append('--reconfigure')
    run(setup + [build, ROOT / 'context/bionic_translation', '--prefix=' + str(PREFIX), '--libdir=lib'], env=env)
    run(['meson', 'compile', '-C', build, '-j', '2'], env=env)
    run(['meson', 'install', '-C', build], env=env)
    if args.stage == 'art':
        run(['make', '-j2', 'dalvikvm', 'dex2oat', 'libjavacore', 'libopenjdk', 'libopenjdkjvm',
             'core-oj-hostdex', 'core-libart-hostdex', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        from bootstrap_boot import desugar_boot
        desugar_boot(ROOT)
    if args.stage == 'resources':
        run(['make', '-j2', 'libandroidfw', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
    # This only records successful build steps, not a claim of a working ART boot.
    (ROOT / 'build/runtime-dependencies.json').write_text(json.dumps({'stage': args.stage, 'sources': SOURCES, 'patches': PATCHES, 'libbsd': BSD_VERSION}, indent=2) + '\n')


if __name__ == '__main__':
    main()
