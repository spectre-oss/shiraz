# SPDX-License-Identifier: GPL-3.0-only
"""Experimental activity startup using standalone host ART and the local framework."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
import tempfile
import zipfile
from .architecture import native_abi, supports_native_abis
from .apk import FormatError
from .native_payload import extract_native


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
HOST = ROOT / 'context/art_standalone/out/host/linux-x86'
JARS = ('core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'okhttp-hostdex.jar', 'bouncycastle-hostdex.jar', 'wolfssljni-hostdex.jar')
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
            'hasCode': manifest.get('application_has_code')}
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


def run_activity(apk, report, *, width=360, height=640, frames=None, snapshot=None, timeout=None, log_level='INFO', device_profile=None):
    if log_level not in ('VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'):
        raise FormatError('Invalid Android log level')
    if width < 1 or height < 1 or width > 8192 or height > 8192 or width * height > 16 * 1024 * 1024:
        raise FormatError('Activity dimensions exceed supported limits')
    if frames is not None and not 1 <= frames <= 600:
        raise FormatError('frames must be between 1 and 600')
    if timeout is not None and timeout < 1:
        raise FormatError('timeout must be positive')
    execution_mode = os.environ.get('SHIRAZ_ART_MODE', 'interpreter')
    execution_options = art_execution_options(execution_mode)
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
    digitalis = None
    if not supports_native_abis(report['native_abis']) and native_abi() == 'x86_64' and 'arm64-v8a' in report['native_abis']:
        if configured_bridge := os.environ.get('SHIRAZ_DIGITALIS_LIBRARY'):
            digitalis = Path(configured_bridge).resolve()
            if not digitalis.is_file():
                raise FormatError('Configured Digitalis native bridge is missing')
    if not supports_native_abis(report['native_abis']) and digitalis is None:
        raise FormatError('APK has no native ABI candidate for this host (' + str(native_abi()) + ')')
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    framework = ROOT / 'build/shiraz-framework.jar'
    # Optional Apache HTTP compatibility DEX, built separately from licensed
    # upstream jars. Keep APK classes ahead of this fallback library.
    legacy_http = ROOT / 'build/shiraz-legacy-http.jar'
    classpath = str(framework) + ':' + str(apk)
    if legacy_http.is_file():
        classpath += ':' + str(legacy_http)
    framework_resources = ROOT / 'build/framework-res/framework-res.apk'
    required = [ROOT / 'build/libshiraz-capture.so', ROOT / 'build/libshiraz-speech.so', ROOT / '.local/share/espeak-ng-data/phondata', vm, framework, framework_resources, HOST / 'lib64/libwolfssljni.so', ROOT / '.local/lib/libwolfssl.so', ROOT / 'build/libshiraz-bitmap.so', ROOT / 'build/libshiraz-notifications.so', ROOT / 'build/libshiraz-sqlite.so', ROOT / 'build/libshiraz-audio.so', ROOT / 'build/libshiraz-text.so', ROOT / 'build/libshiraz-resources.so', ROOT / 'build/libshiraz-android-process.so', HOST / 'lib64/libandroidfw.so', ROOT / 'build/libshiraz-host.so', ROOT / 'build/libshiraz-jni.so', ROOT / 'build/libandroid.so', ROOT / 'build/libandroid.so.0', ROOT / 'build/libEGL.so.1',
                *(HOST / 'framework' / jar for jar in JARS)]
    required.append(ROOT / 'build/libshiraz-gles1.so')
    required.append(ROOT / 'build/libshiraz-gles2.so')
    required.extend(ROOT / '.local/share/shiraz/fonts' / name for name in (
        'NotoSansArabic-Regular.ttf', 'NotoSansArabic-Bold.ttf', 'NotoSansHebrew-Regular.ttf',
        'NotoSansHebrew-Bold.ttf', 'NotoSans-Regular.ttf', 'NotoSans-Bold.ttf',
        'NotoColorEmoji.ttf', 'LICENSE', 'NotoEmoji-OFL.txt'))
    if execution_mode == 'jit':
        required.append(HOST / 'lib64/libart-compiler.so')
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        raise FormatError('Runtime is not built; run make runtime-art build framework. Missing: ' + ', '.join(missing))
    device_profile = selected_device_profile(package, device_profile or os.environ.get('SHIRAZ_DEVICE_PROFILE_TEMPLATE'))
    apk = Path(apk).resolve()
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
                libraries = extract_native(apk, app_lib, 'x86_64' if native_abi() == 'x86_64' and digitalis is None else 'aarch64')
            except (ValueError, OSError, zipfile.BadZipFile) as exc:
                raise FormatError('Cannot prepare APK native libraries: ' + str(exc)) from exc
            print(f'SHIRAZ_NATIVE_LIBRARIES installed={len(libraries)}', flush=True)
        app_lib.mkdir(parents=True, exist_ok=True)
        metadata = work / 'application.properties'
        facts = application_metadata(manifest, apk, data)
        facts['nativeLibraryDir'] = str(app_lib)
        facts.update(package_timestamps(data, report['apk_sha256']))
        metadata.write_text(properties_text(facts), encoding='utf-8')
        boot = ':'.join(str(HOST / 'framework' / name) for name in JARS)
        env = {**os.environ, 'ANDROID_ROOT': str(HOST), 'ANDROID_DATA': str(work / 'android-data'),
               'XDG_CACHE_HOME': str(work / 'cache'), 'BOOTCLASSPATH': boot,
               'LD_LIBRARY_PATH': str(HOST / 'lib64') + ':' + str(ROOT / '.local/lib') + ':' + str(ROOT / 'build') + ':' + str(app_lib),
               'BIONIC_LD_LIBRARY_PATH': str(app_lib),
               'SHIRAZ_FONT_DIR': str(ROOT / '.local/share/shiraz/fonts'),
               'XDG_DATA_DIRS': str(ROOT / '.local/share') + ':/usr/share'}
        # Host headless/window choices must not select an Android SDL backend.
        for key in ('SDL_VIDEODRIVER', 'SDL_RENDER_DRIVER'):
            if key in env:
                env.setdefault('SHIRAZ_HOST_' + key, env.pop(key))
        if digitalis:
            env['LD_LIBRARY_PATH'] = str(digitalis.parent) + ':' + env['LD_LIBRARY_PATH']
            # Digitalis strips host loader variables and unmangles this prefix
            # when constructing the guest's initial environment.
            env['BERBERIS_GUEST_LD_LIBRARY_PATH'] = str(app_lib)
        profile_values = validate_device_profile(device_profile) if device_profile else {}
        env['SHIRAZ_ANDROID_DISPLAY_ID'] = profile_values.get('build.display', 'Shiraz ' + (ROOT / 'VERSION').read_text(encoding='ascii').strip())
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
        compat = ROOT / 'build/libshiraz-android-process.so'
        # NDK libraries resolve platform symbols through the process-wide scope.
        # ART's JNI-local load of Resources alone does not expose AAssetManager_*
        # to native application libraries such as React Native and Flutter.
        platform_libraries = (compat, ROOT / 'build/libandroid.so', ROOT / 'build/libshiraz-resources.so')
        env['LD_PRELOAD'] = ':'.join(map(str, platform_libraries)) + (':' + env['LD_PRELOAD'] if env.get('LD_PRELOAD') else '')
        command = [str(vm), '-Xbootclasspath:' + boot, '-Xnoimage-dex2oat', *execution_options, '-Xcheck:jni',
                   *(['-XX:NativeBridge=' + str(digitalis.resolve()), '-Xforce-nb-testing', '-Dshiraz.guestAbi=arm64-v8a'] if digitalis else []),
                   '-Xcompiler:' + str(HOST / 'bin/dex2oat'), '-Dlog.tag=' + log_level,
                   *(['-Djavax.net.ssl.trustStore=' + os.environ['SHIRAZ_JAVA_TRUST_STORE']]
                     if os.environ.get('SHIRAZ_JAVA_TRUST_STORE') else []),
                   '-Djava.library.path=' + ':'.join((str(HOST / 'lib64'), str(ROOT / 'build'), str(app_lib))),
                   '-Dshiraz.frameworkResources=' + str(framework_resources),
                   *(['-Dshiraz.deviceProfile=' + str(device_profile)] if device_profile else []),
                   '-Dshiraz.speechData=' + str(ROOT / '.local/share'),
                   '-Djava.io.tmpdir=' + str(work / 'tmp'), '-Duser.home=' + str(work / 'home'),
                   '-cp', classpath, 'shiraz.runtime.ActivityRunner',
                   package, app, activity, str(width), str(height), str(frames if frames is not None else -1),
                   str(data), str(snapshot) if snapshot else '-', str(metadata)]
        if loader := os.environ.get('SHIRAZ_RUNTIME_LOADER'):
            # The private loader must also find its matching C++ runtime. Debian
            # 12's system libstdc++ lacks symbols required by the bundled ART.
            library_path = env.pop('LD_LIBRARY_PATH') + ':' + str(Path(loader).parent)
            preload = env.pop('LD_PRELOAD', '')
            # Scope private glibc and compatibility libraries to ART. Child host
            # executables must use their own loader and libraries.
            command = [loader, '--library-path', library_path,
                       *(['--preload', preload] if preload else []), *command]
        print(f'Launching experimental activity {activity}; logs: {work.relative_to(ROOT)}', flush=True)
        try:
            result = run_with_live_logs(command, data, env, work, timeout)
        except subprocess.TimeoutExpired:
            raise FormatError(f'Activity timed out; inspect {work.relative_to(ROOT)}/stderr.log') from None
        current = hashlib.sha256()
        with apk.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                current.update(block)
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
