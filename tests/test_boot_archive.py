# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('bootstrap_boot', Path(__file__).resolve().parents[1] / 'scripts/bootstrap_boot.py')
boot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(boot)


class BootArchiveTests(unittest.TestCase):
    def test_resources_determinism_and_dex_alignment(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            classes, generated, first, second = [root / name for name in ('classes.jar', 'dex.jar', 'first.jar', 'second.jar')]
            with zipfile.ZipFile(classes, 'w') as archive:
                archive.writestr('java/lang/Object.class', b'class bytecode')
                archive.writestr('java/security/security.properties', b'actual resource\n')
                archive.writestr('META-INF/NOTICE', b'original dependency notice\n')
            with zipfile.ZipFile(generated, 'w') as archive:
                archive.writestr('classes.dex', b'dex\n038\0' + bytes(112))
            boot.boot_archive(classes, generated, first)
            boot.boot_archive(classes, generated, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(set(archive.namelist()), {'java/security/security.properties', 'META-INF/NOTICE', 'classes.dex'})
                self.assertEqual(archive.read('META-INF/NOTICE'), b'original dependency notice\n')
                entry = archive.getinfo('classes.dex')
                self.assertEqual(entry.compress_type, zipfile.ZIP_STORED)
                with first.open('rb') as stream:
                    stream.seek(entry.header_offset + 26)
                    name, extra = struct.unpack('<HH', stream.read(4))
                self.assertEqual((entry.header_offset + 30 + name + extra) % 4, 0)
            for names in [('classes.dex', 'classes2.dex'), ('classes.dex',)]:
                with zipfile.ZipFile(generated, 'w') as archive:
                    for name in names:
                        archive.writestr(name, b'dex\n039\0')
                with self.assertRaises(RuntimeError):
                    boot.boot_archive(classes, generated, second)
                self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_modified_compiler_is_rejected_without_execution(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / '.deps').mkdir()
            (root / '.deps' / ('r8-' + boot.R8_VERSION + '.jar')).write_bytes(b'modified build tool')
            with self.assertRaisesRegex(SystemExit, 'checksum mismatch'):
                boot.compiler(root)
