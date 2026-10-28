#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a relocatable, native-launcher AppImage from an unchanged architecture-matched APK."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys
import sysconfig
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
sys.path.insert(0, str(ROOT / 'src'))
from package_aot import compile_package
from package_desktop import package_desktop
from retain_appimages import prune
from shiraz.apk import inspect_apk, FormatError
from shiraz.runtime import application_metadata, properties_text, JARS, HOST, NAME

DOWNLOADS = {
    'appimagetool': ('https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage', 'ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0'),
    'runtime': ('https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64', '2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d'),
    'mozilla-ca.pem': ('https://curl.se/ca/cacert-2026-10-20.pem', '796f1389bfea13c0ed78f663b765baaff54a508e5483786bd6ece73c5e3e3cbe'),
}
ARM64_DOWNLOADS = {
    'appimagetool': ('https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-aarch64.AppImage', 'f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158'),
    'runtime': ('https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-aarch64', '00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444'),
}
RUNTIME_LIBS = ('jnigraphics', 'shiraz-android-fonts', 'shiraz-android-process', 'android', 'shiraz-jni', 'shiraz-host', 'shiraz-text', 'shiraz-path',
                'shiraz-image', 'shiraz-gpu', 'shiraz-gles1', 'shiraz-bitmap', 'shiraz-resources', 'shiraz-sqlite',
                'shiraz-audio', 'shiraz-capture', 'shiraz-playback', 'shiraz-speech', 'shiraz-notifications')
DISPLAY_LIBS = ('libwayland-client.so.0', 'libwayland-egl.so.1', 'libwayland-cursor.so.0',
                'libxkbcommon.so.0', 'libX11.so.6', 'libXext.so.6', 'libXcursor.so.1',
                'libXi.so.6', 'libXfixes.so.3', 'libXrandr.so.2', 'libudev.so.1',
                'libGL.so.1', 'libEGL.so.1', 'libGLESv2.so.2')


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def fetch(name, cache, architecture="x86_64"):
    url, digest = ARM64_DOWNLOADS[name] if architecture == "aarch64" and name in ARM64_DOWNLOADS else DOWNLOADS[name]
    dest = cache / name
    if not dest.exists():
        temp = dest.with_suffix('.download')
        try:
            with urllib.request.urlopen(url, timeout=60) as response, temp.open('wb') as out:
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > 64 * 1024 * 1024:
                        raise RuntimeError('Packaging download exceeds limit')
                    out.write(chunk)
            if sha256(temp) != digest:
                raise RuntimeError('Packaging download checksum mismatch: ' + name)
            temp.replace(dest)
        finally:
            temp.unlink(missing_ok=True)
    if sha256(dest) != digest:
        raise RuntimeError('Cached packaging tool checksum mismatch: ' + name)
    if name != 'mozilla-ca.pem':
        dest.chmod(0o755)
    return dest


def validate_report(report, architecture="x86_64"):
    m = report['manifest']
    minimum = str(m.get('min_sdk') or '1')
    if not minimum.isdecimal() or int(minimum) > 27:
        raise FormatError('Current Shiraz runtime requires APK minSdk <= 27')
    launchers = m.get('launcher_activities', [])
    if m.get('application_enabled') == 'false' or not launchers:
        raise FormatError('APK has no enabled launcher')
    values = [m.get('package'), m.get('application_class') or 'android.app.Application', launchers[0].get('target')]
    if not all(isinstance(v, str) and NAME.fullmatch(v) for v in values):
        raise FormatError('Invalid APK component names')
    if architecture not in ('x86_64', 'aarch64'):
        raise FormatError('Unsupported package architecture')
    abi = 'arm64-v8a' if architecture == 'aarch64' else 'x86_64'
    if report['native_abis'] and abi not in report['native_abis']:
        raise FormatError('APK has no ' + abi + '/managed runtime candidate')
    return values


def elf(path):
    try:
        with path.open('rb') as f:
            return f.read(4) == b'\x7fELF'
    except OSError:
        return False


