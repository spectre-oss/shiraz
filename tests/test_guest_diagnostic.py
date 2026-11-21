# SPDX-License-Identifier: GPL-3.0-only
"""Validate the isolated DEX fault decoder against a locally compiled fixture."""

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from art_smoke import HOST  # noqa: E402
from macos_guest_isolated_app_probe import diagnostic_instruction  # noqa: E402


class GuestDiagnosticTests(unittest.TestCase):
    def test_fault_instruction_and_switch_from_compiled_dex(self):
        dx = HOST / "bin/dx"
        if not dx.is_file() or not shutil.which("javac"):
            self.skipTest("local ART dx and javac are required")
        with tempfile.TemporaryDirectory(prefix="shiraz-diagnostic-fixture-") as directory:
            work = Path(directory)
            source = work / "com/google/internal/getMeteringPointsAe.java"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.google.internal; public class getMeteringPointsAe {"
                " public int CoroutineDebuggingKt(int i) {"
                " switch (i) { case 240: return 7 / (i - 240); default: return i; } } }",
                encoding="utf-8",
            )
            classes = work / "classes"
            classes.mkdir()
            subprocess.run(
                ["javac", "--release", "8", "-d", str(classes), str(source)],
                check=True,
                capture_output=True,
                timeout=60,
            )
            dex = work / "fixture.dex"
            subprocess.run(
                [str(dx), "--dex", "--min-sdk-version=27", "--output=" + str(dex), str(classes)],
                check=True,
                capture_output=True,
                timeout=60,
            )
            data = dex.read_bytes()
            descriptor = "Lcom/google/internal/getMeteringPointsAe;"
            signature = "CoroutineDebuggingKt(I)I"
            found = []
            for pc in range(80):
                try:
                    result = diagnostic_instruction(data, pc, descriptor, signature)
                except ValueError:
                    continue
                if result[0] in (0x93, 0xB3):
                    found.append(result)
            self.assertEqual(len(found), 1)
            self.assertIsNotNone(found[0][3])
            self.assertEqual(found[0][3][1], 240)
            with self.assertRaises(ValueError):
                diagnostic_instruction(data[:40], 0, descriptor, signature)
            with self.assertRaises(ValueError):
                diagnostic_instruction(data, -1, descriptor, signature)


if __name__ == "__main__":
    unittest.main()
