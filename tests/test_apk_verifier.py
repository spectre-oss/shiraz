# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
import os
import queue
import selectors
import signal
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch

from shiraz.apk import FormatError, inspect_apk
from shiraz.apk_verifier import JAR, ROOT, verify_signatures
from shiraz.broker_wire import read_packet, write_packet, write_payload
from shiraz.package_registry import PackageRegistry


@unittest.skipUnless(
    JAR.is_file() and (ROOT / 'build/macos-apk/background.apk').is_file(),
    'run make apk-signature-smoke for signed-APK integration tests',
)
class ApkVerifierTests(unittest.TestCase):
    def test_verified_identity_tamper_and_unsigned(self):
        work = ROOT / 'build/apk-verifier-tests'
        work.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=work) as directory:
            temp = Path(directory)
            unsigned = ROOT / 'build/macos-apk/background.apk'
            with self.assertRaisesRegex(FormatError, 'verification failed'):
                verify_signatures(unsigned)
            store = temp / 'fixture.p12'
            password = temp / 'password'
            password.write_text('local-fixture-only\n')
            subprocess.run(
                [
                    'keytool',
                    '-genkeypair',
                    '-alias',
                    'fixture',
                    '-keyalg',
                    'RSA',
                    '-keysize',
                    '2048',
                    '-validity',
                    '2',
                    '-dname',
                    'CN=Shiraz test fixture',
                    '-keystore',
                    str(store),
                    '-storepass:file',
                    str(password),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            signed = temp / 'signed.apk'
            subprocess.run(
                [
                    'jarsigner',
                    '-keystore',
                    str(store),
                    '-storepass:file',
                    str(password),
                    '-signedjar',
                    str(signed),
                    str(unsigned),
                    'fixture',
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            modern_target = int(inspect_apk(unsigned)['manifest'].get('target_sdk') or 1) >= 30
            if modern_target:
                with self.assertRaisesRegex(FormatError, 'verification failed'):
                    verify_signatures(signed)
                subprocess.run(
                    [
                        'javac',
                        '--release',
                        '8',
                        '-cp',
                        str(JAR),
                        '-d',
                        str(temp),
                        str(ROOT / 'tests/java/SignApkFixture.java'),
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                subprocess.run(
                    [
                        'java',
                        '-cp',
                        str(temp) + ':' + str(JAR),
                        'SignApkFixture',
                        str(unsigned),
                        str(signed),
                        str(store),
                        str(password),
                        'v2-only',
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
            result = verify_signatures(signed)
            self.assertEqual(result['apk_sha256'], hashlib.sha256(signed.read_bytes()).hexdigest())
            self.assertEqual(len(result['verified_signers_sha256']), 1)
            certificate = subprocess.run(
                [
                    'keytool',
                    '-exportcert',
                    '-alias',
                    'fixture',
                    '-keystore',
                    str(store),
                    '-storepass:file',
                    str(password),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            ).stdout
            self.assertEqual(
                result['verified_signers_sha256'], [hashlib.sha256(certificate).hexdigest()]
            )
            subprocess.run(
                [
                    'javac',
                    '--release',
                    '8',
                    '-cp',
                    str(JAR),
                    '-d',
                    str(temp),
                    str(ROOT / 'tests/java/SignApkFixture.java'),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            modern = temp / 'modern.apk'
            subprocess.run(
                [
                    'java',
                    '-cp',
                    str(temp) + ':' + str(JAR),
                    'SignApkFixture',
                    str(unsigned),
                    str(modern),
                    str(store),
                    str(password),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            self.assertEqual(
                verify_signatures(modern)['verified_signers_sha256'],
                result['verified_signers_sha256'],
            )
            registry = PackageRegistry(temp / 'registry')
            enrolled = registry.enroll(signed, expected_current='absent')
            if os.environ.get('SHIRAZ_TEST_JOB_PROCESS') == '1':
                from shiraz.service_broker import ServiceBroker

                launch = subprocess.run(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'run',
                        str(signed),
                        '--registry',
                        str(registry.root),
                        '--timeout',
                        '30',
                    ],
                    cwd=ROOT,
                    env=dict(
                        os.environ,
                        PYTHONPATH=str(ROOT / 'src'),
                        SDL_VIDEODRIVER='dummy',
                        SDL_RENDER_DRIVER='software',
                    ),
                    capture_output=True,
                    timeout=45,
                    check=False,
                )
                (work / 'background-job-handoff-launch.log').write_bytes(
                    launch.stdout + launch.stderr
                )
                self.assertEqual(launch.returncode, 0, 'Inspect background-job-handoff-launch.log')
                queued = registry.list_persisted_jobs(enrolled['uid'], enrolled['apk_sha256'])
                handoff = next(item for item in queued if item['id'] == 1)
                self.assertEqual(
                    handoff['definition']['component'], enrolled['package'] + '.BackgroundJob'
                )
                with ServiceBroker(registry) as broker:
                    handoff_worker = broker.start_job(
                        enrolled['uid'], enrolled['apk_sha256'], handoff['definition'], timeout=20
                    )
                    self.assertFalse(handoff_worker.wait(30))
                    self.assertEqual(handoff_worker.process.returncode, 0)
                    self.assertIn(
                        'BACKGROUND_APK_JOB_FINISHED',
                        (handoff_worker.evidence / 'stdout.log').read_text(),
                    )
                    self.assertIn(
                        'BACKGROUND_APK_JOB_DESTROYED',
                        (handoff_worker.evidence / 'stdout.log').read_text(),
                    )
                    registry.cancel_persisted_job(
                        enrolled['uid'],
                        enrolled['apk_sha256'],
                        1,
                        expected_revision=handoff['revision'],
                    )
                    print(
                        'BACKGROUND_APK_REGISTERED_JOB_HANDOFF_OK',
                        handoff_worker.evidence.relative_to(ROOT),
                        flush=True,
                    )
                    for action in (
                        'complete',
                        'retry',
                        'deadline-true',
                        'deadline-false',
                        'stop-true',
                        'stop-false',
                        'stop-hang',
                        'bad-stop',
                        'bad-duplicate',
                        'bad-attachment',
                        'throw',
                        'destroy',
                        'hang',
                    ):
                        definition = dict(
                            version=1,
                            id=17,
                            component=enrolled['package'] + '.HeadlessJob',
                            persisted=True,
                            minimum_latency_ms=0,
                            deadline_ms=0 if action.startswith('deadline-') else None,
                            interval_ms=0,
                            flex_ms=0,
                            initial_backoff_ms=30000,
                            backoff_policy=1,
                            extras={
                                'action': dict(
                                    type='string',
                                    value='hang' if action.startswith('bad-') else action,
                                )
                            },
                        )
                        worker = broker.start_job(
                            enrolled['uid'],
                            enrolled['apk_sha256'],
                            definition,
                            timeout=3 if action == 'hang' else 30,
                            deadline_expired=action == 'deadline-true',
                        )
                        started_stop = time.monotonic()
                        if action.startswith('stop-'):
                            worker.stop_for_constraints()
                        elif action.startswith('bad-'):
                            self.assertTrue(worker.definition_sent.wait(5))
                            with worker.endpoint.dup() as channel:
                                if action == 'bad-duplicate':
                                    write_payload(channel, b'{"stop_reason":9,"stop_reason":9}')
                                elif action == 'bad-attachment':
                                    with tempfile.TemporaryFile() as attached:
                                        write_packet(
                                            channel, {'stop_reason': 9}, [attached.fileno()]
                                        )
                                else:
                                    write_packet(channel, {'stop_reason': 8})
                        if action in (
                            'complete',
                            'retry',
                            'deadline-true',
                            'deadline-false',
                            'stop-true',
                            'stop-false',
                        ):
                            self.assertIs(worker.wait(40), action in ('retry', 'stop-true'))
                            self.assertEqual(
                                worker.stop_reason, 9 if action.startswith('stop-') else 0
                            )
                            self.assertEqual(worker.process.returncode, 0)
                        else:
                            with self.assertRaisesRegex(RuntimeError, 'Job execution failed'):
                                worker.wait(40)
                        if action == 'stop-hang':
                            self.assertLess(time.monotonic() - started_stop, 20)
                            self.assertTrue(worker.cancelled.is_set())
                            self.assertIn(
                                'APK_STORAGE_STOP_HANG_ENTERED',
                                (worker.evidence / 'stdout.log').read_text(),
                            )
                        if action.startswith('bad-'):
                            self.assertIn(
                                'Invalid job stop message',
                                (worker.evidence / 'stderr.log').read_text(),
                            )
                        self.assertTrue(worker.terminal_cleanup_complete)
                        self.assertTrue(worker.cleanup_complete)
                        self.assertIsNotNone(worker.process.poll())
                        self.assertNotIn(worker, broker.processes)
                        # Keep only public test action/path/status evidence outside temporary signing data.
                        print(
                            'JOB_PROCESS_CASE',
                            action,
                            worker.process.returncode,
                            worker.evidence.relative_to(ROOT),
                            flush=True,
                        )
                    denied = dict(definition, component='test.other.Worker')
                    with self.assertRaises((PermissionError, ValueError)):
                        broker.start_job(enrolled['uid'], enrolled['apk_sha256'], denied)
                    active = broker.start_job(
                        enrolled['uid'], enrolled['apk_sha256'], definition, timeout=30
                    )
                    with self.assertRaises(TimeoutError):
                        active.wait(0)
                    with self.assertRaisesRegex(FormatError, 'generation is in use'):
                        with registry._generation_lock(enrolled['package'], exclusive=True):
                            pass
                    broker.quiesce_package(
                        enrolled['package'],
                        expected_sha256=enrolled['apk_sha256'],
                        expected_uid=enrolled['uid'],
                    )
                    with self.assertRaises(RuntimeError):
                        active.wait(10)
                    self.assertTrue(active.cleanup_complete)
                    self.assertIsNotNone(active.process.poll())
                    with registry._generation_lock(enrolled['package'], exclusive=True):
                        pass
                    with self.assertRaisesRegex(PermissionError, 'quiesced'):
                        broker.start_job(enrolled['uid'], enrolled['apk_sha256'], definition)
                    broker.resume_package(
                        enrolled['package'],
                        expected_sha256=enrolled['apk_sha256'],
                        expected_uid=enrolled['uid'],
                    )
                    for action in ('complete', 'retry', 'throw'):
                        scheduled = dict(
                            definition, extras={'action': dict(type='string', value=action)}
                        )
                        stored = registry.store_persisted_job(
                            enrolled['uid'], enrolled['apk_sha256'], scheduled
                        )
                        run = broker.dispatch_persisted_job(
                            enrolled['package'],
                            enrolled['uid'],
                            enrolled['apk_sha256'],
                            stored['id'],
                            stored['revision'],
                        )
                        self.assertIsNotNone(run)
                        # Observe the supervisor event without invoking the client
                        # wait API: durable state must already be finalized.
                        self.assertTrue(run.done.wait(40))
                        persisted_before_wait = PackageRegistry(registry.root).list_persisted_jobs(
                            enrolled['uid'], enrolled['apk_sha256']
                        )
                        if action == 'complete':
                            self.assertEqual(persisted_before_wait, [])
                        else:
                            self.assertEqual(persisted_before_wait[0]['attempts'], 1)
                        if action == 'throw':
                            with self.assertRaisesRegex(RuntimeError, 'claim transition recorded'):
                                run.wait(40)
                        else:
                            self.assertEqual(
                                run.wait(40), dict(applied=True, retry=action == 'retry')
                            )
                            self.assertEqual(
                                run.wait(0), dict(applied=True, retry=action == 'retry')
                            )
                        persisted = PackageRegistry(registry.root).list_persisted_jobs(
                            enrolled['uid'], enrolled['apk_sha256']
                        )
                        if action == 'complete':
                            self.assertEqual(persisted, [])
                        else:
                            self.assertEqual(persisted[0]['attempts'], 1)
                            self.assertNotEqual(persisted[0]['revision'], stored['revision'])
                            self.assertGreater(
                                persisted[0]['next_run_at_ms'], stored['next_run_at_ms']
                            )
                        print(
                            'PERSISTED_JOB_DISPATCH',
                            action,
                            run.worker.evidence.relative_to(ROOT),
                            flush=True,
                        )
                    dispatched = queue.Queue(maxsize=4)
                    dispatch = broker.dispatch_persisted_job

                    def observe_dispatch(*args, **kwargs):
                        run = dispatch(*args, **kwargs)
                        if run is not None:
                            dispatched.put_nowait(run)
                        return run

                    with (
                        patch.object(
                            broker, 'dispatch_persisted_job', side_effect=observe_dispatch
                        ),
                        broker.open_session(
                            enrolled['package'], enrolled['apk_sha256']
                        ) as submitter,
                    ):

                        def job_request(request):
                            write_packet(submitter, request)
                            reply, descriptors = read_packet(submitter, 10, max_descriptors=0)
                            self.assertEqual(descriptors, [])
                            self.assertTrue(reply['ok'], reply)
                            return reply

                        job_request(
                            dict(
                                op='schedule_job',
                                definition=dict(
                                    definition,
                                    minimum_latency_ms=250,
                                    extras={'action': dict(type='string', value='complete')},
                                ),
                            )
                        )
                        scheduler = broker.start_job_scheduler()
                        automatic = dispatched.get(timeout=40)
                        self.assertTrue(automatic.done.wait(40))
                        self.assertIsNone(automatic.error)
                        self.assertEqual(
                            PackageRegistry(registry.root).list_persisted_jobs(
                                enrolled['uid'], enrolled['apk_sha256']
                            ),
                            [],
                        )
                        print(
                            'PERSISTED_JOB_AUTOMATIC_COMPLETE',
                            automatic.worker.evidence.relative_to(ROOT),
                            flush=True,
                        )
                        # Bootstrap one signed-APK worker; that APK uses only the public
                        # JobScheduler API to submit the automatically executed child.
                        for submission_action in ('submit', 'submit-transient'):
                            submitter_worker = broker.start_job(
                                enrolled['uid'],
                                enrolled['apk_sha256'],
                                dict(
                                    definition,
                                    extras={'action': dict(type='string', value=submission_action)},
                                ),
                                timeout=30,
                            )
                            self.assertFalse(submitter_worker.wait(40))
                            self.assertTrue(submitter_worker.terminal_cleanup_complete)
                            self.assertIn(
                                'APK_PUBLIC_JOB_SUBMITTED',
                                (submitter_worker.evidence / 'stdout.log').read_text(),
                            )
                            public_job = dispatched.get(timeout=40)
                            self.assertTrue(public_job.done.wait(40))
                            self.assertIsNone(public_job.error)
                            self.assertEqual(public_job.claim['id'], 18)
                            self.assertEqual(public_job.wait(0), dict(applied=True, retry=False))
                            self.assertTrue(public_job.worker.terminal_cleanup_complete)
                            self.assertEqual(public_job.worker.process.returncode, 0)
                            self.assertIn(
                                'APK_PUBLIC_JOB_EXECUTED',
                                (public_job.worker.evidence / 'stdout.log').read_text(),
                            )
                            self.assertEqual(
                                registry.list_persisted_jobs(
                                    enrolled['uid'], enrolled['apk_sha256']
                                ),
                                [],
                            )
                            print(
                                'APK_PUBLIC_JOB_AUTOMATIC_COMPLETE',
                                submitter_worker.evidence.relative_to(ROOT),
                                public_job.worker.evidence.relative_to(ROOT),
                                flush=True,
                            )
                        # Public APK admission retains storage requirements while
                        # real dispatch observes controlled low/healthy host state.
                        from shiraz.job_constraints import StorageObservation

                        healthy = StorageObservation(registry.root, 100, 50, 5)
                        low = StorageObservation(registry.root, 100, 5, 5)
                        with patch(
                            'shiraz.job_constraints.observe_storage', return_value=None
                        ) as storage:
                            source = broker.start_job(
                                enrolled['uid'],
                                enrolled['apk_sha256'],
                                dict(
                                    definition,
                                    extras={'action': dict(type='string', value='submit-storage')},
                                ),
                            )
                            self.assertFalse(source.wait(40))
                            saved = PackageRegistry(registry.root).list_persisted_jobs(
                                enrolled['uid'], enrolled['apk_sha256']
                            )
                            self.assertEqual(len(saved), 1)
                            self.assertTrue(saved[0]['definition']['requires_storage_not_low'])
                            with self.assertRaises(queue.Empty):
                                dispatched.get(timeout=1.5)
                            self.assertGreater(storage.call_count, 0)
                            with registry.connect() as db:
                                self.assertEqual(
                                    db.execute(
                                        'SELECT count(*) FROM persisted_job_claims'
                                    ).fetchone()[0],
                                    0,
                                )
                            storage.return_value = low
                            with self.assertRaises(queue.Empty):
                                dispatched.get(timeout=1.5)
                            storage.return_value = healthy
                            run = dispatched.get(timeout=40)
                            self.assertEqual(run.wait(40), dict(applied=True, retry=False))
                            self.assertIn(
                                'APK_PUBLIC_JOB_EXECUTED',
                                (run.worker.evidence / 'stdout.log').read_text(),
                            )
                            self.assertEqual(
                                registry.list_persisted_jobs(
                                    enrolled['uid'], enrolled['apk_sha256']
                                ),
                                [],
                            )
                            print(
                                'APK_PUBLIC_STORAGE_GATED_AND_EXECUTED',
                                run.worker.evidence.relative_to(ROOT),
                                flush=True,
                            )
                        with patch(
                            'shiraz.job_constraints.observe_storage', return_value=healthy
                        ) as storage:
                            source = broker.start_job(
                                enrolled['uid'],
                                enrolled['apk_sha256'],
                                dict(
                                    definition,
                                    extras={
                                        'action': dict(type='string', value='submit-storage-retry')
                                    },
                                ),
                            )
                            self.assertFalse(source.wait(40))
                            first = dispatched.get(timeout=40)
                            storage.return_value = low
                            self.assertEqual(first.wait(40), dict(applied=True, retry=True))
                            self.assertEqual(first.worker.stop_reason, 9)
                            self.assertFalse(first.worker.cancelled.is_set())
                            first_output = (first.worker.evidence / 'stdout.log').read_text()
                            self.assertIn('APK_STORAGE_RETRY_FIRST', first_output)
                            self.assertIn('APK_STORAGE_STOP_CALLBACK_OK', first_output)
                            self.assertTrue(first.worker.terminal_cleanup_complete)
                            retried = registry.list_persisted_jobs(
                                enrolled['uid'], enrolled['apk_sha256']
                            )[0]
                            self.assertEqual(retried['attempts'], 1)
                            self.assertEqual(retried['definition'], first.claim['definition'])
                            self.assertNotEqual(retried['revision'], first.claim['revision'])
                            # Let real backoff expire while storage remains low;
                            # the normal scheduler must still leave it unclaimed.
                            wait_seconds = (
                                max(0, retried['next_run_at_ms'] / 1000 - time.time()) + 1.5
                            )
                            with self.assertRaises(queue.Empty):
                                dispatched.get(timeout=wait_seconds)
                            self.assertEqual(
                                registry.list_persisted_jobs(
                                    enrolled['uid'], enrolled['apk_sha256']
                                )[0]['attempts'],
                                1,
                            )
                            storage.return_value = healthy
                            second = dispatched.get(timeout=40)
                            self.assertEqual(second.wait(40), dict(applied=True, retry=False))
                            self.assertIn(
                                'APK_STORAGE_RETRY_COMPLETE',
                                (second.worker.evidence / 'stdout.log').read_text(),
                            )
                            self.assertNotEqual(first.worker.evidence, second.worker.evidence)
                            self.assertEqual(
                                registry.list_persisted_jobs(
                                    enrolled['uid'], enrolled['apk_sha256']
                                ),
                                [],
                            )
                            print(
                                'APK_PUBLIC_STORAGE_STOP_RETRY_RECOVERY_OK',
                                first.worker.evidence.relative_to(ROOT),
                                second.worker.evidence.relative_to(ROOT),
                                flush=True,
                            )
                        with patch(
                            'shiraz.job_constraints.observe_storage', return_value=low
                        ) as storage:
                            source = broker.start_job(
                                enrolled['uid'],
                                enrolled['apk_sha256'],
                                dict(
                                    definition,
                                    extras={
                                        'action': dict(
                                            type='string', value='submit-storage-deadline'
                                        )
                                    },
                                ),
                            )
                            self.assertFalse(source.wait(40))
                            first = dispatched.get(timeout=40)
                            self.assertTrue(first.deadline_expired)
                            self.assertEqual(first.claim['definition']['deadline_ms'], 2000)
                            self.assertTrue(first.claim['definition']['requires_storage_not_low'])
                            self.assertGreaterEqual(
                                time.time() * 1000, first.claim['scheduled_at_ms'] + 2000
                            )
                            self.assertEqual(first.wait(40), dict(applied=True, retry=True))
                            self.assertEqual(first.worker.stop_reason, 0)
                            self.assertIn(
                                'APK_STORAGE_DEADLINE_FIRST_EXPIRED',
                                (first.worker.evidence / 'stdout.log').read_text(),
                            )
                            retried = registry.list_persisted_jobs(
                                enrolled['uid'], enrolled['apk_sha256']
                            )[0]
                            self.assertEqual(retried['attempts'], 1)
                            self.assertEqual(retried['definition'], first.claim['definition'])
                            # The original deadline is long past, but cannot
                            # override low storage on this rescheduled attempt.
                            with self.assertRaises(queue.Empty):
                                dispatched.get(
                                    timeout=max(0, retried['next_run_at_ms'] / 1000 - time.time())
                                    + 1.5
                                )
                            storage.return_value = healthy
                            second = dispatched.get(timeout=40)
                            self.assertFalse(second.deadline_expired)
                            self.assertEqual(second.wait(40), dict(applied=True, retry=False))
                            self.assertIn(
                                'APK_STORAGE_DEADLINE_RETRY_NOT_EXPIRED',
                                (second.worker.evidence / 'stdout.log').read_text(),
                            )
                            self.assertEqual(
                                registry.list_persisted_jobs(
                                    enrolled['uid'], enrolled['apk_sha256']
                                ),
                                [],
                            )
                            print(
                                'APK_PUBLIC_STORAGE_DEADLINE_RETRY_OK',
                                first.worker.evidence.relative_to(ROOT),
                                second.worker.evidence.relative_to(ROOT),
                                flush=True,
                            )
                        job_request(
                            dict(
                                op='schedule_job',
                                definition=dict(
                                    definition, extras={'action': dict(type='string', value='hang')}
                                ),
                            )
                        )
                        canceled = dispatched.get(timeout=40)
                        self.assertTrue(job_request(dict(op='cancel_all_jobs'))['removed'])
                        self.assertTrue(canceled.done.wait(40))
                        self.assertTrue(canceled.worker.terminal_cleanup_complete)
                        self.assertEqual(job_request(dict(op='list_job_ids'))['ids'], [])
                        self.assertIsNone(
                            job_request(dict(op='get_job', id=definition['id']))['definition']
                        )
                        print('PERSISTED_JOB_AUTHENTICATED_CANCEL', flush=True)
                        job_request(
                            dict(
                                op='schedule_job',
                                definition=dict(
                                    definition, extras={'action': dict(type='string', value='hang')}
                                ),
                            )
                        )
                        remote_canceled = dispatched.get(timeout=40)
                        # An independent host process has no handle to this worker.
                        subprocess.run(
                            [
                                sys.executable,
                                '-c',
                                'import sys; from shiraz.package_registry import PackageRegistry; '
                                'r=PackageRegistry(sys.argv[1]); '
                                'assert r.cancel_all_shared_jobs(int(sys.argv[2]),sys.argv[3])[0]',
                                str(registry.root),
                                str(enrolled['uid']),
                                enrolled['apk_sha256'],
                            ],
                            check=True,
                            capture_output=True,
                            timeout=15,
                        )
                        self.assertTrue(remote_canceled.done.wait(15))
                        self.assertTrue(remote_canceled.worker.terminal_cleanup_complete)
                        self.assertFalse(remote_canceled.result['applied'])
                        self.assertIsNotNone(remote_canceled.worker.process.poll())
                        self.assertEqual(
                            registry.list_persisted_jobs(enrolled['uid'], enrolled['apk_sha256']),
                            [],
                        )
                        print(
                            'JOB_OTHER_HOST_CANCEL_TERMINATED',
                            remote_canceled.worker.evidence.relative_to(ROOT),
                            flush=True,
                        )

                        registry.store_persisted_job(
                            enrolled['uid'],
                            enrolled['apk_sha256'],
                            dict(definition, extras={'action': dict(type='string', value='hang')}),
                        )
                        stopping = dispatched.get(timeout=40)
                        broker.close()
                        self.assertFalse(scheduler.thread.is_alive())
                        self.assertEqual(list(scheduler.failures), [])
                        self.assertTrue(stopping.done.is_set())
                        self.assertTrue(stopping.worker.terminal_cleanup_complete)
                        persisted = PackageRegistry(registry.root).list_persisted_jobs(
                            enrolled['uid'], enrolled['apk_sha256']
                        )
                        self.assertEqual(persisted[0]['attempts'], 1)
                        self.assertEqual(broker.job_dispatches, set())
                        print('PERSISTED_JOB_AUTOMATIC_SHUTDOWN_FINALIZED', flush=True)
                self.probe_storage_job_restart(registry, enrolled)
                self.probe_idle_job_host(registry, enrolled, definition)
                self.probe_job_service_host(registry, enrolled, definition)
                self.probe_job_service_host(registry, enrolled, definition, supervised=True)
                self.probe_crashed_job_host(registry, enrolled, definition)
                self.probe_crashed_job_host(registry, enrolled, definition, kill_worker=True)
            if (
                os.environ.get('SHIRAZ_TEST_AUTHENTICATOR') == '1'
                or os.environ.get('SHIRAZ_TEST_EXPLICIT_ACTIVITY') == '1'
            ):
                authenticator = temp / 'authenticator.apk'
                subprocess.run(
                    [
                        'jarsigner',
                        '-keystore',
                        str(store),
                        '-storepass:file',
                        str(password),
                        '-signedjar',
                        str(authenticator),
                        str(ROOT / 'build/macos-apk/authenticator.apk'),
                        'fixture',
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                identity = registry.enroll(authenticator)
                with registry.connect() as db:
                    row = db.execute(
                        'SELECT manifest FROM packages WHERE package=?', (identity['package'],)
                    ).fetchone()
                descriptor = json.loads(row['manifest'])['authenticators']
                self.assertEqual(len(descriptor), 2)
                descriptor.sort(key=lambda item: item["type"])
                self.assertEqual(descriptor[1]["type"], "org.shiraz.fixture.custom")
                self.assertIs(descriptor[1]["custom_tokens"], True)
                self.assertEqual(descriptor[0]['type'], 'org.shiraz.fixture.account')
                self.assertEqual(
                    descriptor[0]['component'],
                    'org.shiraz.authenticatorfixture.AuthenticatorService',
                )
                self.assertIs(descriptor[0]['custom_tokens'], False)
                self.assertGreater(descriptor[0]['label'], 0)
                self.assertEqual(descriptor[0]['icon'], 0)
                with zipfile.ZipFile(authenticator) as archive:
                    self.assertFalse(
                        archive.read('res/xml/authenticator.xml').lstrip().startswith(b'<')
                    )
                from shiraz.service_broker import ServiceBroker, read_frame, write_frame

                with (
                    ServiceBroker(registry) as broker,
                    broker.open_session(enrolled['package'], enrolled['apk_sha256']) as channel,
                ):
                    write_frame(channel, {'op': 'list_authenticators'})
                    listed = read_frame(channel, 3)
                    self.assertTrue(listed['ok'])
                    self.assertEqual(len(listed['authenticators']), 2)
                    listed['authenticators'].sort(key=lambda item: item['type'])
                    self.assertEqual(
                        listed['authenticators'][0]['apk_sha256'], identity['apk_sha256']
                    )
                    self.assertEqual(listed['authenticators'][0]['uid'], identity['uid'])
                    self.assertEqual(listed['authenticators'][0]['type'], descriptor[0]['type'])
                account_markers = (
                    (
                        b'AUTHENTICATOR_SHARED_ACCOUNT_CREATED',
                        b'AUTHENTICATOR_SHARED_ACCOUNT_RESTORED_AND_REMOVED',
                    )
                    if os.environ.get('SHIRAZ_TEST_AUTHENTICATOR') == '1'
                    else ()
                )
                for attempt, marker in enumerate(account_markers):
                    launch = subprocess.run(
                        [
                            sys.executable,
                            '-m',
                            'shiraz',
                            'run',
                            str(authenticator),
                            '--registry',
                            str(registry.root),
                            '--timeout',
                            '60',
                        ],
                        cwd=ROOT,
                        env=dict(
                            os.environ,
                            PYTHONPATH=str(ROOT / 'src'),
                            SDL_VIDEODRIVER='dummy',
                            SDL_RENDER_DRIVER='software',
                        ),
                        capture_output=True,
                        timeout=90,
                        check=False,
                    )
                    self.assertEqual(
                        launch.returncode,
                        0,
                        'Inspect build/apk-verifier-tests/authenticator-api.log',
                    )
                    session = json.loads(launch.stdout.split(b'\n', 1)[1])
                    self.assertEqual(len(session['apps']), 1)
                    event = session['apps'][0]
                    self.assertEqual(event['package'], identity['package'])
                    self.assertEqual(event['exit_status'], 0)
                    output = Path(event['stdout']).read_bytes() + Path(event['stderr']).read_bytes()
                    (work / ('authenticator-api-' + str(attempt) + '.log')).write_bytes(output)
                    self.assertIn(b'AUTHENTICATOR_ACCOUNT_MANAGER_DISCOVERY_OK', output)
                    self.assertIn(b'AUTHENTICATOR_SIGNED_SERVICE_REQUEST_OK', output)
                    self.assertIn(b'AUTHENTICATOR_PUBLIC_ASYNC_OK', output)
                    self.assertIn(b'AUTHENTICATOR_PUBLIC_TOKEN_OK', output)
                    self.assertIn(b'AUTHENTICATOR_TOKEN_CACHE_OK', output)
                    self.assertIn(b'AUTHENTICATOR_DELAYED_TOKEN_BARRIERS_OK', output)
                    self.assertIn(b'AUTHENTICATOR_CUSTOM_TOKEN_CACHE_OK', output)
                    self.assertIn(b'AUTHENTICATOR_PUBLIC_ADD_ACCOUNT_OK', output)
                    for lifecycle_marker in (
                        b'AUTHENTICATOR_WORKER_DEATH_OK',
                        b'AUTHENTICATOR_DEADLINE_OK',
                        b'AUTHENTICATOR_CAPACITY_RECOVERY_OK',
                    ):
                        self.assertIn(lifecycle_marker, output)
                    self.assertIn(marker, output)
                if os.environ.get('SHIRAZ_TEST_EXPLICIT_ACTIVITY') == '1':
                    caller_apk = temp / 'activity-caller.apk'
                    subprocess.run(
                        [
                            'jarsigner',
                            '-keystore',
                            str(store),
                            '-storepass:file',
                            str(password),
                            '-signedjar',
                            str(caller_apk),
                            str(ROOT / 'build/macos-apk/activity-caller.apk'),
                            'fixture',
                        ],
                        check=True,
                        capture_output=True,
                        timeout=30,
                    )
                    caller_identity = registry.enroll(caller_apk)
                    self.probe_explicit_activity(registry, caller_identity, identity, work)
                    self.probe_account_ui(registry, caller_identity, identity, work)
                    self.probe_local_account_ui(registry, identity, work)
                    self.probe_account_listeners(registry, identity, work)
                    self.probe_account_listeners(registry, caller_identity, work)
                (work / 'authenticator-descriptor.json').write_text(
                    json.dumps(dict(identity=identity, descriptors=descriptor), indent=2) + '\n'
                )
            if os.environ.get('SHIRAZ_TEST_HOST_CONTROL') == '1':
                self.probe_host_control(registry, enrolled)
            if os.environ.get('SHIRAZ_TEST_HOST_SWIFT') == '1':
                update_manifest = temp / 'update-manifest.xml'
                update_manifest.write_text(
                    (ROOT / 'tests/background-apk/AndroidManifest.xml')
                    .read_text()
                    .replace('android:versionCode="1"', 'android:versionCode="2"')
                )
                update_unsigned = temp / 'update-unsigned.apk'
                subprocess.run(
                    [
                        str(ROOT / '.deps/android-sdk/aapt2'),
                        'link',
                        '-o',
                        str(update_unsigned),
                        '--manifest',
                        str(update_manifest),
                        '-I',
                        str(ROOT / '.deps/android-sdk/android.jar'),
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                with (
                    zipfile.ZipFile(unsigned) as source,
                    zipfile.ZipFile(update_unsigned, 'a') as target,
                ):
                    target.writestr(
                        'classes.dex', source.read('classes.dex'), compress_type=zipfile.ZIP_STORED
                    )
                update_signed = temp / 'update-signed.apk'
                subprocess.run(
                    [
                        'java',
                        '-cp',
                        str(temp) + ':' + str(JAR),
                        'SignApkFixture',
                        str(update_unsigned),
                        str(update_signed),
                        str(store),
                        str(password),
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                self.assertEqual(
                    verify_signatures(update_signed)['verified_signers_sha256'],
                    result['verified_signers_sha256'],
                )
                env = dict(
                    os.environ,
                    SHIRAZ_TEST_SHARED_HOST_REGISTRY=str(registry.root),
                    SHIRAZ_TEST_SHARED_HOST_PACKAGE=enrolled['package'],
                    SHIRAZ_TEST_SHARED_HOST_SHA256=enrolled['apk_sha256'],
                    SHIRAZ_TEST_SHARED_HOST_UPDATE_APK=str(update_signed),
                    SDL_VIDEODRIVER='dummy',
                    SDL_RENDER_DRIVER='software',
                )
                with (work / 'swift-host-client.log').open('wb') as out:
                    swift = subprocess.run(
                        ['make', 'launcher-test'],
                        cwd=ROOT,
                        env=env,
                        stdout=out,
                        stderr=subprocess.STDOUT,
                        timeout=180,
                        check=False,
                    )
                self.assertEqual(
                    swift.returncode, 0, 'Inspect build/apk-verifier-tests/swift-host-client.log'
                )
            with self.assertRaisesRegex(FormatError, 'changed since enrollment was prepared'):
                registry.enroll(signed, expected_current='absent')
            command = [
                sys.executable,
                '-m',
                'shiraz',
                'register-package',
                str(signed),
                '--registry',
                str(registry.root),
                '--expected-package',
                enrolled['package'],
                '--expected-current',
                enrolled['apk_sha256'],
                '--expected-sha256',
                enrolled['apk_sha256'],
            ]
            cli_env = dict(os.environ, PYTHONPATH=str(ROOT / 'src'))
            accepted = subprocess.run(
                command, env=cli_env, capture_output=True, timeout=90, check=False
            )
            self.assertEqual(accepted.returncode, 0, accepted.stderr.decode())
            self.assertEqual(json.loads(accepted.stdout), enrolled)
            denied = subprocess.run(
                command[:-1] + ['0' * 64], env=cli_env, capture_output=True, timeout=90, check=False
            )
            self.assertNotEqual(denied.returncode, 0)
            self.assertIn(b'digest differs from enrollment request', denied.stderr)
            self.assertEqual(
                registry.identity(enrolled['package'])['sha256'], enrolled['apk_sha256']
            )
            self.assertEqual(registry.enroll(signed), enrolled)
            with ThreadPoolExecutor(max_workers=2) as pool:
                self.assertEqual(
                    list(pool.map(registry.enroll, [signed, signed])), [enrolled, enrolled]
                )
            self.assertEqual(enrolled['apk_sha256'], result['apk_sha256'])
            with self.assertRaisesRegex(FormatError, 'higher version'):
                registry.enroll(modern)
            with self.assertRaisesRegex(FormatError, 'verification failed'):
                registry.enroll(unsigned)
            if os.environ.get('SHIRAZ_TEST_REGISTERED_LAUNCH') == '1':
                env = dict(
                    os.environ,
                    PYTHONPATH=str(ROOT / 'src'),
                    SDL_VIDEODRIVER='dummy',
                    SDL_RENDER_DRIVER='software',
                )
                for key in list(env):
                    if key.startswith('SHIRAZ_'):
                        del env[key]
                launch = subprocess.run(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'run',
                        str(signed),
                        '--registry',
                        str(registry.root),
                        '--timeout',
                        '30',
                    ],
                    env=env,
                    cwd=ROOT,
                    capture_output=True,
                    timeout=60,
                    check=False,
                )
                evidence = ROOT / 'build/registered-launch-smoke'
                evidence.mkdir(parents=True, exist_ok=True)
                output = (launch.stdout + launch.stderr).decode('utf-8', errors='replace')
                (evidence / 'run.log').write_text(output)
                self.assertEqual(launch.returncode, 0, f'Inspect {evidence}')
                markers = (
                    'SHIRAZ_BROKER_CHANNEL_ATTACHED',
                    'BACKGROUND_APK_ACTIVITY_DESTROYED',
                    'SHIRAZ_BACKGROUND_ENTERED',
                    'BACKGROUND_APK_WORK_AFTER_ACTIVITY',
                    'BACKGROUND_APK_SERVICE_DESTROYED',
                    'BACKGROUND_APK_JOB_FINISHED',
                    'BACKGROUND_APK_JOB_DESTROYED',
                    'SHIRAZ_RUNTIME_CLEANUP_COMPLETE',
                )
                positions = []
                for marker in markers:
                    self.assertEqual(output.count(marker), 1, marker)
                    positions.append(output.index(marker))
                self.assertEqual(
                    positions, sorted(positions), 'Registered background lifecycle order'
                )
                self.assertIn(f"BACKGROUND_APK_UID={enrolled['uid']}", output)
                denied = subprocess.run(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'run',
                        str(modern),
                        '--registry',
                        str(registry.root),
                        '--timeout',
                        '30',
                    ],
                    env=env,
                    cwd=ROOT,
                    capture_output=True,
                    timeout=60,
                    check=False,
                )
                self.assertNotEqual(denied.returncode, 0)
                self.assertIn(b'Launch APK does not match enrolled package', denied.stderr)
                self.assertNotIn(b'SHIRAZ_BROKER_CHANNEL_ATTACHED', denied.stdout)
            if (
                os.environ.get('SHIRAZ_TEST_REGISTERED_SERVICE') == '1'
                or os.environ.get('SHIRAZ_TEST_BROKER_BIND') == '1'
                or os.environ.get('SHIRAZ_TEST_CONTEXT_BIND') == '1'
                or os.environ.get('SHIRAZ_TEST_SHARED_SERVICE') == '1'
                or os.environ.get('SHIRAZ_TEST_HOST_SESSION') == '1'
            ):
                service_apk = temp / 'service.apk'
                subprocess.run(
                    [
                        'jarsigner',
                        '-keystore',
                        str(store),
                        '-storepass:file',
                        str(password),
                        '-signedjar',
                        str(service_apk),
                        str(ROOT / 'build/macos-apk/service-only.apk'),
                        'fixture',
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
                identity = registry.enroll(service_apk)
                self.probe_registered_service(registry, identity)
                if os.environ.get('SHIRAZ_TEST_BROKER_BIND') == '1':
                    probe = subprocess.run(
                        [
                            sys.executable,
                            str(ROOT / 'tests/broker_bind_probe.py'),
                            str(registry.root),
                            enrolled['package'],
                            enrolled['apk_sha256'],
                        ],
                        env=dict(os.environ, PYTHONPATH=str(ROOT / 'src')),
                        capture_output=True,
                        timeout=60,
                        check=False,
                    )
                    (work / 'bind-probe.log').write_bytes(probe.stdout + probe.stderr)
                    self.assertEqual(
                        probe.returncode, 0, 'Inspect build/apk-verifier-tests/bind-probe.log'
                    )
                    self.assertIn(b'SIGNED_TARGET_ART_BINDER_AND_TEARDOWN_OK', probe.stdout)
                if (
                    os.environ.get('SHIRAZ_TEST_CONTEXT_BIND') == '1'
                    or os.environ.get('SHIRAZ_TEST_SHARED_SERVICE') == '1'
                ):
                    subprocess.run(
                        [
                            'keytool',
                            '-genkeypair',
                            '-alias',
                            'other',
                            '-keyalg',
                            'RSA',
                            '-keysize',
                            '2048',
                            '-validity',
                            '2',
                            '-dname',
                            'CN=Other Shiraz test signer',
                            '-keystore',
                            str(store),
                            '-storepass:file',
                            str(password),
                        ],
                        check=True,
                        capture_output=True,
                        timeout=30,
                    )
                    caller_apk = temp / 'caller.apk'
                    subprocess.run(
                        [
                            'jarsigner',
                            '-keystore',
                            str(store),
                            '-storepass:file',
                            str(password),
                            '-signedjar',
                            str(caller_apk),
                            str(ROOT / 'build/macos-apk/broker-caller.apk'),
                            'other',
                        ],
                        check=True,
                        capture_output=True,
                        timeout=30,
                    )
                    caller_identity = registry.enroll(caller_apk)
                    authorized = registry.authorize_launcher(
                        caller_identity['uid'],
                        enrolled['package'],
                        caller_sha256=caller_identity['apk_sha256'],
                        target_sha256=enrolled['apk_sha256'],
                    )
                    self.assertEqual(
                        authorized['component'], 'org.shiraz.backgroundfixture.BackgroundActivity'
                    )
                    launch = subprocess.run(
                        [
                            sys.executable,
                            '-m',
                            'shiraz',
                            'run',
                            str(caller_apk),
                            '--registry',
                            str(registry.root),
                            '--timeout',
                            '30',
                        ],
                        env=dict(
                            os.environ,
                            PYTHONPATH=str(ROOT / 'src'),
                            SDL_VIDEODRIVER='dummy',
                            SDL_RENDER_DRIVER='software',
                            SHIRAZ_PACKAGE_UID='19999',
                            SHIRAZ_SERVICE_CALLER_UID='19998',
                        ),
                        cwd=ROOT,
                        capture_output=True,
                        timeout=45,
                        check=False,
                    )
                    evidence = ROOT / 'build/context-broker-smoke'
                    evidence.mkdir(parents=True, exist_ok=True)
                    output = launch.stdout + launch.stderr
                    (evidence / 'run.log').write_bytes(output)
                    self.assertEqual(
                        launch.returncode, 0, 'Inspect build/context-broker-smoke/run.log'
                    )
                    session = json.loads(launch.stdout.split(b'\n', 1)[1])
                    self.assertEqual(len(session['apps']), 1)
                    for event in session['apps']:
                        self.assertEqual(event['exit_status'], 0)
                        output += (
                            Path(event['stdout']).read_bytes() + Path(event['stderr']).read_bytes()
                        )
                    (evidence / 'run.log').write_bytes(output)
                    for marker in (
                        b'CONTEXT_BROKER_SIGNATURES_OK',
                        b'CONTEXT_BROKER_IDENTITIES_OK',
                        b'CONTEXT_BROKER_PRIVATE_DENIED',
                        b'CONTEXT_BROKER_CONNECTED_1',
                        b'CONTEXT_BROKER_CONNECTED_2',
                        b'CONTEXT_BROKER_UNBOUND',
                        b'CONTEXT_BROKER_CALLER_DESTROYED',
                    ):
                        self.assertIn(marker, output)
                    self.assertIn(
                        ('CONTEXT_BROKER_CALLER_UID=' + str(caller_identity['uid'])).encode(),
                        output,
                    )
                    self.assertIn(b'CONTEXT_PACKAGE_ACTION_BIND_OK', output)
                    self.assertIn(b'REGISTERED_SERVICE_QUERY_OK', output)
                    self.assertIn(b'REGISTERED_PACKAGE_ENUMERATION_OK', output)
                    self.assertIn(b'REGISTERED_GLOBAL_SERVICE_QUERY_OK', output)
                    self.assertIn(b'REGISTERED_LAUNCH_INTENT_OK', output)
                    self.assertIn(
                        ('CONTEXT_BROKER_TARGET_UID=' + str(identity['uid'])).encode(), output
                    )
                    if os.environ.get('SHIRAZ_TEST_SHARED_SERVICE') == '1':
                        probe = subprocess.run(
                            [
                                sys.executable,
                                str(ROOT / 'tests/shared_service_probe.py'),
                                str(registry.root),
                                enrolled['package'],
                                caller_identity['package'],
                            ],
                            env=dict(os.environ, PYTHONPATH=str(ROOT / 'src')),
                            capture_output=True,
                            timeout=90,
                            check=False,
                        )
                        (work / 'shared-probe.log').write_bytes(probe.stdout + probe.stderr)
                        self.assertEqual(
                            probe.returncode, 0, 'Inspect build/apk-verifier-tests/shared-probe.log'
                        )
                        self.assertIn(b'SHARED_SERVICE_TWO_CALLERS_OK', probe.stdout)
                        self.assertIn(
                            b'SHARED_SERVICE_RETIREMENT_REVOKES_LIVE_BINDING_OK', probe.stdout
                        )
            if os.environ.get('SHIRAZ_TEST_HOST_SESSION') == '1':
                packages = []
                for fixture in ('session-one', 'session-two'):
                    apk = temp / (fixture + '.apk')
                    subprocess.run(
                        [
                            'jarsigner',
                            '-keystore',
                            str(store),
                            '-storepass:file',
                            str(password),
                            '-signedjar',
                            str(apk),
                            str(ROOT / ('build/macos-apk/' + fixture + '.apk')),
                            'fixture',
                        ],
                        check=True,
                        capture_output=True,
                        timeout=30,
                    )
                    packages.append(registry.enroll(apk)['package'])
                from receiver_process_probe import verify as verify_receivers

                receiver_evidence = verify_receivers(self, registry)
                (work / 'receiver-processes.json').write_text(
                    json.dumps(receiver_evidence, indent=2) + '\n'
                )
                launch = subprocess.run(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'run-session',
                        '--registry',
                        str(registry.root),
                        '--timeout',
                        '40',
                        'org.shiraz.servicefixture',
                        *packages,
                    ],
                    env=dict(
                        os.environ,
                        PYTHONPATH=str(ROOT / 'src'),
                        SDL_VIDEODRIVER='dummy',
                        SDL_RENDER_DRIVER='software',
                    ),
                    cwd=ROOT,
                    capture_output=True,
                    timeout=60,
                    check=False,
                )
                (work / 'host-session.log').write_bytes(launch.stdout + launch.stderr)
                self.assertEqual(
                    launch.returncode, 0, 'Inspect build/apk-verifier-tests/host-session.log'
                )
                session = json.loads(launch.stdout)
                results = []
                owner_marker = None
                for app in session['apps']:
                    self.assertEqual(app['exit_status'], 0)
                    lines = Path(app['stdout']).read_text().splitlines()
                    if app['package'] == 'org.shiraz.sessionone':
                        self.assertIn('SESSION_RECEIVER_BROADCAST_ENQUEUED', lines)
                        self.assertEqual(
                            sum(
                                line.startswith(
                                    'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/dynamic:'
                                )
                                for line in lines
                            ),
                            1,
                        )
                        self.assertEqual(
                            lines.count(
                                'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/org.shiraz.sessionfixture.ExportedReceiver'
                            ),
                            1,
                        )
                        self.assertNotIn(
                            'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/org.shiraz.sessionfixture.ManifestReceiver',
                            lines,
                        )
                        self.assertIn(
                            'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/org.shiraz.sessionfixture.LiveReceiver',
                            lines,
                        )
                        self.assertNotIn(
                            'SHIRAZ_RECEIVER_DELIVERY_FAILED', Path(app['stderr']).read_text()
                        )
                    if app['package'] == 'org.shiraz.sessiontwo':
                        self.assertEqual(
                            lines.count(
                                'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/org.shiraz.sessionfixture.ImplicitReceiver'
                            ),
                            1,
                        )
                        self.assertEqual(
                            lines.count(
                                'SHIRAZ_RECEIVER_DELIVERY_COMPLETE org.shiraz.sessiontwo/org.shiraz.sessionfixture.RemoteReceiver'
                            ),
                            2,
                        )
                        self.assertNotIn(
                            'SHIRAZ_RECEIVER_DELIVERY_FAILED', Path(app['stderr']).read_text()
                        )
                        self.assertEqual(
                            sum(
                                line.startswith('SESSION_LIVE_RECEIVER_FINISHED ') for line in lines
                            ),
                            1,
                        )
                        self.assertEqual(
                            sum(
                                line.startswith('SESSION_DYNAMIC_RECEIVER_FINISHED ')
                                for line in lines
                            ),
                            1,
                        )
                    if app['package'] == 'org.shiraz.servicefixture':
                        self.assertIn('SAME_PACKAGE_ACTIVITY_SERVICE_OK', lines)
                        owners = [
                            line.split()
                            for line in lines
                            if line.startswith('SAME_PACKAGE_STARTED_BOUND ')
                        ]
                        self.assertEqual(len(owners), 1)
                        owner_marker = owners[0][1:]
                        continue
                    markers = [
                        line.split() for line in lines if line.startswith('SESSION_SHARED_RESULT ')
                    ]
                    self.assertEqual(len(markers), 1, str(app))
                    marker = markers[0]
                    self.assertEqual(marker[1], app['package'])
                    self.assertEqual(int(marker[4]), registry.identity(app['package'])['uid'])
                    results.append(marker)
                self.assertEqual(len(results), 2)
                self.assertEqual(results[0][2:4], results[1][2:4])
                self.assertEqual(owner_marker, results[0][2:4])
                self.assertNotEqual(results[0][4], results[1][4])
                from shiraz.host_session import HostSession

                alias_identity = registry.identity(packages[1])
                alias_report = inspect_apk(
                    registry.launch_apk(packages[1], alias_identity['sha256'])
                )
                self.assertEqual(
                    alias_report['manifest']['launcher_activities'][0],
                    dict(
                        name='org.shiraz.sessiontwo.Launcher',
                        target='org.shiraz.sessionfixture.MainActivity',
                    ),
                )
                self.assertEqual(alias_report['manifest']['activities'][0]['exported'], 'false')
                self.assertIsNone(alias_report['manifest']['activity_aliases'][0]['permission'])

                # The first signed caller remains at the service barrier while
                # the host authenticates its token and admits the second caller.
                with patch.dict(os.environ, SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software'):
                    with HostSession(registry.root) as host:
                        host.start('org.shiraz.servicefixture')
                        first = host.start(packages[0])
                        requester = dict(package=packages[0], launch_id=host.launch_ids[first])
                        with self.assertRaisesRegex(PermissionError, 'no longer active'):
                            host.start(packages[1], requester=dict(requester, launch_id='0' * 32))
                        second = host.start(packages[1], requester=requester)
                        result = host.wait(timeout=40)
                        self.assertEqual([app['exit_status'] for app in result['apps']], [0, 0, 0])
                        for app in result['apps']:
                            marker = (
                                'SAME_PACKAGE_ACTIVITY_SERVICE_OK'
                                if app['package'] == 'org.shiraz.servicefixture'
                                else 'SESSION_SHARED_RESULT '
                            )
                            self.assertIn(marker, Path(app['stdout']).read_text())
                        first_output = next(
                            app['stdout'] for app in result['apps'] if app['package'] == packages[0]
                        )
                        self.assertIn(
                            'SESSION_RECEIVER_BROKER_RESOLUTION_OK', Path(first_output).read_text()
                        )
                        second_output = next(
                            app['stdout'] for app in result['apps'] if app['package'] == packages[1]
                        )
                        self.assertIn('SESSION_ALIAS_DISCOVERY_OK', Path(second_output).read_text())
                        self.assertIn('SESSION_ALIAS_LIFECYCLE_OK', Path(second_output).read_text())
                        self.assertIn(
                            'SESSION_MANIFEST_RECEIVER_OK', Path(second_output).read_text()
                        )
                        self.assertIn(
                            'SESSION_MANIFEST_ASYNC_AFTER_ACTIVITY_OK',
                            Path(second_output).read_text(),
                        )
                        self.assertIsNotNone(second.poll())
                        (work / 'authenticated-host-launch.json').write_text(
                            json.dumps(result, indent=2)
                        )

                # One caller cannot pass the two-caller barrier: enforce bounded
                # shutdown while its service transaction is still waiting.
                with patch.dict(os.environ, SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software'):
                    with HostSession(registry.root) as host:
                        process = host.start(packages[0])
                        with self.assertRaisesRegex(FormatError, 'Duplicate package'):
                            host.start(packages[0])
                        with self.assertRaisesRegex(FormatError, 'timed out'):
                            host.wait(timeout=2)
                        self.assertIsNotNone(process.poll())
                        self.assertFalse(host.broker.processes)
                        self.assertFalse(host.broker.sessions)
                        self.assertFalse(host.broker.targets)
                        with self.assertRaisesRegex(RuntimeError, 'closed'):
                            host.start(packages[1])
            tampered = temp / 'tampered.apk'
            with zipfile.ZipFile(signed) as source, zipfile.ZipFile(tampered, 'w') as target:
                for info in source.infolist():
                    data = source.read(info.filename)
                    if info.filename == 'classes.dex':
                        data = data[:-1] + bytes([data[-1] ^ 1])
                    target.writestr(info, data)
            with self.assertRaisesRegex(FormatError, 'verification failed'):
                verify_signatures(tampered)
            self.assertFalse(any((ROOT / 'build/apk-verifier').glob('*/input.apk')))

    def activity_sender(self, work):
        sys.path.insert(0, str(ROOT / 'scripts'))
        from art_smoke import HOST, JARS, NATIVE, environment
        from build_macos_fixture import append_aligned_dex

        directory = work / 'activity-sender'
        classes = directory / 'classes'
        classes.mkdir(parents=True, exist_ok=True)
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
                str(ROOT / 'tests/java/SignedActivityLaunchSender.java'),
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        env = environment(directory)
        dex = directory / 'classes.dex'
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
        jar = directory / 'sender.jar'
        with zipfile.ZipFile(jar, 'w'):
            pass
        append_aligned_dex(jar, dex)
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
            'SignedActivityLaunchSender',
        ]
        return command, env

    def probe_explicit_activity(self, registry, caller, target, work):
        from shiraz.host_session import HostSession

        sender_command, sender_env = self.activity_sender(work)
        with patch.dict(
            os.environ,
            SDL_VIDEODRIVER='dummy',
            SDL_RENDER_DRIVER='software',
            SHIRAZ_TEST_GUEST_ACTIVITY='1',
        ):
            completed = subprocess.run(
                [
                    sys.executable,
                    '-m',
                    'shiraz',
                    'run',
                    str(registry.launch_apk(caller['package'], caller['apk_sha256'])),
                    '--registry',
                    str(registry.root),
                    '--timeout',
                    '40',
                ],
                env=dict(os.environ, PYTHONPATH=str(ROOT / 'src')),
                cwd=ROOT,
                capture_output=True,
                timeout=50,
            )
            (work / 'guest-activity-cli.log').write_bytes(completed.stdout + completed.stderr)
            self.assertEqual(completed.returncode, 0, completed.stderr.decode(errors='replace'))
            result = json.loads(completed.stdout.split(b'\n', 1)[1])
            events = result['apps']
            self.assertEqual(len(events), 2, 'Guest launch must create a second activity process')
            output = b''
            for event in events:
                output += Path(event['stdout']).read_bytes() + Path(event['stderr']).read_bytes()
                self.assertEqual(event['exit_status'], 0)
            (work / 'guest-activity-handoff.log').write_bytes(output)
            self.assertIn(b'GUEST_ACTIVITY_HANDOFF_OK', output)
            self.assertIn(b'GUEST_ACTIVITY_RECEIVED_OK', output)
        with patch.dict(
            os.environ,
            SDL_VIDEODRIVER='dummy',
            SDL_RENDER_DRIVER='software',
            SHIRAZ_TEST_GUEST_ACTIVITY='kill',
        ):
            completed = subprocess.run(
                [
                    sys.executable,
                    '-m',
                    'shiraz',
                    'run',
                    str(registry.launch_apk(caller['package'], caller['apk_sha256'])),
                    '--registry',
                    str(registry.root),
                    '--timeout',
                    '40',
                ],
                env=dict(os.environ, PYTHONPATH=str(ROOT / 'src')),
                cwd=ROOT,
                capture_output=True,
                timeout=50,
            )
            (work / 'guest-process-termination-cli.log').write_bytes(
                completed.stdout + completed.stderr
            )
            self.assertEqual(completed.returncode, 0, completed.stderr.decode(errors='replace'))
            result = json.loads(completed.stdout.split(b'\n', 1)[1])
            self.assertEqual(len(result['apps']), 3)
            output = b''
            for event in result['apps']:
                output += Path(event['stdout']).read_bytes() + Path(event['stderr']).read_bytes()
            self.assertEqual(sorted(event['exit_status'] for event in result['apps']), [-9, 0, 0])
            self.assertEqual(
                [
                    event.get('termination_reason')
                    for event in result['apps']
                    if event['exit_status'] == -9
                ],
                ['package_request'],
            )
            (work / 'guest-process-termination.log').write_bytes(output)
            self.assertIn(b'OWNED_PARENT_TERMINATED_OK', output)
            self.assertIn(b'GUEST_RESTART_MAIN_OK', output)
        with (
            patch.dict(os.environ, SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software'),
            HostSession(registry.root) as host,
        ):
            process = host.start(
                caller['package'], expected_sha256=caller['apk_sha256'], settings={'timeout': 40}
            )
            caller_log = host.apps[caller['package']][1] / 'stdout.log'
            deadline = time.monotonic() + 15
            while b'EXPLICIT_ACTIVITY_CALLER_READY' not in caller_log.read_bytes():
                self.assertIsNone(process.poll(), 'Explicit activity caller exited')
                self.assertLess(time.monotonic(), deadline, 'Explicit caller readiness timeout')
                time.sleep(0.05)
            requester = dict(package=caller['package'], launch_id=host.launch_ids[process])
            evidence = []
            secondary_ids = []
            for attempt in range(2):
                secondary = host.start(
                    caller['package'],
                    expected_sha256=caller['apk_sha256'],
                    requester=requester,
                    component=caller['package'] + '.SecondaryActivity',
                    settings={'timeout': 15},
                )
                self.assertNotEqual(secondary.pid, process.pid)
                secondary_ids.append(host.launch_ids[secondary])
                self.assertEqual(secondary.wait(timeout=20), 0, 'Inspect secondary activity logs')
                # The next admission must reclaim the exited slot without an
                # operator collect call, while preserving the completion event.
            self.assertNotEqual(*secondary_ids)
            secondary_events = host.collect()
            self.assertEqual([event['launch_id'] for event in secondary_events], secondary_ids)
            for index, event in enumerate(secondary_events):
                self.assertEqual(event['process_name'], caller['package'] + ':secondary')
                output = Path(event['stdout']).read_bytes()
                (work / ('secondary-activity-process-' + str(index) + '.log')).write_bytes(
                    output + Path(event['stderr']).read_bytes()
                )
                self.assertIn(('SECONDARY_ACTIVITY_PROCESS_OK pid=').encode(), output)
                self.assertIn(('process=' + caller['package'] + ':secondary').encode(), output)
                self.assertIsNone(process.poll(), 'Secondary completion stopped primary')
                evidence.append(event)
            self.assertFalse(host.collect(), 'Completion delivered twice')
            for suffix in ('ExplicitActivity', 'ExplicitAlias'):
                component = target['package'] + '.' + suffix
                child = host.start(
                    target['package'],
                    expected_sha256=target['apk_sha256'],
                    requester=requester,
                    component=component,
                    settings={'timeout': 15},
                )
                self.assertEqual(child.wait(timeout=20), 0, 'Inspect host session activity logs')
                events = host.collect()
                self.assertEqual(len(events), 1)
                event = events[0]
                output = Path(event['stdout']).read_bytes()
                (work / ('explicit-' + suffix + '.log')).write_bytes(
                    output + Path(event['stderr']).read_bytes()
                )
                self.assertIn(
                    ('SIGNED_EXPLICIT_ACTIVITY_OK component=' + component).encode(), output
                )
                self.assertNotIn(b'AUTHENTICATOR_ACCOUNT_MANAGER_DISCOVERY_OK', output)
                self.assertIsNone(process.poll(), 'Caller was stopped by target completion')
                evidence.append(event)
            for mode, suffix in (
                ('result', 'ExplicitAlias'),
                ('cancel', 'ExplicitActivity'),
                ('disconnect', 'ExplicitActivity'),
            ):
                component = target['package'] + '.' + suffix
                with host.broker.open_session(caller['package'], caller['apk_sha256']) as control:
                    result = subprocess.run(
                        sender_command
                        + [
                            str(control.fileno()),
                            target['package'],
                            component,
                            str(target['uid']),
                            mode,
                        ],
                        env=sender_env,
                        pass_fds=(control.fileno(),),
                        capture_output=True,
                        timeout=20,
                        check=False,
                    )
                    child = host.apps[target['package']][0]
                    (work / ('activity-callback-' + mode + '-sender.log')).write_bytes(
                        result.stdout + result.stderr
                    )
                    self.assertEqual(
                        result.returncode, 0, 'Inspect signed activity callback sender log'
                    )
                    self.assertIn(
                        ('SIGNED_ACTIVITY_CALLBACK_OK mode=' + mode).encode(), result.stdout
                    )
                    self.assertEqual(
                        child.wait(timeout=15),
                        1 if mode == 'disconnect' else 0,
                        'Inspect host activity callback logs',
                    )
                events = host.collect()
                self.assertEqual(len(events), 1)
                event = events[0]
                output = Path(event['stdout']).read_bytes()
                (work / ('activity-callback-' + mode + '-target.log')).write_bytes(
                    output + Path(event['stderr']).read_bytes()
                )
                if mode == 'disconnect':
                    self.assertNotIn(b'SIGNED_EXPLICIT_ACTIVITY_OK', output)
                else:
                    self.assertIn(('SIGNED_ACTIVITY_INTENT_OK mode=' + mode).encode(), output)
                self.assertIsNone(process.poll(), 'Caller exited during callback launch')
                evidence.append(event)
            self.assertTrue(host.stop(caller['package'], requester['launch_id']))
            host.collect()
            (work / 'explicit-activity-admission.json').write_text(
                json.dumps(evidence, indent=2) + '\n'
            )

    def probe_account_ui(self, registry, caller, target, work):
        from shiraz.host_session import HostSession

        for mode in (
            'result',
            'cancel',
            'foreign',
            'grants',
            'private',
            'token-result',
            'token-custom',
            'token-cancel',
            'token-wrong-account',
            'token-missing',
            'token-expiry',
            'token-die',
        ):
            with (
                patch.dict(
                    os.environ,
                    SHIRAZ_TEST_ACCOUNT_UI=mode,
                    SDL_VIDEODRIVER='dummy',
                    SDL_RENDER_DRIVER='software',
                ),
                HostSession(registry.root) as host,
            ):
                process = host.start(
                    caller['package'],
                    expected_sha256=caller['apk_sha256'],
                    settings={'timeout': 30},
                )
                status = process.wait(timeout=35)
                caller_logs = host.apps[caller['package']][1]
                (work / ('account-ui-' + mode + '-caller.log')).write_bytes(
                    (caller_logs / 'stdout.log').read_bytes()
                    + (caller_logs / 'stderr.log').read_bytes()
                )
                self.assertEqual(status, 0, 'Account UI caller failed: ' + mode)
                launches_ui = mode in ('result', 'cancel') or mode.startswith('token-')
                if launches_ui:
                    self.assertIn(target['package'], host.apps, 'Authenticator UI never launched')
                    self.assertEqual(
                        host.apps[target['package']][0].wait(timeout=15),
                        1 if mode == 'token-die' else 0,
                        'Account UI target exit mismatch',
                    )
                else:
                    self.assertNotIn(target['package'], host.apps, 'Rejected UI launched a target')
                events = host.collect()
                self.assertEqual(len(events), 2 if launches_ui else 1)
                for event in events:
                    output = Path(event['stdout']).read_bytes()
                    label = 'caller' if event['package'] == caller['package'] else 'target'
                    (work / ('account-ui-' + mode + '-' + label + '.log')).write_bytes(
                        output + Path(event['stderr']).read_bytes()
                    )
                    marker = (
                        'ACCOUNT_UI_CALLER_OK mode=' + mode
                        if label == 'caller'
                        else 'ACCOUNT_UI_ACTIVITY_OK mode=ui-' + mode
                    )
                    if mode == 'token-die':
                        if label == 'target':
                            marker = 'ACCOUNT_UI_ACTIVITY_DIED mode=ui-token-die'
                        else:
                            self.assertIn(b'ACCOUNT_UI_DEATH_RECOVERY_OK', output)
                    self.assertIn(marker.encode(), output)

    def probe_account_listeners(self, registry, identity, work):
        from shiraz.host_session import HostSession

        with (
            patch.dict(
                os.environ,
                SHIRAZ_TEST_ACCOUNT_LISTENERS='1',
                SDL_VIDEODRIVER='dummy',
                SDL_RENDER_DRIVER='software',
            ),
            HostSession(registry.root) as host,
        ):
            process = host.start(
                identity['package'],
                expected_sha256=identity['apk_sha256'],
                settings={'timeout': 20},
            )
            status = process.wait(timeout=25)
            logs = host.apps[identity['package']][1]
            output = (logs / 'stdout.log').read_bytes()
            (work / ('account-listeners-' + identity['package'] + '.log')).write_bytes(
                output + (logs / 'stderr.log').read_bytes()
            )
            self.assertEqual(status, 0, 'Signed account listeners failed')
            self.assertIn(
                ('SIGNED_ACCOUNT_LISTENERS_OK package=' + identity['package'] + ' pid=').encode(),
                output,
            )
            self.assertIn(b'SHIRAZ_RUNTIME_CLEANUP_COMPLETE', output)
            with host.broker.lock:
                self.assertFalse(
                    host.broker.account_watches,
                    'Account watch survived listener removal and app exit',
                )
            self.assertEqual(len(host.collect()), 1)

    def probe_local_account_ui(self, registry, identity, work):
        from shiraz.host_session import HostSession

        for mode in ('result', 'cancel'):
            with (
                patch.dict(
                    os.environ,
                    SHIRAZ_TEST_LOCAL_ACCOUNT_UI=mode,
                    SDL_VIDEODRIVER='dummy',
                    SDL_RENDER_DRIVER='software',
                ),
                HostSession(registry.root) as host,
            ):
                process = host.start(
                    identity['package'],
                    expected_sha256=identity['apk_sha256'],
                    settings={'timeout': 20},
                )
                status = process.wait(timeout=25)
                logs = host.apps[identity['package']][1]
                output = (logs / 'stdout.log').read_bytes()
                (work / ('account-local-ui-' + mode + '.log')).write_bytes(
                    output + (logs / 'stderr.log').read_bytes()
                )
                self.assertEqual(status, 0, 'Local account UI failed: ' + mode)
                self.assertIn(('ACCOUNT_LOCAL_UI_CALLER_OK mode=' + mode).encode(), output)
                self.assertIn(('ACCOUNT_UI_ACTIVITY_OK mode=ui-local-' + mode).encode(), output)
                self.assertEqual(len(host.collect()), 1, 'Local UI started a separate app process')

    def probe_storage_job_restart(self, registry, identity):
        registry.cancel_all_shared_jobs(identity['uid'], identity['apk_sha256'])
        evidence = ROOT / 'build/job-storage-restart-smoke'
        evidence.mkdir(parents=True, exist_ok=True)
        results = []
        for mode in ('prepare', 'resume'):
            completed = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / 'tests/job_storage_restart_host.py'),
                    str(registry.root),
                ],
                input=json.dumps(dict(package=identity['package'], mode=mode)),
                text=True,
                capture_output=True,
                timeout=60,
                check=True,
            )
            (evidence / (mode + '.stderr.log')).write_text(completed.stderr)
            result = json.loads(completed.stdout)
            self.assertTrue(result['low_observed'])
            results.append(result)
            if mode == 'prepare':
                self.assertEqual(
                    registry.list_persisted_jobs(identity['uid'], identity['apk_sha256']),
                    [result['saved']],
                )
        self.assertNotEqual(results[0]['pid'], results[1]['pid'])
        self.assertEqual(results[0]['saved'], results[1]['saved'])
        self.assertEqual(registry.list_persisted_jobs(identity['uid'], identity['apk_sha256']), [])
        (evidence / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
        print(
            'APK_PUBLIC_STORAGE_PROCESS_RESTART_OK',
            results[0]['pid'],
            results[1]['pid'],
            flush=True,
        )

    def probe_crashed_job_host(self, registry, identity, definition, *, kill_worker=False):
        from shiraz.job_lifetime import probe

        evidence = (
            ROOT / 'build' / ('job-crash-art-lock-smoke' if kill_worker else 'job-crash-host-smoke')
        )
        evidence.mkdir(parents=True, exist_ok=True)
        job_id = 992 if kill_worker else 991
        definition = dict(
            definition,
            id=job_id,
            minimum_latency_ms=0,
            initial_backoff_ms=10000,
            extras={
                'action': dict(type='string', value='hang' if kill_worker else 'crash-retry'),
                'recovery_key': dict(type='string', value=uuid.uuid4().hex),
            },
        )
        worker_pid = None

        def group_members():
            output = subprocess.run(
                ['ps', '-axo', 'pid=,ppid=,pgid=,stat=,comm='],
                capture_output=True,
                text=True,
                check=True,
                timeout=5,
            ).stdout
            members = []
            for line in output.splitlines():
                fields = line.strip().split(None, 4)
                if (
                    len(fields) == 5
                    and int(fields[2]) == worker_pid
                    and not fields[3].startswith('Z')
                ):
                    members.append(
                        dict(
                            pid=int(fields[0]),
                            parent=int(fields[1]),
                            state=fields[3],
                            name=Path(fields[4]).name,
                        )
                    )
            return members

        with (evidence / 'stderr.log').open('wb') as err:
            host = subprocess.Popen(
                [sys.executable, str(ROOT / 'tests/job_crash_host.py'), str(registry.root)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=err,
                text=True,
            )
            try:
                host.stdin.write(
                    json.dumps(dict(package=identity['package'], definition=definition))
                )
                host.stdin.close()
                with selectors.DefaultSelector() as selector:
                    selector.register(host.stdout, selectors.EVENT_READ)
                    self.assertTrue(selector.select(40), 'Crash host did not reach APK onStartJob')
                ready = json.loads(host.stdout.readline())
                worker_pid = ready['pid']
                before = group_members()
                self.assertIsNone(host.poll())
                self.assertGreaterEqual(len(before), 2, before)
                self.assertTrue(
                    any(
                        item['name'] == identity['package'] + ':jobs'
                        and item['parent'] == worker_pid
                        for item in before
                    ),
                    before,
                )
                with registry.connect() as db:
                    claim_token = db.execute(
                        'SELECT token FROM persisted_job_claims WHERE owner_uid=? AND job_id=?',
                        (identity['uid'], job_id),
                    ).fetchone()[0]
                self.assertIsNone(probe(registry.root, claim_token))
                if kill_worker:
                    # Freeze ART as well: its new control reader otherwise exits
                    # on host EOF. An unresponsive descendant must still retain
                    # the inherited lifetime lock after both parents die.
                    art_pid = next(
                        item['pid']
                        for item in before
                        if item['name'] == identity['package'] + ':jobs'
                    )
                    os.kill(art_pid, signal.SIGSTOP)
                    deadline = time.monotonic() + 5
                    while (
                        not any(
                            item['pid'] == art_pid and 'T' in item['state']
                            for item in group_members()
                        )
                        and time.monotonic() < deadline
                    ):
                        time.sleep(0.02)
                    self.assertTrue(
                        any(
                            item['pid'] == art_pid and 'T' in item['state']
                            for item in group_members()
                        ),
                        'ART did not suspend',
                    )
                    os.kill(host.pid, signal.SIGSTOP)
                    os.kill(worker_pid, signal.SIGKILL)
                host.kill()
                host.wait(timeout=5)
                if kill_worker:
                    deadline = time.monotonic() + 5
                    while (
                        any(item['pid'] == worker_pid for item in group_members())
                        and time.monotonic() < deadline
                    ):
                        time.sleep(0.02)
                    self.assertTrue(group_members(), 'ART did not survive injected double crash')
                    self.assertFalse(any(item['pid'] == worker_pid for item in group_members()))
                    self.assertIsNone(
                        probe(registry.root, claim_token), 'ART lost lifetime descriptor'
                    )
                    os.killpg(worker_pid, signal.SIGKILL)
                deadline = time.monotonic() + 10
                while group_members() and time.monotonic() < deadline:
                    time.sleep(0.05)
                self.assertEqual(group_members(), [])
                reopened = PackageRegistry(registry.root)
                jobs = reopened.list_persisted_jobs(identity['uid'], identity['apk_sha256'])
                saved = next(job for job in jobs if job['id'] == job_id)
                with reopened.connect() as db:
                    self.assertEqual(
                        db.execute(
                            'SELECT count(*) FROM persisted_job_claims WHERE owner_uid=? AND job_id=?',
                            (identity['uid'], job_id),
                        ).fetchone()[0],
                        1,
                    )
                self.assertIsNone(
                    reopened.claim_due_persisted_job(
                        identity['uid'], identity['apk_sha256'], job_id, saved['revision']
                    )
                )
                with probe(registry.root, claim_token):
                    pass
                from shiraz.job_recovery import recover

                completed_evidence = None
                elapsed = None
                if kill_worker:
                    self.assertTrue(recover(reopened, identity['uid'], job_id, claim_token))
                    retried = next(
                        job
                        for job in reopened.list_persisted_jobs(
                            identity['uid'], identity['apk_sha256']
                        )
                        if job['id'] == job_id
                    )
                else:
                    from shiraz.service_broker import ServiceBroker

                    with ServiceBroker(reopened) as replacement_host:
                        invocations = queue.Queue(maxsize=2)
                        dispatch = replacement_host.dispatch_persisted_job

                        def observe_reexecution(*args, **kwargs):
                            run = dispatch(*args, **kwargs)
                            if run is not None:
                                invocations.put_nowait(run)
                            return run

                        with patch.object(
                            replacement_host,
                            'dispatch_persisted_job',
                            side_effect=observe_reexecution,
                        ):
                            started = time.monotonic()
                            scheduler = replacement_host.start_job_scheduler()
                            deadline = started + 5
                            while True:
                                retried = next(
                                    job
                                    for job in reopened.list_persisted_jobs(
                                        identity['uid'], identity['apk_sha256']
                                    )
                                    if job['id'] == job_id
                                )
                                if retried['attempts'] == 1:
                                    break
                                self.assertLess(
                                    time.monotonic(), deadline, 'Automatic recovery did not run'
                                )
                                time.sleep(0.02)
                            self.assertEqual(retried['definition'], saved['definition'])
                            execution = invocations.get(timeout=30)
                            elapsed = time.monotonic() - started
                            self.assertGreaterEqual(elapsed, 10, 'Retry bypassed real backoff')
                            self.assertTrue(execution.done.wait(20))
                            self.assertIsNone(execution.error)
                            self.assertEqual(execution.result, dict(applied=True, retry=False))
                            self.assertTrue(execution.worker.terminal_cleanup_complete)
                            self.assertEqual(execution.worker.process.returncode, 0)
                            completed_evidence = str(execution.worker.evidence)
                            self.assertIn(
                                'APK_CRASH_RETRY_COMPLETE',
                                (execution.worker.evidence / 'stdout.log').read_text(),
                            )
                            self.assertEqual(
                                reopened.list_persisted_jobs(
                                    identity['uid'], identity['apk_sha256']
                                ),
                                [],
                            )
                            retired_paths = [
                                reopened.root / 'job-lifetimes' / (token + '.lock')
                                for token in (claim_token, execution.claim['token'])
                            ]
                            deadline = time.monotonic() + 5
                            while (
                                any(path.exists() for path in retired_paths)
                                and time.monotonic() < deadline
                            ):
                                time.sleep(0.02)
                            self.assertFalse(any(path.exists() for path in retired_paths))
                            self.assertEqual(list(scheduler.failures), [])
                            print('ART_JOB_LIFETIME_LOCKS_RETIRED', flush=True)
                            print(
                                'ART_CRASH_AUTOMATIC_REEXECUTION_OK', round(elapsed, 3), flush=True
                            )
                self.assertEqual(retried['attempts'], 1)
                self.assertNotEqual(retried['revision'], saved['revision'])
                self.assertFalse(recover(reopened, identity['uid'], job_id, claim_token))

                (evidence / 'result.json').write_text(
                    json.dumps(
                        dict(
                            before=before,
                            executing_after=[],
                            claim_retained_before_recovery=True,
                            recovery_retry_attempts=1,
                            worker_evidence=ready['evidence'],
                            completed_evidence=completed_evidence,
                            retry_elapsed_seconds=elapsed,
                        ),
                        indent=2,
                    )
                    + '\n'
                )
                print(
                    'ART_DESCENDANT_LIFETIME_LOCK_OK'
                    if kill_worker
                    else 'ART_HOST_CRASH_GROUP_STOPPED_CLAIM_RETAINED',
                    flush=True,
                )
            finally:
                if host.poll() is None:
                    host.kill()
                    host.wait(timeout=5)
                if worker_pid is not None and group_members():
                    try:
                        os.killpg(worker_pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                if not host.stdin.closed:
                    host.stdin.close()
                host.stdout.close()

    def probe_job_service_host(self, registry, identity, definition, *, supervised=False):
        evidence = (
            ROOT
            / 'build'
            / ('job-supervised-host-smoke' if supervised else 'job-service-host-smoke')
        )
        evidence.mkdir(parents=True, exist_ok=True)
        with (evidence / 'stderr.log').open('wb') as err:
            host = subprocess.Popen(
                [
                    sys.executable,
                    '-m',
                    'shiraz',
                    'supervise-jobs' if supervised else 'serve-jobs',
                    '--registry',
                    str(registry.root),
                ],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=err,
                text=True,
            )
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(host.stdout, selectors.EVENT_READ)
                    self.assertTrue(selector.select(10), 'Job service readiness timed out')
                ready = json.loads(host.stdout.readline())
                self.assertEqual(ready['event'], 'ready')
                self.assertEqual(ready['service'], 'jobs')
                if not supervised:
                    self.assertEqual(ready['pid'], host.pid)
                for job_id in (981, 982):
                    registry.store_shared_job(
                        identity['uid'],
                        identity['apk_sha256'],
                        dict(
                            definition,
                            id=job_id,
                            minimum_latency_ms=250,
                            extras={'action': dict(type='string', value='complete')},
                        ),
                    )
                    deadline = time.monotonic() + 40
                    while any(
                        job['id'] == job_id
                        for job in registry.list_persisted_jobs(
                            identity['uid'], identity['apk_sha256']
                        )
                    ):
                        self.assertIsNone(host.poll(), 'Job service exited while running work')
                        self.assertLess(
                            time.monotonic(), deadline, 'Job service did not complete work'
                        )
                        time.sleep(0.05)
                    self.assertIsNone(host.poll(), 'Job service exited when idle')
                    if supervised and job_id == 981:
                        os.kill(ready['pid'], signal.SIGKILL)
                        with selectors.DefaultSelector() as selector:
                            selector.register(host.stdout, selectors.EVENT_READ)
                            self.assertTrue(selector.select(10))
                        restart = json.loads(host.stdout.readline())
                        self.assertEqual(restart['event'], 'restart')
                        with selectors.DefaultSelector() as selector:
                            selector.register(host.stdout, selectors.EVENT_READ)
                            self.assertTrue(selector.select(10))
                        replacement = json.loads(host.stdout.readline())
                        self.assertEqual(replacement['event'], 'ready')
                        self.assertNotEqual(replacement['pid'], ready['pid'])
                        ready = replacement

                crash_result = None
                if supervised:
                    ready, crash_result = self.probe_supervised_active_job_crash(
                        registry, identity, definition, host, ready
                    )
                host.terminate()
                stdout, _ = host.communicate(timeout=20)
                self.assertEqual(host.returncode, 0)
                self.assertEqual(json.loads(stdout), dict(event='stopped', service='jobs'))
                (evidence / 'result.json').write_text(
                    json.dumps(
                        dict(
                            ready=ready,
                            completed=[981, 982],
                            crash_recovery=crash_result,
                            exit_status=0,
                        )
                    )
                    + '\n'
                )
                print(
                    'JOB_SUPERVISED_RESTART_AND_EXECUTION_OK'
                    if supervised
                    else 'JOB_SERVICE_STDIN_EOF_IDLE_AND_EXECUTION_OK',
                    flush=True,
                )
            finally:
                if host.poll() is None:
                    host.kill()
                    host.wait(timeout=5)
                host.stdout.close()

    def probe_supervised_active_job_crash(self, registry, identity, definition, host, ready):
        worker_root = ROOT / 'build/job-processes'
        before = set(worker_root.iterdir())
        scheduled = registry.store_shared_job(
            identity['uid'],
            identity['apk_sha256'],
            dict(
                definition,
                id=983,
                minimum_latency_ms=0,
                initial_backoff_ms=10000,
                extras={
                    'action': dict(type='string', value='crash-retry'),
                    'recovery_key': dict(type='string', value=uuid.uuid4().hex),
                },
            ),
        )

        def workers_with(marker):
            return [
                path
                for path in set(worker_root.iterdir()) - before
                if (path / 'stdout.log').exists() and marker in (path / 'stdout.log').read_text()
            ]

        deadline = time.monotonic() + 30
        while not workers_with('APK_CRASH_RETRY_FIRST'):
            self.assertIsNone(host.poll())
            self.assertLess(time.monotonic(), deadline, 'Supervised APK job did not start')
            time.sleep(0.05)
        initial = workers_with('APK_CRASH_RETRY_FIRST')
        self.assertEqual(len(initial), 1)
        crashed_pid = ready['pid']
        started = time.monotonic()
        os.kill(crashed_pid, signal.SIGKILL)
        with selectors.DefaultSelector() as selector:
            selector.register(host.stdout, selectors.EVENT_READ)
            self.assertTrue(selector.select(10))
        self.assertEqual(json.loads(host.stdout.readline())['event'], 'restart')
        with selectors.DefaultSelector() as selector:
            selector.register(host.stdout, selectors.EVENT_READ)
            self.assertTrue(selector.select(10))
        ready = json.loads(host.stdout.readline())
        self.assertEqual(ready['event'], 'ready')
        self.assertNotEqual(ready['pid'], crashed_pid)
        deadline = time.monotonic() + 5
        while True:
            jobs = registry.list_persisted_jobs(identity['uid'], identity['apk_sha256'])
            recovered = next(job for job in jobs if job['id'] == 983)
            if recovered['attempts'] == 1:
                break
            self.assertLess(time.monotonic(), deadline, 'Supervised recovery did not run')
            time.sleep(0.02)
        self.assertNotEqual(recovered['revision'], scheduled['revision'])
        self.assertEqual(recovered['definition'], scheduled['definition'])
        deadline = time.monotonic() + 30
        while any(
            job['id'] == 983
            for job in registry.list_persisted_jobs(identity['uid'], identity['apk_sha256'])
        ):
            self.assertIsNone(host.poll())
            self.assertLess(time.monotonic(), deadline, 'Supervised retry did not complete')
            time.sleep(0.05)
        elapsed = time.monotonic() - started
        self.assertGreaterEqual(elapsed, 10)
        completed = workers_with('APK_CRASH_RETRY_COMPLETE')
        self.assertEqual(len(completed), 1)
        self.assertNotEqual(completed, initial)
        print('JOB_SUPERVISED_ACTIVE_CRASH_RECOVERY_OK', round(elapsed, 3), flush=True)
        return ready, dict(
            initial_worker=str(initial[0]),
            completed_worker=str(completed[0]),
            elapsed_seconds=elapsed,
            recovered_attempts=1,
            original_host=crashed_pid,
            replacement_host=ready['pid'],
        )

    def probe_idle_job_host(self, registry, identity, definition):
        evidence = ROOT / 'build/idle-job-host-smoke'
        evidence.mkdir(parents=True, exist_ok=True)
        transcript = []
        with (evidence / 'stderr.log').open('wb') as err:
            process = subprocess.Popen(
                [sys.executable, '-m', 'shiraz', 'serve-session', '--registry', str(registry.root)],
                env=dict(os.environ, PYTHONPATH=str(ROOT / 'src')),
                cwd=ROOT,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=err,
                bufsize=0,
            )
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(process.stdout, selectors.EVENT_READ)

                    def receive():
                        self.assertTrue(selector.select(10), 'Idle host control timed out')
                        response = json.loads(process.stdout.readline())
                        transcript.append(response)
                        return response

                    def request(value):
                        process.stdin.write(json.dumps(value).encode() + b'\n')
                        process.stdin.flush()
                        return receive()

                    self.assertTrue(receive()['ready'])
                    self.assertEqual(request(dict(op='status'))['running'], [])
                    for attempt in range(2):
                        registry.store_persisted_job(
                            identity['uid'],
                            identity['apk_sha256'],
                            dict(
                                definition,
                                minimum_latency_ms=250,
                                extras={'action': dict(type='string', value='complete')},
                            ),
                        )
                        deadline = time.monotonic() + 40
                        while registry.list_persisted_jobs(identity['uid'], identity['apk_sha256']):
                            self.assertIsNone(
                                process.poll(), 'Idle host exited while executing job'
                            )
                            self.assertLess(
                                time.monotonic(), deadline, 'Idle host job did not complete'
                            )
                            time.sleep(0.05)
                        self.assertIsNone(process.poll())
                        self.assertEqual(request(dict(op='status'))['running'], [])
                        print('IDLE_HOST_JOB_COMPLETE', attempt + 1, flush=True)
                    self.assertTrue(request(dict(op='shutdown'))['ok'])
                    self.assertEqual(process.wait(timeout=20), 0)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=20)
                process.stdin.close()
                process.stdout.close()
                (evidence / 'protocol.json').write_text(json.dumps(transcript, indent=2) + '\n')

    def probe_host_control(self, registry, identity):
        evidence = ROOT / 'build/host-control-smoke'
        evidence.mkdir(parents=True, exist_ok=True)
        env = dict(
            os.environ,
            PYTHONPATH=str(ROOT / 'src'),
            SDL_VIDEODRIVER='dummy',
            SDL_RENDER_DRIVER='software',
        )
        env.update(SHIRAZ_LAUNCHER_APPS='/not-a-library', SHIRAZ_LAUNCHER_INBOX='/not-an-inbox')
        with (evidence / 'stderr.log').open('wb') as err:
            process = subprocess.Popen(
                [sys.executable, '-m', 'shiraz', 'serve-session', '--registry', str(registry.root)],
                env=env,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=err,
                bufsize=0,
            )
            selector = selectors.DefaultSelector()
            selector.register(process.stdout, selectors.EVENT_READ)
            transcript = []

            def receive():
                self.assertTrue(selector.select(10), 'Host control reply timed out')
                response = json.loads(process.stdout.readline())
                transcript.append(response)
                return response

            def request(value):
                process.stdin.write(json.dumps(value).encode() + b'\n')
                process.stdin.flush()
                return receive()

            try:
                self.assertTrue(receive()['ready'])
                rejected = request(dict(op='start', package=identity['package'], sha256='0' * 64))
                self.assertFalse(rejected['ok'])
                logs = []
                launches = []
                for _ in range(2):
                    started = request(
                        dict(op='start', package=identity['package'], sha256=identity['apk_sha256'])
                    )
                    self.assertTrue(started['ok'], started)
                    launches.append(started['launch_id'])
                    deadline = time.monotonic() + 60
                    while True:
                        status = request(dict(op='status'))
                        self.assertTrue(status['ok'])
                        if status['finished']:
                            completed = status['finished'][0]
                            self.assertEqual(completed['launch_id'], started['launch_id'])
                            self.assertEqual(completed['exit_status'], 0, completed)
                            logs.append(completed['stdout'])
                            self.assertIn(
                                'BACKGROUND_APK_JOB_FINISHED', Path(completed['stdout']).read_text()
                            )
                            self.assertNotIn(
                                'BACKGROUND_APK_INBOX=', Path(completed['stdout']).read_text()
                            )
                            break
                        self.assertLess(time.monotonic(), deadline, 'App did not finish')
                        time.sleep(0.05)
                    self.assertIsNone(process.poll(), 'Host exited with its app')
                    self.assertEqual(
                        request(dict(op='status')), dict(ok=True, finished=[], running=[])
                    )
                self.assertNotEqual(logs[0], logs[1])
                self.assertNotEqual(launches[0], launches[1])
                started = request(
                    dict(op='start', package=identity['package'], sha256=identity['apk_sha256'])
                )
                self.assertTrue(started['ok'])
                stale = request(dict(op='stop', package=identity['package'], launch_id=launches[0]))
                self.assertFalse(stale['ok'])
                stopped = request(
                    dict(op='stop', package=identity['package'], launch_id=started['launch_id'])
                )
                self.assertTrue(stopped['stopped'])
                self.assertIsNone(process.poll())
                completed = request(dict(op='status'))
                self.assertEqual(len(completed['finished']), 1)
                self.assertEqual(completed['running'], [])
                self.assertFalse(
                    request(
                        dict(op='stop', package=identity['package'], launch_id=started['launch_id'])
                    )['stopped']
                )
                self.assertTrue(request(dict(op='shutdown'))['ok'])
                self.assertEqual(process.wait(timeout=10), 0)
                selector.unregister(process.stdout)
                process.stdin.close()
                process.stdout.close()
                # Losing the launcher's pipe must also retire a live app host.
                process = subprocess.Popen(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'serve-session',
                        '--registry',
                        str(registry.root),
                    ],
                    env=env,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=err,
                    bufsize=0,
                )
                selector.register(process.stdout, selectors.EVENT_READ)
                self.assertTrue(receive()['ready'])
                started = request(
                    dict(op='start', package=identity['package'], sha256=identity['apk_sha256'])
                )
                self.assertTrue(started['ok'])
                process.stdin.close()
                self.assertEqual(process.wait(timeout=10), 0)
                with self.assertRaises(ProcessLookupError):
                    os.kill(started['pid'], 0)
                selector.unregister(process.stdout)
                process.stdout.close()
                isolated = PackageRegistry(registry.root.parent / 'retirement-registry')
                enrolled = isolated.enroll(
                    registry.launch_apk(identity['package'], identity['apk_sha256'])
                )
                process = subprocess.Popen(
                    [
                        sys.executable,
                        '-m',
                        'shiraz',
                        'serve-session',
                        '--registry',
                        str(isolated.root),
                    ],
                    env=env,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=err,
                    bufsize=0,
                )
                selector.register(process.stdout, selectors.EVENT_READ)
                self.assertTrue(receive()['ready'])
                started = request(
                    dict(op='start', package=enrolled['package'], sha256=enrolled['apk_sha256'])
                )
                self.assertTrue(started['ok'])
                retirement = dict(
                    op='retire',
                    package=enrolled['package'],
                    sha256=enrolled['apk_sha256'],
                    uid=enrolled['uid'],
                )
                deadline = time.monotonic() + 60
                while 'BACKGROUND_APK_UID=' not in Path(started['stdout']).read_text():
                    self.assertLess(
                        time.monotonic(), deadline, 'Retirement fixture did not enter ART activity'
                    )
                    time.sleep(0.02)
                with self.assertRaisesRegex(FormatError, 'generation is in use'):
                    with isolated._generation_lock(enrolled['package'], exclusive=True):
                        self.fail('Live ART app did not retain its generation lease')
                self.assertFalse(request(dict(retirement, uid=enrolled['uid'] + 1))['ok'])
                os.kill(started['pid'], 0)
                self.assertTrue(request(retirement)['retired'])
                with isolated._generation_lock(enrolled['package'], exclusive=True):
                    pass
                with self.assertRaises(ProcessLookupError):
                    os.kill(started['pid'], 0)
                self.assertIsNone(process.poll())
                status = request(dict(op='status'))
                self.assertEqual(status['running'], [])
                self.assertFalse(
                    request(
                        dict(op='start', package=enrolled['package'], sha256=enrolled['apk_sha256'])
                    )['ok']
                )
                self.assertTrue(request(dict(op='shutdown'))['ok'])
                self.assertEqual(process.wait(timeout=10), 0)
            finally:
                process.stdin.close()
                process.wait(timeout=10)
                selector.close()
                process.stdout.close()
                (evidence / 'transcript.json').write_text(json.dumps(transcript, indent=2))

    def probe_registered_service(self, registry, identity):
        from shiraz.runtime import run_service

        evidence = ROOT / 'build/registered-service-smoke'
        evidence.mkdir(parents=True, exist_ok=True)
        server, client = socket.socketpair()
        try:
            with self.assertRaisesRegex(FormatError, 'does not match'):
                run_service(
                    registry.root,
                    identity['package'],
                    '0' * 64,
                    'org.shiraz.servicefixture.Worker',
                    client,
                )
            with self.assertRaisesRegex(FormatError, 'not uniquely declared'):
                run_service(
                    registry.root,
                    identity['package'],
                    identity['apk_sha256'],
                    'org.shiraz.servicefixture.Undeclared',
                    client,
                )
        except BaseException:
            server.close()
            client.close()
            raise
        env = dict(os.environ, PYTHONPATH=str(ROOT / 'src'))
        env.pop('SHIRAZ_BROKER_FD', None)
        command = [
            sys.executable,
            str(ROOT / 'tests/registered_service_probe.py'),
            str(registry.root),
            identity['package'],
            identity['apk_sha256'],
            'org.shiraz.servicefixture.Worker',
            str(client.fileno()),
        ]
        try:
            with (evidence / 'stderr.log').open('wb') as errors:
                child = subprocess.Popen(
                    command,
                    env=env,
                    cwd=ROOT,
                    pass_fds=(client.fileno(),),
                    stdout=subprocess.PIPE,
                    stderr=errors,
                )
                client.close()
                output = bytearray()
                try:
                    deadline = time.monotonic() + 30
                    with selectors.DefaultSelector() as selector:
                        selector.register(child.stdout, selectors.EVENT_READ)
                        while b'SHIRAZ_SERVICE_CHANNEL_READY' not in output:
                            remaining = deadline - time.monotonic()
                            self.assertGreater(remaining, 0, 'Service readiness deadline')
                            self.assertTrue(selector.select(remaining), 'Service readiness timeout')
                            chunk = os.read(child.stdout.fileno(), 65536)
                            self.assertTrue(
                                chunk, 'Service exited before readiness; inspect stderr.log'
                            )
                            output.extend(chunk)
                    server.close()
                    rest, _ = child.communicate(timeout=15)
                    output.extend(rest)
                    self.assertEqual(
                        child.returncode, 0, 'Inspect registered-service-smoke/stderr.log'
                    )
                    for marker in (
                        b'SHIRAZ_BROKER_CHANNEL_ATTACHED',
                        b'SERVICE_ONLY_CREATED',
                        b'SERVICE_ONLY_DESTROYED',
                    ):
                        self.assertIn(marker, output)
                finally:
                    if child.poll() is None:
                        child.kill()
                        child.communicate()
                    (evidence / 'stdout.log').write_bytes(output)
        finally:
            server.close()
            client.close()


if __name__ == '__main__':
    unittest.main()
