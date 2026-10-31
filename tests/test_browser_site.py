# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
spec = importlib.util.spec_from_file_location('package_browser_site', ROOT/'scripts/package_browser_site.py')
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)


class BrowserSiteTests(unittest.TestCase):
    def prepare(self, root):
        (root/'VERSION').write_text('1.2.3\n')
        for name in ('demo.html', 'shiraz-assemble.wasm', 'wasm_exec.js',
                     'fonts/Manrope-wght.ttf', 'fonts/OFL.txt', 'fonts/README.md'):
            file = root/'go-wasm'/name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'fixture')
        (root/'dist').mkdir()
        for arch, machine in (('x86_64', 62), ('aarch64', 183)):
            self.base(root, arch, machine)

    def base(self, root, arch, machine):
        data = bytearray(64)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<H', data, 18, machine)
        base = root/'dist'/f'Shiraz-Browser-Base-1.2.3-{arch}.AppImage'
        base.write_bytes(data)
        base.with_suffix('.AppImage.sha256').write_text(hashlib.sha256(data).hexdigest()+'  '+base.name+'\n')
        return base

    def test_manifest_contains_verified_bases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.prepare(root)
            with patch.object(site, 'ROOT', root):
                site.main()
            manifest = json.loads((root/'build/pages-site/release.json').read_text())
            self.assertEqual(set(manifest['architectures']), {'x86_64', 'aarch64'})
            for arch, entry in manifest['architectures'].items():
                self.assertIn(arch, entry['base_file'])
                self.assertEqual(entry['base_size'], 64)
                self.assertTrue((root/'build/pages-site'/entry['base_file']).is_file())

    def test_rejects_relabelled_or_corrupt_base(self):
        for wrong_machine in (True, False):
            with self.subTest(wrong_machine=wrong_machine), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                self.prepare(root)
                base = self.base(root, 'aarch64', 62 if wrong_machine else 183)
                if not wrong_machine:
                    base.write_bytes(base.read_bytes()+b'corruption')
                with patch.object(site, 'ROOT', root), self.assertRaises(RuntimeError):
                    site.main()
