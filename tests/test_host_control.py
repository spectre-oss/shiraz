# SPDX-License-Identifier: GPL-3.0-only
import io
import json
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import test_package_registry as fixtures

from shiraz.host_control import serve
from shiraz.host_session import HostSession
from shiraz.service_broker import QuiescenceCleanupError, RetirementCleanupError


class FakeProcess:
    pid = 42


class FakeHost:
    def __init__(self, _):
        self.apps = {}
        self.started = []
        self.launch_ids = {}
        self.closed = False

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def close(self):
        self.closed = True

    def collect(self):
        return []

    def start(self, package, expected_sha256=None, settings=None):
        self.started.append((package, expected_sha256))
        self.settings = settings
        self.apps[package] = (None, Path('/fixture'))
        process = FakeProcess()
        self.launch_ids[process] = 'a' * 32
        return process

    def stop(self, package, expected_launch):
        if expected_launch != 'a' * 32:
            raise RuntimeError('Stale launch')
        return bool(self.apps.pop(package, False))

    def retire(self, package, *, expected_sha256, expected_uid):
        self.retirement = (package, expected_sha256, expected_uid)
        return True


class HostControlTests(unittest.TestCase):
    def test_idle_host_owns_scheduler_until_context_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            with HostSession(Path(directory) / 'registry') as host:
                scheduler = host.broker.job_scheduler
                self.assertIsNotNone(scheduler)
                self.assertTrue(scheduler.thread.is_alive())
                self.assertEqual(host.apps, {})
                self.assertEqual(host.collect(), [])
                self.assertFalse(host.stop('test.absent', 'a' * 32))
                self.assertTrue(scheduler.thread.is_alive())
            self.assertFalse(scheduler.thread.is_alive())

    def test_control_rejects_malformed_requester(self):
        request = dict(op='start', package='test.target', sha256='a' * 64)
        invalid = [
            None,
            {},
            {'package': 'test.caller'},
            dict(package='../caller', launch_id='b' * 32),
            dict(package='test.caller', launch_id='stale'),
            dict(package='test.caller', launch_id='b' * 32, uid=10000),
        ]
        for requester in invalid:
            with self.subTest(requester=requester):
                host, replies = self.run_control(
                    json.dumps(dict(request, requester=requester)).encode() + b'\n'
                )
                self.assertFalse(replies[1]['ok'])
                self.assertFalse(host.started)

    def test_requester_requires_live_exact_launch_and_pinned_identity(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            HostSession(Path(directory) / 'registry') as host,
        ):
            process = Mock()
            process.poll.return_value = None
            host.apps['test.caller'] = (process, Path(directory))
            host.launch_ids[process] = 'a' * 32
            host.launch_identities[process] = dict(uid=10000, sha256='b' * 64)
            self.addCleanup(host.apps.clear)
            requester = dict(package='test.caller', launch_id='a' * 32)
            try:
                for invalid in (
                    dict(requester, launch_id='c' * 32),
                    dict(requester, package='test.absent'),
                ):
                    with self.assertRaises(PermissionError):
                        host.start('test.target', requester=invalid)
                process.poll.return_value = 0
                with self.assertRaises(PermissionError):
                    host.start('test.target', requester=requester)
                process.poll.return_value = None
                with (
                    patch.object(host.registry, 'identity', return_value=dict(sha256='d' * 64)),
                    patch.object(
                        host.registry, 'launch_apk', return_value=Path(directory) / 'app.apk'
                    ),
                    patch(
                        'shiraz.host_session.inspect_apk',
                        return_value=dict(
                            apk_sha256='d' * 64, manifest=dict(launcher_activities=['fixture'])
                        ),
                    ),
                    patch.object(host.registry, 'lease'),
                    patch.object(
                        host.registry,
                        'authorize_launcher',
                        side_effect=PermissionError('policy denied'),
                    ) as policy,
                    patch('shiraz.host_session.subprocess.Popen') as spawn,
                ):
                    with self.assertRaisesRegex(PermissionError, 'policy denied'):
                        host.start('test.target', requester=requester)
                    policy.assert_called_once_with(
                        10000, 'test.target', caller_sha256='b' * 64, target_sha256='d' * 64
                    )
                    spawn.assert_not_called()
            finally:
                host.apps.clear()

    def test_explicit_component_admission_pins_caller_and_target(self):
        fixture = fixtures.PackageRegistryTests()
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        fixture.enroll('test.caller')
        activity = dict(name='test.target.Login', exported='true', process='test.target')
        target = fixture.enroll('test.target', activities=[activity])
        with HostSession(fixture.registry.root) as host:
            source = Mock()
            source.poll.return_value = None
            host.apps['test.caller'] = (source, fixture.root)
            host.launch_ids[source] = 'a' * 32
            host.launch_identities[source] = host.registry.identity('test.caller')
            requester = dict(package='test.caller', launch_id='a' * 32)
            try:
                for arguments in (
                    {},
                    {'expected_sha256': target['apk_sha256']},
                    {'requester': requester},
                ):
                    with self.assertRaises(PermissionError):
                        host.start('test.target', component=activity['name'], **arguments)
                with (
                    patch(
                        'shiraz.host_session.inspect_apk',
                        return_value=dict(
                            apk_sha256=target['apk_sha256'],
                            manifest=dict(package='test.target', activities=[activity]),
                        ),
                    ),
                    patch(
                        'shiraz.host_session.subprocess.Popen',
                        side_effect=RuntimeError('spawn boundary'),
                    ) as spawn,
                ):
                    with self.assertRaisesRegex(RuntimeError, 'spawn boundary'):
                        host.start(
                            'test.target',
                            expected_sha256=target['apk_sha256'],
                            requester=requester,
                            component=activity['name'],
                        )
                    self.assertEqual(spawn.call_args.args[0][-1], activity['name'])
                    self.assertNotIn('test.target', host.apps)
                    spawn.reset_mock()
                    with self.assertRaises(PermissionError):
                        host.start(
                            'test.target',
                            expected_sha256=target['apk_sha256'],
                            requester=requester,
                            component='test.target.Missing',
                        )
                    spawn.assert_not_called()
                    host.broker.quiesced.add('test.caller')
                    with self.assertRaisesRegex(PermissionError, 'requester unavailable'):
                        host.start(
                            'test.target',
                            expected_sha256=target['apk_sha256'],
                            requester=requester,
                            component=activity['name'],
                        )
                    host.broker.quiesced.remove('test.caller')
                    spawn.assert_not_called()
                    fixture.enroll('test.caller', version=2)
                    with self.assertRaises((PermissionError, fixtures.FormatError)):
                        host.start(
                            'test.target',
                            expected_sha256=target['apk_sha256'],
                            requester=requester,
                            component=activity['name'],
                        )
                    spawn.assert_not_called()
            finally:
                host.apps.clear()

    def test_quiesce_resume_control_preserves_enrollment(self):
        fixture = fixtures.PackageRegistryTests()
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        enrolled = fixture.enroll('test.one')
        identity = dict(package='test.one', uid=enrolled['uid'], sha256=enrolled['apk_sha256'])
        requests = [
            dict(op='quiesce', **identity),
            dict(op='identity', package='test.one'),
            dict(op='resume', **dict(identity, sha256='0' * 64)),
            dict(op='resume', **identity),
        ]
        source = io.BytesIO(b''.join(json.dumps(request).encode() + b'\n' for request in requests))
        output = io.StringIO()
        serve(fixture.registry.root, source, output)
        replies = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertTrue(replies[1]['quiesced'])
        self.assertEqual(replies[2]['identity'], identity)
        self.assertFalse(replies[3]['ok'])
        self.assertTrue(replies[4]['resumed'])

    def test_quiescence_cleanup_failure_still_stops_app(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            HostSession(Path(directory) / 'registry') as host,
        ):
            process = subprocess.Popen(
                [sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True
            )
            host.apps['test.one'] = (process, Path(directory))
            host.launch_ids[process] = 'a' * 32
            host.reapers[process] = threading.Thread(target=process.wait)
            host.reapers[process].start()
            with patch.object(
                host.broker,
                'quiesce_package',
                side_effect=QuiescenceCleanupError('cleanup incomplete'),
            ):
                with self.assertRaises(QuiescenceCleanupError):
                    host.quiesce('test.one', expected_sha256='a' * 64, expected_uid=10000)
            self.assertIsNotNone(process.poll())
            self.assertEqual(len(host.collect()), 1)

    def test_committed_retirement_cleanup_failure_still_stops_app(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            HostSession(Path(directory) / 'registry') as host,
        ):
            process = subprocess.Popen(
                [sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True
            )
            host.apps['test.one'] = (process, Path(directory))
            host.launch_ids[process] = 'a' * 32
            host.reapers[process] = threading.Thread(target=process.wait)
            host.reapers[process].start()
            with patch.object(
                host.broker,
                'retire_package',
                side_effect=RetirementCleanupError('cleanup incomplete'),
            ):
                with self.assertRaises(RetirementCleanupError):
                    host.retire('test.one', expected_sha256='a' * 64, expected_uid=10000)
            self.assertIsNotNone(process.poll())
            self.assertEqual(len(host.collect()), 1)

    def test_identity_query_is_read_only_and_reports_absence(self):
        fixture = fixtures.PackageRegistryTests()
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        enrolled = fixture.enroll('test.one')
        requests = [
            dict(op='identity', package=package)
            for package in ('test.one', 'test.absent', '../invalid')
        ]
        source = io.BytesIO(b''.join(json.dumps(request).encode() + b'\n' for request in requests))
        output = io.StringIO()
        serve(fixture.registry.root, source, output)
        replies = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual(
            replies[1]['identity'],
            dict(package='test.one', uid=enrolled['uid'], sha256=enrolled['apk_sha256']),
        )
        self.assertTrue(replies[1]['found'])
        self.assertFalse(replies[2]['found'])
        self.assertIsNone(replies[2]['identity'])
        self.assertFalse(replies[3]['ok'])
        with fixture.registry.connect() as db:
            self.assertEqual(db.execute('SELECT COUNT(*) FROM packages').fetchone()[0], 1)
            self.assertEqual(db.execute('SELECT COUNT(*) FROM retired_uids').fetchone()[0], 0)

    def test_retirement_stops_package_process_and_rejects_stale_identity(self):
        fixture = fixtures.PackageRegistryTests()
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        identity = fixture.enroll('test.one')
        with HostSession(fixture.registry.root) as host:
            processes = []
            for package in ('test.one', 'test.two'):
                process = subprocess.Popen(
                    [sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True
                )
                processes.append(process)
                host.apps[package] = (process, fixture.root)
                host.launch_ids[process] = package
                host.reapers[process] = threading.Thread(target=process.wait)
                host.reapers[process].start()
            with self.assertRaisesRegex(fixtures.FormatError, 'changed since retirement'):
                host.retire('test.one', expected_sha256='0' * 64, expected_uid=identity['uid'])
            self.assertIsNone(processes[0].poll())
            self.assertTrue(
                host.retire(
                    'test.one', expected_sha256=identity['apk_sha256'], expected_uid=identity['uid']
                )
            )
            self.assertIsNotNone(processes[0].poll())
            self.assertIsNone(processes[1].poll())
            self.assertEqual([event['package'] for event in host.collect()], ['test.one'])
            with self.assertRaises(PermissionError):
                host.registry.identity('test.one')
            self.assertFalse(host.closed)

    def test_retirement_control_validates_identity(self):
        request = dict(op='retire', package='test.app', sha256='a' * 64, uid=10000)
        for invalid in (True, 9999, 20000, '10000', None):
            host, replies = self.run_control(
                json.dumps(dict(request, uid=invalid)).encode() + b'\n'
            )
            self.assertFalse(replies[1]['ok'])
            self.assertFalse(hasattr(host, 'retirement'))
        host, replies = self.run_control(json.dumps(request).encode() + b'\n')
        self.assertTrue(replies[1]['retired'])
        self.assertEqual(host.retirement, ('test.app', 'a' * 64, 10000))

    def test_failed_launch_removes_only_its_new_inbox(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            library = root / 'library'
            (library / 'apps').mkdir(parents=True)
            with HostSession(root / 'registry', library=library) as host:
                with (
                    patch.object(host.registry, 'identity', return_value={'sha256': 'a' * 64}),
                    patch.object(host.registry, 'launch_apk', return_value=root / 'app.apk'),
                    patch(
                        'shiraz.host_session.inspect_apk',
                        return_value={
                            'apk_sha256': 'a' * 64,
                            'manifest': {
                                'package': 'test.app',
                                'launcher_activities': [{'target': 'test.app.Main'}],
                            },
                        },
                    ),
                    patch.object(
                        host.broker, 'open_session', side_effect=PermissionError('fixture denial')
                    ),
                ):
                    with self.assertRaises(PermissionError):
                        host.start('test.app')
                self.assertEqual(list((library / 'sessions').iterdir()), [])
                self.assertTrue((library / 'apps').is_dir())
                self.assertFalse(host.apps)

    def test_stop_leaves_other_process_and_host_running(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            HostSession(Path(directory) / 'registry') as host,
        ):
            processes = []
            for package in ('test.one', 'test.two'):
                process = subprocess.Popen(
                    [sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True
                )
                processes.append(process)
                host.apps[package] = (process, Path(directory))
                reaper = threading.Thread(target=process.wait)
                host.reapers[process] = reaper
                host.launch_ids[process] = package
                reaper.start()
            self.assertTrue(host.stop('test.one', 'test.one'))
            self.assertIsNotNone(processes[0].poll())
            self.assertIsNone(processes[1].poll())
            self.assertFalse(host.closed)
            self.assertEqual([event['package'] for event in host.collect()], ['test.one'])
            self.assertEqual(list(host.apps), ['test.two'])

    def test_closed_host_cannot_collect_shutdown_owned_reapers(self):
        with tempfile.TemporaryDirectory() as directory:
            host = HostSession(Path(directory) / 'registry')
            host.close()
            with self.assertRaisesRegex(RuntimeError, 'closed'):
                host.collect()

    def run_control(self, content):
        host = FakeHost(None)
        output = io.StringIO()
        serve(None, io.BytesIO(content), output, host_factory=lambda _: host)
        self.assertTrue(host.closed)
        return host, [json.loads(line) for line in output.getvalue().splitlines()]

    def test_start_status_shutdown_and_eof(self):
        start = json.dumps(dict(op='start', package='test.app', sha256='a' * 64)).encode() + b'\n'
        for ending in (b'', b'{"op":"shutdown"}\n'):
            host, replies = self.run_control(start + b'{"op":"status"}\n' + ending)
            self.assertEqual(replies[0], dict(ready=True, protocol=1))
            self.assertEqual(host.started, [('test.app', 'a' * 64)])
            self.assertEqual(replies[1]['pid'], 42)
            self.assertEqual(replies[2]['running'], ['test.app'])

    def test_invalid_requests_cannot_launch(self):
        for request in (
            b'[]',
            b'{"op":"start"}',
            b'{"op":"status","op":"shutdown"}',
            b'{"op":"status","extra":1}',
            b'\xff',
            b'{"op":"exec"}',
        ):
            host, replies = self.run_control(request + b'\n{"op":"status"}\n')
            self.assertEqual(host.started, [])
            self.assertFalse(replies[1]['ok'])
            self.assertTrue(replies[2]['ok'])

    def test_stop_requires_exact_launch_and_preserves_host(self):
        start = json.dumps(dict(op='start', package='test.app', sha256='a' * 64)).encode() + b'\n'
        stop = lambda launch: (
            json.dumps(dict(op='stop', package='test.app', launch_id=launch)).encode() + b'\n'
        )
        host, replies = self.run_control(
            start
            + stop(True)
            + stop('b' * 32)
            + stop('a' * 32)
            + stop('a' * 32)
            + b'{"op":"status"}\n'
        )
        self.assertFalse(replies[2]['ok'])
        self.assertFalse(replies[3]['ok'])
        self.assertTrue(replies[4]['stopped'])
        self.assertFalse(replies[5]['stopped'])
        self.assertEqual(replies[6]['running'], [])

    def test_oversized_or_unterminated_frame_closes_host(self):
        for request in (b'x' * 4097 + b'\n{"op":"status"}\n', b'{"op":"status"}'):
            host, replies = self.run_control(request)
            self.assertEqual(host.started, [])
            self.assertEqual(len(replies), 2)
            self.assertFalse(replies[1]['ok'])
