#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the pinned macOS arm64 text, path and image dependencies in-tree.

A separate stage from scripts/bootstrap_darwin.py, so changing this file never
invalidates that script's ICU/ART dependency stamp. Sources come from
checksum-verified upstream release tarballs under .deps, build under
build/macos/deps and install as static, position-independent arm64 archives
into .local (lib/, include/, lib/pkgconfig/). Nothing is installed into the
host system, and no Homebrew library is searched or linked: pkg-config only
sees .local/lib/pkgconfig, Meson's CMake dependency lookup is disabled, and
the CMake builds ignore the Homebrew prefixes. zlib and expat come from the
macOS SDK (stub zlib.pc and expat.pc describe them); ICU is the in-tree ICU
78.3 from bootstrap_darwin.py.

  FreeType 2.14.3      libfreetype.a (SDK zlib; no bzip2, brotli, png or harfbuzz)
  HarfBuzz 15.1.0      libharfbuzz.a (core + hb-ft only)
  fontconfig 2.18.3    libfontconfig.a + an in-tree fonts.conf (no host state)
  pixman 0.46.4        libpixman-1.a
  cairo 1.18.6         libcairo.a (image surfaces only)
  libwebp 1.6.0        libwebp.a, libsharpyuv.a, libwebpdemux.a (no tools)
  libjpeg-turbo 3.2.1  libjpeg.a (libjpeg 6.2 ABI; no TurboJPEG, tools or tests)

Build tools: Meson 1.11.2 and Ninja 1.13.2 are installed from hash-pinned
PyPI wheels into .local/tools/meson-venv with uv on the OS /usr/bin/python3;
uv, CMake and pkg-config are taken from the host PATH (Homebrew on the
reference Mac) as tools only. The python3 that runs this script must support
tarfile extraction filters (Python 3.12+, or a 3.8-3.11 build with the 2023
backport): extract() from bootstrap_darwin.py uses filter='data', which the
macOS /usr/bin/python3 3.9.6 lacks. The reference Mac runs Homebrew python3.

The check stage also needs libpng16 and ICU 78.3, which only the deps stage of
scripts/bootstrap_darwin.py (make macos-art-deps, also run by make runtime-art)
installs into .local.

  python3 scripts/bootstrap_darwin_graphics.py          build (no-op when stamped)
  python3 scripts/bootstrap_darwin_graphics.py check    link and run tests/darwin/graphics
