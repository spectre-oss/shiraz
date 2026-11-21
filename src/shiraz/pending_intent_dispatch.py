# SPDX-License-Identifier: GPL-3.0-only
"""Bounded creator-process dispatch. No success until the creator acknowledges.

Separate result sockets keep the caller's control channel available while the
creator performs normal broker operations. Missing creator hosts are explicit;
this component does not yet cold-start a process for a persisted token.
"""

import base64
import re
import secrets
import socket
import threading
from contextlib import contextmanager
from dataclasses import dataclass, field

from .broker_wire import read_packet, write_packet
from .pending_intent_store import IMMUTABLE, CanceledTokenError, PendingIntentStore

LIVE_PREFIX = b'SHIRAZ-LIVE-PI\0'


def live_reference(payload):
    if not payload.startswith(LIVE_PREFIX):
        return None
    value = payload[len(LIVE_PREFIX) :]
    if not re.fullmatch(b'[0-9a-f]{64}:[0-9a-f]{64}', value):
        raise ValueError('Invalid live PendingIntent reference')
    return tuple(part.decode('ascii') for part in value.split(b':'))


@dataclass
class _Host:
    identity: object
    channel: socket.socket
    lock: threading.Lock = field(default_factory=threading.Lock)
    closed: bool = False
    nonce: str = field(default_factory=lambda: secrets.token_hex(32))

    @contextmanager
    def admission(self):
        if not self.lock.acquire(timeout=10):
            raise RuntimeError('PendingIntent creator queue deadline')
        try:
            yield
        finally:
            self.lock.release()

    def close(self):
        self.closed = True
        try:
            self.channel.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.channel.close()


