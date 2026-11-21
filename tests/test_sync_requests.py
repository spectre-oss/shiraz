# SPDX-License-Identifier: GPL-3.0-only
import json
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

import test_package_registry as fixtures

from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker, read_frame, write_frame
from shiraz.sync_requests import validate


class SyncRequestTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        auth = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        sync = dict(
            component='test.Sync',
            authority='test.data',
            account_type='test.accounts',
            user_visible=True,
            supports_uploading=True,
            always_syncable=True,
            allow_parallel_syncs=False,
            settings_activity=None,
        )
        with (
            patch('shiraz.package_registry.inspect_authenticators', return_value=[auth]),
            patch('shiraz.package_registry.inspect_sync_adapters', return_value=[sync]),
        ):
            self.owner = self.fixture.enroll(
                'test.owner', services=[dict(name='test.Auth'), dict(name='test.Sync')]
            )
        self.other = self.fixture.enroll('test.other', signer='b')
        self.registry.account_operation(
            self.owner['uid'],
            self.owner['apk_sha256'],
            dict(action='add', name='fixture-user', type='test.accounts', password=None, extras={}),
        )

    def tearDown(self):
        self.fixture.tearDown()

    def request(self, caller=None, **kwargs):
        caller = caller or self.owner
        request = dict(account=None, authority=None, extras={})
        request.update(kwargs)
        return self.registry.request_sync(caller['uid'], caller['apk_sha256'], request)

    def test_persistence_concurrent_dedup_and_identity(self):
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.request(), range(8)))
        self.assertEqual(len(results[0]), 1)
        self.assertTrue(all(result == results[0] for result in results))
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.request(), results[0])
        with self.registry.connect() as db:
            rows = db.execute('SELECT * FROM pending_sync_requests').fetchall()
        self.assertEqual(len(rows), 1)
        payload = json.loads(rows[0]['payload'])
        self.assertEqual(payload['adapter']['apk_sha256'], self.owner['apk_sha256'])
        self.assertEqual(payload['account_owner_sha256'], self.owner['apk_sha256'])
        self.assertNotIn('password', payload)

    def test_visibility_and_filters(self):
        self.assertEqual(self.request(self.other), [])
        self.assertEqual(self.request(authority='test.unknown'), [])
        self.assertEqual(self.request(account=dict(name='absent', type='test.accounts')), [])
        with self.assertRaises(PermissionError):
            self.request(dict(self.owner, apk_sha256='0' * 64))
        disabled = self.fixture.enroll('test.disabled', application_enabled='false')
        with self.assertRaises(PermissionError):
            self.request(disabled)

    def test_broker_admission_uses_channel_identity(self):
        request = dict(op='request_sync', request=dict(account=None, authority=None, extras={}))
        with ServiceBroker(self.registry) as broker:
            with broker.open_session(self.owner['package'], self.owner['apk_sha256']) as channel:
                write_frame(channel, request)
                self.assertEqual(read_frame(channel), dict(ok=True, queued=1))
                write_frame(channel, dict(request, uid=self.other['uid']))
                self.assertFalse(read_frame(channel)['ok'])
                write_frame(
                    channel,
                    dict(
                        op='request_sync',
                        request=dict(account=None, authority=None, extras={'x': 5}),
                    ),
                )
                self.assertFalse(read_frame(channel)['ok'])
            with broker.open_session(self.other['package'], self.other['apk_sha256']) as channel:
                write_frame(channel, request)
                self.assertEqual(read_frame(channel), dict(ok=True, queued=0))

    def test_bounded_and_transactional(self):
        for i in range(128):
            self.assertEqual(len(self.request(extras={'n': dict(type='int', value=i)})), 1)
        with self.assertRaisesRegex(ValueError, 'limit'):
            self.request(extras={'n': dict(type='int', value=128)})
        self.assertEqual(len(self.request(extras={'n': dict(type='int', value=0)})), 1)
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT COUNT(*) FROM pending_sync_requests').fetchone()[0], 128
            )

    def test_types_and_bounds(self):
        for value in [
            dict(type='int', value=True),
            dict(type='long', value=1 << 63),
            dict(type='float', value=float('nan')),
            dict(type='double', value=float('inf')),
            dict(type='blob', value='not-permitted'),
            dict(type='account', value={}),
            dict(type='string', value='x' * 32769),
        ]:
            with self.subTest(kind=value['type']), self.assertRaises(ValueError):
                validate(dict(account=None, authority=None, extras={'x': value}))
        validate(
            dict(account=None, authority=None, extras={'x': dict(type='long', value=(1 << 63) - 1)})
        )

    def test_account_removal_and_caller_update_invalidate_pending(self):
        self.request()
        self.assertEqual(len(self.registry.pending_sync_requests()), 1)
        self.registry.account_operation(
            self.owner['uid'],
            self.owner['apk_sha256'],
            dict(action='remove', name='fixture-user', type='test.accounts'),
        )
        self.assertEqual(self.registry.pending_sync_requests(), [])

        self.registry.account_operation(
            self.owner['uid'],
            self.owner['apk_sha256'],
            dict(action='add', name='fixture-user', type='test.accounts', password=None, extras={}),
        )
        peer = self.fixture.enroll('test.peer')
        self.request(peer)
        self.fixture.enroll('test.peer', version=2)
        self.assertEqual(self.registry.pending_sync_requests(), [])

    def test_dispatch_claim_is_exclusive_and_completion_is_fenced(self):
        key = self.request()[0]
        with ThreadPoolExecutor(max_workers=4) as pool:
            claims = list(pool.map(lambda _: self.registry.claim_sync_request(key), range(8)))
        winners = [claim for claim in claims if claim is not None]
        self.assertEqual(len(winners), 1)
        token = winners[0]['claim_token']
        self.assertFalse(self.registry.finish_sync_request(key, 'wrong', success=True))
        self.assertEqual(len(self.registry.pending_sync_requests()), 1)
        self.assertTrue(self.registry.finish_sync_request(key, token, success=True))
        self.assertEqual(self.registry.pending_sync_requests(), [])
        self.assertFalse(self.registry.finish_sync_request(key, token, success=True))

    def test_request_during_dispatch_survives_completion(self):
        key = self.request()[0]
        first = self.registry.claim_sync_request(key)
        self.assertEqual(self.request(), [key])
        self.assertTrue(self.registry.finish_sync_request(key, first['claim_token'], success=True))
        second = self.registry.claim_sync_request(key)
        self.assertIsNotNone(second)
        self.assertNotEqual(first['claim_token'], second['claim_token'])
        self.assertFalse(self.registry.finish_sync_request(key, first['claim_token'], success=True))
        self.assertTrue(self.registry.finish_sync_request(key, second['claim_token'], success=True))
        self.assertEqual(self.registry.pending_sync_requests(), [])

    def test_failed_dispatch_retains_work_and_restart_retains_claim(self):
        key = self.request()[0]
        claim = self.registry.claim_sync_request(key)
        self.registry = PackageRegistry(self.registry.root)
        self.assertIsNone(self.registry.claim_sync_request(key))
        self.assertTrue(self.registry.finish_sync_request(key, claim['claim_token'], success=False))
        self.assertEqual(len(self.registry.pending_sync_requests()), 1)
        self.assertIsNone(self.registry.claim_sync_request(key))
        self.assertIsNotNone(self.registry.claim_sync_request(key, now_ms=10**15))

    def test_adapter_serialization_survives_pending_invalidation(self):
        first = self.request(extras={'n': dict(type='int', value=1)})[0]
        second = self.request(extras={'n': dict(type='int', value=2)})[0]
        claim = self.registry.claim_sync_request(first)
        self.assertIsNone(self.registry.claim_sync_request(second))
        self.registry.account_operation(
            self.owner['uid'],
            self.owner['apk_sha256'],
            dict(action='remove', name='fixture-user', type='test.accounts'),
        )
        self.assertEqual(self.registry.pending_sync_requests(), [])
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT COUNT(*) FROM sync_request_claims').fetchone()[0], 1
            )
        self.assertTrue(
            self.registry.finish_sync_request(first, claim['claim_token'], success=False)
        )

    def test_durable_retry_backoff_and_adapter_delay(self):
        key = self.request()[0]
        first = self.registry.claim_sync_request(key, now_ms=1000)
        self.registry.finish_sync_request(key, first['claim_token'], success=False, now_ms=1000)
        self.registry = PackageRegistry(self.registry.root)
        self.assertIsNone(self.registry.claim_sync_request(key, now_ms=30999))
        second = self.registry.claim_sync_request(key, now_ms=31000)
        self.assertIsNotNone(second)
        self.registry.finish_sync_request(
            key, second['claim_token'], success=False, delay_until=200, now_ms=31000
        )
        self.assertIsNone(self.registry.claim_sync_request(key, now_ms=199999))
        third = self.registry.claim_sync_request(key, now_ms=200000)
        self.assertIsNotNone(third)
        self.registry.finish_sync_request(
            key, third['claim_token'], success=False, status='failed', now_ms=200000
        )
        self.assertIsNone(self.registry.claim_sync_request(key, now_ms=10**15))
        self.request()  # Explicit fresh demand clears suspension, but not the retry deadline.
        self.assertIsNone(self.registry.claim_sync_request(key, now_ms=200001))
        self.assertIsNotNone(self.registry.claim_sync_request(key, now_ms=10**15))


if __name__ == '__main__':
    unittest.main()
