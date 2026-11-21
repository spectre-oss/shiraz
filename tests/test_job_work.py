# SPDX-License-Identifier: GPL-3.0-only
import unittest
from concurrent.futures import ThreadPoolExecutor

import test_job_store

from shiraz import job_store, job_work
from shiraz.package_registry import PackageRegistry


class JobWorkTests(unittest.TestCase):
    def setUp(self):
        self.fixture = test_job_store.JobStoreTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.uid = self.fixture.owner['uid']
        self.sha = self.fixture.owner['apk_sha256']
        self.definition = dict(self.fixture.job, persisted=False)
        self.job = self.registry.store_shared_job(self.uid, self.sha, self.definition)

    def tearDown(self):
        self.fixture.tearDown()

    def append(self, payload=b'intent parcel'):
        return job_work.append(self.registry, self.uid, self.sha, 1, self.job['revision'], payload)

    def claim(self):
        return job_store.claim(self.registry, self.uid, self.sha, 1, self.job['revision'])['token']

    def test_reopen_retry_redelivers_only_unacknowledged(self):
        first, second = self.append(b'first'), self.append(b'second')
        token = self.claim()
        self.assertEqual(
            job_work.dequeue(self.registry, self.uid, 1, token),
            dict(id=first, deliveries=1, payload=b'first'),
        )
        self.assertTrue(job_work.complete(self.registry, self.uid, 1, token, first))
        self.assertFalse(job_work.complete(self.registry, self.uid, 1, token, first))
        self.assertEqual(job_work.dequeue(self.registry, self.uid, 1, token)['id'], second)
        reopened = PackageRegistry(self.registry.root)
        self.assertIsNone(job_work.dequeue(reopened, self.uid, 1, token))
        self.assertTrue(job_store.complete_claim(reopened, self.uid, 1, token, retry=True))
        self.job = self.registry.list_persisted_jobs(self.uid, self.sha)[0]
        new_token = self.claim()
        with self.assertRaises(PermissionError):
            job_work.complete(self.registry, self.uid, 1, token, second)
        self.assertEqual(
            job_work.dequeue(reopened, self.uid, 1, new_token),
            dict(id=second, deliveries=2, payload=b'second'),
        )

    def test_concurrent_dequeue_is_exclusive(self):
        ids = {self.append() for _ in range(12)}
        token = self.claim()
        with ThreadPoolExecutor(max_workers=4) as pool:
            delivered = list(
                pool.map(lambda _: job_work.dequeue(self.registry, self.uid, 1, token), range(16))
            )
        self.assertEqual({item['id'] for item in delivered if item}, ids)
        self.assertEqual(sum(item is not None for item in delivered), 12)

    def test_completion_requires_delivery_and_current_claim(self):
        work_id = self.append()
        token = self.claim()
        self.assertFalse(job_work.complete(self.registry, self.uid, 1, token, work_id))
        with self.assertRaises(PermissionError):
            job_work.dequeue(self.registry, self.uid, 1, '0' * 32)
        job_work.dequeue(self.registry, self.uid, 1, token)
        self.assertTrue(job_work.complete(self.registry, self.uid, 1, token, work_id))

    def test_replace_drops_old_work_without_reusing_ids(self):
        old_id = self.append()
        token = self.claim()
        job_work.dequeue(self.registry, self.uid, 1, token)
        old_revision = self.job['revision']
        self.job = self.registry.store_shared_job(self.uid, self.sha, self.definition)
        new_id = self.append()
        self.assertGreater(new_id, old_id)
        with self.assertRaises(PermissionError):
            job_work.append(self.registry, self.uid, self.sha, 1, old_revision, b'old')
        with self.assertRaises(PermissionError):
            job_work.complete(self.registry, self.uid, 1, token, old_id)
        job_store.release_claim(self.registry, self.uid, 1, token)
        fresh = self.claim()
        self.assertEqual(job_work.dequeue(self.registry, self.uid, 1, fresh)['id'], new_id)
        self.assertIsNone(job_work.dequeue(self.registry, self.uid, 1, fresh))

    def test_cancel_removes_payloads(self):
        self.append()
        self.registry.cancel_persisted_job(self.uid, self.sha, 1)
        with self.registry.connect() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM job_work_items').fetchone()[0], 0)

    def test_limits_and_owner_identity(self):
        for payload in (b'', b'x' * (job_work.MAX_PAYLOAD + 1), 'text'):
            with self.assertRaises(ValueError):
                self.append(payload)
        with self.assertRaises(PermissionError):
            job_work.append(self.registry, self.uid, '0' * 64, 1, self.job['revision'], b'x')
        for _ in range(job_work.PER_JOB_LIMIT):
            self.append(b'x')
        with self.assertRaises(ValueError):
            self.append(b'x')

    def test_nonpersisted_one_off_required(self):
        self.job = self.registry.store_shared_job(
            self.uid, self.sha, dict(self.definition, persisted=True)
        )
        with self.assertRaises(ValueError):
            self.append()

    def test_atomic_create_and_concurrent_identical_enqueue(self):
        definition = dict(self.definition, id=22)
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(
                pool.map(
                    lambda i: job_work.enqueue(
                        self.registry, self.uid, self.sha, definition, bytes([i])
                    ),
                    range(12),
                )
            )
        self.assertEqual(len({item['revision'] for item in results}), 1)
        self.assertEqual(len({item['work_id'] for item in results}), 12)
        token = job_store.claim(self.registry, self.uid, self.sha, 22, results[0]['revision'])[
            'token'
        ]
        delivered = [
            job_work.dequeue(self.registry, self.uid, 22, token)['payload'] for _ in range(12)
        ]
        self.assertEqual(set(delivered), {bytes([i]) for i in range(12)})

    def test_enqueue_identical_job_preserves_running_claim(self):
        token = self.claim()
        result = job_work.enqueue(self.registry, self.uid, self.sha, self.definition, b'new work')
        self.assertEqual(result['revision'], self.job['revision'])
        self.assertEqual(result['next_run_elapsed_ms'], self.job['next_run_elapsed_ms'])
        self.assertEqual(
            job_work.dequeue(self.registry, self.uid, 1, token)['payload'], b'new work'
        )

    def test_changed_definition_preserves_unacknowledged_work(self):
        first = self.append(b'old')
        token = self.claim()
        job_work.dequeue(self.registry, self.uid, 1, token)
        self.job = job_work.enqueue(
            self.registry,
            self.uid,
            self.sha,
            dict(self.definition, minimum_latency_ms=1000),
            b'new',
        )
        with self.assertRaises(PermissionError):
            job_work.complete(self.registry, self.uid, 1, token, first)
        self.assertIsNone(
            job_store.claim(self.registry, self.uid, self.sha, 1, self.job['revision'])
        )
        job_store.release_claim(self.registry, self.uid, 1, token)
        new_token = self.claim()
        redelivery = job_work.dequeue(self.registry, self.uid, 1, new_token)
        self.assertEqual(
            (redelivery['id'], redelivery['deliveries'], redelivery['payload']), (first, 2, b'old')
        )
        self.assertEqual(job_work.dequeue(self.registry, self.uid, 1, new_token)['payload'], b'new')

    def test_enqueue_capacity_failure_rolls_back_definition(self):
        for _ in range(job_work.PER_JOB_LIMIT):
            self.append(b'x')
        with self.assertRaises(ValueError):
            job_work.enqueue(
                self.registry,
                self.uid,
                self.sha,
                dict(self.definition, minimum_latency_ms=1000),
                b'overflow',
            )
        current = self.registry.list_persisted_jobs(self.uid, self.sha)[0]
        self.assertEqual(current, self.job)
        with self.registry.connect() as db:
            self.assertEqual(
                db.execute('SELECT count(*) FROM job_work_items').fetchone()[0],
                job_work.PER_JOB_LIMIT,
            )

    def test_new_job_is_not_left_behind_when_item_insert_fails(self):
        from unittest.mock import patch

        with patch('shiraz.job_work.GLOBAL_LIMIT', 0):
            with self.assertRaises(ValueError):
                job_work.enqueue(
                    self.registry, self.uid, self.sha, dict(self.definition, id=23), b'new'
                )
        self.assertEqual(
            [job['id'] for job in self.registry.list_persisted_jobs(self.uid, self.sha)], [1]
        )

    def test_sealed_worker_completion_cannot_delete_later_enqueue(self):
        token = self.claim()
        self.assertTrue(job_work.seal_drained(self.registry, self.uid, 1, token))
        self.assertTrue(job_work.seal_drained(self.registry, self.uid, 1, token))
        with self.assertRaises(PermissionError):
            job_work.dequeue(self.registry, self.uid, 1, token)
        self.job = job_work.enqueue(
            self.registry, self.uid, self.sha, self.definition, b'after drain'
        )
        self.assertFalse(job_store.complete_claim(self.registry, self.uid, 1, token))
        fresh = self.claim()
        self.assertEqual(
            job_work.dequeue(self.registry, self.uid, 1, fresh)['payload'], b'after drain'
        )
        with self.registry.connect() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM job_work_drains').fetchone()[0], 0)

    def test_pending_and_delivered_items_prevent_sealing(self):
        work_id = self.append()
        token = self.claim()
        self.assertFalse(job_work.seal_drained(self.registry, self.uid, 1, token))
        job_work.dequeue(self.registry, self.uid, 1, token)
        self.assertFalse(job_work.seal_drained(self.registry, self.uid, 1, token))
        job_work.complete(self.registry, self.uid, 1, token, work_id)
        self.assertTrue(job_work.seal_drained(self.registry, self.uid, 1, token))

    def test_early_success_preserves_pending_work(self):
        token = self.claim()
        self.append(b'enqueued before completion')
        self.assertTrue(job_store.complete_claim(self.registry, self.uid, 1, token))
        self.job = self.registry.list_persisted_jobs(self.uid, self.sha)[0]
        self.assertEqual(self.job['attempts'], 0)
        fresh = self.claim()
        self.assertEqual(job_work.dequeue(self.registry, self.uid, 1, fresh)['deliveries'], 1)

    def test_early_success_retries_unacknowledged_delivery(self):
        self.append()
        token = self.claim()
        job_work.dequeue(self.registry, self.uid, 1, token)
        self.assertTrue(job_store.complete_claim(self.registry, self.uid, 1, token))
        self.job = self.registry.list_persisted_jobs(self.uid, self.sha)[0]
        self.assertEqual(self.job['attempts'], 1)
        fresh = self.claim()
        self.assertEqual(job_work.dequeue(self.registry, self.uid, 1, fresh)['deliveries'], 2)

    def test_private_wire_does_not_accept_guest_claim_identity(self):
        from shiraz.job_work_wire import handle

        token = self.claim()
        claim = dict(owner_uid=self.uid, id=1, token=token)
        for request in (
            {'op': 'work_dequeue', 'token': token},
            {'op': 'work_complete', 'id': 1, 'owner_uid': self.uid},
            {'op': 'unrecognized'},
        ):
            with self.assertRaises(ValueError):
                handle(self.registry, claim, request)
        with self.assertRaises(PermissionError):
            handle(self.registry, None, {'op': 'work_dequeue'})

    def test_private_wire_delivery_ack_and_drain(self):
        import base64

        from shiraz.job_work_wire import handle

        work_id = self.append(b'\x00\xffparcel')
        token = self.claim()
        claim = dict(owner_uid=self.uid, id=1, token=token)
        reply = handle(self.registry, claim, {'op': 'work_dequeue'})
        self.assertEqual(reply['item']['id'], work_id)
        self.assertEqual(base64.b64decode(reply['item']['payload']), b'\x00\xffparcel')
        self.assertFalse(reply['drained'])
        self.assertEqual(
            handle(self.registry, claim, {'op': 'work_dequeue'}),
            dict(ok=True, item=None, drained=False),
        )
        self.assertEqual(
            handle(self.registry, claim, {'op': 'work_complete', 'id': work_id}),
            dict(ok=True, completed=True, drained=True),
        )
        with self.assertRaises(PermissionError):
            handle(self.registry, claim, {'op': 'work_dequeue'})


if __name__ == '__main__':
    unittest.main()
