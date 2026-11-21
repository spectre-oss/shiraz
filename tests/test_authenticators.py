# SPDX-License-Identifier: GPL-3.0-only
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.authenticators import ACTION, META, inspect_authenticators
from shiraz.manifest import ANDROID


class AuthenticatorTests(unittest.TestCase):
    def inspect(self, xml, metadata=None):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('res/xml/auth.xml', xml)
                archive.writestr('resources.arsc', b'fixture table')
            manifest = dict(
                services=[
                    dict(
                        name='test.Authenticator',
                        filters=[dict(action=[ACTION])],
                        metadata=metadata
                        if metadata is not None
                        else [dict(name=META, kind='resource_name', value='xml/auth')],
                    )
                ]
            )
            return inspect_authenticators(apk, manifest)

    def test_literal_descriptor(self):
        result = self.inspect(
            f'<account-authenticator xmlns:android="{ANDROID}" android:accountType="test.accounts" android:customTokens="true" android:icon="@0x7f010001"/>'
        )
        self.assertEqual(
            result,
            [
                dict(
                    type='test.accounts',
                    component='test.Authenticator',
                    custom_tokens=True,
                    icon=0x7F010001,
                    label=0,
                    smallIcon=0,
                    accountPreferences=0,
                )
            ],
        )

    def test_reference_resolution_and_ambiguity(self):
        with patch('shiraz.service_descriptors.ResourceTable') as table:
            table.return_value.values.return_value = [(0, 3, 0)]
            table.return_value.string.return_value = 'test.resolved'
            xml = f'<account-authenticator xmlns:android="{ANDROID}" android:accountType="@0x7f010001"/>'
            self.assertEqual(self.inspect(xml)[0]['type'], 'test.resolved')
            table.return_value.values.return_value = [(0, 3, 0), (1, 3, 1)]
            with self.assertRaisesRegex(FormatError, 'configuration-dependent'):
                self.inspect(xml)
            table.return_value.values.return_value = [(0, 1, 0x7F010001)]
            with self.assertRaisesRegex(FormatError, 'budget'):
                self.inspect(xml)

    def test_malformed_and_unsupported_descriptors(self):
        for xml in (
            '<wrong/>',
            '<account-authenticator/>',
            '<!DOCTYPE x><account-authenticator/>',
            f'<account-authenticator xmlns:android="{ANDROID}" android:accountType="x" android:customTokens="maybe"/>',
            f'<account-authenticator xmlns:android="{ANDROID}" android:accountType="x" android:label="literal"/>',
        ):
            with self.subTest(xml=xml), self.assertRaises(FormatError):
                self.inspect(xml)
        with self.assertRaises(FormatError):
            self.inspect('<account-authenticator/>', [])
        self.assertEqual(inspect_authenticators('not-opened.apk', dict(services=[])), [])
