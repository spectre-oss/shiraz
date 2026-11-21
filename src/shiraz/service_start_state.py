# SPDX-License-Identifier: GPL-3.0-only
"""Process-local redelivery snapshots; never persistent app data or capabilities."""

import base64
import binascii
import struct

MAX_BYTES = 32768
MAX_STARTS = 64


class ServiceStartState:
    def __init__(self):
        self.intents = {}
        self.finished = 0

    def retain(self, start_id, encoded):
        # Validate even if work finished before onStartCommand returned.
        if not isinstance(encoded, str) or not 0 < len(encoded) <= ((MAX_BYTES + 2) // 3) * 4:
            raise ValueError('Service redelivery snapshot size')
        try:
            raw = base64.b64decode(encoded, validate=True)
        except (ValueError, binascii.Error) as error:
            raise ValueError('Invalid service redelivery snapshot') from error
        if not raw or len(raw) > MAX_BYTES or base64.b64encode(raw).decode('ascii') != encoded:
            raise ValueError('Invalid service redelivery snapshot encoding')
        if start_id <= self.finished:
            return
        if start_id in self.intents:
            raise ValueError('Duplicate service redelivery snapshot')
        if (
            len(self.intents) >= MAX_STARTS
            or sum(map(len, self.intents.values())) + len(raw) > MAX_BYTES
        ):
            raise ValueError('Service redelivery retention limit')
        self.intents[start_id] = raw

    def finish(self, start_id):
        self.finished = max(self.finished, start_id)
        self.intents = {key: value for key, value in self.intents.items() if key > self.finished}

    def clear(self):
        self.intents.clear()

    def restart_frame(self):
        if not self.intents:
            raise ValueError('No unfinished service intents')
        entries = sorted(self.intents.items())
        return struct.pack('!II', 2, len(entries)) + b''.join(
            struct.pack('!II', start_id, len(raw)) + raw for start_id, raw in entries
        )
