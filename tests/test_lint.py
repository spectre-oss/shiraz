# SPDX-License-Identifier: GPL-3.0-only
"""Exercise real Git staging and check isolation without installing tools."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "lint", Path(__file__).resolve().parents[1] / "scripts/lint.py"
)
lint = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lint)


class LintTests(unittest.TestCase):
    def test_staged_snapshot_preserves_partial_staging(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            for name in lint.CONFIGS:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("staged configuration\n")
            source = root / "example.py"
            source.write_text("staged = True\n")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            source.write_text("working_tree = True\n")
            (root / "ruff.toml").write_text("unstaged config\n")

            def inspect(snapshot, names):
                self.assertEqual(names, ["example.py"])
                self.assertEqual((snapshot / "example.py").read_text(), "staged = True\n")
                self.assertEqual((snapshot / "ruff.toml").read_text(), "staged configuration\n")
                return 0

            with (
                patch.object(lint, "ROOT", root),
                patch.object(lint, "check", side_effect=inspect),
                patch("sys.argv", ["lint.py", "--staged"]),
            ):
                self.assertEqual(lint.main(), 0)
            self.assertEqual(source.read_text(), "working_tree = True\n")
            staged = subprocess.check_output(["git", "show", ":example.py"], cwd=root, text=True)
            self.assertEqual(staged, "staged = True\n")

    def test_dependencies_are_excluded(self):
        self.assertFalse(lint.eligible("third_party/library/test.cpp"))
        self.assertFalse(lint.eligible("context/android/Foo.java"))
        self.assertTrue(lint.eligible("native/darwin/guest/jni.cpp"))
        self.assertTrue(lint.eligible("scripts/file with spaces.py"))

    def test_missing_tool_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(lint, "TOOLS", Path(directory)):
                with self.assertRaisesRegex(RuntimeError, "make init"):
                    lint.check(Path(directory), ["example.py"])


if __name__ == "__main__":
    unittest.main()
