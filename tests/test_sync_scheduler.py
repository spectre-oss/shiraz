# SPDX-License-Identifier: GPL-3.0-only
import unittest
from unittest.mock import Mock

from shiraz.sync_scheduler import SyncScheduler


class SyncSchedulerTests(unittest.TestCase):
    def test_skips_ineligible_and_limits_successful_dispatch(self):
        broker = Mock(closed=False)
        broker.registry.pending_sync_requests.return_value = [
            dict(request_id='a'),
            dict(request_id='b'),
            dict(request_id='c'),
        ]
        broker.dispatch_sync_request.side_effect = [None, dict(status='succeeded')]
        scheduler = SyncScheduler(broker)
        scheduler.tick()
        self.assertEqual(
            [call.args[0] for call in broker.dispatch_sync_request.call_args_list], ['a', 'b']
        )
        self.assertEqual(scheduler.cursor, 2)

    def test_stop_and_failure_diagnostics(self):
        broker = Mock(closed=False)
        broker.registry.pending_sync_requests.return_value = [dict(request_id='a')]
        broker.dispatch_sync_request.side_effect = RuntimeError('private fixture account detail')
        scheduler = SyncScheduler(broker)
        scheduler.tick()
        self.assertEqual(list(scheduler.failures), ['RuntimeError'])
        scheduler.stop.set()
        scheduler.tick()
        self.assertEqual(broker.dispatch_sync_request.call_count, 1)


if __name__ == '__main__':
    unittest.main()
