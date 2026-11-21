# SPDX-License-Identifier: GPL-3.0-only
"""One-shot receiver worker; admission is rechecked with its APK generation pinned."""

from .service_process import ServiceProcess


class ReceiverProcess(ServiceProcess):
    worker_module = 'shiraz.receiver_worker'
    worker_kind = 'receiver'
    lifetime = 30
    service_session = False

    def __init__(self, broker, receiver, identity, request):
        self.identity = identity
        self.request = dict(request)
        super().__init__(broker, receiver)

    def authorize(self, broker, receiver):
        current = broker._resolve_receiver(self.identity, self.request)
        if current != receiver:
            raise PermissionError('Receiver identity changed before launch')

    def open_endpoint(self, caller_uid):
        if caller_uid != self.identity.uid:
            raise PermissionError('Receiver caller identity mismatch')
        with self.lock:
            if self.closed:
                raise ValueError('Receiver worker already exited')
            return self.endpoint.dup()
