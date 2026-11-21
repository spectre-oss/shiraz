# SPDX-License-Identifier: GPL-3.0-only
"""One broker-owned invocation; durable claims remain the dispatcher's responsibility."""

import socket
import threading
import time

from .broker_wire import read_packet, write_packet
from .job_definition import validate
from .service_process import ServiceProcess


class JobProcess(ServiceProcess):
    worker_module = 'shiraz.job_worker'
    worker_kind = 'job'
    service_session = False
    watch_parent = True

    def __init__(
        self,
        broker,
        service,
        definition,
        *,
        timeout=60,
        lifetime_fd=None,
        deadline_expired=False,
        work_claim=None,
    ):
        if type(timeout) is not int or not 1 <= timeout <= 600:
            raise ValueError('Job worker timeout must be 1..600 seconds')
        if type(deadline_expired) is not bool:
            raise ValueError('Invalid host job deadline state')
        self.deadline_expired = deadline_expired
        self.work_claim = None if work_claim is None else dict(work_claim)
        if self.work_claim is not None and (
            self.work_claim['owner_uid'] != service['uid']
            or self.work_claim['apk_sha256'] != service['apk_sha256']
            or self.work_claim['id'] != definition['id']
        ):
            raise PermissionError('Job work claim identity mismatch')
        self.lifetime_fd = lifetime_fd
        self.definition = validate(definition)
        if deadline_expired and self.definition['deadline_ms'] is None:
            raise ValueError('Deadline expiry requires a deadline')
        if self.definition['component'] != service['component']:
            raise PermissionError('Job worker component mismatch')
        self.broker = broker
        self.timeout = timeout
        self.done = threading.Event()
        self.cancelled = threading.Event()
        self.error = None
        self.retry = None
        self.stop_reason = 0
        self.definition_sent = threading.Event()
        self.stop_lock = threading.Lock()
        self.stop_requested = False
        self.terminal_cleanup_complete = False
        super().__init__(broker, service)

    def authorize(self, broker, service):
        # ServiceProcess already holds the generation lease here.
        current = broker.registry.authorize_job(
            service['uid'], service['apk_sha256'], service['component']
        )
        declared = current['service']
        expected = dict(
            package=current['package'],
            uid=current['uid'],
            apk_sha256=current['apk_sha256'],
            component=declared['name'],
            process=declared.get('process') or current['package'],
        )
        if expected != service:
            raise PermissionError('Job identity changed before launch')

    def _reap(self):
        deadline = time.monotonic() + self.timeout
        try:
            # Only the child owns this side after spawn. Keeping a parent copy
            # would hide EOF when the worker fails without a result packet.
            self.control.close()
            # Keep the reader's descriptor alive until recv returns. Concurrent
            # close() shuts down the shared socket but cannot recycle this fd
            # underneath the blocking select/recv operation.
            with self.endpoint.dup() as channel:
                write_packet(
                    channel,
                    dict(
                        version=1,
                        definition=self.definition,
                        deadline_expired=self.deadline_expired,
                    ),
                )
                self.definition_sent.set()
                while True:
                    reply, _ = read_packet(
                        channel, max(0.001, deadline - time.monotonic()), max_descriptors=0
                    )
                    if type(reply) is not dict or 'op' not in reply:
                        break
                    from .job_work_wire import handle

                    response = handle(self.broker.registry, self.work_claim, reply)
                    write_packet(channel, response)
            if (
                type(reply) is not dict
                or set(reply) != {'ok', 'retry', 'stop_reason'}
                or reply['ok'] is not True
                or type(reply['retry']) is not bool
                or type(reply['stop_reason']) is not int
                or reply['stop_reason'] not in (0, 9, 14)
            ):
                raise ValueError('Invalid job completion packet')
            status = self.process.wait(timeout=max(0.001, deadline - time.monotonic()))
            if status != 0:
                raise RuntimeError('Job worker failed after completion packet')
            self.retry = reply['retry']
            self.stop_reason = reply['stop_reason']
        except BaseException as error:
            self.error = error
        finally:
            drained = False
            try:
                super().close()
                self._drain_session()
                drained = True
                self.terminal_cleanup_complete = True
            except BaseException as error:
                self.error = error
            finally:
                with self.broker.lock:
                    if self.cleanup_complete and drained:
                        self.broker.processes.discard(self)
                self.done.set()

    def session_opened(self, broker, service):
        process = service['process']
        if process.startswith(':'):
            process = service['package'] + process
        self.session_channel = broker.live_processes[
            (service['package'], service['apk_sha256'], process)
        ]

    def _drain_session(self):
        # A dead process can still have broker-owned bindings being released.
        # Wait for this exact session, never a later worker's process identity.
        deadline = time.monotonic() + 12
        with self.broker.ready:
            while self.session_channel in self.broker.sessions:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise RuntimeError('Job broker session cleanup incomplete')
                self.broker.ready.wait(remaining)

    def stop_for_constraints(self):
        self._request_stop(9)

    def stop_for_foreground(self):
        self._request_stop(14)

    def _request_stop(self, reason):
        """Attempt one cooperative stop, then fall back to terminal cancellation.

        The reader remains sole owner of completion. A stop write is never retried
        after ambiguity; normal wait/cleanup still governs claim finalization.
        """
        with self.stop_lock:
            if self.stop_requested or self.done.is_set():
                return
            self.stop_requested = True
        try:
            if not self.definition_sent.wait(5):
                raise TimeoutError('Job definition delivery incomplete')
            with self.endpoint.dup() as channel:
                write_packet(channel, {'stop_reason': reason})
            if self.done.wait(5):
                return
        except (OSError, ValueError, EOFError, TimeoutError):
            pass
        self.close()

    def close(self):
        self.cancelled.set()
        try:
            self.endpoint.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        super().close()

    def wait(self, timeout=None):
        """Return retry only after packet, successful exit and process cleanup.

        An observation timeout leaves the worker running; callers retain this
        handle and must wait again or explicitly close it.
        """
        if not self.done.wait(timeout):
            raise TimeoutError('Job worker is still running')
        if self.error is not None:
            raise RuntimeError('Job execution failed; inspect worker evidence') from self.error
        if self.cancelled.is_set():
            raise RuntimeError('Job worker was cancelled')
        return self.retry

    def open_endpoint(self, caller_uid):
        raise RuntimeError('Job workers have no guest binding endpoint')

    def open_authenticator_endpoint(self, caller_uid):
        raise RuntimeError('Job workers have no authenticator endpoint')
