# SPDX-License-Identifier: GPL-3.0-only
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz.job_network import LIMIT, NetworkObservation, observe_network, read_linux


class JobNetworkTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.proc, self.links = root / 'proc', root / 'links'
        self.proc.mkdir()
        self.links.mkdir()
        (self.proc / 'route').write_text(
            'Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT\n'
        )

    def route(self, name='eth0', metric=10, flags='0003'):
        with (self.proc / 'route').open('a') as stream:
            stream.write(f'{name} 00000000 01020304 {flags} 0 0 {metric} 00000000 0 0 0\n')

    def link(self, name='eth0', *, flags='0x1', state='up', carrier='1', wifi=False):
        root = self.links / name
        root.mkdir()
        for field, value in (
            ('flags', flags),
            ('operstate', state),
            ('carrier', carrier),
            ('type', '1'),
        ):
            (root / field).write_text(value)
        if wifi:
            (root / 'wireless').mkdir()
        return root

    def test_no_route_and_live_link_changes(self):
        self.assertEqual(read_linux(self.proc, self.links), NetworkObservation(False))
        self.route()
        link = self.link()
        self.assertEqual(
            read_linux(self.proc, self.links), NetworkObservation(True, 'eth0', 'ethernet')
        )
        for carrier, expected in (('0', False), ('1', True)):
            (link / 'carrier').write_text(carrier)
            self.assertIs(read_linux(self.proc, self.links).connected, expected)
        (link / 'flags').write_text('0x9')
        self.assertFalse(read_linux(self.proc, self.links).connected)

    def test_metric_wifi_and_no_invented_capabilities(self):
        self.route('eth0', 50)
        self.route('wlan0', 5)
        self.link()
        self.link('wlan0', wifi=True)
        value = read_linux(self.proc, self.links)
        self.assertEqual((value.interface, value.transport), ('wlan0', 'wifi'))
        self.assertIsNone(value.metered)
        self.assertIsNone(value.validated)

    def test_ipv6_default_and_reject_route(self):
        self.route(flags='0201')
        (self.proc / 'ipv6_route').write_text(
            f'{"0" * 32} 00 {"0" * 32} 00 {"0" * 32} 00000001 00000000 00000000 00000001 eth0\n'
        )
        self.link()
        self.assertTrue(read_linux(self.proc, self.links).connected)

    def test_unsafe_malformed_and_bounded_routes(self):
        for value in (
            'bad row\n',
            '../bad 00000000 0 1 0 0 1 00000000 0 0 0\n',
            'eth0 00000000 0 1 0 0 -1 00000000 0 0 0\n',
            'x' * (LIMIT + 1),
        ):
            (self.proc / 'route').write_text(value)
            with self.assertRaises(ValueError):
                read_linux(self.proc, self.links)

    def test_missing_and_unsupported_are_unknown(self):
        with patch('shiraz.job_network.sys.platform', 'unsupported'):
            self.assertIsNone(observe_network())
        with (
            patch('shiraz.job_network.sys.platform', 'linux'),
            patch('shiraz.job_network.read_linux', side_effect=OSError('unavailable')),
        ):
            self.assertIsNone(observe_network())
        self.route()
        with self.assertRaises(OSError):
            read_linux(self.proc, self.links)
        link = self.link()
        (link / 'tun_flags').write_text('0x1')
        with self.assertRaises(ValueError):
            read_linux(self.proc, self.links)
        (link / 'tun_flags').unlink()
        (link / 'carrier').write_text('unknown')
        with self.assertRaises(ValueError):
            read_linux(self.proc, self.links)
