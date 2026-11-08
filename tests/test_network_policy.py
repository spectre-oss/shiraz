# SPDX-License-Identifier: GPL-3.0-only
"""Manifest-to-runtime policy metadata must preserve explicit application decisions."""
import subprocess
import tempfile
import unittest
from pathlib import Path

from shiraz.manifest import ANDROID, ManifestError, inspect_manifest
from shiraz.runtime import application_metadata


class NetworkPolicyMetadata(unittest.TestCase):
    def manifest(self, attributes=''):
        return inspect_manifest(
            f'<manifest xmlns:android="{ANDROID}" package="policy.test">'
            '<uses-sdk android:minSdkVersion="27" android:targetSdkVersion="35"/>'
            f'<application {attributes}/></manifest>'.encode()
        )

    def test_host_metadata_decisions_do_not_require_libcore(self):
        root = Path(__file__).resolve().parents[1]
        (root / 'build').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / 'build') as directory:
            subprocess.run(['javac', '--release', '8', '-d', directory,
                            str(root / 'java/shiraz/runtime/ManifestNetworkPolicy.java'),
                            str(root / 'tests/java/ManifestNetworkPolicyHostSmoke.java')],
                           check=True, timeout=30)
            subprocess.run(['java', '-cp', directory, 'ManifestNetworkPolicyHostSmoke'],
                           check=True, timeout=10)

    def test_default_is_preserved_as_absent(self):
        values = application_metadata(self.manifest(), Path('/app.apk'), Path('/data'))
        self.assertIsNone(values['usesCleartextTraffic'])
        self.assertIsNone(values['networkSecurityConfig'])
        self.assertEqual(values['targetSdk'], '35')

    def test_explicit_boolean_reaches_the_process(self):
        for value in ('true', 'false'):
            with self.subTest(value=value):
                manifest = self.manifest(f'android:usesCleartextTraffic="{value}"')
                values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
                self.assertEqual(values['usesCleartextTraffic'], value)

    def test_resource_policy_is_retained_for_explicit_support_check(self):
        manifest = self.manifest('android:usesCleartextTraffic="@bool/cleartext" '
                                 'android:networkSecurityConfig="@xml/policy"')
        values = application_metadata(manifest, Path('/app.apk'), Path('/data'))
        self.assertEqual(values['usesCleartextTraffic'], '@bool/cleartext')
        self.assertEqual(values['networkSecurityConfig'], '@xml/policy')

    def test_malformed_boolean_is_rejected(self):
        for value in ('yes', '1', ''):
            with self.subTest(value=value), self.assertRaises(ManifestError):
                self.manifest(f'android:usesCleartextTraffic="{value}"')


if __name__ == '__main__':
    unittest.main()