"""
import argparse
import fcntl
import hashlib
import inspect
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bootstrap_darwin import (BUILD, DEPS, MACOS_MIN, PREFIX, ROOT, dependency_env, extract,  # noqa: E402
                              fetch, ncpu, run, sdk, tool)

FONT_DIR = PREFIX / 'share/shiraz/fonts'
FONTCONFIG_CACHE = PREFIX / 'var/cache/fontconfig'
FONTS_CONF = PREFIX / 'etc/fonts/fonts.conf'
TOOLS = PREFIX / 'tools'
VENV = TOOLS / 'meson-venv'
STAMP = BUILD / 'graphics.stamp'
CHECK_OUT = ROOT / 'build/macos/graphics-deps-check'
# Host font directories fontconfig may read (never write). ~/Library/Fonts is
# deliberately absent: per-user host state must not change what an app sees.
SYSTEM_FONT_DIRS = ('/System/Library/Fonts', '/Library/Fonts')
# Read by conf.d/50-user.conf (~/.config/fontconfig, ~/.fonts.conf); removed.
HOST_STATE_CONFS = ('50-user.conf',)
HOMEBREW_PREFIXES = ('/opt/homebrew', '/usr/local')

# Pure-Python Meson wheel and the universal2 Ninja wheel, sha256 from PyPI's JSON API.
# Meson 1.11.x is the newest series that runs on the OS python3 (3.9; Meson 1.12
# needs 3.10), and fontconfig 2.18 needs Meson >= 1.11.0.
BUILD_TOOL_REQUIREMENTS = '''\
meson==1.11.2 --hash=sha256:7e4f6e83fec83e3eaac928e058b073c7557b282c35b6a2024cea143a39926a39
ninja==1.13.2 --hash=sha256:fd82e26c0706ad4ab88e5fdd26f3fab0a987a90f810160f6c322e752c6af298b
'''

# verified: how the sha256 pin was checked against upstream when it was chosen.
PACKAGES = {
    'freetype': dict(
        version='2.14.3', top='freetype-2.14.3', archive='freetype-2.14.3.tar.xz',
        url='https://download.savannah.gnu.org/releases/freetype/freetype-2.14.3.tar.xz',
        sha256='36bc4f1cc413335368ee656c42afca65c5a3987e8768cc28cf11ba775e785a5f',
        verified='Good GnuPG signature (freetype-2.14.3.tar.xz.sig) by Werner Lemberg <wl@gnu.org>, '
                 'key E30674707856409FF1948010BE6C3AAC63AD8E3F.',
        licenses=('LICENSE.TXT', 'docs/FTL.TXT', 'docs/GPLv2.TXT'),
        license_note='Dual-licensed; Shiraz (GPL-3.0-only) uses it under the FreeType License (docs/FTL.TXT).'),
    'harfbuzz': dict(
        version='15.1.0', top='harfbuzz-15.1.0', archive='harfbuzz-15.1.0.tar.xz',
        url='https://github.com/harfbuzz/harfbuzz/releases/download/15.1.0/harfbuzz-15.1.0.tar.xz',
        sha256='0fc7c565443a5f4be7da65ac5ee5bd737d9b158dc908708b7689a0a5796b0c8d',
        verified='Equals the sha256 digest GitHub publishes for the 15.1.0 release asset '
                 '(no upstream signature or checksum file is published).',
        licenses=('COPYING',)),
    'fontconfig': dict(
        version='2.18.3', top='fontconfig-2.18.3', archive='fontconfig-2.18.3.tar.xz',
        url='https://gitlab.freedesktop.org/api/v4/projects/890/packages/generic/fontconfig/2.18.3/fontconfig-2.18.3.tar.xz',
        sha256='4f7b554a38cdf78c033f666c8871f3749e14a094f65a07f630c91ed0b43d35e3',
        verified='Equals the upstream fontconfig-2.18.3.tar.xz.sha256sum release asset '
                 '(same GitLab release; unsigned).',
        licenses=('COPYING',)),
    'pixman': dict(
        version='0.46.4', top='pixman-0.46.4', archive='pixman-0.46.4.tar.xz',
        url='https://cairographics.org/releases/pixman-0.46.4.tar.xz',
        sha256='a098c33924754ad43f981b740f6d576c70f9ed1006e12221b1845431ebce1239',
        verified='sha512 equals pixman-0.46.4.tar.xz.sha512, whose .asc has a good GnuPG signature by '
                 'Matt Turner <mattst88@gmail.com>, key 16238A7D92448B02B7B10344B6B1CEAE5103DB07.',
        licenses=('COPYING',)),
    'cairo': dict(
        version='1.18.6', top='cairo-1.18.6', archive='cairo-1.18.6.tar.xz',
        url='https://cairographics.org/releases/cairo-1.18.6.tar.xz',
        sha256='1c767308174337a74694da0f3ec069c271452163a1ef4540964c50c301f157d4',
        verified='Equals cairo-1.18.6.tar.xz.sha256sum, whose .asc has a good GnuPG signature by '
                 'Emmanuele Bassi <ebassi@gnome.org>, key 53EF3DC3B63E2899271BD26322E8091EEA11BBB7.',
        licenses=('COPYING', 'COPYING-LGPL-2.1', 'COPYING-MPL-1.1'),
        license_note='LGPL-2.1 or MPL-1.1; Shiraz (GPL-3.0-only) uses it under LGPL-2.1.'),
    'libwebp': dict(
        version='1.6.0', top='libwebp-1.6.0', archive='libwebp-1.6.0.tar.gz',
        url='https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz',
        sha256='e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564',
        verified='Good GnuPG signature (libwebp-1.6.0.tar.gz.asc) by WebP release signing key '
                 '<webp-discuss@webmproject.org>, key 6B0E6B70976DE303EDF2F601F9C3D6BDB8232B5D.',
        licenses=('COPYING', 'PATENTS')),
    'libjpeg-turbo': dict(
        version='3.2.1', top='libjpeg-turbo-3.2.1', archive='libjpeg-turbo-3.2.1.tar.gz',
        url='https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.2.1/libjpeg-turbo-3.2.1.tar.gz',
        sha256='c27ad72a68da151177929aa969ae773a2da5ac2af9ce946079314fdac8cece7f',
        verified='Good GnuPG signature (libjpeg-turbo-3.2.1.tar.gz.sig) by The libjpeg-turbo Project, '
                 'key 0338C8D8D9FDA62CF9C421BD7EC2DBB6F4DBF434 (the LJT-GPG-KEY in libjpeg-turbo/repo); '
                 'also equals the GitHub release asset sha256 digest.',
        licenses=('LICENSE.md', 'README.ijg')),
}
BUILD_ORDER = ('freetype', 'harfbuzz', 'fontconfig', 'pixman', 'cairo', 'libwebp', 'libjpeg-turbo')

OUTPUTS = ([PREFIX / 'lib' / name for name in (
    'libfreetype.a', 'libharfbuzz.a', 'libfontconfig.a', 'libpixman-1.a', 'libcairo.a',
    'libwebp.a', 'libsharpyuv.a', 'libjpeg.a')]
    + [PREFIX / 'lib/pkgconfig' / f'{name}.pc' for name in (
        'freetype2', 'harfbuzz', 'fontconfig', 'pixman-1', 'cairo', 'libwebp', 'libsharpyuv',
        'libjpeg', 'zlib', 'expat')]
    + [PREFIX / 'include' / name for name in (
        'freetype2/ft2build.h', 'harfbuzz/hb-ft.h', 'fontconfig/fontconfig.h', 'pixman-1/pixman.h',
        'cairo/cairo.h', 'webp/decode.h', 'webp/encode.h', 'jpeglib.h')]
    + [FONTS_CONF, VENV / 'bin/meson', VENV / 'bin/ninja'])

# The archives may import only from libSystem, the SDK's libz/libexpat and
# libc++. check() enforces that by linking every member of all eight archives
# (-all_load) into a throwaway dylib against exactly those libraries: any other
# import (libpng, ICU, GLib, iconv, liblzma, CoreText, ...) fails to resolve.
# These families are also rejected by name, so a leak is reported clearly even
# when some other library on the link line happened to provide the symbol.
LINK_GATE_LIBRARIES = ('-lz', '-lexpat', '-lc++')
DENIED_IMPORT = re.compile(
    r'^_(png_|u_|ubidi_|ubrk_|ucol_|uscript_|g_|gobject_|hb_glib_|hb_icu_|iconv|libiconv|lzma_|json_|BZ2_|'
    r'Brotli|cairo_quartz|CT[A-Z]|CG[A-Z]|CF[A-Z])')


def build_env():
    """dependency_env() plus pkg-config, PATH and visibility isolation."""
    env = dependency_env()
    for key in ('CMAKE_PREFIX_PATH', 'CMAKE_FRAMEWORK_PATH', 'CMAKE_APPBUNDLE_PATH', 'PKG_CONFIG_SYSROOT_DIR',
                'ACLOCAL_PATH', 'PYTHONPATH', 'PYTHONHOME'):
        env.pop(key, None)
    flags = ' -fPIC -fvisibility=hidden'
    env['CFLAGS'] += flags
    env['CXXFLAGS'] += flags + ' -fvisibility-inlines-hidden'
    env['PKG_CONFIG_LIBDIR'] = str(PREFIX / 'lib/pkgconfig')
    env.pop('PKG_CONFIG_PATH', None)
    # No /opt/homebrew/bin: optional build programs (ragel, ttx, gs, gperf, ...)
    # resolve to the venv or the OS, never to Homebrew. Tools are passed by path.
    env['PATH'] = os.pathsep.join([str(VENV / 'bin'), '/usr/bin', '/bin', '/usr/sbin', '/sbin'])
    return env


def host_tool(name):
    path = shutil.which(name)
    if not path:
        raise SystemExit(f'{name} is required as a build tool and was not found on PATH')
    return path


def log(message):
    print(f'== bootstrap_darwin_graphics: {message}', flush=True)


OS_PYTHON = '/usr/bin/python3'


def tool_version(command):
    """First line of COMMAND's version output, or None when it cannot run."""
    try:
        output = subprocess.run(command, capture_output=True, text=True, timeout=60, check=True).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    return output.strip().splitlines()[0] if output.strip() else None


