# SPDX-License-Identifier: GPL-3.0-only
import threading
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import test_job_store as fixtures

from shiraz.job_scheduler import JobScheduler
from shiraz.service_broker import ServiceBroker


class JobSchedulerTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.JobStoreTests()
        self.fixture.setUp()
        self.broker = ServiceBroker(self.fixture.registry)
        self.scheduler = JobScheduler(self.broker)

    def tearDown(self):
        self.broker.close()
        self.fixture.tearDown()

    def test_due_clock_and_automatic_completion(self):
        with fixtures.clock(1000):
            self.fixture.put(dict(self.fixture.job, minimum_latency_ms=1000))
        worker = SimpleNamespace(wait=Mock(return_value=False), terminal_cleanup_complete=True)
        with patch.object(self.broker, 'start_job', return_value=worker) as launch:
            with fixtures.clock(1999):
                self.scheduler.tick()
                launch.assert_not_called()
            with fixtures.clock(2000):
                with self.broker.lock:
                    self.scheduler.tick()
                    dispatches = list(self.broker.job_dispatches)
                self.assertEqual(len(dispatches), 1)
                self.assertTrue(dispatches[0].done.wait(5))
            self.assertEqual(self.fixture.listing(), [])
            launch.assert_called_once()

    def test_lifetime_loop_starts_once_and_stops(self):
        visited = threading.Event()
        with patch.object(
            self.fixture.registry, 'due_persisted_jobs', side_effect=lambda **_: visited.set() or []
        ):
            scheduler = self.broker.start_job_scheduler()
            self.assertIs(self.broker.start_job_scheduler(), scheduler)
            self.assertTrue(visited.wait(5))
            self.broker.close()
            self.assertFalse(scheduler.thread.is_alive())
            with self.assertRaisesRegex(RuntimeError, 'closed'):
                self.broker.start_job_scheduler()

    def test_candidates_rotate_past_busy_first_page(self):
        rows = [
            dict(package='test.owner', owner_uid=1, apk_sha256='a' * 64, id=i, revision='b' * 32)
            for i in range(130)
        ]
        with (
            patch.object(self.fixture.registry, 'due_persisted_jobs', return_value=rows),
            patch.object(self.broker, 'dispatch_persisted_job', return_value=None) as dispatch,
        ):
            for _ in range(3):
                before = dispatch.call_count
                self.scheduler.tick()
                self.assertEqual(dispatch.call_count - before, 64)
            self.assertEqual({call.args[3] for call in dispatch.call_args_list}, set(range(130)))

    def test_shutdown_stops_workers_before_waiting_for_scheduler(self):
        order = []
        process = Mock()
        process.close.side_effect = lambda: order.append('worker')
        scheduler = Mock()
        scheduler.close.side_effect = lambda: order.append('scheduler')
        self.broker.processes.add(process)
        self.broker.job_scheduler = scheduler
        self.broker.close()
        self.assertEqual(order, ['worker', 'scheduler'])
        scheduler.stop.set.assert_called_once()

    def test_quiescence_capacity_stop_and_failure_isolation(self):
        self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))
        with patch.object(
            self.broker, 'dispatch_persisted_job', side_effect=RuntimeError('launch failed')
        ) as dispatch:
            self.broker.quiesced.add(self.fixture.owner['package'])
            self.scheduler.tick()
            dispatch.assert_not_called()
            self.broker.quiesced.clear()
            self.broker.job_dispatches.update(
                Mock(done=Mock(is_set=Mock(return_value=True))) for _ in range(4)
            )
            self.scheduler.tick()
            dispatch.assert_not_called()
            self.broker.job_dispatches.clear()
            for _ in range(65):
                self.scheduler.tick()
            self.assertEqual(len(self.scheduler.failures), 64)
            self.assertEqual(self.scheduler.failures[-1]['error'], 'RuntimeError')
            self.scheduler.stop.set()
            self.scheduler.tick()
            self.assertEqual(dispatch.call_count, 65)


if __name__ == '__main__':
    unittest.main()
