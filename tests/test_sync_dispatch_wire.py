# SPDX-License-Identifier: GPL-3.0-only
import socket
import struct
import threading
import unittest

from shiraz.broker_wire import read_payload, write_packet
from shiraz.service_process import ServiceProcess
from shiraz.sync_dispatch_wire import encode


def claim():
    return dict(
        request_id='a' * 64,
        account=dict(name='fixture-user', type='fixture.accounts'),
        adapter=dict(
            package='test.sync',
            component='test.sync.Adapter',
            authority='fixture.data',
            apk_sha256='b' * 64,
        ),
        extras={
            'empty': dict(type='string', value=''),
            'large': dict(type='long', value=(1 << 63) - 1),
        },
    )


class SyncDispatchWireTests(unittest.TestCase):
    def test_binary_contract_and_bounds(self):
        value = claim()
        expected = bytearray(struct.pack('!I', 3))

        def string(text):
            raw = text.encode()
            expected.extend(struct.pack('!I', len(raw)))
            expected.extend(raw)

        for text in [
            'a' * 64,
            'test.sync',
            'test.sync.Adapter',
            'fixture.data',
            'fixture-user',
            'fixture.accounts',
        ]:
            string(text)
        expected.extend(struct.pack('!I', 2))
        string('empty')
        expected.append(6)
        string('')
        string('large')
        expected.append(3)
        expected.extend(struct.pack('!q', (1 << 63) - 1))
        self.assertEqual(encode(value), bytes(expected))
        value['extras']['bad'] = dict(type='double', value=float('nan'))
        with self.assertRaises(ValueError):
            encode(value)

    def test_process_target_pin_before_delivery(self):
        process = ServiceProcess.__new__(ServiceProcess)
        process.key = ('test.other', 'b' * 64, 'test.sync.Adapter')
        with self.assertRaises(PermissionError):
            process.dispatch_sync(claim())

    def test_descriptor_handoff_and_acknowledgement(self):
        process = ServiceProcess.__new__(ServiceProcess)
        process.key = ('test.sync', 'b' * 64, 'test.sync.Adapter')
        process.lock = threading.Lock()
        process.closed = False
        process.endpoint, peer = socket.socketpair()
        errors = []

        def worker():
            try:
                payload, fds = read_payload(peer, idle_timeout=5, max_descriptors=1)
                self.assertEqual(payload, encode(claim()))
                self.assertEqual(len(fds), 1)
                with socket.socket(fileno=fds[0]) as result:
                    write_packet(peer, dict(sync_admitted=True))
                    result.sendall(b'completion-channel')
            except BaseException as error:
                errors.append(error)

        thread = threading.Thread(target=worker)
        thread.start()
        try:
            with process.dispatch_sync(claim()) as result:
                result.settimeout(5)
                self.assertEqual(result.recv(100), b'completion-channel')
            thread.join(6)
            self.assertFalse(thread.is_alive())
            self.assertEqual(errors, [])
        finally:
            process.endpoint.close()
            peer.close()


if __name__ == '__main__':
    unittest.main()
