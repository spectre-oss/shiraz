# SPDX-License-Identifier: GPL-3.0-only
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from shiraz.installed import uninstall

class InstalledRemovalTests(unittest.TestCase):
    def test_removal_preserves_app_data_and_audits(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            paths = root/'installed', root/'cache', root/'state'
            target = paths[0]/'app.test'
            target.mkdir(parents=True)
            for name in ('package.properties', 'original.apk', 'application.AppImage'):
                (target/name).write_text('fixture')
            saved = root/'app-data'
            saved.mkdir()
            (saved/'preferences').write_text('retain')
            with patch('shiraz.installed.roots', return_value=paths):
                uninstall('app.test', 'org.fdroid.fdroid')
            self.assertFalse(target.exists())
            self.assertEqual((saved/'preferences').read_text(), 'retain')
            events = [json.loads(line)['event'] for line in (paths[2]/'install-history.jsonl').read_text().splitlines()]
            self.assertEqual(events, ['uninstall-requested', 'uninstalled'])

    def test_rejects_escape_symlink_and_unexpected_contents(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            paths = root/'installed', root/'cache', root/'state'
            paths[0].mkdir()
            outside = root/'outside'
            outside.mkdir()
            (outside/'keep').write_text('safe')
            (paths[0]/'app.link').symlink_to(outside, target_is_directory=True)
            (paths[0]/'app.unknown').mkdir()
            (paths[0]/'app.unknown'/'extra').write_text('keep')
            with patch('shiraz.installed.roots', return_value=paths):
                for package in ('../outside', 'app.link', 'app.unknown'):
                    with self.assertRaises(ValueError):
                        uninstall(package, 'test')
            self.assertEqual((outside/'keep').read_text(), 'safe')
