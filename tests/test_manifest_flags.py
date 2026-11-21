# SPDX-License-Identifier: GPL-3.0-only
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from test_app_icon import table, type_chunk

from shiraz.apk import FormatError
from shiraz.manifest_flags import normalize, resolve_boolean, select


def entry(value, *, sdk=0, ui=0, extra=None, kind=18):
    config = bytearray(64)
    struct.pack_into('<I', config, 0, len(config))
    struct.pack_into('<H', config, 24, sdk)
    config[29] = ui
    if extra is not None:
        config[extra] = 1
    return (0, kind, value, bytes(config))


class Table:
    def __init__(self, values):
        self.entries = values

    def values(self, ident, configuration=False):
        assert configuration
        return self.entries.get(ident, [])


class ManifestFlagTests(unittest.TestCase):
    def test_process_resource_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'process.apk'
            for process in (':worker', 'sample.app.persistent', '', '../escape', '@unresolved'):
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr(
                        'resources.arsc', table([process], [type_chunk(1, {0: (3, 0, False)})])
                    )
                manifest = {'services': [{'process': '@0x7f010000'}]}
                if process in (':worker', 'sample.app.persistent'):
                    self.assertEqual(normalize(apk, manifest)['services'][0]['process'], process)
                else:
                    with self.assertRaises(FormatError):
                        normalize(apk, manifest)

    def test_normalization_reads_table_without_mutating_apk_or_manifest(self):
        manifest = {'services': [{'enabled': '@0x7f010000', 'exported': '@0x7f010001'}]}
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'flags.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr(
                    'resources.arsc',
                    table([], [type_chunk(1, {0: (18, 1, False), 1: (18, 0, False)})]),
                )
            original = apk.read_bytes()
            resolved = normalize(apk, manifest)
            self.assertEqual(resolved['services'], [{'enabled': 'true', 'exported': 'false'}])
            self.assertEqual(manifest['services'][0]['enabled'], '@0x7f010000')
            self.assertEqual(apk.read_bytes(), original)

    def test_sdk_and_tv_do_not_enable_desktop_components(self):
        table = Table({1: [entry(0), entry(1, sdk=33)], 2: [entry(1), entry(0, ui=6)]})
        self.assertEqual(resolve_boolean(table, 1), 'false')
        self.assertEqual(resolve_boolean(table, 2), 'true')
        self.assertEqual(select(table, 1, sdk=33), (18, 1))
        self.assertEqual(select(table, 2, ui_type=6), (18, 0))

    def test_ui_priority_before_sdk(self):
        table = Table({1: [entry(0, sdk=27), entry(1, sdk=21, ui=1)]})
        self.assertEqual(resolve_boolean(table, 1), 'true')

    def test_references_and_rejections(self):
        self.assertEqual(resolve_boolean(Table({1: [entry(2, kind=1)], 2: [entry(1)]}), 1), 'true')
        for entries in (
            {1: [entry(1, kind=1)]},
            {1: [entry(0), entry(1)]},
            {1: [entry(1, sdk=33)]},
            {1: [entry(1, extra=8)]},
            {1: [entry(1, kind=16)]},
        ):
            with self.assertRaises(FormatError):
                resolve_boolean(Table(entries), 1)