def venv_record():
    """The requirements plus the interpreter the venv was created from."""
    # /usr/bin/python3 is an xcrun shim; record the interpreter it runs.
    interpreter = tool_version([OS_PYTHON, '-c', 'import sys; print(sys.executable, sys.version.split()[0])'])
    return BUILD_TOOL_REQUIREMENTS + f'# python {interpreter}\n'


def venv_usable():
    """The venv's own meson and ninja run and report the pinned versions."""
    pins = dict(re.findall(r'^(meson|ninja)==(\S+)', BUILD_TOOL_REQUIREMENTS, re.M))
    # The ninja wheel reports e.g. 1.13.2.git.kitware.jobserver-pipe-1.
    versions = {name: tool_version([str(VENV / 'bin' / name), '--version']) or '' for name in pins}
    return all(versions[name] == pin or versions[name].startswith(pin + '.') for name, pin in pins.items())


def install_build_tools():
    """Meson and Ninja in a private venv from hash-pinned wheels.

    Reused only when the recorded requirements and OS interpreter still match
    and the venv's meson and ninja run at the pinned versions: an Xcode update
    can replace the python3 the venv links to.
    """
    requirements = TOOLS / 'meson-requirements.txt'
    record = venv_record()
    if requirements.is_file() and requirements.read_text() == record and venv_usable():
        return
    shutil.rmtree(VENV, ignore_errors=True)
    TOOLS.mkdir(parents=True, exist_ok=True)
    requirements.write_text(BUILD_TOOL_REQUIREMENTS)
    env = {key: value for key, value in os.environ.items() if not key.startswith(('UV_', 'PIP_', 'VIRTUAL_ENV'))}
    env.update(UV_PYTHON_DOWNLOADS='never', UV_CACHE_DIR=str(DEPS / 'uv-cache'), UV_NO_CONFIG='1')
    uv = host_tool('uv')
    # The OS python3, not a Homebrew one: the venv must not depend on Homebrew's lifecycle.
    run([uv, 'venv', '--python', '/usr/bin/python3', '--no-project', VENV], env=env)
    run([uv, 'pip', 'install', '--python', VENV / 'bin/python', '--require-hashes', '--no-deps',
         '--only-binary', ':all:', '--index-url', 'https://pypi.org/simple', '-r', requirements], env=env)
    if not venv_usable():
        raise SystemExit(f'{VENV}: meson/ninja do not run at the pinned versions after installation')
    requirements.write_text(record)


