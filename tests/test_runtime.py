# SPDX-License-Identifier: GPL-3.0-only
from copy import deepcopy
import io
import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from shiraz.apk import FormatError
from shiraz.runtime import art_execution_options, ROOT, local_path, run_activity, run_with_live_logs, missing_framework_class, missing_framework_method, properties_text, application_metadata, package_timestamps


class RuntimeTests(unittest.TestCase):
    def test_art_logs_stream_live_and_remain_in_files(self):
        class Sink:
            def __init__(self):
                self.buffer = io.BytesIO()

        stdout, stderr = Sink(), Sink()
        script = ('import sys,time; print("out one",flush=True); '
                  'print("err one",file=sys.stderr,flush=True); time.sleep(.3); '
                  'print("out two",flush=True); print("err two",file=sys.stderr,flush=True)')
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            result = []
            with patch('shiraz.runtime.sys.stdout', stdout), patch('shiraz.runtime.sys.stderr', stderr):
                thread = threading.Thread(target=lambda: result.append(run_with_live_logs(
                    [sys.executable, '-u', '-c', script], work, os.environ.copy(), work, 5)))
                thread.start()
                deadline = time.monotonic() + 3
                while b'out one\n' not in stdout.buffer.getvalue() and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertIn(b'out one\n', stdout.buffer.getvalue())
                self.assertTrue(thread.is_alive())
                thread.join(5)
            self.assertFalse(thread.is_alive())
            self.assertEqual(result[0].returncode, 0)
            self.assertEqual(stdout.buffer.getvalue(), (work / 'stdout.log').read_bytes())
            self.assertEqual(stderr.buffer.getvalue(), (work / 'stderr.log').read_bytes())
            self.assertIn(b'err two\n', stderr.buffer.getvalue())

    def test_receiver_and_provider_intent_metadata(self):
        from shiraz.manifest import inspect_manifest
        manifest = inspect_manifest(b'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="sample.app"><application><meta-data android:name="app.flag" android:value="true"/>
        <receiver android:name=".Alarm" android:enabled="false"><meta-data android:name="value" android:value="42"/></receiver>
        <provider android:name=".Provider" android:authorities="sample.items"><intent-filter><action android:name="sample.LOOKUP"/><data android:mimeType="text/plain"/></intent-filter></provider>
        </application></manifest>''')
        facts = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(facts['application.metadata.0.name'], 'app.flag')
        self.assertEqual(facts['receiver.0.name'], 'sample.app.Alarm')
        self.assertEqual(facts['receiver.0.enabled'], 'false')
        self.assertEqual(facts['receiver.0.metadata.0.value'], 42)
        self.assertEqual(facts['provider.0.filter.0.action.0'], 'sample.LOOKUP')
        self.assertEqual(facts['provider.0.filter.0.mime.0'], 'text/plain')

    def test_launcher_alias_metadata_keeps_resolved_target(self):
        from shiraz.manifest import inspect_manifest
        manifest = inspect_manifest(b'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="sample.app"><application><activity android:name=".Main"/><activity-alias android:name=".Launcher" android:targetActivity=".Main" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity-alias></application></manifest>''')
        facts = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(facts['launcher.count'], 1)
        self.assertEqual(facts['launcher.0.target'], 'sample.app.Main')

    def test_package_timestamps_survive_launches_and_track_apk_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch('shiraz.runtime.time.time_ns', return_value=1_000_000_000):
                first = package_timestamps(directory, 'a' * 64)
            self.assertEqual(first, dict(firstInstallTime=1000, lastUpdateTime=1000))
            with patch('shiraz.runtime.time.time_ns', return_value=2_000_000_000):
                self.assertEqual(package_timestamps(directory, 'a' * 64), first)
                updated = package_timestamps(directory, 'b' * 64)
            self.assertEqual(updated, dict(firstInstallTime=1000, lastUpdateTime=2000))
            with patch('shiraz.runtime.time.time_ns', return_value=500_000_000):
                self.assertEqual(package_timestamps(directory, 'a' * 64)['lastUpdateTime'], 2001)
            record = Path(directory) / '.package-install.json'
            record.write_text('{broken')
            with self.assertRaises(FormatError):
                package_timestamps(directory, 'a' * 64)
            self.assertEqual(record.read_text(), '{broken')

    def setUp(self):
        self.report = {'manifest': {'package': 'sample.app', 'application_class': None, 'min_sdk': '27',
                                   'launcher_activities': [{'target': 'sample.app.Main'}]},
                       'native_abis': [], 'host_native_candidate': True, 'apk_sha256': '0' * 64}

    def test_art_execution_mode_is_explicit_and_validated_before_launch(self):
        self.assertEqual(art_execution_options('interpreter'), ['-Xint', '-Xusejit:false'])
        self.assertEqual(art_execution_options('jit'), ['-Xusejit:true'])
        for mode in ('', 'JIT', '-Xusejit:true', 'aot'):
            with patch.dict('os.environ', {'SHIRAZ_ART_MODE': mode}):
                with patch('shiraz.runtime.subprocess.run', side_effect=AssertionError('must not launch')):
                    with self.assertRaisesRegex(FormatError, 'SHIRAZ_ART_MODE'):
                        run_activity(Path('missing.apk'), self.report)

    def test_invalid_geometry_never_launches(self):
        with patch('shiraz.runtime.subprocess.run', side_effect=AssertionError('must not launch')):
            for values in ({'width': 0}, {'height': -1}, {'width': 8193}, {'width': 8192, 'height': 8192},
                           {'frames': 0}, {'frames': 601}, {'timeout': 0}, {'log_level': 'INVALID'}):
                with self.assertRaises(FormatError):
                    run_activity(Path('missing.apk'), self.report, **values)

    def test_unsupported_profile_and_native_abi(self):
        with patch('shiraz.runtime.subprocess.run', side_effect=AssertionError('must not launch')):
            for minimum in ('28', '35', 'preview', '²'):
                report = deepcopy(self.report); report['manifest']['min_sdk'] = minimum
                with self.assertRaisesRegex(FormatError, 'API profile'):
                    run_activity(Path('missing.apk'), report)
            self.report['native_abis'] = ['armeabi-v7a']
            with self.assertRaisesRegex(FormatError, 'native ABI'):
                run_activity(Path('missing.apk'), self.report)

    def test_native_abi_is_checked_on_current_host_not_cached_report(self):
        for machine, own, foreign in [('x86_64', 'x86_64', 'arm64-v8a'),
                                      ('aarch64', 'arm64-v8a', 'x86_64')]:
            with self.subTest(machine=machine), patch('shiraz.architecture.platform.machine', return_value=machine), patch.object(Path, 'is_file', return_value=False):
                report = deepcopy(self.report)
                report.update(native_abis=[foreign], host_native_candidate=True)
                with self.assertRaisesRegex(FormatError, 'native ABI'):
                    run_activity(Path('missing.apk'), report)
                report.update(native_abis=[own], host_native_candidate=False)
                with self.assertRaisesRegex(FormatError, 'Runtime is not built'):
                    run_activity(Path('missing.apk'), report)

    def test_digitalis_requires_explicit_existing_library(self):
        report = deepcopy(self.report)
        report['native_abis'] = ['arm64-v8a']
        with patch('shiraz.architecture.platform.machine', return_value='x86_64'):
            with patch.dict(os.environ, {'SHIRAZ_DIGITALIS_LIBRARY': '/missing/bridge.so'}):
                with patch.object(Path, 'is_file', return_value=False):
                    with self.assertRaisesRegex(FormatError, 'Digitalis native bridge is missing'):
                        run_activity(Path('missing.apk'), report)
            with tempfile.TemporaryDirectory() as directory:
                bridge = Path(directory) / 'bridge.so'
                bridge.touch()
                with patch.dict(os.environ, {'SHIRAZ_DIGITALIS_LIBRARY': str(bridge)}):
                    with patch.object(Path, 'is_file', return_value=True), patch('shiraz.runtime.selected_device_profile', side_effect=RuntimeError('past ABI gate')):
                        with self.assertRaisesRegex(RuntimeError, 'past ABI gate'):
                            run_activity(Path('missing.apk'), report)

    def test_invalid_component_never_launches(self):
        self.report['manifest']['launcher_activities'][0]['target'] = '../escape'
        with self.assertRaisesRegex(FormatError, 'component'):
            run_activity(Path('missing.apk'), self.report)

    def test_disabled_application_never_launches(self):
        self.report['manifest']['application_enabled'] = 'false'
        with patch('shiraz.runtime.subprocess.run', side_effect=AssertionError('must not launch')):
            with self.assertRaisesRegex(FormatError, 'disabled'):
                run_activity(Path('missing.apk'), self.report)

    def test_properties_preserve_values_without_injecting_keys(self):
        self.assertEqual(properties_text({'label': ' café\npackage=other\\end\r\t\f\x01', 'absent': None}),
                         'label=\\ café\\npackage\\=other\\\\end\\r\\t\\f\\u0001\n')
        values = application_metadata({**self.report['manifest'], 'unrelated': 'not forwarded'}, Path('/app.apk'), Path('/data'))
        self.assertNotIn('unrelated', values)
        self.assertEqual(values['sourceDir'], '/app.apk')
        self.assertEqual(values['targetSdk'], '27')
        self.assertEqual(values['versionCode'], '0')

    def test_package_manifest_identity_metadata(self):
        manifest = {**self.report['manifest'], 'shared_user_id': 'test.shared',
                    'shared_user_label': 0x7f010001, 'revision_code': 3, 'install_location': 2,
                    'application_icon': 0x7f010002, 'application_logo': 0x7f010003, 'application_banner': 0x7f010004}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['sharedUserId'], 'test.shared')
        self.assertEqual(values['sharedUserLabel'], 0x7f010001)
        self.assertEqual(values['baseRevisionCode'], 3)
        self.assertEqual(values['installLocation'], 2)
        self.assertEqual(values['icon'], 0x7f010002)
        self.assertEqual(values['logo'], 0x7f010003)
        self.assertEqual(values['banner'], 0x7f010004)

    def test_permission_metadata_is_bounded(self):
        manifest = {**self.report['manifest'], 'permissions': ['android.permission.ACCESS_FINE_LOCATION']}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['permission.count'], 1)
        self.assertEqual(values['permission.0'], 'android.permission.ACCESS_FINE_LOCATION')
        with self.assertRaises(FormatError):
            application_metadata({**manifest, 'permissions': ['x'] * 1025}, Path('/app.apk'), Path('/data'))

    def test_own_signature_permission_scope(self):
        manifest = {**self.report['manifest'], 'package': 'sample.app', 'permissions': ['sample.app.LOCAL', 'android.permission.CAMERA', 'other.app.LOCAL'], 'defined_permissions': [{'name': 'sample.app.LOCAL', 'protection': 2}, {'name': 'android.permission.CAMERA', 'protection': 2}, {'name': 'other.app.LOCAL', 'protection': 2}, {'name': 'sample.app.UNREQUESTED', 'protection': 2}]}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['ownSignaturePermission.count'], 1)
        self.assertEqual(values['ownSignaturePermission.0'], 'sample.app.LOCAL')
        manifest['defined_permissions'][0]['protection'] = 1
        self.assertEqual(application_metadata(manifest, Path('/app.apk'), Path('/data'))['ownSignaturePermission.count'], 0)

    def test_activity_configuration_facts_are_forwarded(self):
        manifest = {**self.report['manifest'], 'activities': [{'name': 'sample.app.Main', 'config_changes': 512, 'screen_orientation': -1, 'launch_mode': 2, 'theme': 2131886080, 'private': 'excluded'}]}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['activity.0.config_changes'], 512)
        self.assertEqual(values['activity.0.screen_orientation'], -1)
        self.assertEqual(values['activity.0.launch_mode'], 2)
        self.assertEqual(values['activity.0.theme'], 2131886080)
        self.assertNotIn('activity.0.private', values)
        with self.assertRaises(FormatError):
            application_metadata({**manifest, 'activities': manifest['activities'] * 257}, Path('/app.apk'), Path('/data'))

    def test_activity_metadata_forwarding_and_bound(self):
        item = {'name': 'typed', 'kind': 'string', 'value': 'true', 'private': 'excluded'}
        activity = {'name': 'sample.app.Main', 'metadata': [item], 'label': 'Activity café', 'labelRes': 2130771968}
        manifest = {**self.report['manifest'], 'activities': [activity]}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['activity.0.metadata.count'], 1)
        self.assertEqual(values['activity.0.label'], 'Activity café')
        self.assertEqual(values['activity.0.labelRes'], 2130771968)
        self.assertEqual(values['activity.0.metadata.0.value'], 'true')
        self.assertNotIn('activity.0.metadata.0.private', values)
        activity['metadata'] = [item] * 129
        with self.assertRaises(FormatError):
            application_metadata(manifest, Path('/app.apk'), Path('/data'))

    def test_only_service_dispatch_facts_are_forwarded(self):
        manifest = {**self.report['manifest'], 'services': [{'name': 'sample.app.Worker', 'enabled': 'false', 'process': ':other', 'isolated': 'true', 'private': 'not forwarded'}]}
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['service.count'], '1')
        self.assertEqual(values['service.0.name'], 'sample.app.Worker')
        self.assertEqual(values['service.0.process'], ':other')
        self.assertNotIn('service.0.private', values)
        with self.assertRaises(FormatError):
            application_metadata({**manifest, 'services': manifest['services'] * 257}, Path('/app.apk'), Path('/data'))

    def test_output_path_stays_local(self):
        with tempfile.TemporaryDirectory() as outside:
            with self.assertRaises(FormatError):
                local_path(Path(outside) / 'frame.ppm', 'build')
            with tempfile.TemporaryDirectory(dir=ROOT / 'build') as inside:
                link = Path(inside) / 'escape'
                link.symlink_to(outside, target_is_directory=True)
                with self.assertRaises(FormatError):
                    local_path(link / 'frame.ppm', 'build')
        self.assertEqual(local_path(ROOT / 'build/frame.ppm', 'build'), ROOT / 'build/frame.ppm')


