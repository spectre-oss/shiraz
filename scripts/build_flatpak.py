#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a sandboxed single-file Flatpak bundle (.flatpak) from an architecture-matched APK."""

import argparse
import fcntl
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
sys.path.insert(0, str(ROOT / 'src'))

from build_appimage import (  # noqa: E402
    DOWNLOADS,
    RUNTIME_LIBS,
    Libraries,
    copy_file,
    elf,
    fetch,
    notices,
    package_bionic_libc,
    remove_staging_tree,
    require_architecture,
    sha256,
    validate_report,
    validate_unwind_abi,
)
from ostree_bundle import (  # noqa: E402
    DEFAULT_RUNTIME_BRANCH,
    DEFAULT_RUNTIME_REPO,
    build_flatpak_bundle_from_tree,
    flatpak_app_id,
    flatpak_desktop_entry,
    flatpak_metadata_ini,
    flatpak_metainfo_xml,
)
from package_aot import compile_package  # noqa: E402
from package_desktop import package_desktop  # noqa: E402
from package_native import extract_native  # noqa: E402

from shiraz.apk import FormatError, inspect_apk  # noqa: E402
from shiraz.runtime import HOST, JARS, application_metadata, properties_text  # noqa: E402

# Core C/C++, GL/DRM, Wayland/X11, and audio protocol SONAMEs supplied by
# org.freedesktop.Platform//24.08 so hardware GPU/Mesa/DMA-BUF and host audio
# work directly inside Flatpak without duplicating or conflicting with glibc.
PLATFORM_PROVIDED_LIBS = frozenset(
    {
        'ld-linux-x86-64.so.2',
        'ld-linux-aarch64.so.1',
        'libc.so.6',
        'libm.so.6',
        'libdl.so.2',
        'libpthread.so.0',
        'librt.so.1',
        'libresolv.so.2',
        'libutil.so.1',
        'libnss_dns.so.2',
        'libnss_files.so.2',
        'libanl.so.1',
        'libmvec.so.1',
        'libgcc_s.so.1',
        'libstdc++.so.6',
        'libGL.so.1',
        'libGLX.so.0',
        'libGLdispatch.so.0',
        'libEGL.so.1',
        'libEGL_mesa.so.0',
        'libGLESv2.so.2',
        'libgbm.so.1',
        'libdrm.so.2',
        'libglapi.so.0',
        'libwayland-client.so.0',
        'libwayland-egl.so.1',
        'libwayland-cursor.so.0',
        'libwayland-server.so.0',
        'libxkbcommon.so.0',
        'libX11.so.6',
        'libX11-xcb.so.1',
        'libxcb.so.1',
        'libXext.so.6',
        'libXcursor.so.1',
        'libXi.so.6',
        'libXfixes.so.3',
        'libXrandr.so.2',
        'libXrender.so.1',
        'libasound.so.2',
        'libpulse.so.0',
        'libpulse-simple.so.0',
        'libpipewire-0.3.so.0',
        'libdbus-1.so.3',
    }
)


def _write_dex2oat_wrapper(path: Path, architecture: str) -> None:
    if architecture == 'aarch64':
        loader = 'ld-linux-aarch64.so.1'
        multiarch = 'aarch64-linux-gnu'
        sysloader = '/lib/ld-linux-aarch64.so.1'
    else:
        loader = 'ld-linux-x86-64.so.2'
        multiarch = 'x86_64-linux-gnu'
        sysloader = '/lib64/ld-linux-x86-64.so.2'
    path.write_text(
        '#!/bin/sh\n'
        'base=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd) || exit 1\n'
        f'if [ -f "$base/lib/{loader}" ]; then\n'
        f'    exec "$base/lib/{loader}" --library-path "$base/lib" "$base/art/bin/dex2oat" "$@"\n'
        'fi\n'
        f'exec "{sysloader}" --library-path "$base/lib:/usr/lib/{multiarch}:/lib/{multiarch}" "$base/art/bin/dex2oat" "$@"\n',
        encoding='utf-8',
    )
    path.chmod(0o755)


