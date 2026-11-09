#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run Go's Node WASM test harness without inheriting a compiler environment.

Use as ``go test -exec='python3 ../scripts/run_wasm_test.py'`` after setting
SHIRAZ_WASM_GOROOT to ``go env GOROOT``. Go itself retains its original build
configuration; only the test runtime receives this small environment. The Node
invocation matches GOROOT/lib/wasm/go_js_wasm_exec without needing its shell tools.
"""
import argparse
import os
import shutil
from pathlib import Path

MAX_OPTIONAL_PATH_BYTES = 512
MAX_RUNTIME_PATH_BYTES = 1024


def runtime_environment(node, parent):
    """Keep Node discoverable and optional filesystem roots, never Nix flags."""
    path = str(Path(node).parent)
    if len(os.fsencode(path)) > MAX_RUNTIME_PATH_BYTES:
        raise ValueError("Node directory exceeds the WASM runtime environment limit")
    result = {"PATH": path}
    for name in ("HOME", "TMPDIR"):
        value = parent.get(name)
        if value and len(os.fsencode(value)) <= MAX_OPTIONAL_PATH_BYTES:
            result[name] = value
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--goroot", default=os.environ.get("SHIRAZ_WASM_GOROOT"))
    parser.add_argument("binary", help="Go WASM test binary")
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if not args.goroot:
        parser.error("set --goroot or SHIRAZ_WASM_GOROOT to the build's Go root")
    directory = Path(args.goroot) / "lib" / "wasm"
    harness = directory / "wasm_exec_node.js"
    if not harness.is_file() or not (directory / "wasm_exec.js").is_file():
        parser.error("the selected Go root lacks its Node WASM harness")
    node = shutil.which("node")
    if node is None:
        parser.error("node executable not found on the build PATH")
    node = str(Path(node).resolve())
    try:
        environment = runtime_environment(node, os.environ)
    except ValueError as error:
        parser.error(str(error))
    command = [node, "--stack-size=8192", str(harness.resolve()), args.binary, *args.arguments]
    try:
        # Replace this wrapper: Node's exit status and signals reach go test
        # directly, with no shell parsing or intermediary status translation.
        os.execve(node, command, environment)
    except OSError:
        parser.exit(126, "run_wasm_test: cannot execute the Node runtime\n")


if __name__ == "__main__":
    main()
