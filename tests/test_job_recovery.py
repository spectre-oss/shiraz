# SPDX-License-Identifier: GPL-3.0-only
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import test_job_store as fixtures

from shiraz import job_lifetime, job_recovery
from shiraz.job_scheduler import JobScheduler
from shiraz.service_broker import ServiceBroker


class JobRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.JobStoreTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.owner = self.fixture.owner
        self.row = self.fixture.put()
        self.claim = self.registry.claim_persisted_job(
            self.owner['uid'], self.owner['apk_sha256'], 1, self.row['revision']
        )
        self.lock = None

    def tearDown(self):
        if self.lock is not None:
            self.lock.close()
        self.fixture.tearDown()

    def register(self):
        self.lock = job_lifetime.create(self.registry.root, self.claim['token'])
        job_recovery.register(self.registry, self.claim, self.lock)

    def recover(self):
        return job_recovery.recover(self.registry, self.owner['uid'], 1, self.claim['token'])

    def test_live_holder_excluded_then_unknown_completion_retried_once(self):
        self.register()
        self.assertFalse(self.recover())
        self.assertEqual(self.fixture.listing(), [self.row])
        self.lock.close()
        with ServiceBroker(self.registry) as broker:
            scheduler = JobScheduler(broker)
            scheduler.tick()
            self.assertEqual(list(scheduler.failures), [])
        saved = self.fixture.listing()[0]
        self.assertEqual(saved['attempts'], 1)
        self.assertNotEqual(saved['revision'], self.row['revision'])
        self.assertFalse(self.recover())
        self.assertEqual(self.fixture.listing(), [saved])
        self.assertEqual(job_recovery.candidates(self.registry), [])

    def test_legacy_and_missing_or_replaced_lock_fail_closed(self):
        self.assertFalse(self.recover())
        self.register()
        self.lock.close()
        path = Path(self.registry.root) / 'job-lifetimes' / (self.claim['token'] + '.lock')
        moved = path.with_suffix('.original')
        path.rename(moved)
        with self.assertRaises(FileNotFoundError):
            self.recover()
        with job_lifetime.create(self.registry.root, self.claim['token']):
            pass
        with self.assertRaisesRegex(RuntimeError, 'identity changed'):
            self.recover()
        self.assertEqual(self.fixture.listing(), [self.row])

    def test_recovery_of_old_claim_preserves_replacement(self):
        self.register()
        replacement = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=1000))
        self.lock.close()
        self.assertFalse(self.recover())
        self.assertEqual(self.fixture.listing(), [replacement])
        self.assertEqual(job_recovery.candidates(self.registry), [])
        self.assertIsNotNone(
            self.registry.claim_persisted_job(
                self.owner['uid'], self.owner['apk_sha256'], 1, replacement['revision']
            )
        )

    def test_retirement_excludes_live_holders_and_claims(self):
        self.register()
        self.assertFalse(job_recovery.retire(self.registry, self.claim['token']))
        self.lock.close()
        self.assertFalse(job_recovery.retire(self.registry, self.claim['token']))
        self.assertTrue(self.recover())
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(
                pool.map(
                    lambda _: job_recovery.retire(self.registry, self.claim['token']), range(2)
                )
            )
        self.assertEqual(sum(results), 1)
        self.assertFalse(job_recovery.retire(self.registry, self.claim['token']))

    def test_bounded_collector_preserves_unrelated_files(self):
        tokens = [format(index + 1, '032x') for index in range(130)]
        for token in tokens:
            with job_lifetime.create(self.registry.root, token):
                pass
        directory = Path(self.registry.root) / 'job-lifetimes'
        unrelated = directory / 'operator-note'
        unrelated.write_text('leave intact')
        collector = job_recovery.LockCollector(self.registry)
        try:
            collector.tick()
            remaining = list(directory.glob('*.lock'))
            self.assertGreaterEqual(len(remaining), 66)
            self.assertLess(len(remaining), 130)
            for _ in range(3):
                collector.tick()
            self.assertEqual(list(directory.glob('*.lock')), [])
            self.assertEqual(unrelated.read_text(), 'leave intact')
        finally:
            collector.close()

    def test_retirement_rejects_symlink_without_touching_target(self):
        self.register()
        path = Path(self.registry.root) / 'job-lifetimes' / ('f' * 32 + '.lock')
        target = Path(self.registry.root) / 'unrelated'
        target.write_text('keep')
        path.symlink_to(target)
        with self.assertRaises(OSError):
            job_recovery.retire(self.registry, 'f' * 32)
        self.assertEqual(target.read_text(), 'keep')
        self.assertTrue(path.is_symlink())
