# SPDX-License-Identifier: GPL-3.0-only
import json
import subprocess
import sys
import unittest
from unittest.mock import patch

from shiraz import job_clock
from shiraz.job_definition import LONG_MAX


class JobClockTests(unittest.TestCase):
    def test_platform_clock_selection_and_no_wall_fallback(self):
        for platform, name in (('linux', 'CLOCK_BOOTTIME'), ('darwin', 'CLOCK_MONOTONIC')):
            with (
                patch.object(job_clock.sys, 'platform', platform),
                patch.object(job_clock.time, name, 12345, create=True),
                patch.object(job_clock.time, 'clock_gettime_ns', return_value=123456789) as read,
                patch.object(
                    job_clock.time, 'time_ns', side_effect=AssertionError('wall fallback')
                ),
            ):
                self.assertEqual(job_clock.elapsed_ms(), 123)
                read.assert_called_once_with(12345)
                read.side_effect = OSError('clock unavailable')
                with self.assertRaises(OSError):
                    job_clock.elapsed_ms()
        with patch.object(job_clock.sys, 'platform', 'unsupported'):
            with self.assertRaises(RuntimeError):
                job_clock.elapsed_ms()
        with (
            patch.object(job_clock.sys, 'platform', 'linux'),
            patch.object(job_clock.time, 'CLOCK_BOOTTIME', None, create=True),
        ):
            with self.assertRaises(RuntimeError):
                job_clock.elapsed_ms()

    def test_invalid_elapsed_values(self):
        for value in (-1, True, 1.5, (LONG_MAX + 1) * 1000000):
            with (
                patch.object(job_clock.time, 'clock_gettime_ns', return_value=value),
                self.assertRaises(ValueError),
            ):
                job_clock.elapsed_ms()

    def test_bounded_pairing_and_pause_rejection(self):
        with (
            patch.object(job_clock.boot_identity, 'current', return_value='boot'),
            patch.object(job_clock.time, 'time_ns', return_value=1000000000),
        ):
            with patch.object(job_clock, 'elapsed_ms', side_effect=[10, 111, 112, 115]):
                value = job_clock.sample()
                self.assertEqual(value, job_clock.Sample('boot', 1000, 115, 3))
            for times in ([10, 9], [0, 101, 200, 301, 400, 501]):
                with (
                    patch.object(job_clock, 'elapsed_ms', side_effect=times),
                    self.assertRaises(RuntimeError),
                ):
                    job_clock.sample()

    def test_invalid_wall_sample(self):
        with patch.object(job_clock.boot_identity, 'current', return_value='boot'):
            for nanos in (True, -1, 1.5, (LONG_MAX + 1) * 1000000):
                with (
                    patch.object(job_clock.time, 'time_ns', return_value=nanos),
                    self.assertRaises(ValueError),
                ):
                    job_clock.sample()

    def test_restore_boundary_and_saturation(self):
        value = job_clock.Sample('boot', 1000, 5000, 0)
        self.assertEqual(value.restore_target(900), 5000)
        self.assertEqual(value.restore_target(1000), 5000)
        self.assertEqual(value.restore_target(1100), 5100)
        self.assertEqual(value.restore_target(LONG_MAX), LONG_MAX)
        for invalid in (-1, True, LONG_MAX + 1):
            with self.assertRaises(ValueError):
                value.restore_target(invalid)

    def test_live_cross_process_epoch(self):
        before = job_clock.sample()
        output = subprocess.run(
            [
                sys.executable,
                '-c',
                'import json; from dataclasses import asdict; from shiraz.job_clock import sample; print(json.dumps(asdict(sample())))',
            ],
            capture_output=True,
            text=True,
            check=True,
            timeout=10,
        )
        child = json.loads(output.stdout)
        after = job_clock.sample()
        self.assertEqual(child['boot_id'], before.boot_id)
        self.assertEqual(after.boot_id, before.boot_id)
        self.assertLessEqual(before.elapsed_ms, child['elapsed_ms'])
        self.assertLessEqual(child['elapsed_ms'], after.elapsed_ms)