def require_architecture(path, architecture):
    expected = {'x86_64': 62, 'aarch64': 183}[architecture]
    with Path(path).open('rb') as source:
        header = source.read(64)
    if len(header) < 64 or header[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', header, 18)[0] != expected:
        raise RuntimeError('Package ELF architecture mismatch: ' + str(path))


def writable_staging_tree(appdir):
    if appdir.is_symlink():
        raise RuntimeError('AppDir staging path must not be a symlink')
    # Nix data directories copied into this generated tree retain mode 0555.
    # Restore owner access only inside staging; never follow directory symlinks.
    for directory, _, _ in os.walk(appdir, followlinks=False):
        path = Path(directory)
        path.chmod(path.stat().st_mode | 0o700)


def remove_staging_tree(appdir):
    writable_staging_tree(appdir)
    shutil.rmtree(appdir)


class Libraries:
    """Resolve trusted build artifacts only; never run ldd on APK-supplied libraries."""
    def __init__(self, appdir):
        self.appdir = appdir
        self.directory = appdir / 'usr/lib'
        self.directory.mkdir(parents=True)
        self.sources = {}
        self.extra_sources = {}
        self.scanned = set()
        self.by_digest = {}
        roots = [str(ROOT/'build'), str(HOST/'lib64'), str(ROOT/'.local/lib')]
        roots += [p for p in os.environ.get('SHIRAZ_SYSTEM_LIBRARY_PATH', '').split(':') if p]
        self.env = dict(os.environ, LD_LIBRARY_PATH=':'.join(roots))

    def add(self, source, name=None):
        source = Path(source)
        name = name or source.name
        if '/' in name or name in ('.', '..'):
            raise RuntimeError('Invalid library basename')
        digest = sha256(source)
        if name in self.sources:
            if self.sources[name]['sha256'] != digest:
                raise RuntimeError('Conflicting library: ' + name)
            return
        real = source.resolve()
        # Store only a public package/build-relative provenance label, never machine paths.
        label = str(real.relative_to(ROOT)) if real.is_relative_to(ROOT) else str(real)
        self.sources[name] = dict(source=label, sha256=digest)
        dest = self.directory / name
        if digest in self.by_digest:
            dest.symlink_to(self.by_digest[digest])
        else:
            shutil.copy2(real, dest)
            self.by_digest[digest] = name
        self.scan(real)

    def scan(self, source):
        source = Path(source).resolve()
        if source in self.scanned:
            return
        self.scanned.add(source)
        result = subprocess.run(['ldd', str(source)], env=self.env, capture_output=True, text=True, check=True)
        for line in result.stdout.splitlines():
            if 'not found' in line:
                raise RuntimeError('Unresolved ELF dependency: ' + line.strip())
            match = re.match(r'\s*(\S+) => (/\S+) \(', line)
            if match:
                name = match[1]
                if name.startswith('/') and Path(name).name in ('ld-linux-x86-64.so.2', 'ld-linux-aarch64.so.1'):
                    name = Path(name).name
                # One ELF namespace reuses an already loaded SONAME. A later
                # transitive reference must not introduce a different variant.
                # Explicit roots still go through add()'s conflict check.
                if name in self.sources:
                    continue
                # Android lookup aliases in build/ point at our EGL bridge.
                # Host rendering must retain the real OS driver underneath it.
                if name in ('libEGL.so.1', 'libGLESv2.so.2'):
                    self.system(name)
                    continue
                # Old DT_RPATH entries in ART/Nix builds can outrank the
                # runtime's library path. Package our selected build libraries
                # consistently; copied ELF RPATHs are removed before delivery.
                selected = Path(match[2])
                for directory in self.env['LD_LIBRARY_PATH'].split(':'):
                    candidate = Path(directory) / name
                    if elf(candidate):
                        selected = candidate
                        break
                self.add(selected, name)
                continue
            match = re.match(r'\s*(/\S+) \(', line)
            if match:
                self.add(Path(match[1]))

    def system(self, name):
        # Nix build shells provide explicit public library roots instead of ldconfig.
        for directory in os.environ.get('SHIRAZ_SYSTEM_LIBRARY_PATH', '').split(':'):
            if directory and (Path(directory) / name).is_file():
                self.add(Path(directory) / name, name)
                return
        # ldconfig reads the system cache, not an application's executable code.
        if shutil.which('ldconfig'):
            listing = subprocess.check_output(['ldconfig', '-p'], text=True)
            marker = 'AArch64' if platform.machine() == 'aarch64' else 'x86-64'
            for line in listing.splitlines():
                if line.strip().startswith(name + ' ') and marker in line:
                    self.add(Path(line.split('=>', 1)[1].strip()), name)
                    return
        raise RuntimeError('Missing desktop runtime dependency: ' + name)


def copy_file(source, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, dest)


def validate_unwind_abi(directory):
    """Catch an older bundled libunwind before shipping an AppImage."""
    bridge = directory / 'libdl_bio.so.0'
    unwind = directory / 'libunwind.so.8'
    if not bridge.is_file() or not unwind.is_file():
        raise RuntimeError('Packaged bionic bridge or libunwind is missing')

    def symbols(path):
        output = subprocess.check_output(['readelf', '-W', '--dyn-syms', str(path)], text=True)
        found = {}
        for line in output.splitlines():
            parts = line.split()
            if len(parts) >= 8 and parts[7].startswith('_UL'):
                found[parts[7].split('@', 1)[0]] = parts[6]
        return found

    needed = {name for name, section in symbols(bridge).items() if section == 'UND'}
    exported = {name for name, section in symbols(unwind).items() if section != 'UND'}
    missing = needed - exported
    if missing:
        raise RuntimeError('Packaged libunwind lacks bionic bridge symbols: ' + ', '.join(sorted(missing)))


def notices(share, libraries):
    dest = share/'licenses'
    shutil.copytree(ROOT/'LICENSES', dest)
    copy_file(ROOT/'LICENSE', dest/'Shiraz-GPL-3.0.txt')
    shutil.copytree(ROOT/'third_party/opensles', dest/'opensles-headers')
    candidates = ['context/art_standalone/libcore/LICENSE', 'context/osmand/LICENSE', 'context/osmand/LICENSE.md']
    candidates += [str(p.relative_to(ROOT)) for p in (ROOT/'.deps').glob('*/COPYING*') if p.is_file()]
    candidates += [str(p.relative_to(ROOT)) for p in (ROOT/'.deps').glob('*/LICENSE*') if p.is_file()]
    for name in candidates:
        p = ROOT/name
        if p.is_file():
            copy_file(p, dest/'sources'/name.replace('/', '_'))
    packages = {}
    if shutil.which('pacman'):
        records = list(libraries.sources.values()) + list(libraries.extra_sources.values())
        paths = sorted({r['source'] for r in records if r['source'].startswith('/usr/')})
        owners = set()
        for offset in range(0, len(paths), 128):
            q = subprocess.run(['pacman', '-Qoq', *paths[offset:offset+128]], capture_output=True, text=True)
            owners.update(q.stdout.splitlines())
        for package in sorted(owners):
            info = subprocess.run(['pacman', '-Qi', package], capture_output=True, text=True)
            packages[package] = {key: value.strip() for key, value in re.findall(
                r'^(Name|Version|URL|Licenses)\s*:\s*(.*)$', info.stdout, re.MULTILINE)}
            folder = Path('/usr/share/licenses')/package
            if folder.is_dir():
                shutil.copytree(folder, dest/'system'/package, symlinks=False)
    playback_notices=ROOT/'.local/share/shiraz/playback-codec'
    if playback_notices.is_dir():shutil.copytree(playback_notices,dest/'playback-codec',symlinks=False)
    capture_notices=ROOT/'.local/share/shiraz/capture-codec'
    if capture_notices.is_dir():shutil.copytree(capture_notices,dest/'capture-codec',symlinks=False)
    (dest/'system-packages.json').write_text(json.dumps(packages, indent=2)+'\n')
    (dest/'README.txt').write_text('This bundle combines Shiraz GPL-3.0-only with independently licensed components.\n'
        'The original APK is unchanged and retains its own license and notices.\n'
        'Runtime sources: https://gitlab.com/android_translation_layer/art_standalone and the AOSP sources referenced in AOSP-NOTICE.txt.\n'
        'OsmAnd sources: https://github.com/osmandapp/OsmAnd\n'
        'System package versions are recorded in system-packages.json; build with the matching distribution source packages.\n'
        'Packaging tools: AppImage appimagetool 1.9.1 and type2-runtime 20251108 (https://github.com/AppImage).\n'
        'Public CA roots: https://curl.se/docs/caextract.html (Mozilla certificate data).\n'
        'No private app data, host trust stores, credentials or local maps are included.\n')


from package_native import extract_native

def build(args):
    patchelf = shutil.which('patchelf') or ROOT/'.local/bin/patchelf'
    if not Path(patchelf).is_file():
        raise RuntimeError('Packaging requires patchelf; run make packaging-tools')
    architecture = platform.machine()
    if architecture not in ('x86_64', 'aarch64'):
        raise RuntimeError('Packaging supports Linux x86_64 and aarch64 only')
    loader = 'ld-linux-aarch64.so.1' if architecture == 'aarch64' else 'ld-linux-x86-64.so.2'
    apk = args.apk.resolve()
    report = inspect_apk(apk)
    package, application, activity = validate_report(report, architecture)
    version = (ROOT/'VERSION').read_text().strip()
    title = args.name or ('OsmAnd' if package.startswith('net.osmand') else package.rsplit('.', 1)[-1].replace('_', ' ').title())
    slug = re.sub(r'[^A-Za-z0-9._-]+', '-', title).strip('-.') or 'App'
    work = ROOT/'build/appimage'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work/'.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    cache = ROOT/'.deps/appimage-tools'/architecture
    cache.mkdir(parents=True, exist_ok=True)
    tool, runtime, ca = (fetch(name, cache, architecture) for name in DOWNLOADS)
    appdir = work/(package+'.AppDir')
    # Only remove our fixed generated staging tree, never user-supplied output paths.
    if appdir.exists():
        remove_staging_tree(appdir)
    share = appdir/'usr/share/shiraz'
    share.mkdir(parents=True)
    binaries = appdir/'usr/bin'
    binaries.mkdir(parents=True)
    copy_file(ROOT/'build/shiraz-app', binaries/'shiraz-app')
    copy_file(ROOT/'packaging/AppRun', appdir/'AppRun')
    (appdir/'AppRun').chmod(0o755)
    libraries = Libraries(appdir)
    multiarch = 'aarch64-linux-gnu' if architecture == 'aarch64' else 'x86_64-linux-gnu'
    # The bridge is compiled with the flake's libunwind headers. Select the
    # matching shared library before transitive scanning can pick Ubuntu's
    # older libunwind with the same SONAME.
    unwind_override = os.environ.get('SHIRAZ_LIBUNWIND_SO8')
    if unwind_override:
        # The Nix libunwind RUNPATH points at Nix glibc. Use Ubuntu only while
        # scanning this library; other bundled Nix tools need their own ABI.
        library_path = libraries.env['LD_LIBRARY_PATH']
        try:
            libraries.env['LD_LIBRARY_PATH'] = library_path + ':/usr/lib/' + multiarch
            libraries.add(Path(unwind_override), 'libunwind.so.8')
        finally:
            libraries.env['LD_LIBRARY_PATH'] = library_path
    libraries.scan(binaries/'shiraz-app')
    for name in RUNTIME_LIBS:
        libraries.add(ROOT/'build'/('lib'+name+'.so'))
    libraries.add(ROOT/"build/libOpenSLES.so.1")
    # Keep the optional GStreamer bridge separate; decoder plugins are supplied
    # by the host, never extracted from arbitrary APKs or bundled here.
    if (ROOT/'build/libshiraz-video.so').is_file():
        copy_file(ROOT/'build/libshiraz-video.so', appdir/'usr/lib/libshiraz-video.so')
    for folder in (HOST/'lib64', ROOT/'.local/lib'):
        for path in sorted(folder.glob('*.so*')):
            if elf(path):
                libraries.add(path)
    for name in DISPLAY_LIBS:
        libraries.system(name)
    # GLVND loads its EGL vendor and Mesa loads its DRI driver with dlopen(),
    # so ldd on libEGL.so.1 cannot discover either dependency. Without these,
    # a Wayland window can be created but SDL cannot create a renderer.
    if (appdir/'usr/lib/libEGL.so.1').is_file():
        libraries.system('libEGL_mesa.so.0')
        dri_roots = (Path('/usr/lib')/multiarch/'dri', Path('/lib')/multiarch/'dri',
                     Path('/usr/lib/dri'), Path('/lib/dri'))
        swrast = next((root/'swrast_dri.so' for root in dri_roots if (root/'swrast_dri.so').is_file()), None)
        if swrast is None:
            raise RuntimeError('Missing Mesa swrast_dri.so for portable Wayland rendering')
        libraries.add(swrast, 'swrast_dri.so')
        vendor = appdir/'usr/share/glvnd/egl_vendor.d/50_mesa.json'
        vendor.parent.mkdir(parents=True, exist_ok=True)
        vendor.write_text('{"file_format_version":"1.0.0","ICD":{"library_path":"libEGL_mesa.so.0"}}\n')
    validate_unwind_abi(libraries.directory)
    for name in ('libnss_dns.so.2', 'libnss_files.so.2'):
        if (Path('/usr/lib')/name).is_file():
            libraries.add(Path('/usr/lib')/name)
    for name in ('dalvikvm', 'dex2oat'):
        source = HOST/'bin'/name
        if name == 'dalvikvm' and (HOST/'bin/dalvikvm64').is_file():
            source = HOST/'bin/dalvikvm64'
        copy_file(source, appdir/'usr/art/bin'/name)
        libraries.scan(source)
    # ART expects lib64 below ANDROID_ROOT as well as java.library.path.
    (appdir/'usr/art/lib64').symlink_to('../lib')
    for name in JARS:
        copy_file(HOST/'framework'/name, appdir/'usr/art/framework'/name)
    (binaries/'dex2oat').write_text('#!/bin/sh\nbase=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd) || exit 1\nexec "$base/lib/ld-linux-x86-64.so.2" --library-path "$base/lib" "$base/art/bin/dex2oat" "$@"\n'.replace('ld-linux-x86-64.so.2', loader))
    (binaries/'dex2oat').chmod(0o755)
    for source, name in [(apk, 'original.apk'), (ROOT/'build/shiraz-framework.jar', 'shiraz-framework.jar'),
                         (ROOT/'build/framework-res/framework-res.apk', 'framework-res.apk'), (ROOT/'VERSION', 'VERSION')]:
        copy_file(source, share/name)
    if sha256(share/'original.apk') != report['apk_sha256']:
        raise RuntimeError('Input APK changed while packaging')
    for source, name in [(ROOT/'.local/share/shiraz/fonts', 'fonts'), (ROOT/'.local/share/espeak-ng-data', 'espeak-ng-data')]:
        shutil.copytree(source, share/name, symlinks=False)
    copy_file(ROOT/'packaging/android-fonts.xml', share/'fonts/android-fonts.xml')
    native_payload = extract_native(share/'original.apk', appdir/'usr/android-lib', architecture)
    copy_file(ROOT/'.local/share/bionic_translation/cfg.d/bionic_translation.cfg', share/'bionic_translation/cfg.d/bionic_translation.cfg')
    cfg = share/'bionic_translation/cfg.d/bionic_translation.cfg'
    cfg_text = cfg.read_text()
    for android_name, host_name in (('libEGL.so', 'libEGL.so.1'), ('libGLESv2.so', 'libGLESv2.so.2'), ('libGLESv3.so', 'libGLESv2.so.2')):
        cfg_text = re.sub(r'^' + re.escape(android_name) + r'\s+' + re.escape(host_name) + r'\b', android_name + ' libEGL_shiraz.so.1', cfg_text, flags=re.M)
    cfg.write_text(cfg_text)
    copy_file(ROOT/'packaging/bionic-host.cfg', share/'bionic_translation/cfg.d/shiraz-host.cfg')
    (appdir/'usr/lib/libandroid.so.0').symlink_to('libandroid.so')
    (share/'fonts.conf').write_text('<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd"><fontconfig><dir prefix="relative">fonts</dir><cachedir prefix="xdg">fontconfig</cachedir><alias><family>sans-serif</family><prefer><family>Noto Sans</family></prefer></alias><alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias><alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias></fontconfig>\n')
    # ALSA/PipeWire plugins are dlopened and are not in the normal ELF closure.
    for name in ('alsa-lib', 'pipewire-0.3', 'spa-0.2'):
        roots = [Path(p) for p in os.environ.get('SHIRAZ_SYSTEM_LIBRARY_PATH', '').split(':') if p] + [Path('/usr/lib')]
        folder = next((root / name for root in roots if (root / name).is_dir()), Path('/usr/lib') / name)
        if folder.is_dir():
            for path in folder.rglob('*.so'):
                target = appdir/'usr/lib'/name/path.relative_to(folder)
                copy_file(path, target)
                libraries.extra_sources[str(target.relative_to(appdir))] = {'source': str(path.resolve()), 'sha256': sha256(path)}
                libraries.scan(path)
    for name in ('alsa', 'pipewire', 'X11/xkb'):
        roots = [Path(p) for p in os.environ.get('SHIRAZ_SYSTEM_DATA_PATH', '').split(':') if p] + [Path('/usr/share')]
        folder = next((root / name for root in roots if (root / name).is_dir()), Path('/usr/share') / name)
        if folder.is_dir():
            shutil.copytree(folder, appdir/'usr/share'/name, symlinks=False)
            for path in folder.rglob('*'):
                if path.is_file():
                    target = appdir/'usr/share'/name/path.relative_to(folder)
                    libraries.extra_sources[str(target.relative_to(appdir))] = {'source': str(path.resolve()), 'sha256': sha256(path)}
    # Host /etc/alsa symlinks are not portable inside the package.
    alsa_config = appdir/'usr/share/alsa/alsa.conf'
    pipewire_config = appdir/'usr/share/alsa/alsa.conf.d/50-pipewire.conf'
    if alsa_config.is_file() and pipewire_config.is_file():
        with alsa_config.open('a') as stream:
            stream.write('\n' + pipewire_config.read_text())
    classes = work/'trust-classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '-d', str(classes), str(ROOT/'packaging/BuildTrustStore.java')], check=True)
    subprocess.run(['java', '-cp', str(classes), 'BuildTrustStore', str(ca), str(share/'cacerts.jks')], check=True)
    aot = None if args.no_aot else compile_package(appdir, work/'aot-logs', HOST, JARS, ROOT/'.local')
    facts = application_metadata(report['manifest'], Path('/unused.apk'), Path('/unused-data'))
    for key in ('sourceDir', 'dataDir'):
        facts.pop(key)
    (share/'application.properties').write_text(properties_text(facts))
    (share/'launch.properties').write_text(properties_text(dict(package=package, application=application, activity=activity,
                                                               sha256=report['apk_sha256'], version=version, aot='true' if aot else 'false', native='true' if native_payload else 'false')))
    package_desktop(apk, appdir, title)
    egl = appdir/'usr/lib/libEGL.so.1'
    mesa_host = appdir/'usr/lib/libEGL_mesa_host.so.1'
    shim = appdir/'usr/lib/libEGL_shiraz.so.1'
    if egl.exists():
        shutil.copy2(egl.resolve(), mesa_host)
        copy_file(ROOT/'build/libEGL_shim.so', shim)
        libraries.extra_sources['usr/lib/libEGL_shiraz.so.1'] = {'source': 'build/libEGL_shim.so', 'sha256': sha256(shim)}
        libraries.extra_sources['usr/lib/libEGL_mesa_host.so.1'] = {'source': 'host libEGL.so.1', 'sha256': sha256(mesa_host)}
    # Ship the temporary installer with its own Python and conversion tools.
    installer = share/'installer'
    installer.mkdir()
    copy_file(Path(sys.executable).resolve(), installer/'python')
    libraries.scan(installer/'python')
    stdlib = Path(sysconfig.get_path('stdlib'))
    pythonlib = installer/'lib'/stdlib.name
    shutil.copytree(stdlib, pythonlib, ignore=shutil.ignore_patterns(
        'site-packages', '__pycache__', 'config-*', '*.a', '*.o', 'test', 'tests', 'tkinter', 'idlelib', 'turtledemo', 'ensurepip'))
    writable_staging_tree(pythonlib)
    for extension in pythonlib.rglob('*.so'):
        if extension.name.startswith('_tkinter'):
            extension.unlink()
        else:
            libraries.scan(extension)
    shutil.copytree(ROOT/'src/shiraz', installer/'shiraz', ignore=shutil.ignore_patterns('__pycache__'))
    copy_file(ROOT/'scripts/package_native.py', installer/'package_native.py')
    copy_file(ROOT/'packaging/shiraz-app.svg', installer/'fallback-icon.svg')
    copy_file(tool, installer/'appimagetool')
    copy_file(runtime, installer/'runtime')
    notices(share, libraries)
    provenance = {'format': 'Shiraz AppImage v1', 'version': version, 'apk_sha256': report['apk_sha256'],
                  'package': package, 'apk_version': report['manifest'].get('version_name'), 'architecture': architecture,
                  'tools': {k: {'url': v[0], 'sha256': v[1]} for k,v in
                            (DOWNLOADS | (ARM64_DOWNLOADS if architecture == 'aarch64' else {})).items()},
                  'native_payload': native_payload, 'libraries': libraries.sources, 'public_assets': libraries.extra_sources, 'aot': aot,
                  'execution': ('standalone ART with packaged boot image and speed-filter AOT plus JIT; unchanged APK; no tree-shaking'
                                if aot else 'standalone ART with JIT, unchanged APK; no AOT/tree-shaking'),
                  'host_requirements': ['Linux ' + architecture + ' kernel', 'X11 or Wayland desktop for visible UI', 'host audio service/device for sound'],
                  'compatibility': 'Packaging does not resolve outstanding Android API compatibility failures.'}
    (share/'bundle.json').write_text(json.dumps(provenance, indent=2)+'\n')
    # Strip generated native-library copies only. ART owns the generated OAT ELF
    # layout/checksums, so preserve its compiler output byte-for-byte.
    for binary in appdir.rglob('*'):
        if binary.is_file() and elf(binary):
            require_architecture(binary, architecture)
        if binary.is_file() and not binary.is_symlink() and binary.suffix not in ('.oat', '.odex') and not binary.is_relative_to(appdir/'usr/android-lib') and binary not in (installer/'appimagetool', installer/'runtime') and elf(binary):
            # Copies from immutable package stores may be read-only. Keep all
            # trusted native dependencies relocatable under the bundled loader.
            binary.chmod(binary.stat().st_mode | 0o200)
            subprocess.run([str(patchelf), '--remove-rpath', str(binary)], check=True)
            subprocess.run(['strip', '--strip-unneeded', str(binary)], check=True)
    hashes = {str(p.relative_to(appdir)): sha256(p) for p in sorted(appdir.rglob('*')) if p.is_file()}
    (share/'files.sha256.json').write_text(json.dumps(hashes, indent=2)+'\n')
    output = args.output.resolve() if args.output else ROOT/'dist'/(slug+'-Shiraz-'+version+'-'+architecture+'.AppImage')
    if not output.is_relative_to((ROOT/'dist').resolve()) or output.suffix != '.AppImage':
        raise RuntimeError('AppImage outputs must be under dist/ and end in .AppImage')
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.AppImage.tmp')
    env = dict(os.environ, ARCH=architecture, APPIMAGE_EXTRACT_AND_RUN='1', VERSION=version)
    subprocess.run([str(tool), '--runtime-file', str(runtime), str(appdir), str(temporary)], env=env, check=True)
    temporary.chmod(0o755)
    temporary.replace(output)
    output.with_suffix('.AppImage.sha256').write_text(sha256(output)+'  '+output.name+'\n')
    removed, reclaimed = prune(ROOT/'dist', apply=True)
    print(f'RELEASE_RETENTION removed={len(removed)} reclaimed_bytes={reclaimed} keep=3')
    print('APPIMAGE_READY '+str(output))
    print('APPDIR_READY '+str(appdir))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--name', help='Display name; defaults to the last package segment')
    parser.add_argument('--no-aot', action='store_true', help='Build a smaller JIT-only package without precompiled ART artifacts')
    args = parser.parse_args()
    try:
        build(args)
    except (OSError, RuntimeError, FormatError, subprocess.CalledProcessError) as error:
        raise SystemExit('AppImage build failed: '+str(error))

if __name__ == '__main__':
    main()
