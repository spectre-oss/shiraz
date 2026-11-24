# SPDX-License-Identifier: GPL-3.0-only
"""Unit and cross-language verification tests for Shiraz Flatpak packaging."""

import ctypes
import hashlib
import json
import lzma
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))

import flatpak_smoke as fs  # noqa: E402
import ostree_bundle as ob  # noqa: E402
import package_browser_site as pbs  # noqa: E402
import prepare_pages_runtime as ppr  # noqa: E402


def _make_elf(machine: int) -> bytes:
    header = bytearray(64)
    header[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', header, 16, 3)
    struct.pack_into('<H', header, 18, machine)
    return bytes(header)


def _stage_minimal_files(files_dir: Path, machine: int = 62) -> None:
    (files_dir / 'bin').mkdir(parents=True)
    (files_dir / 'usr/bin').mkdir(parents=True)
    (files_dir / 'usr/lib').mkdir(parents=True)
    (files_dir / 'usr/art').mkdir(parents=True)
    (files_dir / 'usr/share/shiraz/fonts').mkdir(parents=True)
    runner = files_dir / 'bin/shiraz-flatpak-run'
    runner.write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
    runner.chmod(0o755)
    (files_dir / 'usr/bin/shiraz-app').write_bytes(_make_elf(machine))
    (files_dir / 'usr/lib/libdemo.so').write_bytes(b'demo-lib')
    (files_dir / 'usr/lib/libdemo.so.0').symlink_to('libdemo.so')
    (files_dir / 'usr/art/README').write_text('art', encoding='utf-8')
    (files_dir / 'usr/share/shiraz/VERSION').write_text('0.360.0\n', encoding='utf-8')
    (files_dir / 'usr/share/shiraz/fonts/font.ttf').write_bytes(b'font')


class FlatpakPackagingTests(unittest.TestCase):
    def test_app_id_sanitization_matches_flatpak_rules(self):
        self.assertEqual(ob.flatpak_app_id('chat.delta'), 'io.github.spectre_oss.shiraz.chat_delta')
        self.assertEqual(
            ob.flatpak_app_id('org.shiraz.fixture'),
            'io.github.spectre_oss.shiraz.org_shiraz_fixture',
        )
        self.assertEqual(
            ob.flatpak_app_id('123.weird-pkg.app'),
            'io.github.spectre_oss.shiraz.p123_weird_pkg_app',
        )
        long_id = ob.flatpak_app_id('com.' + 'segment.' * 40 + 'app')
        self.assertLessEqual(len(long_id), 255)
        self.assertTrue(long_id.startswith(ob.APP_ID_PREFIX + '.'))

    def test_metadata_ini_network_toggle(self):
        online = ob.flatpak_metadata_ini(
            'io.github.spectre_oss.shiraz.app', 'x86_64', allow_network=True
        )
        offline = ob.flatpak_metadata_ini(
            'io.github.spectre_oss.shiraz.app', 'x86_64', allow_network=False
        )
        self.assertIn('shared=ipc;network;\n', online)
        self.assertIn('devices=dri;\n', online)
        self.assertIn('filesystems=xdg-run/pipewire-0:ro;\n', online)
        self.assertIn('shared=ipc;\n', offline)
        self.assertNotIn('network;', offline)

    def test_pure_python_bundle_is_glib_normal_form(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp) / 'stage'
            _stage_minimal_files(stage / 'files', machine=62)
            app_id = ob.flatpak_app_id('org.shiraz.fixture')
            (stage / 'export/share/applications').mkdir(parents=True)
            (stage / 'export/share/applications' / f'{app_id}.desktop').write_text(
                ob.flatpak_desktop_entry(app_id, 'Fixture'), encoding='utf-8'
            )
            (stage / 'metadata').write_text(
                ob.flatpak_metadata_ini(app_id, 'x86_64'), encoding='utf-8'
            )
            bundle = Path(tmp) / 'fixture.flatpak'
            bundle.write_bytes(
                ob.build_flatpak_bundle_from_tree(stage, app_id, 'x86_64', timestamp=1700000000)
            )
            fs.verify_gvariant_superblock(bundle)

    def test_flatpak_base_verification_and_release_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            files_x86 = tmp_path / 'files-x86'
            _stage_minimal_files(files_x86, machine=62)
            base_bytes = ob.build_flatpak_base_from_files_dir(files_x86, '1.2.3', 'x86_64')
            source = tmp_path / 'release'
            source.mkdir()
            ai_name = 'Shiraz-Browser-Base-1.2.3-x86_64.AppImage'
            ai_bytes = _make_elf(62)
            (source / ai_name).write_bytes(ai_bytes)
            (source / (ai_name + '.sha256')).write_text(
                f'{hashlib.sha256(ai_bytes).hexdigest()}  {ai_name}\n', encoding='utf-8'
            )
            fp_name = 'Shiraz-Flatpak-Base-1.2.3-x86_64.flatpak-base'
            (source / fp_name).write_bytes(base_bytes)
            (source / (fp_name + '.sha256')).write_text(
                f'{hashlib.sha256(base_bytes).hexdigest()}  {fp_name}\n', encoding='utf-8'
            )
            pbs.verify_flatpak_base(source / fp_name, 'x86_64')
            with self.assertRaises(RuntimeError):
                pbs.verify_flatpak_base(source / fp_name, 'aarch64')

            site = tmp_path / 'site'
            site.mkdir()
            ppr.install_runtime(site, source, '1.2.3')
            manifest = json.loads((site / 'release.json').read_text(encoding='utf-8'))
            self.assertEqual(manifest['base_file'], 'base/' + ai_name)
            formats = manifest['architectures']['x86_64']['formats']
            self.assertEqual(formats['appimage']['base_file'], 'base/' + ai_name)
            self.assertEqual(formats['flatpak']['base_file'], 'base/' + fp_name)
            self.assertEqual(formats['flatpak']['base_size'], len(base_bytes))

    def test_cross_language_python_base_to_wasm_flatpak(self):
        wasm_path = ROOT / 'go-wasm/shiraz-assemble.wasm'
        apk_path = ROOT / 'build/apk-fixture/fixture.apk'
        if not wasm_path.is_file() or not apk_path.is_file():
            self.skipTest(
                'Requires built go-wasm/shiraz-assemble.wasm and build/apk-fixture/fixture.apk'
            )
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            files_dir = tmp_path / 'files'
            _stage_minimal_files(files_dir, machine=62)
            base_path = tmp_path / 'base.flatpak-base'
            base_path.write_bytes(
                ob.build_flatpak_base_from_files_dir(files_dir, '0.360.0', 'x86_64')
            )
            out_path = tmp_path / 'wasm.flatpak'
            script = (
                "const fs = require('node:fs');"
                "const crypto = require('node:crypto');"
                "globalThis.crypto = crypto.webcrypto;"
                f"require({str(ROOT / 'go-wasm/wasm_exec.js')!r});"
                "(async () => {"
                "const go = new Go();"
                f"const wasm = fs.readFileSync({str(wasm_path)!r});"
                "const {instance} = await WebAssembly.instantiate(wasm, go.importObject);"
                "go.run(instance);"
                f"const base = fs.readFileSync({str(base_path)!r});"
                f"const apk = fs.readFileSync({str(apk_path)!r});"
                "const res = shirazBuildFlatpakFromApk(new Uint8Array(base), new Uint8Array(apk), '0.360.0', {allowNetwork: false});"
                "if (res.error) throw new Error(res.error);"
                f"fs.writeFileSync({str(out_path)!r}, Buffer.from(res.bytes));"
                "process.exit(0);"
                "})().catch(err => { console.error(err); process.exit(1); });"
            )
            subprocess.run(['node', '-e', script], check=True)
            fs.verify_gvariant_superblock(out_path)
            raw = out_path.read_bytes()
            self.assertIn(b'shared=ipc;\n', raw)
            self.assertNotIn(b'shared=ipc;network;', raw)
            # Verify Part 0 (XZ) and Part 1 (uncompressed) inside the WASM bundle via libglib-2.0.
            try:
                glib = ctypes.CDLL('libglib-2.0.so.0')
            except OSError:
                return
            glib.g_variant_type_new.argtypes = [ctypes.c_char_p]
            glib.g_variant_type_new.restype = ctypes.c_void_p
            glib.g_variant_new_from_data.argtypes = [
                ctypes.c_void_p,
                ctypes.c_void_p,
                ctypes.c_size_t,
                ctypes.c_int,
                ctypes.c_void_p,
                ctypes.c_void_p,
            ]
            glib.g_variant_new_from_data.restype = ctypes.c_void_p
            glib.g_variant_ref_sink.argtypes = [ctypes.c_void_p]
            glib.g_variant_ref_sink.restype = ctypes.c_void_p
            glib.g_variant_get_child_value.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
            glib.g_variant_get_child_value.restype = ctypes.c_void_p
            glib.g_variant_lookup_value.argtypes = [
                ctypes.c_void_p,
                ctypes.c_char_p,
                ctypes.c_void_p,
            ]
            glib.g_variant_lookup_value.restype = ctypes.c_void_p
            glib.g_variant_get_data.argtypes = [ctypes.c_void_p]
            glib.g_variant_get_data.restype = ctypes.c_void_p
            glib.g_variant_get_size.argtypes = [ctypes.c_void_p]
            glib.g_variant_get_size.restype = ctypes.c_size_t
            glib.g_variant_is_normal_form.argtypes = [ctypes.c_void_p]
            glib.g_variant_is_normal_form.restype = ctypes.c_int
            buf = ctypes.create_string_buffer(raw, len(raw))
            v = glib.g_variant_ref_sink(
                glib.g_variant_new_from_data(
                    glib.g_variant_type_new(fs.OSTREE_SUPERBLOCK_SIG),
                    ctypes.addressof(buf),
                    len(raw),
                    0,
                    None,
                    None,
                )
            )
            meta = glib.g_variant_get_child_value(v, 0)
            to_v = glib.g_variant_get_child_value(v, 3)
            to_bytes = ctypes.string_at(
                glib.g_variant_get_data(to_v), glib.g_variant_get_size(to_v)
            )
            payload_type = glib.g_variant_type_new(b'(a(uuu)aa(ayay)ayay)')
            yay_type = glib.g_variant_type_new(b'(yay)')
            for idx in (0, 1):
                part_v = glib.g_variant_lookup_value(
                    meta, ob.ostree_delta_part_key(to_bytes, idx).encode('ascii'), yay_type
                )
                self.assertTrue(part_v, f'missing inline part {idx}')
                part_raw = ctypes.string_at(
                    glib.g_variant_get_data(part_v), glib.g_variant_get_size(part_v)
                )
                body = lzma.decompress(part_raw[1:]) if part_raw[0] == ord('x') else part_raw[1:]
                pbuf = ctypes.create_string_buffer(body, len(body))
                pv = glib.g_variant_ref_sink(
                    glib.g_variant_new_from_data(
                        payload_type, ctypes.addressof(pbuf), len(body), 0, None, None
                    )
                )
                self.assertEqual(glib.g_variant_is_normal_form(pv), 1)


if __name__ == '__main__':
    unittest.main()
