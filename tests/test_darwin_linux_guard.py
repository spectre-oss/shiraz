# SPDX-License-Identifier: GPL-3.0-only
"""The platform guard compares both revisions under the same Linux defines."""

import importlib.util
import shutil
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    'darwin_linux_guard_test', Path(__file__).resolve().parents[1] / 'scripts/darwin_linux_guard.py'
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


@unittest.skipUnless(shutil.which('unifdef'), 'unifdef is required for the platform guard')
class LinuxGuardTests(unittest.TestCase):
    def test_existing_apple_branch_may_change_without_linux_change(self):
        before = b'#if defined(__APPLE__)\nint mac = 1;\n#else\nint linux = 2;\n#endif\n'
        after = before.replace(b'mac = 1', b'mac = 3')
        self.assertEqual(guard.linux_view(before), guard.linux_view(after))

    def test_linux_branch_change_remains_detectable(self):
        before = b'#if defined(__APPLE__)\nint mac = 1;\n#else\nint linux = 2;\n#endif\n'
        after = before.replace(b'linux = 2', b'linux = 3')
        self.assertNotEqual(guard.linux_view(before), guard.linux_view(after))

    def test_malformed_directive_is_not_accepted_as_matching(self):
        with self.assertRaises(ValueError):
            guard.linux_view(b'#if defined(__APPLE__)\nint broken;\n')
