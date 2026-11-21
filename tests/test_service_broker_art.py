# SPDX-License-Identifier: GPL-3.0-only
"""ART client integration over a host-issued fd; registry identities are test fixtures."""

import socket
import struct
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import test_service_broker as fixtures

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from art_smoke import HOST, JARS, NATIVE, environment  # noqa: E402
from build_macos_fixture import append_aligned_dex  # noqa: E402

from shiraz.broker_wire import read_packet, write_packet  # noqa: E402
from shiraz.service_broker import MAX_FRAME, read_frame  # noqa: E402
from shiraz.service_process import ServiceProcess  # noqa: E402


@unittest.skipUnless(
    (HOST / 'bin/dx').is_file() and (ROOT / 'build/shiraz-framework.jar').is_file(),
    'ART/framework required; run make service-broker-art-smoke',
)
class ServiceBrokerArtTests(unittest.TestCase):
    def test_art_resolution(self):
        fixture = fixtures.ServiceBrokerTests()
        fixture.setUp()
        with patch(
            'shiraz.package_registry.inspect_authenticators',
            return_value=[
                dict(
                    type='test.accounts',
                    component='test.auth.Service',
                    custom_tokens=False,
                    label=0,
                    icon=0,
                    smallIcon=0,
                    accountPreferences=0,
                )
            ],
        ):
            fixture.fixture.enroll(
                'test.auth',
                services=[dict(name='test.auth.Service')],
                activities=[
                    dict(name='test.auth.Login', exported='true', process=':login', launch_mode=1),
                    dict(name='test.auth.Private', exported='false'),
                ],
                activity_aliases=[
                    dict(name='test.auth.Alias', target='test.auth.Login', exported='true')
                ],
            )
        sync_descriptor = dict(
            component='test.sync.Adapter',
            authority='test.data',
            account_type='test.accounts',
            user_visible=False,
            supports_uploading=True,
            always_syncable=False,
            allow_parallel_syncs=False,
            settings_activity=None,
        )
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[sync_descriptor]):
            fixture.fixture.enroll(
                'test.sync', services=[dict(name='test.sync.Adapter', process=':sync')]
            )
            fixture.fixture.enroll(
                'test.synccompetitor',
                signer='b',
                services=[dict(name='test.sync.Adapter', process=':sync')],
            )
        fixture.fixture.enroll(
            'test.provider',
            providers=[
                dict(
                    name='test.provider.Data',
                    authorities='test.data',
                    access_metadata_version=1,
                    exported='true',
                    process=':data',
                    writePermission='test.UNKNOWN',
                )
            ],
        )
        work = ROOT / 'build/service-broker-art'
        work.mkdir(parents=True, exist_ok=True)
        (work / 'passed').unlink(missing_ok=True)
        try:
            with tempfile.TemporaryDirectory(dir=work) as temporary:
                temp = Path(temporary)
                classes = temp / 'classes'
                classes.mkdir()
                core = (
                    ROOT
                    / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
                )
                subprocess.run(
                    [
                        'javac',
                        '--release',
                        '8',
                        '-cp',
                        str(ROOT / 'build/framework/classes') + ':' + str(core),
                        '-d',
                        str(classes),
                        str(ROOT / 'tests/java/ServiceBrokerClientSmoke.java'),
                        str(ROOT / 'tests/java/ServiceBrokerDescriptorSmoke.java'),
                        str(ROOT / 'tests/java/SyncDiscoveryApiSmoke.java'),
                        str(ROOT / 'tests/java/AuthenticatorResponseTransportSmoke.java'),
                        str(ROOT / 'tests/java/AuthenticatorRequestTransportSmoke.java'),
                        str(ROOT / 'tests/java/AbstractAuthenticatorSmoke.java'),
                        str(ROOT / 'tests/java/AuthenticatorEndpointSmoke.java'),
                        str(ROOT / 'tests/java/AuthenticatorAdmissionSmoke.java'),
                        str(ROOT / 'tests/java/AccountFutureSmoke.java'),
                        str(ROOT / 'tests/java/AccountListenersSmoke.java'),
                        str(ROOT / 'tests/java/HandlerShutdownSmoke.java'),
                        str(ROOT / 'tests/java/JobBrokerSmoke.java'),
                        str(ROOT / 'tests/java/AuthenticatorActivitySmoke.java'),
                        str(ROOT / 'tests/java/ActivityLaunchTransportSmoke.java'),
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                env = environment(temp)
                dex = temp / 'classes.dex'
                subprocess.run(
                    [
                        str(HOST / 'bin/dx'),
                        '--dex',
                        '--min-sdk-version=27',
                        '--output=' + str(dex),
                        str(classes),
                    ],
                    env=env,
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                jar = temp / 'smoke.jar'
                with zipfile.ZipFile(jar, 'w'):
                    pass
                append_aligned_dex(jar, dex)
                receiver_channel = fixture.broker.open_session(
                    fixture.target['package'], fixture.target['apk_sha256'], process_name=':events'
                )
                fixture.channels.append(receiver_channel)
                self.assertTrue(
                    fixture.exchange(
                        receiver_channel,
                        dict(
                            op='register_dynamic_receiver',
                            receiver='a' * 32,
                            permission='test.PRIVATE',
                            flags=2,
                            filter=dict(
                                action=['test.EVENT'], category=[], mime=[], scheme=[], priority=5
                            ),
                        ),
                    )['ok']
                )
                channel = fixture.channel(fixture.caller)
                vm = HOST / 'bin/dalvikvm64'
                if not vm.is_file():
                    vm = HOST / 'bin/dalvikvm'
                command = [
                    str(vm),
                    '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                    '-Xcheck:jni',
                    '-Xusejit:false',
                    '-Xint',
                    '-Xnoimage-dex2oat',
                    '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
                    '-cp',
                    str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar),
                    'ServiceBrokerClientSmoke',
                    str(channel.fileno()),
                    fixture.target['package'],
                    'test.target.Worker',
                    fixture.target['apk_sha256'],
                    str(fixture.target['uid']),
                ]
                for account_add in (False, True):
                    suffix = '-add' if account_add else ''
                    management, worker = socket.socketpair()
                    admission_command = command[:-6] + [
                        'shiraz.runtime.AuthenticatorAdmissionSmoke'
                    ]
                    admitted = None
                    host = ServiceProcess.__new__(ServiceProcess)
                    host.lock, host.closed, host.endpoint = threading.Lock(), False, management
                    try:
                        admitted = subprocess.Popen(
                            admission_command + ['receive' + suffix, str(worker.fileno())],
                            env=env,
                            pass_fds=(worker.fileno(),),
                            stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,
                        )
                        worker.close()
                        with (
                            host.open_add_account_endpoint(10000, 'test.caller', 'test.accounts')
                            if account_add
                            else host.open_authenticator_endpoint(10000)
                        ) as endpoint:
                            sent = subprocess.run(
                                admission_command + ['send' + suffix, str(endpoint.fileno())],
                                env=env,
                                pass_fds=(endpoint.fileno(),),
                                capture_output=True,
                                timeout=20,
                                check=False,
                            )
                        management.close()
                        stdout, stderr = admitted.communicate(timeout=20)
                        (work / ('authenticator-admission' + suffix + '-sender.log')).write_bytes(
                            sent.stdout + sent.stderr
                        )
                        (work / ('authenticator-admission' + suffix + '-receiver.log')).write_bytes(
                            stdout + stderr
                        )
                        self.assertEqual(
                            sent.returncode, 0, 'Inspect authenticator-admission-sender.log'
                        )
                        self.assertEqual(
                            admitted.returncode, 0, 'Inspect authenticator-admission-receiver.log'
                        )
                        self.assertIn(b'AUTHENTICATOR_ADMISSION_SENDER_OK', sent.stdout)
                        self.assertIn(b'AUTHENTICATOR_ADMISSION_RECEIVER_OK', stdout)
                    finally:
                        management.close()
                        worker.close()
                        if admitted is not None and admitted.poll() is None:
                            admitted.kill()
                            admitted.communicate(timeout=5)
                for test_class, log_prefix, marker in (
                    (
                        'AuthenticatorResponseTransportSmoke',
                        'authenticator-response',
                        b'AUTHENTICATOR_REMOTE',
                    ),
                    (
                        'AuthenticatorRequestTransportSmoke',
                        'authenticator-request',
                        b'AUTHENTICATOR_REQUEST',
                    ),
                    ('ActivityLaunchTransportSmoke', 'activity-launch', b'ACTIVITY_LAUNCH'),
                ):
                    receiver, sender = socket.socketpair()
                    callback_command = command[:-6] + [test_class]
                    process = None
                    try:
                        process = subprocess.Popen(
                            callback_command + ['receive', str(receiver.fileno())],
                            env=env,
                            pass_fds=(receiver.fileno(),),
                            stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,
                        )
                        receiver.close()
                        sent = subprocess.run(
                            callback_command + ['send', str(sender.fileno())],
                            env=env,
                            pass_fds=(sender.fileno(),),
                            capture_output=True,
                            timeout=20,
                            check=False,
                        )
                        sender.close()
                        stdout, stderr = process.communicate(timeout=20)
                        (work / (log_prefix + '-sender.log')).write_bytes(sent.stdout + sent.stderr)
                        (work / (log_prefix + '-receiver.log')).write_bytes(stdout + stderr)
                        self.assertEqual(
                            sent.returncode, 0, 'Inspect ' + log_prefix + '-sender.log'
                        )
                        self.assertEqual(
                            process.returncode, 0, 'Inspect ' + log_prefix + '-receiver.log'
                        )
                        self.assertIn(marker + b'_SENDER_OK', sent.stdout)
                        self.assertIn(marker + b'_RECEIVER_OK', stdout)
                    finally:
                        receiver.close()
                        sender.close()
                        if process is not None and process.poll() is None:
                            process.kill()
                            process.communicate(timeout=5)
                result = subprocess.run(
                    command,
                    env=env,
                    pass_fds=(channel.fileno(),),
                    capture_output=True,
                    timeout=30,
                    check=False,
                )
                (work / 'stdout.log').write_bytes(result.stdout)
                (work / 'stderr.log').write_bytes(result.stderr)
                self.assertEqual(result.returncode, 0, f'ART broker test failed; inspect {work}')
                self.assertIn(b'ART_SERVICE_BROKER_RESOLUTION_OK', result.stdout)
                self.assertIn(b'ART_PROVIDER_RESOLUTION_OK', result.stdout)
                self.assertIn(b'AUTHENTICATOR_ACTIVITY_LIFECYCLE_OK', result.stdout)
                with patch(
                    'shiraz.package_registry.inspect_authenticators',
                    return_value=[
                        dict(
                            type='test.accounts',
                            component='test.auth.Service',
                            custom_tokens=True,
                            label=0,
                            icon=0,
                            smallIcon=0,
                            accountPreferences=0,
                        )
                    ],
                ):
                    fixture.fixture.enroll(
                        'test.auth', version=2, services=[dict(name='test.auth.Service')]
                    )
                with fixture.channel(fixture.caller) as custom_channel:
                    custom_run = subprocess.run(
                        command[:-5] + [str(custom_channel.fileno()), 'custom-token'],
                        env=env,
                        pass_fds=(custom_channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                        check=False,
                    )
                    (work / 'custom-token.log').write_bytes(custom_run.stdout + custom_run.stderr)
                    self.assertEqual(custom_run.returncode, 0, 'Inspect custom-token.log')
                    self.assertIn(b'ART_CUSTOM_TOKEN_CACHE_OK', custom_run.stdout)
                    self.assertIn(b'ART_ACCOUNT_SNAPSHOT_OK', custom_run.stdout)
                    self.assertIn(b'ART_ACCOUNT_WATCH_OK', custom_run.stdout)
                job_owner = fixture.fixture.enroll(
                    'test.jobs',
                    permissions=['android.permission.RECEIVE_BOOT_COMPLETED'],
                    services=[
                        dict(
                            name='JobBrokerSmoke$Worker',
                            exported='false',
                            permission='android.permission.BIND_JOB_SERVICE',
                        )
                    ],
                )
                with fixture.channel(job_owner) as job_channel:
                    job_run = subprocess.run(
                        command[:-5] + [str(job_channel.fileno()), 'job'],
                        env=env,
                        pass_fds=(job_channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                        check=False,
                    )
                    (work / 'job-admission.log').write_bytes(job_run.stdout + job_run.stderr)
                    self.assertEqual(job_run.returncode, 0, 'Inspect job-admission.log')
                    self.assertIn(b'ART_JOB_BROKER_ADMISSION_OK', job_run.stdout)
                    self.assertIn(b'ART_JOB_BROKER_PERSISTED_CLIENT_OK', job_run.stdout)
                    self.assertIn(b'ART_PUBLIC_DURABLE_JOB_LIFECYCLE_OK', job_run.stdout)
                with fixture.channel(job_owner) as recovery_channel:
                    recovery = subprocess.run(
                        command[:-5] + [str(recovery_channel.fileno()), 'job-recover'],
                        env=env,
                        pass_fds=(recovery_channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                        check=False,
                    )
                    (work / 'job-recovery.log').write_bytes(recovery.stdout + recovery.stderr)
                    self.assertEqual(recovery.returncode, 0, 'Inspect job-recovery.log')
                    self.assertIn(b'ART_PUBLIC_DURABLE_JOB_PROCESS_RECOVERY_OK', recovery.stdout)
                self.assertIn(b'ART_ACCOUNT_LISTENERS_OK', custom_run.stdout)
                self.assertIn(b'ART_HANDLER_SHUTDOWN_OK', custom_run.stdout)
                self.assertIn(b'ART_HANDLER_SHUTDOWN_TIMEOUT_OK', custom_run.stdout)
                self.assertIn(b'ART_ACCOUNT_LISTENER_BACKPRESSURE_REVOCATION_OK', custom_run.stdout)
                self.assertIn(b'ART_ACCOUNT_LISTENER_RUNTIME_SHUTDOWN_OK', custom_run.stdout)
                server, client = socket.socketpair()

                def malformed_peer():
                    try:
                        read_frame(server, idle_timeout=3)
                        server.sendall(struct.pack('<III', 0x315A4853, MAX_FRAME + 1, 0))
                    finally:
                        server.close()

                peer = threading.Thread(target=malformed_peer)
                peer.start()
                try:
                    bad_command = command[:-5] + [str(client.fileno()), 'malformed']
                    bad = subprocess.run(
                        bad_command,
                        env=env,
                        pass_fds=(client.fileno(),),
                        capture_output=True,
                        timeout=30,
                        check=False,
                    )
                    (work / 'malformed-stderr.log').write_bytes(bad.stderr)
                    self.assertEqual(bad.returncode, 0)
                    self.assertIn(b'ART_SERVICE_BROKER_MALFORMED_REJECTED', bad.stdout)
                finally:
                    client.close()
                    peer.join(timeout=5)
                    self.assertFalse(peer.is_alive())
                server, client = socket.socketpair()
                peer, endpoint = socket.socketpair()
                if hasattr(socket, 'SO_PASSCRED'):
                    client.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
                try:
                    write_packet(server, {'probe': True}, (endpoint.fileno(),))
                    endpoint.close()
                    descriptor_command = command[:-6] + [
                        'ServiceBrokerDescriptorSmoke',
                        str(client.fileno()),
                    ]
                    transferred = subprocess.run(
                        descriptor_command,
                        env=env,
                        pass_fds=(client.fileno(),),
                        capture_output=True,
                        timeout=30,
                        check=False,
                    )
                    (work / 'descriptor-stderr.log').write_bytes(transferred.stderr)
                    self.assertEqual(transferred.returncode, 0, 'Inspect descriptor-stderr.log')
                    self.assertIn(b'ART_BROKER_DESCRIPTOR_ROUNDTRIP_OK', transferred.stdout)
                    value, descriptors = read_packet(server, 3)
                    self.assertEqual(value, {'ok': True})
                    self.assertEqual(len(descriptors), 1)
                    with socket.socket(fileno=descriptors[0]) as returned:
                        returned.sendall(b'capability roundtrip')
                        peer.settimeout(3)
                        self.assertEqual(peer.recv(100), b'capability roundtrip')
                    self.assertEqual(peer.recv(1), b'')
                finally:
                    server.close()
                    client.close()
                    peer.close()
                    endpoint.close()
                receiver = dict(
                    package='test.target',
                    uid=10001,
                    apk_sha256='a' * 64,
                    component='test.target.Receiver',
                    process='test.target',
                )
                authenticator = dict(
                    type='test.accounts',
                    package='test.target',
                    component='test.target.Auth',
                    process=':accounts',
                    apk_sha256='a' * 64,
                    uid=10001,
                    custom_tokens=False,
                    label=0,
                    icon=0,
                    smallIcon=0,
                    accountPreferences=0,
                )
                preparation = dict(
                    owner_uid=10001,
                    owner_sha256='a' * 64,
                    revision='b' * 32,
                    custom_tokens=False,
                    token=None,
                )
                activity_target = dict(
                    package='test.auth',
                    requested_component='test.auth.Login',
                    component='test.auth.Login',
                    process=':login',
                    uid=10002,
                    apk_sha256='a' * 64,
                    launch_mode=1,
                )
                custom_preparation = dict(
                    preparation, custom_tokens=True, token='synthetic-custom', expires_ms=2000
                )
                auth_service = dict(
                    package='test.target',
                    component='test.target.Auth',
                    process=':accounts',
                    apk_sha256='a' * 64,
                    uid=10001,
                )
                dynamic = dict(
                    package='test.target',
                    uid=10001,
                    apk_sha256='a' * 64,
                    process='test.target:events',
                    receiver='a' * 32,
                    registration='b' * 32,
                    priority=5,
                )
                job = dict(
                    package='test.jobs',
                    component='JobBrokerSmoke$Worker',
                    process='test.jobs',
                    uid=10000,
                    apk_sha256='a' * 64,
                )
                sync_reply = dict(
                    sync_descriptor,
                    package='test.sync',
                    uid=10000,
                    apk_sha256='a' * 64,
                    process=':sync',
                )
                provider_reply = dict(
                    package='test.provider',
                    component='test.provider.Data',
                    process=':data',
                    uid=10000,
                    apk_sha256='a' * 64,
                    authority='test.data',
                    access='read',
                )
                for mode, malformed in (
                    ('malformed-provider', {'ok': True}),
                    (
                        'malformed-provider',
                        {'ok': True, 'provider': dict(provider_reply, authority='test.other')},
                    ),
                    (
                        'malformed-provider',
                        {'ok': True, 'provider': dict(provider_reply, access='write')},
                    ),
                    (
                        'malformed-provider',
                        {'ok': True, 'provider': dict(provider_reply, uid='10000')},
                    ),
                    (
                        'malformed-provider',
                        {'ok': True, 'provider': dict(provider_reply, process='bad/path')},
                    ),
                    (
                        'malformed-provider',
                        {'ok': True, 'provider': dict(provider_reply, apk_sha256='bad')},
                    ),
                    (
                        'malformed-provider',
                        {'ok': False, 'error': 'denied', 'provider': provider_reply},
                    ),
                    (
                        'malformed-selected-sync',
                        {'ok': True, 'sync_adapters': [sync_reply, sync_reply]},
                    ),
                    ('malformed-sync', {'ok': True}),
                    (
                        'malformed-sync',
                        {'ok': True, 'sync_adapters': [dict(sync_reply, uid='10000')]},
                    ),
                    (
                        'malformed-sync',
                        {'ok': True, 'sync_adapters': [dict(sync_reply, user_visible='false')]},
                    ),
                    (
                        'malformed-sync',
                        {'ok': True, 'sync_adapters': [dict(sync_reply, process='bad/path')]},
                    ),
                    (
                        'malformed-sync',
                        {'ok': True, 'sync_adapters': [dict(sync_reply, apk_sha256='bad')]},
                    ),
                    (
                        'malformed-sync',
                        {'ok': True, 'sync_adapters': [dict(sync_reply, unexpected=True)]},
                    ),
                    ('malformed-sync', {'ok': False, 'error': 'denied', 'sync_adapters': []}),
                    ('malformed-job', {'ok': True}),
                    ('malformed-job', {'ok': True, 'job': dict(job, uid='10000')}),
                    ('malformed-job', {'ok': True, 'job': dict(job, uid=10000.0)}),
                    ('malformed-job', {'ok': True, 'job': dict(job, package='test.other')}),
                    ('malformed-job', {'ok': True, 'job': dict(job, component='test.Wrong')}),
                    ('malformed-job', {'ok': True, 'job': dict(job, process=':bad:process')}),
                    ('malformed-job', {'ok': True, 'job': dict(job, apk_sha256='bad')}),
                    ('malformed-job', {'ok': False, 'error': 'denied', 'job': job}),
                    ('malformed-job', {'ok': True, 'error': 'denied', 'job': job}),
                    (
                        'malformed-lookup',
                        {'ok': True, 'packages': [{'package': 'test.target', 'uid': 10001}]},
                    ),
                    ('malformed-lookup', {'ok': True}),
                    (
                        'malformed-lookup',
                        {'ok': True, 'packages': [{'package': 'a/b', 'uid': 10000}]},
                    ),
                    ('malformed-signature', {'ok': True, 'result': 1}),
                    ('malformed-signature', {'ok': True}),
                    ('malformed-permission', {'ok': True, 'result': 1}),
                    ('malformed-permission', {'ok': True, 'result': '0'}),
                    ('malformed-permission', {'ok': True, 'result': 0.0}),
                    ('malformed-permission', {'ok': True}),
                    ('malformed-permission', {'ok': True, 'result': 0, 'error': 'contradiction'}),
                    ('malformed-signature', {'ok': True, 'result': 0, 'error': 'contradiction'}),
                    ('malformed-token-preparation', {'ok': True, 'result': None}),
                    (
                        'malformed-token-preparation',
                        {'ok': True, 'result': dict(preparation, owner_uid=0)},
                    ),
                    (
                        'malformed-token-preparation',
                        {'ok': True, 'result': dict(preparation, owner_sha256='invalid')},
                    ),
                    (
                        'malformed-token-preparation',
                        {'ok': True, 'result': dict(preparation, revision='invalid')},
                    ),
                    (
                        'malformed-token-preparation',
                        {'ok': True, 'result': dict(preparation, custom_tokens='false')},
                    ),
                    (
                        'malformed-token-preparation',
                        {'ok': True, 'result': dict(preparation, token=123)},
                    ),
                    (
                        'malformed-token-preparation',
                        {
                            'ok': True,
                            'result': dict(preparation, custom_tokens=True, token='fixture-token'),
                        },
                    ),
                    (
                        'malformed-token-preparation',
                        {
                            'ok': True,
                            'result': {
                                key: value for key, value in preparation.items() if key != 'token'
                            },
                        },
                    ),
                    ('malformed-custom-token', {'ok': True, 'result': preparation}),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, expires_ms=None)},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, expires_ms='2000')},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, expires_ms=2.5)},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, expires_ms=-1)},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, expires_ms=2**63)},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, token=None)},
                    ),
                    (
                        'malformed-custom-token',
                        {'ok': True, 'result': dict(custom_preparation, custom_tokens=False)},
                    ),
                    ('malformed-activity', {'ok': True}),
                    ('malformed-activity', {'ok': True, 'activity': None}),
                    ('malformed-activity', {'ok': 'true', 'activity': activity_target}),
                    ('malformed-activity', {'ok': True, 'activity': dict(activity_target, uid=0)}),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, launch_mode='1')},
                    ),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, launch_mode=5)},
                    ),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, apk_sha256='invalid')},
                    ),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, component='../Login')},
                    ),
                    (
                        'malformed-activity',
                        {
                            'ok': True,
                            'activity': dict(
                                activity_target, requested_component='test.auth.Other'
                            ),
                        },
                    ),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, package='test.other')},
                    ),
                    (
                        'malformed-activity',
                        {'ok': True, 'activity': dict(activity_target, process=':bad:process')},
                    ),
                    (
                        'malformed-activity',
                        {'ok': False, 'error': 'denied', 'activity': activity_target},
                    ),
                    (
                        'malformed-activity-open',
                        {
                            'ok': True,
                            'activity': dict(activity_target, uid=10003),
                            'lease': 'c' * 32,
                        },
                    ),
                    (
                        'malformed-activity-open',
                        {
                            'ok': True,
                            'activity': dict(activity_target, process=':other'),
                            'lease': 'c' * 32,
                        },
                    ),
                    (
                        'malformed-activity-open',
                        {
                            'ok': True,
                            'activity': dict(activity_target, apk_sha256='b' * 64),
                            'lease': 'c' * 32,
                        },
                    ),
                    (
                        'malformed-activity-open',
                        {'ok': True, 'activity': activity_target, 'lease': 'invalid'},
                    ),
                    ('malformed-activity-open', {'ok': False, 'error': 'denied'}),
                    (
                        'malformed-activity-open-nofd',
                        {'ok': True, 'activity': activity_target, 'lease': 'c' * 32},
                    ),
                    ('malformed-account-snapshot', {'ok': True, 'result': None}),
                    ('malformed-account-snapshot', {'ok': True, 'result': {}}),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
                            },
                        },
                    ),
                    ('malformed-account-snapshot', {'ok': True, 'result': {'accounts': []}}),
                    (
                        'malformed-account-snapshot',
                        {'ok': True, 'result': {'revision': 1, 'accounts': []}},
                    ),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg',
                                'accounts': [],
                            },
                        },
                    ),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                                'accounts': [],
                            },
                        },
                    ),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                                'accounts': None,
                            },
                        },
                    ),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                                'accounts': [
                                    {'name': 'x', 'type': 't'},
                                    {'name': 'x', 'type': 't'},
                                ],
                            },
                        },
                    ),
                    (
                        'malformed-account-snapshot',
                        {
                            'ok': True,
                            'result': {
                                'revision': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                                'accounts': [],
                                'extra': False,
                            },
                        },
                    ),
                    (
                        'malformed-account-watch',
                        {'ok': True, 'lease': 'a' * 32, 'revision': 'b' * 64},
                    ),
                    ('malformed-account-watch', {'ok': True}),
                    (
                        'malformed-account-watch-fd',
                        {'ok': True, 'lease': 'bad', 'revision': 'b' * 64},
                    ),
                    (
                        'malformed-account-watch-fd',
                        {'ok': True, 'lease': 'a' * 32, 'revision': 'bad'},
                    ),
                    ('malformed-account-watch-fd', {'ok': False, 'error': 'denied'}),
                    ('malformed-account-notice', {}),
                    ('malformed-account-notice', {'revision': None}),
                    ('malformed-account-notice', {'revision': 'bad'}),
                    ('malformed-account-notice', {'revision': 'b' * 64, 'extra': True}),
                    ('malformed-account-notice', {'revision': 'b' * 300}),
                    ('malformed-account-notice-fd', {'revision': 'b' * 64}),
                    ('malformed-account-watch', {'ok': 'true'}),
                    (
                        'malformed-account-watch',
                        {'ok': False, 'error': 'denied', 'revision': 'b' * 64},
                    ),
                    ('malformed-account', {'ok': True}),
                    ('malformed-account', {'ok': True, 'result': True}),
                    ('malformed-account', {'ok': True, 'result': None, 'error': 'contradiction'}),
                    ('malformed-account', {'ok': False, 'error': 'denied', 'result': None}),
                    (
                        'malformed-account-list',
                        {'ok': True, 'result': [{'name': 42, 'type': 'test.accounts'}]},
                    ),
                    ('malformed-account-list', {'ok': True, 'result': None}),
                    ('malformed-account-list', {'ok': True, 'result': [{'type': 'test.accounts'}]}),
                    (
                        'malformed-account-list',
                        {'ok': True, 'result': [{'name': 'fixture', 'type': 'wrong.type'}]},
                    ),
                    (
                        'malformed-account-list',
                        {'ok': True, 'result': [{'name': 'fixture', 'type': 'test.accounts'}] * 2},
                    ),
                    ('malformed-account-add', {'ok': True, 'result': None}),
                    ('malformed-account-add', {'ok': True, 'result': 'true'}),
                    ('malformed-authenticator-open', {'ok': True}),
                    (
                        'malformed-authenticator-open',
                        {'ok': True, 'service': auth_service, 'lease': 'a' * 32},
                    ),
                    (
                        'malformed-authenticator-open',
                        {'ok': True, 'service': dict(auth_service, uid=10002), 'lease': 'a' * 32},
                    ),
                    (
                        'malformed-authenticator-open',
                        {
                            'ok': True,
                            'service': dict(auth_service, process=':wrong'),
                            'lease': 'a' * 32,
                        },
                    ),
                    (
                        'malformed-authenticator-open',
                        {
                            'ok': True,
                            'service': dict(auth_service, apk_sha256='b' * 64),
                            'lease': 'a' * 32,
                        },
                    ),
                    (
                        'malformed-authenticator-open',
                        {'ok': False, 'error': 'denied', 'lease': 'a' * 32},
                    ),
                    ('malformed-authenticator-resolution', {'ok': True}),
                    ('malformed-authenticator-resolution', {'ok': True, 'authenticator': None}),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': True, 'authenticator': dict(authenticator, type='wrong.type')},
                    ),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': True, 'authenticator': dict(authenticator, type=42)},
                    ),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': True, 'authenticator': dict(authenticator, uid=0)},
                    ),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': True, 'authenticator': dict(authenticator, apk_sha256='invalid')},
                    ),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': True, 'authenticator': authenticator, 'error': 'contradiction'},
                    ),
                    (
                        'malformed-authenticator-resolution',
                        {'ok': False, 'authenticator': authenticator, 'error': 'denied'},
                    ),
                    ('malformed-authenticators', {'ok': True}),
                    (
                        'malformed-authenticators',
                        {'ok': True, 'authenticators': [dict(authenticator, type='')]},
                    ),
                    (
                        'malformed-authenticators',
                        {
                            'ok': True,
                            'authenticators': [dict(authenticator, process=':bad:process')],
                        },
                    ),
                    (
                        'malformed-authenticators',
                        {'ok': True, 'authenticators': [dict(authenticator, label=0.5)]},
                    ),
                    (
                        'malformed-authenticators',
                        {'ok': True, 'authenticators': [dict(authenticator, icon=4294967296)]},
                    ),
                    (
                        'malformed-authenticators',
                        {'ok': True, 'authenticators': [authenticator, authenticator]},
                    ),
                    (
                        'malformed-authenticators',
                        {'ok': True, 'authenticators': [], 'error': 'contradiction'},
                    ),
                    ('malformed-dynamic-open', {'ok': True}),
                    (
                        'malformed-dynamic-open',
                        {'ok': True, 'receiver': dynamic, 'lease': 'c' * 32},
                    ),
                    ('malformed-dynamic-open', {'ok': False, 'error': 'denied', 'lease': 'c' * 32}),
                    (
                        'malformed-dynamic-open',
                        {
                            'ok': True,
                            'receiver': dict(dynamic, registration='d' * 32),
                            'lease': 'c' * 32,
                        },
                    ),
                    ('malformed-dynamic-query', {'ok': True}),
                    (
                        'malformed-dynamic-query',
                        {'ok': True, 'receivers': [dict(dynamic, uid=10001.0)]},
                    ),
                    (
                        'malformed-dynamic-query',
                        {'ok': True, 'receivers': [dict(dynamic, registration='bad')]},
                    ),
                    (
                        'malformed-dynamic-query',
                        {'ok': True, 'receivers': [dict(dynamic, package='test.other')]},
                    ),
                    (
                        'malformed-dynamic-query',
                        {'ok': True, 'receivers': [dict(dynamic, process='test.other:events')]},
                    ),
                    ('malformed-dynamic-query', {'ok': True, 'receivers': [dynamic, dynamic]}),
                    (
                        'malformed-dynamic-query',
                        {
                            'ok': True,
                            'receivers': [
                                dynamic,
                                dict(
                                    dynamic,
                                    receiver='c' * 32,
                                    registration='d' * 32,
                                    apk_sha256='b' * 64,
                                ),
                            ],
                        },
                    ),
                    (
                        'malformed-dynamic-query',
                        {'ok': True, 'receivers': [], 'error': 'contradiction'},
                    ),
                    ('malformed-dynamic', {'ok': True}),
                    ('malformed-dynamic', {'ok': True, 'registration': '../bad'}),
                    (
                        'malformed-dynamic',
                        {'ok': True, 'registration': 'a' * 32, 'error': 'contradiction'},
                    ),
                    (
                        'malformed-dynamic',
                        {'ok': False, 'error': 'denied', 'registration': 'a' * 32},
                    ),
                    ('malformed-dynamic-remove', {'ok': True, 'registration': 'a' * 32}),
                    (
                        'malformed-receiver-open',
                        {'ok': False, 'error': 'denied', 'filtered': False},
                    ),
                    (
                        'malformed-receiver-open',
                        {'ok': True, 'receiver': receiver, 'filtered': True},
                    ),
                    ('malformed-receiver-open', {'ok': False, 'filtered': True}),
                    (
                        'malformed-receiver-open',
                        {'ok': False, 'error': 'denied', 'filtered': True, 'lease': 'a' * 32},
                    ),
                    ('malformed-service', {'ok': False, 'error': 'denied', 'filtered': True}),
                    ('malformed-receiver', {'ok': True}),
                    (
                        'malformed-receiver',
                        {'ok': True, 'receivers': [dict(receiver, package='test.other')]},
                    ),
                    ('malformed-receiver', {'ok': True, 'receivers': [receiver, receiver]}),
                    (
                        'malformed-receiver',
                        {
                            'ok': True,
                            'receivers': [
                                receiver,
                                dict(receiver, component='test.target.Other', apk_sha256='b' * 64),
                            ],
                        },
                    ),
                    ('malformed-receiver', {'ok': True, 'receivers': [], 'error': 'contradiction'}),
                ):
                    server, client = socket.socketpair()
                    if hasattr(socket, 'SO_PASSCRED'):
                        client.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
                    peer_errors = []

                    def invalid_lookup_peer():
                        try:
                            request = read_frame(server, idle_timeout=3)
                            if mode == 'malformed-dynamic':
                                self.assertEqual(request['filter']['mime'], ['text/*'])
                                self.assertIsNone(request['permission'])
                                self.assertEqual(request['receiver'], 'a' * 32)
                            if mode.startswith('malformed-account-notice'):
                                self.assertEqual(request, {'op': 'watch_accounts'})
                                left, right = socket.socketpair()
                                with left, right:
                                    write_packet(
                                        server,
                                        {'ok': True, 'lease': 'a' * 32, 'revision': 'b' * 64},
                                        (left.fileno(),),
                                    )
                                    write_packet(
                                        right,
                                        malformed,
                                        (left.fileno(),) if mode.endswith('-fd') else (),
                                    )
                                    self.assertEqual(
                                        read_frame(server, idle_timeout=3),
                                        {'op': 'unwatch_accounts', 'lease': 'a' * 32},
                                    )
                                    write_packet(server, {'ok': True})
                                    self.assertEqual(
                                        read_frame(server, idle_timeout=3),
                                        {
                                            'op': 'account_operation',
                                            'request': {'action': 'snapshot'},
                                        },
                                    )
                                    write_packet(
                                        server,
                                        {
                                            'ok': True,
                                            'result': {'revision': 'b' * 64, 'accounts': []},
                                        },
                                    )
                            elif mode == 'malformed-account-watch-fd':
                                left, right = socket.socketpair()
                                with left, right:
                                    write_packet(server, malformed, (left.fileno(),))
                            elif mode.startswith('malformed-activity-open'):
                                write_packet(server, {'ok': True, 'activity': activity_target})
                                read_frame(server, idle_timeout=3)
                                if mode == 'malformed-activity-open':
                                    left, right = socket.socketpair()
                                    with left, right:
                                        write_packet(server, malformed, (left.fileno(),))
                                else:
                                    write_packet(server, malformed)
                            else:
                                write_packet(server, malformed)
                        except BaseException as error:
                            peer_errors.append(error)
                        finally:
                            server.close()

                    peer = threading.Thread(target=invalid_lookup_peer)
                    peer.start()
                    try:
                        invalid_command = command[:-5] + [str(client.fileno()), mode]
                        invalid = subprocess.run(
                            invalid_command,
                            env=env,
                            pass_fds=(client.fileno(),),
                            capture_output=True,
                            timeout=30,
                            check=False,
                        )
                        self.assertEqual(
                            invalid.returncode, 0, invalid.stderr.decode(errors='replace')
                        )
                        self.assertIn(b'ART_SERVICE_BROKER_MALFORMED_REJECTED', invalid.stdout)
                    finally:
                        client.close()
                        peer.join(timeout=5)
                        self.assertFalse(peer.is_alive())
                        self.assertEqual(peer_errors, [], f'Invalid reply peer failed for {mode}')
                (work / 'passed').write_text(
                    'ART_SERVICE_BROKER_RESOLUTION_OK\nfixture registry; not Google APK acceptance\n'
                )
        finally:
            fixture.tearDown()


if __name__ == '__main__':
    unittest.main()
