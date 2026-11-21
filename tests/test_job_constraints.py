# SPDX-License-Identifier: GPL-3.0-only
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from shiraz.job_constraints import MAX_LOW_BYTES, deadline_expired, observe_storage, satisfied


class JobConstraintTests(unittest.TestCase):
    def test_unconstrained_jobs_do_not_depend_on_measurement(self):
        with patch(
            'shiraz.job_constraints.observe_storage',
            side_effect=AssertionError('unexpected measurement'),
        ):
            self.assertTrue(satisfied({'version': 1}, 'test.jobs'))
            self.assertTrue(
                satisfied({'version': 2, 'requires_storage_not_low': False}, 'test.jobs')
            )

    def test_deadline_boundary_retry_and_overflow(self):
        record = dict(deadline_elapsed_ms=1500, attempts=0)
        for now, expected in ((999, False), (1499, False), (1500, True), (1501, True)):
            self.assertIs(deadline_expired(record, now), expected)
        self.assertFalse(deadline_expired(dict(record, attempts=1), 999999))
        self.assertFalse(deadline_expired(dict(record, deadline_elapsed_ms=None), 999999))
        maximum = (1 << 63) - 1
        self.assertFalse(deadline_expired(dict(record, deadline_elapsed_ms=maximum), maximum - 1))
        self.assertTrue(deadline_expired(dict(record, deadline_elapsed_ms=1000), 1000))
        with patch(
            'shiraz.job_constraints.observe_storage', side_effect=AssertionError('deadline bypass')
        ):
            self.assertTrue(
                satisfied(
                    {'requires_storage_not_low': True}, 'test.jobs', override_deadline_expired=True
                )
            )
        with self.assertRaises(ValueError):
            satisfied({}, 'test.jobs', override_deadline_expired=1)

    def test_percentage_threshold_boundary_and_reserved_space(self):
        info = SimpleNamespace(f_frsize=4096, f_blocks=1000, f_bavail=50, f_bfree=900)
        with patch('shiraz.job_constraints.os.statvfs', return_value=info):
            value = observe_storage('/data/app')
            self.assertEqual(value.available_bytes, 50 * 4096)
            self.assertEqual(value.low_threshold_bytes, 50 * 4096)
            self.assertFalse(value.not_low)
            info.f_bavail = 51
            self.assertTrue(observe_storage('/data/app').not_low)
            info.f_bavail = 49
            self.assertFalse(observe_storage('/data/app').not_low)

    def test_absolute_threshold_cap(self):
        info = SimpleNamespace(f_frsize=1, f_blocks=100 * 1024**3, f_bavail=MAX_LOW_BYTES + 1)
        with patch('shiraz.job_constraints.os.statvfs', return_value=info):
            value = observe_storage('/data')
            self.assertEqual(value.low_threshold_bytes, MAX_LOW_BYTES)
            self.assertTrue(value.not_low)

    def test_missing_app_uses_real_existing_volume_without_creating_it(self):
        with tempfile.TemporaryDirectory() as directory:
            requested = Path(directory) / 'not-created' / 'app'
            value = observe_storage(requested)
            self.assertEqual(value.measured_path, Path(directory).resolve())
            self.assertFalse(requested.exists())
            info = os.statvfs(directory)
            self.assertEqual(value.total_bytes, info.f_frsize * info.f_blocks)
            self.assertGreaterEqual(value.available_bytes, 0)

    def test_unknown_or_invalid_measurement_is_not_success(self):
        for error in (PermissionError('denied'), OSError('unavailable')):
            with patch('shiraz.job_constraints.os.statvfs', side_effect=error):
                self.assertIsNone(observe_storage('/data'))
        for size, blocks, free in (
            (0, 100, 10),
            (1, 0, 0),
            (1, 100, -1),
            (1, 100, 101),
            (True, 100, 10),
        ):
            with patch(
                'shiraz.job_constraints.os.statvfs',
                return_value=SimpleNamespace(f_frsize=size, f_blocks=blocks, f_bavail=free),
            ):
                self.assertIsNone(observe_storage('/data'))

    def test_symlink_uses_target_volume(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'volume').mkdir()
            (root / 'alias').symlink_to(root / 'volume', target_is_directory=True)
            value = observe_storage(root / 'alias' / 'future-app')
            self.assertEqual(value.measured_path, (root / 'volume').resolve())
