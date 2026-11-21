# SPDX-License-Identifier: GPL-3.0-only
"""The guest bridge must rebuild when its native I/O implementation changes."""

import platform
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(platform.system() == "Darwin", "Darwin bridge build only")
class GuestBridgeDependencies(unittest.TestCase):
    def test_io_sources_and_headers_are_prerequisites(self):
        targets = (
            "build/macos/libshiraz-guest.so",
            "build/macos/m5/libshiraz-guest-isolated-probe.so",
            "build/macos/m5/libshiraz-guest-isolated-app-probe.so",
        )
        result = subprocess.run(
            ["make", "-np", *targets], cwd=ROOT, text=True, capture_output=True, check=True
        )
        for target in targets:
            lines = [line for line in result.stdout.splitlines() if line.startswith(target + ":")]
            self.assertEqual(len(lines), 1, target)
            self.assertIn("native/darwin/guest/io.cpp", lines[0], target)
            self.assertIn("include/shiraz/darwin/guest/io.hpp", lines[0], target)
            self.assertIn("native/darwin/guest/process.cpp", lines[0], target)

    def test_changed_io_source_schedules_bridge_relink(self):
        target = "build/macos/m5/libshiraz-guest-isolated-app-probe.so"
        result = subprocess.run(
            ["make", "-n", "-W", "native/darwin/guest/io.cpp", target],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=True,
        )
        self.assertTrue(
            any(
                "native/darwin/guest/io.cpp" in line and "-o " + target in line
                for line in result.stdout.splitlines()
            ),
            "a changed guest I/O source must schedule a bridge relink",
        )


if __name__ == "__main__":
    unittest.main()
