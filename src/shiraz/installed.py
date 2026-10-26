# SPDX-License-Identifier: GPL-3.0-only
"""Temporary local APK-to-AppImage installation backend; no Android package daemon."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import platform
import re
import uuid
import shutil
import subprocess
import tempfile
import time
from .apk import inspect_apk, FormatError
from .runtime import application_metadata, properties_text, NAME
from .architecture import supports_native_abis


def xdg(name, fallback):
    value = os.environ.get('SHIRAZ_HOST_' + name, os.environ.get(name, ''))
    if value and Path(value).is_absolute():
        return Path(value)
    home = os.environ.get('HOME', '')
    if not home or not Path(home).is_absolute():
        raise ValueError('An absolute HOME or XDG base directory is required')
    return Path(home) / fallback


def roots():
    return (xdg('XDG_DATA_HOME', '.local/share') / 'shiraz/installed',
            xdg('XDG_CACHE_HOME', '.cache') / 'shiraz/install',
            xdg('XDG_STATE_HOME', '.local/state') / 'shiraz')


def atomic(path, content):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def audit(state, event, **fields):
    with (state / 'install-history.jsonl').open('a') as out:
        fcntl.flock(out, fcntl.LOCK_EX)
        out.write(json.dumps(dict(time=time.time(), event=event, **fields)) + '\n')
        out.flush()
        os.fsync(out.fileno())


def install(template, apk, expected, installer, result):
    from package_native import extract_native
    os.umask(0o077)
    data, cache, state = roots()
    for directory in (data, cache, state):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (data / '.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        audit(state, 'requested', installer=installer, expected=expected)
        try:
            with tempfile.TemporaryDirectory(prefix='session-', dir=cache) as temporary:
                work = Path(temporary)
                # Freeze the input before parsing: caller writes cannot race validation/copying.
                staged_apk = work / 'input.apk'
                if not apk.is_file() or apk.stat().st_size > 1024 * 1024 * 1024:
                    raise FormatError('APK size or file type is unsupported')
                with apk.open('rb') as source, staged_apk.open('wb') as dest:
                    remaining = 1024 * 1024 * 1024
                    while chunk := source.read(min(1024 * 1024, remaining + 1)):
                        remaining -= len(chunk)
                        if remaining < 0:
                            raise FormatError('APK size limit')
                        dest.write(chunk)
                report = inspect_apk(staged_apk)
                manifest = report['manifest']
                package = manifest['package']
                launchers = manifest.get('launcher_activities', [])
                app = manifest.get('application_class') or 'android.app.Application'
                activity = launchers[0]['target'] if launchers else ''
                if not all(isinstance(v, str) and NAME.fullmatch(v) for v in (package, app, activity)):
                    raise FormatError('APK must declare a valid launcher')
                if expected and package != expected:
                    raise FormatError('APK package differs from installation request')
                if int(manifest.get('min_sdk') or '1') > 27 or not supports_native_abis(report['native_abis']):
                    raise FormatError('APK requires an unsupported Android API or CPU architecture')
                if manifest.get('application_enabled') == 'false':
                    raise FormatError('APK application is disabled')
                destination = data / package
                if destination.exists():
                    # Updates need certificate lineage verification before replacing an installed APK.
                    raise FormatError('This temporary installer supports new installations only')
                if shutil.disk_usage(data).free < 512 * 1024 * 1024:
                    raise OSError('Not enough space to store an AppImage')
                appdir = work / 'AppDir'
                def ignore(folder, names):
                    relative = Path(folder).relative_to(template).as_posix()
                    if relative == 'usr/share/shiraz':
                        return {'original.apk', 'oat', 'bundle.json', 'files.sha256.json'}
                    if relative == 'usr':
                        return {'android-lib'}
                    if relative == '.':
                        return {name for name in names if name.endswith('.desktop')}
                    return set()
                shutil.copytree(template, appdir, symlinks=True, ignore=ignore)
                share = appdir / 'usr/share/shiraz'
                shutil.copy2(staged_apk, share / 'original.apk')
                native = extract_native(staged_apk, appdir / 'usr/android-lib', platform.machine())
                version = (share / 'VERSION').read_text().strip()
                facts = application_metadata(manifest, Path('/unused.apk'), Path('/unused-data'))
                facts.pop('sourceDir', None)
                facts.pop('dataDir', None)
                (share / 'application.properties').write_text(properties_text(facts))
                (share / 'launch.properties').write_text(properties_text(dict(package=package,
                    application=app, activity=activity, sha256=report['apk_sha256'],
                    version=version, aot='false', native='true' if native else 'false')))
                shutil.copy2(template / 'usr/share/shiraz/installer/fallback-icon.svg', appdir / 'shiraz-app.svg')
                (appdir / '.DirIcon').unlink(missing_ok=True)
                (appdir / '.DirIcon').symlink_to('shiraz-app.svg')
                (appdir / 'shiraz-app.desktop').write_text(
                    '[Desktop Entry]\nType=Application\nName=' + package +
                    ' (Shiraz)\nExec=AppRun\nIcon=shiraz-app\nCategories=Utility;\nTerminal=false\n')
                (share / 'bundle.json').write_text(json.dumps(dict(format='Shiraz installed AppImage v1',
                    package=package, apk_sha256=report['apk_sha256'], version=version, installer=installer)))
                output = work / 'application.AppImage'
                tools = template / 'usr/share/shiraz/installer'
                env = dict(os.environ, APPIMAGE_EXTRACT_AND_RUN='1', ARCH=platform.machine(),
                           VERSION=version, TMPDIR=str(work))
                for key in ('LD_LIBRARY_PATH', 'LD_PRELOAD', 'PYTHONHOME', 'PYTHONPATH'):
                    env.pop(key, None)
                subprocess.run([str(tools / 'appimagetool'), '--runtime-file', str(tools / 'runtime'),
                                str(appdir), str(output)], env=env, check=True, timeout=600)
                output.chmod(0o700)
                # Publish record and executable together; failed builds never appear installed.
                publish = Path(tempfile.mkdtemp(prefix='.publish-', dir=data))
                try:
                    shutil.copy2(output, publish / 'application.AppImage')
                    shutil.copy2(staged_apk, publish / 'original.apk')
                    now = int(time.time() * 1000)
                    facts.update(sourceDir=str(destination / 'original.apk'),
                        dataDir=str(xdg('XDG_DATA_HOME', '.local/share') / 'shiraz-apps' / package),
                        firstInstallTime=now, lastUpdateTime=now, installer=installer,
                        launchActivity=activity, appImage=str(destination / 'application.AppImage'))
                    (publish / 'package.properties').write_text(properties_text(facts))
                    publish.rename(destination)
                finally:
                    if publish.exists():
                        shutil.rmtree(publish)
                audit(state, 'installed', package=package, sha256=report['apk_sha256'], installer=installer)
                atomic(result, properties_text(dict(package=package)).encode())
        except Exception as error:
            audit(state, 'failed', installer=installer, expected=expected, reason=str(error))
            raise


def uninstall(package, installer):
    if not re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*', package) or len(package) > 1024:
        raise ValueError('Invalid installed package')
    data, cache, state = roots()
    for directory in (data, cache, state):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (data / '.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        target = data / package
        if target.is_symlink() or not target.is_dir():
            raise ValueError('Package is not installed')
        names = {entry.name for entry in target.iterdir()}
        if names != {'package.properties', 'original.apk', 'application.AppImage'} or any(entry.is_symlink() or not entry.is_file() for entry in target.iterdir()):
            raise ValueError('Unexpected installed package contents')
        audit(state, 'uninstall-requested', package=package, installer=installer)
        # Hide the complete record atomically; never touch the separate app-data directory.
        removed = data / ('.removed-' + uuid.uuid4().hex)
        target.rename(removed)
        shutil.rmtree(removed)
        audit(state, 'uninstalled', package=package, installer=installer, app_data_retained=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path)
    parser.add_argument('--uninstall')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--expected', default='')
    parser.add_argument('--installer', required=True)
    parser.add_argument('--result', type=Path)
    args = parser.parse_args()
    if args.uninstall:
        uninstall(args.uninstall, args.installer)
    else:
        if args.template is None or args.apk is None or args.result is None:
            parser.error("installation requires --template, --apk and --result")
        install(args.template.resolve(), args.apk.resolve(), args.expected, args.installer, args.result)


if __name__ == '__main__':
    main()
