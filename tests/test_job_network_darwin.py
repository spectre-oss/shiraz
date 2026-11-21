# SPDX-License-Identifier: GPL-3.0-only
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from shiraz.job_network import NetworkObservation, observe_network
from shiraz.job_network_darwin import observe


class DarwinJobNetworkTests(unittest.TestCase):
    def test_status_and_transport_decode(self):
        cases = [
            (1, 1, 1, NetworkObservation(True, transport='wifi')),
            (1, 1, 2, NetworkObservation(True, transport='ethernet')),
            (1, 1, 4, NetworkObservation(True, transport='cellular')),
            (1, 1, 9, NetworkObservation(True)),
            (1, 1, 0, NetworkObservation(True)),
            (1, 2, 0, NetworkObservation(False)),
            (1, 3, 0, NetworkObservation(False)),
            (1, 0, 0, None),
            (2, 1, 1, None),
            (1, 4, 0, None),
            (1, 1, 16, None),
        ]
        for version, status, transports, expected in cases:

            def snapshot(output, count):
                self.assertEqual(count, 3)
                output[:] = (version, status, transports)
                return 1

            with (
                self.subTest(case=(version, status, transports)),
                patch(
                    'shiraz.job_network_darwin.library',
                    return_value=SimpleNamespace(shiraz_job_network_snapshot=snapshot),
                ),
            ):
                self.assertEqual(observe(), expected)

    def test_timeout_missing_library_and_backend_selection(self):
        for result in (0, -1, 2):
            with patch(
                'shiraz.job_network_darwin.library',
                return_value=SimpleNamespace(shiraz_job_network_snapshot=lambda *_: result),
            ):
                self.assertIsNone(observe())
        for error in (OSError('missing library'), AttributeError('missing ABI')):
            with patch('shiraz.job_network_darwin.library', side_effect=error):
                self.assertIsNone(observe())
        with (
            patch('shiraz.job_network.sys.platform', 'darwin'),
            patch(
                'shiraz.job_network_darwin.observe', return_value=NetworkObservation(False)
            ) as backend,
        ):
            self.assertEqual(observe_network(), NetworkObservation(False))
            backend.assert_called_once()
