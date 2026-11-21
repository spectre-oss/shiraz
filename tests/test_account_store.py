# SPDX-License-Identifier: GPL-3.0-only
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

import test_package_registry as fixtures

from shiraz.package_registry import PackageRegistry


class AccountStoreTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        self.owner = self.enroll_owner('test.owner')
        self.peer = self.fixture.enroll('test.peer')
        self.other = self.fixture.enroll('test.other', signer='b')

    def tearDown(self):
        self.fixture.tearDown()

    def enroll_owner(self, package, **kwargs):
        with patch(
            'shiraz.package_registry.inspect_authenticators', return_value=[self.descriptor]
        ):
            return self.fixture.enroll(package, services=[dict(name='test.Auth')], **kwargs)

    def call(self, action, caller=None, **kwargs):
        caller = caller or self.owner
        return self.registry.account_operation(
            caller['uid'],
            caller['apk_sha256'],
            dict(action=action, type='test.accounts', name='fixture-user', **kwargs),
        )

    def test_shared_persistence_authorization_and_atomic_add(self):
        def add(_):
            return self.call('add', password='fixture-pw', extras={'fixture-key': 'fixture-value'})

        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual(sorted(pool.map(add, range(2))), [False, True])
        self.assertEqual(self.call('get_password', self.peer), 'fixture-pw')
        self.assertEqual(self.call('get_data', self.peer, key='fixture-key'), 'fixture-value')
        for action, fields in (
            ('get_password', {}),
            ('remove', {}),
            ('set_password', {'value': 'different'}),
        ):
            with self.assertRaises(PermissionError):
                self.call(action, self.other, **fields)
        self.call('set_token', key='fixture-token-type', value='fixture-token')
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertEqual(
            self.call('get_token', self.peer, key='fixture-token-type'), 'fixture-token'
        )
        listed = self.registry.account_operation(
            self.peer['uid'], self.peer['apk_sha256'], dict(action='list', type=None)
        )
        self.assertEqual(listed, [dict(name='fixture-user', type='test.accounts')])
        self.assertEqual(
            self.registry.account_operation(
                self.other['uid'], self.other['apk_sha256'], dict(action='list', type=None)
            ),
            [],
        )
        self.registry.account_operation(
            self.peer['uid'],
            self.peer['apk_sha256'],
            dict(action='invalidate', type='test.accounts', value='fixture-token'),
        )
        self.assertIsNone(self.call('get_token', key='fixture-token-type'))
        self.call('set_token', key='fixture-token-type', value='fixture-token')
        self.call('set_password', self.peer, value=None)
        self.assertIsNone(self.call('get_password'))
        self.assertIsNone(self.call('get_token', key='fixture-token-type'))
        self.call('set_data', key='fixture-key', value=None)
        self.assertIsNone(self.call('get_data', key='fixture-key'))
        with self.assertRaises(ValueError):
            self.call('set_data', key='oversize', value='x' * 16385)
        for malformed in (
            {'action': []},
            {
                'action': 'remove',
                'name': 'fixture-user',
                'type': 'test.accounts',
                'uid': self.owner['uid'],
            },
        ):
            with self.assertRaises(ValueError):
                self.registry.account_operation(
                    self.owner['uid'], self.owner['apk_sha256'], malformed
                )
        with self.assertRaisesRegex(ValueError, 'byte limit'):
            self.call('set_password', value='\U0001f600' * 4000)
        self.assertIsNone(self.call('get_password'))
        self.assertTrue(self.call('remove', self.peer))
        self.assertFalse(self.call('remove'))

    def snapshot(self, caller=None):
        caller = caller or self.peer
        return self.registry.account_operation(
            caller['uid'], caller['apk_sha256'], {'action': 'snapshot'}
        )

    def test_snapshot_revisions_are_transactional_persistent_and_signer_scoped(self):
        initial = self.snapshot()
        other = self.snapshot(self.other)
        self.assertEqual(initial['accounts'], [])
        self.assertRegex(initial['revision'], r'^[0-9a-f]{64}$')
        self.assertTrue(self.call('add', password=None, extras={}))
        added = self.snapshot()
        self.assertEqual(added['accounts'], [dict(name='fixture-user', type='test.accounts')])
        self.assertNotEqual(initial['revision'], added['revision'])
        self.assertEqual(added, self.snapshot(self.owner))
        self.assertEqual(other, self.snapshot(self.other))
        self.assertFalse(self.call('add', password=None, extras={}))
        self.call('set_password', value='fixture-pw')
        self.call('set_data', key='fixture-key', value='fixture-value')
        self.call('set_token', key='fixture-token', value='fixture-value')
        self.assertEqual(added, self.snapshot())
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertEqual(added, self.snapshot())
        self.assertTrue(self.call('remove'))
        removed = self.snapshot()
        self.assertEqual(removed['accounts'], [])
        self.assertNotEqual(initial['revision'], removed['revision'])
        self.assertFalse(self.call('remove'))
        self.assertEqual(removed, self.snapshot())
        self.assertEqual(other, self.snapshot(self.other))
        with self.assertRaises(ValueError):
            self.registry.account_operation(
                self.peer['uid'],
                self.peer['apk_sha256'],
                dict(action='snapshot', uid=self.owner['uid']),
            )
        with self.assertRaises(PermissionError):
            self.registry.account_operation(self.peer['uid'], '0' * 64, dict(action='snapshot'))

    def test_snapshot_visibility_changes_and_failed_write_rollback(self):
        initial = self.snapshot()
        with patch(
            'shiraz.package_registry.os.urandom', side_effect=OSError('fixture revision failure')
        ):
            with self.assertRaises(OSError):
                self.call('add', password=None, extras={})
        self.assertEqual(initial, self.snapshot())
        self.call('add', password=None, extras={})
        visible = self.snapshot()
        self.owner = self.enroll_owner('test.owner', version=2, application_enabled='false')
        hidden = self.snapshot()
        self.assertEqual(hidden['accounts'], [])
        self.assertNotEqual(hidden['revision'], visible['revision'])
        with self.assertRaises(PermissionError):
            self.snapshot(self.owner)
        self.owner = self.enroll_owner('test.owner', version=3)
        self.assertEqual(visible, self.snapshot())

    def test_snapshot_schema_upgrade_preserves_accounts(self):
        self.call('add', password=None, extras={})
        with self.registry.connect() as db:
            db.execute('DROP TABLE account_change_revisions')
            db.execute('PRAGMA user_version=6')
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertEqual(
            self.snapshot()['accounts'], [dict(name='fixture-user', type='test.accounts')]
        )
        with self.registry.connect() as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 20)

    def prepare(self):
        return self.call('prepare_token', self.peer, key='fixture-token-type')

    def commit_token(self, prepared, value='fixture-token'):
        return self.call(
            'commit_token',
            self.peer,
            key='fixture-token-type',
            value=value,
            expected_uid=prepared['owner_uid'],
            expected_sha256=prepared['owner_sha256'],
            revision=prepared['revision'],
        )

    def test_token_cache_compare_and_swap_and_restart(self):
        self.call('add', password='fixture-pw', extras={})
        prepared = self.prepare()
        self.assertIsNone(prepared['token'])
        self.assertFalse(prepared['custom_tokens'])
        self.assertEqual(prepared['owner_uid'], self.owner['uid'])
        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual(
                sorted(
                    pool.map(
                        lambda value: self.commit_token(prepared, value), ['token-one', 'token-two']
                    )
                ),
                [False, True],
            )
        cached = self.prepare()
        self.assertIn(cached['token'], ['token-one', 'token-two'])
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertEqual(self.prepare(), cached)
        self.assertFalse(self.commit_token(prepared, 'late-token'))
        for invalid in (
            dict(cached, owner_uid=cached['owner_uid'] + 1),
            dict(cached, owner_sha256='0' * 64),
        ):
            self.assertFalse(self.commit_token(invalid))
        with self.assertRaises(PermissionError):
            self.call('prepare_token', self.other, key='fixture-token-type')
        with self.assertRaises(ValueError):
            self.commit_token(cached, None)
        with self.assertRaises(ValueError):
            self.commit_token(dict(cached, revision='not-a-revision'))

    def test_token_cache_invalidation_and_account_recreation_barriers(self):
        self.call('add', password='fixture-pw', extras={})
        operations = [
            lambda: self.registry.account_operation(
                self.peer['uid'],
                self.peer['apk_sha256'],
                dict(action='invalidate', type='test.accounts', value='not-yet-cached'),
            ),
            lambda: self.call('set_password', value='new-password'),
            lambda: self.call('set_token', key='fixture-token-type', value='explicit-token'),
            lambda: self.call('set_data', key='server', value='new-server'),
        ]
        for operation in operations:
            prepared = self.prepare()
            operation()
            self.assertFalse(self.commit_token(prepared, 'obsolete-token'))
        prepared = self.prepare()
        self.call('remove')
        self.call('add', password='replaced-pw', extras={})
        self.assertFalse(self.commit_token(prepared))
        self.assertIsNone(self.prepare()['token'])
        self.call('remove')
        absent = self.prepare()
        self.assertFalse(self.commit_token(absent))
        self.call('add', password=None, extras={})
        self.assertFalse(self.commit_token(absent))

    def test_token_cache_owner_update_and_custom_token_exclusion(self):
        self.call('add', password=None, extras={})
        prepared = self.prepare()
        self.assertTrue(self.commit_token(prepared))
        before_update = self.prepare()
        self.owner = self.enroll_owner('test.owner', version=2)
        self.assertFalse(self.commit_token(before_update))
        self.assertIsNone(self.prepare()['token'])
        self.descriptor = dict(self.descriptor, custom_tokens=True)
        self.owner = self.enroll_owner('test.owner', version=3)
        self.call('set_token', key='fixture-token-type', value='explicit-custom-token')
        custom = self.prepare()
        self.assertTrue(custom['custom_tokens'])
        self.assertIsNone(custom['token'])
        self.assertFalse(self.commit_token(custom))
        # Migration from schema 4 retains account data and initializes revision state.
        with self.registry.connect() as db:
            db.execute('DROP TABLE account_token_revisions')
            db.execute('PRAGMA user_version=4')
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertTrue(self.prepare()['custom_tokens'])
        self.assertEqual(self.call('get_token', key='fixture-token-type'), 'explicit-custom-token')

    def custom_prepare(self, caller=None):
        return self.call('prepare_custom_token', caller or self.peer, key='custom-type')

    def custom_commit(self, prepared, caller=None, **overrides):
        fields = dict(
            key='custom-type',
            value='synthetic-custom-token',
            expected_uid=prepared['owner_uid'],
            expected_sha256=prepared['owner_sha256'],
            revision=prepared['revision'],
            expires_ms=2000,
        )
        fields.update(overrides)
        return self.call('commit_custom_token', caller or self.peer, **fields)

    def enable_custom(self):
        self.descriptor = dict(self.descriptor, custom_tokens=True)
        self.owner = self.enroll_owner('test.owner', version=2)
        self.call('add', password=None, extras={})

    @patch('shiraz.account_store.time.time_ns', return_value=1000 * 1000000)
    def test_custom_token_caller_isolation_expiry_and_restart(self, clock):
        self.enable_custom()
        prepared = self.custom_prepare()
        self.assertIsNone(prepared['token'])
        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual(
                sorted(pool.map(lambda _: self.custom_commit(prepared), range(2))), [False, True]
            )
        self.assertFalse(self.custom_commit(prepared, value='late-token'))
        self.assertEqual(self.custom_prepare()['token'], 'synthetic-custom-token')
        self.assertIsNone(self.custom_prepare(self.owner)['token'])
        self.assertIsNone(self.prepare()['token'])
        self.assertIsNone(self.call('get_token', key='custom-type'))
        with self.assertRaises(PermissionError):
            self.custom_prepare(self.other)
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertEqual(self.custom_prepare()['expires_ms'], 2000)
        clock.return_value = 2000 * 1000000
        self.assertIsNone(self.custom_prepare()['token'])
        self.assertFalse(self.custom_commit(self.custom_prepare()))
        for expiry in (True, -1, 2**63, '3000', None):
            with self.assertRaises(ValueError):
                self.custom_commit(self.custom_prepare(), expires_ms=expiry)

    @patch('shiraz.account_store.time.time_ns', return_value=1000 * 1000000)
    def test_custom_token_mutation_and_generation_barriers(self, _clock):
        self.enable_custom()
        for mutate in (
            lambda: self.registry.account_operation(
                self.peer['uid'],
                self.peer['apk_sha256'],
                dict(action='invalidate', type='test.accounts', value='not-cached-yet'),
            ),
            lambda: self.call('set_password', value='changed'),
            lambda: self.call('set_data', key='server', value='changed'),
        ):
            self.assertTrue(self.custom_commit(self.custom_prepare()))
            before = self.custom_prepare()
            mutate()
            self.assertIsNone(self.custom_prepare()['token'])
            self.assertFalse(self.custom_commit(before))
        self.assertTrue(self.custom_commit(self.custom_prepare()))
        before = self.custom_prepare()
        self.call('remove')
        self.assertFalse(self.custom_commit(before))
        self.call('add', password=None, extras={})
        self.assertFalse(self.custom_commit(before))
        self.assertTrue(self.custom_commit(self.custom_prepare()))
        self.peer = self.fixture.enroll('test.peer', version=2)
        self.assertIsNone(self.custom_prepare()['token'])
        self.assertTrue(self.custom_commit(self.custom_prepare()))
        before = self.custom_prepare()
        self.owner = self.enroll_owner('test.owner', version=3)
        self.assertIsNone(self.custom_prepare()['token'])
        self.assertFalse(self.custom_commit(before))

    @patch('shiraz.account_store.time.time_ns', return_value=1000 * 1000000)
    def test_custom_token_mode_capacity_and_schema_migration(self, _clock):
        self.call('add', password=None, extras={})
        self.assertFalse(self.custom_commit(self.custom_prepare()))
        self.enable_custom()
        for index in range(256):
            self.assertTrue(self.custom_commit(self.custom_prepare(), key='type-' + str(index)))
        self.assertFalse(self.custom_commit(self.custom_prepare(), key='overflow'))
        self.assertTrue(
            self.custom_commit(self.custom_prepare(), key='type-0', value='replacement')
        )
        # Updating the caller frees obsolete entries even when another caller asks.
        self.peer = self.fixture.enroll('test.peer', version=2)
        self.assertTrue(self.custom_commit(self.custom_prepare(self.owner), self.owner))
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT COUNT(*) FROM custom_account_tokens').fetchone()[0], 1
            )
        with self.registry.connect() as db:
            db.execute('DROP TABLE custom_account_tokens')
            db.execute('PRAGMA user_version=5')
        self.registry = PackageRegistry(self.fixture.root / 'registry')
        self.assertIsNone(self.custom_prepare()['token'])
        self.assertTrue(self.custom_commit(self.custom_prepare()))

    def test_authenticator_resolution_revalidates_enabled_service_and_caller(self):
        def resolve(caller):
            return self.registry.resolve_authenticator(
                caller['uid'], caller['apk_sha256'], 'test.accounts'
            )

        self.assertEqual(resolve(self.peer)['uid'], self.owner['uid'])
        for version, fields in enumerate(
            ({'enabled': 'false'}, {'isolated': 'true'}, {'externalService': 'true'}), 2
        ):
            with patch(
                'shiraz.package_registry.inspect_authenticators', return_value=[self.descriptor]
            ):
                self.owner = self.fixture.enroll(
                    'test.owner', version=version, services=[dict(name='test.Auth', **fields)]
                )
            with self.assertRaises((PermissionError, ValueError)):
                resolve(self.peer)
        self.owner = self.enroll_owner('test.owner', version=5)
        self.assertEqual(resolve(self.peer)['apk_sha256'], self.owner['apk_sha256'])
        self.peer = self.fixture.enroll('test.peer', version=2, application_enabled='false')
        with self.assertRaises(PermissionError):
            resolve(self.peer)

    def test_replacement_owner_cannot_inherit_previous_accounts(self):
        self.call('add', password='fixture-orig', extras={})
        replacement = self.enroll_owner('test.replacement', signer='b')
        with self.assertRaises(PermissionError):
            self.call('get_password', replacement)
        self.registry.retire(
            self.owner['package'],
            expected_uid=self.owner['uid'],
            expected_sha256=self.owner['apk_sha256'],
        )
        self.assertIsNone(self.call('get_password', replacement))
        self.assertTrue(self.call('add', replacement, password='fixture-new', extras={}))
        self.assertEqual(self.call('get_password', replacement), 'fixture-new')
        with self.assertRaises(PermissionError):
            self.call('get_password', self.peer)
        with self.assertRaises(PermissionError):
            self.call('get_password')
        self.fixture.enroll('test.replacement', signer='b', version=2, application_enabled='false')
        with self.assertRaises(PermissionError):
            self.call('get_password', replacement)
