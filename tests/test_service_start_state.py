# SPDX-License-Identifier: GPL-3.0-only
import base64
import unittest

from shiraz.service_start_state import MAX_BYTES, MAX_STARTS, ServiceStartState


def encoded(raw):
    return base64.b64encode(raw).decode('ascii')


class ServiceStartStateTests(unittest.TestCase):
    def test_finish_discards_only_completed_work_and_late_reports(self):
        state = ServiceStartState()
        state.retain(1, encoded(b'first'))
        state.retain(3, encoded(b'third'))
        state.finish(2)
        self.assertEqual(state.intents, {3: b'third'})
        state.retain(2, encoded(b'late callback'))
        state.finish(1)
        self.assertEqual(state.intents, {3: b'third'})
        self.assertEqual(state.finished, 2)
        state.clear()
        self.assertFalse(state.intents)
        state.retain(2, encoded(b'late stopped callback'))
        self.assertFalse(state.intents)
        state.retain(4, encoded(b'new start'))
        self.assertEqual(state.intents, {4: b'new start'})

    def test_invalid_encoding_is_rejected_without_changing_retained_work(self):
        state = ServiceStartState()
        state.retain(1, encoded(b'original'))
        for invalid in (
            None,
            1,
            '',
            'AA',
            'AA==\n',
            'AB==',
            '====',
            'éééé',
            encoded(b'x' * (MAX_BYTES + 1)),
        ):
            with self.subTest(invalid_type=type(invalid)):
                with self.assertRaises(ValueError):
                    state.retain(2, invalid)
                self.assertEqual(state.intents, {1: b'original'})
        with self.assertRaises(ValueError):
            state.retain(1, encoded(b'replacement'))
        self.assertEqual(state.intents, {1: b'original'})

    def test_byte_budget_released_by_acknowledgement(self):
        state = ServiceStartState()
        state.retain(1, encoded(b'a' * MAX_BYTES))
        with self.assertRaises(ValueError):
            state.retain(2, encoded(b'b'))
        self.assertEqual(len(state.intents[1]), MAX_BYTES)
        state.finish(1)
        state.retain(2, encoded(b'b' * MAX_BYTES))
        self.assertEqual(state.intents, {2: b'b' * MAX_BYTES})

    def test_count_budget_released_by_acknowledgement(self):
        state = ServiceStartState()
        for index in range(1, MAX_STARTS + 1):
            state.retain(index, encoded(b'x'))
        with self.assertRaises(ValueError):
            state.retain(MAX_STARTS + 1, encoded(b'x'))
        state.finish(1)
        state.retain(MAX_STARTS + 1, encoded(b'x'))
        self.assertEqual(len(state.intents), MAX_STARTS)