def source_tree(name):
    package = PACKAGES[name]
    archive = fetch(package['url'], package['sha256'], package['archive'])
    return extract(archive, DEPS, package['top'])


def build_dir(name):
    return BUILD / f"{name}-{PACKAGES[name]['version']}"


def meson_native_file(directory):
    """Pin the compilers and pkg-config; an unrunnable cmake disables Meson's CMake lookup."""
    lines = ['[binaries]']
    for key, value in (('c', tool('clang')), ('cpp', tool('clang++')), ('objc', tool('clang')),
                       ('ar', tool('ar')), ('strip', tool('strip')), ('pkg-config', host_tool('pkg-config')),
                       ('cmake', '/usr/bin/false')):
        lines.append(f"{key} = '{value}'")
    path = directory / 'native.ini'
    path.write_text('\n'.join(lines) + '\n')
    return path


def meson_build(name, options, extra=()):
    source = source_tree(name)
    build = build_dir(name)
    env = build_env()
    # A private HOME: Meson's wrap support otherwise reads ~/.netrc, and no
    # per-user host file should reach a dependency build.
    env['HOME'] = str(build / 'home')
    meson = VENV / 'bin/meson'
    if not (build / 'out/build.ninja').is_file():
        shutil.rmtree(build, ignore_errors=True)
        (build / 'home').mkdir(parents=True)
        native = meson_native_file(build)
        run([meson, 'setup', build / 'out', source, '--native-file', native, '--prefix', PREFIX,
             '--libdir', 'lib', '--buildtype', 'release', '--default-library', 'static',
             '-Db_staticpic=true', '-Db_ndebug=if-release', '--wrap-mode', 'nofallback',
             *extra, *[f'-D{option}' for option in options]], cwd=build, env=env)
    run([meson, 'compile', '-C', build / 'out', '-j', str(ncpu())], cwd=build, env=env)
    run([meson, 'install', '-C', build / 'out', '--no-rebuild', '--quiet'], cwd=build, env=env)
    return source


def cmake_build(name, options):
    source = source_tree(name)
    build = build_dir(name)
    env = build_env()
    if not (build / 'out/CMakeCache.txt').is_file():
        shutil.rmtree(build, ignore_errors=True)
        build.mkdir(parents=True)
        run([host_tool('cmake'), '-S', source, '-B', build / 'out', '-G', 'Ninja',
             f"-DCMAKE_MAKE_PROGRAM={VENV / 'bin/ninja'}", f"-DCMAKE_C_COMPILER={tool('clang')}",
             f"-DCMAKE_CXX_COMPILER={tool('clang++')}", '-DCMAKE_BUILD_TYPE=Release',
             f'-DCMAKE_INSTALL_PREFIX={PREFIX}', '-DCMAKE_INSTALL_LIBDIR=lib',
             '-DCMAKE_OSX_ARCHITECTURES=arm64', f'-DCMAKE_OSX_DEPLOYMENT_TARGET={MACOS_MIN}',
             f'-DCMAKE_OSX_SYSROOT={sdk()}', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON',
             '-DCMAKE_C_VISIBILITY_PRESET=hidden', '-DCMAKE_CXX_VISIBILITY_PRESET=hidden',
             '-DCMAKE_VISIBILITY_INLINES_HIDDEN=ON', '-DBUILD_SHARED_LIBS=OFF',
             f"-DCMAKE_IGNORE_PREFIX_PATH={';'.join(HOMEBREW_PREFIXES)}",
             f"-DCMAKE_SYSTEM_IGNORE_PREFIX_PATH={';'.join(HOMEBREW_PREFIXES)}",
             '-DCMAKE_FIND_USE_PACKAGE_REGISTRY=OFF', '-DCMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY=OFF',
             '-DCMAKE_FIND_FRAMEWORK=NEVER', '-DCMAKE_FIND_APPBUNDLE=NEVER',
             f"-DPKG_CONFIG_EXECUTABLE={host_tool('pkg-config')}",
             *[f'-D{option}' for option in options]], cwd=build, env=env)
    run([host_tool('cmake'), '--build', build / 'out', '--parallel', str(ncpu())], cwd=build, env=env)
    run([host_tool('cmake'), '--install', build / 'out'], cwd=build, env=env)
    return source


def install_licenses(name, source, options):
    package = PACKAGES[name]
    target = PREFIX / 'share/licenses' / name
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True)
    for relative in package['licenses']:
        shutil.copyfile(source / relative, target / Path(relative).name)
    note = package.get('license_note')
    (target / 'PROVENANCE').write_text(
        f"{name} {package['version']}\nsource: {package['url']}\nsha256: {package['sha256']}\n"
        f"pin check: {package['verified']}\n" + (f'license: {note}\n' if note else '')
        + 'Built unmodified as a static arm64 archive by scripts/bootstrap_darwin_graphics.py.\n'
        + 'options: ' + ' '.join(options) + '\n')


