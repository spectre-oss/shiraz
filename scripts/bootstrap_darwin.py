#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the pinned macOS arm64 ART host dependencies and ART itself in-tree.

Dependencies come from checksum-verified upstream release tarballs under .deps,
build under build/macos/deps and install into .local. Nothing is installed into
the host system, and Homebrew libraries are never linked or staged.

  ICU4C 78.3  shared @rpath dylibs, data packaged as libicudata, symbol renaming on
  lz4 1.10.0  static liblz4.a
  xz 5.8.4    static liblzma.a
"""
import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREFIX = ROOT / '.local'
DEPS = ROOT / '.deps'
BUILD = ROOT / 'build/macos/deps'
MACOS_MIN = '15.0'  # Matches build/core/combo/HOST_darwin-arm64.mk mac_sdk_version.

ICU_VERSION = '78.3'
ICU_MAJOR = ICU_VERSION.split('.')[0]
# sha512 of this tarball equals upstream release-78.3 SHASUM512.txt (04a49455...225245).
ICU_SHA256 = '3a2e7a47604ba702f345878308e6fefeca612ee895cf4a5f222e7955fabfe0c0'
ICU_URL = f'https://github.com/unicode-org/icu/releases/download/release-{ICU_VERSION}/icu4c-{ICU_VERSION}-sources.tgz'
LZ4_VERSION = '1.10.0'
# Equals the upstream lz4-1.10.0.tar.gz.sha256 release asset.
LZ4_SHA256 = '537512904744b35e232912055ccf8ec66d768639ff3abe5788d90d792ec5f48b'
LZ4_URL = f'https://github.com/lz4/lz4/releases/download/v{LZ4_VERSION}/lz4-{LZ4_VERSION}.tar.gz'
XZ_VERSION = '5.8.4'
# Equals the independently published Homebrew xz 5.8.4 source checksum.
XZ_SHA256 = '0014c7886930454fe8bd4228665b51af55eeae560ea135c9c4cd33f55b2591d9'
XZ_URL = f'https://github.com/tukaani-project/xz/releases/download/v{XZ_VERSION}/xz-{XZ_VERSION}.tar.gz'
ICU_LIBRARIES = ('icudata', 'icuuc', 'icui18n')

ART_TARGETS = ('dalvikvm', 'libart', 'libopenjdkjvm', 'libjavacore', 'libopenjdk',
               'libnativehelper', 'libsigchain', 'core-oj-hostdex', 'core-libart-hostdex')
HOST_TAG = 'darwin-x86'

# Variables that could route a build to Homebrew or other host-wide headers/libs.
_LEAKY_ENV = ('CPATH', 'C_INCLUDE_PATH', 'CPLUS_INCLUDE_PATH', 'OBJC_INCLUDE_PATH',
              'LIBRARY_PATH', 'PKG_CONFIG_PATH', 'LDFLAGS', 'CFLAGS', 'CXXFLAGS', 'CPPFLAGS',
              'DYLD_LIBRARY_PATH', 'DYLD_FALLBACK_LIBRARY_PATH', 'DYLD_INSERT_LIBRARIES')


def run(argv, cwd=ROOT, env=None):
    subprocess.run([str(x) for x in argv], cwd=cwd, env=env, check=True)


def tool(name):
    return subprocess.check_output(['xcrun', '-f', name], text=True).strip()


def sdk():
    return subprocess.check_output(['xcrun', '--sdk', 'macosx', '--show-sdk-path'], text=True).strip()


def ncpu():
    return int(subprocess.check_output(['sysctl', '-n', 'hw.ncpu'], text=True).strip())


def dependency_env():
    """Clean compiler environment for third-party configure/make builds."""
    env = {key: value for key, value in os.environ.items() if key not in _LEAKY_ENV}
    # The toolchain clang (unlike the /usr/bin shim) needs the SDK named explicitly.
    target = f'-arch arm64 -isysroot {sdk()} -mmacosx-version-min={MACOS_MIN}'
    env.update(CC=tool('clang'), CXX=tool('clang++'), AR=tool('ar'), RANLIB=tool('ranlib'),
               CFLAGS=target + ' -O2', CXXFLAGS=target + ' -O2', LDFLAGS=target,
               INSTALL='/usr/bin/install -C', SDKROOT=sdk(), MACOSX_DEPLOYMENT_TARGET=MACOS_MIN)
    return env


def art_env():
    """ART make environment: in-tree .local headers and libraries searched first.

    The macOS SDK ships a partial unicode/ header set (for libicucore) and
    liblzma.tbd. CPATH/LIBRARY_PATH are searched before the SDK, so the pinned
    in-tree ICU headers, liblz4.a and liblzma.a win.
    """
    env = {key: value for key, value in os.environ.items() if key not in _LEAKY_ENV}
    env.update(CPATH=str(PREFIX / 'include'), LIBRARY_PATH=str(PREFIX / 'lib'),
               MACOSX_DEPLOYMENT_TARGET=MACOS_MIN)
    return env


def fetch(url, sha256, name):
    DEPS.mkdir(exist_ok=True)
    archive = DEPS / name
    if not archive.exists():
        with tempfile.NamedTemporaryFile(dir=DEPS, delete=False) as stream:
            temporary = Path(stream.name)
        try:
            with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
                while block := source.read(1024 * 1024):
                    output.write(block)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != sha256:
                raise SystemExit(f'{name}: download checksum mismatch')
            os.replace(temporary, archive)
        finally:
            temporary.unlink(missing_ok=True)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != sha256:
        raise SystemExit(f'{name}: source checksum mismatch; refusing modified source')
    return archive


def extract(archive, destination, top):
    """Extract once into destination (which then contains top/).

    Extraction goes to a private staging directory and top/ is renamed into
    place last, so an interrupted run never leaves a half-extracted tree that a
    later run would mistake for a complete one.
    """
    source = destination / top
    if not source.is_dir():
        destination.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix=f'.{top}.', dir=destination))
        try:
            with tarfile.open(archive) as tar:
                tar.extractall(staging, filter='data')
            if not (staging / top).is_dir():
                raise SystemExit(f'{archive.name}: unexpected archive layout')
            os.rename(staging / top, source)
        finally:
            shutil.rmtree(staging, ignore_errors=True)
    if not source.is_dir():
        raise SystemExit(f'{archive.name}: unexpected archive layout')
    return source


def install_license(source_file, name, version, url, sha256):
    target = PREFIX / 'share/licenses' / name
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source_file, target / source_file.name)
    (target / 'PROVENANCE').write_text(f'{name} {version}\nsource: {url}\nsha256: {sha256}\n'
                                       'Built unmodified by scripts/bootstrap_darwin.py.\n')


def icu_names():
    """Install-name leaves: libicuuc.78.dylib -> @rpath/libicuuc.78.dylib."""
    return [f'lib{library}.{ICU_MAJOR}.dylib' for library in ICU_LIBRARIES]


def _load_commands(path):
    return subprocess.check_output(['otool', '-l', str(path)], text=True)


def _dependencies(path):
    lines = subprocess.check_output(['otool', '-L', str(path)], text=True).splitlines()[1:]
    return [line.strip().split(' (')[0] for line in lines if line.strip()]


def normalize_icu_install_names(libdir=PREFIX / 'lib'):
    """Give every ICU dylib @rpath install names and an @loader_path rpath.

    Idempotent: re-signs ad hoc (install_name_tool invalidates the linker
    signature, which arm64 dyld rejects) only when it changed a file.
    """
    names = icu_names()
    for name in names:
        # libicuuc.78.dylib -> libicuuc.78.3.dylib; the install name is the major-version link.
        path = (libdir / name).resolve()
        if not path.is_file() or path.parent != libdir.resolve():
            raise SystemExit(f'ICU library missing after install: {libdir / name}')
        changes = []
        identity = subprocess.check_output(['otool', '-D', str(path)], text=True).splitlines()[1:]
        if identity != [f'@rpath/{name}']:
            changes += ['-id', f'@rpath/{name}']
        siblings = False
        for dependency in _dependencies(path):
            leaf = os.path.basename(dependency)
            if leaf in names and leaf != name:
                siblings = True
                if dependency != f'@rpath/{leaf}':
                    changes += ['-change', dependency, f'@rpath/{leaf}']
        # Only libraries that load ICU siblings need the rpath (libicudata needs none
        # and, linked by pkgdata, has no header padding for an extra load command).
        if siblings and 'path @loader_path ' not in _load_commands(path):
            changes += ['-add_rpath', '@loader_path']
        if changes:
            run(['install_name_tool', *changes, path])
            run(['codesign', '--force', '--sign', '-', path])
        for dependency in _dependencies(path):
            if not dependency.startswith(('@rpath/', '/usr/lib/', '/System/')):
                raise SystemExit(f'{name}: non-relocatable dependency {dependency}')


def build_icu():
    archive = fetch(ICU_URL, ICU_SHA256, f'icu4c-{ICU_VERSION}-sources.tgz')
    source = extract(archive, DEPS / f'icu4c-{ICU_VERSION}', 'icu') / 'source'
    build = BUILD / f'icu-{ICU_VERSION}'
    build.mkdir(parents=True, exist_ok=True)
    env = dependency_env()
    # Room for the @rpath install names and rpath rewritten after install.
    env['LDFLAGS'] += ' -Wl,-headerpad_max_install_names'
    if not (build / 'config.status').is_file():
        # Tools stay enabled: pkgdata/icupkg are required to package libicudata.
        run([source / 'configure', '--prefix=' + str(PREFIX), '--libdir=' + str(PREFIX / 'lib'),
             '--enable-shared', '--disable-static', '--with-data-packaging=library',
             '--disable-tests', '--disable-samples', '--disable-extras', '--disable-icuio',
             '--disable-layoutex', '--disable-icu-config', '--disable-debug', '--enable-release'],
            cwd=build, env=env)
    run(['make', f'-j{ncpu()}'], cwd=build, env=env)
    # Install only the libraries and their headers, not the ICU command-line tools.
    for directory in ('data', 'common', 'i18n'):
        run(['make', 'install'], cwd=build / directory, env=env)
    # mh-darwin and pkgdata link with absolute $(libdir) install names (the libs
    # carry -headerpad_max_install_names); rewrite them to @rpath so the dylibs
    # can be staged next to the ART natives.
    normalize_icu_install_names()
    install_license(source.parent / 'LICENSE', 'icu', ICU_VERSION, ICU_URL, ICU_SHA256)


def build_lz4():
    archive = fetch(LZ4_URL, LZ4_SHA256, f'lz4-{LZ4_VERSION}.tar.gz')
    source = extract(archive, DEPS, f'lz4-{LZ4_VERSION}')
    # The lz4 make build is in-source (lib/Makefile includes ../Makefile.inc);
    # build a private copy so the verified extracted source stays pristine.
    build = BUILD / f'lz4-{LZ4_VERSION}'
    if build.exists():
        shutil.rmtree(build)
    shutil.copytree(source, build, symlinks=True)
    env = dependency_env()
    run(['make', f'-j{ncpu()}', 'install', 'BUILD_SHARED=no', 'BUILD_STATIC=yes',
         'PREFIX=' + str(PREFIX), 'LIBDIR=' + str(PREFIX / 'lib')], cwd=build / 'lib', env=env)
    for stale in (PREFIX / 'lib').glob('liblz4*.dylib'):
        raise SystemExit(f'unexpected shared lz4 in prefix: {stale}')
    install_license(source / 'lib/LICENSE', 'lz4', LZ4_VERSION, LZ4_URL, LZ4_SHA256)


def build_xz():
    archive = fetch(XZ_URL, XZ_SHA256, f'xz-{XZ_VERSION}.tar.gz')
    source = extract(archive, DEPS, f'xz-{XZ_VERSION}')
    build = BUILD / f'xz-{XZ_VERSION}'
    build.mkdir(parents=True, exist_ok=True)
    env = dependency_env()
    if not (build / 'config.status').is_file():
        run([source / 'configure', '--prefix=' + str(PREFIX), '--libdir=' + str(PREFIX / 'lib'),
             '--disable-shared', '--enable-static', '--with-pic', '--disable-xz', '--disable-xzdec',
             '--disable-lzmadec', '--disable-lzmainfo', '--disable-lzma-links', '--disable-scripts',
             '--disable-doc', '--disable-nls'], cwd=build, env=env)
    run(['make', f'-j{ncpu()}'], cwd=build / 'src/liblzma', env=env)
    run(['make', 'install'], cwd=build / 'src/liblzma', env=env)
    for stale in (PREFIX / 'lib').glob('liblzma*.dylib'):
        raise SystemExit(f'unexpected shared liblzma in prefix: {stale}')
    install_license(source / 'COPYING.0BSD', 'xz', XZ_VERSION, XZ_URL, XZ_SHA256)


def deps_key():
    """Everything the dependency builds bake into their Makefiles and outputs."""
    clang = subprocess.check_output([tool('clang'), '--version'], text=True)
    material = '\n'.join([hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), str(PREFIX),
                          str(BUILD), sdk(), MACOS_MIN, tool('clang'), clang])
    return hashlib.sha256(material.encode()).hexdigest()


def deps_outputs():
    return ([PREFIX / 'lib' / name for name in icu_names()]
            + [PREFIX / 'lib/liblz4.a', PREFIX / 'lib/liblzma.a',
               PREFIX / 'include/unicode/uversion.h', PREFIX / 'include/lz4.h',
               PREFIX / 'include/lzma.h'])


def build_deps():
    """Build ICU, lz4 and liblzma once per configuration.

    A stamp keyed by this script, the prefix, SDK, deployment target and
    compiler skips the rebuild (so ART is not recompiled against freshly
    reinstalled headers); any change wipes the configured build directories,
    because configure bakes those values into its generated Makefiles.
    """
    stamp = BUILD / 'deps.stamp'
    key = deps_key()
    if (stamp.is_file() and stamp.read_text().strip() == key
            and all(path.exists() for path in deps_outputs())):
        return
    stamp.unlink(missing_ok=True)
    for directory in (f'icu-{ICU_VERSION}', f'xz-{XZ_VERSION}', f'lz4-{LZ4_VERSION}'):
        shutil.rmtree(BUILD / directory, ignore_errors=True)
    build_xz()
    build_lz4()
    build_icu()
    BUILD.mkdir(parents=True, exist_ok=True)
    stamp.write_text(key + '\n')


def write_versions_checked(tree):
    """Skip main.mk's case-sensitive-FS and Oracle-JDK host checks without patching."""
    out = Path(tree) / 'out'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'versions_checked.mk').write_text('VERSIONS_CHECKED := 5\nBUILD_EMULATOR ?= false\n')


