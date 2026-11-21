# SPDX-License-Identifier: GPL-3.0-only
import threading
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from shiraz.foreground_admission import reserve


class ForegroundAdmissionTests(unittest.TestCase):
    def broker(self):
        return SimpleNamespace(
            lock=threading.RLock(),
            closed=False,
            quiesced=set(),
            foreground_admissions={},
            job_dispatches=set(),
        )

    def test_drain_outside_lock_and_hold_through_launch(self):
        broker = self.broker()
        key = ('app', 'hash', 'app:jobs')
        dispatch = Mock()
        dispatch.worker.service = dict(package='app', apk_sha256='hash', process=':jobs')

        def drain(timeout):
            self.assertGreater(timeout, 0)
            self.assertFalse(broker.lock._is_owned())
            self.assertIn(key, broker.foreground_admissions)

        dispatch.drain.side_effect = drain
        broker.job_dispatches.add(dispatch)
        with reserve(broker, *key):
            dispatch.stop_for_foreground.assert_called_once()
            dispatch.drain.assert_called_once()
            with self.assertRaisesRegex(RuntimeError, 'already pending'):
                with reserve(broker, *key):
                    self.fail('Duplicate admission')
            self.assertIn(key, broker.foreground_admissions)
        self.assertFalse(broker.foreground_admissions)

    def test_timeout_preserves_worker_and_releases_only_reservation(self):
        broker = self.broker()
        dispatch = Mock()
        dispatch.worker.service = dict(package='app', apk_sha256='hash', process='app')
        dispatch.drain.side_effect = TimeoutError('Still running')
        broker.job_dispatches.add(dispatch)
        with self.assertRaises(TimeoutError):
            with reserve(broker, 'app', 'hash', 'app'):
                self.fail('Admitted before cleanup')
        self.assertFalse(broker.foreground_admissions)
        self.assertIn(dispatch, broker.job_dispatches)
        dispatch.worker.close.assert_not_called()
        dispatch.stop_for_foreground.assert_called_once()

    def test_other_generation_and_process_do_not_wait(self):
        broker = self.broker()
        dispatch = Mock()
        dispatch.worker.service = dict(package='app', apk_sha256='old', process='app')
        broker.job_dispatches.add(dispatch)
        with reserve(broker, 'app', 'new', 'app'):
            pass
        dispatch.drain.assert_not_called()
        dispatch.stop_for_foreground.assert_not_called()
