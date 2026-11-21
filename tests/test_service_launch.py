# SPDX-License-Identifier: GPL-3.0-only
"""Service entrypoint command boundary; does not claim broker routing."""

import json
import os
import platform
import selectors
import socket
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path

from shiraz import broker_wire, runtime
from shiraz.apk import FormatError, inspect_apk

ROOT = Path(__file__).resolve().parents[1]


class ServiceLaunchTests(unittest.TestCase):
    def arguments(self, work):
        host = runtime.HOST
        vm = host / 'bin/dalvikvm64'
        if not vm.exists():
            vm = host / 'bin/dalvikvm'
        apk = ROOT / 'build/macos-apk/background.apk'
        return dict(
            root=ROOT,
            host=host,
            environ=os.environ,
            vm=vm,
            classpath=str(ROOT / 'build/shiraz-framework.jar') + ':' + str(apk),
            framework_resources=ROOT / 'build/framework-res/framework-res.apk',
            work=work,
            data=work / 'data',
            app_lib=work / 'app_lib',
            metadata=work / 'application.properties',
            package='org.shiraz.backgroundfixture',
            app='android.app.Application',
            activity='org.shiraz.backgroundfixture.BackgroundWorker',
            width=360,
            height=640,
            frames=None,
            snapshot=None,
            log_level='INFO',
            execution_options=['-Xint', '-Xusejit:false'],
            device_profile=None,
            profile_values={},
            digitalis=None,
            version='test',
            service_process='org.shiraz.backgroundfixture',
            service_fd=9,
        )

    def test_command_boundary(self):
        args = self.arguments(Path('/tmp/service-test'))
        for system in ('Linux', 'Darwin'):
            command, _ = runtime.launch_command(system, **dict(args, service_process=':remote'))
            self.assertNotIn('shiraz.runtime.ActivityRunner', command)
            self.assertEqual(
                command[-11:],
                [
                    'shiraz.runtime.ServiceRunner',
                    args['package'],
                    args['app'],
                    args['activity'],
                    '360',
                    '640',
                    '160',
                    str(args['data']),
                    ':remote',
                    str(args['metadata']),
                    '9',
                ],
            )
        receiver, _ = runtime.launch_command('Darwin', **dict(args, receiver=True))
        self.assertIn('shiraz.runtime.ReceiverRunner', receiver)
        for system in ('Darwin', 'Linux'):
            command, _ = runtime.launch_command(system, **dict(args, job=True))
            self.assertIn('shiraz.runtime.JobRunner', command)
        for invalid in ({'receiver': True}, {'service_shared': True}, {'service_process': None}):
            with self.assertRaises(FormatError):
                runtime.launch_command('Darwin', **dict(args, job=True, **invalid))
        for invalid in ({'service_shared': True}, {'service_process': None}):
            with self.assertRaises(FormatError):
                runtime.launch_command('Darwin', **dict(args, receiver=True, **invalid))
        shared, _ = runtime.launch_command('Darwin', **dict(args, service_shared=True))
        self.assertEqual(shared[-2:], ['9', 'shared'])
        for invalid in (
            {'service_fd': 2},
            {'service_fd': True},
            {'service_fd': None},
            {'service_process': ':'},
            {'service_process': 'a/b'},
            {'service_density': 0},
            {'frames': 1},
            {'snapshot': Path('/tmp/snapshot')},
            {'service_process': None},
        ):
            with self.subTest(invalid=invalid), self.assertRaises(FormatError):
                runtime.launch_command('Darwin', **dict(args, **invalid))

    @unittest.skipUnless(
        os.environ.get('SHIRAZ_TEST_SERVICE_ENTRY') == '1', 'make service-entry-smoke'
    )
    def test_art_explicit_descriptor_lifetime(self):
        work_root = ROOT / 'build/service-entry-smoke'
        work_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=work_root) as folder:
            work = Path(folder)
            args = self.arguments(work)
            for directory in (
                'data',
                'app_lib',
                'tmp',
                'home',
                'cache',
                'android-data/dalvik-cache',
            ):
                (work / directory).mkdir(parents=True, exist_ok=True)
            apk = ROOT / 'build/macos-apk/background.apk'
            manifest = inspect_apk(apk)['manifest']
            metadata = runtime.application_metadata(manifest, apk, args['data'])
            metadata['nativeLibraryDir'] = str(args['app_lib'])
            args['metadata'].write_text(runtime.properties_text(metadata))
            # Occupy fd 3 so this verifies the explicit descriptor, not the legacy default.
            with tempfile.TemporaryFile():
                with tempfile.TemporaryFile():
                    server, client = socket.socketpair()
                    try:
                        self.assertGreater(client.fileno(), 3)
                        args['service_fd'] = client.fileno()
                        command, env = runtime.launch_command(platform.system(), **args)
                        with (work_root / 'stderr.log').open('wb') as errors:
                            child = subprocess.Popen(
                                command,
                                env=env,
                                pass_fds=(client.fileno(),),
                                stdout=subprocess.PIPE,
                                stderr=errors,
                            )
                            client.close()
                            try:
                                with selectors.DefaultSelector() as selector:
                                    selector.register(child.stdout, selectors.EVENT_READ)
                                    self.assertTrue(
                                        selector.select(20), 'Service did not become ready'
                                    )
                                    self.assertEqual(
                                        child.stdout.readline().strip(),
                                        b'SHIRAZ_SERVICE_CHANNEL_READY',
                                    )
                                server.close()
                                stdout, _ = child.communicate(timeout=15)
                                (work_root / 'stdout.log').write_bytes(stdout)
                                self.assertEqual(
                                    child.returncode,
                                    0,
                                    'Inspect build/service-entry-smoke/stderr.log',
                                )
                                self.assertIn(b'BACKGROUND_APK_SERVICE_DESTROYED', stdout)
                            finally:
                                if child.poll() is None:
                                    child.kill()
                                    child.communicate()
                    finally:
                        server.close()
                        client.close()

    @unittest.skipUnless(
        os.environ.get('SHIRAZ_TEST_SERVICE_ENTRY') == '1', 'make service-entry-smoke'
    )
    def test_art_receiver_completion_and_failure(self):
        work_root = ROOT / 'build/receiver-entry-smoke'
        work_root.mkdir(parents=True, exist_ok=True)

        def string(value):
            if value is None:
                return struct.pack('<i', -1)
            encoded = value.encode('utf-16-le')
            return struct.pack('<i', len(encoded) // 2) + encoded + b'\0' * (len(encoded) % 4)

        for action in ('complete', 'mismatch', 'trailing', 'throw', 'timeout'):
            with self.subTest(action=action), tempfile.TemporaryDirectory(dir=work_root) as folder:
                work = Path(folder)
                args = self.arguments(work)
                args.update(
                    receiver=True,
                    service_process=':events',
                    activity='org.shiraz.backgroundfixture.HeadlessReceiver',
                )
                for directory in (
                    'data',
                    'app_lib',
                    'tmp',
                    'home',
                    'cache',
                    'android-data/dalvik-cache',
                ):
                    (work / directory).mkdir(parents=True, exist_ok=True)
                apk = ROOT / 'build/macos-apk/background.apk'
                metadata = runtime.application_metadata(
                    inspect_apk(apk)['manifest'], apk, args['data']
                )
                metadata['nativeLibraryDir'] = str(args['app_lib'])
                args['metadata'].write_text(runtime.properties_text(metadata))
                component = (
                    args['package'] + '/' + (args['activity'] if action != 'mismatch' else 'Wrong')
                )
                # Shiraz Parcel typed Intent, without extras, categories, selector or attachments.
                payload = (
                    struct.pack('<i', 1)
                    + b''.join(string(value) for value in (action, None, None, None, component))
                    + struct.pack('<iiiii', 0, -1, 0, 0, 0)
                )
                if action == 'trailing':
                    payload += b'bad!'
                server, client = socket.socketpair()
                try:
                    args['service_fd'] = client.fileno()
                    command, env = runtime.launch_command(platform.system(), **args)
                    child = subprocess.Popen(
                        command,
                        env=env,
                        pass_fds=(client.fileno(),),
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                    )
                    client.close()
                    try:
                        server.sendall(
                            broker_wire.HEADER.pack(broker_wire.MAGIC, len(payload), 0) + payload
                        )
                        stdout, stderr = child.communicate(timeout=25)
                        (work_root / (action + '.stdout.log')).write_bytes(stdout)
                        (work_root / (action + '.stderr.log')).write_bytes(stderr)
                        if action == 'complete':
                            self.assertEqual(
                                child.returncode, 0, stderr.decode(errors='replace')[-2000:]
                            )
                            self.assertIn(b'HEADLESS_RECEIVER_ASYNC_FINISHED', stdout)
                            self.assertEqual(broker_wire.read_packet(server, 2), ({'ok': True}, []))
                        else:
                            self.assertNotEqual(child.returncode, 0)
                            reason = {
                                'mismatch': b'Receiver component mismatch',
                                'trailing': b'Unexpected trailing parcel data',
                                'throw': b'RECEIVER_EXPECTED_FAILURE',
                                'timeout': b'did not finish before its deadline',
                            }[action]
                            self.assertIn(reason, stderr)
                            self.assertNotIn(b'HEADLESS_RECEIVER_ASYNC_FINISHED', stdout)
                            with self.assertRaises((EOFError, ConnectionResetError)):
                                broker_wire.read_packet(server, 2)
                        self.assertNotIn(b'SHIRAZ_SERVICE_CHANNEL_READY', stdout)
                    finally:
                        if child.poll() is None:
                            child.kill()
                            child.communicate()
                finally:
                    server.close()
                    client.close()

    @unittest.skipUnless(
        os.environ.get('SHIRAZ_TEST_SERVICE_ENTRY') == '1', 'make service-entry-smoke'
    )
    def test_art_job_completion_and_failure(self):
        work_root = ROOT / 'build/job-entry-smoke'
        work_root.mkdir(parents=True, exist_ok=True)
        for action in (
            'complete',
            'retry',
            'deadline-true',
            'deadline-false',
            'mismatch',
            'malformed',
            'bare-definition',
            'attachments',
            'throw',
            'destroy',
        ):
            with self.subTest(action=action), tempfile.TemporaryDirectory(dir=work_root) as folder:
                work = Path(folder)
                args = self.arguments(work)
                args.update(
                    job=True,
                    service_process=':jobs',
                    activity='org.shiraz.backgroundfixture.HeadlessJob',
                )
                for directory in (
                    'data',
                    'app_lib',
                    'tmp',
                    'home',
                    'cache',
                    'android-data/dalvik-cache',
                ):
                    (work / directory).mkdir(parents=True, exist_ok=True)
                apk = ROOT / 'build/macos-apk/background.apk'
                metadata = runtime.application_metadata(
                    inspect_apk(apk)['manifest'], apk, args['data']
                )
                metadata['nativeLibraryDir'] = str(args['app_lib'])
                args['metadata'].write_text(runtime.properties_text(metadata))
                definition = dict(
                    version=1,
                    id=17,
                    component=args['activity'],
                    persisted=True,
                    minimum_latency_ms=0,
                    deadline_ms=None,
                    interval_ms=0,
                    flex_ms=0,
                    initial_backoff_ms=30000,
                    backoff_policy=1,
                    extras={'action': dict(type='string', value=action)},
                )
                if action == 'mismatch':
                    definition['component'] = 'test.other.Worker'
                if action.startswith('deadline-'):
                    definition['deadline_ms'] = 500
                launch = dict(
                    version=1, definition=definition, deadline_expired=action == 'deadline-true'
                )
                payload = (
                    b'{}'
                    if action == 'malformed'
                    else json.dumps(definition if action == 'bare-definition' else launch).encode()
                )
                server, client = socket.socketpair()
                child = None
                try:
                    args['service_fd'] = client.fileno()
                    command, env = runtime.launch_command(platform.system(), **args)
                    child = subprocess.Popen(
                        command,
                        env=env,
                        pass_fds=(client.fileno(),),
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                    )
                    client.close()
                    if action == 'attachments':
                        with tempfile.TemporaryFile() as attachment:
                            broker_wire.write_packet(server, launch, (attachment.fileno(),))
                    else:
                        server.sendall(
                            broker_wire.HEADER.pack(broker_wire.MAGIC, len(payload), 0) + payload
                        )
                    stdout, stderr = child.communicate(timeout=25)
                    (work_root / (action + '.stdout.log')).write_bytes(stdout)
                    (work_root / (action + '.stderr.log')).write_bytes(stderr)
                    if action in ('complete', 'retry', 'deadline-true', 'deadline-false'):
                        self.assertEqual(
                            child.returncode, 0, stderr.decode(errors='replace')[-2000:]
                        )
                        self.assertEqual(
                            broker_wire.read_packet(server, 2),
                            ({'ok': True, 'retry': action == 'retry', 'stop_reason': 0}, []),
                        )
                        for marker in (
                            b'HEADLESS_JOB_STARTED',
                            b'HEADLESS_JOB_DESTROYED',
                            b'SHIRAZ_RUNTIME_CLEANUP_COMPLETE',
                        ):
                            self.assertEqual(stdout.count(marker), 1)
                        self.assertLess(
                            stdout.index(b'HEADLESS_JOB_DESTROYED'),
                            stdout.index(b'SHIRAZ_RUNTIME_CLEANUP_COMPLETE'),
                        )
                        if action.startswith('deadline-'):
                            self.assertIn(b'APK_DEADLINE_STATE_OK ' + action[9:].encode(), stdout)
                    else:
                        self.assertNotEqual(child.returncode, 0)
                        if action in ('mismatch', 'malformed', 'bare-definition', 'attachments'):
                            self.assertNotIn(b'HEADLESS_JOB_STARTED', stdout)
                            self.assertIn(
                                b'Job payload attachments unsupported'
                                if action == 'attachments'
                                else b'Invalid job definition',
                                stderr,
                            )
                        else:
                            self.assertIn(b'JOB_EXPECTED_', stderr)
                            self.assertEqual(stdout.count(b'HEADLESS_JOB_DESTROYED'), 1)
                        with self.assertRaises((EOFError, ConnectionResetError)):
                            broker_wire.read_packet(server, 2)
                finally:
                    if child is not None and child.poll() is None:
                        child.kill()
                        child.communicate()
                    server.close()
                    client.close()

    def test_job_launch_requires_exclusive_broker_session(self):
        for extra in (
            {},
            {'registry': Path('/unused')},
            {'registry': Path('/unused'), '_broker_channel': object(), '_receiver': ('x', 9)},
        ):
            with self.assertRaisesRegex(FormatError, 'exclusive registered'):
                runtime.run_activity(Path('/unused.apk'), {}, _job=('x', 9), **extra)

    def test_inherited_broker_requires_registry(self):
        with self.assertRaisesRegex(FormatError, 'requires registered launch'):
            runtime.run_activity(Path('/unused.apk'), {}, _broker_channel=object())

    def test_receiver_admission_uses_receiver_declarations(self):
        component = 'org.shiraz.servicefixture.Worker'
        manifest = {
            'package': 'org.shiraz.servicefixture',
            'application_class': None,
            'services': [{'name': component}],
            'launcher_activities': [],
        }
        for declarations in (
            [],
            [{'name': component, 'enabled': 'false'}],
            [{'name': component, 'enabled': '@bool/enabled'}],
            [{'name': component}, {'name': component}],
        ):
            with (
                self.subTest(declarations=declarations),
                self.assertRaisesRegex(
                    FormatError,
                    'Receiver|Resource-based package flags|Unresolved manifest flag resource name',
                ),
            ):
                runtime.run_activity(
                    Path('/unused.apk'),
                    {'manifest': dict(manifest, receivers=declarations)},
                    registry=Path('/unused-registry'),
                    _receiver=(component, 9),
                )
        with self.assertRaisesRegex(FormatError, 'Conflicting'):
            runtime.run_activity(
                Path('/unused.apk'), {}, _service=(component, 9), _receiver=(component, 9)
            )

    def test_service_admission_rejects_unsupported_declarations(self):
        component = 'org.shiraz.servicefixture.Worker'
        manifest = {
            'package': 'org.shiraz.servicefixture',
            'application_class': None,
            'launcher_activities': [],
            'services': [],
        }
        for declarations in (
            [],
            [{'name': component, 'enabled': 'false'}],
            [{'name': component, 'isolated': 'true'}],
            [{'name': component, 'externalService': 'true'}],
            [{'name': component, 'enabled': '@bool/enabled'}],
            [{'name': component}, {'name': component}],
        ):
            with self.subTest(declarations=declarations), self.assertRaises(FormatError):
                runtime.run_activity(
                    Path('/unused.apk'),
                    {'manifest': dict(manifest, services=declarations)},
                    registry=Path('/unused-registry'),
                    _service=(component, 9),
                )


if __name__ == '__main__':
    unittest.main()
