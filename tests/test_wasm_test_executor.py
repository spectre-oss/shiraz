# SPDX-License-Identifier: GPL-3.0-only
"""Runtime process checks without requiring Go, Node, or a WASM fixture."""
import json
import os
import signal
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "run_wasm_test.py"


class WasmTestExecutorTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wasm executor ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.bin = self.root / "node tools"
        self.bin.mkdir()
        self.node = self.bin / "node"
        self.goroot = self.root / "go root"
        harness = self.goroot / "lib" / "wasm"
        harness.mkdir(parents=True)
        (harness / "wasm_exec_node.js").write_text("// fixture harness\n")
        (harness / "wasm_exec.js").write_text("// fixture runtime\n")
        self.environment = {
            "PATH": str(self.bin),
            "SHIRAZ_WASM_GOROOT": str(self.goroot),
            "HOME": str(self.root / "home"),
            "TMPDIR": str(self.root / "temporary"),
        }
        self.write_node('''import json, os, sys
print(json.dumps({"argv": sys.argv[1:], "environment": dict(os.environ)}))
''')

    def write_node(self, source):
        self.node.write_text(f"#!{sys.executable}\n{source}")
        self.node.chmod(0o755)

    def run_wrapper(self, *arguments, environment=None):
        return subprocess.run([sys.executable, str(SCRIPT), *arguments],
            env=self.environment if environment is None else environment,
            text=True, capture_output=True, check=False)

    def test_oversized_build_context_stays_in_compiler_but_not_runtime(self):
        environment = {**self.environment, "BUILD_CONTEXT": "x" * 60000}
        environment["PATH"] += os.pathsep + os.pathsep.join("/unused/toolchain" for _ in range(2000))
        compiler = '''import json, os, subprocess, sys
before = len(os.environ["BUILD_CONTEXT"])
child = subprocess.run(sys.argv[1:], text=True, capture_output=True, check=True)
print(json.dumps({"compiler_before": before,
                  "compiler_after": len(os.environ["BUILD_CONTEXT"]),
                  "runtime": json.loads(child.stdout)}))
'''
        result = subprocess.run([sys.executable, "-c", compiler, sys.executable,
            str(SCRIPT), "fixture.test.wasm"], env=environment,
            text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        observed = json.loads(result.stdout)
        self.assertEqual(observed["compiler_before"], 60000)
        self.assertEqual(observed["compiler_after"], 60000)
        runtime = observed["runtime"]["environment"]
        self.assertNotIn("BUILD_CONTEXT", runtime)
        self.assertNotIn("SHIRAZ_WASM_GOROOT", runtime)
        self.assertEqual(runtime["PATH"], str(self.bin))
        self.assertLess(sum(len(key) + len(value) + 2 for key, value in runtime.items()), 4096)

    def test_paths_with_spaces_and_test_arguments_are_not_shell_parsed(self):
        arguments = [str(self.root / "test binary.wasm"), "-test.run=With Spaces", "literal;$(not-a-command)"]
        result = self.run_wrapper(*arguments)
        self.assertEqual(result.returncode, 0, result.stderr)
        observed = json.loads(result.stdout)
        self.assertEqual(observed["argv"], ["--stack-size=8192",
            str(self.goroot / "lib" / "wasm" / "wasm_exec_node.js"), *arguments])
        self.assertEqual(observed["environment"]["HOME"], self.environment["HOME"])
        self.assertEqual(observed["environment"]["TMPDIR"], self.environment["TMPDIR"])

    def test_oversized_optional_paths_are_omitted_without_truncation(self):
        result = self.run_wrapper("fixture.wasm", environment={**self.environment,
            "HOME": "h" * 6000, "TMPDIR": "t" * 6000})
        self.assertEqual(result.returncode, 0, result.stderr)
        observed = json.loads(result.stdout)["environment"]
        self.assertNotIn("HOME", observed)
        self.assertNotIn("TMPDIR", observed)

    def test_runtime_nonzero_exit_is_preserved(self):
        self.write_node("import sys\nsys.exit(27)\n")
        self.assertEqual(self.run_wrapper("fixture.wasm").returncode, 27)

    @unittest.skipUnless(os.name == "posix", "requires POSIX signal return codes")
    def test_runtime_signal_is_preserved(self):
        self.write_node("import os, signal\nos.kill(os.getpid(), signal.SIGTERM)\n")
        self.assertEqual(self.run_wrapper("fixture.wasm").returncode, -signal.SIGTERM)

    def test_missing_configuration_harness_node_and_binary_fail_explicitly(self):
        no_root = {key: value for key, value in self.environment.items() if key != "SHIRAZ_WASM_GOROOT"}
        cases = [self.run_wrapper("fixture.wasm", environment=no_root),
                 self.run_wrapper("--goroot", str(self.root / "missing"), "fixture.wasm"),
                 self.run_wrapper("fixture.wasm", environment={**self.environment, "PATH": ""}),
                 self.run_wrapper()]
        for result in cases:
            with self.subTest(stderr=result.stderr):
                self.assertEqual(result.returncode, 2)
                self.assertTrue(result.stderr)