def art_make_command(targets=ART_TARGETS, jobs=None, dry_run=False):
    return ['make', *(['-n'] if dry_run else []), f'-j{jobs or ncpu()}', 'SHELL=/bin/bash',
            'ARCH=aarch64', 'WITH_HOST_DALVIK=true', 'ART_BUILD_HOST_DEBUG=false',
            '____PREFIX=' + str(PREFIX), '____LIBDIR=lib', *targets]


def stage_icu(tree):
    """Copy the in-tree ICU dylibs next to the ART natives (lib64, rpath @loader_path)."""
    lib64 = Path(tree) / 'out/host' / HOST_TAG / 'lib64'
    lib64.mkdir(parents=True, exist_ok=True)
    for name in icu_names():
        # Fresh inode: overwriting a loaded or previously exec'd dylib in place
        # leaves XNU's cached signature stale (SIGKILL at the next dlopen).
        fresh = lib64 / f'.{name}.fresh'
        shutil.copy2(PREFIX / 'lib' / name, fresh)
        os.replace(fresh, lib64 / name)
    return lib64


def refresh_vnodes(host_out):
    """Give every installed Mach-O a fresh inode.

    The AOSP install rules copy rebuilt binaries over the previous ones in
    place. XNU caches a code signature per vnode, so a rewritten-in-place
    binary is killed with SIGKILL (Code Signature Invalid) at exec or dlopen
    on an incremental rebuild. Copy-and-rename makes each a new vnode.
    """
    for directory in ('bin', 'lib64'):
        for path in sorted((Path(host_out) / directory).glob('*')):
            if not path.is_file() or path.is_symlink():
                continue
            with path.open('rb') as stream:
                if stream.read(4) != b'\xcf\xfa\xed\xfe':  # MH_MAGIC_64, little-endian
                    continue
            fresh = path.with_name(f'.{path.name}.fresh')
            shutil.copy2(path, fresh)
            os.replace(fresh, path)


