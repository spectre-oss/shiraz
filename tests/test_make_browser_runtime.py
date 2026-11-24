# SPDX-License-Identifier: GPL-3.0-only
"""Unit tests for scripts/make_browser_runtime.py."""

from __future__ import annotations

import hashlib
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))

import make_browser_runtime as mbr  # noqa: E402


class MakeBrowserRuntimeTest(unittest.TestCase):
    def _create_fake_appdir(self, root: Path) -> None:
        required = [
            'usr/bin/shiraz-app',
            'usr/art/bin/dalvikvm',
            'usr/lib/ld-linux-aarch64.so.1',
            'usr/share/shiraz/shiraz-framework.jar',
            'usr/share/shiraz/framework-res.apk',
            'usr/lib/libart.so',
        ]
        dropped = [
            'usr/lib/libLLVM.so.20.1',
            'usr/lib/libgallium-25.2.8.so',
            'usr/lib/swrast_dri.so',
            'usr/lib/libpython3.13.so.1.0',
            'usr/share/shiraz/original.apk',
            'usr/share/shiraz/launch.properties',
            'usr/share/shiraz/application.properties',
            'usr/android-lib/libstale.so',
        ]
        for rel in required + dropped:
            target = root / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b'test-payload-' + rel.encode('utf-8'))

    def test_should_drop_rules(self) -> None:
        self.assertTrue(mbr.should_drop('usr/lib/libLLVM.so.20.1'))
        self.assertTrue(mbr.should_drop('usr/lib/libgallium-25.2.8.so'))
        self.assertTrue(mbr.should_drop('usr/lib/swrast_dri.so'))
        self.assertTrue(mbr.should_drop('usr/lib/libpython3.13.so.1.0'))
        self.assertTrue(mbr.should_drop('usr/share/shiraz/original.apk'))
        self.assertTrue(mbr.should_drop('usr/android-lib/libfoo.so'))
        self.assertFalse(mbr.should_drop('usr/lib/libart.so'))
        self.assertFalse(mbr.should_drop('usr/lib/ld-linux-aarch64.so.1'))
        self.assertFalse(mbr.should_drop('usr/share/shiraz/shiraz-framework.jar'))

    def test_stage_tree_strips_bloat_and_preserves_required(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / 'appdir'
            dst = Path(tmp) / 'staged'
            dst.mkdir()
            self._create_fake_appdir(src)
            total = mbr.stage_tree(src, dst)
            self.assertGreater(total, 0)
            self.assertTrue((dst / 'usr/bin/shiraz-app').is_file())
            self.assertTrue((dst / 'usr/lib/libart.so').is_file())
            self.assertFalse((dst / 'usr/lib/libLLVM.so.20.1').exists())
            self.assertFalse((dst / 'usr/lib/libgallium-25.2.8.so').exists())
            self.assertFalse((dst / 'usr/lib/swrast_dri.so').exists())
            self.assertFalse((dst / 'usr/share/shiraz/original.apk').exists())

    def test_main_builds_ext4_and_sha256(self) -> None:
        if not (shutil.which('mke2fs') or shutil.which('mkfs.ext4')):
            self.skipTest('mke2fs not available')
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / 'appdir'
            out = Path(tmp) / 'Shiraz-Browser-Runtime-0.0.0-aarch64.img'
            self._create_fake_appdir(src)
            rc = mbr.main([str(src), str(out), '--format', 'ext4', '--skip-arch-check'])
            self.assertEqual(rc, 0)
            self.assertTrue(out.is_file())
            self.assertGreater(out.stat().st_size, 0)
            sha_file = Path(str(out) + '.sha256')
            digest, name = sha_file.read_text(encoding='utf-8').strip().split(None, 1)
            self.assertEqual(name, out.name)
            self.assertEqual(digest, hashlib.sha256(out.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
