# SPDX-License-Identifier: GPL-3.0-only
"""Session-owned revision notices; account identities stay on authenticated reads."""

import select
import socket
import sqlite3
import threading

from .broker_wire import write_packet


class AccountWatch:
    LIMIT = 32

    def __init__(self, broker, identity, channel, revision):
        self.broker = broker
        self.identity = identity
        self.channel = channel
        self.revision = revision
        self.stopped = threading.Event()
        self.thread = threading.Thread(target=self._run, name='account-watch', daemon=True)
        channel.settimeout(1)

    def start(self):
        self.thread.start()

    def _run(self):
        try:
            while not self.stopped.is_set():
                # The notice endpoint is receive-only for the guest. EOF or any
                # inbound bytes revoke it instead of accepting guest identities.
                if select.select([self.channel], [], [], 0)[0]:
                    break
                with self.broker.lock:
                    if self.broker.closed or self.identity.package in self.broker.quiesced:
                        break
                    snapshot = self.broker.registry.account_operation(
                        self.identity.uid, self.identity.sha256, {'action': 'snapshot'}
                    )
                    revision = snapshot['revision']
                    if revision == self.revision:
                        # Broker mutations wake immediately; periodic revalidation
                        # also detects registry changes made by another host process.
                        self.broker.ready.wait(timeout=1)
                        continue
                    self.revision = revision
                write_packet(self.channel, {'revision': revision})
        except (OSError, ValueError, PermissionError, sqlite3.Error):
            pass  # A failed/stale subscription is revoked, never silently reauthorized.
        finally:
            self.stopped.set()
            self.channel.close()
            with self.broker.lock:
                self.broker.account_watches.discard(self)
                self.broker.ready.notify_all()

    def close(self):
        self.stopped.set()
        try:
            self.channel.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        with self.broker.lock:
            self.broker.ready.notify_all()
        if self.thread.ident is not None:
            self.thread.join(timeout=12)
            if self.thread.is_alive():
                raise RuntimeError('Account watch did not stop')
        else:
            self.channel.close()
            with self.broker.lock:
                self.broker.account_watches.discard(self)
