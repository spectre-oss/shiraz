# SPDX-License-Identifier: GPL-3.0-only
"""Private broker control channels; registered resolution and private service process endpoints.

The trusted launcher must issue a session for the exact verified APK it launches
and pass only the client fd to that process. There is no public socket listener
and no request can choose a caller UID. This does not create host OS isolation.
"""

import json
import socket
import subprocess
import threading
import time
import uuid
from dataclasses import dataclass

from .account_watch import AccountWatch
from .apk import FormatError
from .broker_wire import MAX_FRAME, read_packet, write_packet, write_payload  # noqa: F401
from .dynamic_receivers import validate_registration
from .job_definition import validate as validate_job_definition
from .package_registry import NAME, ReceiverFilteredError, ServiceUnavailableError
from .pending_intent_dispatch import PendingIntentDispatch
from .pending_intent_store import CanceledTokenError, PendingIntentStore
from .pending_intent_wire import blob as pending_intent_blob
from .pending_intent_wire import handle as pending_intent_request
from .service_filter import matches, validate_query
from .service_start_state import ServiceStartState


def read_frame(channel, idle_timeout=None):
    value, _ = read_packet(channel, idle_timeout, max_descriptors=0)
    return value


def write_frame(channel, value):
    write_packet(channel, value)


@dataclass(frozen=True)
class Identity:
    package: str
    uid: int
    sha256: str
    worker: object = None
    process_name: str = None


class RetirementCleanupError(RuntimeError):
    """Enrollment was revoked, but process cleanup could not be confirmed."""


class QuiescenceCleanupError(RuntimeError):
    """Admission is blocked, but process cleanup could not be confirmed."""


