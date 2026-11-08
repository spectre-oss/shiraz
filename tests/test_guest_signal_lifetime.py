# SPDX-License-Identifier: GPL-3.0-only
"""Failure injection for stale-success evidence, not native execution proof."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import macos_guest_signal_lifetime as runner


class SignalLifetimeEvidenceTests(unittest.TestCase):
    def test_staging_or_signing_failure_invalidates_previous_success(self):
        for step in ("stage", "sign"):
            with self.subTest(step=step), tempfile.TemporaryDirectory() as tmp:
                work = Path(tmp)
                result_path = work / "adhoc.result.json"
                result_path.write_text(json.dumps({"execution_verified": True}))
                closure = {"main": str(work / "probe"), "bridge": str(work / "bridge"), "tree": str(work)}
                with (
                    patch.object(runner, "WORK", work),
                    patch.object(runner, "environment", return_value={}),
                    patch.object(runner, "stage_closure", side_effect=ValueError("stage failed") if step == "stage" else None, return_value=closure),
                    patch.object(runner, "sign_staged_adhoc_closure", side_effect=ValueError("sign failed")),
                    patch.object(sys, "argv", ["fixture", "--mode", "adhoc"]),
                    self.assertRaises(ValueError),
                ):
                    runner.main()
                current = json.loads(result_path.read_text())
                self.assertIs(current["execution_verified"], False)
                self.assertIs(current["hardened_verified"], False)
                self.assertEqual(current["phase"], "preparing" if step == "stage" else "signing")

    def test_timeout_or_launch_failure_cannot_leave_prior_success(self):
        for failing_call in (1, 2, 3):
            for error in (subprocess.TimeoutExpired(["fixture"], 60), OSError("launch failed")):
                with self.subTest(call=failing_call, error=type(error).__name__), tempfile.TemporaryDirectory() as tmp:
                    work = Path(tmp)
                    result_path = work / "adhoc.result.json"
                    result_path.write_text(json.dumps({"execution_verified": True, "hardened_verified": True}))
                    (work / "adhoc.log").write_text("old PASS")
                    closure = {"main": str(work / "probe"), "bridge": str(work / "bridge"), "tree": str(work)}
                    calls = 0

                    def run(*args, result_path=result_path, work=work,
                            failing_call=failing_call, error=error, **kwargs):
                        nonlocal calls
                        calls += 1
                        current = json.loads(result_path.read_text())
                        self.assertIs(current["execution_verified"], False)
                        self.assertIs(current["hardened_verified"], False)
                        self.assertEqual(current["native_closure"], str(work / "closure.json"))
                        if calls == failing_call:
                            raise error
                        marker = runner.REFUSAL_MARKER if calls == 2 else runner.MARKER
                        return SimpleNamespace(returncode=0, stdout=marker + "\n", stderr="")

                    with (
                        patch.object(runner, "WORK", work),
                        patch.object(runner, "environment", return_value={}),
                        patch.object(runner, "stage_closure", return_value=closure),
                        patch.object(runner, "sign_staged_adhoc_closure"),
                        patch.object(runner, "validate_native_inventory", return_value={}),
                        patch.object(runner.subprocess, "run", side_effect=run),
                        patch.object(sys, "argv", ["fixture", "--mode", "adhoc"]),
                        self.assertRaises(type(error)),
                    ):
                        runner.main()
                    self.assertFalse(json.loads(result_path.read_text())["execution_verified"])
                    self.assertNotIn("old PASS", (work / "adhoc.log").read_text())


if __name__ == "__main__":
    unittest.main()
