# SPDX-License-Identifier: GPL-3.0-only
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.manifest import ANDROID
from shiraz.sync_adapters import ACTION, inspect_sync_adapters


class SyncAdapterTests(unittest.TestCase):
    def inspect(self, attributes='', *, xml=None, metadata=None, count=1):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr(
                    'res/xml/sync.xml',
                    xml
                    if xml is not None
                    else f'<sync-adapter xmlns:android="{ANDROID}" android:contentAuthority="test.data" '
                    f'android:accountType="test.accounts" {attributes}/>',
                )
                archive.writestr('resources.arsc', b'fixture table')
            service = dict(
                name='test.Sync',
                filters=[dict(action=[ACTION])],
                metadata=metadata
                if metadata is not None
                else [dict(name=ACTION, kind='resource_name', value='xml/sync')],
            )
            return inspect_sync_adapters(apk, dict(services=[service] * count))

    def test_defaults_and_explicit_flags(self):
        expected = dict(
            component='test.Sync',
            authority='test.data',
            account_type='test.accounts',
            user_visible=True,
            supports_uploading=True,
            always_syncable=False,
            allow_parallel_syncs=False,
            settings_activity=None,
        )
        self.assertEqual(self.inspect(), [expected])
        expected.update(
            user_visible=False,
            supports_uploading=False,
            always_syncable=True,
            allow_parallel_syncs=True,
            settings_activity='test.Settings',
        )
        self.assertEqual(
            self.inspect(
                'android:userVisible="false" android:supportsUploading="0" '
                'android:isAlwaysSyncable="true" android:allowParallelSyncs="1" '
                'android:settingsActivity="test.Settings"'
            ),
            [expected],
        )

    def test_typed_resources_and_budgets(self):
        with patch('shiraz.service_descriptors.ResourceTable') as table:
            table.return_value.values.return_value = [(0, 0x12, -1)]
            self.assertTrue(
                self.inspect('android:allowParallelSyncs="@0x7f010001"')[0]['allow_parallel_syncs']
            )
            table.return_value.values.return_value = [(0, 3, 0)]
            table.return_value.string.return_value = 'test.Settings'
            self.assertEqual(
                self.inspect('android:settingsActivity="@0x7f010001"')[0]['settings_activity'],
                'test.Settings',
            )
            with self.assertRaises(FormatError):
                self.inspect('android:allowParallelSyncs="@0x7f010001"')
            table.return_value.values.return_value = [(0, 3, 0), (1, 3, 1)]
            with self.assertRaisesRegex(FormatError, 'configuration-dependent'):
                self.inspect('android:settingsActivity="@0x7f010001"')
            table.return_value.values.return_value = [(0, 1, 0x7F010001)]
            with self.assertRaisesRegex(FormatError, 'budget'):
                self.inspect('android:settingsActivity="@0x7f010001"')

    def test_invalid_declarations(self):
        for xml in (
            '<wrong/>',
            '<sync-adapter/>',
            '<!DOCTYPE x><sync-adapter/>',
            '<sync-adapter><nested/></sync-adapter>',
        ):
            with self.subTest(xml=xml), self.assertRaises(FormatError):
                self.inspect(xml=xml)
        for attributes in (
            'android:userVisible=""',
            'android:supportsUploading="maybe"',
            'android:settingsActivity="@string/unresolved"',
        ):
            with self.subTest(attributes=attributes), self.assertRaises(FormatError):
                self.inspect(attributes)
        for metadata in (
            [],
            [dict(name=ACTION, kind='value', value='xml/sync')],
            [dict(name=ACTION, kind='resource_name', value='xml/../sync')],
        ):
            with self.subTest(metadata=metadata), self.assertRaises(FormatError):
                self.inspect(metadata=metadata)
        with self.assertRaisesRegex(FormatError, 'count limit'):
            self.inspect(count=129)
        self.assertEqual(inspect_sync_adapters('not-opened.apk', dict(services=[])), [])
