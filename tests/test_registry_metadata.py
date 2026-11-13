# SPDX-License-Identifier: GPL-3.0-only
"""Package facts the macOS launcher stores next to each library APK."""

import unittest
from pathlib import Path

from shiraz.apk import FormatError
from shiraz.runtime import ROOT, properties_text, registry_metadata


def report(package='org.example.app', activity='org.example.app.Main'):
    manifest = {
        'package': package,
        'version_code': '7',
        'version_name': '1.0',
        'min_sdk': '21',
        'launcher_activities': [{'target': activity}] if activity else [],
    }
    return {'manifest': manifest, 'apk_sha256': 'ab' * 32}


class RegistryMetadata(unittest.TestCase):
    def test_paths_match_run_activity_and_carry_installer(self):
        facts = registry_metadata(
            Path('/library/apps/org.example.app/app.apk'), report(), 'org.fdroid.fdroid'
        )
        self.assertEqual(facts['dataDir'], str(ROOT / 'data/apps/org.example.app'))
        self.assertEqual(
            facts['sourceDir'], str(Path('/library/apps/org.example.app/app.apk').resolve())
        )
        self.assertEqual(
            (facts['launchActivity'], facts['installer'], facts['versionCode']),
            ('org.example.app.Main', 'org.fdroid.fdroid', '7'),
        )
        text = properties_text(facts)
        self.assertIn('package=org.example.app\n', text)
        self.assertNotIn(
            'installer=', properties_text(registry_metadata(Path('/a.apk'), report(), None))
        )

    def test_app_without_launcher_has_no_launch_activity(self):
        self.assertIsNone(
            registry_metadata(Path('/a.apk'), report(activity=None))['launchActivity']
        )

    def test_rejects_hostile_names(self):
        for bad in (report(package='../evil'), report(activity='a/b')):
            with self.assertRaises(FormatError):
                registry_metadata(Path('/a.apk'), bad)


if __name__ == '__main__':
    unittest.main()
