# SPDX-License-Identifier: GPL-3.0-only
import socket
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

import test_sync_requests as fixtures

from shiraz.broker_wire import write_packet
from shiraz.service_broker import ServiceBroker
from shiraz.sync_dispatch import SyncDispatchError, outcome


def reply(**changes):
    result = dict(
        ok=True,
        canceled=False,
        hard_error=False,
        soft_error=False,
        full_sync_requested=False,
        more_records=False,
        partial_sync_unavailable=False,
        delay_until=0,
        already_in_progress=False,
    )
    result.update(changes)
    return result


class Worker:
    response = reply()
    fail_cleanup = False

    def __init__(self, broker, adapter):
        self.key = (adapter['package'], adapter['apk_sha256'], adapter['component'])
        self.references = 0
        self.started = self.closed = self.cleanup_complete = False

    def dispatch_sync(self, claim):
        host, child = socket.socketpair()
        with child:
            write_packet(child, self.response)
        return host

    def close(self):
        if self.fail_cleanup:
            raise RuntimeError('cleanup uncertain')
        self.closed = self.cleanup_complete = True


class SyncDispatchTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.SyncRequestTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.broker = ServiceBroker(self.registry)
        self.key = self.fixture.request()[0]

    def tearDown(self):
        for worker in self.broker.processes:
            worker.fail_cleanup = False
        self.broker.close()
        self.fixture.tearDown()

    def claims(self):
        with self.registry.connect() as db:
            return db.execute('SELECT COUNT(*) FROM sync_request_claims').fetchone()[0]

    def test_dispatch_and_terminal_transition(self):
        with patch('shiraz.sync_dispatch.ServiceProcess', Worker):
            self.assertEqual(
                self.broker.dispatch_sync_request(self.key),
                dict(status='succeeded', delay_until=0, applied=True),
            )
        self.assertEqual(self.registry.pending_sync_requests(), [])
        self.assertEqual(self.claims(), 0)
        self.assertEqual(self.broker.processes, set())

    def test_retry_and_hard_failure_preserve_pending(self):
        for flags, status in [
            (dict(soft_error=True, delay_until=123), 'retry'),
            (dict(hard_error=True), 'failed'),
            (dict(canceled=True), 'canceled'),
        ]:
            with (
                self.subTest(status=status),
                patch.object(Worker, 'response', reply(**flags)),
                patch('shiraz.sync_dispatch.ServiceProcess', Worker),
            ):
                key = self.fixture.request(extras={'case': dict(type='string', value=status)})[0]
                self.assertEqual(self.broker.dispatch_sync_request(key)['status'], status)
                self.assertTrue(
                    any(item['request_id'] == key for item in self.registry.pending_sync_requests())
                )
                self.assertEqual(self.claims(), 0)

    def test_uncertain_reply_releases_only_after_cleanup(self):
        with (
            patch.object(Worker, 'response', dict(ok=False, delivery_uncertain=True)),
            patch('shiraz.sync_dispatch.ServiceProcess', Worker),
        ):
            with self.assertRaises(SyncDispatchError) as caught:
                self.broker.dispatch_sync_request(self.key)
        self.assertFalse(caught.exception.retained)
        self.assertEqual(self.claims(), 0)
        self.assertEqual(len(self.registry.pending_sync_requests()), 1)

    def test_failed_cleanup_retains_claim(self):
        with (
            patch.object(Worker, 'fail_cleanup', True),
            patch('shiraz.sync_dispatch.ServiceProcess', Worker),
        ):
            with self.assertRaises(SyncDispatchError) as caught:
                self.broker.dispatch_sync_request(self.key)
        self.assertTrue(caught.exception.retained)
        self.assertEqual(self.claims(), 1)

    def test_shared_live_worker_uncertainty_retains_claim(self):
        adapter = self.registry.pending_sync_requests()[0]['adapter']
        worker = Worker(self.broker, adapter)
        worker.references = 1
        worker.response = dict(ok=False, delivery_uncertain=True)
        self.broker.targets[worker.key] = worker
        self.broker.processes.add(worker)
        with self.assertRaises(SyncDispatchError) as caught:
            self.broker.dispatch_sync_request(self.key)
        self.assertTrue(caught.exception.retained)
        self.assertFalse(worker.closed)
        self.assertEqual(worker.references, 1)
        self.assertEqual(self.claims(), 1)

    def test_strict_outcome(self):
        for malformed in [
            reply(hard_error=1),
            reply(delay_until=True),
            reply(delay_until=-1),
            reply(extra=True),
            dict(ok=True),
        ]:
            with self.subTest(fields=list(malformed)), self.assertRaises(ValueError):
                outcome(malformed)

    def test_account_removal_cancels_waiting_dispatch(self):
        ready = threading.Event()

        class WaitingWorker(Worker):
            def dispatch_sync(self, claim):
                host, self.peer = socket.socketpair()
                ready.set()
                return host

            def close(self):
                self.peer.close()
                super().close()

        with (
            patch('shiraz.sync_dispatch.ServiceProcess', WaitingWorker),
            ThreadPoolExecutor(max_workers=1) as pool,
        ):
            future = pool.submit(self.broker.dispatch_sync_request, self.key, timeout=5)
            self.assertTrue(ready.wait(2))
            owner = self.fixture.owner
            self.registry.account_operation(
                owner['uid'],
                owner['apk_sha256'],
                dict(action='remove', name='fixture-user', type='test.accounts'),
            )
            with self.assertRaises(SyncDispatchError) as caught:
                future.result(timeout=4)
        self.assertFalse(caught.exception.retained)
        self.assertEqual(self.claims(), 0)
        self.assertEqual(self.registry.pending_sync_requests(), [])


if __name__ == '__main__':
    unittest.main()