def sdk_version(header, macro):
    text = (Path(sdk()) / 'usr/include' / header).read_text(errors='replace')
    match = re.search(rf'#\s*define\s+{macro}\s+"?([0-9][0-9.]*)', text)
    if not match:
        raise SystemExit(f'cannot read {macro} from the SDK {header}')
    return match.group(1)


def sdk_expat_version():
    text = (Path(sdk()) / 'usr/include/expat.h').read_text(errors='replace')
    parts = [re.search(rf'#\s*define\s+XML_{part}_VERSION\s+(\d+)', text) for part in ('MAJOR', 'MINOR', 'MICRO')]
    if not all(parts):
        raise SystemExit('cannot read the expat version from the SDK expat.h')
    return '.'.join(part.group(1) for part in parts)


def install_sdk_pkgconfig():
    """zlib.pc and expat.pc for the SDK libraries (headers come from -isysroot)."""
    directory = PREFIX / 'lib/pkgconfig'
    directory.mkdir(parents=True, exist_ok=True)
    for name, version, library, description in (
            ('zlib', sdk_version('zlib.h', 'ZLIB_VERSION'), 'z', 'zlib from the macOS SDK'),
            ('expat', sdk_expat_version(), 'expat', 'expat from the macOS SDK')):
        (directory / f'{name}.pc').write_text(
            f'# Written by scripts/bootstrap_darwin_graphics.py: the macOS SDK lib{library}.tbd.\n'
            f'Name: {name}\nDescription: {description}\nVersion: {version}\nLibs: -l{library}\nCflags:\n')


# Generic families bound strongly to the bundled fonts (make fonts), in the
# order of the bundled android-fonts.xml fallback list. native/text.cpp asks
# for "sans-serif" plus the text's charset; fontconfig ranks charset coverage
# first and a strong family next, so bundled faces win over host faces that
# cover the same text (for example Arial in /System/Library/Fonts/Supplemental,
# which the stock 60-latin.conf prefers weakly). System fonts remain only as a
# fallback for scripts the bundled set does not cover. Same choices as the
# AppImage fonts.conf in scripts/build_appimage.py.
BUNDLED_FAMILIES = (
    ('sans-serif', ('Noto Sans', 'Noto Naskh Arabic', 'Noto Sans Arabic', 'Noto Sans Hebrew', 'Noto Sans Lao')),
    ('serif', ('Noto Serif',)),
    ('monospace', ('Droid Sans Mono',)),
)


def fonts_conf_text():
    dirs = ''.join(f'  <dir>{path}</dir>\n' for path in (FONT_DIR, *SYSTEM_FONT_DIRS))
    # Before the conf.d include, so these prepends precede every stock rule.
    aliases = ''.join(
        f'  <alias binding="strong">\n    <family>{generic}</family>\n    <prefer>\n'
        + ''.join(f'      <family>{family}</family>\n' for family in families)
        + '    </prefer>\n  </alias>\n'
        for generic, families in BUNDLED_FAMILIES)
    return ('<?xml version="1.0"?>\n<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">\n'
            '<!-- Written by scripts/bootstrap_darwin_graphics.py. Shiraz fonts plus read-only\n'
            '     macOS system font directories; the only cache directory is inside the\n'
            '     repository, and no per-user host configuration is read or written.\n'
            '     Generic families prefer the bundled fonts (strong binding). -->\n'
            f'<fontconfig>\n{dirs}  <cachedir>{FONTCONFIG_CACHE}</cachedir>\n{aliases}'
            f'  <include ignore_missing="yes">{PREFIX}/etc/fonts/conf.d</include>\n</fontconfig>\n')


def install_fonts_conf():
    conf_d = PREFIX / 'etc/fonts/conf.d'
    for name in HOST_STATE_CONFS:
        (conf_d / name).unlink(missing_ok=True)
    for path in sorted(conf_d.iterdir()):
        text = path.read_text(errors='replace')
        if re.search(r'prefix="(xdg|cwd)"|<(dir|cachedir|include)[^>]*>\s*~', text):
            raise SystemExit(f'{path}: fontconfig rule reads per-user host state')
    FONTCONFIG_CACHE.mkdir(parents=True, exist_ok=True)
    fresh = FONTS_CONF.with_name('.fonts.conf.fresh')
    fresh.write_text(fonts_conf_text())
    os.replace(fresh, FONTS_CONF)


FREETYPE_OPTIONS = ('brotli=disabled', 'bzip2=disabled', 'harfbuzz=disabled', 'png=disabled',
                    'tests=disabled', 'zlib=system', 'error_strings=false')
HARFBUZZ_OPTIONS = tuple(f'{name}=disabled' for name in (
    'glib', 'gobject', 'cairo', 'chafa', 'png', 'zlib', 'icu', 'graphite', 'fontations', 'gdi',
    'directwrite', 'coretext', 'harfrust', 'kbts', 'wasm', 'raster', 'vector', 'gpu', 'gpu_demo',
    'subset', 'tests', 'introspection', 'docs', 'utilities', 'benchmark')) + (
    'freetype=enabled', 'icu_builtin=false', 'doc_tests=false', 'experimental_api=false')


