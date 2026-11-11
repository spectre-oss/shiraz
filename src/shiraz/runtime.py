# SPDX-License-Identifier: GPL-3.0-only
"""Experimental activity startup using standalone host ART and the local framework."""
import fcntl
import hashlib
import json
import os
import platform
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import xml.etree.ElementTree as ElementTree
import zipfile
from pathlib import Path

from .apk import FormatError, inspect_apk
from .architecture import native_abi, supports_native_abis
from .host_layout import darwin_socket_directory, host_out_dir
from .native_payload import extract_native


def native_build_environment(profile_values):
    """Publish the same honest default build identity as android.os.Build."""
    version = (ROOT / 'VERSION').read_text(encoding='ascii').strip()
    defaults = dict(model='Shiraz Linux', manufacturer='Shiraz', brand='Shiraz',
                    device='linux', product='shiraz', board='linux',
                    hardware=os.uname().machine, bootloader='unknown', serial='unknown',
                    host='shiraz', user='shiraz', id='shiraz', type='host', tags='unsigned',
                    incremental=version, securityPatch='unknown', release='8.1',
                    display='Shiraz ' + version)
    values = {key: profile_values.get('build.' + key, value) for key, value in defaults.items()}
    variables = {'display': 'DISPLAY_ID', 'securityPatch': 'SECURITY_PATCH'}
    env = {'SHIRAZ_ANDROID_' + variables.get(key, key.upper()): value
           for key, value in values.items()}
    env['SHIRAZ_ANDROID_FINGERPRINT'] = (
        '/'.join(values[k] for k in ('brand', 'product', 'device')) + ':' + values['release']
        + '/' + values['id'] + '/' + values['incremental'] + ':' + values['type']
        + '/' + values['tags'])
    return env


def run_with_live_logs(command, cwd, env, work, timeout):
    """Keep the diagnostic files and forward ART output to the parent process."""
    with (work / 'stdout.log').open('wb') as stdout_log, (work / 'stderr.log').open('wb') as stderr_log:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        def pump(pipe, log_file, destination):
            for chunk in iter(lambda: pipe.read1(8192), b''):
                log_file.write(chunk)
                log_file.flush()
                try:
                    if hasattr(destination, 'buffer'):
                        destination.buffer.write(chunk)
                        destination.buffer.flush()
                    else:
                        destination.write(chunk.decode('utf-8', errors='replace'))
                        destination.flush()
                except (BrokenPipeError, OSError):
                    pass
            pipe.close()

        threads = [threading.Thread(target=pump, args=(process.stdout, stdout_log, sys.stdout), daemon=True),
                   threading.Thread(target=pump, args=(process.stderr, stderr_log, sys.stderr), daemon=True)]
        for thread in threads:
            thread.start()
        try:
            returncode = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise
        finally:
            for thread in threads:
                thread.join()
        return subprocess.CompletedProcess(command, returncode)

