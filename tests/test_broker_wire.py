# SPDX-License-Identifier: GPL-3.0-only
import array
import os
import socket
import unittest

from shiraz.broker_wire import HEADER, MAGIC, read_packet, read_payload, write_packet, write_payload


class BrokerWireTests(unittest.TestCase):
    def pair(self):
        first, second = socket.socketpair()
        self.addCleanup(first.close)
        self.addCleanup(second.close)
        return first, second

    def test_raw_payload_is_bounded_without_json_interpretation(self):
        sender, receiver = self.pair()
        data = b'\xff\0not-json'
        write_payload(sender, data)
        self.assertEqual(read_payload(receiver, 1, max_descriptors=0), (data, []))
        sender.sendall(HEADER.pack(MAGIC, 4 * 1024 * 1024 + 1, 0))
        with self.assertRaises(ValueError):
            read_payload(receiver, 1, max_bytes=4 * 1024 * 1024)
        for limit in (0, 4 * 1024 * 1024 + 1):
            with self.assertRaises(ValueError):
                read_payload(receiver, 1, max_bytes=limit)
            with self.assertRaises(ValueError):
                write_payload(sender, b'x', max_bytes=limit)

    def test_descriptor_is_owned_and_close_on_exec(self):
        sender, receiver = self.pair()
        peer, endpoint = self.pair()
        write_packet(sender, {'ok': True}, (endpoint.fileno(),))
        endpoint.close()
        value, descriptors = read_packet(receiver, 1)
        self.assertEqual(value, {'ok': True})
        self.assertEqual(len(descriptors), 1)
        descriptor = descriptors[0]
        try:
            self.assertFalse(os.get_inheritable(descriptor))
            os.write(descriptor, b'private endpoint')
            self.assertEqual(peer.recv(100), b'private endpoint')
        finally:
            os.close(descriptor)
        peer.settimeout(1)
        self.assertEqual(peer.recv(1), b'')

    def test_rejected_messages_close_received_descriptors(self):
        for payload, declared, allowed in (
            (b'{"ok":true}', 1, 0),
            (b'{"ok":true}', 0, 1),
            (b'{"ok":true,"ok":false}', 1, 1),
            (b'\xff', 1, 1),
        ):
            with self.subTest(payload=payload, declared=declared, allowed=allowed):
                sender, receiver = self.pair()
                peer, endpoint = self.pair()
                sender.sendmsg(
                    [HEADER.pack(MAGIC, len(payload), declared)],
                    [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', [endpoint.fileno()]))],
                )
                endpoint.close()
                sender.sendall(payload)
                with self.assertRaises(ValueError):
                    read_packet(receiver, 1, max_descriptors=allowed)
                peer.settimeout(1)
                self.assertEqual(peer.recv(1), b'', 'Malformed packet leaked its descriptor')

    def test_descriptor_count_and_header_validation(self):
        for magic, size, count in ((0, 1, 0), (MAGIC, 65537, 0), (MAGIC, 1, 2), (MAGIC, 2, 1)):
            sender, receiver = self.pair()
            sender.sendall(HEADER.pack(magic, size, count) + b'{}')
            with self.assertRaises(ValueError):
                read_packet(receiver, 1)

    def test_partial_header_and_payload_closure(self):
        for complete in (True, False):
            sender, receiver = self.pair()
            peer, endpoint = self.pair()
            header = HEADER.pack(MAGIC, 2, 1)
            sender.sendmsg(
                [header[:1]],
                [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', [endpoint.fileno()]))],
            )
            endpoint.close()
            sender.sendall(header[1:] + (b'{}' if complete else b'{'))
            sender.shutdown(socket.SHUT_WR)
            if complete:
                value, descriptors = read_packet(receiver, 1)
                self.assertEqual(value, {})
                for descriptor in descriptors:
                    os.close(descriptor)
            else:
                with self.assertRaises(EOFError):
                    read_packet(receiver, 1)
            peer.settimeout(1)
            self.assertEqual(peer.recv(1), b'')


if __name__ == '__main__':
    unittest.main()