def fontconfig_options():
    return tuple(f'{name}=disabled' for name in (
        'doc', 'doc-txt', 'doc-man', 'doc-pdf', 'doc-html', 'nls', 'tests', 'tests-bwrap',
        'tests-external-fonts', 'tools', 'cache-build', 'iconv', 'fontations')) + (
        'xml-backend=expat', f'cache-dir={FONTCONFIG_CACHE}', 'additional-fonts-dirs=no',
        'default-fonts-dirs=' + ','.join((str(FONT_DIR), *SYSTEM_FONT_DIRS)))


PIXMAN_OPTIONS = ('gtk=disabled', 'libpng=disabled', 'tests=disabled', 'demos=disabled',
                  'openmp=disabled', 'timers=false', 'gnuplot=false')
CAIRO_OPTIONS = tuple(f'{name}=disabled' for name in (
    'dwrite', 'fontconfig', 'freetype', 'png', 'quartz', 'tee', 'xcb', 'xlib', 'xlib-xcb', 'zlib',
    'tests', 'lzo', 'gtk2-utils', 'glib', 'spectre', 'symbol-lookup')) + ('gtk_doc=false',)
WEBP_OPTIONS = ('WEBP_LINK_STATIC=ON', 'WEBP_BUILD_ANIM_UTILS=OFF', 'WEBP_BUILD_CWEBP=OFF',
                'WEBP_BUILD_DWEBP=OFF', 'WEBP_BUILD_GIF2WEBP=OFF', 'WEBP_BUILD_IMG2WEBP=OFF',
                'WEBP_BUILD_VWEBP=OFF', 'WEBP_BUILD_WEBPINFO=OFF', 'WEBP_BUILD_LIBWEBPMUX=OFF',
                'WEBP_BUILD_WEBPMUX=OFF', 'WEBP_BUILD_EXTRAS=OFF', 'WEBP_BUILD_FUZZTEST=OFF',
                'WEBP_USE_THREAD=ON', 'WEBP_ENABLE_SIMD=ON')
JPEG_OPTIONS = ('ENABLE_SHARED=OFF', 'ENABLE_STATIC=ON', 'WITH_TURBOJPEG=OFF', 'WITH_TOOLS=OFF',
                'WITH_TESTS=OFF', 'WITH_FUZZ=OFF', 'WITH_SIMD=ON', 'REQUIRE_SIMD=ON',
                'WITH_JPEG7=OFF', 'WITH_JPEG8=OFF')


def build_package(name):
    log(f"{name} {PACKAGES[name]['version']}")
    if name == 'freetype':
        options, source = FREETYPE_OPTIONS, meson_build('freetype', FREETYPE_OPTIONS)
    elif name == 'harfbuzz':
        options, source = HARFBUZZ_OPTIONS, meson_build('harfbuzz', HARFBUZZ_OPTIONS)
    elif name == 'fontconfig':
        options = fontconfig_options()
        source = meson_build('fontconfig', options, extra=('--sysconfdir', 'etc', '--localstatedir', 'var'))
        install_fonts_conf()
    elif name == 'pixman':
        options, source = PIXMAN_OPTIONS, meson_build('pixman', PIXMAN_OPTIONS)
    elif name == 'cairo':
        options, source = CAIRO_OPTIONS, meson_build('cairo', CAIRO_OPTIONS)
    elif name == 'libwebp':
        options, source = WEBP_OPTIONS, cmake_build('libwebp', WEBP_OPTIONS)
    else:
        options, source = JPEG_OPTIONS, cmake_build('libjpeg-turbo', JPEG_OPTIONS)
    install_licenses(name, source, options)


def verify_install():
    """Static arm64 archives only; no Homebrew path in archives or .pc files."""
    lib = PREFIX / 'lib'
    shared = [path for stem in ('freetype', 'harfbuzz', 'fontconfig', 'pixman', 'cairo', 'webp',
                                'sharpyuv', 'jpeg', 'turbojpeg')
              for path in lib.glob(f'lib{stem}*.dylib')]
    if shared:
        raise SystemExit('unexpected shared libraries in prefix: ' + ', '.join(map(str, shared)))
    missing = [str(path) for path in OUTPUTS if not path.exists()]
    if missing:
        raise SystemExit('graphics dependency outputs missing: ' + ', '.join(missing))
    for path in [*OUTPUTS[:8], *(lib / 'pkgconfig').glob('*.pc')]:
        data = path.read_bytes()
        # Archives may mention /usr/local in strings; a .pc file must not point there.
        for prefix in HOMEBREW_PREFIXES if path.suffix == '.pc' else HOMEBREW_PREFIXES[:1]:
            if f'{prefix}/'.encode() in data:
                raise SystemExit(f'{path}: references {prefix}')
    for archive in OUTPUTS[:8]:
        architectures = subprocess.check_output(['lipo', '-archs', str(archive)], text=True).split()
        if architectures != ['arm64']:
            raise SystemExit(f'{archive}: architectures {architectures}, expected arm64')


