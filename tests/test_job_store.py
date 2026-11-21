# SPDX-License-Identifier: GPL-3.0-only
import json
import subprocess
import sys
import unittest
from concurrent.futures import ThreadPoolExecutor
from contextlib import ExitStack, contextmanager
from unittest.mock import patch

import test_package_registry as fixtures

from shiraz import boot_identity, job_clock
from shiraz.package_registry import PackageRegistry


@contextmanager
def clock(milliseconds):
    with ExitStack() as stack:
        stack.enter_context(patch('shiraz.job_clock.elapsed_ms', return_value=milliseconds))
        stack.enter_context(
            patch(
                'shiraz.job_clock.sample',
                return_value=job_clock.Sample(
                    boot_identity.current(), milliseconds, milliseconds, 0
                ),
            )
        )
        yield


class JobStoreTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.service = dict(
            name='test.jobs.Worker',
            permission='android.permission.BIND_JOB_SERVICE',
            exported='false',
        )
        self.fields = dict(
            services=[self.service], permissions=['android.permission.RECEIVE_BOOT_COMPLETED']
        )
        self.owner = self.fixture.enroll('test.jobs', **self.fields)
        self.job = dict(
            version=1,
            id=1,
            component=self.service['name'],
            persisted=True,
            minimum_latency_ms=500,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={'large': dict(type='long', value=(1 << 63) - 1)},
        )

    def tearDown(self):
        self.fixture.tearDown()

    def put(self, job=None, owner=None):
        owner = owner or self.owner
        return self.registry.store_persisted_job(owner['uid'], owner['apk_sha256'], job or self.job)

    def listing(self, owner=None):
        owner = owner or self.owner
        return self.registry.list_persisted_jobs(owner['uid'], owner['apk_sha256'])

    def test_storage_definition_admission_preserves_requirement(self):
        saved = self.put()
        for persisted in (False, True):
            replacement = self.registry.store_shared_job(
                self.owner['uid'],
                self.owner['apk_sha256'],
                dict(self.job, version=2, persisted=persisted, requires_storage_not_low=True),
            )
            self.assertTrue(self.listing()[0]['definition']['requires_storage_not_low'])
            self.assertNotEqual(replacement['revision'], saved['revision'])
            saved = replacement
        reopened = PackageRegistry(self.registry.root)
        self.assertTrue(
            reopened.list_persisted_jobs(self.owner['uid'], self.owner['apk_sha256'])[0][
                'definition'
            ]['requires_storage_not_low']
        )

    def test_cancel_all_claim_snapshot_and_later_replacement(self):
        first = self.put()
        second = self.registry.store_shared_job(
            self.owner['uid'], self.owner['apk_sha256'], dict(self.job, id=2, persisted=False)
        )
        peer = self.fixture.enroll('test.peer', **self.fields)
        peer_job = self.put(owner=peer)
        claim = self.registry.claim_persisted_job(
            self.owner['uid'], self.owner['apk_sha256'], 1, first['revision']
        )
        with self.assertRaises(PermissionError):
            self.registry.cancel_all_shared_jobs(self.owner['uid'], '0' * 64)
        self.assertEqual(len(self.listing()), 2)
        removed, claims = self.registry.cancel_all_shared_jobs(
            self.owner['uid'], self.owner['apk_sha256']
        )
        self.assertTrue(removed)
        self.assertEqual(claims, {claim['token']})
        self.assertEqual(self.listing(), [])
        self.assertEqual(self.listing(peer), [peer_job])
        replacement = self.put()
        self.assertIsNone(
            self.registry.claim_persisted_job(
                self.owner['uid'], self.owner['apk_sha256'], 1, replacement['revision']
            )
        )
        self.assertFalse(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, claim['token'])
        )
        self.assertEqual(self.listing(), [replacement])
        self.assertNotEqual(second['id'], replacement['id'])
        self.assertEqual(
            self.registry.cancel_all_shared_jobs(self.owner['uid'], self.owner['apk_sha256']),
            (True, set()),
        )
        self.assertEqual(
            self.registry.cancel_all_shared_jobs(self.owner['uid'], self.owner['apk_sha256']),
            (False, set()),
        )

    def test_shared_nonpersisted_boot_lifetime_and_claims(self):
        transient = dict(self.job, persisted=False)
        owner = self.fixture.enroll('test.transient', services=[self.service])
        with patch(
            'shiraz.boot_identity.current', return_value='11111111-1111-4111-8111-111111111111'
        ):
            registry = PackageRegistry(self.registry.root)
            saved = registry.store_shared_job(owner['uid'], owner['apk_sha256'], transient)
            claim = registry.claim_persisted_job(
                owner['uid'], owner['apk_sha256'], saved['id'], saved['revision']
            )
            durable = registry.store_shared_job(
                self.owner['uid'], self.owner['apk_sha256'], self.job
            )
            durable_claim = registry.claim_persisted_job(
                self.owner['uid'], self.owner['apk_sha256'], durable['id'], durable['revision']
            )
            reopened = PackageRegistry(self.registry.root)
            self.assertEqual(
                reopened.list_persisted_jobs(owner['uid'], owner['apk_sha256']), [saved]
            )
            self.assertIsNone(
                reopened.claim_persisted_job(
                    owner['uid'], owner['apk_sha256'], saved['id'], saved['revision']
                )
            )
        restored_clock = job_clock.Sample(
            '22222222-2222-4222-8222-222222222222', durable['scheduled_at_ms'], 100, 0
        )
        with (
            patch('shiraz.boot_identity.current', return_value=restored_clock.boot_id),
            patch('shiraz.job_clock.sample', return_value=restored_clock),
        ):
            rebooted = PackageRegistry(self.registry.root)
            self.assertEqual(rebooted.list_persisted_jobs(owner['uid'], owner['apk_sha256']), [])
            self.assertEqual(
                rebooted.list_persisted_jobs(self.owner['uid'], self.owner['apk_sha256']),
                [dict(durable, next_run_elapsed_ms=600)],
            )
            self.assertFalse(
                rebooted.complete_persisted_job_claim(owner['uid'], saved['id'], claim['token'])
            )
            self.assertFalse(
                rebooted.complete_persisted_job_claim(
                    self.owner['uid'], durable['id'], durable_claim['token']
                )
            )
            self.assertIsNotNone(
                rebooted.claim_persisted_job(
                    self.owner['uid'], self.owner['apk_sha256'], durable['id'], durable['revision']
                )
            )

    def test_shared_job_replacement_and_retry_without_boot_permission(self):
        owner = self.fixture.enroll('test.transient', services=[self.service])
        saved = self.registry.store_shared_job(
            owner['uid'], owner['apk_sha256'], dict(self.job, persisted=False)
        )
        claim = self.registry.claim_persisted_job(
            owner['uid'], owner['apk_sha256'], saved['id'], saved['revision']
        )
        self.assertTrue(
            self.registry.complete_persisted_job_claim(
                owner['uid'], saved['id'], claim['token'], retry=True
            )
        )
        self.assertEqual(self.listing(owner)[0]['attempts'], 1)
        with self.assertRaises(PermissionError):
            self.registry.store_shared_job(owner['uid'], owner['apk_sha256'], self.job)
        self.assertFalse(self.listing(owner)[0]['definition']['persisted'])
        first = self.put()
        replacement = self.registry.store_shared_job(
            self.owner['uid'], self.owner['apk_sha256'], dict(self.job, persisted=False)
        )
        self.assertNotEqual(first['revision'], replacement['revision'])
        self.assertEqual(len(self.listing()), 1)
        self.assertTrue(self.put()['definition']['persisted'])

    def test_restart_replacement_and_conditional_cancel(self):
        first = self.put()
        self.assertEqual(first['definition'], self.job)
        self.assertGreater(first['scheduled_at_ms'], 0)
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.listing(), [first])
        child = subprocess.run(
            [
                sys.executable,
                '-c',
                'import json,sys; from shiraz.package_registry import PackageRegistry; '
                'print(json.dumps(PackageRegistry(sys.argv[1]).list_persisted_jobs(int(sys.argv[2]),sys.argv[3])))',
                str(self.registry.root),
                str(self.owner['uid']),
                self.owner['apk_sha256'],
            ],
            capture_output=True,
            text=True,
            timeout=15,
            check=True,
        )
        self.assertEqual(json.loads(child.stdout), [first])
        replacement = self.put(dict(self.job, minimum_latency_ms=700))
        self.assertNotEqual(replacement['revision'], first['revision'])
        self.assertFalse(
            self.registry.cancel_persisted_job(
                self.owner['uid'], self.owner['apk_sha256'], 1, expected_revision=first['revision']
            )
        )
        self.assertEqual(self.listing(), [replacement])
        self.assertTrue(
            self.registry.cancel_persisted_job(
                self.owner['uid'],
                self.owner['apk_sha256'],
                1,
                expected_revision=replacement['revision'],
            )
        )
        self.assertFalse(
            self.registry.cancel_persisted_job(self.owner['uid'], self.owner['apk_sha256'], 1)
        )

    def test_permission_identity_and_failed_replacement_preserve_data(self):
        first = self.put()
        peer = self.fixture.enroll('test.peer', permissions=self.fields['permissions'])
        with self.assertRaises(PermissionError):
            self.put(owner=peer)
        self.assertEqual(self.listing(peer), [])
        missing = self.fixture.enroll('test.missing', services=[self.service])
        with self.assertRaisesRegex(PermissionError, 'RECEIVE_BOOT_COMPLETED'):
            self.put(owner=missing)
        for invalid in (
            dict(self.job, persisted=False),
            dict(self.job, component='test.Missing'),
            dict(self.job, extras={'x': dict(type='parcelable', value={})}),
        ):
            with self.assertRaises((ValueError, PermissionError)):
                self.put(invalid)
        with self.assertRaises(PermissionError):
            self.registry.store_persisted_job(self.owner['uid'], '0' * 64, self.job)
        self.assertEqual(self.listing(), [first])

    def test_compatible_update_rotates_revision_and_removed_service_drops_job(self):
        first = self.put()
        old = self.owner
        self.owner = self.fixture.enroll('test.jobs', version=2, **self.fields)
        updated = self.listing()[0]
        self.assertEqual(updated['definition'], first['definition'])
        self.assertEqual(updated['scheduled_at_ms'], first['scheduled_at_ms'])
        self.assertEqual(updated['apk_sha256'], self.owner['apk_sha256'])
        self.assertNotEqual(updated['revision'], first['revision'])
        self.assertFalse(
            self.registry.cancel_persisted_job(
                self.owner['uid'], self.owner['apk_sha256'], 1, expected_revision=first['revision']
            )
        )
        with self.assertRaises(PermissionError):
            self.registry.cancel_persisted_job(old['uid'], old['apk_sha256'], 1)
        self.owner = self.fixture.enroll(
            'test.jobs', version=3, permissions=self.fields['permissions']
        )
        self.assertEqual(self.listing(), [])

    def test_lost_boot_permission_retirement_and_atomic_update_failure(self):
        first = self.put()
        with patch('shiraz.job_store.reconcile', side_effect=RuntimeError('fixture rollback')):
            with self.assertRaises(RuntimeError):
                self.fixture.enroll('test.jobs', version=2, **self.fields)
        self.assertEqual(self.listing(), [first])
        self.owner = self.fixture.enroll('test.jobs', version=2, services=[self.service])
        self.assertEqual(self.listing(), [])
        self.owner = self.fixture.enroll('test.jobs', version=3, **self.fields)
        self.put()
        self.registry.retire(
            'test.jobs', expected_uid=self.owner['uid'], expected_sha256=self.owner['apk_sha256']
        )
        with self.registry.connect() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM persisted_jobs').fetchone()[0], 0)
        with self.assertRaises(PermissionError):
            self.listing()

    def test_concurrent_quota_and_replace_at_capacity(self):
        with patch('shiraz.job_store.PER_PACKAGE_LIMIT', 3):

            def submit(index):
                try:
                    return self.put(dict(self.job, id=index))
                except ValueError:
                    return None

            with ThreadPoolExecutor(max_workers=8) as pool:
                admitted = [row for row in pool.map(submit, range(8)) if row]
            self.assertEqual(len(admitted), 3)
            self.assertEqual(len(self.listing()), 3)
            selected = admitted[0]
            self.put(dict(self.job, id=selected['id'], minimum_latency_ms=900))
            self.assertEqual(len(self.listing()), 3)
        with patch('shiraz.job_store.GLOBAL_LIMIT', 3):
            other = self.fixture.enroll('test.other', **self.fields)
            with self.assertRaisesRegex(ValueError, 'global limit'):
                self.put(owner=other)

    def test_elapsed_due_ignores_wall_jumps_and_reopen(self):
        with clock(1000):
            row = self.put(dict(self.job, deadline_ms=700))
        for wall in (0, 999999999999):
            with (
                patch('shiraz.job_clock.time.time_ns', return_value=wall * 1000000),
                patch('shiraz.job_clock.elapsed_ms', return_value=1499),
            ):
                self.registry = PackageRegistry(self.registry.root)
                self.assertEqual(self.listing(), [row])
                self.assertEqual(self.registry.due_persisted_jobs(), [])
                self.assertIsNone(self.claim_due(row))
        with patch('shiraz.job_clock.elapsed_ms', return_value=1500):
            self.assertIsNotNone(self.claim_due(row))

    def test_retry_uses_elapsed_target_with_independent_wall_projection(self):
        with clock(1000):
            row = self.put(dict(self.job, deadline_ms=700))
            owned = self.claim(row)
        sample = job_clock.Sample(boot_identity.current(), 900000, 2000, 0)
        with patch('shiraz.job_clock.sample', return_value=sample):
            self.registry.complete_persisted_job_claim(
                self.owner['uid'], row['id'], owned['token'], retry=True
            )
        retried = self.listing()[0]
        self.assertEqual(retried['next_run_at_ms'], 930000)
        self.assertEqual(retried['next_run_elapsed_ms'], 32000)
        self.assertIsNone(retried['deadline_elapsed_ms'])
        with patch('shiraz.job_clock.elapsed_ms', return_value=31999):
            self.assertIsNone(self.claim_due(retried))
        with patch('shiraz.job_clock.elapsed_ms', return_value=32000):
            self.assertIsNotNone(self.claim_due(retried))

    def test_elapsed_reboot_restores_targets_and_retires_old_claims(self):
        with clock(1000):
            row = self.put(dict(self.job, deadline_ms=700))
            self.claim(row)
        new_boot = '11111111-2222-4333-8444-555555555555'
        with (
            patch('shiraz.boot_identity.current', return_value=new_boot),
            patch('shiraz.job_clock.sample', return_value=job_clock.Sample(new_boot, 1200, 100, 0)),
        ):
            self.registry = PackageRegistry(self.registry.root)
        restored = self.listing()[0]
        self.assertEqual(restored['next_run_elapsed_ms'], 400)
        self.assertEqual(restored['deadline_elapsed_ms'], 600)
        self.assertEqual(restored['revision'], row['revision'])
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT count(*) FROM persisted_job_claims').fetchone()[0], 0
            )
        with patch('shiraz.job_clock.elapsed_ms', return_value=400):
            self.assertIsNotNone(self.claim_due(restored))

    def test_current_elapsed_jobs_require_recorded_boot_identity(self):
        row = self.put()
        owned = self.claim(row)
        with self.registry.connect() as db:
            db.execute('DELETE FROM job_boot_state')
        with self.assertRaisesRegex(ValueError, 'boot identity'):
            PackageRegistry(self.registry.root)
        with self.registry.connect() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM job_boot_state').fetchone()[0], 0)
            self.assertEqual(
                db.execute('SELECT token FROM persisted_job_claims').fetchone()[0], owned['token']
            )

    def test_current_schema_bad_elapsed_state_is_not_repaired(self):
        row = self.put(dict(self.job, deadline_ms=700))
        for column, value in (
            ('next_run_elapsed_ms', None),
            ('next_run_elapsed_ms', -1),
            ('deadline_elapsed_ms', None),
        ):
            with self.registry.connect() as db:
                db.execute('UPDATE persisted_jobs SET ' + column + '=?', (value,))
            with self.assertRaises(ValueError):
                PackageRegistry(self.registry.root)
            with self.registry.connect() as db:
                self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 20)
                db.execute(
                    'UPDATE persisted_jobs SET next_run_elapsed_ms=?,deadline_elapsed_ms=?',
                    (row['next_run_elapsed_ms'], row['deadline_elapsed_ms']),
                )

    def test_legacy_elapsed_migration_failure_rolls_back_and_retains_claim(self):
        row = self.put(dict(self.job, deadline_ms=700))
        owned = self.claim(row)
        with self.registry.connect() as db:
            db.execute(
                'UPDATE persisted_jobs SET next_run_elapsed_ms=NULL,deadline_elapsed_ms=NULL'
            )
            db.execute('PRAGMA user_version=12')
        with patch('shiraz.job_clock.sample', side_effect=RuntimeError('clock unavailable')):
            with self.assertRaisesRegex(RuntimeError, 'clock unavailable'):
                PackageRegistry(self.registry.root)
        with self.registry.connect() as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 12)
            self.assertEqual(
                db.execute('SELECT token FROM persisted_job_claims').fetchone()[0], owned['token']
            )
        with patch(
            'shiraz.job_clock.sample',
            return_value=job_clock.Sample(
                boot_identity.current(), row['scheduled_at_ms'], 10000, 0
            ),
        ):
            self.registry = PackageRegistry(self.registry.root)
        restored = self.listing()[0]
        self.assertEqual(restored['next_run_elapsed_ms'], 10500)
        self.assertEqual(restored['deadline_elapsed_ms'], 10700)
        self.assertIsNone(self.claim(restored))

    def test_schema_seven_migration(self):
        with self.registry.connect() as db:
            db.execute('DROP TABLE persisted_jobs')
            db.execute('PRAGMA user_version=7')
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.registry.identity('test.jobs')['uid'], self.owner['uid'])
        self.assertEqual(self.listing(), [])
        self.put()
        with self.registry.connect() as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 20)

    def claim(self, row):
        return self.registry.claim_persisted_job(
            self.owner['uid'], self.owner['apk_sha256'], row['id'], row['revision']
        )

    def claim_due(self, row):
        return self.registry.claim_due_persisted_job(
            self.owner['uid'], self.owner['apk_sha256'], row['id'], row['revision']
        )

    def test_due_boundary_clock_recheck_and_competing_dispatchers(self):
        with clock(1000):
            row = self.put()
        with clock(1499):
            self.assertEqual(self.registry.due_persisted_jobs(), [])
            self.assertIsNone(self.claim_due(row))
        with clock(1500):
            found = self.registry.due_persisted_jobs()
            self.assertEqual(found, [dict(row, owner_uid=self.owner['uid'], package='test.jobs')])
        # A discovery snapshot does not bypass a later clock/eligibility check.
        with clock(1490):
            self.assertIsNone(self.claim_due(found[0]))
        with clock(1500):
            with ThreadPoolExecutor(max_workers=8) as pool:
                claims = [
                    item for item in pool.map(lambda _: self.claim_due(row), range(8)) if item
                ]
            self.assertEqual(len(claims), 1)
            self.assertEqual(self.registry.due_persisted_jobs(), [])
            self.registry = PackageRegistry(self.registry.root)
            self.assertEqual(self.registry.due_persisted_jobs(), [])

    def test_due_order_limits_replacement_and_retained_claim(self):
        with clock(1000):
            late = self.put(dict(self.job, id=3, minimum_latency_ms=900))
            early = self.put(dict(self.job, id=2, minimum_latency_ms=100))
            tied = self.put(dict(self.job, id=1, minimum_latency_ms=100))
        with clock(2000):
            self.assertEqual([r['id'] for r in self.registry.due_persisted_jobs(limit=2)], [1, 2])
            for invalid in (0, 4097, True, 1.5):
                with self.assertRaises(ValueError):
                    self.registry.due_persisted_jobs(limit=invalid)
            owned = self.claim_due(tied)
            replacement = self.put(dict(self.job, minimum_latency_ms=0))
            self.assertEqual(
                [r['id'] for r in self.registry.due_persisted_jobs()], [early['id'], late['id']]
            )
            self.assertIsNone(self.claim_due(replacement))
            self.assertFalse(
                self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
            )
            self.assertIsNone(self.claim_due(tied))
            self.assertIsNotNone(self.claim_due(replacement))

    def test_due_discovery_revalidates_policy_and_update_snapshot(self):
        with clock(1000):
            row = self.put()
        with clock(2000):
            snapshot = self.registry.due_persisted_jobs()[0]
            self.owner = self.fixture.enroll('test.jobs', version=2, **self.fields)
            self.assertIsNone(self.claim_due(snapshot))
            updated = self.registry.due_persisted_jobs()[0]
            self.assertNotEqual(updated['revision'], row['revision'])
            owned = self.claim_due(updated)
            self.assertIsNotNone(owned)
        # Simulate policy becoming unavailable without mutating the stored job.
        self.registry.release_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        with patch.object(
            self.registry, '_job_caller', side_effect=PermissionError('disabled')
        ) as policy:
            with clock(2000):
                self.assertEqual(self.registry.due_persisted_jobs(), [])
                policy.assert_called_once()
                with self.assertRaises(PermissionError):
                    self.claim_due(updated)

    def test_claim_race_restart_and_oneoff_completion(self):
        row = self.put()
        with ThreadPoolExecutor(max_workers=8) as pool:
            claims = [item for item in pool.map(lambda _: self.claim(row), range(8)) if item]
        self.assertEqual(len(claims), 1)
        owned = claims[0]
        self.registry = PackageRegistry(self.registry.root)
        self.assertIsNone(self.claim(row))
        child = subprocess.run(
            [
                sys.executable,
                '-c',
                'import json,sys; from shiraz.package_registry import PackageRegistry; '
                'print(json.dumps(PackageRegistry(sys.argv[1]).claim_persisted_job(int(sys.argv[2]),sys.argv[3],1,sys.argv[4])))',
                str(self.registry.root),
                str(self.owner['uid']),
                self.owner['apk_sha256'],
                row['revision'],
            ],
            capture_output=True,
            text=True,
            timeout=15,
            check=True,
        )
        self.assertIsNone(json.loads(child.stdout))
        self.assertFalse(self.registry.complete_persisted_job_claim(self.owner['uid'], 1, '0' * 32))
        self.assertTrue(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        )
        self.assertEqual(self.listing(), [])
        self.assertFalse(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        )

    def test_replacement_and_cancellation_keep_old_worker_reservation(self):
        first = self.put()
        owned = self.claim(first)
        replacement = self.put(dict(self.job, minimum_latency_ms=900))
        self.assertIsNone(self.claim(first))
        self.assertIsNone(self.claim(replacement))
        self.assertFalse(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        )
        self.assertEqual(self.listing(), [replacement])
        next_claim = self.claim(replacement)
        self.assertIsNotNone(next_claim)
        self.registry.cancel_persisted_job(self.owner['uid'], self.owner['apk_sha256'], 1)
        third = self.put()
        self.assertIsNone(self.claim(third))
        self.assertFalse(
            self.registry.release_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        )
        self.assertTrue(
            self.registry.release_persisted_job_claim(self.owner['uid'], 1, next_claim['token'])
        )
        self.assertIsNotNone(self.claim(third))

    def test_package_update_and_retirement_claim_cleanup(self):
        owned = self.claim(self.put())
        self.owner = self.fixture.enroll('test.jobs', version=2, **self.fields)
        updated = self.listing()[0]
        self.assertIsNone(self.claim(updated))
        self.assertFalse(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
        )
        self.assertEqual(self.listing(), [updated])
        next_claim = self.claim(updated)
        self.registry.retire(
            'test.jobs', expected_uid=self.owner['uid'], expected_sha256=self.owner['apk_sha256']
        )
        self.assertFalse(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, next_claim['token'])
        )
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT count(*) FROM persisted_job_claims').fetchone()[0], 0
            )

    def test_periodic_retry_and_success_requeue_atomically(self):
        with clock(1000):
            row = self.put(dict(self.job, minimum_latency_ms=0, interval_ms=900000, flex_ms=300000))
        self.assertEqual(row['next_run_at_ms'], 601000)
        owned = self.claim(row)
        with clock(700000):
            self.assertTrue(
                self.registry.complete_persisted_job_claim(
                    self.owner['uid'], 1, owned['token'], retry=True
                )
            )
        retry = self.listing()[0]
        self.assertEqual((retry['next_run_at_ms'], retry['attempts']), (730000, 1))
        self.assertNotEqual(retry['revision'], row['revision'])
        self.assertEqual(retry['definition'], row['definition'])
        self.assertFalse(
            self.registry.complete_persisted_job_claim(
                self.owner['uid'], 1, owned['token'], retry=True
            )
        )
        owned = self.claim(retry)
        with clock(740000):
            self.assertTrue(
                self.registry.complete_persisted_job_claim(self.owner['uid'], 1, owned['token'])
            )
        periodic = self.listing()[0]
        self.assertEqual((periodic['next_run_at_ms'], periodic['attempts']), (1640000, 0))
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.listing(), [periodic])
        self.assertIsNotNone(self.claim(periodic))

    def test_retry_backoff_cap_and_completion_rollback(self):
        from shiraz.job_store import retry_delay

        for policy, attempt, expected in (
            (0, 2, 90000),
            (1, 2, 120000),
            (0, (1 << 31) - 1, 18000000),
            (1, (1 << 31) - 1, 18000000),
        ):
            self.assertEqual(retry_delay(dict(self.job, backoff_policy=policy), attempt), expected)
        with clock(1000):
            row = self.put(dict(self.job, deadline_ms=200))
        self.assertEqual(row['next_run_at_ms'], 1200)
        owned = self.claim(row)
        with patch('shiraz.job_clock.sample', side_effect=RuntimeError('clock unavailable')):
            with self.assertRaises(RuntimeError):
                self.registry.complete_persisted_job_claim(
                    self.owner['uid'], 1, owned['token'], retry=True
                )
        self.assertEqual(self.listing(), [row])
        self.assertIsNone(self.claim(row))
        with clock(2000):
            self.assertTrue(
                self.registry.complete_persisted_job_claim(
                    self.owner['uid'], 1, owned['token'], retry=True
                )
            )
        retried = self.listing()[0]
        self.assertEqual((retried['next_run_at_ms'], retried['attempts']), (32000, 1))
        final = self.claim(retried)
        self.assertTrue(
            self.registry.complete_persisted_job_claim(self.owner['uid'], 1, final['token'])
        )
        self.assertEqual(self.listing(), [])

    def test_timing_saturation_and_retry_flag_validation(self):
        with clock((1 << 63) - 100):
            row = self.put()
        self.assertEqual(row['next_run_at_ms'], (1 << 63) - 1)
        owned = self.claim(row)
        with self.assertRaises(ValueError):
            self.registry.complete_persisted_job_claim(
                self.owner['uid'], 1, owned['token'], retry=1
            )
        self.assertIsNone(self.claim(row))

    def test_schema_eight_migration_preserves_definition(self):
        row = self.put()
        with self.registry.connect() as db:
            db.execute('DROP TABLE persisted_job_claims')
            db.execute('PRAGMA user_version=8')
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.listing(), [row])
        self.assertIsNotNone(self.claim(row))

    def test_schema_nine_timing_migration_rolls_back_on_invalid_definition(self):
        row = self.put()
        with self.registry.connect() as db:
            db.execute('ALTER TABLE persisted_jobs RENAME TO jobs_new')
            db.execute('''CREATE TABLE persisted_jobs(owner_uid INTEGER NOT NULL,job_id INTEGER NOT NULL,
                       apk_sha256 TEXT NOT NULL,revision TEXT NOT NULL,scheduled_at_ms INTEGER NOT NULL,
                       definition TEXT NOT NULL,PRIMARY KEY(owner_uid,job_id))''')
            db.execute('''INSERT INTO persisted_jobs SELECT owner_uid,job_id,apk_sha256,revision,
                       scheduled_at_ms,definition FROM jobs_new''')
            db.execute('DROP TABLE jobs_new')
            db.execute("UPDATE persisted_jobs SET definition='{}'")
            db.execute('PRAGMA user_version=9')
        with self.assertRaises(ValueError):
            PackageRegistry(self.registry.root)
        with self.registry.connect() as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 9)
            self.assertNotIn(
                'next_run_at_ms',
                {column['name'] for column in db.execute('PRAGMA table_info(persisted_jobs)')},
            )
            db.execute('UPDATE persisted_jobs SET definition=?', (json.dumps(self.job),))
        with patch(
            'shiraz.job_clock.sample',
            return_value=job_clock.Sample(
                boot_identity.current(), row['scheduled_at_ms'], 10000, 0
            ),
        ):
            self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.listing(), [dict(row, next_run_elapsed_ms=10500)])