def build(args):
    patchelf = shutil.which('patchelf') or ROOT / '.local/bin/patchelf'
    if not Path(patchelf).is_file():
        raise RuntimeError('Packaging requires patchelf; run make packaging-tools')
    architecture = platform.machine()
    if architecture not in ('x86_64', 'aarch64'):
        raise RuntimeError('Flatpak packaging supports Linux x86_64 and aarch64 only')
    multiarch = 'aarch64-linux-gnu' if architecture == 'aarch64' else 'x86_64-linux-gnu'
    apk = args.apk.resolve()
    report = inspect_apk(apk)
    package, application, activity = validate_report(report, architecture)
    version = (ROOT / 'VERSION').read_text().strip()
    title = args.name or (
        'OsmAnd'
        if package.startswith('net.osmand')
        else package.rsplit('.', 1)[-1].replace('_', ' ').title()
    )
    app_id = args.app_id or flatpak_app_id(package)
    if (
        not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_-]*){2,}', app_id)
        or len(app_id) > 255
    ):
        raise RuntimeError('Invalid Flatpak App ID: ' + app_id)
    slug = re.sub(r'[^A-Za-z0-9._-]+', '-', title).strip('-.') or 'App'

    work = args.work_dir.resolve() if args.work_dir else ROOT / 'build/flatpak'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    cache = ROOT / '.deps/appimage-tools' / architecture
    cache.mkdir(parents=True, exist_ok=True)
    ca = fetch('mozilla-ca.pem', cache, architecture)

    stage = work / (app_id + '.stage')
    if stage.exists():
        remove_staging_tree(stage)
    files_dir = stage / 'files'
    share = files_dir / 'usr/share/shiraz'
    share.mkdir(parents=True)
    binaries = files_dir / 'usr/bin'
    binaries.mkdir(parents=True)
    app_bin = files_dir / 'bin'
    app_bin.mkdir(parents=True)

    copy_file(ROOT / 'build/shiraz-app', binaries / 'shiraz-app')
    copy_file(ROOT / 'packaging/shiraz-flatpak-run', app_bin / 'shiraz-flatpak-run')
    (app_bin / 'shiraz-flatpak-run').chmod(0o755)

    libraries = Libraries(files_dir)
    unwind_override = os.environ.get('SHIRAZ_LIBUNWIND_SO8')
    if unwind_override:
        library_path = libraries.env['LD_LIBRARY_PATH']
        try:
            libraries.env['LD_LIBRARY_PATH'] = library_path + ':/usr/lib/' + multiarch
            libraries.add(Path(unwind_override), 'libunwind.so.8')
        finally:
            libraries.env['LD_LIBRARY_PATH'] = library_path
    libraries.scan(binaries / 'shiraz-app')
    for name in RUNTIME_LIBS:
        libraries.add(ROOT / 'build' / ('lib' + name + '.so'))
    if (ROOT / 'build/libaaudio.so').is_file():
        libraries.add(ROOT / 'build/libaaudio.so')
    if (ROOT / 'build/libvulkan.so').is_file():
        libraries.add(ROOT / 'build/libvulkan.so')
    libraries.add(ROOT / 'build/libOpenSLES.so.1')
    libraries.add(ROOT / 'build/libOpenSLES.so.1', 'libOpenSLES.so')
    if (ROOT / 'build/libshiraz-video.so').is_file():
        copy_file(ROOT / 'build/libshiraz-video.so', files_dir / 'usr/lib/libshiraz-video.so')
    for folder in (HOST / 'lib64', ROOT / '.local/lib'):
        for path in sorted(folder.glob('*.so*')):
            if elf(path):
                libraries.add(path)
    validate_unwind_abi(libraries.directory)

    for name in ('dalvikvm', 'dex2oat'):
        source = HOST / 'bin' / name
        if name == 'dalvikvm' and (HOST / 'bin/dalvikvm64').is_file():
            source = HOST / 'bin/dalvikvm64'
        copy_file(source, files_dir / 'usr/art/bin' / name)
        libraries.scan(source)
    (files_dir / 'usr/art/lib64').symlink_to('../lib')
    for name in JARS:
        copy_file(HOST / 'framework' / name, files_dir / 'usr/art/framework' / name)
    _write_dex2oat_wrapper(binaries / 'dex2oat', architecture)

    for source, name in [
        (apk, 'original.apk'),
        (ROOT / 'build/shiraz-framework.jar', 'shiraz-framework.jar'),
        (ROOT / 'build/framework-res/framework-res.apk', 'framework-res.apk'),
        (ROOT / 'VERSION', 'VERSION'),
    ]:
        copy_file(source, share / name)
    if sha256(share / 'original.apk') != report['apk_sha256']:
        raise RuntimeError('Input APK changed while packaging')
    for source, name in [
        (ROOT / '.local/share/shiraz/fonts', 'fonts'),
        (ROOT / '.local/share/espeak-ng-data', 'espeak-ng-data'),
    ]:
        shutil.copytree(source, share / name, symlinks=False)
    copy_file(ROOT / 'packaging/android-fonts.xml', share / 'fonts/android-fonts.xml')
    native_payload = extract_native(
        share / 'original.apk', files_dir / 'usr/android-lib', architecture
    )
    copy_file(
        ROOT / '.local/share/bionic_translation/cfg.d/bionic_translation.cfg',
        share / 'bionic_translation/cfg.d/bionic_translation.cfg',
    )
    cfg = share / 'bionic_translation/cfg.d/bionic_translation.cfg'
    cfg_text = cfg.read_text()
    for android_name, host_name in (
        ('libEGL.so', 'libEGL.so.1'),
        ('libGLESv2.so', 'libGLESv2.so.2'),
        ('libGLESv3.so', 'libGLESv2.so.2'),
    ):
        cfg_text = re.sub(
            r'^' + re.escape(android_name) + r'\s+' + re.escape(host_name) + r'\b',
            android_name + ' libEGL_shiraz.so.1',
            cfg_text,
            flags=re.M,
        )
    cfg.write_text(cfg_text)
    copy_file(
        ROOT / 'packaging/bionic-host.cfg', share / 'bionic_translation/cfg.d/shiraz-host.cfg'
    )
    (files_dir / 'usr/lib/libandroid.so.0').symlink_to('libandroid.so')
    (share / 'fonts.conf').write_text(
        '<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">'
        '<fontconfig><dir prefix="relative">fonts</dir><cachedir prefix="xdg">fontconfig</cachedir>'
        '<alias><family>sans-serif</family><prefer><family>Noto Sans</family></prefer></alias>'
        '<alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias>'
        '<alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias>'
        '</fontconfig>\n'
    )

    classes = work / 'trust-classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(
        ['javac', '-d', str(classes), str(ROOT / 'packaging/BuildTrustStore.java')], check=True
    )
    subprocess.run(
        ['java', '-cp', str(classes), 'BuildTrustStore', str(ca), str(share / 'cacerts.jks')],
        check=True,
    )

    aot = (
        None
        if args.no_aot
        else compile_package(files_dir, work / 'aot-logs', HOST, JARS, ROOT / '.local')
    )
    facts = application_metadata(report['manifest'], share / 'original.apk', Path('/unused-data'))
    for key in ('sourceDir', 'dataDir'):
        facts.pop(key)
    (share / 'application.properties').write_text(properties_text(facts))
    (share / 'launch.properties').write_text(
        properties_text(
            dict(
                package=package,
                application=application,
                activity=activity,
                sha256=report['apk_sha256'],
                version=version,
                aot='true' if aot else 'false',
                native='true' if native_payload else 'false',
            )
        )
    )

    # Always ship our EGL shim for Android guest libraries; Platform supplies host libEGL.so.1.
    shim = files_dir / 'usr/lib/libEGL_shiraz.so.1'
    copy_file(ROOT / 'build/libEGL_shim.so', shim)
    libraries.extra_sources['usr/lib/libEGL_shiraz.so.1'] = {
        'source': 'build/libEGL_shim.so',
        'sha256': sha256(shim),
    }

    package_bionic_libc(libraries)

    # Remove core libs supplied by org.freedesktop.Platform//24.08 unless --bundle-system-libs.
    if not args.bundle_system_libs:
        for soname in PLATFORM_PROVIDED_LIBS:
            candidate = libraries.directory / soname
            if candidate.exists() or candidate.is_symlink():
                candidate.unlink()
                libraries.sources.pop(soname, None)

    notices(share, libraries)

    # Render desktop icon via BuildDesktopIcon into a temporary folder, then export into Flatpak paths.
    with tempfile.TemporaryDirectory(prefix='shiraz-flatpak-icon-', dir=work) as icon_tmp:
        icon_dir = Path(icon_tmp)
        package_desktop(apk, icon_dir, title)
        rendered_icon = icon_dir / 'shiraz-icon.png'
        for root_prefix in (files_dir / 'share', stage / 'export/share'):
            apps_dir = root_prefix / 'applications'
            icons_dir = root_prefix / 'icons/hicolor/256x256/apps'
            meta_dir = root_prefix / 'metainfo'
            apps_dir.mkdir(parents=True, exist_ok=True)
            icons_dir.mkdir(parents=True, exist_ok=True)
            meta_dir.mkdir(parents=True, exist_ok=True)
            (apps_dir / f'{app_id}.desktop').write_text(
                flatpak_desktop_entry(app_id, title), encoding='utf-8'
            )
            copy_file(rendered_icon, icons_dir / f'{app_id}.png')
            (meta_dir / f'{app_id}.metainfo.xml').write_text(
                flatpak_metainfo_xml(app_id, title, package, version), encoding='utf-8'
            )

    metadata_text = flatpak_metadata_ini(
        app_id=app_id,
        architecture=architecture,
        runtime_branch=args.runtime_branch,
        allow_network=not args.no_internet,
    )
    (stage / 'metadata').write_text(metadata_text, encoding='utf-8')

    provenance = {
        'format': 'Shiraz Flatpak v1',
        'app_id': app_id,
        'runtime': f'org.freedesktop.Platform/{architecture}/{args.runtime_branch}',
        'runtime_repo': args.runtime_repo,
        'allow_network': not args.no_internet,
        'version': version,
        'apk_sha256': report['apk_sha256'],
        'package': package,
        'apk_version': report['manifest'].get('version_name'),
        'architecture': architecture,
        'native_payload': native_payload,
        'libraries': libraries.sources,
        'public_assets': libraries.extra_sources,
        'aot': aot,
        'tools': {
            'mozilla-ca.pem': {
                'url': DOWNLOADS['mozilla-ca.pem'][0],
                'sha256': DOWNLOADS['mozilla-ca.pem'][1],
            }
        },
        'execution': (
            'standalone ART with packaged boot image and speed-filter AOT plus JIT inside Flatpak; unchanged APK'
            if aot
            else 'standalone ART with JIT inside Flatpak; unchanged APK; no AOT'
        ),
        'compatibility': 'Packaging does not resolve outstanding Android API compatibility failures.',
    }
    (share / 'bundle.json').write_text(json.dumps(provenance, indent=2) + '\n')

    for binary in files_dir.rglob('*'):
        if binary.is_file() and not binary.is_symlink() and elf(binary):
            require_architecture(binary, architecture)
            if binary.suffix not in ('.oat', '.odex') and not binary.is_relative_to(
                files_dir / 'usr/android-lib'
            ):
                binary.chmod(binary.stat().st_mode | 0o200)
                subprocess.run([str(patchelf), '--remove-rpath', str(binary)], check=True)
                subprocess.run(['strip', '--strip-unneeded', str(binary)], check=True)

    hashes = {
        str(p.relative_to(files_dir)): sha256(p)
        for p in sorted(files_dir.rglob('*'))
        if p.is_file()
    }
    (share / 'files.sha256.json').write_text(json.dumps(hashes, indent=2) + '\n')

    output = (
        args.output.resolve()
        if args.output
        else ROOT / 'dist' / f'{slug}-Shiraz-{version}-{architecture}.flatpak'
    )
    if not output.is_relative_to((ROOT / 'dist').resolve()) or output.suffix != '.flatpak':
        raise RuntimeError('Flatpak outputs must be under dist/ and end in .flatpak')
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.flatpak.tmp')

    flatpak_bin = None if args.pure_python else shutil.which('flatpak')
    if flatpak_bin:
        with tempfile.TemporaryDirectory(prefix='shiraz-flatpak-repo-', dir=work) as repo_tmp:
            repo_dir = Path(repo_tmp) / 'repo'
            subprocess.run(
                [
                    flatpak_bin,
                    'build-export',
                    f'--arch={architecture}',
                    '--no-update-summary',
                    str(repo_dir),
                    str(stage),
                    'stable',
                ],
                check=True,
            )
            bundle_cmd = [
                flatpak_bin,
                'build-bundle',
                f'--arch={architecture}',
                str(repo_dir),
                str(temporary),
                app_id,
                'stable',
            ]
            if args.runtime_repo:
                bundle_cmd.insert(3, f'--runtime-repo={args.runtime_repo}')
            subprocess.run(bundle_cmd, check=True)
    else:
        bundle_bytes = build_flatpak_bundle_from_tree(
            stage_dir=stage,
            app_id=app_id,
            architecture=architecture,
            branch='stable',
            runtime_repo=args.runtime_repo,
            compress=True,
        )
        temporary.write_bytes(bundle_bytes)

    temporary.replace(output)
    output.with_suffix('.flatpak.sha256').write_text(sha256(output) + '  ' + output.name + '\n')
    print('FLATPAK_READY ' + str(output))
    print('FLATPAK_APP_ID ' + app_id)
    print('FLATPAK_STAGE_READY ' + str(stage))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument(
        '--work-dir',
        type=Path,
        help='Override temporary staging directory (default: build/flatpak)',
    )
    parser.add_argument('--name', help='Display name; defaults to the last package segment')
    parser.add_argument('--app-id', help='Explicit reverse-DNS Flatpak App ID override')
    parser.add_argument('--runtime-branch', default=DEFAULT_RUNTIME_BRANCH)
    parser.add_argument('--runtime-repo', default=DEFAULT_RUNTIME_REPO)
    parser.add_argument(
        '--no-aot', action='store_true', help='Build a smaller JIT-only Flatpak without AOT'
    )
    parser.add_argument(
        '--no-internet', action='store_true', help='Omit network sharing from Flatpak sandbox'
    )
    parser.add_argument(
        '--bundle-system-libs',
        action='store_true',
        help='Keep host glibc/Mesa/display libs inside /app/usr/lib instead of using org.freedesktop.Platform libs',
    )
    parser.add_argument(
        '--pure-python',
        action='store_true',
        help='Emit the OSTree static-delta .flatpak bundle with scripts/ostree_bundle.py directly',
    )
    args = parser.parse_args()
    try:
        build(args)
    except (OSError, RuntimeError, FormatError, subprocess.CalledProcessError) as error:
        raise SystemExit('Flatpak build failed: ' + str(error))


if __name__ == '__main__':
    main()
