# SPDX-License-Identifier: GPL-3.0-only
import tempfile
import unittest
from pathlib import Path

from shiraz.apk import FormatError
from shiraz.runtime import package_session


class LaunchAdmissionTests(unittest.TestCase):
    def test_process_and_generation_exclusion(self):
        with tempfile.TemporaryDirectory() as folder:
            data = Path(folder)
            with package_session(data, 'a' * 64, 'test.app') as first:
                with package_session(data, 'a' * 64, 'test.app:worker') as second:
                    self.assertEqual(first, second)
                    for process, digest, message in (
                        ('test.app', 'a' * 64, 'process already'),
                        ('test.app:new', 'b' * 64, 'generation'),
                        (None, 'a' * 64, 'package already'),
                    ):
                        with self.assertRaisesRegex(FormatError, message):
                            with package_session(data, digest, process):
                                self.fail('Conflicting launch admitted')
            with package_session(data, 'b' * 64, 'test.app') as updated:
                self.assertEqual(first['firstInstallTime'], updated['firstInstallTime'])
                self.assertGreater(updated['lastUpdateTime'], first['lastUpdateTime'])
            with package_session(data, 'b' * 64):
                with self.assertRaisesRegex(FormatError, 'exclusive'):
                    with package_session(data, 'b' * 64, 'test.app:worker'):
                        self.fail('Unregistered session shared')

    def test_failed_prepare_releases_locks(self):
        with tempfile.TemporaryDirectory() as folder:
            data = Path(folder)
            (data / '.package-install.json').write_text('{}')
            with self.assertRaises(FormatError):
                with package_session(data, 'a' * 64, 'test.app'):
                    self.fail('Malformed timestamps accepted')
            (data / '.package-install.json').unlink()
            with package_session(data, 'a' * 64, 'test.app'):
                pass
