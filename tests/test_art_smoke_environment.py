# SPDX-License-Identifier: GPL-3.0-only
"""ART smoke must use the libunwind selected for the bionic bridge build."""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import art_smoke


class ArtSmokeEnvironmentTests(unittest.TestCase):
    def test_linux_retains_only_selected_unwind_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            library = root / 'pinned' / 'libunwind.so.8'
            library.parent.mkdir()
            library.touch()
            with patch.dict(os.environ, {'SHIRAZ_LIBUNWIND_SO8': str(library),
                                         'LD_LIBRARY_PATH': '/unrelated'}, clear=True), \
                    patch.object(art_smoke, 'DARWIN', False):
                env = art_smoke.environment(root / 'work')
            self.assertEqual(env['LD_LIBRARY_PATH'].split(':'),
                             [str(library.parent), str(art_smoke.HOST / 'lib64'),
                              str(art_smoke.ROOT / '.local/lib')])

    def test_linux_rejects_missing_selected_library(self):
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(os.environ, {'SHIRAZ_LIBUNWIND_SO8': '/missing/libunwind.so.8'}, clear=True), \
                patch.object(art_smoke, 'DARWIN', False):
            with self.assertRaisesRegex(ValueError, 'existing absolute'):
                art_smoke.environment(Path(directory))

    def test_darwin_does_not_use_linux_override(self):
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(os.environ, {'SHIRAZ_LIBUNWIND_SO8': '/missing/libunwind.so.8'}, clear=True), \
                patch.object(art_smoke, 'DARWIN', True), \
                patch.object(art_smoke.host_layout, 'darwin_socket_directory', return_value=Path(directory)):
            env = art_smoke.environment(Path(directory))
            self.assertNotIn('LD_LIBRARY_PATH', env)
