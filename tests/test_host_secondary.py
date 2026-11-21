# SPDX-License-Identifier: GPL-3.0-only
import json
import subprocess
import threading
import unittest
from contextlib import ExitStack
from unittest.mock import Mock, patch

import test_package_registry as fixtures

from shiraz.apk import FormatError
from shiraz.host_session import HostSession, session_exit_status
from shiraz.service_broker import Identity


class Worker:
    def __init__(self, pid):
        self.pid = pid
        self.done = threading.Event()
        self.returncode = None
        self.observed = threading.Event()

    def poll(self):
        self.observed.set()
        return self.returncode

    def wait(self, timeout=None):
        if not self.done.wait(timeout):
            raise subprocess.TimeoutExpired('fixture worker', timeout)
        return self.returncode

    def finish(self):
        self.returncode = 0
        self.done.set()


class HostSecondaryTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.main = dict(name='test.app.Main', enabled='true', exported='true', process='test.app')
        self.helper = dict(
            name='test.app.Helper', enabled='true', exported='false', process=':helper'
        )
        self.enrolled = self.fixture.enroll('test.app', activities=[self.main, self.helper])
        self.host = HostSession(self.fixture.registry.root)
        self.workers = []
        self.signaled = []
        self.patches = ExitStack()
        self.patches.enter_context(
            patch(
                'shiraz.host_session.inspect_apk',
                return_value=dict(
                    apk_sha256=self.enrolled['apk_sha256'],
                    manifest=dict(
                        package='test.app',
                        activities=[self.main, self.helper],
                        launcher_activities=[dict(target=self.main['name'])],
                    ),
                ),
            )
        )
        self.spawn = self.patches.enter_context(
            patch('shiraz.host_session.subprocess.Popen', side_effect=self.start_worker)
        )
        self.patches.enter_context(
            patch('shiraz.host_session.os.killpg', side_effect=self.signal_worker)
        )

    def tearDown(self):
        try:
            self.host.close()
        finally:
            self.patches.close()
            self.fixture.tearDown()

    def start_worker(self, *args, **kwargs):
        worker = Worker(70000 + len(self.workers))
        self.workers.append(worker)
        return worker

    def signal_worker(self, pid, signal):
        self.signaled.append(pid)
        matches = [worker for worker in self.workers if worker.pid == pid]
        self.assertEqual(len(matches), 1, 'Attempt to signal an unowned worker')
        matches[0].finish()

    def start_helper(self, source):
        return self.host.start(
            'test.app',
            expected_sha256=self.enrolled['apk_sha256'],
            requester=dict(package='test.app', launch_id=self.host.launch_ids[source]),
            component=self.helper['name'],
        )

    def test_completion_keeps_requested_kill_reason_through_collection(self):
        process = self.host.start('test.app')
        self.host.process_controls[process].group_termination_requested = True
        process.returncode = -9
        process.done.set()
        result = self.host.wait(2)
        self.assertEqual(result['apps'][0]['exit_status'], -9)
        self.assertEqual(result['apps'][0]['termination_reason'], 'package_request')
        self.assertEqual(session_exit_status(result), 0)
        events = self.host.collect()
        self.assertEqual(events, result['apps'])
        self.assertFalse(self.host.collect())
        self.assertNotIn(process, self.host.process_controls)

    def test_completion_does_not_hide_crash_or_unrequested_signal(self):
        process = self.host.start('test.app')
        work = self.host.apps['test.app'][1]
        for status, requested in ((-9, False), (-11, True), (3, True)):
            process.returncode = status
            self.host.process_controls[process].group_termination_requested = requested
            event = self.host._completion('test.app', 'test.app', process, work)
            self.assertNotIn('termination_reason', event)
            self.assertEqual(session_exit_status(dict(apps=[event])), 1)
        process.finish()

    def test_termination_requires_live_same_package_generation_and_reported_art_pid(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        identity = self.host.registry.identity('test.app')
        caller = Identity(
            'test.app', identity['uid'], identity['sha256'], process_name='test.app:helper'
        )
        real_control = self.host.process_controls[primary]
        control = Mock()
        control.reported_pid.return_value = 123456
        control.terminate.side_effect = lambda pid: primary.finish()
        self.host.process_controls[primary] = control
        try:
            for pid in (primary.pid, helper.pid, 1, True):
                with self.assertRaises(PermissionError):
                    self.host._terminate_process(caller, pid)
            with self.assertRaises(PermissionError):
                self.host._terminate_process(
                    Identity('test.app', identity['uid'], 'b' * 64, process_name='test.app:helper'),
                    123456,
                )
            self.host.broker.quiesced.add('test.app')
            with self.assertRaises(PermissionError):
                self.host._terminate_process(caller, 123456)
            self.host.broker.quiesced.remove('test.app')
            self.host.launch_identities[primary] = dict(identity, uid=identity['uid'] + 1)
            with self.assertRaises(PermissionError):
                self.host._terminate_process(caller, 123456)
            self.host.launch_identities[primary] = dict(identity, sha256='c' * 64)
            with self.assertRaises(PermissionError):
                self.host._terminate_process(caller, 123456)
            self.host.launch_identities[primary] = dict(identity)
            control.terminate.assert_not_called()
            self.host._terminate_process(caller, 123456)
            control.terminate.assert_called_once_with(123456)
            self.assertIsNone(helper.poll())
            with self.assertRaises(PermissionError):
                self.host._terminate_process(caller, 123456)
        finally:
            self.host.process_controls[primary] = real_control

    def test_broker_activity_inherits_runtime_settings_without_startup_limits(self):
        primary = self.host.start(
            'test.app',
            settings=dict(
                native_code=True,
                width=720,
                height=1280,
                log_level='DEBUG',
                frames=3,
                timeout=10,
                snapshot='/tmp/initial.ppm',
            ),
        )
        identity = self.host.registry.identity('test.app')
        caller = Identity('test.app', identity['uid'], identity['sha256'], process_name='test.app')
        target = dict(
            package='test.app',
            apk_sha256=identity['sha256'],
            requested_component=self.helper['name'],
        )
        with self.host._admit_activity(caller, target):
            pass
        settings = json.loads(self.spawn.call_args.args[0][7])
        self.assertTrue(settings['native_code'])
        self.assertEqual(
            (settings['width'], settings['height'], settings['log_level']), (720, 1280, 'DEBUG')
        )
        for key in ('frames', 'timeout', 'snapshot', 'embed'):
            self.assertIsNone(settings[key])
        primary.finish()
        self.host.collect()
        self.assertNotIn(primary, self.host.launch_settings)

    def test_secondary_admission_identity_duplicate_stop_and_collection(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        self.assertIs(self.host.apps['test.app'][0], primary)
        self.assertIs(self.host.activity_processes[('test.app', 'test.app:helper')][0], helper)
        self.assertEqual(self.host.process_names[helper], 'test.app:helper')
        self.assertNotEqual(self.host.launch_ids[primary], self.host.launch_ids[helper])
        self.assertNotEqual(
            self.host.apps['test.app'][1],
            self.host.activity_processes[('test.app', 'test.app:helper')][1],
        )
        with self.assertRaisesRegex(FormatError, 'already owned'):
            self.start_helper(primary)
        self.assertEqual(len(self.workers), 2)
        identity = self.host.registry.identity('test.app')
        caller = Identity(
            'test.app', identity['uid'], identity['sha256'], process_name='test.app:helper'
        )
        target = dict(
            package='test.app', apk_sha256=identity['sha256'], requested_component=self.main['name']
        )
        with patch.object(
            self.host, 'start', side_effect=RuntimeError('admission boundary')
        ) as start:
            with self.assertRaisesRegex(RuntimeError, 'admission boundary'):
                self.host._admit_activity(caller, target)
            self.assertEqual(
                start.call_args.kwargs['requester']['launch_id'], self.host.launch_ids[helper]
            )
            with self.assertRaises(PermissionError):
                self.host._admit_activity(
                    Identity(
                        'test.app',
                        identity['uid'],
                        identity['sha256'],
                        process_name='test.app:absent',
                    ),
                    target,
                )
            self.assertEqual(start.call_count, 1)
        with self.assertRaises(FormatError):
            self.host.stop('test.app', 'stale')
        self.assertTrue(self.host.stop('test.app', self.host.launch_ids[helper]))
        self.assertIsNone(primary.poll())
        self.assertEqual(self.signaled, [helper.pid])
        completed = self.host.collect()
        self.assertEqual(len(completed), 1)
        self.assertEqual(completed[0]['process_name'], 'test.app:helper')
        self.assertFalse(self.host.activity_processes)
        self.assertNotIn(helper, self.host.process_names)
        replacement = self.start_helper(primary)
        self.assertIsNot(replacement, helper)

    def test_quiescence_stops_all_owned_processes_and_resume_checks_secondary(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        identity = self.host.registry.identity('test.app')
        primary.finish()
        self.host.reapers[primary].join(2)
        with self.assertRaisesRegex(RuntimeError, 'cleanup is incomplete'):
            self.host.resume(
                'test.app', expected_sha256=identity['sha256'], expected_uid=identity['uid']
            )
        self.host.quiesce(
            'test.app', expected_sha256=identity['sha256'], expected_uid=identity['uid']
        )
        self.assertEqual(helper.poll(), 0)
        self.assertIn(helper.pid, self.signaled)
        self.assertEqual(len(self.host.collect()), 2)
        self.host.resume(
            'test.app', expected_sha256=identity['sha256'], expected_uid=identity['uid']
        )

    def test_wait_includes_secondary_started_after_wait_begins(self):
        primary = self.host.start('test.app')
        waiting = threading.Event()
        result = []
        errors = []
        original_wait = self.host.changed.wait

        def observe_wait(timeout):
            waiting.set()
            return original_wait(timeout)

        def wait_host():
            try:
                result.append(self.host.wait(timeout=5))
            except BaseException as error:
                errors.append(error)

        with patch.object(self.host.changed, 'wait', side_effect=observe_wait):
            thread = threading.Thread(target=wait_host)
            thread.start()
            try:
                self.assertTrue(waiting.wait(2))
                helper = self.start_helper(primary)
                primary.finish()
                self.host.reapers[primary].join(2)
                helper.observed.clear()
                self.host.changed.set()
                self.assertTrue(helper.observed.wait(2))
                self.assertFalse(result, 'Host wait returned while secondary was alive')
                helper.finish()
            finally:
                for worker in self.workers:
                    worker.finish()
                self.host.changed.set()
                thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertFalse(errors, errors)
        self.assertEqual(
            {item['process_name'] for item in result[0]['apps']}, {'test.app', 'test.app:helper'}
        )

    def test_restart_helper_reuses_completed_primary_without_losing_event(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        old_launch = self.host.launch_ids[primary]
        primary.finish()
        self.host.reapers[primary].join(2)
        restarted = self.host.start(
            'test.app',
            expected_sha256=self.enrolled['apk_sha256'],
            requester=dict(package='test.app', launch_id=self.host.launch_ids[helper]),
            component=self.main['name'],
        )
        self.assertIsNot(restarted, primary)
        self.assertIs(self.host.apps['test.app'][0], restarted)
        self.assertIsNone(helper.poll())
        self.assertNotEqual(self.host.launch_ids[restarted], old_launch)
        events = self.host.collect()
        self.assertEqual([event['launch_id'] for event in events], [old_launch])
        self.assertFalse(self.host.collect())
        with self.assertRaises(FormatError):
            self.host.stop('test.app', old_launch)
        self.assertIsNone(restarted.poll())

    def test_reclamation_blocks_incomplete_or_failed_cleanup(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        helper.finish()
        self.host.reapers[helper].join(2)
        with patch.object(self.host.reapers[helper], 'is_alive', return_value=True):
            with self.assertRaisesRegex(RuntimeError, 'reaper did not stop'):
                self.start_helper(primary)
        self.host.cleanup_errors[helper] = RuntimeError('fixture cleanup error')
        try:
            with self.assertRaisesRegex(RuntimeError, 'cleanup failed'):
                self.start_helper(primary)
            self.assertEqual(len(self.workers), 2)
            self.assertFalse(self.host.completed)
            self.assertIs(self.host.activity_processes[('test.app', 'test.app:helper')][0], helper)
        finally:
            del self.host.cleanup_errors[helper]

    def test_completion_backlog_is_bounded_and_collect_releases_it(self):
        primary = self.host.start('test.app')
        helper = self.start_helper(primary)
        helper.finish()
        self.host.reapers[helper].join(2)
        self.host.completed = [dict(launch_id='old-' + str(index)) for index in range(256)]
        with self.assertRaisesRegex(FormatError, 'backlog'):
            self.start_helper(primary)
        self.assertEqual(len(self.workers), 2)
        self.assertEqual(len(self.host.collect()), 257)
        self.assertFalse(self.host.completed)
        self.assertIsNot(self.start_helper(primary), helper)
