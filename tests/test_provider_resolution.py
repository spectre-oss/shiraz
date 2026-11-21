# SPDX-License-Identifier: GPL-3.0-only
import unittest

import test_package_registry as fixtures

from shiraz.manifest import ANDROID, inspect_manifest


class ProviderResolutionTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry

    def tearDown(self):
        self.fixture.tearDown()

    def resolve(self, caller, authority='test.data', access='read'):
        return self.registry.resolve_provider(
            caller['uid'], caller['apk_sha256'], authority, access
        )

    def test_manifest_permission_inheritance(self):
        manifest = inspect_manifest(
            f'''<manifest xmlns:android="{ANDROID}" package="test.owner">
        <application android:permission="test.BASE"><provider android:name=".Data" android:authorities="test.data"
        android:readPermission="test.READ"><path-permission android:pathPrefix="/private" android:permission="test.SECRET"/>
        </provider></application></manifest>'''.encode()
        )
        provider = manifest['providers'][0]
        self.assertEqual(provider['readPermission'], 'test.READ')
        self.assertEqual(provider['writePermission'], 'test.BASE')
        self.assertTrue(provider['has_path_permissions'])
        self.assertEqual(provider['access_metadata_version'], 1)

    def test_private_and_generation_bound_resolution(self):
        provider = dict(
            name='test.owner.Data', authorities='test.data;test.alias', access_metadata_version=1
        )
        owner = self.fixture.enroll('test.owner', target_sdk='27', providers=[provider])
        peer = self.fixture.enroll('test.peer')
        self.assertEqual(self.resolve(owner, 'test.alias')['apk_sha256'], owner['apk_sha256'])
        with self.assertRaisesRegex(PermissionError, 'not exported'):
            self.resolve(peer)
        self.fixture.enroll(
            'test.owner', version=2, target_sdk='27', providers=[dict(provider, exported='true')]
        )
        self.assertEqual(self.resolve(peer)['package'], 'test.owner')
        with self.assertRaisesRegex(PermissionError, 'generation'):
            self.resolve(owner)
        self.fixture.enroll('test.collision', providers=[dict(provider, exported='true')])
        with self.assertRaisesRegex(PermissionError, 'ambiguous'):
            self.resolve(peer)

    def test_read_write_signature_and_unsupported_access(self):
        provider = dict(
            name='test.owner.Data',
            authorities='test.data',
            exported='true',
            access_metadata_version=1,
            readPermission='test.READ',
            writePermission='test.WRITE',
        )
        owner = self.fixture.enroll(
            'test.owner',
            providers=[provider],
            defined_permissions=[
                dict(name='test.READ', protection=0),
                dict(name='test.WRITE', protection=2),
            ],
        )
        peer = self.fixture.enroll('test.peer', signer='b', permissions=['test.READ', 'test.WRITE'])
        trusted = self.fixture.enroll('test.trusted', permissions=['test.WRITE'])
        self.assertEqual(self.resolve(peer)['uid'], owner['uid'])
        with self.assertRaisesRegex(PermissionError, 'Signature'):
            self.resolve(peer, access='write')
        self.assertEqual(self.resolve(trusted, access='write')['uid'], owner['uid'])
        with self.assertRaises(PermissionError):
            self.resolve(trusted)
        self.fixture.enroll(
            'test.owner', version=2, providers=[dict(provider, has_path_permissions=True)]
        )
        with self.assertRaisesRegex(PermissionError, 'URI-aware'):
            self.resolve(trusted, access='write')
        old = dict(provider)
        old.pop('access_metadata_version')
        self.fixture.enroll('test.owner', version=3, providers=[old])
        with self.assertRaisesRegex(PermissionError, 're-enrollment'):
            self.resolve(peer)
        for authority in ('content://test.data', '0@test.data', 'test.data/path', ''):
            with self.assertRaises(ValueError):
                self.resolve(peer, authority)
        with self.assertRaises(ValueError):
            self.resolve(peer, access='call')

    def test_sdk_export_defaults_and_disabled_packages(self):
        provider = dict(name='test.owner.Data', authorities='test.data', access_metadata_version=1)
        peer = self.fixture.enroll('test.peer')
        self.fixture.enroll('test.owner', target_sdk='16', providers=[provider])
        self.assertEqual(self.resolve(peer)['package'], 'test.owner')
        self.fixture.enroll(
            'test.owner', version=2, min_sdk='16', target_sdk='27', providers=[provider]
        )
        with self.assertRaisesRegex(PermissionError, 'not exported'):
            self.resolve(peer)
        self.fixture.enroll(
            'test.owner', version=3, providers=[dict(provider, exported='true', enabled='false')]
        )
        with self.assertRaises(PermissionError):
            self.resolve(peer)
        self.fixture.enroll(
            'test.owner',
            version=4,
            application_enabled='false',
            providers=[dict(provider, exported='true')],
        )
        with self.assertRaises(PermissionError):
            self.resolve(peer)
        disabled = self.fixture.enroll('test.disabled', application_enabled='false')
        with self.assertRaisesRegex(PermissionError, 'disabled'):
            self.resolve(disabled)