ROOT = Path(__file__).resolve().parents[2]
HOST = host_out_dir(ROOT / 'context/art_standalone')
JARS = ('core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'okhttp-hostdex.jar', 'bouncycastle-hostdex.jar', 'wolfssljni-hostdex.jar')
# Both hosts use the same pinned HTTP/crypto/TLS boot modules.
DARWIN_JARS = JARS
DARWIN_LIBRARIES = 'build/macos'
# Without one of these the first frame cannot be produced.
# libshiraz-text and the bundled fonts are required as on Linux: text.cpp adds
# the font directory to fontconfig and fails without it. libshiraz-host links
# libEGL_shim (its reqGlEsVersion probe), which loads without ANGLE.
DARWIN_FRAME_CRITICAL = ('libandroid.so', 'libshiraz-jni.so', 'libshiraz-gpu.so', 'libshiraz-host.so',
                         'libshiraz-bitmap.so', 'libshiraz-resources.so', 'libshiraz-text.so', 'libEGL_shim.so')
# OpenGL ES (make macos-gpu-deps, then make macos-build): ANGLE's Metal EGL and
# GLES libraries copied into build/macos, and the GLES JNI bridges linked to
# them. With all of them present the launcher points libEGL_shim
# (SHIRAZ_EGL_LIBRARY) and SDL (SDL_VIDEO_EGL_DRIVER, SDL_VIDEO_GL_DRIVER) at
# ANGLE and selects SDL's opengles2 renderer for a Cocoa window, so
# presentation and the Skia canvas run on ANGLE. Otherwise the run keeps the
# Metal renderer and the software canvas, reqGlEsVersion is 0, and GLES20/GL10
# fail with UnsatisfiedLinkError at first use.
DARWIN_ANGLE = ('libEGL.dylib', 'libGLESv2.dylib', 'libGLESv1_CM.dylib')
DARWIN_GLES = (*DARWIN_ANGLE, 'libshiraz-gles1.so', 'libshiraz-gles2.so')
DARWIN_GL_RENDERER = 'opengles2'
BUNDLED_FONTS = ('NotoSansArabic-Regular.ttf', 'NotoSansArabic-Bold.ttf', 'NotoSansHebrew-Regular.ttf',
                 'NotoSansHebrew-Bold.ttf', 'NotoSans-Regular.ttf', 'NotoSans-Bold.ttf',
                 'NotoColorEmoji.ttf', 'LICENSE', 'NotoEmoji-OFL.txt')
# Written by scripts/bootstrap_darwin_graphics.py; libshiraz-text reads it.
DARWIN_FONTCONFIG_FILE = '.local/etc/fonts/fonts.conf'
# fonts.conf prefers Noto Naskh Arabic for Arabic script (the bundled
# android-fonts.xml order), so on macOS it is frame-critical as well.
DARWIN_PREFERRED_FONTS = ('NotoNaskhArabic-Regular.ttf', 'NotoNaskhArabic-Bold.ttf')
# The only host font directories that fonts.conf may list (read-only).
DARWIN_SYSTEM_FONT_DIRS = ('/System/Library/Fonts', '/Library/Fonts')
# Loaded lazily by the framework: a missing one surfaces as UnsatisfiedLinkError
# at first use. No empty-success stand-ins are built for them.
DARWIN_OPTIONAL = ('libshiraz-sqlite.so', 'libshiraz-image.so', 'libshiraz-path.so',
                   'libshiraz-audio.so', 'libshiraz-playback.so', 'libshiraz-capture.so', 'libshiraz-speech.so',
                   'libshiraz-notifications.so', 'libshiraz-video.so')
# Linux dynamic-loader and Bionic/Berberis variables have no meaning under dyld,
# and inherited DYLD_* (for example DYLD_INSERT_LIBRARIES) must not reach ART.
# Inherited FONTCONFIG_* could point fontconfig at host configuration or caches.
DARWIN_STRIPPED_ENV = ('LD_', 'DYLD_', 'BIONIC_', 'BERBERIS_', 'FONTCONFIG_')
NAME = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\Z')
PROFILE_KEYS = frozenset(('build.model', 'build.manufacturer', 'build.brand', 'build.device', 'build.product',
    'build.hardware', 'build.board', 'build.bootloader', 'build.serial', 'build.host', 'build.user', 'build.time',
    'build.id', 'build.incremental', 'build.release', 'build.securityPatch', 'build.type',
    'build.tags', 'build.display', 'build.abi', 'settings.androidId', 'settings.screenBrightness', 'input.touchscreen', 'sensors.available',
    'network.transport', 'network.connected', 'wifi.enabled',
    'telephony.simCountryIso', 'telephony.simOperator', 'telephony.networkCountryIso', 'telephony.networkOperatorName',
    'telephony.phoneType', 'telephony.simState', 'telephony.networkType'))


def validate_device_profile(path):
    """Reject partial or contradictory values before starting an app process."""
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        if '=' not in line:
            raise FormatError('Device profile requires key=value lines')
        key, value = (part.strip() for part in line.split('=', 1))
        if key not in PROFILE_KEYS or key in values:
            raise FormatError('Unknown or duplicate device profile key: ' + key)
        values[key] = value
    if values.keys() != PROFILE_KEYS:
        raise FormatError('Device profile keys missing: ' + ', '.join(sorted(PROFILE_KEYS - values.keys())))
    for key in PROFILE_KEYS - {'telephony.simCountryIso', 'telephony.simOperator', 'telephony.networkCountryIso', 'telephony.networkOperatorName'}:
        if not values[key]:
            raise FormatError('Empty device profile value: ' + key)
    if values['build.abi'] != native_abi() or values['build.release'] != '8.1.0':
        raise FormatError('Device profile ABI and Android release must match the supported runtime')
    if not re.fullmatch(r'[0-9a-f]{16}', values['settings.androidId']):
        raise FormatError('Device profile Android ID must be 16 lowercase hex digits')
    if not re.fullmatch(r'[A-Z0-9]{8,20}', values['build.serial']):
        raise FormatError('Device profile serial must be 8–20 uppercase letters or digits')
    if not values['settings.screenBrightness'].isdigit() or not 0 <= int(values['settings.screenBrightness']) <= 255:
        raise FormatError('Device profile screen brightness must be an integer from 0 to 255')
    if not re.fullmatch(r'\d{4}-\d\d-\d\d', values['build.securityPatch']):
        raise FormatError('Device profile security patch must be YYYY-MM-DD')
    if values['input.touchscreen'] not in ('true', 'false'):
        raise FormatError('Device profile touchscreen must be true or false')
    if values['sensors.available'] != 'false':
        raise FormatError('Device profile cannot advertise unbridged sensors')
    if values['network.transport'] != 'wifi' or values['network.connected'] != 'true' or values['wifi.enabled'] != 'true':
        raise FormatError('Device profile Wi-Fi transport values must agree')
    if values['telephony.phoneType'] == '0':
        if any(values[key] for key in ('telephony.simCountryIso', 'telephony.simOperator', 'telephony.networkCountryIso', 'telephony.networkOperatorName')) or values['telephony.simState'] != '1' or values['telephony.networkType'] != '0':
            raise FormatError('Device profile without a modem cannot report SIM or network identity')
    for key in ('build.time', 'telephony.phoneType', 'telephony.simState', 'telephony.networkType'):
        if not values[key].isdigit():
            raise FormatError('Invalid device profile number: ' + key)
    expected_display = ' '.join((values['build.product'] + '-' + values['build.type'], values['build.release'], values['build.id'], values['build.incremental'], values['build.tags']))
    if values['build.display'] != expected_display:
        raise FormatError('Device profile display does not match build identity')
    return values


def selected_device_profile(package, template=None):
    """Persist a selected app's fabricated device once, keyed across APK updates."""
    base = os.environ.get('XDG_CONFIG_HOME')
    config_home = Path(base) if base and Path(base).is_absolute() else Path.home() / '.config'
    digest = hashlib.sha256(package.encode('utf-8')).hexdigest()
    profile = config_home / 'shiraz' / 'devices' / (digest + '.properties')
    if profile.exists():
        if profile.is_symlink() or not profile.is_file():
            raise FormatError('Stored device profile must be a regular file')
        validate_device_profile(profile)
        return profile
    if template is None:
        return None
    template = Path(template).resolve()
    if not template.is_file() or not template.is_relative_to((ROOT / 'config').resolve()):
        raise FormatError('Device profile template must be a file under config/')
    values = validate_device_profile(template)
    values['settings.androidId'] = secrets.token_hex(8)
    values['build.serial'] = 'EMULATOR' + secrets.token_hex(6).upper()
    profile.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=profile.parent,
                                         prefix='.device-', delete=False) as stream:
            temporary = Path(stream.name)
            os.chmod(temporary, 0o600)
            stream.write(''.join(f'{key}={values[key]}\n' for key in sorted(values)))
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temporary, profile, follow_symlinks=False)
        except FileExistsError:
            pass
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    if profile.is_symlink() or not profile.is_file():
        raise FormatError('Stored device profile must be a regular file')
    validate_device_profile(profile)
    return profile


def local_path(path, directory):
    root = (ROOT / directory).resolve()
    result = Path(path).resolve()
    if not root.is_relative_to(ROOT) or not result.is_relative_to(root):
        raise FormatError(f'Generated output must stay under {directory}/')
    return result


def properties_text(values):
    """Encode known manifest facts for Java Properties.load(Reader), preserving values."""
    escapes = {'\\': '\\\\', '\n': '\\n', '\r': '\\r', '\t': '\\t', '\f': '\\f',
               ' ': '\\ ', '=': '\\=', ':': '\\:', '#': '\\#', '!': '\\!'}
    def encode(value):
        return ''.join(escapes.get(char, ('\\u%04x' % ord(char)) if ord(char) < 32 else char) for char in str(value))
    return ''.join(encode(key) + '=' + encode(value) + '\n' for key, value in sorted(values.items()) if value is not None)


