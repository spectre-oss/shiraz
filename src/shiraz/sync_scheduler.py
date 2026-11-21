# SPDX-License-Identifier: GPL-3.0-only
"""Broker-owned bounded sync queue consumer; registry owns retry eligibility."""

import threading
from collections import deque


class SyncScheduler:
    def __init__(self, broker):
        self.broker = broker
        self.stop = threading.Event()
        self.failures = deque(maxlen=64)
        self.cursor = 0
        self.thread = threading.Thread(target=self._run, name='shiraz-sync-scheduler', daemon=True)

    def tick(self):
        if self.stop.is_set() or self.broker.closed:
            return
        rows = self.broker.registry.pending_sync_requests()
        if not rows:
            self.cursor = 0
            return
        start = self.cursor % len(rows)
        for offset in range(min(64, len(rows))):
            if self.stop.is_set() or self.broker.closed:
                return
            row = rows[(start + offset) % len(rows)]
            self.cursor = (start + offset + 1) % len(rows)
            try:
                result = self.broker.dispatch_sync_request(row['request_id'], timeout=30)
                if result is not None:
                    return  # One operation per turn; registry backoff prevents hot retries.
            except Exception as error:
                # No account names, extras, claim tokens or exception messages in diagnostics.
                self.failures.append(type(error).__name__)
                return

    def _run(self):
        while not self.stop.is_set():
            try:
                self.tick()
            except Exception as error:
                self.failures.append(type(error).__name__)
            self.stop.wait(1)

    def close(self):
        self.stop.set()
        self.thread.join(timeout=15)
        if self.thread.is_alive():
            raise RuntimeError('Sync scheduler did not stop after worker cleanup')
