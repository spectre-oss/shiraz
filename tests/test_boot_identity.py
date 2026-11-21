# SPDX-License-Identifier: GPL-3.0-only
import subprocess
import unittest
from unittest.mock import Mock, patch

from shiraz import boot_identity


class BootIdentityTests(unittest.TestCase):
    def setUp(self):
        boot_identity.current.cache_clear()

    def tearDown(self):
        boot_identity.current.cache_clear()

    def test_linux_identity_cached_per_process(self):
        identity = '11111111-1111-4111-8111-111111111111'
        with (
            patch.object(boot_identity.sys, 'platform', 'linux'),
            patch.object(boot_identity.Path, 'read_text', return_value=identity + '\n') as read,
        ):
            self.assertEqual(boot_identity.current(), identity)
            self.assertEqual(boot_identity.current(), identity)
            read.assert_called_once()

    def test_darwin_normalizes_kernel_uuid(self):
        identity = 'ABCDEF12-1111-4111-8111-111111111111'
        with (
            patch.object(boot_identity.sys, 'platform', 'darwin'),
            patch.object(
                boot_identity.subprocess, 'run', return_value=Mock(stdout=identity + '\n')
            ) as run,
        ):
            self.assertEqual(boot_identity.current(), identity.lower())
            self.assertEqual(
                run.call_args.args[0], ['/usr/sbin/sysctl', '-n', 'kern.bootsessionuuid']
            )
            self.assertEqual(run.call_args.kwargs['timeout'], 5)

    def test_invalid_unavailable_or_unsupported_never_invents_epoch(self):
        for value in ('', 'not-a-uuid', '00000000-0000-0000-0000-000000000000'):
            with (
                patch.object(boot_identity.sys, 'platform', 'linux'),
                patch.object(boot_identity.Path, 'read_text', return_value=value),
            ):
                with self.assertRaises(ValueError):
                    boot_identity.current()
        with (
            patch.object(boot_identity.sys, 'platform', 'darwin'),
            patch.object(
                boot_identity.subprocess, 'run', side_effect=subprocess.TimeoutExpired('sysctl', 5)
            ),
        ):
            with self.assertRaises(subprocess.TimeoutExpired):
                boot_identity.current()
        with patch.object(boot_identity.sys, 'platform', 'unsupported'):
            with self.assertRaises(RuntimeError):
                boot_identity.current()
