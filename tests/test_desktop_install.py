# SPDX-License-Identifier: GPL-3.0-only
import io
import json
import os
import plistlib
import stat
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

from shiraz import cli
from shiraz.desktop_install import install_desktop_app, uninstall_desktop_app

_MINIMAL_PNG = (
    b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01'
    b'\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01'
    b'\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82'
)


class DesktopInstallTests(unittest.TestCase):
    def _make_fixtures(self, root: Path):
        apk = root / 'Sample App.apk'
        apk.write_bytes(b'PK\x05\x06' + b'\x00' * 18)
        icon = root / 'icon.png'
        icon.write_bytes(_MINIMAL_PNG)
        return apk, icon

    def test_linux_desktop_install_and_uninstall(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-desk-linux-') as tmp:
            root = Path(tmp)
            apk, icon = self._make_fixtures(root)
            apps_dir = root / 'share' / 'applications'

            result = install_desktop_app(
                apk,
                'org.example.map',
                'Example Map',
                icon_path=icon,
                sandbox=True,
                allow_network=False,
                applications_dir=apps_dir,
                python_executable='/usr/bin/python3',
                system='Linux',
            )
            self.assertEqual(result.os_name, 'linux')
            self.assertTrue(result.launcher_path.is_file())
            self.assertEqual(result.launcher_path.name, 'org.example.map.Shiraz.desktop')
            self.assertIsNotNone(result.icon_path)
            self.assertTrue(result.icon_path.is_file())

            text = result.launcher_path.read_text(encoding='utf-8')
            self.assertIn('[Desktop Entry]', text)
            self.assertIn('Name=Example Map', text)
            self.assertIn('X-Purism-FormFactor=Workstation;Mobile;', text)
            self.assertIn('X-Shiraz-Package=org.example.map', text)
            self.assertIn('--sandbox', text)
            self.assertIn('--no-network', text)

            # Unrelated desktop entry must stay untouched on uninstall.
            unrelated = apps_dir / 'unrelated.desktop'
            unrelated.write_text('[Desktop Entry]\nName=Other\n', encoding='utf-8')

            removed = uninstall_desktop_app(
                'org.example.map', applications_dir=apps_dir, system='Linux'
            )
            self.assertIn(result.launcher_path, removed)
            self.assertIn(result.icon_path, removed)
            self.assertFalse(result.launcher_path.exists())
            self.assertFalse(result.icon_path.exists())
            self.assertTrue(unrelated.exists())

    def test_macos_wrapper_app_bundle_install_and_uninstall(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-desk-darwin-') as tmp:
            root = Path(tmp)
            apk, icon = self._make_fixtures(root)
            apps_dir = root / 'Applications'

            result = install_desktop_app(
                apk,
                'org.example.notes',
                'My Notes',
                icon_path=icon,
                sandbox=True,
                allow_network=False,
                applications_dir=apps_dir,
                python_executable='/usr/bin/python3',
                system='Darwin',
            )
            self.assertEqual(result.os_name, 'darwin')
            self.assertEqual(result.launcher_path, apps_dir / 'My Notes.app')
            plist_path = result.launcher_path / 'Contents' / 'Info.plist'
            launch_script = result.launcher_path / 'Contents' / 'MacOS' / 'launch'
            self.assertTrue(plist_path.is_file())
            self.assertTrue(launch_script.is_file())
            if sys.platform != 'win32':
                mode = stat.S_IMODE(os.stat(launch_script).st_mode)
                self.assertTrue(mode & stat.S_IXUSR)

            info = plistlib.loads(plist_path.read_bytes())
            self.assertEqual(info['CFBundleIdentifier'], 'org.shiraz.app.org.example.notes')
            self.assertEqual(info['CFBundleDisplayName'], 'My Notes')
            self.assertEqual(info['CFBundleExecutable'], 'launch')
            self.assertTrue(info['ShirazManaged'])
            self.assertEqual(info['ShirazPackageName'], 'org.example.notes')
            self.assertIn('NSCameraUsageDescription', info)
            self.assertIn('NSLocationUsageDescription', info)

            script = launch_script.read_text(encoding='utf-8')
            self.assertTrue(script.startswith('#!/bin/sh\n'))
            self.assertIn('--sandbox', script)
            self.assertIn('--no-network', script)

            # Unmanaged .app bundle in ~/Applications must never be deleted.
            other_app = apps_dir / 'Safari.app' / 'Contents'
            other_app.mkdir(parents=True)
            (other_app / 'Info.plist').write_bytes(
                plistlib.dumps({'CFBundleIdentifier': 'com.apple.Safari'})
            )

            removed = uninstall_desktop_app(
                'org.example.notes', applications_dir=apps_dir, system='Darwin'
            )
            self.assertEqual(removed, [result.launcher_path])
            self.assertFalse(result.launcher_path.exists())
            self.assertTrue((apps_dir / 'Safari.app').exists())

    def test_windows_start_menu_install_and_uninstall(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-desk-win-') as tmp:
            root = Path(tmp)
            apk, icon = self._make_fixtures(root)
            apps_dir = root / 'StartMenu' / 'Shiraz'

            result = install_desktop_app(
                apk,
                'org.example.reader',
                'Book Reader',
                icon_path=icon,
                sandbox=True,
                allow_network=True,
                applications_dir=apps_dir,
                python_executable='C:\\Python312\\python.exe',
                system='Windows',
            )
            self.assertEqual(result.os_name, 'windows')
            self.assertEqual(result.launcher_path, apps_dir / 'Book Reader.cmd')
            self.assertTrue(result.launcher_path.is_file())
            self.assertIsNotNone(result.metadata_path)
            self.assertTrue(result.metadata_path.is_file())

            cmd_text = result.launcher_path.read_text(encoding='utf-8')
            self.assertIn('@echo off', cmd_text)
            self.assertIn('--sandbox', cmd_text)
            self.assertNotIn('--no-network', cmd_text)

            removed = uninstall_desktop_app(
                'org.example.reader', applications_dir=apps_dir, system='Windows'
            )
            self.assertIn(result.launcher_path, removed)
            self.assertIn(result.metadata_path, removed)
            self.assertFalse(result.launcher_path.exists())
            self.assertFalse(result.metadata_path.exists())

    def test_validation_rejects_bad_package_and_invalid_network_flags(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-desk-val-') as tmp:
            root = Path(tmp)
            apk, _ = self._make_fixtures(root)
            with self.assertRaises(ValueError):
                install_desktop_app(apk, '../escape', 'Bad', applications_dir=root / 'apps')
            with self.assertRaises(ValueError):
                install_desktop_app(
                    apk,
                    'org.example.ok',
                    'Ok',
                    sandbox=False,
                    allow_network=False,
                    applications_dir=root / 'apps',
                )
            with self.assertRaises(FileNotFoundError):
                install_desktop_app(
                    root / 'missing.apk',
                    'org.example.ok',
                    'Ok',
                    applications_dir=root / 'apps',
                )

    def test_cli_install_and_uninstall_desktop_round_trip(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-desk-cli-') as tmp:
            root = Path(tmp)
            apk, icon = self._make_fixtures(root)
            apps_dir = root / 'apps'

            out = io.StringIO()
            with (
                mock.patch.object(cli, 'inspect_apk', return_value={'package': 'org.example.cli'}),
                mock.patch.object(cli, 'resolve_label', return_value='CLI App'),
                redirect_stdout(out),
            ):
                rc = cli.main(
                    [
                        'install-desktop',
                        str(apk),
                        '--icon',
                        str(icon),
                        '--sandbox',
                        '--no-network',
                        '--applications-dir',
                        str(apps_dir),
                    ]
                )
            self.assertEqual(rc, 0)
            installed = json.loads(out.getvalue())
            self.assertEqual(installed['package'], 'org.example.cli')
            self.assertEqual(installed['label'], 'CLI App')
            self.assertTrue(Path(installed['launcher']).exists())

            out2 = io.StringIO()
            with redirect_stdout(out2):
                rc2 = cli.main(
                    [
                        'uninstall-desktop',
                        'org.example.cli',
                        '--applications-dir',
                        str(apps_dir),
                    ]
                )
            self.assertEqual(rc2, 0)
            uninstalled = json.loads(out2.getvalue())
            self.assertEqual(uninstalled['package'], 'org.example.cli')
            self.assertTrue(len(uninstalled['removed']) >= 1)
            self.assertFalse(Path(installed['launcher']).exists())

            # Default install enables sandboxing; --no-sandbox opts out explicitly.
            default_res = install_desktop_app(
                apk,
                'org.example.defaultsb',
                'Default SB',
                applications_dir=apps_dir,
                python_executable='/usr/bin/python3',
                system='Linux',
            )
            self.assertIn('--sandbox', default_res.launcher_path.read_text(encoding='utf-8'))
            optout_res = install_desktop_app(
                apk,
                'org.example.optout',
                'OptOut SB',
                sandbox=False,
                applications_dir=apps_dir,
                python_executable='/usr/bin/python3',
                system='Linux',
            )
            self.assertIn('--no-sandbox', optout_res.launcher_path.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
