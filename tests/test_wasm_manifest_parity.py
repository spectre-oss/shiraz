# SPDX-License-Identifier: GPL-3.0-only
import json
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from shiraz.android_attributes import ANDROID_ATTRIBUTE_NAMES
from shiraz.apk import inspect_apk
from shiraz.runtime import application_metadata, properties_text

ROOT = Path(__file__).resolve().parents[1]


class WasmManifestParityTests(unittest.TestCase):
    def test_go_android_attributes_matches_python_table(self):
        go_source = (ROOT / 'go-wasm/android_attributes.go').read_text(encoding='utf-8')
        go_entries = {
            int(match.group(1), 16): match.group(2)
            for match in re.finditer(r'0x([0-9a-fA-F]{8}):\s*"([^"]+)"', go_source)
        }
        self.assertEqual(go_entries, ANDROID_ATTRIBUTE_NAMES)

    def test_wasm_inspect_matches_python_application_metadata(self):
        wasm = ROOT / 'go-wasm/shiraz-assemble.wasm'
        wasm_exec = ROOT / 'go-wasm/wasm_exec.js'
        fixture = ROOT / 'build/apk-fixture/fixture.apk'
        if not (
            wasm.is_file() and wasm_exec.is_file() and fixture.is_file() and shutil.which('node')
        ):
            self.skipTest('Requires built go-wasm/shiraz-assemble.wasm, fixture.apk, and node')

        report = inspect_apk(fixture)
        py_facts = application_metadata(report['manifest'], fixture, Path('/unused-data'))
        py_facts.pop('sourceDir')
        py_facts.pop('dataDir')
        expected = properties_text(py_facts)

        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'meta.json'
            script = (
                "const fs = require('node:fs');"
                "const crypto = require('node:crypto');"
                "globalThis.crypto = crypto.webcrypto;"
                f"require({str(wasm_exec)!r});"
                "(async () => {"
                "  const go = new Go();"
                f"  const wasm = fs.readFileSync({str(wasm)!r});"
                "  const {instance} = await WebAssembly.instantiate(wasm, go.importObject);"
                "  go.run(instance);"
                f"  const apk = fs.readFileSync({str(fixture)!r});"
                "  const raw = shirazInspectApk(new Uint8Array(apk), '0.362.0', 'x86_64');"
                "  if (typeof raw !== 'string') { console.error(JSON.stringify(raw)); process.exit(1); }"
                f"  fs.writeFileSync({str(out)!r}, raw);"
                "  process.exit(0);"
                "})();"
            )
            subprocess.run(['node', '-e', script], check=True)
            go_meta = json.loads(out.read_text(encoding='utf-8'))

        self.assertEqual(go_meta['application_properties'], expected)
        self.assertEqual(go_meta['package'], report['manifest']['package'])
        self.assertEqual(
            go_meta['activity'], report['manifest']['launcher_activities'][0]['target']
        )


if __name__ == '__main__':
    unittest.main()