def build_art(tree=ROOT / 'context/art_standalone', dry_run=False, log=None):
    """Build the M3 ART host modules for darwin-arm64 and desugar the boot jars.

    Requires the darwin patch chain to be applied to tree. The caller owns
    .deps/runtime-build.lock. With dry_run, only print make's commands.
    """
    tree = Path(tree)
    write_versions_checked(tree)
    command = art_make_command(dry_run=dry_run)
    if log is not None:
        with open(log, 'w') as stream:
            subprocess.run(command, cwd=tree, env=art_env(), check=True, stdout=stream,
                           stderr=subprocess.STDOUT)
    else:
        run(command, cwd=tree, env=art_env())
    if dry_run:
        return
    refresh_vnodes(tree / 'out/host' / HOST_TAG)
    stage_icu(tree)
    sys.path.insert(0, str(ROOT / 'scripts'))
    from bootstrap_boot import desugar_boot
    desugar_boot(ROOT, host=tree / 'out/host', host_tag=HOST_TAG, work=ROOT / 'build/macos/runtime-boot')


def run_stage(stage):
    """Darwin branch of bootstrap_runtime.py; the caller owns the build lock."""
    if stage == 'bridges':
        build_deps()
        return
    if stage == 'art':
        build_deps()
        build_art()
        return
    raise SystemExit(f'bootstrap stage {stage!r} is not supported on macOS yet')


def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('bootstrap_darwin.py builds for macOS arm64 hosts only')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('stage', choices=['deps', 'art'], default='deps', nargs='?')
    parser.add_argument('--tree', type=Path, default=ROOT / 'context/art_standalone',
                        help='patched art_standalone checkout to build (stage art)')
    parser.add_argument('--dry-run', action='store_true', help='print the ART make commands only')
    parser.add_argument('--log', type=Path, help='write ART make output to this file')
    args = parser.parse_args()
    DEPS.mkdir(exist_ok=True)
    lock = (DEPS / 'runtime-build.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'Another Shiraz runtime bootstrap owns the build lock. Wait for it to finish.\n')
    if args.stage == 'deps':
        build_deps()
        return
    build_art(args.tree, dry_run=args.dry_run, log=args.log)


if __name__ == '__main__':
    main()
