# SPDX-License-Identifier: GPL-3.0-only
"""Unit tests for scripts/bootstrap_windows.py (Windows ART build plan and toolchain checks)."""

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    'bootstrap_windows', ROOT / 'scripts/bootstrap_windows.py'
)
bw = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bw)


class BootstrapWindowsTests(unittest.TestCase):
    def test_build_plan_for_x64_and_arm64(self):
        x64 = bw.build_plan('AMD64')
        self.assertEqual(x64['target_arch'], 'x86_64')
        self.assertEqual(x64['android_abi'], 'x86_64')
        self.assertEqual(x64['page_size'], 4096)
        self.assertEqual(x64['allocation_granularity'], 65536)
        self.assertIn('libart.dll', x64['art_targets'])
        self.assertIn('dalvikvm.exe', x64['art_targets'])
        for dep, meta in x64['dependencies'].items():
            self.assertEqual(len(meta['sha256']), 64, dep)
            self.assertTrue(meta['url'].startswith('https://'), dep)

        arm64 = bw.build_plan('ARM64')
        self.assertEqual(arm64['target_arch'], 'aarch64')
        self.assertEqual(arm64['android_abi'], 'arm64-v8a')

        with self.assertRaises(ValueError):
            bw.build_plan('riscv64')

    def test_toolchain_detection_and_sha256_verification(self):
        detected = bw.detect_toolchain(which_fn=lambda name: f'/ucrt64/bin/{name}')
        self.assertEqual(detected['missing_tools'], [])

        missing = bw.detect_toolchain(which_fn=lambda name: None)
        self.assertGreaterEqual(len(missing['missing_tools']), 4)
        self.assertFalse(missing['ready_for_build'])

        with tempfile.TemporaryDirectory() as tmp:
            sample = Path(tmp) / 'sample.bin'
            payload = b'shiraz-win32-art-pin-check'
            sample.write_bytes(payload)
            expected = hashlib.sha256(payload).hexdigest()
            bw.verify_sha256(sample, expected)
            with self.assertRaisesRegex(RuntimeError, 'SHA-256 mismatch'):
                bw.verify_sha256(sample, '0' * 64)

    def test_plan_cli_writes_valid_json_and_art_fails_closed_off_windows(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'plan.json'
            self.assertEqual(bw.main(['plan', '--arch', 'x86_64', '--output', str(out)]), 0)
            loaded = json.loads(out.read_text(encoding='utf-8'))
            self.assertEqual(loaded['host_tag'], 'windows-x86')
            self.assertEqual(len(loaded['adaptations']), 5)
        if not bw.host_is_windows():
            self.assertEqual(bw.main(['art']), 2)


if __name__ == '__main__':
    unittest.main()