class FailureDiagnosticTests(unittest.TestCase):
    def test_framework_identifier_only(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'stderr.log'
            path.write_text('java.lang.NoClassDefFoundError: Failed resolution of: Landroid/os/Process;\n')
            self.assertEqual(missing_framework_class(path), 'android.os.Process')
            path.write_text('Caused by: java.lang.ClassNotFoundException: android.util.Log\n')
            self.assertEqual(missing_framework_class(path), 'android.util.Log')
            path.write_text('java.lang.RuntimeException: app-specific message\n')
            self.assertIsNone(missing_framework_class(path))
    def test_terminal_failure_excludes_caught_and_suppressed_errors(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'stderr.log'
            path.write_text('E/Fonts: java.lang.ClassNotFoundException: android.graphics.FontFamily\n'
                            'java.lang.IllegalStateException: Canvas has no bitmap\n'
                            '\tat android.graphics.Canvas.draw(Canvas.java:1)\n'
                            '\tSuppressed: java.lang.NoSuchMethodError: No virtual method cleanup()V in class Landroid/view/View;\n'
                            '\t\tat app.Cleanup.run(Cleanup.java:2)\n')
            self.assertIsNone(missing_framework_class(path))
            self.assertIsNone(missing_framework_method(path))
            with path.open('a') as stream:
                stream.write('Caused by: java.lang.NoClassDefFoundError: Failed resolution of: Landroid/current/Required;\n')
            self.assertEqual(missing_framework_class(path), 'android.current.Required')

    def test_dedicated_process_marker_excludes_earlier_caught_warning(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'stderr.log'
            path.write_text('E/Fonts: java.lang.ClassNotFoundException: android.graphics.FontFamily\n'
                            'SHIRAZ_RUNTIME_FATAL\njava.lang.Throwable: fatal callback\n'
                            '\tat app.Main.run(Main.java:1)\n')
            self.assertIsNone(missing_framework_class(path))
            self.assertIsNone(missing_framework_method(path))

    def test_log_tail_bound(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'stderr.log'
            path.write_text('java.lang.ClassNotFoundException: android.old.Unrelated\n' + 'x' * 70000 +
                            '\njava.lang.ClassNotFoundException: android.current.Required\n')
            self.assertEqual(missing_framework_class(path), 'android.current.Required')

    def test_missing_method_identifier(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'stderr.log'
            path.write_text('java.lang.NoSuchMethodError: No virtual method getPackageManager()Landroid/content/pm/PackageManager; in class Landroid/content/Context; or its super classes\n')
            self.assertEqual(missing_framework_method(path), 'android.content.Context->getPackageManager()Landroid/content/pm/PackageManager;')
            path.write_text('java.lang.NoSuchMethodError: app-specific text\n')
            self.assertIsNone(missing_framework_method(path))
