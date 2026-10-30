# SPDX-License-Identifier: GPL-3.0-only
from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.runtime import ROOT, native_build_environment, selected_device_profile, validate_device_profile


class DeviceProfileTests(unittest.TestCase):
    def test_default_native_identity_is_complete_and_honest(self):
        env = native_build_environment({})
        self.assertEqual(env['SHIRAZ_ANDROID_MANUFACTURER'], 'Shiraz')
        self.assertEqual(env['SHIRAZ_ANDROID_MODEL'], 'Shiraz Linux')
        self.assertEqual(env['SHIRAZ_ANDROID_SERIAL'], 'unknown')
        version = (ROOT / 'VERSION').read_text().strip()
        self.assertEqual(env['SHIRAZ_ANDROID_FINGERPRINT'],
                         'Shiraz/shiraz/linux:8.1/shiraz/' + version + ':host/unsigned')

    def test_explicit_native_identity_overrides_defaults(self):
        profile = validate_device_profile(ROOT / 'config/app.properties')
        env = native_build_environment(profile)
        self.assertEqual(env['SHIRAZ_ANDROID_MANUFACTURER'], profile['build.manufacturer'])
        self.assertEqual(env['SHIRAZ_ANDROID_SECURITY_PATCH'], profile['build.securityPatch'])
        self.assertEqual(env['SHIRAZ_ANDROID_DISPLAY_ID'], profile['build.display'])
        self.assertEqual(env['SHIRAZ_ANDROID_HARDWARE'], profile['build.hardware'])

    def test_selected_profile_is_consistent(self):
        validate_device_profile(ROOT / 'config/app.properties')

    def test_first_run_creates_profile_and_restart_reuses_it(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {'XDG_CONFIG_HOME': directory}, clear=False):
                self.assertIsNone(selected_device_profile('example.other'))
                profile = selected_device_profile('example.app', ROOT / 'config/app.properties')
                first = profile.read_bytes()
                values = validate_device_profile(profile)
                self.assertEqual(profile.parent, Path(directory) / 'shiraz/devices')
                self.assertEqual(profile.stat().st_mode & 0o777, 0o600)
                self.assertNotEqual(values['settings.androidId'], validate_device_profile(ROOT / 'config/app.properties')['settings.androidId'])
                self.assertNotEqual(values['build.serial'], validate_device_profile(ROOT / 'config/app.properties')['build.serial'])
                self.assertEqual(selected_device_profile('example.app'), profile)
                self.assertEqual(profile.read_bytes(), first)
                self.assertIsNone(selected_device_profile('example.other'))

    def test_invalid_stored_profile_is_not_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {'XDG_CONFIG_HOME': directory}, clear=False):
                profile = selected_device_profile('example.app', ROOT / 'config/app.properties')
                profile.write_text('broken=yes\n')
                with self.assertRaises(FormatError):
                    selected_device_profile('example.app', ROOT / 'config/app.properties')

    def test_rejects_contradictory_modem_and_build_identity(self):
        profile = (ROOT / 'config/app.properties').read_text()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'profile.properties'
            path.write_text(profile.replace('telephony.simOperator=\n', 'telephony.simOperator=310260\n'))
            with self.assertRaisesRegex(FormatError, 'without a modem'):
                validate_device_profile(path)
            path.write_text(profile.replace('build.incremental=4944403', 'build.incremental=1'))
            with self.assertRaisesRegex(FormatError, 'display does not match'):
                validate_device_profile(path)
            path.write_text(profile.replace('sensors.available=false', 'sensors.available=true'))
            with self.assertRaisesRegex(FormatError, 'unbridged sensors'):
                validate_device_profile(path)
            path.write_text(profile.replace('settings.screenBrightness=128', 'settings.screenBrightness=300'))
            with self.assertRaisesRegex(FormatError, 'screen brightness'):
                validate_device_profile(path)


if __name__ == '__main__':
    unittest.main()