class ServiceBroker:
    def __init__(self, registry, *, activity_launcher=None, process_terminator=None):
        self.registry = registry
        self.activity_launcher = activity_launcher
        self.process_terminator = process_terminator
        self.lock = threading.RLock()
        self.ready = threading.Condition(self.lock)
        self.sessions = {}
        self.identities = {}
        self.live_processes = {}
        self.foreground_admissions = {}
        self.receiver_hosts = {}
        self.pending_intent_dispatch = PendingIntentDispatch(registry)
        self.dynamic_receivers = {}
        self.account_watches = set()
        self.processes = set()
        self.job_dispatches = set()
        self.job_scheduler = None
        self.sync_scheduler = None
        self.targets = {}
        self.start_tickets = {}
        self.next_start_ticket = 0
        self.service_restarts = {}
        self.restart_threads = set()
        self.restart_delays = (0.25, 1.0, 4.0)
        self.pending_retirements = {}
        self.quiesced = set()
        self.closed = False

    def open_session(self, package, apk_sha256, *, worker=None, process_name=None):
        """Trusted-launcher API. Returned client fd is a capability, not a public endpoint."""
        if process_name is not None:
            if not isinstance(process_name, str) or not 1 <= len(process_name) <= 1024:
                raise ValueError('Invalid host process identity')
            if process_name.startswith(':'):
                if not NAME.fullmatch(process_name[1:]):
                    raise ValueError('Invalid private process identity')
                process_name = package + process_name
            elif ':' in process_name:
                prefix, suffix = process_name.split(':', 1)
                if prefix != package or not NAME.fullmatch(suffix):
                    raise ValueError('Invalid private process identity')
            elif not NAME.fullmatch(process_name):
                raise ValueError('Invalid host process identity')
        if worker is not None and worker.key[:2] != (package, apk_sha256):
            raise PermissionError('Worker session identity mismatch')
        with self.lock:
            if package in self.quiesced:
                raise RuntimeError('Package is quiesced for update')
            if any(key[0] == package for key in self.pending_retirements):
                raise RuntimeError('Package retirement cleanup is incomplete')
            record = self.registry.identity(package)
            if record['sha256'] != apk_sha256:
                raise PermissionError('Launch APK does not match enrolled package')
            identity = Identity(**record, worker=worker, process_name=process_name)
            process_key = (package, apk_sha256, process_name) if process_name is not None else None
            if process_key is not None and process_key in self.live_processes:
                raise RuntimeError('Android process already has a live broker session')
            if self.closed:
                raise RuntimeError('Broker closed')
            if len(self.sessions) >= 128:
                raise RuntimeError('Broker session limit')
            server, client = socket.socketpair()
            if hasattr(socket, 'SO_PASSCRED'):
                server.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
                client.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
            worker = threading.Thread(
                target=self._serve,
                args=(server, identity),
                daemon=True,
                name='shiraz-service-control',
            )
            self.sessions[server] = worker
            self.identities[server] = identity
            if process_key is not None:
                self.live_processes[process_key] = server
            try:
                worker.start()
            except BaseException:
                del self.sessions[server]
                del self.identities[server]
                if process_key is not None:
                    self.live_processes.pop(process_key, None)
                server.close()
                client.close()
                raise
            return client

    def start_job_scheduler(self):
        """Trusted host activation; schedules only existing admitted definitions."""
        from .job_scheduler import JobScheduler

        with self.lock:
            if self.closed:
                raise RuntimeError('Broker closed')
            if self.job_scheduler is None:
                scheduler = JobScheduler(self)
                scheduler.thread.start()
                self.job_scheduler = scheduler
            return self.job_scheduler

    def start_sync_scheduler(self):
        """Trusted host activation of the durable sync queue consumer."""
        from .sync_scheduler import SyncScheduler

        with self.lock:
            if self.closed:
                raise RuntimeError('Broker closed')
            if self.sync_scheduler is None:
                scheduler = SyncScheduler(self)
                scheduler.thread.start()
                self.sync_scheduler = scheduler
            return self.sync_scheduler

    def dispatch_persisted_job(
        self, package, owner_uid, apk_sha256, job_id, revision, *, timeout=60
    ):
        """Host-only due claim and launch with broker-owned durable completion."""
        from .job_dispatch import start

        return start(self, package, owner_uid, apk_sha256, job_id, revision, timeout=timeout)

    def dispatch_sync_request(self, request_id, *, timeout=60):
        """Trusted host dispatch of one claimed request; no guest command selects a worker."""
        from .sync_dispatch import run

        return run(self, request_id, timeout=timeout)

    def start_job(
        self,
        owner_uid,
        apk_sha256,
        definition,
        *,
        timeout=60,
        lifetime_fd=None,
        deadline_expired=False,
        work_claim=None,
    ):
        """Trusted host invocation, not guest scheduling or durable claim admission.

        The dispatcher must hold a reservation and coordinate its completion.
        Workers participate in broker close/quiescence/retirement cleanup.
        """
        from .job_definition import validate
        from .job_process import JobProcess

        definition = validate(definition)
        with self.lock:
            if self.closed:
                raise RuntimeError('Broker closed')
            result = self.registry.authorize_job(owner_uid, apk_sha256, definition['component'])
            if result['package'] in self.quiesced:
                raise PermissionError('Job package quiesced')
            if len(self.processes) >= 64:
                raise RuntimeError('Broker worker limit')
            service = result['service']
            identity = dict(
                package=result['package'],
                uid=result['uid'],
                apk_sha256=result['apk_sha256'],
                component=service['name'],
                process=service.get('process') or result['package'],
            )
            worker = JobProcess(
                self,
                identity,
                definition,
                timeout=timeout,
                lifetime_fd=lifetime_fd,
                deadline_expired=deadline_expired,
                work_claim=work_claim,
            )
            self.processes.add(worker)
            return worker

    def retire_package(self, package, *, expected_sha256, expected_uid):
        """Revoke enrollment and stop its hosted services and broker sessions.

        The host must stop the package's app processes separately: already issued
        endpoints to other packages are capabilities held by those processes.
        """
        return self._drain_package(
            package, expected_sha256=expected_sha256, expected_uid=expected_uid, retire=True
        )

    def quiesce_package(self, package, *, expected_sha256, expected_uid):
        """Trusted host: drain services and deny admission until explicit resume.

        Preserves enrollment/UID. The host must also stop its app processes.
        This gate belongs to this broker, not to every host on the machine.
        """
        return self._drain_package(
            package, expected_sha256=expected_sha256, expected_uid=expected_uid, retire=False
        )

    def resume_package(self, package, *, expected_sha256, expected_uid):
        with self.lock:
            if self.closed:
                raise RuntimeError('Broker closed')
            record = self.registry.identity(package)
            if record['sha256'] != expected_sha256 or record['uid'] != expected_uid:
                raise PermissionError('Package identity changed before resume')
            if any(key[0] == package for key in self.pending_retirements):
                raise RuntimeError('Package cleanup is incomplete')
            resumed = package in self.quiesced
            self.quiesced.discard(package)
            return resumed

    def _drain_package(self, package, *, expected_sha256, expected_uid, retire):
        with self.lock:
            if self.closed:
                raise RuntimeError('Broker closed')
            if retire:
                retired = self.registry.retire(
                    package, expected_sha256=expected_sha256, expected_uid=expected_uid
                )
            else:
                record = self.registry.identity(package)
                if record['sha256'] != expected_sha256 or record['uid'] != expected_uid:
                    raise PermissionError('Package identity changed before quiescence')
                self.quiesced.add(package)
                self.ready.notify_all()
                retired = True
            for key in list(self.service_restarts):
                if key[:2] == (package, expected_sha256):
                    del self.service_restarts[key]
            self.ready.notify_all()
            sessions = [
                (channel, self.sessions[channel])
                for channel, identity in self.identities.items()
                if identity.package == package
                and identity.uid == expected_uid
                and identity.sha256 == expected_sha256
            ]
            processes = [
                process
                for process in self.processes
                if process.key[:2] == (package, expected_sha256)
            ]
            retirement_key = (package, expected_uid, expected_sha256)
            processes = list(set(processes) | self.pending_retirements.get(retirement_key, set()))
            self.pending_retirements[retirement_key] = set(processes)
            for process in processes:
                self.processes.discard(process)
                if self.targets.get(process.key) is process:
                    self.targets.pop(process.key)
        self.pending_intent_dispatch.close()
        for channel, _ in sessions:
            try:
                channel.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        failures = []
        for process in processes:
            try:
                process.close()
                with self.lock:
                    self.pending_retirements[retirement_key].discard(process)
            except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
                failures.append(error)
        for _, worker in sessions:
            worker.join(timeout=12)
            if worker.is_alive():
                failures.append(RuntimeError('Retired package control worker did not stop'))
        with self.lock:
            if not failures:
                self.pending_retirements.pop(retirement_key, None)
                if retire:
                    self.quiesced.discard(package)
        if failures:
            if retire:
                raise RetirementCleanupError(
                    'Package revoked; retirement cleanup is incomplete'
                ) from failures[0]
            raise QuiescenceCleanupError('Package quiesced; cleanup is incomplete') from failures[0]
        return retired

    def _resolve(self, identity, request):
        with self.lock:
            target = request.get('package') if isinstance(request, dict) else None
            if (
                identity.package in self.quiesced
                or isinstance(target, str)
                and target in self.quiesced
            ):
                raise PermissionError('Package is quiesced for update')
        if isinstance(request, dict) and request.get('op') in (
            'open_authenticator',
            'open_add_account',
        ):
            if set(request) != {'op', 'type', 'expected_uid', 'expected_sha256'}:
                raise ValueError('Invalid authenticator admission fields')
            if (
                type(request['expected_uid']) is not int
                or not 10000 <= request['expected_uid'] <= 19999
                or not isinstance(request['expected_sha256'], str)
                or len(request['expected_sha256']) != 64
            ):
                raise ValueError('Invalid authenticator generation')
            with self.lock:
                resolver = (
                    self.registry.resolve_add_account
                    if request['op'] == 'open_add_account'
                    else self.registry.resolve_authenticator
                )
                result = resolver(identity.uid, identity.sha256, request['type'])
                if identity.package in self.quiesced or result['package'] in self.quiesced:
                    raise PermissionError('Authenticator caller or owner unavailable')
                if (
                    result['uid'] != request['expected_uid']
                    or result['apk_sha256'] != request['expected_sha256']
                ):
                    raise PermissionError('Authenticator generation changed')
                return {
                    key: result[key]
                    for key in ('package', 'uid', 'apk_sha256', 'component', 'process')
                }
        if isinstance(request, dict) and request.get('op') == 'resolve_service_action':
            if not {'op', 'package', 'action'} <= set(request) or set(request) - {
                'op',
                'package',
                'action',
                'mime',
                'scheme',
                'has_data',
                'categories',
            }:
                raise ValueError('Invalid service action fields')
            result = self.registry.resolve_service_action(
                identity.uid,
                request['package'],
                request['action'],
                caller_sha256=identity.sha256,
                **{
                    key: request[key]
                    for key in ('mime', 'scheme', 'has_data', 'categories')
                    if key in request
                },
            )
            service = result['service']
            return dict(
                package=result['package'],
                uid=result['uid'],
                apk_sha256=result['apk_sha256'],
                component=service['name'],
                process=service.get('process') or result['package'],
            )
        if not isinstance(request, dict) or set(request) != {'op', 'package', 'component'}:
            raise ValueError('Invalid broker request fields')
        if request['op'] not in (
            'resolve_service',
            'open_service',
            'open_existing_service',
            'open_service_for_start',
            'open_service_for_stop',
        ):
            raise ValueError('Unsupported broker operation')
        if any(
            not isinstance(request[key], str) or not 1 <= len(request[key]) <= 1024
            for key in ('package', 'component')
        ):
            raise ValueError('Invalid service identity')
        result = self.registry.authorize_service(
            identity.uid, request['package'], request['component'], caller_sha256=identity.sha256
        )
        service = result['service']
        return dict(
            package=result['package'],
            uid=result['uid'],
            apk_sha256=result['apk_sha256'],
            component=service['name'],
            process=service.get('process') or result['package'],
        )

    def _resolve_receiver(self, identity, request):
        if (
            not isinstance(request, dict)
            or not {'op', 'package', 'component'} <= set(request)
            or set(request)
            - {'op', 'package', 'component', 'recipient_permission', 'expected_sha256'}
            or request['op'] not in ('resolve_receiver', 'open_receiver')
        ):
            raise ValueError('Invalid receiver resolution fields')
        if any(
            not isinstance(request[key], str)
            or not 1 <= len(request[key]) <= 1024
            or not NAME.fullmatch(request[key])
            for key in ('package', 'component')
        ):
            raise ValueError('Invalid receiver identity')
        with self.lock:
            if (
                self.closed
                or identity.package in self.quiesced
                or request['package'] in self.quiesced
            ):
                raise PermissionError('Broker closed or package quiesced')
            try:
                target = self.registry.identity(request['package'])
            except PermissionError:
                # Authorize the sender before reporting an absent recipient.
                # A concurrent installation still fails the generation check.
                target = {'sha256': None}
            if 'expected_sha256' in request:
                expected = request['expected_sha256']
                if (
                    request['op'] != 'open_receiver'
                    or not isinstance(expected, str)
                    or len(expected) != 64
                    or any(c not in '0123456789abcdef' for c in expected)
                ):
                    raise ValueError('Invalid receiver discovery generation')
                if target['sha256'] != expected:
                    raise PermissionError('Receiver changed after discovery')
            result = self.registry.authorize_receiver(
                identity.uid,
                request['package'],
                request['component'],
                caller_sha256=identity.sha256,
                target_sha256=target['sha256'],
                recipient_permission=request.get('recipient_permission'),
            )
            receiver = result['receiver']
            return dict(
                package=result['package'],
                uid=result['uid'],
                apk_sha256=result['apk_sha256'],
                component=receiver['name'],
                process=receiver.get('process') or result['package'],
            )

    def _register_dynamic_receiver(self, server, identity, request):
        record, size = validate_registration(request)
        with self.lock:
            key = (identity.package, identity.sha256, identity.process_name)
            if (
                self.closed
                or identity.package in self.quiesced
                or identity.process_name is None
                or self.live_processes.get(key) is not server
            ):
                raise PermissionError('Dynamic receiver requires a live host-assigned process')
            self.registry.authenticate_process(identity.package, identity.uid, identity.sha256)
            registrations = self.dynamic_receivers.get(server, {})
            owners = {item['receiver'] for item, _ in registrations.values()}
            if (
                len(registrations) >= 1024
                or record['receiver'] not in owners
                and len(owners) >= 256
                or sum(cost for _, cost in registrations.values()) + size > 262144
            ):
                raise ValueError('Dynamic receiver session capacity')
            token = uuid.uuid4().hex
            registrations[token] = (record, size)
            self.dynamic_receivers[server] = registrations
            return token

    def _query_dynamic_receivers(self, server, identity, request):
        if not {'op', 'action'} <= set(request) or set(request) - {
            'op',
            'action',
            'package',
            'mime',
            'scheme',
            'has_data',
            'categories',
            'recipient_permission',
        }:
            raise ValueError('Invalid dynamic receiver query fields')
        action, package = request['action'], request.get('package')
        if not isinstance(action, str) or not 1 <= len(action) <= 4096:
            raise ValueError('Invalid dynamic receiver action')
        if package is not None and (
            not isinstance(package, str) or len(package) > 1024 or not NAME.fullmatch(package)
        ):
            raise ValueError('Invalid dynamic receiver package')
        query = validate_query(
            **{
                key: request[key]
                for key in ('mime', 'scheme', 'has_data', 'categories')
                if key in request
            }
        )
        with self.lock:
            if self.closed or identity.package in self.quiesced:
                raise PermissionError('Dynamic receiver caller unavailable')
            candidates = []
            for owner, registrations in self.dynamic_receivers.items():
                target = self.identities.get(owner)
                if (
                    owner is server
                    or target is None
                    or target.package in self.quiesced
                    or package is not None
                    and target.package != package
                ):
                    continue
                if (
                    self.live_processes.get((target.package, target.sha256, target.process_name))
                    is not owner
                ):
                    continue
                for token, (record, _) in registrations.items():
                    rule = record['filter']
                    if action not in rule['action'] or not matches(rule, *query):
                        continue
                    candidates.append(
                        dict(
                            package=target.package,
                            uid=target.uid,
                            apk_sha256=target.sha256,
                            process=target.process_name,
                            receiver=record['receiver'],
                            registration=token,
                            priority=rule['priority'],
                            permission=record['permission'],
                            flags=record['flags'],
                        )
                    )
            allowed = self.registry.filter_dynamic_receivers(
                identity.uid, identity.sha256, candidates, request.get('recipient_permission')
            )
            # One callback per receiver owner, even if several filters matched.
            chosen = {}
            for item in sorted(allowed, key=lambda item: (-item['priority'], item['registration'])):
                key = (item['package'], item['apk_sha256'], item['process'], item['receiver'])
                if key not in chosen:
                    chosen[key] = {
                        name: value
                        for name, value in item.items()
                        if name not in ('permission', 'flags')
                    }
            if (
                len(chosen) > 256
                or len(json.dumps({'ok': True, 'receivers': list(chosen.values())}).encode())
                > 60000
            ):
                raise ValueError('Dynamic receiver query fanout limit')
            return list(chosen.values())

    def _resolve_dynamic_receiver(self, server, identity, request):
        """Revalidate a discovered registration; callers must also pin admission leases."""
        required = {'op', 'action', 'registration', 'expected_sha256'}
        if not required <= set(request) or request['op'] not in (
            'resolve_dynamic_receiver',
            'open_dynamic_receiver',
        ):
            raise ValueError('Invalid dynamic receiver admission fields')
        token, expected = request['registration'], request['expected_sha256']
        for value, length in ((token, 32), (expected, 64)):
            if (
                not isinstance(value, str)
                or len(value) != length
                or any(c not in '0123456789abcdef' for c in value)
            ):
                raise ValueError('Invalid dynamic receiver admission identity')
        query = {
            key: value
            for key, value in request.items()
            if key not in ('registration', 'expected_sha256')
        }
        query['op'] = 'query_dynamic_receivers'
        with self.lock:
            candidates = self._query_dynamic_receivers(server, identity, query)
            matches = [item for item in candidates if item['registration'] == token]
            if len(matches) != 1 or matches[0]['apk_sha256'] != expected:
                raise PermissionError(
                    'Dynamic receiver registration changed or no longer permitted'
                )
            receiver = matches[0]
            owner = self.live_processes.get(
                (receiver['package'], receiver['apk_sha256'], receiver['process'])
            )
            if owner is None or token not in self.dynamic_receivers.get(owner, {}):
                raise PermissionError('Dynamic receiver process unavailable')
            return receiver

    def _unregister_dynamic_receiver(self, server, request):
        if set(request) != {'op', 'receiver'} or not isinstance(request['receiver'], str):
            raise ValueError('Invalid dynamic receiver removal fields')
        with self.lock:
            registrations = self.dynamic_receivers.get(server, {})
            tokens = [
                token
                for token, (item, _) in registrations.items()
                if item['receiver'] == request['receiver']
            ]
            if not tokens:
                raise ValueError('Unknown session dynamic receiver')
            for token in tokens:
                del registrations[token]
            if not registrations:
                self.dynamic_receivers.pop(server, None)
            self.ready.notify_all()

    def _queue_service_restart(self, worker, identity):
        """Called under lock before teardown clears started state."""
        attempt = getattr(worker, 'restart_attempt', 0)
        mode = getattr(worker, 'restart_mode', None)
        state = getattr(worker, 'start_state', None)
        redeliver = mode == 3 and isinstance(state, ServiceStartState) and bool(state.intents)
        if (
            self.closed
            or identity.package in self.quiesced
            or self.targets.get(worker.key) is not worker
            or not worker.started
            or not (mode == 1 or redeliver)
            or getattr(worker, 'restart_suppressed', False)
            or not isinstance(getattr(worker, 'service', None), dict)
            or attempt >= len(self.restart_delays)
        ):
            return None
        pending = (worker, identity, attempt + 1)
        self.service_restarts[worker.key] = pending
        return pending

    def _restart_service(self, pending):
        from .service_process import ServiceProcess

        old, identity, attempt = pending
        replacement = None
        try:
            # Reap the entire old process group before reusing its Android identity.
            old.close()
            with self.lock:
                deadline = time.monotonic() + self.restart_delays[attempt - 1]
                while self.service_restarts.get(old.key) is pending and not self.closed:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        break
                    self.ready.wait(remaining)
                if self.closed or self.service_restarts.get(old.key) is not pending:
                    return
                if (
                    getattr(old, 'restart_suppressed', False)
                    or identity.package in self.quiesced
                    or old.key in self.targets
                    or any(key[0] == identity.package for key in self.pending_retirements)
                ):
                    return
                service = self._resolve(
                    identity,
                    dict(op='resolve_service', package=identity.package, component=old.key[2]),
                )
                if service != old.service or sum(not item.closed for item in self.processes) >= 16:
                    return
                replacement = ServiceProcess(self, service)
                replacement.restart_attempt = attempt
                frame = old.start_state.restart_frame() if old.restart_mode == 3 else None
                if frame is not None:
                    replacement.last_accepted_start_id = max(old.start_state.intents)
                self.targets[old.key] = replacement
                self.processes.add(replacement)
                del self.service_restarts[old.key]
            # The worker checks suppression on its main thread before queuing
            # null-intent delivery, covering stop between publication and control.
            if frame is None:
                replacement.restart_sticky()
            else:
                replacement.restart_redelivery(frame)
        except (OSError, EOFError, ValueError, RuntimeError):
            if replacement is not None:
                with self.lock:
                    replacement.restart_suppressed = True
                    if self.targets.get(old.key) is replacement:
                        del self.targets[old.key]
                    self.processes.discard(replacement)
                replacement.close()
        finally:
            with self.lock:
                if self.service_restarts.get(old.key) is pending:
                    del self.service_restarts[old.key]
                self.restart_threads.discard(threading.current_thread())
                self.ready.notify_all()

    def _revoke_start_tickets(self, server, lease=None):
        with self.lock:
            for ticket, entry in list(self.start_tickets.items()):
                if entry[0] is server and (lease is None or entry[1] == lease):
                    del self.start_tickets[ticket]

    def _release(self, process):
        with self.lock:
            process.references -= 1
            last = process.references == 0 and not process.started
            if last:
                if self.targets.get(process.key) is process:
                    del self.targets[process.key]
        if last:
            process.close()
            with self.lock:
                self.processes.discard(process)

    def _serve(self, server, identity):
        owned = {}
        authenticator_channels = {}
        activity_channels = {}
        account_watches = {}

        def revoke_activity(token):
            channel = activity_channels.pop(token, None)
            if channel is not None:
                try:
                    channel.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                channel.close()

        def revoke_authenticator(lease):
            channel = authenticator_channels.pop(lease, None)
            if channel is not None:
                try:
                    channel.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                channel.close()

        try:
            while True:
                request = read_frame(server)
                process = None
                endpoint = None
                lease = None
                restart_cancelled = False
                try:
                    if isinstance(request, dict) and request.get('op') == 'terminate_process':
                        if (
                            set(request) != {'op', 'pid'}
                            or type(request['pid']) is not int
                            or not 1 <= request['pid'] <= 0x7FFFFFFF
                        ):
                            raise ValueError('Invalid process termination request')
                        with self.lock:
                            if (
                                self.closed
                                or identity.package in self.quiesced
                                or self.process_terminator is None
                                or self.live_processes.get(
                                    (identity.package, identity.sha256, identity.process_name)
                                )
                                is not server
                            ):
                                raise PermissionError('Process caller unavailable')
                        self.process_terminator(identity, request['pid'])
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'open_dynamic_receiver':
                        from .live_receiver import LiveReceiverDelivery

                        with self.lock:
                            if (
                                self.closed
                                or len(owned) >= 4
                                or sum(not item.closed for item in self.processes) >= 16
                            ):
                                raise ValueError('Dynamic receiver admission capacity')
                            with self.registry.lease(identity.package, identity.sha256):
                                deadline = time.monotonic() + 5
                                while True:
                                    receiver = self._resolve_dynamic_receiver(
                                        server, identity, request
                                    )
                                    owner = self.live_processes.get(
                                        (
                                            receiver['package'],
                                            receiver['apk_sha256'],
                                            receiver['process'],
                                        )
                                    )
                                    host = self.receiver_hosts.get(owner)
                                    if host is not None:
                                        break
                                    remaining = deadline - time.monotonic()
                                    if remaining <= 0:
                                        raise RuntimeError(
                                            'Dynamic receiver startup deadline exceeded'
                                        )
                                    self.ready.wait(remaining)
                                if (
                                    self.closed
                                    or sum(not item.closed for item in self.processes) >= 16
                                ):
                                    raise ValueError('Dynamic receiver admission capacity')
                                process = LiveReceiverDelivery(
                                    self, receiver, identity, request, host, server=server
                                )
                            process.references = 1
                            self.processes.add(process)
                            lease = uuid.uuid4().hex
                            owned[lease] = process
                            endpoint = process.open_endpoint(identity.uid)
                        reply = {'ok': True, 'receiver': receiver, 'lease': lease}
                        try:
                            write_packet(server, reply, (endpoint.fileno(),))
                        finally:
                            endpoint.close()
                        continue
                    if (
                        isinstance(request, dict)
                        and request.get('op') == 'resolve_dynamic_receiver'
                    ):
                        receiver = self._resolve_dynamic_receiver(server, identity, request)
                        write_frame(server, {'ok': True, 'receiver': receiver})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'query_dynamic_receivers':
                        receivers = self._query_dynamic_receivers(server, identity, request)
                        write_frame(server, {'ok': True, 'receivers': receivers})
                        continue
                    if (
                        isinstance(request, dict)
                        and request.get('op') == 'register_dynamic_receiver'
                    ):
                        token = self._register_dynamic_receiver(server, identity, request)
                        write_frame(server, {'ok': True, 'registration': token})
                        continue
                    if (
                        isinstance(request, dict)
                        and request.get('op') == 'unregister_dynamic_receiver'
                    ):
                        self._unregister_dynamic_receiver(server, request)
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'register_receiver_host':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid receiver host registration fields')
                        with self.lock:
                            if (
                                self.closed
                                or identity.package in self.quiesced
                                or identity.process_name is None
                            ):
                                raise PermissionError(
                                    'Receiver host requires a live host-assigned process'
                                )
                            if server in self.receiver_hosts:
                                raise ValueError('Receiver host already registered')
                            self.registry.launch_apk(identity.package, identity.sha256)
                            host, guest = socket.socketpair()
                            self.receiver_hosts[server] = host
                            self.ready.notify_all()
                        try:
                            write_packet(
                                server,
                                {'ok': True, 'process': identity.process_name},
                                (guest.fileno(),),
                            )
                        finally:
                            guest.close()
                        continue
                    if isinstance(request, dict) and request.get('op') == 'service_restart_allowed':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid service restart authorization')
                        with self.lock:
                            worker = identity.worker
                            if (
                                self.closed
                                or worker is None
                                or worker.closed
                                or worker.started
                                or self.targets.get(worker.key) is not worker
                                or identity.package in self.quiesced
                                or getattr(worker, 'last_start_id', 0) != 0
                                or getattr(worker, 'restart_permit_used', False)
                                or getattr(worker, 'restart_suppressed', False)
                            ):
                                raise PermissionError('Fresh unsuppressed worker required')
                            worker.restart_permit_used = True
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'service_start_accepted':
                        if (
                            set(request) != {'op', 'ticket', 'start_id'}
                            or type(request['ticket']) is not int
                            or not 1 <= request['ticket'] <= 9223372036854775807
                            or type(request['start_id']) is not int
                            or not 1 <= request['start_id'] <= 2147483647
                        ):
                            raise ValueError('Invalid service start ticket')
                        with self.lock:
                            worker = identity.worker
                            entry = self.start_tickets.get(request['ticket'])
                            if (
                                self.closed
                                or worker is None
                                or worker.closed
                                or not worker.started
                                or self.targets.get(worker.key) is not worker
                                or identity.package in self.quiesced
                                or entry is None
                                or entry[2] is not worker
                            ):
                                raise PermissionError('Current worker start ticket required')
                            if request['start_id'] <= max(
                                getattr(worker, 'last_accepted_start_id', 0),
                                getattr(worker, 'last_start_id', 0),
                            ):
                                raise ValueError('Stale service start acceptance')
                            worker.restart_attempt = 0
                            worker.last_accepted_start_id = request['start_id']
                            del self.start_tickets[request['ticket']]
                            worker.last_start_ticket = request['ticket']
                            # Main-thread acceptance precedes onStartCommand completion.
                            # Its old restart result must not authorize this new start.
                            worker.restart_mode = None
                            if getattr(worker, 'restart_stop_token', None) is entry[3]:
                                worker.restart_suppressed = False
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'service_start_finished':
                        if (
                            set(request) != {'op', 'start_id'}
                            or type(request['start_id']) is not int
                            or not 1 <= request['start_id'] <= 2147483647
                        ):
                            raise ValueError('Invalid service work completion')
                        with self.lock:
                            worker = identity.worker
                            if (
                                self.closed
                                or worker is None
                                or worker.closed
                                or not worker.started
                                or self.targets.get(worker.key) is not worker
                            ):
                                raise PermissionError('Current started worker session required')
                            if request['start_id'] > max(
                                worker.last_accepted_start_id, worker.last_start_id
                            ):
                                raise ValueError('Unaccepted service work completion')
                            worker.last_finished_start_id = max(
                                worker.last_finished_start_id, request['start_id']
                            )
                            worker.start_state.finish(request['start_id'])
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'service_start_completed':
                        if (
                            set(request)
                            != (
                                {'op', 'start_id', 'restart_mode', 'intent'}
                                if request.get('restart_mode') == 3
                                else {'op', 'start_id', 'restart_mode'}
                            )
                            or type(request['start_id']) is not int
                            or not 1 <= request['start_id'] <= 2147483647
                            or type(request['restart_mode']) is not int
                            or not 0 <= request['restart_mode'] <= 3
                        ):
                            raise ValueError('Invalid service start completion')
                        worker = identity.worker
                        with self.lock:
                            if (
                                self.closed
                                or worker is None
                                or worker.closed
                                or not worker.started
                                or self.targets.get(worker.key) is not worker
                            ):
                                raise PermissionError('Current started worker session required')
                            if request['start_id'] <= getattr(worker, 'last_start_id', 0):
                                raise ValueError('Stale service start completion')
                            if request['restart_mode'] == 3:
                                worker.start_state.retain(request['start_id'], request['intent'])
                            worker.last_start_id = request['start_id']
                            if request['start_id'] >= getattr(worker, 'last_accepted_start_id', 0):
                                worker.restart_mode = request['restart_mode']
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'service_started':
                        if (
                            set(request) != {'op', 'started'}
                            or type(request['started']) is not bool
                        ):
                            raise ValueError('Invalid service started state')
                        worker = identity.worker
                        with self.lock:
                            if (
                                self.closed
                                or worker is None
                                or worker.closed
                                or self.targets.get(worker.key) is not worker
                            ):
                                raise PermissionError('Current worker session required')
                            worker.started = request['started']
                            if not worker.started:
                                worker.restart_mode = None
                                state = getattr(worker, 'start_state', None)
                                if state is not None:
                                    state.clear()
                            retire = not worker.started and worker.references == 0
                            if retire:
                                self.targets.pop(worker.key)
                                self.processes.discard(worker)
                        # A worker may be waiting in stopSelf; reply before closing its control channel.
                        write_frame(server, {'ok': True})
                        if retire:
                            worker.close()
                        continue
                    if isinstance(request, dict) and request.get('op') == 'finish_receiver':
                        from .live_receiver import LiveReceiverDelivery
                        from .receiver_process import ReceiverProcess

                        if set(request) != {'op', 'lease'} or not isinstance(request['lease'], str):
                            raise ValueError('Invalid receiver completion fields')
                        receiver_worker = owned.get(request['lease'])
                        if not isinstance(receiver_worker, (ReceiverProcess, LiveReceiverDelivery)):
                            raise ValueError('Unknown receiver completion lease')
                        try:
                            try:
                                if isinstance(receiver_worker, LiveReceiverDelivery):
                                    receiver_worker.finish()
                                    status = 0
                                else:
                                    status = receiver_worker.process.wait(timeout=5)
                            except subprocess.TimeoutExpired as error:
                                raise RuntimeError('Receiver teardown deadline exceeded') from error
                        finally:
                            owned.pop(request['lease'])
                            self._release(receiver_worker)
                        if status != 0:
                            raise RuntimeError('Receiver worker exited unsuccessfully')
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'release_service',
                        'release_receiver',
                    ):
                        if (
                            set(request) != {'op', 'lease'}
                            or not isinstance(request['lease'], str)
                            or len(request['lease']) != 32
                        ):
                            raise ValueError('Invalid binding lease')
                        released = owned.pop(request['lease'], None)
                        if released is None:
                            raise ValueError('Unknown session binding lease')
                        revoke_authenticator(request['lease'])
                        self._revoke_start_tickets(server, request['lease'])
                        self._release(released)
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'check_permission':
                        if set(request) != {'op', 'permission', 'kind', 'subject'}:
                            raise ValueError('Invalid permission query fields')
                        result = self.registry.check_permission(
                            identity.uid,
                            identity.sha256,
                            request['permission'],
                            request['kind'],
                            request['subject'],
                        )
                        write_frame(server, {'ok': True, 'result': result})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'compare_signatures':
                        if set(request) != {'op', 'kind', 'first', 'second'}:
                            raise ValueError('Invalid signature comparison fields')
                        verdict = self.registry.compare_signatures(
                            identity.uid,
                            identity.sha256,
                            request['kind'],
                            request['first'],
                            request['second'],
                        )
                        write_frame(server, {'ok': True, 'result': verdict})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'watch_accounts':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid account watch fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Account watch caller unavailable')
                            if account_watches or len(self.account_watches) >= AccountWatch.LIMIT:
                                raise ValueError('Account watch capacity exceeded')
                            snapshot = self.registry.account_operation(
                                identity.uid, identity.sha256, {'action': 'snapshot'}
                            )
                            host, guest = socket.socketpair()
                            watch = AccountWatch(self, identity, host, snapshot['revision'])
                            token = uuid.uuid4().hex
                            account_watches[token] = watch
                            self.account_watches.add(watch)
                        try:
                            watch.start()
                            write_packet(
                                server,
                                {'ok': True, 'lease': token, 'revision': snapshot['revision']},
                                (guest.fileno(),),
                            )
                        except BaseException:
                            account_watches.pop(token, None)
                            watch.close()
                            raise
                        finally:
                            guest.close()
                        continue
                    if isinstance(request, dict) and request.get('op') == 'unwatch_accounts':
                        if (
                            set(request) != {'op', 'lease'}
                            or not isinstance(request['lease'], str)
                            or request['lease'] not in account_watches
                        ):
                            raise ValueError('Unknown account watch lease')
                        account_watches.pop(request['lease']).close()
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'account_operation':
                        if set(request) != {'op', 'request'}:
                            raise ValueError('Invalid account broker fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Account caller unavailable')
                            result = self.registry.account_operation(
                                identity.uid, identity.sha256, request['request']
                            )
                            if request['request']['action'] in ('add', 'remove') and result:
                                self.ready.notify_all()
                        write_frame(server, {'ok': True, 'result': result})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'release_activity':
                        if (
                            set(request) != {'op', 'lease'}
                            or not isinstance(request['lease'], str)
                            or request['lease'] not in activity_channels
                        ):
                            raise ValueError('Unknown activity channel lease')
                        revoke_activity(request['lease'])
                        write_frame(server, {'ok': True})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'open_activity':
                        if set(request) != {
                            'op',
                            'package',
                            'component',
                            'expected_uid',
                            'expected_sha256',
                        }:
                            raise ValueError('Invalid activity admission fields')
                        if type(request['expected_uid']) is not int or not isinstance(
                            request['expected_sha256'], str
                        ):
                            raise ValueError('Invalid activity generation expectation')
                        if self.activity_launcher is None or len(activity_channels) >= 4:
                            raise RuntimeError(
                                'Activity host unavailable or channel capacity reached'
                            )
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Activity caller unavailable')
                            activity = self.registry.resolve_activity(
                                identity.uid,
                                request['package'],
                                request['component'],
                                caller_sha256=identity.sha256,
                            )
                            if (
                                activity['package'] in self.quiesced
                                or activity['uid'] != request['expected_uid']
                                or activity['apk_sha256'] != request['expected_sha256']
                            ):
                                raise PermissionError('Activity target changed or unavailable')
                        # HostSession takes its own lock before opening a broker session.
                        # Never invoke it while holding the broker lock.
                        admitted = self.activity_launcher(identity, activity)
                        activity_lease = uuid.uuid4().hex
                        try:
                            with self.lock:
                                if (
                                    self.closed
                                    or identity.package in self.quiesced
                                    or activity['package'] in self.quiesced
                                ):
                                    raise PermissionError('Activity unavailable after admission')
                                if (
                                    self.registry.resolve_activity(
                                        identity.uid,
                                        request['package'],
                                        request['component'],
                                        caller_sha256=identity.sha256,
                                    )
                                    != activity
                                ):
                                    raise PermissionError('Activity changed during admission')
                                activity_channels[activity_lease] = admitted.dup()
                            write_packet(
                                server,
                                {'ok': True, 'activity': activity, 'lease': activity_lease},
                                (admitted.fileno(),),
                            )
                        except BaseException:
                            revoke_activity(activity_lease)
                            try:
                                admitted.shutdown(socket.SHUT_RDWR)
                            except OSError:
                                pass
                            raise
                        finally:
                            admitted.close()
                        continue
                    if isinstance(request, dict) and request.get('op') == 'resolve_activity':
                        if set(request) != {'op', 'package', 'component'}:
                            raise ValueError('Invalid activity resolution fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Activity caller unavailable')
                            activity = self.registry.resolve_activity(
                                identity.uid,
                                request['package'],
                                request['component'],
                                caller_sha256=identity.sha256,
                            )
                            if activity['package'] in self.quiesced:
                                raise PermissionError('Activity target unavailable')
                        write_frame(server, {'ok': True, 'activity': activity})
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'enqueue_job',
                        'schedule_job',
                        'cancel_job',
                        'get_job',
                        'list_job_ids',
                        'cancel_all_jobs',
                    ):
                        from .job_requests import handle

                        write_payload(
                            server,
                            json.dumps(
                                handle(self, identity, request),
                                ensure_ascii=False,
                                separators=(',', ':'),
                                allow_nan=False,
                            ).encode('utf-8'),
                        )
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'resolve_job',
                        'validate_job',
                    ):
                        validating = request['op'] == 'validate_job'
                        if set(request) != {'op', 'definition' if validating else 'component'}:
                            raise ValueError('Invalid job resolution fields')
                        definition = (
                            validate_job_definition(request['definition']) if validating else None
                        )
                        component = definition['component'] if validating else request['component']
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Job caller unavailable')
                            result = self.registry.authorize_job(
                                identity.uid, identity.sha256, component
                            )
                            service = result['service']
                            job = dict(
                                package=result['package'],
                                uid=result['uid'],
                                apk_sha256=result['apk_sha256'],
                                component=service['name'],
                                process=service.get('process') or result['package'],
                            )
                        # A policy snapshot only; this neither schedules nor opens a service.
                        reply = {'ok': True, 'job': job}
                        if validating:
                            reply['definition'] = definition
                        write_frame(server, reply)
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'resolve_authenticator',
                        'resolve_add_account',
                    ):
                        if set(request) != {'op', 'type'}:
                            raise ValueError('Invalid authenticator resolution fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Authenticator caller unavailable')
                            resolver = (
                                self.registry.resolve_add_account
                                if request['op'] == 'resolve_add_account'
                                else self.registry.resolve_authenticator
                            )
                            authenticator = resolver(identity.uid, identity.sha256, request['type'])
                            if authenticator['package'] in self.quiesced:
                                raise PermissionError('Authenticator owner unavailable')
                        write_frame(server, {'ok': True, 'authenticator': authenticator})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'list_authenticators':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid authenticator enumeration fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Authenticator caller unavailable')
                            authenticators = self.registry.list_authenticators(
                                identity.uid, identity.sha256
                            )
                        write_frame(server, {'ok': True, 'authenticators': authenticators})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'request_sync':
                        if set(request) != {'op', 'request'}:
                            raise ValueError('Invalid sync admission fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Sync caller unavailable')
                            queued = self.registry.request_sync(
                                identity.uid, identity.sha256, request['request']
                            )
                        write_frame(server, {'ok': True, 'queued': len(queued)})
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'list_sync_adapters',
                        'list_selected_sync_adapters',
                    ):
                        if set(request) != {'op'}:
                            raise ValueError('Invalid sync adapter enumeration fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Sync adapter caller unavailable')
                            adapters = self.registry.list_sync_adapters(
                                identity.uid,
                                identity.sha256,
                                selected_only=request['op'] == 'list_selected_sync_adapters',
                            )
                            adapters = [
                                item for item in adapters if item['package'] not in self.quiesced
                            ]
                        write_frame(server, {'ok': True, 'sync_adapters': adapters})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'resolve_provider':
                        if set(request) != {'op', 'authority', 'access'}:
                            raise ValueError('Invalid provider resolution fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('Provider caller unavailable')
                            resolved = self.registry.resolve_provider(
                                identity.uid,
                                identity.sha256,
                                request['authority'],
                                request['access'],
                            )
                            if resolved['package'] in self.quiesced:
                                raise PermissionError('Provider owner unavailable')
                            provider = {
                                key: resolved[key]
                                for key in ('package', 'uid', 'apk_sha256', 'authority', 'access')
                            }
                            provider.update(
                                component=resolved['provider']['name'],
                                process=resolved['provider'].get('process') or resolved['package'],
                            )
                            if len(json.dumps({'ok': True, 'provider': provider}).encode()) > 60000:
                                raise ValueError('Provider resolution reply size limit')
                        write_frame(server, {'ok': True, 'provider': provider})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'list_packages':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid package enumeration fields')
                        packages = self.registry.lookup_package(
                            identity.uid, identity.sha256, all_packages=True
                        )
                        write_frame(server, {'ok': True, 'packages': packages})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'lookup_package':
                        if set(request) not in ({'op', 'uid'}, {'op', 'package'}):
                            raise ValueError('Invalid package lookup fields')
                        packages = self.registry.lookup_package(
                            identity.uid,
                            identity.sha256,
                            **{key: value for key, value in request.items() if key != 'op'},
                        )
                        write_frame(server, {'ok': True, 'packages': packages})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'pending_intent_host':
                        if set(request) != {'op'}:
                            raise ValueError('Invalid PendingIntent host fields')
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('PendingIntent creator unavailable')
                            callback = self.pending_intent_dispatch.register(server, identity)
                        try:
                            write_packet(server, {'ok': True}, (callback.fileno(),))
                        finally:
                            callback.close()
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'pending_intent_live_obtain',
                        'pending_intent_references',
                    ):
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('PendingIntent creator unavailable')
                            if request['op'] == 'pending_intent_references':
                                if set(request) != {'op'}:
                                    raise ValueError('Invalid PendingIntent reference fields')
                                reply = dict(
                                    ok=True,
                                    references=self.pending_intent_dispatch.references(
                                        server, identity
                                    ),
                                )
                            else:
                                if set(request) != {
                                    'op',
                                    'kind',
                                    'request_code',
                                    'identity',
                                    'snapshot',
                                    'flags',
                                }:
                                    raise ValueError('Invalid live PendingIntent fields')
                                result = self.pending_intent_dispatch.obtain_live(
                                    server,
                                    identity,
                                    request['snapshot'],
                                    kind=request['kind'],
                                    request_code=request['request_code'],
                                    flags=request['flags'],
                                    identity=pending_intent_blob(request['identity'], 8192),
                                )
                                reply = dict(ok=True, pending_intent=result)
                        write_frame(server, reply)
                        continue
                    if isinstance(request, dict) and request.get('op') == 'pending_intent_send':
                        if set(request) != {'op', 'token', 'fill'}:
                            raise ValueError('Invalid PendingIntent send fields')
                        fill = (
                            None
                            if request['fill'] is None
                            else pending_intent_blob(request['fill'], 32768)
                        )
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('PendingIntent sender unavailable')
                            facts = PendingIntentStore(self.registry).inspect(
                                identity.uid, identity.sha256, request['token']
                            )
                            if facts['package'] in self.quiesced:
                                raise PermissionError('PendingIntent creator unavailable')
                            result_channel = self.pending_intent_dispatch.send(
                                identity, request['token'], fill
                            )
                        try:
                            write_packet(server, {'ok': True}, (result_channel.fileno(),))
                        finally:
                            result_channel.close()
                        continue
                    if isinstance(request, dict) and request.get('op') in (
                        'pending_intent_obtain',
                        'pending_intent_inspect',
                        'pending_intent_cancel',
                    ):
                        with self.lock:
                            if self.closed or identity.package in self.quiesced:
                                raise PermissionError('PendingIntent caller unavailable')
                            reply = pending_intent_request(self.registry, identity, request)
                            facts = reply.get('pending_intent')
                            if facts is not None and facts['package'] in self.quiesced:
                                raise PermissionError('PendingIntent creator unavailable')
                        write_frame(server, reply)
                        continue
                    if isinstance(request, dict) and request.get('op') == 'package_metadata_stream':
                        if set(request) != {'op', 'package'}:
                            raise ValueError('Invalid package metadata fields')
                        facts = self.registry.package_metadata(
                            identity.uid, identity.sha256, request['package'], stream=True
                        )
                        # Construct all bounded frames before sending: errors never leave a
                        # partially successful stream followed by an unrelated reply.
                        chunks, chunk, size = [], {}, 0
                        if facts is not None:
                            for key, value in facts.items():
                                cost = len(json.dumps({key: value}).encode('utf-8'))
                                if len(key) > 4096 or len(value) > 65536 or cost > 48000:
                                    raise ValueError('Package metadata entry exceeds frame limit')
                                if size + cost > 48000 or len(chunk) >= 4096:
                                    chunks.append(chunk)
                                    chunk, size = {}, 0
                                chunk[key] = value
                                size += cost
                            chunks.append(chunk)
                        else:
                            chunks.append(None)
                        if len(chunks) > 512:
                            raise ValueError('Package metadata stream limit')
                        replies = [
                            dict(ok=True, metadata=chunk, more=index + 1 < len(chunks))
                            for index, chunk in enumerate(chunks)
                        ]
                        if (
                            sum(len(json.dumps(reply).encode('utf-8')) for reply in replies)
                            > 8 * 1024 * 1024
                        ):
                            raise ValueError('Package metadata aggregate wire limit')
                        for reply in replies:
                            write_frame(server, reply)
                        continue
                    if isinstance(request, dict) and request.get('op') == 'package_metadata':
                        if set(request) != {'op', 'package'}:
                            raise ValueError('Invalid package metadata fields')
                        facts = self.registry.package_metadata(
                            identity.uid, identity.sha256, request['package']
                        )
                        write_frame(server, {'ok': True, 'metadata': facts})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'query_receivers':
                        if not {'op', 'package', 'action'} <= set(request) or set(request) - {
                            'op',
                            'package',
                            'action',
                            'mime',
                            'scheme',
                            'has_data',
                            'categories',
                            'recipient_permission',
                        }:
                            raise ValueError('Invalid receiver discovery fields')
                        if not isinstance(request['package'], str):
                            raise ValueError('Invalid receiver package')
                        with self.lock:
                            if (
                                self.closed
                                or identity.package in self.quiesced
                                or request['package'] in self.quiesced
                            ):
                                raise PermissionError('Broker closed or package quiesced')
                            receivers = self.registry.query_receivers(
                                identity.uid,
                                request['package'],
                                request['action'],
                                caller_sha256=identity.sha256,
                                **{
                                    key: request[key]
                                    for key in (
                                        'mime',
                                        'scheme',
                                        'has_data',
                                        'categories',
                                        'recipient_permission',
                                    )
                                    if key in request
                                },
                            )
                        write_frame(server, {'ok': True, 'receivers': receivers})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'resolve_receiver':
                        receiver = self._resolve_receiver(identity, request)
                        write_frame(server, {'ok': True, 'receiver': receiver})
                        continue
                    if isinstance(request, dict) and request.get('op') == 'open_receiver':
                        from .receiver_process import ReceiverProcess

                        with self.lock:
                            if self.closed or len(owned) >= 4:
                                raise ValueError('Receiver lease limit or broker closed')
                            if sum(not item.closed for item in self.processes) >= 16:
                                raise ValueError('Background process limit')
                            # Keep sender identity stable through target admission;
                            # ReceiverProcess reauthorizes after pinning the target.
                            with self.registry.lease(identity.package, identity.sha256):
                                deadline = time.monotonic() + 5
                                while True:
                                    receiver = self._resolve_receiver(identity, request)
                                    process_name = receiver['process']
                                    if process_name.startswith(':'):
                                        process_name = receiver['package'] + process_name
                                    process_key = (
                                        receiver['package'],
                                        receiver['apk_sha256'],
                                        process_name,
                                    )
                                    live = self.live_processes.get(process_key)
                                    host = (
                                        self.receiver_hosts.get(live) if live is not None else None
                                    )
                                    if live is None or host is not None:
                                        break
                                    remaining = deadline - time.monotonic()
                                    if remaining <= 0:
                                        raise RuntimeError(
                                            'Target receiver startup deadline exceeded'
                                        )
                                    # Release the broker lock so the target can
                                    # register, exit or be quiesced while we wait.
                                    self.ready.wait(remaining)
                                if (
                                    self.closed
                                    or sum(not item.closed for item in self.processes) >= 16
                                ):
                                    raise ValueError('Background process limit or broker closed')
                                if live is not None:
                                    from .live_receiver import LiveReceiverDelivery

                                    process = LiveReceiverDelivery(
                                        self, receiver, identity, request, host
                                    )
                                else:
                                    process = ReceiverProcess(self, receiver, identity, request)
                            process.references = 1
                            self.processes.add(process)
                            lease = uuid.uuid4().hex
                            owned[lease] = process
                        endpoint = process.open_endpoint(identity.uid)
                        try:
                            write_packet(
                                server,
                                {'ok': True, 'receiver': receiver, 'lease': lease},
                                (endpoint.fileno(),),
                            )
                        finally:
                            endpoint.close()
                        continue
                    service = self._resolve(identity, request)
                    process = None
                    endpoint = None
                    if request['op'] in (
                        'open_service',
                        'open_existing_service',
                        'open_authenticator',
                        'open_add_account',
                        'open_service_for_start',
                        'open_service_for_stop',
                    ):
                        from .service_process import ServiceProcess

                        with self.lock:
                            if self.closed or len(owned) >= 4:
                                raise ValueError('Service lease limit or broker closed')
                            # Retirement shares this lock; discard any resolution
                            # obtained before revocation before creating a worker.
                            service = self._resolve(identity, request)
                            key = (service['package'], service['apk_sha256'], service['component'])
                            if request['op'] in ('open_service_for_stop', 'open_service_for_start'):
                                restart_cancelled = self.service_restarts.pop(key, None) is not None
                                self.ready.notify_all()
                            if request['op'] == 'open_service':
                                deadline = time.monotonic() + 20
                                while key in self.service_restarts and not self.closed:
                                    remaining = deadline - time.monotonic()
                                    if remaining <= 0:
                                        raise RuntimeError('Service restart admission deadline')
                                    self.ready.wait(remaining)
                                if self.closed:
                                    raise RuntimeError('Broker closed')
                                service = self._resolve(identity, request)
                                if (
                                    service['package'],
                                    service['apk_sha256'],
                                    service['component'],
                                ) != key:
                                    raise PermissionError(
                                        'Service generation changed during restart'
                                    )
                            process = self.targets.get(key)
                            # Record lifecycle intent before endpoint delivery. A failed
                            # stop delivery must not permit a later crash restart.
                            if process is not None and request['op'] == 'open_service_for_stop':
                                process.restart_suppressed = True
                                process.restart_stop_token = object()
                            if (
                                process is None
                                or process.closed
                                or process.process.poll() is not None
                            ):
                                process = None
                                if request['op'] in (
                                    'open_service',
                                    'open_authenticator',
                                    'open_add_account',
                                    'open_service_for_start',
                                ):
                                    if sum(not item.closed for item in self.processes) >= 16:
                                        raise ValueError('Service process limit')
                                    process = ServiceProcess(self, service)
                                    self.targets[key] = process
                                    self.processes.add(process)
                            if process is not None:
                                if process.references >= 64:
                                    raise ValueError('Shared service client limit')
                                process.references += 1
                                lease = uuid.uuid4().hex
                                owned[lease] = process
                                if request['op'] == 'open_service_for_start':
                                    if self.next_start_ticket >= 9223372036854775807:
                                        raise RuntimeError('Service start ticket limit')
                                    self.next_start_ticket += 1
                                    ticket = self.next_start_ticket
                                    self.start_tickets[ticket] = (
                                        server,
                                        lease,
                                        process,
                                        getattr(process, 'restart_stop_token', None),
                                    )
                        if process is not None:
                            if request['op'] in ('open_authenticator', 'open_add_account'):
                                endpoint = (
                                    process.open_add_account_endpoint(
                                        identity.uid, identity.package, request['type']
                                    )
                                    if request['op'] == 'open_add_account'
                                    else process.open_authenticator_endpoint(identity.uid)
                                )
                                try:
                                    with self.lock:
                                        if (
                                            self.closed
                                            or process.closed
                                            or self._resolve(identity, request) != service
                                        ):
                                            raise PermissionError('Authenticator admission revoked')
                                        authenticator_channels[lease] = endpoint.dup()
                                except BaseException:
                                    try:
                                        endpoint.shutdown(socket.SHUT_RDWR)
                                    except OSError:
                                        pass
                                    endpoint.close()
                                    raise
                            elif request['op'] == 'open_service_for_start':
                                endpoint = process.open_start_endpoint(identity.uid, ticket)
                            else:
                                endpoint = process.open_endpoint(identity.uid)
                    reply = {'ok': True, 'service': service}
                    if (
                        request['op'] in ('open_existing_service', 'open_service_for_stop')
                        and process is None
                    ):
                        reply['absent'] = True
                        if request['op'] == 'open_service_for_stop' and restart_cancelled:
                            reply['restart_cancelled'] = True
                    if lease is not None:
                        reply['lease'] = lease
                except (
                    PermissionError,
                    FormatError,
                    ValueError,
                    OSError,
                    EOFError,
                    RuntimeError,
                    CanceledTokenError,
                ) as error:
                    if lease is not None:
                        revoke_authenticator(lease)
                        del owned[lease]
                        self._revoke_start_tickets(server, lease)
                        self._release(process)
                        process = None
                    reply = {'ok': False, 'error': str(error)}
                    if isinstance(error, CanceledTokenError):
                        reply['canceled'] = True
                    if isinstance(error, ServiceUnavailableError) and request.get('op') in (
                        'resolve_service',
                        'resolve_service_action',
                        'open_service_for_start',
                        'open_service_for_stop',
                    ):
                        reply['unavailable'] = True
                    if (
                        isinstance(error, ReceiverFilteredError)
                        and request.get('op') == 'open_receiver'
                    ):
                        reply['filtered'] = True
                if reply['ok'] and process is not None:
                    try:
                        write_packet(server, reply, (endpoint.fileno(),))
                    finally:
                        endpoint.close()
                else:
                    write_frame(server, reply)
        except (EOFError, OSError, ValueError, RecursionError):
            pass  # Malformed transport or peer closure revokes this session.
        finally:
            self._revoke_start_tickets(server)
            self.pending_intent_dispatch.revoke(server)
            for watch in account_watches.values():
                watch.close()
            with self.lock:
                self.dynamic_receivers.pop(server, None)
                receiver_host = self.receiver_hosts.pop(server, None)
            if receiver_host is not None:
                try:
                    receiver_host.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                receiver_host.close()
            worker = identity.worker
            pending_restart = None
            if worker is not None:
                with self.lock:
                    pending_restart = self._queue_service_restart(worker, identity)
                    worker.started = False
                    retire = worker.references == 0 and self.targets.get(worker.key) is worker
                    if retire or pending_restart is not None:
                        self.targets.pop(worker.key, None)
                    if retire:
                        self.processes.discard(worker)
                if retire:
                    worker.close()
            for token in list(activity_channels):
                revoke_activity(token)
            for lease in list(authenticator_channels):
                revoke_authenticator(lease)
            for process in owned.values():
                self._release(process)
            server.close()
            with self.lock:
                self.sessions.pop(server, None)
                self.identities.pop(server, None)
                process_key = (identity.package, identity.sha256, identity.process_name)
                if self.live_processes.get(process_key) is server:
                    del self.live_processes[process_key]
                if pending_restart is not None and not self.closed:
                    restart = threading.Thread(
                        target=self._restart_service,
                        args=(pending_restart,),
                        daemon=True,
                        name='shiraz-service-restart',
                    )
                    self.restart_threads.add(restart)
                    restart.start()
                self.ready.notify_all()

    def close(self):
        with self.lock:
            self.closed = True
            self.service_restarts.clear()
            restarts = list(self.restart_threads)
            self.ready.notify_all()
            sessions = list(self.sessions.items())
            processes = list(self.processes | set().union(*self.pending_retirements.values()))
            dispatches = list(self.job_dispatches)
            scheduler = self.job_scheduler
            sync_scheduler = self.sync_scheduler
            if sync_scheduler is not None:
                sync_scheduler.stop.set()
            if scheduler is not None:
                scheduler.stop.set()
        for channel, _ in sessions:
            try:
                channel.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        for process in processes:
            process.close()
        for _, worker in sessions:
            worker.join(timeout=12)
            if worker.is_alive():
                raise RuntimeError('Broker control worker did not stop')
        for restart in restarts:
            restart.join(timeout=15)
            if restart.is_alive():
                raise RuntimeError('Service restart worker did not stop')
        if scheduler is not None:
            scheduler.close()
        if sync_scheduler is not None:
            sync_scheduler.close()
        for dispatch in dispatches:
            dispatch.drain()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