def emit_filters(values, base, filters):
    if len(filters) > 128:
        raise FormatError('Activity filter limit')
    values[base + 'filter.count'] = len(filters)
    for number, filter in enumerate(filters):
        prefix = base + f'filter.{number}.'
        values[prefix + 'priority'] = filter.get('priority', '0')
        values[prefix + 'unsupported'] = str(filter.get('unsupported_data', False)).lower()
        for kind in ('action', 'category', 'scheme', 'mime'):
            entries = filter.get(kind, [])
            if len(entries) > 128 or any(not isinstance(entry, str) or len(entry) > 4096 for entry in entries):
                raise FormatError('Activity filter entry limit')
            values[prefix + kind + '.count'] = len(entries)
            for entry, value in enumerate(entries):
                values[prefix + kind + '.' + str(entry)] = value


def application_metadata(manifest, apk, data):
    values = {'package': manifest['package'], 'applicationClass': manifest.get('application_class'),
            'versionName': manifest.get('version_name'), 'versionCode': manifest.get('version_code') or '0',
            'versionCodeMajor': manifest.get('version_code_major') or '0',
            'sharedUserId': manifest.get('shared_user_id'), 'sharedUserLabel': manifest.get('shared_user_label', 0),
            'baseRevisionCode': manifest.get('revision_code', 0), 'installLocation': manifest.get('install_location', -1),
            'minSdk': manifest.get('min_sdk') or '1', 'targetSdk': manifest.get('target_sdk') or manifest.get('min_sdk') or '1',
            'theme': manifest.get('application_theme', 0), 'labelRes': manifest.get('application_label_resource'), 'versionNameRes': manifest.get('version_name_resource'),
            'icon': manifest.get('application_icon', 0), 'logo': manifest.get('application_logo', 0), 'banner': manifest.get('application_banner', 0),
            'sourceDir': str(apk), 'dataDir': str(data), 'label': manifest.get('application_label'),
            'enabled': manifest.get('application_enabled'), 'debuggable': manifest.get('application_debuggable'),
            'hasCode': manifest.get('application_has_code'),
            'usesCleartextTraffic': manifest.get('application_uses_cleartext_traffic'),
            'networkSecurityConfig': manifest.get('application_network_security_config')}
    for field in ('backupAgent', 'manageSpaceActivity', 'appComponentFactory', 'permission', 'processName', 'taskAffinity', 'descriptionRes', 'uiOptions', 'category', 'requiresSmallestWidthDp', 'compatibleWidthLimitDp', 'largestWidthLimitDp', 'compileSdkVersion', 'compileSdkVersionCodename'):
        values['applicationInfo.' + field] = manifest.get('application_info', {}).get(field)
    metadata = manifest.get('application_metadata', [])
    if len(metadata) > 128:
        raise FormatError('Application metadata limit')
    values['application.metadata.count'] = len(metadata)
    for entry, item in enumerate(metadata):
        for field in ('name', 'name_resource', 'kind', 'value'):
            values[f'application.metadata.{entry}.{field}'] = item.get(field)
    permissions = manifest.get('permissions', [])
    if len(permissions) > 1024:
        raise FormatError('Permission declaration limit')
    values['permission.count'] = len(permissions)
    for index, permission in enumerate(permissions):
        values[f'permission.{index}'] = permission
    defined = manifest.get('defined_permissions', [])
    if len(defined) > 1024:
        raise FormatError('Defined permission limit')
    values['definedPermission.count'] = len(defined)
    for index, permission in enumerate(defined):
        values[f'definedPermission.{index}.name'] = permission.get('name')
        values[f'definedPermission.{index}.protection'] = permission.get('protection', 0)
    own = sorted({item['name'] for item in defined if isinstance(item.get('name'), str)
                  and item['name'].startswith(manifest['package'] + '.')
                  and item['name'] in permissions and item.get('protection', 0) & 15 == 2})
    values['ownSignaturePermission.count'] = len(own)
    for index, permission in enumerate(own):
        values[f'ownSignaturePermission.{index}'] = permission
    launchers = manifest.get('launcher_activities', [])
    if len(launchers) > 256:
        raise FormatError('Launcher declaration limit')
    values['launcher.count'] = len(launchers)
    for index, launcher in enumerate(launchers):
        values[f'launcher.{index}.target'] = launcher.get('target')
    activities = manifest.get('activities', [])
    if len(activities) > 256:
        raise FormatError('Activity declaration limit')
    values['activity.count'] = len(activities)
    for index, activity in enumerate(activities):
        for field in ('name', 'theme', 'config_changes', 'screen_orientation', 'launch_mode', 'enabled', 'enabledRes', 'exported', 'exportedRes', 'directBootAware', 'process', 'permission', 'parentActivityName', 'label', 'labelRes'):
            values[f'activity.{index}.{field}'] = activity.get(field)
        emit_filters(values, f'activity.{index}.', activity.get('filters', []))
        metadata = activity.get('metadata', [])
        if len(metadata) > 128:
            raise FormatError('Activity metadata limit')
        values[f'activity.{index}.metadata.count'] = len(metadata)
        for entry, item in enumerate(metadata):
            for field in ('name', 'name_resource', 'kind', 'value'):
                values[f'activity.{index}.metadata.{entry}.{field}'] = item.get(field)
    receivers = manifest.get('receivers', [])
    if len(receivers) > 256:
        raise FormatError('Receiver declaration limit')
    values['receiver.count'] = len(receivers)
    for index, receiver in enumerate(receivers):
        for field in ('name', 'enabled', 'enabledRes', 'exported', 'exportedRes', 'permission', 'directBootAware', 'process'):
            values[f'receiver.{index}.{field}'] = receiver.get(field)
        emit_filters(values, f'receiver.{index}.', receiver.get('filters', []))
        metadata = receiver.get('metadata', [])
        if len(metadata) > 128:
            raise FormatError('Receiver metadata limit')
        values[f'receiver.{index}.metadata.count'] = len(metadata)
        for entry, item in enumerate(metadata):
            for field in ('name', 'name_resource', 'kind', 'value'):
                values[f'receiver.{index}.metadata.{entry}.{field}'] = item.get(field)
    def emit_declarations(prefix, records, fields, limit):
        if len(records) > limit:
            raise FormatError('Package declaration limit: ' + prefix)
        values[prefix + '.count'] = len(records)
        for index, record in enumerate(records):
            for field in fields:
                values[f'{prefix}.{index}.{field}'] = record.get(field)
    feature_fields = ('name', 'version', 'reqGlEsVersion', 'flags')
    emit_declarations('feature', manifest.get('features', []), feature_fields, 256)
    emit_declarations('configuration', manifest.get('configurations', []), ('reqTouchScreen', 'reqKeyboardType', 'reqNavigation', 'reqInputFeatures', 'reqGlEsVersion'), 256)
    groups = manifest.get('feature_groups', [])
    if len(groups) > 64 or sum(map(len, groups)) > 256:
        raise FormatError('Feature group limit')
    values['featureGroup.count'] = len(groups)
    for index, group in enumerate(groups):
        emit_declarations(f'featureGroup.{index}', group, feature_fields, 256)
    instrumentation = manifest.get('instrumentation', [])
    if len(instrumentation) > 256:
        raise FormatError('Instrumentation declaration limit')
    values['instrumentation.count'] = len(instrumentation)
    for index, record in enumerate(instrumentation):
        for field in ('name', 'targetPackage', 'targetProcesses', 'handleProfiling', 'functionalTest', 'label', 'labelRes', 'iconRes', 'logoRes', 'bannerRes'):
            values[f'instrumentation.{index}.{field}'] = record.get(field)
        metadata = record.get('metadata', [])
        if len(metadata) > 128:
            raise FormatError('Instrumentation metadata limit')
        values[f'instrumentation.{index}.metadata.count'] = len(metadata)
        for entry, item in enumerate(metadata):
            for field in ('name', 'name_resource', 'kind', 'value'):
                values[f'instrumentation.{index}.metadata.{entry}.{field}'] = item.get(field)
    services = manifest.get('services', [])
    if len(services) > 256:
        raise FormatError('Service declaration limit')
    values['service.count'] = str(len(services))
    for index, service in enumerate(services):
        for field in ('name', 'enabled', 'enabledRes', 'exportedRes', 'process', 'isolated', 'exported', 'permission', 'directBootAware', 'stopWithTask', 'externalService', 'singleUser', 'useAppZygote', 'allowSharedIsolatedProcess'):
            values[f'service.{index}.{field}'] = service.get(field)
        emit_filters(values, f'service.{index}.', service.get('filters', []))
        metadata = service.get('metadata', [])
        if len(metadata) > 128:
            raise FormatError('Service metadata limit')
        values[f'service.{index}.metadata.count'] = len(metadata)
        for entry, item in enumerate(metadata):
            for field in ('name', 'name_resource', 'kind', 'value'):
                values[f'service.{index}.metadata.{entry}.{field}'] = item.get(field)
    providers = manifest.get('providers', [])
    if len(providers) > 256:
        raise FormatError('Provider declaration limit')
    values['provider.count'] = str(len(providers))
    for index, provider in enumerate(providers):
        emit_filters(values, f'provider.{index}.', provider.get('filters', []))
        for field in ('name', 'authorities', 'enabled', 'exported', 'grantUriPermissions', 'multiprocess', 'initOrder', 'process'):
            values[f'provider.{index}.{field}'] = provider.get(field)
        metadata = provider.get('metadata', [])
        if len(metadata) > 128:
            raise FormatError('Provider metadata limit')
        values[f'provider.{index}.metadata.count'] = len(metadata)
        for entry, item in enumerate(metadata):
            for field in ('name', 'name_resource', 'kind', 'value'):
                values[f'provider.{index}.metadata.{entry}.{field}'] = item.get(field)
    return values


