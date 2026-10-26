# SPDX-License-Identifier: GPL-3.0-only
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from shiraz import installed
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))


class InstalledTests(unittest.TestCase):
    def test_xdg_defaults_absolute_and_relative(self):
        with patch.dict(os.environ, {'HOME': '/home/user'}, clear=True):
            self.assertEqual(installed.roots(), (Path('/home/user/.local/share/shiraz/installed'),
                Path('/home/user/.cache/shiraz/install'), Path('/home/user/.local/state/shiraz')))
            with patch.dict(os.environ, {'XDG_DATA_HOME': 'relative', 'XDG_STATE_HOME': '/state'}):
                self.assertEqual(installed.roots()[0], Path('/home/user/.local/share/shiraz/installed'))
                self.assertEqual(installed.roots()[2], Path('/state/shiraz'))
            with patch.dict(os.environ, {'XDG_CACHE_HOME': '/per-app', 'SHIRAZ_HOST_XDG_CACHE_HOME': '/user-cache'}):
                self.assertEqual(installed.roots()[1], Path('/user-cache/shiraz/install'))

    def test_publish_only_after_build_and_preserve_existing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            template = root/'template'; share = template/'usr/share/shiraz'
            share.mkdir(parents=True); (share/'VERSION').write_text('1.2.3')
            (share/'installer').mkdir(); (share/'installer/fallback-icon.svg').write_text('<svg/>')
            (share/'original.apk').write_bytes(b'parent apk')
            (template/'parent.desktop').write_text('parent')
            (template/'AppRun').write_text('launcher')
            apk = root/'source.apk'; apk.write_bytes(b'unchanged input')
            result = root/'result'
            report = dict(apk_sha256='a'*64, native_abis=[], manifest=dict(package='example.app',
                launcher_activities=[dict(target='example.app.Main')], min_sdk='21'))
            def native(apk, destination, architecture):
                Path(destination).mkdir(parents=True)
                return []
            def builder(command, **kwargs):
                tree = Path(command[-2])
                self.assertEqual((tree/'usr/share/shiraz/original.apk').read_bytes(), apk.read_bytes())
                self.assertFalse((tree/'parent.desktop').exists())
                self.assertIn('aot=false', (tree/'usr/share/shiraz/launch.properties').read_text())
                Path(command[-1]).write_bytes(b'appimage')
            env = dict(HOME=str(root), XDG_DATA_HOME=str(root/'data'),
                XDG_CACHE_HOME=str(root/'cache'), XDG_STATE_HOME=str(root/'state'))
            with patch.dict(os.environ, env, clear=True), patch.object(installed, 'inspect_apk', return_value=report), patch('package_native.extract_native', side_effect=native), patch.object(installed.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'build')):
                with self.assertRaises(subprocess.CalledProcessError):
                    installed.install(template, apk, 'example.app', 'store.app', result)
                self.assertFalse((root/'data/shiraz/installed/example.app').exists())
                self.assertFalse(result.exists())
            with patch.dict(os.environ, env, clear=True), patch.object(installed, 'inspect_apk', return_value=report), patch('package_native.extract_native', side_effect=native), patch.object(installed.subprocess, 'run', side_effect=builder):
                installed.install(template, apk, 'example.app', 'store.app', result)
                destination = root/'data/shiraz/installed/example.app'
                self.assertEqual((destination/'application.AppImage').read_bytes(), b'appimage')
                self.assertEqual((destination/'original.apk').read_bytes(), apk.read_bytes())
                self.assertIn('installer=store.app', (destination/'package.properties').read_text())
                with self.assertRaisesRegex(installed.FormatError, 'new installations'):
                    installed.install(template, apk, 'example.app', 'store.app', result)
                with self.assertRaisesRegex(installed.FormatError, 'differs'):
                    installed.install(template, apk, 'other.app', 'store.app', result)
                self.assertEqual((destination/'application.AppImage').read_bytes(), b'appimage')
            events = [json.loads(line)['event'] for line in (root/'state/shiraz/install-history.jsonl').read_text().splitlines()]
            self.assertEqual(events.count('installed'), 1)
            self.assertEqual(events.count('failed'), 3)
            self.assertEqual(list((root/'cache/shiraz/install').iterdir()), [])
