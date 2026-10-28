# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from shiraz.apk import Dex, FormatError, inspect_apk
from shiraz.cli import main


def dex_fixture():
    # One class referencing Activity.onCreate(Bundle):void; identifiers are sorted.
    strings = ['Landroid/app/Activity;', 'Landroid/os/Bundle;', 'Lsample/Main;', 'V', 'onCreate']
    count = len(strings)
    string_off = 112
    type_off = string_off + count * 4
    proto_off = type_off + 4 * 4
    method_off = proto_off + 12
    class_off = method_off + 8
    params_off = class_off + 32
    data_off = params_off + 6
    data = bytearray(data_off)
    string_offsets = []
    for text in strings:
        string_offsets.append(len(data))
        data.extend(bytes([len(text)]) + text.encode() + b'\0')
    data[:8] = b'dex\n035\0'
    struct.pack_into('<III', data, 32, len(data), 112, 0x12345678)
    for off, size, start in [(56, count, string_off), (64, 4, type_off), (72, 1, proto_off), (88, 1, method_off), (96, 1, class_off)]:
        struct.pack_into('<II', data, off, size, start)
    for i, pos in enumerate(string_offsets):
        struct.pack_into('<I', data, string_off + i * 4, pos)
    for i in range(4):
        struct.pack_into('<I', data, type_off + i * 4, i)
    struct.pack_into('<III', data, proto_off, 3, 3, params_off)
    struct.pack_into('<HHI', data, method_off, 0, 0, 4)
    struct.pack_into('<I', data, class_off, 2)
    struct.pack_into('<IH', data, params_off, 1, 1)
    return bytes(data)


class ApkTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'sample.apk'

    def apk(self, extra=None):
        with zipfile.ZipFile(self.path, 'w') as z:
            z.writestr('AndroidManifest.xml', b'<manifest package="sample.app"/>')
            z.writestr('classes.dex', dex_fixture())
            for name, value in (extra or {}).items():
                z.writestr(name, value)
        return self.path

    def test_inventory(self):
        path = self.apk({'classes2.dex': dex_fixture()})
        before = path.read_bytes()
        report = inspect_apk(path)
        self.assertEqual(len(report['dex']), 2)
        self.assertEqual(report['framework_types'], ['Landroid/app/Activity;', 'Landroid/os/Bundle;'])
        self.assertEqual(report['framework_methods'], ['Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V'])
        self.assertIsNone(report['runnable'])
        self.assertFalse(report['compatibility_verified'])
        self.assertEqual(report['apk_sha256'], hashlib.sha256(before).hexdigest())
        self.assertEqual(path.read_bytes(), before)

    def test_native_size_budget_does_not_expand_other_entries(self):
        from test_elf import elf_fixture
        payload = elf_fixture() + bytes(2048)
        with patch('shiraz.apk.MAX_ENTRY', 1024), patch('shiraz.apk.MAX_NATIVE_ENTRY', 4096):
            inspect_apk(self.apk({'lib/x86_64/libengine.so': payload}))
            with self.assertRaisesRegex(FormatError, 'oversized'):
                inspect_apk(self.apk({'res/raw/large.bin': payload}))
            with self.assertRaisesRegex(FormatError, 'oversized'):
                inspect_apk(self.apk({'lib/x86_64/libengine.so': payload + bytes(4096)}))

    def test_asset_budget_preserves_entry_and_archive_limits(self):
        with patch('shiraz.apk.MAX_ENTRY', 1024), patch('shiraz.apk.MAX_ASSET_ENTRY', 4096):
            inspect_apk(self.apk({'assets/data.zip': bytes(4096)}))
            with self.assertRaisesRegex(FormatError, 'oversized'):
                inspect_apk(self.apk({'assets/data.zip': bytes(4097)}))
            with self.assertRaisesRegex(FormatError, 'oversized'):
                inspect_apk(self.apk({'assets-other/data.zip': bytes(2048)}))
            with patch('shiraz.apk.MAX_TOTAL', 5000):
                with self.assertRaisesRegex(FormatError, 'processing limits'):
                    inspect_apk(self.apk({'assets/one': bytes(3000), 'assets/two': bytes(3000)}))

    def test_traversal_rejected(self):
        with self.assertRaisesRegex(FormatError, 'unsafe'):
            inspect_apk(self.apk({'../escape': b'x'}))

    def test_missing_dex(self):
        with zipfile.ZipFile(self.path, 'w') as z:
            z.writestr('AndroidManifest.xml', b'<manifest package="sample.app"/>')
        with self.assertRaisesRegex(FormatError, 'classes.dex'):
            inspect_apk(self.path)

    def test_missing_manifest(self):
        with zipfile.ZipFile(self.path, 'w') as z:
            z.writestr('classes.dex', dex_fixture())
        with self.assertRaisesRegex(FormatError, 'Manifest'):
            inspect_apk(self.path)

    def test_out_of_bounds(self):
        blob = bytearray(dex_fixture())
        struct.pack_into('<I', blob, 60, 0xfffffff0)
        with self.assertRaises(FormatError):
            Dex(blob)

    def test_bad_index(self):
        blob = bytearray(dex_fixture())
        type_off = struct.unpack_from('<I', blob, 68)[0]
        struct.pack_into('<I', blob, type_off, 9999)
        with self.assertRaises(FormatError):
            Dex(blob)

    def test_versions_and_truncation(self):
        for blob in [b'', b'dex\n035\0', b'not dex' * 30]:
            with self.assertRaises(FormatError):
                Dex(blob)
        blob = bytearray(dex_fixture()); blob[4:7] = b'041'
        with self.assertRaisesRegex(FormatError, 'unsupported'):
            Dex(blob)

    def test_native_architecture(self):
        elf = bytearray(64); elf[:7] = b'\x7fELF\x02\x01\x01'; struct.pack_into('<H', elf, 18, 183)
        apk = self.apk({'lib/arm64-v8a/libsample.so': elf})
        for machine, compatible in [('x86_64', False), ('aarch64', True), ('riscv64', False)]:
            with self.subTest(machine=machine), patch('shiraz.architecture.platform.machine', return_value=machine):
                report = inspect_apk(apk)
                self.assertEqual(report['host_native_candidate'], compatible)
        self.assertEqual(report['native_abis'], ['arm64-v8a'])
        struct.pack_into('<H', elf, 18, 62)
        apk = self.apk({'lib/x86_64/libsample.so': elf})
        for machine, compatible in [('x86_64', True), ('aarch64', False)]:
            with self.subTest(machine=machine), patch('shiraz.architecture.platform.machine', return_value=machine):
                self.assertEqual(inspect_apk(apk)['host_native_candidate'], compatible)
        struct.pack_into('<H', elf, 18, 183)
        with self.assertRaisesRegex(FormatError, 'disagrees'):
            inspect_apk(self.apk({'lib/x86_64/libsample.so': elf}))

    def test_run_requires_a_launcher(self):
        self.assertEqual(main(['run', str(self.apk())]), 1)

    def test_plan_stays_local(self):
        self.assertEqual(main(['plan', str(self.apk()), '--output', str(Path(self.temp.name) / 'plan.json')]), 1)

    def test_archive_size_bound(self):
        self.apk()
        with patch('shiraz.apk.MAX_TOTAL', 1):
            with self.assertRaisesRegex(FormatError, 'limits'):
                inspect_apk(self.path)
