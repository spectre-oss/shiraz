# SPDX-License-Identifier: GPL-3.0-only
import socket
import subprocess
import threading
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from shiraz.job_process import JobProcess
from shiraz.service_process import ServiceProcess


class JobProcessTests(unittest.TestCase):
    def test_deadline_metadata_rejected_before_process_creation(self):
        definition = dict(
            version=1,
            id=1,
            component='test.jobs.Worker',
            persisted=False,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={},
        )
        with patch.object(ServiceProcess, '__init__', return_value=None) as spawn:
            for expired in (1, None, 'true', True):
                with self.subTest(expired=expired), self.assertRaises(ValueError):
                    JobProcess(
                        None,
                        {'component': definition['component']},
                        definition,
                        deadline_expired=expired,
                    )
            spawn.assert_not_called()
            worker = JobProcess(
                None,
                {'component': definition['component']},
                dict(definition, deadline_ms=0),
                deadline_expired=True,
            )
            self.assertTrue(worker.deadline_expired)
            self.assertNotIn('deadline_expired', worker.definition)

    def test_result_requires_exact_packet_and_successful_exit(self):
        for reply, status, accepted in [
            ({'ok': True, 'retry': False, 'stop_reason': 0}, 0, True),
            ({'ok': True, 'retry': True, 'stop_reason': 9}, 0, True),
            ({'ok': True, 'retry': False, 'stop_reason': True}, 0, False),
            ({'ok': True, 'retry': False, 'stop_reason': 8}, 0, False),
            ({'ok': True, 'retry': False}, 0, False),
            ({'ok': True, 'retry': 1}, 0, False),
            ({'ok': 1, 'retry': False, 'stop_reason': 0}, 0, False),
            ({'ok': True}, 0, False),
            ({'ok': True, 'retry': False, 'extra': 0}, 0, False),
            ({'ok': True, 'retry': False, 'stop_reason': 0}, 10, False),
        ]:
            with self.subTest(reply=reply, status=status):
                worker = JobProcess.__new__(JobProcess)
                worker.control = Mock()
                worker.endpoint = Mock()
                worker.endpoint.dup.return_value = unittest.mock.MagicMock()
                worker.definition = {}
                worker.deadline_expired = False
                worker.timeout = 1
                worker.process = Mock()
                worker.process.wait.return_value = status
                worker.done = threading.Event()
                worker.cancelled = threading.Event()
                worker.error = None
                worker.retry = None
                worker.definition_sent = threading.Event()
                worker.cleanup_complete = False
                worker.broker = SimpleNamespace(lock=threading.RLock(), processes={worker})

                def cleanup():
                    worker.cleanup_complete = True

                with (
                    patch('shiraz.job_process.write_packet'),
                    patch('shiraz.job_process.read_packet', return_value=(reply, [])),
                    patch.object(ServiceProcess, 'close', side_effect=cleanup),
                    patch.object(JobProcess, '_drain_session'),
                ):
                    worker._reap()
                self.assertTrue(worker.done.is_set())
                self.assertNotIn(worker, worker.broker.processes)
                worker.control.close.assert_called_once()
                if accepted:
                    self.assertIs(worker.wait(0), reply['retry'])
                    worker.cancelled.set()
                    with self.assertRaisesRegex(RuntimeError, 'cancelled'):
                        worker.wait(0)
                else:
                    with self.assertRaises(RuntimeError):
                        worker.wait(0)

    def test_cooperative_stop_is_once_and_failure_falls_back(self):
        for reason in (9, 14):
            for delivered, completed, write_error in (
                (True, True, False),
                (False, False, False),
                (True, False, False),
                (True, False, True),
            ):
                worker = JobProcess.__new__(JobProcess)
                worker.stop_lock = threading.Lock()
                worker.stop_requested = False
                worker.done = Mock(
                    is_set=Mock(return_value=False), wait=Mock(return_value=completed)
                )
                worker.definition_sent = Mock(wait=Mock(return_value=delivered))
                worker.endpoint = unittest.mock.MagicMock()
                worker.close = Mock()
                with patch(
                    'shiraz.job_process.write_packet',
                    side_effect=OSError('broken') if write_error else None,
                ) as write:
                    (worker.stop_for_constraints if reason == 9 else worker.stop_for_foreground)()
                    (worker.stop_for_constraints if reason == 9 else worker.stop_for_foreground)()
                    self.assertEqual(write.call_count, int(delivered))
                    self.assertEqual(
                        worker.close.call_count,
                        int(not (delivered and completed and not write_error)),
                    )
                    if delivered:
                        self.assertEqual(write.call_args.args[1], {'stop_reason': reason})

    def test_group_kill_once_and_failed_termination_retains_lease(self):
        for failure in (False, True):
            with self.subTest(failure=failure):
                worker = ServiceProcess.__new__(ServiceProcess)
                worker.control, worker.endpoint = socket.socketpair()
                worker.lock = threading.Lock()
                worker.cleanup_complete = False
                worker.closed = False
                worker.lease = Mock()
                worker.process = Mock(pid=1234567, returncode=-9)
                worker.process.wait.side_effect = [subprocess.TimeoutExpired('fixture', 3), -9]
                try:
                    with patch(
                        'shiraz.service_process.os.killpg',
                        side_effect=PermissionError('fixture') if failure else None,
                    ) as kill:
                        if failure:
                            with self.assertRaises(PermissionError):
                                worker.close()
                            worker.lease.close.assert_not_called()
                            self.assertFalse(worker.cleanup_complete)
                        else:
                            worker.close()
                            worker.close()
                            self.assertTrue(worker.cleanup_complete)
                            worker.lease.close.assert_called_once()
                        kill.assert_called_once()
                finally:
                    worker.control.close()
                    worker.endpoint.close()


if __name__ == '__main__':
    unittest.main()
