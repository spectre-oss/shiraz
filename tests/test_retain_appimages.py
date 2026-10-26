# SPDX-License-Identifier: GPL-3.0-only
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
from retain_appimages import prune


class RetentionTests(unittest.TestCase):
    def test_numeric_versions_architectures_checksums_and_unrelated_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for app in ('Firefox', 'F-Droid'):
                for version in (1, 2, 9, 10):
                    for arch in ('x86_64', 'aarch64'):
                        image = root/f'{app}-Shiraz-0.1.{version}-{arch}.AppImage'
                        image.write_bytes(b'image')
                        image.with_suffix('.AppImage.sha256').write_text('checksum')
            other = root/'notes.txt'; other.write_text('keep')
            link = root/'Other-Shiraz-0.1.1-x86_64.AppImage'; link.symlink_to(other)
            names, size = prune(root)
            self.assertEqual(len(names), 8)
            self.assertGreater(size, 0)
            self.assertTrue(all((root/name).exists() for name in names))
            self.assertEqual(prune(root, apply=True), (names, size))
            self.assertTrue(all(not (root/name).exists() for name in names))
            self.assertTrue(other.exists() and link.is_symlink())
            self.assertEqual(prune(root), ([], 0))
            self.assertTrue((root/'Firefox-Shiraz-0.1.10-x86_64.AppImage').exists())
