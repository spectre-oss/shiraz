# SPDX-License-Identifier: GPL-3.0-only
"""Broker-owned host worker and its private service socket lifetime."""

import array
import os
import re
import signal
import socket
import struct
import subprocess
import sys
import threading
import uuid
from contextlib import ExitStack
from pathlib import Path

from .broker_wire import HEADER, MAGIC, read_packet, write_payload
from .service_start_state import ServiceStartState

ROOT = Path(__file__).resolve().parents[2]


class ServiceProcess:
    worker_module = 'shiraz.service_worker'
    worker_kind = 'service'
    lifetime = None
    service_session = True
    watch_parent = False

    def authorize(self, broker, component):
        pass  # Service admission is performed by the broker.

    def session_opened(self, broker, service):
        pass

    def __init__(self, broker, service):
        registry = broker.registry
        self.lock = threading.Lock()
        self.closed = False
        self.references = 0
        self.started = False
        self.start_state = ServiceStartState()
        self.last_finished_start_id = 0
        self.last_start_id = 0
        self.last_start_ticket = 0
        self.last_accepted_start_id = 0
        self.service = dict(service)
        self.restart_attempt = 0
        self.restart_permit_used = False
        self.restart_mode = None
        self.restart_suppressed = False
        self.restart_stop_token = None
        self.key = (service['package'], service['apk_sha256'], service['component'])
        self.process = None
        self.lease = ExitStack()
        self.cleanup_complete = False
        self.control, self.endpoint = socket.socketpair()
        try:
            self.lease.enter_context(registry.lease(service['package'], service['apk_sha256']))
            self.authorize(broker, service)
            if hasattr(socket, 'SO_PASSCRED'):
                for channel in (self.control, self.endpoint):
                    channel.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
            work = ROOT / 'build' / (self.worker_kind + '-processes') / uuid.uuid4().hex
            work.mkdir(parents=True, mode=0o700)
            self.evidence = work
            env = dict(os.environ, PYTHONPATH=str(ROOT / 'src'))
            env.pop('SHIRAZ_BROKER_FD', None)
            env.pop('SHIRAZ_LAUNCHER_APPS', None)
            env.pop('SHIRAZ_LAUNCHER_INBOX', None)
            with broker.open_session(
                service['package'],
                service['apk_sha256'],
                worker=self if self.service_session else None,
                process_name=service['process'],
            ) as session:
                self.session_opened(broker, service)
                command = [
                    sys.executable,
                    '-m',
                    self.worker_module,
                    str(registry.root),
                    service['package'],
                    service['apk_sha256'],
                    service['component'],
                    str(self.control.fileno()),
                    str(session.fileno()),
                ]
                extra_fds = ()
                if self.watch_parent:
                    command.extend(
                        (
                            str(os.getpid()),
                            str(self.lifetime_fd if self.lifetime_fd is not None else -1),
                        )
                    )
                    if self.lifetime_fd is not None:
                        extra_fds = (self.lifetime_fd,)
                with (
                    (work / 'stdout.log').open('wb') as out,
                    (work / 'stderr.log').open('wb') as err,
                ):
                    self.process = subprocess.Popen(
                        command,
                        cwd=ROOT,
                        env=env,
                        stdout=out,
                        stderr=err,
                        pass_fds=(self.control.fileno(), session.fileno(), *extra_fds),
                        start_new_session=True,
                    )
            self.reaper = threading.Thread(
                target=self._reap, daemon=True, name='shiraz-' + self.worker_kind + '-worker-reap'
            )
            self.reaper.start()
        except BaseException:
            if self.process is not None:
                self.close()
            else:
                self.control.close()
                self.endpoint.close()
                self.lease.close()
            raise

    def open_endpoint(self, caller_uid):
        return self._open_endpoint(caller_uid, authenticator=False)

    def open_start_endpoint(self, caller_uid, ticket):
        if type(ticket) is not int or not 1 <= ticket <= 9223372036854775807:
            raise ValueError('Invalid service start ticket')
        return self._open_endpoint(caller_uid, authenticator=False, start_ticket=ticket)

    def restart_sticky(self):
        """Trusted broker control: deliver one null-intent start to a fresh worker."""
        with self.lock:
            if self.closed:
                raise ValueError('Service worker already exited')
            self.endpoint.settimeout(10)
            payload = struct.pack('!I', 1)
            self.endpoint.sendall(HEADER.pack(MAGIC, len(payload), 0) + payload)
            reply, _ = read_packet(self.endpoint, idle_timeout=10, max_descriptors=0)
            if reply != {'restart_queued': True}:
                raise ValueError('Sticky service restart rejected')

    def dispatch_sync(self, claim):
        """Private coordinator entry point; caller retains its claim and process lease."""
        from .sync_dispatch_wire import encode

        target = claim['adapter']
        if (target['package'], target['apk_sha256'], target['component']) != self.key:
            raise PermissionError('Sync dispatch generation mismatch')
        payload = encode(claim)
        with self.lock:
            if self.closed:
                raise ValueError('Sync worker already exited')
            worker, result = socket.socketpair()
            try:
                self.endpoint.settimeout(10)
                write_payload(self.endpoint, payload, (worker.fileno(),))
                reply, _ = read_packet(self.endpoint, idle_timeout=10, max_descriptors=0)
                if reply != {'sync_admitted': True}:
                    raise ValueError('Sync dispatch admission rejected')
                return result
            except BaseException:
                result.close()
                raise
            finally:
                worker.close()

    def restart_redelivery(self, frame):
        """Private broker control; payload contains no descriptors or Binder handles."""
        with self.lock:
            if self.closed:
                raise ValueError('Service worker already exited')
            self.endpoint.settimeout(10)
            self.endpoint.sendall(HEADER.pack(MAGIC, len(frame), 0) + frame)
            reply, _ = read_packet(self.endpoint, idle_timeout=10, max_descriptors=0)
            if reply != {'restart_queued': True}:
                raise ValueError('Service redelivery restart rejected')

    def open_authenticator_endpoint(self, caller_uid):
        """Only the broker calls this after managed-type authorization."""
        return self._open_endpoint(caller_uid, authenticator=True)

    def open_add_account_endpoint(self, caller_uid, caller_package, account_type):
        if (
            not isinstance(caller_package, str)
            or not 1 <= len(caller_package) <= 1024
            or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.]*', caller_package)
            or not isinstance(account_type, str)
            or not 1 <= len(account_type) <= 1024
            or '\0' in account_type
        ):
            raise ValueError('Invalid account-add admission identity')
        fields = [value.encode('utf-8') for value in (caller_package, account_type)]
        payload = b''.join(struct.pack('!I', len(value)) + value for value in fields)
        return self._open_endpoint(caller_uid, authenticator=False, account_add=payload)

    def _open_endpoint(self, caller_uid, *, authenticator, start_ticket=None, account_add=None):
        if type(caller_uid) is not int or not 10000 <= caller_uid <= 19999:
            raise ValueError('Invalid service caller UID')
        with self.lock:
            if self.closed:
                raise ValueError('Service worker already exited')
            peer, client = socket.socketpair()
            try:
                if hasattr(socket, 'SO_PASSCRED'):
                    for channel in (peer, client):
                        channel.setsockopt(socket.SOL_SOCKET, socket.SO_PASSCRED, 1)
                payload = (
                    struct.pack('!II', caller_uid, 1)
                    if authenticator
                    else struct.pack('!I', caller_uid)
                )
                if start_ticket is not None:
                    payload = struct.pack('!IIQ', caller_uid, 2, start_ticket)
                if account_add is not None:
                    if authenticator or start_ticket is not None:
                        raise ValueError('Conflicting account-add admission')
                    payload = struct.pack('!III', 4, 1, caller_uid) + account_add
                header = HEADER.pack(MAGIC, len(payload), 1)
                self.endpoint.settimeout(10)
                sent = self.endpoint.sendmsg(
                    [header],
                    [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', [peer.fileno()]))],
                )
                if sent <= 0:
                    raise OSError('Service management channel closed')
                self.endpoint.sendall(header[sent:] + payload)
                # Retain both originals until the target owns its endpoint. In
                # particular, never leave both socket ends only in SCM_RIGHTS
                # queues while the service process is still starting.
                acknowledgement, _ = read_packet(self.endpoint, 10, max_descriptors=0)
                expected = (
                    {'uid': caller_uid, 'authenticator': True}
                    if authenticator
                    else {'uid': caller_uid}
                )
                if start_ticket is not None:
                    expected['start_ticket'] = start_ticket
                if account_add is not None:
                    expected['account_add'] = True
                if acknowledgement != expected:
                    raise ValueError('Invalid service admission acknowledgement')
                return client
            except BaseException:
                client.close()
                raise
            finally:
                peer.close()

    def _reap(self):
        try:
            self.process.wait(timeout=self.lifetime)
        except subprocess.TimeoutExpired:
            pass  # close() terminates the whole worker group before releasing its lease.
        # Wake an admission waiting under self.lock before acquiring that lock.
        try:
            self.control.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.close()

    def close(self):
        with self.lock:
            if self.cleanup_complete:
                return
            self.closed = True
            self.endpoint.close()
            try:
                self.control.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.control.close()
            killed_group = False
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                # The worker and ART child own a fresh process group. Kill both,
                # including a service stuck in onCreate/onDestroy.
                try:
                    os.killpg(self.process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                killed_group = True
                self.process.wait(timeout=3)
            # An unexpectedly terminated worker may leave its ART descendant.
            if self.process.returncode and not killed_group:
                try:
                    os.killpg(self.process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            self.lease.close()
            self.cleanup_complete = True