def package_timestamps(data, digest):
    """Record first host registration and APK changes while holding the package session lock."""
    if not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise FormatError('Invalid package fingerprint')
    path = Path(data) / '.package-install.json'
    record = None
    if path.exists():
        try:
            if path.stat().st_size > 4096:
                raise ValueError('size')
            record = json.loads(path.read_text(encoding='utf-8'))
            if not isinstance(record, dict) or record.get('version') != 1 or not isinstance(record.get('sha256'), str) or not re.fullmatch(r'[0-9a-f]{64}', record['sha256']):
                raise ValueError('record')
            first, last = record['firstInstallTime'], record['lastUpdateTime']
            if type(first) is not int or type(last) is not int or not 0 < first <= last < 2**63 - 1:
                raise ValueError('timestamps')
        except (ValueError, KeyError, UnicodeError) as error:
            raise FormatError('Invalid host package timestamp record') from error
    if record is None or record['sha256'] != digest:
        now = max(1, time.time_ns() // 1_000_000)
        first = record['firstInstallTime'] if record else now
        last = max(now, record['lastUpdateTime'] + 1) if record else now
        if last >= 2**63 - 1:
            raise FormatError('Package timestamp range')
        record = dict(version=1, sha256=digest, firstInstallTime=first, lastUpdateTime=last)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=data, prefix='.package-install-', delete=False) as stream:
                temporary = Path(stream.name)
                json.dump(record, stream, sort_keys=True)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    return {key: record[key] for key in ('firstInstallTime', 'lastUpdateTime')}


def failure_log_tail(path):
    """Bound diagnostics to the terminal stack, excluding caught logs and cleanup failures."""
    with Path(path).open('rb') as stream:
        stream.seek(max(0, stream.seek(0, 2) - 65536))
        text = stream.read().decode('utf-8', errors='replace').replace('\0', '')
    fatal = list(re.finditer(r"(?m)^SHIRAZ_RUNTIME_FATAL\r?$", text))
    if fatal:
        text = text[fatal[-1].end():]
    roots = list(re.finditer(r'(?m)^(?:Exception in thread "[^"\n]*" )?'
                            r'[A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error)(?::|$)', text))
    if roots:
        text = text[roots[-1].start():]
    return re.sub(r'(?m)^\t+Suppressed:.*(?:\n\t.*)*', '', text)


def missing_framework_class(path):
    """Report only a bounded, identifier-shaped framework class, not app log contents."""
    text = failure_log_tail(path)
    match = re.search(r'NoClassDefFoundError: Failed resolution of: L((?:android|dalvik|libcore|com/android)/[A-Za-z0-9_$/]+);', text)
    if match:
        return match[1].replace('/', '.')
    match = re.search(r'ClassNotFoundException: ((?:android|dalvik|libcore|com\.android)\.[A-Za-z0-9_.$]+)', text)
    return match[1] if match else None


def missing_framework_method(path):
    text = failure_log_tail(path)
    match = re.search(r'NoSuchMethodError: No (?:virtual|static|interface|direct) method '
                      r'([A-Za-z0-9_$<>]+\([A-Za-z0-9_$/;\[]*\)[A-Za-z0-9_$/;\[]+) '
                      r'in class L((?:android|dalvik|libcore|com/android)/[A-Za-z0-9_$/]+);', text)
    return match[2].replace('/', '.') + '->' + match[1] if match else None


def art_execution_options(mode):
    """Keep interpreter reproducibility by default; JIT is an explicit experiment."""
    if mode == 'interpreter':
        return ['-Xint', '-Xusejit:false']
    if mode == 'jit':
        return ['-Xusejit:true']
    raise FormatError('SHIRAZ_ART_MODE must be interpreter or jit')


