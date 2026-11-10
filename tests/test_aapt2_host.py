# SPDX-License-Identifier: GPL-3.0-only
"""Native tool selection must reject the x86 Maven compiler on Linux ARM64."""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_apk_fixture as fixture


class NativeAaptTests(unittest.TestCase):
    def test_real_version_process_on_either_output_stream(self):
        with tempfile.TemporaryDirectory() as directory:
            tool = Path(directory) / 'aapt2'
            for stream in ('stdout', 'stderr'):
                tool.write_text('#!' + sys.executable + '\nimport sys\n'
                                'assert sys.argv[1:] == ["version"]\n'
                                'print("Android Asset Packaging Tool (aapt) 2.19", file=sys.' + stream + ')\n')
                tool.chmod(0o755)
                self.assertEqual(fixture.aapt2_version(tool),
                                 'Android Asset Packaging Tool (aapt) 2.19')

    def test_arm_without_override_refuses_download(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True), \
                patch.object(fixture.platform, 'system', return_value='Linux'), \
                patch.object(fixture.platform, 'machine', return_value='aarch64'), \
                patch.object(fixture, 'fetch') as fetch:
            with self.assertRaisesRegex(ValueError, 'x86_64-only'):
                fixture.install_aapt2(Path(directory))
            fetch.assert_not_called()

    def test_native_override_is_copied_and_wrong_architecture_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'native-aapt2'
            header = bytearray(20)
            header[:6] = b'\x7fELF\x02\x01'
            header[18:20] = (183).to_bytes(2, 'little')
            source.write_bytes(header)
            cache = root / 'cache'
            cache.mkdir()
            with patch.dict(os.environ, {'SHIRAZ_AAPT2': str(source)}), \
                    patch.object(fixture.platform, 'system', return_value='Linux'), \
                    patch.object(fixture.platform, 'machine', return_value='aarch64'), \
                    patch.object(fixture.subprocess, 'run') as run:
                run.return_value.stdout = 'Android Asset Packaging Tool (aapt) 2.19-debian\n'
                target, provenance = fixture.install_aapt2(cache)
                self.assertEqual(target.read_bytes(), source.read_bytes())
                self.assertEqual(provenance['source'], str(source))
                self.assertEqual(len(provenance['binary_sha256']), 64)
                self.assertTrue(target.stat().st_mode & 0o111)
                header[18:20] = (62).to_bytes(2, 'little')
                source.write_bytes(header)
                run.reset_mock()
                with self.assertRaisesRegex(ValueError, 'native'):
                    fixture.install_aapt2(cache)
                run.assert_not_called()

    def test_relative_override_rejected(self):
        with patch.dict(os.environ, {'SHIRAZ_AAPT2': 'aapt2'}):
            with self.assertRaisesRegex(ValueError, 'absolute'):
                fixture.install_aapt2(Path('/unused'))
