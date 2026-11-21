# SPDX-License-Identifier: GPL-3.0-only
"""Alternate Skia checkouts stay inside the project's dependency directory."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    'bootstrap_skia_source_test', Path(__file__).resolve().parents[1] / 'scripts/bootstrap_skia.py'
)
bootstrap = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap)


class SkiaSourceTests(unittest.TestCase):
    def test_default_and_alternate_checkouts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            self.assertEqual(bootstrap.source_directory(root), root / '.deps/skia')
            alternate = root / '.deps/skia-clean'
            self.assertEqual(bootstrap.source_directory(root, '.deps/skia-clean'), alternate)
            self.assertEqual(bootstrap.source_directory(root, str(alternate)), alternate)

    def test_rejects_parent_and_external_sources(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            for configured in ('.deps', '.deps/../other', '../skia', '/tmp/external-skia'):
                with self.subTest(configured=configured), self.assertRaises(ValueError):
                    bootstrap.source_directory(root, configured)

    def test_rejects_symlink_escape(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            (root / '.deps').mkdir()
            (root / 'outside').mkdir()
            (root / '.deps/link').symlink_to(root / 'outside', target_is_directory=True)
            with self.assertRaises(ValueError):
                bootstrap.source_directory(root, '.deps/link/skia')
