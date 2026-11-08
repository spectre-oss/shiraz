# SPDX-License-Identifier: GPL-3.0-only
"""Public capability parsing only; these mocks are not host execution evidence."""

import subprocess
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_return_auth_profile import host_return_auth_profile


class ReturnAuthProfileTests(unittest.TestCase):
    def setUp(self):
        host_return_auth_profile.cache_clear()
        self.addCleanup(host_return_auth_profile.cache_clear)

    def responses(self, values):
        return [SimpleNamespace(returncode=0, stdout=value) for value in values]

    def test_observed_legacy_profile_is_cached_without_key_or_signature_access(self):
        with patch("guest_return_auth_profile.sys.platform", "darwin"), \
                patch("guest_return_auth_profile.platform.machine", return_value="arm64"), \
                patch("guest_return_auth_profile.subprocess.run", side_effect=self.responses(["1\n", "0\n"])) as run:
            result = host_return_auth_profile()
            self.assertEqual((result["feature_pauth"], result["feature_pauth_lr"]), (1, 0))
            self.assertEqual(host_return_auth_profile(), result)
            self.assertEqual(run.call_count, 2)
            self.assertTrue(all(call.args[0][:2] == ["/usr/sbin/sysctl", "-n"] for call in run.call_args_list))

    def test_missing_invalid_or_failed_queries_do_not_invent_a_profile(self):
        for values in (["1", ""], ["1", "yes"], ["2", "0"]):
            host_return_auth_profile.cache_clear()
            with patch("guest_return_auth_profile.sys.platform", "darwin"), \
                    patch("guest_return_auth_profile.platform.machine", return_value="arm64"), \
                    patch("guest_return_auth_profile.subprocess.run", side_effect=self.responses(values)):
                self.assertIsNone(host_return_auth_profile())

    def test_newer_features_are_reported_without_assuming_legacy_semantics(self):
        with patch("guest_return_auth_profile.sys.platform", "darwin"), \
                patch("guest_return_auth_profile.platform.machine", return_value="arm64"), \
                patch("guest_return_auth_profile.subprocess.run", side_effect=self.responses(["1", "1"])):
            self.assertEqual(host_return_auth_profile()["feature_pauth_lr"], 1)

    def test_other_platform_does_not_run_host_commands(self):
        with patch("guest_return_auth_profile.sys.platform", "linux"), \
                patch("guest_return_auth_profile.subprocess.run") as run:
            self.assertIsNone(host_return_auth_profile())
        run.assert_not_called()

    def test_command_failure_and_timeout_leave_capabilities_unproved(self):
        for error in (OSError("unavailable"), subprocess.TimeoutExpired("sysctl", 5)):
            host_return_auth_profile.cache_clear()
            with patch("guest_return_auth_profile.sys.platform", "darwin"), \
                    patch("guest_return_auth_profile.platform.machine", return_value="arm64"), \
                    patch("guest_return_auth_profile.subprocess.run", side_effect=error):
                self.assertIsNone(host_return_auth_profile())
        with patch("guest_return_auth_profile.sys.platform", "darwin"), \
                patch("guest_return_auth_profile.platform.machine", return_value="arm64"), \
                patch("guest_return_auth_profile.subprocess.run", return_value=SimpleNamespace(returncode=1, stdout="1")):
            host_return_auth_profile.cache_clear()
            self.assertIsNone(host_return_auth_profile())


if __name__ == "__main__":
    unittest.main()