def graphics_key():
    clang = subprocess.check_output([tool('clang'), '--version'], text=True)
    # Host build tools change generated build files and options (CMake, pkgconf);
    # their versions are part of the key. The venv is validated separately.
    tools = [f"{name} {host_tool(name)} {tool_version([host_tool(name), '--version'])}"
             for name in ('cmake', 'pkg-config')]
    material = '\n'.join([hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), str(PREFIX), str(BUILD),
                          sdk(), MACOS_MIN, tool('clang'), clang, *tools])
    return hashlib.sha256(material.encode()).hexdigest()


def build_graphics():
    key = graphics_key()
    if STAMP.is_file() and STAMP.read_text().strip() == key and all(path.exists() for path in OUTPUTS):
        log('up to date (stamp matches)')
        return
    STAMP.unlink(missing_ok=True)
    # Meson and CMake bake compilers, flags and prefixes into their build trees.
    for name in BUILD_ORDER:
        shutil.rmtree(build_dir(name), ignore_errors=True)
    install_build_tools()
    install_sdk_pkgconfig()
    for name in BUILD_ORDER:
        build_package(name)
    verify_install()
    BUILD.mkdir(parents=True, exist_ok=True)
    STAMP.write_text(key + '\n')
    log('done')


# Test program -> (pkg-config modules, C++ link driver needed, arguments).
CHECKS = (
    ('freetype', ('freetype2',), False, lambda: [FONT_DIR / 'Roboto-Regular.ttf']),
    ('harfbuzz', ('harfbuzz', 'freetype2'), True, lambda: [FONT_DIR / 'Roboto-Regular.ttf']),
    ('fontconfig', ('fontconfig',), False, lambda: [ROOT, FONT_DIR]),
    ('cairo', ('cairo',), False, lambda: []),
    ('webp', ('libwebp',), False, lambda: []),
    ('jpeg', ('libjpeg',), False, lambda: []),
    ('png', ('libpng16', 'zlib'), False, lambda: []),
)


def pkg_config(*args):
    env = build_env()
    return subprocess.check_output([host_tool('pkg-config'), *args], env=env, text=True).split()


def undefined_imports(archives):
    defined, undefined = set(), set()
    for archive in archives:
        for line in subprocess.check_output(['nm', '-g', str(archive)], text=True, stderr=subprocess.DEVNULL).splitlines():
            fields = line.split()
            if len(fields) == 2 and fields[0] == 'U':
                undefined.add(fields[1])
            elif len(fields) == 3 and fields[1] != 'U':
                defined.add(fields[2])
    return sorted(undefined - defined)


def dependencies(binary):
    lines = subprocess.check_output(['otool', '-L', str(binary)], text=True).splitlines()[1:]
    return [line.strip().split(' (')[0] for line in lines if line.strip()]


def link_gate(archives, target, env, extra=()):
    """Link every archive member into a throwaway dylib against LINK_GATE_LIBRARIES only."""
    probe = CHECK_OUT / 'link-gate.c'
    probe.write_text('int shiraz_link_gate(void) { return 0; }\n')
    output = CHECK_OUT / '.link-gate.dylib'
    result = subprocess.run([tool('clang'), *target, '-dynamiclib', probe, *extra, '-o', output, '-Wl,-all_load',
                             *archives, *LINK_GATE_LIBRARIES], env=env, capture_output=True, text=True)
    output.unlink(missing_ok=True)
    return result


def require_art_deps():
    """libpng16 and ICU come from scripts/bootstrap_darwin.py deps, not from this script."""
    missing = [str(path) for path in (PREFIX / 'lib/pkgconfig/libpng16.pc', PREFIX / 'lib/libpng16.a',
                                      PREFIX / 'include/unicode/ubidi.h', PREFIX / 'lib/libicuuc.dylib')
               if not path.exists()]
    if missing:
        raise SystemExit('libpng16/ICU from scripts/bootstrap_darwin.py are missing (' + ', '.join(missing)
                         + '); run make macos-art-deps (python3 scripts/bootstrap_darwin.py deps) first')


