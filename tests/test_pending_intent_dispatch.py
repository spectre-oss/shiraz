# SPDX-License-Identifier: GPL-3.0-only
import base64
import select
import socket
import unittest

import test_service_broker as fixtures

from shiraz.broker_wire import read_packet, write_packet
from shiraz.pending_intent_store import IMMUTABLE, ONE_SHOT, CanceledTokenError, PendingIntentStore


class PendingIntentDispatchTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.ServiceBrokerTests()
        self.fixture.setUp()
        self.owner = self.fixture.channel(self.fixture.caller)
        self.other = self.fixture.channel(self.fixture.other)
        self.store = PendingIntentStore(self.fixture.fixture.registry)
        self.callbacks = []

    def tearDown(self):
        for channel in self.callbacks:
            channel.close()
        self.fixture.tearDown()

    def live_token(self, reference, flags=0):
        reply = self.fixture.exchange(
            self.owner,
            dict(
                op='pending_intent_live_obtain',
                kind=0,
                request_code=77,
                identity=base64.b64encode(b'live-filter').decode(),
                snapshot=reference,
                flags=flags,
            ),
        )
        self.assertTrue(reply['ok'], reply)
        return reply['pending_intent']['token']

    def refs(self):
        reply = self.fixture.exchange(self.owner, dict(op='pending_intent_references'))
        self.assertTrue(reply['ok'], reply)
        return reply['references']

    def test_live_reference_retirement_and_inflight_pin(self):
        callback = self.register()
        first, second = 'a' * 64, 'b' * 64
        token = self.live_token(first)
        self.assertEqual(self.refs(), [first])
        with self.send(token) as result:
            delivery, _ = read_packet(callback, 3)
            read_packet(callback, 3)
            self.assertIn(first.encode(), base64.b64decode(delivery['payload']))
            self.assertEqual(self.live_token(second, 0x08000000), token)
            self.assertEqual(self.refs(), [first, second])
            self.store.cancel(self.fixture.caller['uid'], self.fixture.caller['apk_sha256'], token)
            self.assertEqual(self.refs(), [first])
            write_packet(callback, dict(ok=True))
            self.assertEqual(self.result(result), dict(ok=True))
        # A same-channel broker query may race result-worker retirement; wait for
        # that specific worker rather than treating acknowledgment as its exit.
        with self.fixture.broker.pending_intent_dispatch.lock:
            workers = list(self.fixture.broker.pending_intent_dispatch.threads)
        for worker in workers:
            worker.join(3)
            self.assertFalse(worker.is_alive())
        self.assertEqual(self.refs(), [])

    def test_live_reference_is_bound_to_creator_channel(self):
        self.register()
        token = self.live_token('c' * 64)
        other_owner = self.fixture.channel(self.fixture.caller)
        reply = self.fixture.exchange(other_owner, dict(op='pending_intent_references'))
        self.assertFalse(reply['ok'])
        self.owner.close()
        # Explicitly revoke the original authenticated session's host to make
        # the disconnected state deterministic before testing a new host.
        coordinator = self.fixture.broker.pending_intent_dispatch
        with coordinator.lock:
            for host in coordinator.hosts.values():
                host.close()
        self.owner = other_owner
        self.register()
        self.assertEqual(self.refs(), [])
        reply = self.fixture.exchange(
            self.other, dict(op='pending_intent_send', token=token, fill=None)
        )
        self.assertFalse(reply['ok'])

    def token(self, flags=0):
        caller = self.fixture.caller
        return self.store.obtain(
            caller['uid'],
            caller['apk_sha256'],
            kind=0,
            request_code=1,
            identity=b'filter',
            payload=b'creator-snapshot',
            flags=flags,
        )['token']

    def register(self):
        write_packet(self.owner, dict(op='pending_intent_host'))
        reply, descriptors = read_packet(self.owner, 3)
        self.assertEqual(reply, dict(ok=True))
        self.assertEqual(len(descriptors), 1)
        callback = socket.socket(fileno=descriptors[0])
        self.callbacks.append(callback)
        return callback

    def send(self, token, sender=None):
        sender = sender or self.other
        write_packet(
            sender,
            dict(op='pending_intent_send', token=token, fill=base64.b64encode(b'fill').decode()),
        )
        reply, descriptors = read_packet(sender, 3)
        self.assertEqual(reply, dict(ok=True))
        self.assertEqual(len(descriptors), 1)
        return socket.socket(fileno=descriptors[0])

    def result(self, channel):
        reply, descriptors = read_packet(channel, 3)
        self.assertFalse(descriptors)
        write_packet(channel, dict(received=True))
        return reply

    def test_creator_dispatch_and_reentrant_control_channel(self):
        callback = self.register()
        for flags in (0, IMMUTABLE):
            token = self.token(flags)
            # Self-send must release control before waiting on callback result.
            with self.send(token, sender=self.owner) as result:
                message, _ = read_packet(callback, 3)
                fill, _ = read_packet(callback, 3)
                self.assertEqual(message['package'], 'test.caller')
                self.assertEqual(message['uid'], self.fixture.caller['uid'])
                self.assertEqual(base64.b64decode(message['payload']), b'creator-snapshot')
                self.assertEqual(
                    fill['fill'], None if flags & IMMUTABLE else base64.b64encode(b'fill').decode()
                )
                self.assertTrue(
                    self.fixture.exchange(
                        self.owner, dict(op='lookup_package', package='test.caller')
                    )['ok']
                )
                write_packet(callback, dict(ok=True))
                self.assertEqual(self.result(result), dict(ok=True))

    def test_missing_creator_does_not_consume_and_disconnect_is_uncertain(self):
        token = self.token(ONE_SHOT)
        request = dict(op='pending_intent_send', token=token, fill=None)
        self.assertFalse(self.fixture.exchange(self.other, request)['ok'])
        self.store.inspect(self.fixture.other['uid'], self.fixture.other['apk_sha256'], token)
        callback = self.register()
        with self.send(token) as result:
            read_packet(callback, 3)
            read_packet(callback, 3)
            callback.close()
            self.assertEqual(self.result(result), dict(ok=False, canceled=False, uncertain=True))
        with self.assertRaises(CanceledTokenError):
            self.store.inspect(self.fixture.other['uid'], self.fixture.other['apk_sha256'], token)
        self.assertTrue(self.fixture.exchange(self.other, request)['canceled'])

    def test_cancellation_while_waiting_does_not_deliver(self):
        callback = self.register()
        first = self.token()
        queued = self.token(ONE_SHOT)
        with self.send(first) as first_result:
            read_packet(callback, 3)
            read_packet(callback, 3)
            with self.send(queued) as queued_result:
                caller = self.fixture.caller
                self.store.cancel(caller['uid'], caller['apk_sha256'], queued)
                write_packet(callback, dict(ok=True))
                self.assertEqual(self.result(first_result), dict(ok=True))
                self.assertEqual(
                    self.result(queued_result), dict(ok=False, canceled=True, uncertain=False)
                )
                self.assertFalse(select.select([callback], [], [], 0.05)[0])

    def test_duplicate_host_and_quiescence_rejected(self):
        self.register()
        self.assertFalse(self.fixture.exchange(self.owner, dict(op='pending_intent_host'))['ok'])
        token = self.token()
        self.fixture.broker.quiesced.add('test.caller')
        self.assertFalse(
            self.fixture.exchange(
                self.other, dict(op='pending_intent_send', token=token, fill=None)
            )['ok']
        )
        self.fixture.broker.quiesced.clear()


if __name__ == '__main__':
    unittest.main()
