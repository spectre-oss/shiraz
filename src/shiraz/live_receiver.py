# SPDX-License-Identifier: GPL-3.0-only
"""One admitted broadcast relayed into an existing app, never a second process."""

import socket
import threading
from contextlib import ExitStack

from .broker_wire import read_packet, read_payload, write_packet, write_payload


class LiveReceiverDelivery:
    def __init__(self, broker, receiver, identity, request, host, *, server=None):
        dynamic = request['op'] == 'open_dynamic_receiver'
        component = 'dynamic:' + receiver['registration'] if dynamic else receiver['component']
        self.key = (receiver['package'], receiver['apk_sha256'], component)
        self.references = 0
        self.started = False
        self.closed = False
        self.cleanup_complete = False
        self.lock = threading.Lock()
        self.done = threading.Event()
        self.failure = None
        self.lease = ExitStack()
        self.control, self.endpoint = socket.socketpair()
        self.target, guest = socket.socketpair()
        self.thread = None
        try:
            self.lease.enter_context(
                broker.registry.lease(receiver['package'], receiver['apk_sha256'])
            )
            resolved = (
                broker._resolve_dynamic_receiver(server, identity, request)
                if dynamic
                else broker._resolve_receiver(identity, request)
            )
            if resolved != receiver:
                raise PermissionError('Live receiver identity changed')
            # Admission is serialized by broker.lock; the target reader never
            # needs that lock to consume this management message.
            try:
                write_payload(host, component.encode('utf-8'), (guest.fileno(),))
            except BaseException:
                # A partially written management frame must never be reused.
                for owner, channel in list(broker.receiver_hosts.items()):
                    if channel is host:
                        del broker.receiver_hosts[owner]
                try:
                    host.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                host.close()
                raise
            self.thread = threading.Thread(
                target=self._relay, name='shiraz-live-receiver', daemon=True
            )
            self.thread.start()
        except BaseException:
            self.thread = None
            self.close()
            raise
        finally:
            guest.close()

    def open_endpoint(self, caller_uid):
        with self.lock:
            if self.closed:
                raise RuntimeError('Live receiver delivery closed')
            return self.endpoint.dup()

    def _relay(self):
        try:
            payload, _ = read_payload(
                self.control, 15, max_descriptors=0, max_bytes=4 * 1024 * 1024
            )
            write_payload(self.target, payload, max_bytes=4 * 1024 * 1024)
            result, _ = read_packet(self.target, 15, max_descriptors=0)
            if result != {'ok': True}:
                raise ValueError('Live receiver did not complete')
            write_packet(self.control, result)
        except (OSError, EOFError, ValueError) as error:
            self.failure = error
        finally:
            self.done.set()
            self.close()

    def finish(self):
        if not self.done.wait(5):
            raise RuntimeError('Live receiver completion deadline')
        if self.failure is not None:
            raise RuntimeError('Live receiver delivery failed') from self.failure

    def close(self):
        with self.lock:
            if self.cleanup_complete:
                return
            self.closed = True
            for channel in (self.control, self.endpoint, self.target):
                try:
                    channel.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            # Let the relay leave its syscall before closing/reusing its fds.
        if self.thread is not None and self.thread is not threading.current_thread():
            self.thread.join(timeout=12)
            if self.thread.is_alive():
                raise RuntimeError('Live receiver relay did not stop')
        with self.lock:
            if not self.cleanup_complete:
                for channel in (self.control, self.endpoint, self.target):
                    channel.close()
                self.lease.close()
                self.cleanup_complete = True