def host_system():
    """Operating system that runs ART; a seam for tests on other hosts."""
    return platform.system()


def runtime_inputs(system, root, host, execution_mode):
    """Return the VM, framework inputs, required files and lazily loaded optional files."""
    framework = root / 'build/shiraz-framework.jar'
    framework_resources = root / 'build/framework-res/framework-res.apk'
    if system == 'Darwin':
        vm = host / 'bin/dalvikvm'
        required = [vm, *(host / 'framework' / jar for jar in DARWIN_JARS), framework, framework_resources,
                    *(root / DARWIN_LIBRARIES / name for name in DARWIN_FRAME_CRITICAL), host / 'lib64/libandroidfw.so',
                    *(root / '.local/share/shiraz/fonts' / name for name in (*BUNDLED_FONTS, *DARWIN_PREFERRED_FONTS)),
                    root / DARWIN_FONTCONFIG_FILE, root / DARWIN_LIBRARIES / 'libshiraz-trust.so',
                    host / 'lib64/libwolfssljni.so']
        optional = [root / DARWIN_LIBRARIES / name for name in DARWIN_OPTIONAL]
        return vm, framework, framework_resources, required, optional
    vm = host / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = host / 'bin/dalvikvm'
    required = [root / 'build/libshiraz-capture.so', root / 'build/libshiraz-speech.so', root / '.local/share/espeak-ng-data/phondata', vm, framework, framework_resources, host / 'lib64/libwolfssljni.so', root / '.local/lib/libwolfssl.so', root / 'build/libshiraz-bitmap.so', root / 'build/libshiraz-notifications.so', root / 'build/libshiraz-sqlite.so', root / 'build/libshiraz-audio.so', root / 'build/libshiraz-text.so', root / 'build/libshiraz-resources.so', root / 'build/libshiraz-android-process.so', host / 'lib64/libandroidfw.so', root / 'build/libshiraz-host.so', root / 'build/libshiraz-jni.so', root / 'build/libandroid.so', root / 'build/libandroid.so.0', root / 'build/libEGL.so.1',
                *(host / 'framework' / jar for jar in JARS)]
    required.append(root / 'build/libshiraz-gles1.so')
    required.append(root / 'build/libshiraz-gles2.so')
    required.extend(root / '.local/share/shiraz/fonts' / name for name in BUNDLED_FONTS)
    if execution_mode == 'jit':
        required.append(host / 'lib64/libart-compiler.so')
    return vm, framework, framework_resources, required, []


def darwin_fontconfig_failure(root):
    """Why ROOT's in-tree fonts.conf is unusable, or None.

    fontconfig falls back silently to its compiled-in configuration, which reads
    per-user (xdg) and /usr/local font and cache directories, when the file
    cannot be loaded; a fonts.conf from another checkout path points cache
    writes and fonts at that tree. Both are rejected before launch.
    """
    path = root / DARWIN_FONTCONFIG_FILE
    rerun = '; rerun make macos-graphics-deps'
    try:
        tree = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as error:
        return f'{DARWIN_FONTCONFIG_FILE} cannot be read as fontconfig XML ({error}){rerun}'
    if tree.tag != 'fontconfig':
        return f'{DARWIN_FONTCONFIG_FILE} has root <{tree.tag}>, not <fontconfig>{rerun}'
    for element in tree.iter():
        if element.get('prefix') in ('xdg', 'cwd', 'default', 'relative') or (element.text or '').strip().startswith('~'):
            return f'{DARWIN_FONTCONFIG_FILE} <{element.tag}> reads per-user or relative host state{rerun}'
    caches = [(element.text or '').strip() for element in tree.findall('cachedir')]
    cache = root / '.local/var/cache/fontconfig'
    if caches != [str(cache)]:
        return f'{DARWIN_FONTCONFIG_FILE} cache directories {caches} are not exactly {cache}{rerun}'
    if not cache.is_dir() or not os.access(cache, os.W_OK | os.X_OK):
        return f'fontconfig cache directory {cache} is missing or not writable{rerun}'
    fonts = root / '.local/share/shiraz/fonts'
    for element in tree.findall('dir') + tree.findall('include'):
        value = Path((element.text or '').strip())
        allowed = value.is_absolute() and (value.is_relative_to(root) or (
            element.tag == 'dir' and str(value) in DARWIN_SYSTEM_FONT_DIRS))
        if not allowed:
            return (f'{DARWIN_FONTCONFIG_FILE} <{element.tag}> {value} is neither inside this checkout ({root}) '
                    f'nor a read-only system font directory{rerun}')
    if str(fonts) not in [(element.text or '').strip() for element in tree.findall('dir')]:
        return f'{DARWIN_FONTCONFIG_FILE} does not list the bundled font directory {fonts}{rerun}'
    return None


def profile_environment(env, profile_values, version):
    env['SHIRAZ_ANDROID_DISPLAY_ID'] = profile_values.get('build.display', 'Shiraz ' + version)
    env['SHIRAZ_ANDROID_RELEASE'] = profile_values.get('build.release', '8.1')
    # Keep native property reads consistent with the explicitly selected profile.
    for field in ('model', 'manufacturer', 'brand', 'device', 'product', 'board',
                  'hardware', 'bootloader', 'serial', 'host', 'user', 'id', 'type',
                  'tags', 'incremental', 'securityPatch'):
        variable = 'SHIRAZ_ANDROID_' + ('SECURITY_PATCH' if field == 'securityPatch' else field.upper())
        env.pop(variable, None)
        if 'build.' + field in profile_values:
            env[variable] = profile_values['build.' + field]
    env.pop('SHIRAZ_ANDROID_FINGERPRINT', None)
    if profile_values:
        env['SHIRAZ_ANDROID_FINGERPRINT'] = (
            '/'.join(profile_values['build.' + k] for k in ('brand', 'product', 'device'))
            + ':' + profile_values['build.release'] + '/' + profile_values['build.id']
            + '/' + profile_values['build.incremental'] + ':' + profile_values['build.type']
            + '/' + profile_values['build.tags']
        )