def check():
    """Link each tests/darwin/graphics program statically against .local and run it."""
    if not STAMP.is_file():
        raise SystemExit('graphics dependencies are not built; run make macos-graphics-deps first')
    if not (FONT_DIR / 'Roboto-Regular.ttf').is_file():
        raise SystemExit(f'bundled fonts missing in {FONT_DIR}; run make fonts first')
    require_art_deps()
    CHECK_OUT.mkdir(parents=True, exist_ok=True)
    archives = [PREFIX / 'lib' / name for name in ('libfreetype.a', 'libharfbuzz.a', 'libfontconfig.a',
                                                     'libpixman-1.a', 'libcairo.a', 'libwebp.a',
                                                     'libsharpyuv.a', 'libjpeg.a')]
    imports = undefined_imports(archives)
    denied = [symbol for symbol in imports if DENIED_IMPORT.match(symbol)]
    (CHECK_OUT / 'archive-imports.txt').write_text('\n'.join(imports) + '\n')
    print(f'archive imports: {len(imports)} undefined symbols outside the graphics set; denied families: {denied}')
    if denied:
        raise SystemExit('graphics archives import denied symbol families: ' + ', '.join(denied))
    env = build_env()
    target = ['-arch', 'arm64', '-isysroot', sdk(), f'-mmacosx-version-min={MACOS_MIN}']
    gate = link_gate(archives, target, env)
    if gate.returncode:
        raise SystemExit('graphics archives import symbols outside libSystem, libz, libexpat and libc++:\n'
                         + gate.stderr[-4000:])
    # The gate must reject a leak: an extra object importing libpng fails to link.
    leak = CHECK_OUT / 'link-gate-leak.c'
    leak.write_text('void* png_create_read_struct(const char*, void*, void*, void*);\n'
                    'void* shiraz_link_gate_leak(void) { return png_create_read_struct("1.6", 0, 0, 0); }\n')
    leak_object = CHECK_OUT / 'link-gate-leak.o'
    run([tool('clang'), *target, '-c', leak, '-o', leak_object], env=env)
    if not link_gate(archives, target, env, extra=(leak_object,)).returncode:
        raise SystemExit('link gate accepted an object importing _png_create_read_struct')
    print(f"archive link gate: all members resolve against {' '.join(LINK_GATE_LIBRARIES)} and libSystem; "
          'a libpng import is rejected')
    for name, modules, cxx, arguments in CHECKS:
        source = ROOT / 'tests/darwin/graphics' / f'{name}.c'
        executable = CHECK_OUT / f'test-{name}'
        cflags = pkg_config('--cflags', *modules)
        libs = pkg_config('--static', '--libs', *modules)
        objects = CHECK_OUT / f'{name}.o'
        run([tool('clang'), *target, '-std=c11', '-Wall', '-Wextra', '-Werror', '-O1', *cflags, '-c', source,
             '-o', objects], env=env)
        fresh = executable.with_name(f'.{executable.name}.fresh')
        run([tool('clang++' if cxx else 'clang'), *target, objects, '-o', fresh, *libs], env=env)
        os.replace(fresh, executable)
        for dependency in dependencies(executable):
            if not dependency.startswith('/usr/lib/'):
                raise SystemExit(f'{executable}: unexpected dynamic dependency {dependency}')
        print(f"== test-{name}: links {' '.join(dependencies(executable))}", flush=True)
        test_env = {key: value for key, value in os.environ.items()
                    if not key.startswith(('FONTCONFIG_', 'XDG_'))}
        run([executable, *arguments()], env=test_env)
        if name == 'fontconfig':
            # Again with an empty private HOME: nothing may be written there
            # (no ~/.cache/fontconfig, ~/.fontconfig or ~/Library/Caches).
            home = CHECK_OUT / 'home'
            shutil.rmtree(home, ignore_errors=True)
            home.mkdir()
            run([executable, *arguments()], env=dict(test_env, HOME=str(home)))
            written = sorted(str(path.relative_to(home)) for path in home.rglob('*'))
            if written:
                raise SystemExit(f'fontconfig wrote into HOME: {written}')
            print('fontconfig: private HOME left empty')
            # An unloadable config makes fontconfig use its compiled-in fallback,
            # which names per-user (xdg) and /usr/local font and cache dirs. The
            # test must then fail instead of passing on host fonts, and HOME
            # must stay empty. (shiraz run validates fonts.conf before launch.)
            shutil.rmtree(home, ignore_errors=True)
            home.mkdir()
            fallback = subprocess.run([executable, *arguments()], capture_output=True, text=True,
                                      env=dict(test_env, HOME=str(home),
                                               FONTCONFIG_FILE=str(CHECK_OUT / 'missing-fonts.conf')))
            (CHECK_OUT / 'fontconfig-fallback.log').write_text(fallback.stdout + fallback.stderr)
            written = sorted(str(path.relative_to(home)) for path in home.rglob('*'))
            if fallback.returncode == 0 or written:
                raise SystemExit(f'fontconfig fallback config: rc={fallback.returncode}, HOME writes {written}; '
                                 'expected a failing test and an empty HOME')
            print(f'fontconfig: a missing FONTCONFIG_FILE fails the test (rc={fallback.returncode}), HOME left empty')
    print('graphics dependency check passed')


def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('bootstrap_darwin_graphics.py builds for macOS arm64 hosts only')
    if 'filter' not in inspect.signature(tarfile.TarFile.extractall).parameters:
        raise SystemExit(f'{sys.executable} {platform.python_version()} has no tarfile extraction filters, which '
                         "bootstrap_darwin.py's extract() needs; run this script with python3 3.12 or newer "
                         '(the macOS /usr/bin/python3 3.9 is too old)')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('stage', choices=['build', 'check'], default='build', nargs='?')
    args = parser.parse_args()
    DEPS.mkdir(exist_ok=True)
    lock = (DEPS / 'graphics-build.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'Another graphics dependency bootstrap owns the build lock. Wait for it to finish.\n')
    if args.stage == 'build':
        build_graphics()
    else:
        check()


if __name__ == '__main__':
    main()