class PendingIntentDispatch:
    def __init__(self, registry):
        self.registry = registry
        self.store = PendingIntentStore(registry)
        self.lock = threading.RLock()
        self.hosts = {}
        self.threads = set()
        self.results = set()
        self.active_payloads = {}
        self.closed = False

    def register(self, session, identity):
        with self.lock:
            old = self.hosts.get(session)
            if (
                self.closed
                or old is not None
                and not old.closed
                or old is None
                and len(self.hosts) >= 128
            ):
                raise RuntimeError('PendingIntent host unavailable or already registered')
            # Require a still-current enabled authenticated caller even for registration.
            with self.registry.connect() as db:
                self.store._caller(db, identity.uid, identity.sha256)
            owner, client = socket.socketpair()
            owner.settimeout(10)
            self.hosts[session] = _Host(identity, owner)
            return client

    def obtain_live(self, session, caller, snapshot, **arguments):
        if not isinstance(snapshot, str) or not re.fullmatch('[0-9a-f]{64}', snapshot):
            raise ValueError('Invalid creator snapshot reference')
        with self.lock:
            host = self.hosts.get(session)
            if host is None or host.closed or host.identity != caller:
                raise RuntimeError('PendingIntent creator host unavailable')
            payload = LIVE_PREFIX + (host.nonce + ':' + snapshot).encode('ascii')
            return self.store.obtain(caller.uid, caller.sha256, payload=payload, **arguments)

    def references(self, session, identity):
        with self.lock:
            host = self.hosts.get(session)
            if host is None or host.closed or host.identity != identity:
                raise RuntimeError('PendingIntent creator host unavailable')
            with self.registry.connect() as db:
                self.store._caller(db, identity.uid, identity.sha256)
                payloads = [
                    bytes(row[0])
                    for row in db.execute(
                        'SELECT payload FROM pending_intent_tokens WHERE owner_uid=? AND owner_sha256=?',
                        (identity.uid, identity.sha256),
                    )
                ]
            payloads.extend(self.active_payloads.values())
            refs = {
                ref[1]
                for payload in payloads
                if (ref := live_reference(payload)) is not None and ref[0] == host.nonce
            }
            if len(refs) > 288:
                raise RuntimeError('PendingIntent snapshot reference limit')
            return sorted(refs)

    def revoke(self, session):
        with self.lock:
            host = self.hosts.pop(session, None)
        if host is not None:
            host.close()

    def send(self, identity, token, fill=None):
        if fill is not None and (not isinstance(fill, bytes) or not 1 <= len(fill) <= 32768):
            raise ValueError('PendingIntent fill-in limit')
        facts = self.store.inspect(identity.uid, identity.sha256, token)
        with self.lock:
            if self.closed or len(self.threads) >= 32:
                raise RuntimeError('PendingIntent dispatch capacity unavailable')
            with self.registry.connect() as db:
                row = self.store._resolve(db, identity.uid, identity.sha256, token)
                reference = live_reference(bytes(row['payload']))
            host = next(
                (
                    item
                    for item in self.hosts.values()
                    if not item.closed
                    and item.identity.uid == facts['uid']
                    and item.identity.sha256 == facts['sha256']
                    and (reference is None or reference[0] == item.nonce)
                ),
                None,
            )
            if host is None:
                raise RuntimeError('PendingIntent creator process is not available')
            result, client = socket.socketpair()
            result.settimeout(10)
            worker = threading.Thread(
                target=self._deliver,
                args=(host, identity, token, fill, result),
                name='shiraz-pending-intent',
                daemon=True,
            )
            self.results.add(result)
            self.threads.add(worker)
            try:
                worker.start()
            except BaseException:
                self.threads.remove(worker)
                self.results.remove(result)
                result.close()
                client.close()
                raise
            return client

    def _deliver(self, host, caller, token, fill, result):
        claimed = False
        try:
            with host.admission():
                if host.closed:
                    raise RuntimeError('PendingIntent creator process disconnected')
                with self.registry.lease(host.identity.package, host.identity.sha256):
                    with self.lock:
                        # GC and claim share this lock: a one-shot is pinned before its
                        # durable row disappears from the creator's reference list.
                        with self.registry.connect() as db:
                            row = self.store._resolve(db, caller.uid, caller.sha256, token)
                            reference = live_reference(bytes(row['payload']))
                            if reference is not None and reference[0] != host.nonce:
                                raise RuntimeError('PendingIntent creator session changed')
                        snapshot = self.store.claim_send(caller.uid, caller.sha256, token)
                        self.active_payloads[threading.current_thread()] = snapshot['payload']
                        claimed = True
                        reference = live_reference(snapshot['payload'])
                        if reference is not None and reference[0] != host.nonce:
                            raise RuntimeError('PendingIntent creator session changed during claim')
                    # Snapshot is host-authorized; never substitute receiver-supplied target.
                    payload = base64.b64encode(snapshot.pop('payload')).decode('ascii')
                    write_packet(
                        host.channel, dict(op='pending_intent_deliver', **snapshot, payload=payload)
                    )
                    write_packet(
                        host.channel,
                        dict(
                            fill=None
                            if fill is None or snapshot['flags'] & IMMUTABLE
                            else base64.b64encode(fill).decode('ascii')
                        ),
                    )
                    reply, _ = read_packet(host.channel, 10, max_descriptors=0)
                    if reply != {'ok': True}:
                        raise RuntimeError('PendingIntent creator did not acknowledge delivery')
            outcome = dict(ok=True)
        except CanceledTokenError:
            outcome = dict(ok=False, canceled=True, uncertain=False)
        except (OSError, EOFError, ValueError, RuntimeError, PermissionError):
            if claimed:
                # The operation may already have happened. Never replay it or
                # reuse a callback stream whose acknowledgment is uncertain.
                host.close()
            outcome = dict(ok=False, canceled=False, uncertain=claimed)
        try:
            write_packet(result, outcome)
            # Keep the endpoint live until NativeIpc has sampled peer identity.
            acknowledgment, _ = read_packet(result, 10, max_descriptors=0)
            if acknowledgment != {'received': True}:
                raise ValueError('PendingIntent result acknowledgment')
        except (OSError, EOFError, ValueError):
            pass
        finally:
            result.close()
            with self.lock:
                self.active_payloads.pop(threading.current_thread(), None)
                self.results.discard(result)
                self.threads.discard(threading.current_thread())

    def close(self):
        with self.lock:
            self.closed = True
            hosts, results, workers = (
                list(self.hosts.values()),
                list(self.results),
                list(self.threads),
            )
            self.hosts.clear()
        for host in hosts:
            host.close()
        for result in results:
            try:
                result.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        for worker in workers:
            worker.join(timeout=12)
            if worker.is_alive():
                raise RuntimeError('PendingIntent dispatch did not stop')