def launch_command(system, *, root, host, environ, vm, classpath, framework_resources, work, data, app_lib,
                   metadata, package, app, activity, width, height, frames, snapshot, log_level,
                   execution_options, device_profile, profile_values, digitalis, version, gles=False, guest_bridge=None):
    """Build the ART command line and environment without touching the filesystem."""
    darwin = system == 'Darwin'
    boot = ':'.join(str(host / 'framework' / name) for name in (DARWIN_JARS if darwin else JARS))
    if darwin:
        env = {key: value for key, value in environ.items() if not key.startswith(DARWIN_STRIPPED_ENV)}
        env.update({'ANDROID_ROOT': str(host), 'ANDROID_DATA': str(work / 'android-data'),
                    'XDG_CACHE_HOME': str(work / 'cache'), 'BOOTCLASSPATH': boot,
                    'SHIRAZ_FONT_DIR': str(root / '.local/share/shiraz/fonts'),
                    'SHIRAZ_LOCAL_SOCKET_DIR': str(darwin_socket_directory(root)),
                    # The in-tree fonts.conf (make macos-graphics-deps): bundled
                    # fonts plus read-only system font directories, cache in .local.
                    'FONTCONFIG_FILE': str(root / DARWIN_FONTCONFIG_FILE),
                    'FONTCONFIG_PATH': str((root / DARWIN_FONTCONFIG_FILE).parent),
                    'XDG_DATA_DIRS': str(root / '.local/share') + ':/usr/share'})
    else:
        env = {**environ, 'ANDROID_ROOT': str(host), 'ANDROID_DATA': str(work / 'android-data'),
               'XDG_CACHE_HOME': str(work / 'cache'), 'BOOTCLASSPATH': boot,
               'LD_LIBRARY_PATH': str(host / 'lib64') + ':' + str(root / '.local/lib') + ':' + str(root / 'build') + ':' + str(app_lib),
               'BIONIC_LD_LIBRARY_PATH': str(app_lib),
               'SHIRAZ_FONT_DIR': str(root / '.local/share/shiraz/fonts'),
               'XDG_DATA_DIRS': str(root / '.local/share') + ':/usr/share'}
    # Host headless/window choices must not select an Android SDL backend.
    for key in ('SDL_VIDEODRIVER', 'SDL_RENDER_DRIVER'):
        if key in env:
            env.setdefault('SHIRAZ_HOST_' + key, env.pop(key))
    if darwin:
        darwin_gles_environment(env, root, gles)
        env.pop('SHIRAZ_GUEST_EGL_SHIM', None)
        if guest_bridge:
            env['SHIRAZ_ANDROID_FONT_DIR'] = str(root / '.local/share/shiraz/fonts')
        if guest_bridge and gles:
            env['SHIRAZ_GUEST_EGL_SHIM'] = str(root / DARWIN_LIBRARIES / 'libEGL_shim.so')
    if digitalis:
        env['LD_LIBRARY_PATH'] = str(digitalis.parent) + ':' + env['LD_LIBRARY_PATH']
        # Digitalis strips host loader variables and unmangles this prefix
        # when constructing the guest's initial environment.
        env['BERBERIS_GUEST_LD_LIBRARY_PATH'] = str(app_lib)
    env.update(native_build_environment(profile_values))
    trust_store = (['-Djavax.net.ssl.trustStore=' + environ['SHIRAZ_JAVA_TRUST_STORE']]
                   if environ.get('SHIRAZ_JAVA_TRUST_STORE') else [])
    tail = ['-Dshiraz.frameworkResources=' + str(framework_resources),
            *(['-Dshiraz.deviceProfile=' + str(device_profile)] if device_profile else []),
            '-Dshiraz.speechData=' + str(root / '.local/share'),
            '-Djava.io.tmpdir=' + str(work / 'tmp'), '-Duser.home=' + str(work / 'home'),
            '-cp', classpath, 'shiraz.runtime.ActivityRunner',
            package, app, activity, str(width), str(height), str(frames if frames is not None else -1),
            str(data), str(snapshot) if snapshot else '-', str(metadata)]
    if darwin:
        # dyld resolves the framework's System.loadLibrary through these
        # absolute directories, then @rpath. No preload, no -Xcompiler: the
        # interposers and dex2oat are not ported (M5).
        command = [str(vm), '-Xbootclasspath:' + boot, '-Xnoimage-dex2oat', *execution_options, '-Xcheck:jni',
                   *(['-XX:NativeBridge=' + str(guest_bridge), '-Xforce-nb-testing', '-Dshiraz.guestAbi=arm64-v8a'] if guest_bridge else []),
                   '-Dlog.tag=' + log_level, *trust_store,
                   '-Djava.library.path=' + ':'.join((str(root / DARWIN_LIBRARIES), str(host / 'lib64'), *([str(app_lib)] if guest_bridge else []))),
                   *tail]
        return command, env
    compat = root / 'build/libshiraz-android-process.so'
    # NDK libraries resolve platform symbols through the process-wide scope.
    # ART's JNI-local load of Resources alone does not expose AAssetManager_*
    # to native application libraries such as React Native and Flutter.
    platform_libraries = (compat, root / 'build/libandroid.so', root / 'build/libshiraz-resources.so',
                          root / 'build/libshiraz-android-fonts.so')
    # Match the AppImage launcher's process-scoped native Android font mapping.
    env.setdefault('SHIRAZ_ANDROID_FONT_DIR', str(root / '.local/share/shiraz/fonts'))
    env['LD_PRELOAD'] = ':'.join(map(str, platform_libraries)) + (':' + env['LD_PRELOAD'] if env.get('LD_PRELOAD') else '')
    command = [str(vm), '-Xbootclasspath:' + boot, '-Xnoimage-dex2oat', *execution_options, '-Xcheck:jni',
               *(['-XX:NativeBridge=' + str(digitalis.resolve()), '-Xforce-nb-testing', '-Dshiraz.guestAbi=arm64-v8a'] if digitalis else []),
               '-Xcompiler:' + str(host / 'bin/dex2oat'), '-Dlog.tag=' + log_level,
               *trust_store,
               '-Djava.library.path=' + ':'.join((str(host / 'lib64'), str(root / 'build'), str(app_lib))),
               *tail]
    if loader := environ.get('SHIRAZ_RUNTIME_LOADER'):
        # The private loader must also find its matching C++ runtime. Debian
        # 12's system libstdc++ lacks symbols required by the bundled ART.
        library_path = env.pop('LD_LIBRARY_PATH') + ':' + str(Path(loader).parent)
        preload = env.pop('LD_PRELOAD', '')
        # Scope private glibc and compatibility libraries to ART. Child host
        # executables must use their own loader and libraries.
        command = [loader, '--library-path', library_path,
                   *(['--preload', preload] if preload else []), *command]
    return command, env


def darwin_guest_bridge(root, native_abis, environ):
    """Select the explicit M5 bridge; do not fall back after opting in."""
    mode = environ.get('SHIRAZ_GUEST_LOADER', '0')
    if mode not in ('0', '1'):
        raise FormatError('SHIRAZ_GUEST_LOADER must be 0 or 1')
    if not native_abis or mode == '0':
        return None
    if 'arm64-v8a' not in native_abis or native_abi() != 'arm64-v8a':
        raise FormatError('The macOS M5 loader requires arm64-v8a libraries on Apple Silicon')
    library = root / DARWIN_LIBRARIES / 'libshiraz-guest.so'
    if not library.is_file():
        raise FormatError('The M5 guest loader is not built; run make macos-runtime-libs')
    return library.resolve()


