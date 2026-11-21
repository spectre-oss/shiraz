# SPDX-License-Identifier: GPL-3.0-only
"""Safety checks for the isolated guest math source bootstrap."""

import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import bootstrap_guest_mpfr as bootstrap  # noqa: E402


class BootstrapSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.original_out = bootstrap.OUT
        self.addCleanup(setattr, bootstrap, "OUT", self.original_out)
        bootstrap.OUT = Path(self.temporary.name).resolve() / "out"
        bootstrap.OUT.mkdir()
        self.archive = Path(self.temporary.name).resolve() / "source.tar.xz"

    def make_archive(self, name, contents=b"test", kind=tarfile.REGTYPE):
        with tarfile.open(self.archive, "w:xz") as archive:
            member = tarfile.TarInfo(name)
            member.type = kind
            member.size = len(contents) if kind == tarfile.REGTYPE else 0
            member.mtime = 1234567890
            member.mode = 0o755
            if kind == tarfile.SYMTYPE:
                member.linkname = "/tmp"
            archive.addfile(member, io.BytesIO(contents) if member.size else None)

    def test_extract_detects_mutation_and_preserves_metadata(self):
        self.make_archive("pkg/configure")
        bootstrap.extract(self.archive, "pkg")
        source = bootstrap.OUT / "src/pkg/configure"
        self.assertEqual(source.read_bytes(), b"test")
        self.assertEqual(int(source.stat().st_mtime), 1234567890)
        self.assertEqual(source.stat().st_mode & 0o777, 0o755)
        source.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "modified private source"):
            bootstrap.extract(self.archive, "pkg")

    def test_rejects_unsafe_entries(self):
        for name, kind in (
            ("../escape", tarfile.REGTYPE),
            ("/absolute", tarfile.REGTYPE),
            ("pkg/link", tarfile.SYMTYPE),
            ("pkg/../../escape", tarfile.REGTYPE),
        ):
            with self.subTest(name=name):
                self.make_archive(name, kind=kind)
                with self.assertRaises(ValueError):
                    bootstrap.extract(self.archive, "pkg")

    def test_rejects_modified_existing_archive(self):
        archive = bootstrap.OUT / "pkg.tar.xz"
        archive.write_bytes(b"wrong")
        with self.assertRaisesRegex(ValueError, "modified source archive"):
            bootstrap.fetch("pkg", "https://invalid.example", "0" * 64)

    def test_rejects_dynamic_library_and_symlink(self):
        install = bootstrap.OUT / "install"
        for name in bootstrap.FILES:
            path = install / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"x")
        bad = install / "lib/bad.dylib"
        bad.write_bytes(b"x")
        with self.assertRaises(ValueError):
            bootstrap.installed_hashes(install)
        bad.unlink()
        bad.symlink_to(install / "lib/libgmp.a")
        with self.assertRaises(ValueError):
            bootstrap.installed_hashes(install)


if __name__ == "__main__":
    unittest.main()
