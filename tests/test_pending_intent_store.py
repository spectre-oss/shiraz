# SPDX-License-Identifier: GPL-3.0-only
import concurrent.futures
import unittest

import test_package_registry as fixtures

from shiraz.pending_intent_store import (
    CANCEL_CURRENT,
    IMMUTABLE,
    MUTABLE,
    NO_CREATE,
    ONE_SHOT,
    UPDATE_CURRENT,
    CanceledTokenError,
    PendingIntentStore,
)


class PendingIntentStoreTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.owner = self.fixture.enroll('test.owner')
        self.other = self.fixture.enroll('test.other', signer='b')
        self.store = PendingIntentStore(self.fixture.registry)

    def tearDown(self):
        self.fixture.tearDown()

    def args(self, owner=None):
        owner = owner or self.owner
        return owner['uid'], owner['apk_sha256']

    def obtain(self, owner=None, **changes):
        values = dict(kind=0, request_code=1, identity=b'filter', payload=b'original', flags=0)
        values.update(changes)
        return self.store.obtain(*self.args(owner), **values)

    def test_identity_update_cancel_and_no_create(self):
        self.assertIsNone(self.obtain(flags=NO_CREATE))
        original = self.obtain(flags=IMMUTABLE)
        self.assertEqual(original, self.obtain(flags=IMMUTABLE, payload=b'ignored'))
        self.assertEqual(
            original, self.obtain(flags=IMMUTABLE | UPDATE_CURRENT, payload=b'updated')
        )
        self.assertEqual(
            self.store.claim_send(*self.args(self.other), original['token'])['payload'], b'updated'
        )
        replacement = self.obtain(flags=IMMUTABLE | CANCEL_CURRENT)
        self.assertNotEqual(original['token'], replacement['token'])
        with self.assertRaises(CanceledTokenError):
            self.store.inspect(*self.args(), original['token'])
        self.assertIsNone(self.obtain(flags=IMMUTABLE | CANCEL_CURRENT | NO_CREATE))
        with self.assertRaises(CanceledTokenError):
            self.store.inspect(*self.args(), replacement['token'])
        # Different creator, kind, code, mutability and filter are distinct keys.
        tokens = [
            self.obtain(),
            self.obtain(self.other),
            self.obtain(kind=1),
            self.obtain(request_code=2),
            self.obtain(flags=MUTABLE),
            self.obtain(identity=b'other'),
        ]
        self.assertEqual(len({row['token'] for row in tokens}), len(tokens))

    def test_creator_cannot_be_forged_and_holder_cannot_cancel(self):
        token = self.obtain()['token']
        facts = self.store.inspect(*self.args(self.other), token)
        self.assertEqual(facts['package'], 'test.owner')
        self.assertEqual(facts['uid'], self.owner['uid'])
        self.assertNotIn('payload', facts)
        with self.assertRaises(PermissionError):
            self.store.cancel(*self.args(self.other), token)
        self.store.cancel(*self.args(), token)
        with self.assertRaises(CanceledTokenError):
            self.store.claim_send(*self.args(self.other), token)
        with self.assertRaises(CanceledTokenError):
            self.store.inspect(*self.args(), '0' * 64)
        with self.assertRaises(ValueError):
            self.store.inspect(*self.args(), '../forged')

    def test_one_shot_claim_is_atomic_across_store_instances(self):
        token = self.obtain(flags=ONE_SHOT)['token']

        def claim(_):
            try:
                PendingIntentStore(self.fixture.registry).claim_send(*self.args(self.other), token)
                return True
            except CanceledTokenError:
                return False

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            self.assertEqual(sum(pool.map(claim, range(16))), 1)

    def test_persistence_and_generation_revocation(self):
        token = self.obtain()['token']
        reopened = PendingIntentStore(type(self.fixture.registry)(self.fixture.registry.root))
        self.assertEqual(reopened.inspect(*self.args(self.other), token)['package'], 'test.owner')
        updated = self.fixture.enroll('test.owner', version=2)
        with self.assertRaises(CanceledTokenError):
            reopened.inspect(*self.args(self.other), token)
        with self.assertRaises(PermissionError):
            self.obtain()
        self.owner = updated
        self.assertNotEqual(self.obtain()['token'], token)
        self.fixture.enroll('test.other', version=2, signer='b')
        with self.assertRaises(PermissionError):
            reopened.inspect(*self.args(self.other), token)

    def test_disabled_and_mismatched_callers_cannot_mint_authority(self):
        disabled = self.fixture.enroll('test.disabled', application_enabled='false')
        with self.assertRaises(PermissionError):
            self.obtain(disabled)
        with self.assertRaises(PermissionError):
            self.store.obtain(
                self.other['uid'],
                self.owner['apk_sha256'],
                kind=0,
                request_code=1,
                identity=b'filter',
                payload=b'snapshot',
                flags=0,
            )
        token = self.obtain()['token']
        with self.assertRaises(PermissionError):
            self.store.claim_send(*self.args(disabled), token)
        self.assertEqual(self.store.inspect(*self.args(), token)['package'], 'test.owner')

    def test_bounds_and_invalid_input_do_not_consume_existing_token(self):
        token = self.obtain()['token']
        for changes in (
            dict(flags=IMMUTABLE | MUTABLE),
            dict(flags=1),
            dict(kind=True),
            dict(request_code=2**31),
            dict(identity=b''),
            dict(payload=b'x' * 32769),
        ):
            with self.assertRaises(ValueError):
                self.obtain(**changes)
        self.assertEqual(self.store.inspect(*self.args(), token)['package'], 'test.owner')
        for code in range(2, 257):
            self.obtain(request_code=code)
        with self.assertRaisesRegex(ValueError, 'limit'):
            self.obtain(request_code=257)
        self.assertEqual(self.obtain(flags=NO_CREATE)['token'], token)
        self.assertEqual(self.obtain(flags=UPDATE_CURRENT)['token'], token)
        self.store.cancel(*self.args(), token)
        self.obtain(request_code=257)


if __name__ == '__main__':
    unittest.main()
