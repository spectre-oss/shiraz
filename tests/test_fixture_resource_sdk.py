# SPDX-License-Identifier: GPL-3.0-only
"""Fixture resource SDK is bounded and independent of its Java/API-probe SDK."""
import json
import stat
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_apk_fixture as fixture


class FixtureResourceSdkTests(unittest.TestCase):
    def archive(self, tools, entries):
        path = tools / fixture.FIXTURE_RESOURCE_SDK[0]
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in entries:
                archive.writestr(name, data)
        return path

    def test_resource_cache_is_verified_and_repaired_without_touching_java_sdk(self):
        with tempfile.TemporaryDirectory() as directory:
            tools = Path(directory)
            archive = self.archive(tools, [('android-34/android.jar', b'resource SDK34')])
            java = tools / 'android.jar'
            java.write_bytes(b'Java SDK35')
            target = tools / 'android-resources-34.jar'
            target.write_bytes(b'corrupt cached resource jar')
            with patch.object(fixture, 'TOOLS', tools), patch.object(fixture, 'fetch') as fetch:
                self.assertEqual(fixture.fixture_resource_jar().read_bytes(), b'resource SDK34')
                self.assertEqual(fixture.fixture_resource_jar(), target)
                fetch.assert_called_with(archive, fixture.FIXTURE_RESOURCE_SDK[1],
                                         fixture.FIXTURE_RESOURCE_SDK[2], 128 * 1024 * 1024)
            self.assertEqual(java.read_bytes(), b'Java SDK35')
            self.assertFalse(list(tools.glob('*.pending')))

    def test_unexpected_or_duplicate_jar_layout_does_not_publish(self):
        cases = [[], [('android-33/android.jar', b'wrong SDK')],
                 [('android-34/android.jar', b'first'), ('extra/android.jar', b'second')]]
        for entries in cases:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as directory:
                tools = Path(directory)
                self.archive(tools, entries)
                with patch.object(fixture, 'TOOLS', tools), patch.object(fixture, 'fetch'):
                    with self.assertRaisesRegex(ValueError, 'archive layout'):
                        fixture.fixture_resource_jar()
                self.assertFalse((tools / 'android-resources-34.jar').exists())
                self.assertFalse(list(tools.glob('*.pending')))

    def test_oversized_or_symlink_jar_does_not_publish(self):
        with tempfile.TemporaryDirectory() as directory:
            tools = Path(directory)
            self.archive(tools, [('android-34/android.jar', b'123456789')])
            with patch.object(fixture, 'TOOLS', tools), patch.object(fixture, 'fetch'), \
                    patch.object(fixture, 'FIXTURE_RESOURCE_JAR_LIMIT', 8):
                with self.assertRaisesRegex(ValueError, 'bounded regular file'):
                    fixture.fixture_resource_jar()
            info = zipfile.ZipInfo('android-34/android.jar')
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            self.archive(tools, [(info, b'target')])
            with patch.object(fixture, 'TOOLS', tools), patch.object(fixture, 'fetch'):
                with self.assertRaisesRegex(ValueError, 'bounded regular file'):
                    fixture.fixture_resource_jar()
            self.assertFalse((tools / 'android-resources-34.jar').exists())

    def test_default_tools_still_install_only_sdk35(self):
        with tempfile.TemporaryDirectory() as directory:
            tools = Path(directory)
            with zipfile.ZipFile(tools / 'platform-35_r02.zip', 'w') as archive:
                archive.writestr('android-35/android.jar', b'Java SDK35')
            with patch.object(fixture, 'TOOLS', tools), patch.object(fixture, 'fetch') as fetch, \
                    patch.object(fixture, 'install_aapt2'):
                fixture.tools()
            self.assertEqual((tools / 'android.jar').read_bytes(), b'Java SDK35')
            self.assertEqual([call.args[0].name for call in fetch.call_args_list], ['platform-35_r02.zip'])
            self.assertNotIn(fixture.FIXTURE_RESOURCE_SDK[0], fixture.DOWNLOADS)

    def test_fixture_links_resources34_but_compiles_java_against35(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / '.deps/android-sdk'
            tools.mkdir(parents=True)
            (root / 'build').mkdir()
            (root / 'tests/apk/assets').mkdir(parents=True)
            (root / 'tests/apk/res').mkdir()
            (root / 'tests/apk/src').mkdir()
            java = tools / 'android.jar'
            resource = tools / 'android-resources-34.jar'
            java.write_bytes(b'Java SDK35')
            resource.write_bytes(b'resource SDK34')
            (tools / 'aapt2').write_bytes(b'compiler fixture')

            def command(arguments, **kwargs):
                if arguments[0] == str(tools / 'aapt2') and arguments[1] == 'link':
                    with zipfile.ZipFile(arguments[arguments.index('-o') + 1], 'w'):
                        pass
                if '--dex' in arguments:
                    Path(next(value.split('=', 1)[1] for value in arguments if value.startswith('--output='))).write_bytes(b'dex fixture')

            with patch.object(fixture, 'ROOT', root), patch.object(fixture, 'TOOLS', tools), \
                    patch.object(fixture, 'tools'), patch.object(fixture, 'fixture_resource_jar', return_value=resource), \
                    patch.object(fixture, 'prepare'), patch.object(fixture, 'host_out_dir', return_value=root / 'host'), \
                    patch.object(fixture.subprocess, 'run', side_effect=command) as run:
                fixture.main()
            link = next(call.args[0] for call in run.call_args_list if 'link' in call.args[0])
            javac = next(call.args[0] for call in run.call_args_list if call.args[0][0] == 'javac')
            self.assertEqual(link[link.index('-I') + 1], str(resource))
            self.assertEqual(link[link.index('--compile-sdk-version-code') + 1], '35')
            self.assertEqual(link[link.index('--compile-sdk-version-name') + 1], '15')
            self.assertEqual(javac[javac.index('-cp') + 1], str(java))
            self.assertEqual(java.read_bytes(), b'Java SDK35')
            metadata = json.loads((root / 'build/apk-fixture/toolchain.json').read_text())
            self.assertEqual((metadata['java_sdk'], metadata['resource_sdk']), (35, 34))