def darwin_gles_environment(env, root, gles):
    """Point EGL and SDL at the in-tree ANGLE when DARWIN_GLES is built (see there)."""
    if not gles:
        env.pop('SHIRAZ_EGL_LIBRARY', None)
        return
    libraries = (root / DARWIN_LIBRARIES).resolve()
    # Absolute: the macOS shim (native/egl_shim.cpp) loads only an absolute
    # SHIRAZ_EGL_LIBRARY; dyld would search the cwd (the app's data directory)
    # for a relative or leaf name.
    env['SHIRAZ_EGL_LIBRARY'] = str(libraries / 'libEGL.dylib')
    # A Cocoa window gets the GL renderer by default; an explicit
    # SDL_RENDER_DRIVER wins, and SDL's dummy/offscreen drivers on macOS have
    # no GL (headless runs present in software; app EGL contexts still use ANGLE).
    if 'SHIRAZ_HOST_SDL_RENDER_DRIVER' not in env and env.get('SHIRAZ_HOST_SDL_VIDEODRIVER', 'cocoa') == 'cocoa':
        env['SHIRAZ_HOST_SDL_RENDER_DRIVER'] = DARWIN_GL_RENDERER
    if env.get('SHIRAZ_HOST_SDL_RENDER_DRIVER') == DARWIN_GL_RENDERER:
        # Read by SDL itself (SDL_getenv), not as hints. Only for opengles2:
        # SDL's desktop-GL (CGL) path would load SDL_VIDEO_GL_DRIVER as well.
        # SDL's GLES2 renderer requests the ES profile, which makes Cocoa use EGL.
        env['SDL_VIDEO_EGL_DRIVER'] = str(libraries / 'libEGL.dylib')
        env['SDL_VIDEO_GL_DRIVER'] = str(libraries / 'libGLESv2.dylib')


