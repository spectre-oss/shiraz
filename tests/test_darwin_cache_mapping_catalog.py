# SPDX-License-Identifier: GPL-3.0-only
"""Exercise bounded cache-file metadata reads without a system cache fixture."""

import os
import struct
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import darwin_cache_mapping_catalog as catalog  # noqa: E402


class CacheMappingCatalogTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "fixture"
        self.uuid = bytes(range(16))
        data = bytearray(512)
        data[:7] = b"dyld_v1"
        data[88:104] = self.uuid
        struct.pack_into("<II", data, 16, 128, 2)
        struct.pack_into("<QQQII", data, 128, 0x1000, 32, 0, 5, 5)
        struct.pack_into("<QQQII", data, 160, 0x2000, 64, 256, 3, 3)
        self.path.write_bytes(data)

    def tearDown(self):
        self.directory.cleanup()

    def read_fixture(self, expected=None):
        fd, before = catalog.open_regular(os.fsencode(self.path))
        try:
            return catalog.read_mappings(
                fd, before, catalog.exact(fd, 104, 0), expected or self.uuid
            )
        finally:
            os.close(fd)

    def test_valid_ranges_and_identity(self):
        entries = self.read_fixture()
        st = self.path.stat()
        self.assertEqual(
            entries,
            [(0x1000, 32, 0, st.st_dev, st.st_ino), (0x2000, 64, 256, st.st_dev, st.st_ino)],
        )

    def test_wrong_uuid_and_mapping_bounds_rejected(self):
        with self.assertRaises(ValueError):
            self.read_fixture(b"x" * 16)
        data = bytearray(self.path.read_bytes())
        struct.pack_into("<Q", data, 160 + 16, 500)
        self.path.write_bytes(data)
        with self.assertRaises(ValueError):
            self.read_fixture()

    def test_invalid_table_and_changed_file_rejected(self):
        data = bytearray(self.path.read_bytes())
        struct.pack_into("<II", data, 16, 500, 2)
        self.path.write_bytes(data)
        with self.assertRaises(ValueError):
            self.read_fixture()
        fd, before = catalog.open_regular(os.fsencode(self.path))
        try:
            os.utime(self.path, ns=(before.st_atime_ns, before.st_mtime_ns + 1_000_000_000))
            self.assertFalse(catalog.unchanged(before, os.fstat(fd)))
        finally:
            os.close(fd)

    def test_named_catalog_and_path_identity(self):
        entries = self.read_fixture()
        path = os.fsencode(self.path)
        encoded = catalog.named_catalog(self.uuid, entries, [path, path])
        self.assertEqual(encoded[:8], catalog.NAMED_MAGIC)
        self.assertEqual(encoded[8:24], self.uuid)
        self.assertEqual(struct.unpack_from("<I", encoded, 24)[0], 2)
        self.assertEqual(struct.unpack_from("<QQQQQ", encoded, 28), entries[0])
        self.assertEqual(struct.unpack_from("<I", encoded, 68)[0], len(path))
        self.assertEqual(encoded[72 : 72 + len(path)], path)
        before = self.path.stat()
        catalog.verify_path(path, before)
        replacement = self.path.with_name("replacement")
        replacement.write_bytes(b"different")
        replacement.replace(self.path)
        with self.assertRaises(ValueError):
            catalog.verify_path(path, before)

    def test_named_catalog_bounds(self):
        entries = self.read_fixture()
        for path in (b"relative", b"/bad\0name", b"/" + b"x" * 4096):
            with self.assertRaises(ValueError):
                catalog.named_catalog(self.uuid, entries, [path, path])
        with self.assertRaises(ValueError):
            catalog.named_catalog(self.uuid, entries, [b"/one"])
        with self.assertRaises(ValueError):
            catalog.named_catalog(self.uuid, [entries[0]] * 4096, [b"/" + b"x" * 4095] * 4096)

    def test_symlink_and_directory_rejected(self):
        link = self.path.with_name("link")
        link.symlink_to(self.path)
        with self.assertRaises(OSError):
            catalog.open_regular(os.fsencode(link))
        with self.assertRaises(ValueError):
            catalog.open_regular(os.fsencode(Path(self.directory.name)))


if __name__ == "__main__":
    unittest.main()
