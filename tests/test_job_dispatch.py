# SPDX-License-Identifier: GPL-3.0-only
import json
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace
from unittest.mock import Mock, patch

import test_job_store as fixtures

from shiraz.apk import FormatError
from shiraz.job_dispatch import JobLaunchError, start
from shiraz.job_requests import handle
from shiraz.job_scheduler import JobScheduler
from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker


class JobDispatchTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.JobStoreTests()
        self.fixture.setUp()
        self.registry = self.fixture.registry
        self.owner = self.fixture.owner
        self.release = threading.Event()
        self.outcome = Mock(return_value=False)

        def await_worker(timeout=None):
            if not self.release.wait(timeout):
                raise TimeoutError('still running')
            return self.outcome()

        self.worker = SimpleNamespace(
            wait=Mock(side_effect=await_worker),
            terminal_cleanup_complete=True,
            close=Mock(side_effect=self.release.set),
        )
        self.runs = []
        self.broker = SimpleNamespace(
            registry=self.registry,
            lock=threading.RLock(),
            closed=False,
            quiesced=set(),
            processes=set(),
            job_dispatches=set(),
            pending_retirements={},
            live_processes={},
            foreground_admissions={},
            sessions={},
            job_scheduler=None,
            sync_scheduler=None,
            service_restarts={},
            restart_threads=set(),
            start_job=Mock(return_value=self.worker),
        )
        self.broker.ready = threading.Condition(self.broker.lock)
        self.row = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))

    def tearDown(self):
        self.release.set()
        for run in self.runs:
            run.thread.join(5)
            self.assertFalse(run.thread.is_alive())
        self.fixture.tearDown()

    def start(self, row=None):
        row = row or self.row
        run = start(
            self.broker,
            self.owner['package'],
            self.owner['uid'],
            self.owner['apk_sha256'],
            row['id'],
            row['revision'],
        )
        if run is not None:
            self.runs.append(run)
        return run

    def test_storage_gate_and_running_loss_preserve_retry(self):
        # Seed a stored requirement to isolate dispatch from admission.
        definition = dict(self.row['definition'], version=2, requires_storage_not_low=True)
        with self.registry.connect() as db:
            db.execute('UPDATE persisted_jobs SET definition=?', (json.dumps(definition),))
        from shiraz.job_constraints import StorageObservation

        observation = StorageObservation(self.registry.root, 100, 50, 5)
        with patch('shiraz.job_constraints.observe_storage', return_value=None):
            self.assertIsNone(self.start())
        low = StorageObservation(self.registry.root, 100, 5, 5)
        with patch('shiraz.job_constraints.observe_storage', return_value=low):
            self.assertIsNone(self.start())
        self.broker.start_job.assert_not_called()
        self.assertEqual(self.fixture.listing()[0]['revision'], self.row['revision'])
        with patch('shiraz.job_constraints.observe_storage', return_value=observation):
            run = self.start()
            self.assertIsNotNone(run)
            JobScheduler(self.broker).reconcile_running()
            self.worker.close.assert_not_called()
        # Even a racing success packet must not delete the constraint-stopped job.
        with patch('shiraz.job_constraints.observe_storage', return_value=None):
            JobScheduler(self.broker).reconcile_running()
        self.assertEqual(run.wait(5), dict(applied=True, retry=True))
        row = self.fixture.listing()[0]
        self.assertEqual(row['definition'], definition)
        self.assertEqual(row['attempts'], 1)
        self.worker.close.assert_called_once()
        run.stop_for_constraints()
        self.worker.close.assert_called_once()  # Terminal work cannot be stopped twice.
        self.assertNotEqual(row['revision'], self.row['revision'])
        self.assertIsNone(self.start(row))  # Retry timing still applies.

    def test_deadline_dispatch_bypasses_storage_but_retry_does_not(self):
        # Host-only timestamp seed for deterministic deadline boundaries.
        definition = dict(
            self.row['definition'],
            version=2,
            requires_storage_not_low=True,
            deadline_ms=500,
            minimum_latency_ms=10000,
        )
        with self.registry.connect() as db:
            db.execute(
                'UPDATE persisted_jobs SET definition=?,scheduled_at_ms=1000,next_run_at_ms=1500,next_run_elapsed_ms=1500,deadline_elapsed_ms=1500',
                (json.dumps(definition),),
            )
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=None),
            fixtures.clock(1499),
        ):
            self.assertIsNone(self.start())
        self.outcome.return_value = True
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=None),
            fixtures.clock(1500),
        ):
            run = self.start()
            self.assertTrue(run.deadline_expired)
            self.assertTrue(self.broker.start_job.call_args.kwargs['deadline_expired'])
            JobScheduler(self.broker).reconcile_running()
            self.worker.close.assert_not_called()
            # A clock rollback cannot stop an already deadline-admitted run.
            with fixtures.clock(900):
                JobScheduler(self.broker).reconcile_running()
                self.worker.close.assert_not_called()
            self.release.set()
            self.assertEqual(run.wait(5), dict(applied=True, retry=True))
        retry = self.fixture.listing()[0]
        self.assertEqual(retry['definition'], definition)
        self.assertEqual(retry['attempts'], 1)
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=None),
            fixtures.clock(retry['next_run_at_ms']),
        ):
            self.assertIsNone(self.start(retry))
        self.assertEqual(self.broker.start_job.call_count, 1)
        from shiraz.job_constraints import StorageObservation

        healthy = StorageObservation(self.registry.root, 100, 50, 5)
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=healthy),
            fixtures.clock(retry['next_run_at_ms']),
        ):
            retried_run = self.start(retry)
            self.assertFalse(self.broker.start_job.call_args.kwargs['deadline_expired'])
            self.assertEqual(retried_run.wait(5), dict(applied=True, retry=True))

    def test_deadline_expiring_while_running_overrides_later_storage_loss(self):
        definition = dict(
            self.row['definition'], version=2, requires_storage_not_low=True, deadline_ms=500
        )
        with self.registry.connect() as db:
            db.execute(
                'UPDATE persisted_jobs SET definition=?,scheduled_at_ms=1000,next_run_at_ms=1000,next_run_elapsed_ms=1000,deadline_elapsed_ms=1500',
                (json.dumps(definition),),
            )
        from shiraz.job_constraints import StorageObservation

        healthy = StorageObservation(self.registry.root, 100, 50, 5)
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=healthy),
            fixtures.clock(1000),
        ):
            run = self.start()
            self.assertFalse(run.deadline_expired)
        with (
            patch('shiraz.job_constraints.observe_storage', return_value=None),
            fixtures.clock(1500),
        ):
            JobScheduler(self.broker).reconcile_running()
            self.assertTrue(run.deadline_expired)
            self.worker.close.assert_not_called()
        self.release.set()
        self.assertEqual(run.wait(5), dict(applied=True, retry=False))

    def test_acknowledged_constraint_callback_false_is_not_forced_to_retry(self):
        self.worker.stop_reason = 9
        self.worker.stop_for_constraints = self.worker.close
        run = self.start()
        run.stop_for_constraints()
        self.assertEqual(run.wait(5), dict(applied=True, retry=False))
        self.assertEqual(self.fixture.listing(), [])

    def test_foreground_stop_retries_interrupted_job_but_not_natural_completion(self):
        for reason in (14, 0):
            with self.subTest(reason=reason):
                self.release.clear()
                row = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))
                self.worker.stop_reason = reason
                self.worker.stop_for_foreground = self.worker.close
                run = self.start(row)
                run.stop_for_foreground()
                self.assertEqual(run.wait(5), dict(applied=True, retry=reason == 14))
                if reason == 14:
                    self.assertEqual(self.fixture.listing()[0]['attempts'], 1)
                else:
                    self.assertEqual(self.fixture.listing(), [])

    def test_terminal_completion_concurrent_and_restart(self):
        run = self.start()
        self.assertIsNone(self.start())
        self.release.set()
        with ThreadPoolExecutor(max_workers=8) as pool:
            outcomes = list(pool.map(lambda _: run.wait(), range(8)))
        self.assertEqual(outcomes, [dict(applied=True, retry=False)] * 8)
        self.assertEqual(self.fixture.listing(), [])
        reopened = PackageRegistry(self.registry.root)
        self.assertEqual(
            reopened.list_persisted_jobs(self.owner['uid'], self.owner['apk_sha256']), []
        )
        self.broker.start_job.assert_called_once()

    def test_observation_timeout_and_unconfirmed_cleanup_retain_claim(self):
        run = self.start()
        with self.assertRaises(TimeoutError):
            run.wait(0)
        self.assertIsNone(self.start())
        self.outcome.side_effect = RuntimeError('cleanup failed')
        self.worker.terminal_cleanup_complete = False
        self.release.set()
        with self.assertRaisesRegex(RuntimeError, 'reservation retained'):
            run.wait(5)
        self.assertIsNone(self.start())
        self.assertEqual(self.fixture.listing(), [self.row])
        self.assertIn(run, self.broker.job_dispatches)
        with self.assertRaisesRegex(RuntimeError, 'reservation retained'):
            run.drain()

    def test_completion_without_client_wait(self):
        run = self.start()
        self.release.set()
        self.assertTrue(run.done.wait(5))
        self.assertEqual(self.fixture.listing(), [])
        self.assertNotIn(run, self.broker.job_dispatches)
        self.worker.wait.assert_called_once_with()

    def test_database_failure_retains_reservation_and_is_not_retried(self):
        with patch.object(
            self.registry,
            'complete_persisted_job_claim',
            side_effect=OSError('database unavailable'),
        ) as complete:
            run = self.start()
            self.release.set()
            self.assertTrue(run.done.wait(5))
            for _ in range(2):
                with self.assertRaisesRegex(OSError, 'database unavailable'):
                    run.wait(0)
            complete.assert_called_once()
        self.assertIn(run, self.broker.job_dispatches)
        self.assertIsNone(self.start())
        self.assertEqual(self.fixture.listing(), [self.row])

    def test_broker_shutdown_drains_completion_without_client_wait(self):
        run = self.start()
        # A real process close unblocks the JobProcess reaper/wait path.
        process = Mock()
        process.close.side_effect = self.release.set
        self.broker.processes = {process}
        ServiceBroker.close(self.broker)
        self.assertTrue(run.done.is_set())
        self.assertEqual(self.fixture.listing(), [])
        self.assertEqual(self.broker.job_dispatches, set())

    def test_failure_requeues_once_after_verified_teardown(self):
        run = self.start()
        self.outcome.side_effect = RuntimeError('worker crashed')
        self.release.set()
        for _ in range(2):
            with self.assertRaisesRegex(RuntimeError, 'claim transition recorded'):
                run.wait()
        row = self.fixture.listing()[0]
        self.assertEqual(row['attempts'], 1)
        self.assertNotEqual(row['revision'], self.row['revision'])
        self.assertGreater(row['next_run_at_ms'], self.row['next_run_at_ms'])
        self.assertIsNone(self.start(row))

    def test_old_completion_preserves_replacement_and_cancel(self):
        run = self.start()
        replacement = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))
        self.assertIsNone(self.start(replacement))
        self.release.set()
        self.assertEqual(run.wait(5), dict(applied=False, retry=False))
        self.assertEqual(self.fixture.listing(), [replacement])
        self.release.clear()
        following = self.start(replacement)
        self.registry.cancel_persisted_job(self.owner['uid'], self.owner['apk_sha256'], 1)
        self.release.set()
        self.assertFalse(following.wait(5)['applied'])
        self.assertEqual(self.fixture.listing(), [])

    def test_launch_failure_retains_claim_and_generation_is_pinned(self):
        def failed_launch(*args, **kwargs):
            with self.assertRaises(FormatError):
                with self.registry._generation_lock(self.owner['package'], exclusive=True):
                    pass
            raise RuntimeError('ambiguous startup failure')

        self.broker.start_job.side_effect = failed_launch
        with self.assertRaises(JobLaunchError) as captured:
            self.start()
        self.assertEqual(captured.exception.claim['revision'], self.row['revision'])
        self.assertIsNone(self.start())

    def test_not_due_and_quiesced_do_not_launch(self):
        with fixtures.clock(0):
            self.assertIsNone(self.start())
        self.broker.quiesced.add(self.owner['package'])
        with self.assertRaises(RuntimeError):
            self.start()
        self.broker.start_job.assert_not_called()

    def test_busy_process_does_not_reserve_job(self):
        key = (self.owner['package'], self.owner['apk_sha256'], self.owner['package'])
        self.broker.live_processes[key] = object()
        self.assertIsNone(self.start())
        self.broker.live_processes.clear()
        self.assertIsNotNone(self.start())

    def test_foreground_admission_does_not_claim_job(self):
        key = (self.owner['package'], self.owner['apk_sha256'], self.owner['package'])
        self.broker.foreground_admissions[key] = object()
        self.assertIsNone(self.start())
        self.broker.start_job.assert_not_called()
        self.broker.foreground_admissions.clear()
        self.assertIsNotNone(self.start())

    def test_supervisor_capacity_does_not_reserve_job(self):
        self.broker.job_dispatches.update(object() for _ in range(64))
        self.assertIsNone(self.start())
        self.broker.start_job.assert_not_called()
        self.broker.job_dispatches.clear()
        self.assertIsNotNone(self.start())

    def test_shutdown_reports_failed_finalization(self):
        self.worker.terminal_cleanup_complete = False
        run = self.start()
        self.release.set()
        self.assertTrue(run.done.wait(5))
        with self.assertRaisesRegex(RuntimeError, 'reservation retained'):
            ServiceBroker.close(self.broker)
        self.assertEqual(self.fixture.listing(), [self.row])

    def test_guest_replacement_and_cancel_stop_old_invocation(self):
        identity = SimpleNamespace(
            package=self.owner['package'], uid=self.owner['uid'], sha256=self.owner['apk_sha256']
        )
        run = self.start()
        reply = handle(
            self.broker,
            identity,
            dict(op='schedule_job', definition=dict(self.fixture.job, minimum_latency_ms=0)),
        )
        self.assertTrue(reply['ok'])
        self.worker.close.assert_called_once()
        self.assertFalse(run.wait(5)['applied'])
        replacement = self.fixture.listing()[0]
        self.assertEqual(replacement['revision'], reply['revision'])
        self.release.clear()
        following = self.start(replacement)
        reply = handle(self.broker, identity, dict(op='cancel_job', id=replacement['id']))
        self.assertTrue(reply['removed'])
        self.assertFalse(following.wait(5)['applied'])
        self.assertEqual(self.fixture.listing(), [])

    def test_cancel_all_does_not_touch_job_scheduled_during_cleanup(self):
        identity = SimpleNamespace(
            package=self.owner['package'], uid=self.owner['uid'], sha256=self.owner['apk_sha256']
        )
        run = self.start()
        later = []

        def cleanup_with_concurrent_submission():
            saved = self.fixture.put(dict(self.fixture.job, id=2, minimum_latency_ms=0))
            claim = self.registry.claim_due_persisted_job(
                identity.uid, identity.sha256, 2, saved['revision']
            )
            later.append((saved, claim))
            self.release.set()
            self.assertTrue(run.done.wait(5))

        self.worker.close.side_effect = cleanup_with_concurrent_submission
        reply = handle(self.broker, identity, dict(op='cancel_all_jobs'))
        self.assertEqual(reply, dict(ok=True, removed=True, cleanup_pending=False))
        self.worker.close.assert_called_once()
        self.assertFalse(run.wait(0)['applied'])
        self.assertEqual(self.fixture.listing(), [later[0][0]])
        self.assertTrue(
            self.registry.release_persisted_job_claim(identity.uid, 2, later[0][1]['token'])
        )

    def test_owner_observes_other_host_cancellation_and_replacement(self):
        other = PackageRegistry(self.registry.root)
        for replacement in (False, True):
            saved = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))
            run = self.start(saved)
            if replacement:
                newer = other.store_shared_job(
                    self.owner['uid'],
                    self.owner['apk_sha256'],
                    dict(self.fixture.job, minimum_latency_ms=60000),
                )
            else:
                other.cancel_all_shared_jobs(self.owner['uid'], self.owner['apk_sha256'])
            self.assertFalse(
                other.job_claim_is_current(self.owner['uid'], saved['id'], run.claim['token'])
            )
            JobScheduler(self.broker).reconcile_running()
            self.assertTrue(run.done.wait(5))
            self.assertFalse(run.wait(0)['applied'])
            self.assertEqual(self.fixture.listing(), [newer] if replacement else [])
            self.release.clear()
        self.assertEqual(self.worker.close.call_count, 2)

    def test_invalidation_read_failure_does_not_stop_or_release_worker(self):
        run = self.start()
        scheduler = JobScheduler(self.broker)
        with patch.object(
            self.registry, 'job_claim_is_current', side_effect=OSError('database unavailable')
        ):
            scheduler.reconcile_running()
        self.worker.close.assert_not_called()
        self.assertFalse(run.done.is_set())
        self.assertEqual(scheduler.failures[-1]['error'], 'OSError')
        self.assertTrue(
            self.registry.job_claim_is_current(
                self.owner['uid'], self.row['id'], run.claim['token']
            )
        )

    def test_guest_write_stays_committed_after_cleanup_failure(self):
        identity = SimpleNamespace(
            package=self.owner['package'], uid=self.owner['uid'], sha256=self.owner['apk_sha256']
        )
        self.start()
        self.worker.close.side_effect = OSError('cleanup unavailable')
        reply = handle(
            self.broker,
            identity,
            dict(op='schedule_job', definition=dict(self.fixture.job, minimum_latency_ms=0)),
        )
        self.assertTrue(reply['ok'])
        self.assertTrue(reply['cleanup_pending'])
        replacement = self.fixture.listing()[0]
        self.assertEqual(replacement['revision'], reply['revision'])
        self.assertIsNone(self.start(replacement))

    def test_guest_cancel_reports_claim_owned_outside_this_broker(self):
        identity = SimpleNamespace(
            package=self.owner['package'], uid=self.owner['uid'], sha256=self.owner['apk_sha256']
        )
        claim = self.registry.claim_due_persisted_job(
            identity.uid, identity.sha256, self.row['id'], self.row['revision']
        )
        reply = handle(self.broker, identity, dict(op='cancel_job', id=self.row['id']))
        self.assertTrue(reply['removed'])
        self.assertTrue(reply['cleanup_pending'])
        self.assertEqual(self.fixture.listing(), [])
        # No takeover/release merely because this broker has no worker handle.
        replacement = self.fixture.put(dict(self.fixture.job, minimum_latency_ms=0))
        self.assertIsNone(self.start(replacement))
        self.assertTrue(
            self.registry.release_persisted_job_claim(identity.uid, self.row['id'], claim['token'])
        )


if __name__ == '__main__':
    unittest.main()
