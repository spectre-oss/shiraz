# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import socket
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz import app_worker
from shiraz.apk import FormatError
from shiraz.launch_settings import normalize_settings


class LaunchSettingsTests(unittest.TestCase):
    def test_defaults_and_invalid_settings(self):
        self.assertFalse(normalize_settings()['native_code'])
        for settings in (
            [],
            {'width': True},
            {'height': 0},
            {'width': 8193},
            {'width': 8192, 'height': 8192},
            {'timeout': True},
            {'timeout': 0},
            {'timeout': 2**31},
            {'frames': True},
            {'frames': 601},
            {'snapshot': 'relative.ppm'},
            {'device_profile': '/tmp/../profile.json'},
            {'native_code': 1},
            {'log_level': 'unknown'},
            {'classpath': '/tmp'},
        ):
            with self.subTest(settings=settings), self.assertRaises(FormatError):
                normalize_settings(settings)

    def test_explicit_activity_selection_and_admission_requirements(self):
        from shiraz.runtime import explicit_activity, run_activity

        activity = dict(name='test.app.Login', enabled='true', process='test.app')
        alias = dict(name='test.app.Alias', target=activity['name'], enabled='true')
        manifest = dict(package='test.app', activities=[activity], activity_aliases=[alias])
        self.assertEqual(explicit_activity(manifest, activity['name']), activity['name'])
        self.assertEqual(explicit_activity(manifest, alias['name']), alias['name'])
        for component in (None, '', '../Login', 'test.app.Missing'):
            with self.assertRaises(FormatError):
                explicit_activity(manifest, component)
        activity['enabled'] = 'false'
        with self.assertRaises(FormatError):
            explicit_activity(manifest, activity['name'])
        self.assertEqual(explicit_activity(manifest, alias['name']), alias['name'])
        alias['enabled'] = 'false'
        with self.assertRaises(FormatError):
            explicit_activity(manifest, alias['name'])
        activity['enabled'] = 'true'
        activity['process'] = ':login'
        self.assertEqual(explicit_activity(manifest, activity['name']), activity['name'])
        with self.assertRaisesRegex(FormatError, 'inherited registered'):
            run_activity(None, {}, _activity='test.app.Login')

    def test_worker_owns_and_forwards_private_activity_channel(self):
        report = dict(apk_sha256='a' * 64, manifest=dict(package='test.app'))
        broker_left, broker_right = socket.socketpair()
        launch_left, launch_right = socket.socketpair()
        with broker_left, broker_right, launch_left, launch_right:
            broker_fd, launch_fd = os.dup(broker_left.fileno()), os.dup(launch_left.fileno())
            arguments = [
                'worker',
                '/registry',
                'test.app',
                'a' * 64,
                str(broker_fd),
                '{}',
                'test.app.Login',
                str(launch_fd),
                '10001',
            ]
            with (
                patch.object(app_worker.sys, 'argv', arguments),
                patch.object(app_worker, 'PackageRegistry') as registry,
                patch.object(app_worker, 'inspect_apk', return_value=report),
                patch.object(app_worker, 'run_activity', return_value=0) as run,
            ):
                registry.return_value.launch_apk.return_value = Path('/verified.apk')
                self.assertEqual(app_worker.main(), 0)
                channel, uid = run.call_args.kwargs['_activity_channel']
                self.assertEqual(uid, 10001)
                self.assertEqual(channel.fileno(), -1)
                self.assertEqual(run.call_args.kwargs['_broker_channel'].fileno(), -1)
                self.assertEqual(run.call_args.kwargs['_activity'], 'test.app.Login')

    def test_worker_forwards_settings_and_overrides_inherited_native_flag(self):
        for native in (True, False):
            settings = dict(
                width=480, height=800, timeout=13, log_level='DEBUG', native_code=native
            )
            report = dict(apk_sha256='a' * 64, manifest=dict(package='test.app'))
            left, right = socket.socketpair()
            with (
                left,
                right,
                patch.dict(os.environ, SHIRAZ_GUEST_LOADER='1' if not native else '0'),
            ):
                descriptor = os.dup(left.fileno())
                arguments = [
                    'worker',
                    '/registry',
                    'test.app',
                    'a' * 64,
                    str(descriptor),
                    json.dumps(settings),
                ]
                if native:
                    arguments.append('test.app.Login')
                with (
                    patch.object(app_worker.sys, 'argv', arguments),
                    patch.object(app_worker, 'PackageRegistry') as registry,
                    patch.object(app_worker, 'inspect_apk', return_value=report),
                    patch.object(app_worker, 'run_activity', return_value=7) as run,
                ):
                    registry.return_value.launch_apk.return_value = Path('/verified.apk')
                    self.assertEqual(app_worker.main(), 7)
                    self.assertEqual(os.environ['SHIRAZ_GUEST_LOADER'], '1' if native else '0')
                    options = run.call_args.kwargs
                    for key in ('width', 'height', 'timeout', 'log_level'):
                        self.assertEqual(options[key], settings[key])
                    self.assertEqual(options['_broker_channel'].fileno(), -1)
                    self.assertEqual(options.get('_activity'), 'test.app.Login' if native else None)