def run_activity(apk, report, *, width=360, height=640, frames=None, snapshot=None, timeout=None, log_level='INFO', device_profile=None, native_split=None):
    if log_level not in ('VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'):
        raise FormatError('Invalid Android log level')
    if width < 1 or height < 1 or width > 8192 or height > 8192 or width * height > 16 * 1024 * 1024:
        raise FormatError('Activity dimensions exceed supported limits')
    if frames is not None and not 1 <= frames <= 600:
        raise FormatError('frames must be between 1 and 600')
    if timeout is not None and timeout < 1:
        raise FormatError('timeout must be positive')
    system = host_system()
    darwin = system == 'Darwin'
    execution_mode = os.environ.get('SHIRAZ_ART_MODE', 'interpreter')
    execution_options = art_execution_options(execution_mode)
    if darwin and execution_mode != 'interpreter':
        raise FormatError('SHIRAZ_ART_MODE=jit is not supported on macOS yet; the darwin ART port runs -Xint only')
    if darwin and os.environ.get('SHIRAZ_RUNTIME_LOADER'):
        raise FormatError('SHIRAZ_RUNTIME_LOADER selects a Linux ld.so and is not supported on macOS')
    manifest = report['manifest']
    minimum = manifest.get('min_sdk')
    if minimum and (not re.fullmatch(r'[0-9]+', str(minimum)) or int(minimum) > 27):
        raise FormatError('APK requires a newer Android API profile than the current experimental API-27 subset')
    if manifest.get('application_enabled') == 'false':
        raise FormatError('The application is disabled in its manifest')
    launcher = manifest['launcher_activities']
    if not launcher or not launcher[0]['target']:
        raise FormatError('APK has no supported MAIN/LAUNCHER activity')
    package = manifest['package']
    app = manifest['application_class'] or 'android.app.Application'
    activity = launcher[0]['target']
    if not all(NAME.fullmatch(name) for name in (package, app, activity)):
        raise FormatError('Invalid package or component class name')
    native_source = apk
    split_report = None
    if native_split is not None:
        native_split = Path(native_split).resolve()
        with zipfile.ZipFile(native_split) as archive:
            if any(not (name == 'AndroidManifest.xml' or name == 'stamp-cert-sha256' or
                        name.startswith(('lib/', 'META-INF/'))) for name in archive.namelist()):
                raise FormatError('Only native-library splits are supported; code/resource splits are not implemented')
        split_report = inspect_apk(native_split, require_dex=False)
        if any(split_report['manifest'].get(key) != manifest.get(key)
               for key in ('package', 'version_code', 'version_code_major')):
            raise FormatError('Native split package/version does not match the base APK')
        if report['native_abis'] or split_report['dex'] or not split_report['native_abis']:
            raise FormatError('Expected a native-only split and a base without native libraries')
        report = dict(report, native_abis=split_report['native_abis'])
        native_source = native_split
    digitalis = None
    guest_bridge = None
    if darwin:
        if report['native_abis']:
            if os.environ.get('SHIRAZ_REFUSE_NATIVE_LIBS') == '1':
                raise FormatError('APK contains native ABI libraries (lib/' + '/, lib/'.join(report['native_abis'])
                                  + '/); native JNI libraries need the M5 guest loader on macOS')
        guest_bridge = darwin_guest_bridge(ROOT, report['native_abis'], os.environ)
        if report['native_abis']:
            if guest_bridge:
                print('SHIRAZ_GUEST_LOADER configured=darwin-arm64 app_execution=experimental', flush=True)
            else:
                print('SHIRAZ_UNSUPPORTED native-libraries abis=' + ','.join(report['native_abis'])
                      + ' reason=m5-guest-loader-disabled enable=SHIRAZ_GUEST_LOADER=1 first-use=UnsatisfiedLinkError', flush=True)
    else:
        if not supports_native_abis(report['native_abis']) and native_abi() == 'x86_64' and 'arm64-v8a' in report['native_abis']:
            if configured_bridge := os.environ.get('SHIRAZ_DIGITALIS_LIBRARY'):
                digitalis = Path(configured_bridge).resolve()
                if not digitalis.is_file():
                    raise FormatError('Configured Digitalis native bridge is missing')
                for name in ('libberberis_proxy_libc.so', 'libberberis_proxy_libm.so'):
                    if not (digitalis.parent / name).is_file():
                        raise FormatError('Incomplete Digitalis runtime: missing ' + name + '; run make runtime-digitalis')
        if not supports_native_abis(report['native_abis']) and digitalis is None:
            raise FormatError('APK has no native ABI candidate for this host (' + str(native_abi()) + ')')
    vm, framework, framework_resources, required, optional = runtime_inputs(system, ROOT, HOST, execution_mode)
    # Optional Apache HTTP compatibility DEX, built separately from licensed
    # upstream jars. Keep APK classes ahead of this fallback library.
    legacy_http = ROOT / 'build/shiraz-legacy-http.jar'
    # ART runs with cwd set to the app data directory, so a relative APK path
    # on the class path would not resolve there.
    apk = Path(apk).resolve()
    classpath = str(framework) + ':' + str(apk)
    if legacy_http.is_file():
        classpath += ':' + str(legacy_http)
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        if darwin:
            raise FormatError('Runtime is not built for macOS; frame-critical inputs missing: ' + ', '.join(missing))
        raise FormatError('Runtime is not built; run make runtime-art build framework. Missing: ' + ', '.join(missing))
    if darwin:
        if failure := darwin_fontconfig_failure(ROOT):
            raise FormatError('Runtime is not configured for macOS: ' + failure)
        unavailable = [path.name for path in optional if not path.is_file()]
        if unavailable:
            print('SHIRAZ_HOST_UNAVAILABLE libraries=' + ','.join(unavailable)
                  + ' reason=not-built-for-macos first-use=UnsatisfiedLinkError', flush=True)
        if gles_missing := [name for name in DARWIN_GLES if not (ROOT / DARWIN_LIBRARIES / name).is_file()]:
            print('SHIRAZ_HOST_UNAVAILABLE gles libraries=' + ','.join(gles_missing)
                  + ' reason=angle-not-built-for-macos fallback=renderer-metal,canvas-software,reqGlEsVersion-0'
                  + ' first-use=UnsatisfiedLinkError', flush=True)
    device_profile = selected_device_profile(package, device_profile or os.environ.get('SHIRAZ_DEVICE_PROFILE_TEMPLATE'))
    if snapshot:
        snapshot = local_path(snapshot, 'build')
        if snapshot.suffix.lower() != '.ppm':
            raise FormatError('Startup screenshots must use the .ppm format')
        if snapshot in {apk, *(item.resolve() for item in required)}:
            raise FormatError('Screenshot must not overwrite APK/runtime input')
        snapshot.parent.mkdir(parents=True, exist_ok=True)
    work = local_path(ROOT / 'build/runs' / report['apk_sha256'], 'build')
    data = local_path(ROOT / 'data/apps' / package, 'data')
    work.mkdir(parents=True, exist_ok=True)
    data.mkdir(parents=True, exist_ok=True)
    with (data / '.session.lock').open('a') as lock:
        try:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise FormatError('This application package already has a running host session') from exc
        (work / 'result.json').unlink(missing_ok=True)
        for child in ('cache', 'tmp', 'home', 'android-data/dalvik-cache'):
            (work / child).mkdir(parents=True, exist_ok=True)
        app_lib = work / 'app_lib'
        if app_lib.exists():
            shutil.rmtree(app_lib)
        if report['native_abis']:
            try:
                libraries = extract_native(native_source, app_lib, 'x86_64' if native_abi() == 'x86_64' and digitalis is None else 'aarch64')
            except (ValueError, OSError, zipfile.BadZipFile) as exc:
                raise FormatError('Cannot prepare APK native libraries: ' + str(exc)) from exc
            print(f'SHIRAZ_NATIVE_LIBRARIES installed={len(libraries)}', flush=True)
        app_lib.mkdir(parents=True, exist_ok=True)
        metadata = work / 'application.properties'
        facts = application_metadata(manifest, apk, data)
        facts['nativeLibraryDir'] = str(app_lib)
        facts.update(package_timestamps(data, report['apk_sha256']))
        metadata.write_text(properties_text(facts), encoding='utf-8')
        profile_values = validate_device_profile(device_profile) if device_profile else {}
        if darwin:
            from .app_icon import resolve_label
            from .darwin_app import badged_icon, prepare_app_bundle
            try:
                label = resolve_label(apk)
            except (FormatError, OSError, zipfile.BadZipFile):
                label = None
            icon = badged_icon(apk, work, ROOT / 'build/icons') or ROOT / 'build/icons/shiraz.icns'
            vm = prepare_app_bundle(work, vm, HOST, package, icon=icon, name=label)
        command, env = launch_command(
            system, root=ROOT, host=HOST, environ=os.environ, vm=vm, classpath=classpath,
            framework_resources=framework_resources, work=work, data=data, app_lib=app_lib, metadata=metadata,
            package=package, app=app, activity=activity, width=width, height=height, frames=frames,
            snapshot=snapshot, log_level=log_level, execution_options=execution_options,
            device_profile=device_profile, profile_values=profile_values, digitalis=digitalis,
            version=(ROOT / 'VERSION').read_text(encoding='ascii').strip(),
            gles=darwin and not gles_missing, guest_bridge=guest_bridge)
        print(f'Launching experimental activity {activity}; logs: {work.relative_to(ROOT)}', flush=True)
        try:
            result = run_with_live_logs(command, data, env, work, timeout)
        except subprocess.TimeoutExpired:
            raise FormatError(f'Activity timed out; inspect {work.relative_to(ROOT)}/stderr.log') from None
        current = hashlib.sha256()
        with apk.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                current.update(block)
        if split_report:
            split_digest = hashlib.sha256()
            with native_split.open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    split_digest.update(block)
            if split_digest.hexdigest() != split_report['apk_sha256']:
                raise FormatError('Input native split changed during the run')
        if current.hexdigest() != report['apk_sha256']:
            raise FormatError('Input APK changed during the run')
        evidence = {'package': package, 'activity': activity, 'exit_status': result.returncode,
                    'apk_sha256': report['apk_sha256'], 'apk_unchanged': True,
                    'runtime': 'standalone-art', 'execution_mode': execution_mode, 'scope': 'experimental programmatic activity with queued callbacks and software redraw'}
        missing = missing_framework_class(work / 'stderr.log') if result.returncode else None
        method = missing_framework_method(work / 'stderr.log') if result.returncode else None
        if missing:
            evidence['missing_framework_class'] = missing
        if method:
            evidence['missing_framework_method'] = method
        (work / 'result.json').write_text(json.dumps(evidence, indent=2) + '\n')
        if result.returncode:
            detail = f'; missing framework class {missing}' if missing else f'; missing framework method {method}' if method else ''
            raise FormatError(f'Activity failed ({result.returncode}){detail}; inspect {work.relative_to(ROOT)}/stderr.log')
    return 0
