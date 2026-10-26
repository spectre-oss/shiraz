# SPDX-License-Identifier: GPL-3.0-only
import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from test_elf import elf_fixture
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from package_native import extract_native

class NativePackageTests(unittest.TestCase):
    def payload(self):
        data = bytearray(elf_fixture()); struct.pack_into('<HH', data, 16, 3, 62)
        return bytes(data)
    def test_unchanged_separate_x86_64_payload(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); apk = root/'app.apk'; data = self.payload()
            with zipfile.ZipFile(apk,'w') as out:
                out.writestr('lib/x86_64/libexample.so',data)
                out.writestr('lib/arm64-v8a/libexample.so',b'not selected')
            before = apk.read_bytes(); result = extract_native(apk, root/'libs')
            self.assertEqual((root/'libs/libexample.so').read_bytes(),data)
            self.assertEqual(result[0]['sha256'],hashlib.sha256(data).hexdigest())
            self.assertEqual(len(result),1); self.assertEqual(apk.read_bytes(),before)
    def test_native_size_limit_rejects_before_extracting(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); apk = root/'app.apk'; data = self.payload()
            with zipfile.ZipFile(apk, 'w') as out:
                out.writestr('lib/x86_64/libengine.so', data)
            with patch('package_native.MAX_NATIVE_ENTRY', len(data) - 1):
                with self.assertRaisesRegex(ValueError, 'entry'):
                    extract_native(apk, root/'libs')
            self.assertFalse((root/'libs').exists())
            with patch('package_native.MAX_NATIVE_ENTRY', len(data)):
                extract_native(apk, root/'libs')
            self.assertEqual((root/'libs/libengine.so').read_bytes(), data)

    def test_arm64_payload_and_machine_mismatch(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); apk = root/'app.apk'
            arm = bytearray(self.payload()); struct.pack_into('<H', arm, 18, 183)
            with zipfile.ZipFile(apk, 'w') as out:
                out.writestr('lib/arm64-v8a/libexample.so', arm)
                out.writestr('lib/x86_64/libexample.so', self.payload())
            result = extract_native(apk, root/'arm', 'aarch64')
            self.assertEqual((root/'arm/libexample.so').read_bytes(), arm)
            self.assertEqual(result[0]['apk_entry'], 'lib/arm64-v8a/libexample.so')
            with zipfile.ZipFile(apk, 'w') as out:
                out.writestr('lib/arm64-v8a/libexample.so', self.payload())
            with self.assertRaises(ValueError): extract_native(apk, root/'bad', 'aarch64')
            with self.assertRaises(ValueError): extract_native(apk, root/'unknown', 'unknown')
    def test_reject_bad_names_reserved_bridges_and_wrong_elf(self):
        for name,data in [('lib/x86_64/../evil.so',self.payload()),
                          ('lib/x86_64/libc.so',self.payload()),
                          ('lib/x86_64/libbad.so',b'not ELF'),
                          ('lib/x86_64/lib32.so',elf_fixture(32))]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                root = Path(folder); apk = root/'app.apk'
                with zipfile.ZipFile(apk,'w') as out: out.writestr(name,data)
                with self.assertRaises(ValueError): extract_native(apk,root/'libs')
    def test_reject_symlink_and_preserve_existing_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); apk=root/'app.apk'
            entry=zipfile.ZipInfo('lib/x86_64/libalias.so'); entry.external_attr=0o120777 << 16
            with zipfile.ZipFile(apk,'w') as out: out.writestr(entry,self.payload())
            with self.assertRaises(ValueError): extract_native(apk,root/'libs')
            existing=root/'existing'; existing.mkdir(); (existing/'keep').write_text('keep')
            with self.assertRaises(ValueError): extract_native(apk,existing)
            self.assertEqual((existing/'keep').read_text(),'keep')
