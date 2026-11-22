# SPDX-License-Identifier: GPL-3.0-only
"""Unit and end-to-end tests for scripts/build_windows_app.py."""

import json
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_windows_app as bundler  # noqa: E402


def _make_minimal_pe64(imported_dlls=()):
    """Synthesize a minimal valid PE32+ image with an optional import directory."""
    pe_offset = 0x80
    opt_size = 240  # Standard PE32+ optional header with 16 data directories
    sec_off = pe_offset + 24 + opt_size
    raw_ptr = 0x200
    rva_base = 0x1000

    # Build .idata payload: N+1 descriptors (20 bytes each) followed by NUL-terminated names.
    names_rel = (len(imported_dlls) + 1) * 20
    name_offsets = []
    name_blob = bytearray()
    for dll in imported_dlls:
        name_offsets.append(names_rel + len(name_blob))
        name_blob.extend(dll.encode('ascii') + b'\x00')

    idata = bytearray()
    for idx in range(len(imported_dlls)):
        idata.extend(struct.pack('<IIIII', 0, 0, 0, rva_base + name_offsets[idx], 0))
    idata.extend(b'\x00' * 20)
    idata.extend(name_blob)

    total_size = raw_ptr + max(len(idata), 0x200)
    buf = bytearray(total_size)
    buf[0:2] = b'MZ'
    struct.pack_into('<I', buf, 0x3C, pe_offset)
    buf[pe_offset : pe_offset + 4] = b'PE\x00\x00'
    # COFF header: Machine=AMD64(0x8664), NumberOfSections=1, SizeOfOptionalHeader=opt_size
    struct.pack_into('<HHIIIHH', buf, pe_offset + 4, 0x8664, 1, 0, 0, 0, opt_size, 0x22)
    # Optional header magic = PE32+ (0x20B)
    opt_off = pe_offset + 24
    struct.pack_into('<H', buf, opt_off, 0x20B)
    # Import directory (DataDirectory[1]) at opt_off + 112 + 8
    if imported_dlls:
        struct.pack_into('<II', buf, opt_off + 112 + 8, rva_base, len(idata))
    # Section header (.idata)
    buf[sec_off : sec_off + 6] = b'.idata'
    struct.pack_into('<IIII', buf, sec_off + 8, len(idata), rva_base, len(idata), raw_ptr)
    buf[raw_ptr : raw_ptr + len(idata)] = idata
    return bytes(buf)


def _make_minimal_png(width=32, height=32):
    header = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr = struct.pack('>I', len(ihdr_data)) + b'IHDR' + ihdr_data + b'\x00\x00\x00\x00'
    iend = struct.pack('>I', 0) + b'IEND' + b'\x00\x00\x00\x00'
    return header + ihdr + iend


class BuildWindowsAppTest(unittest.TestCase):
    def test_system_dll_classification(self):
        self.assertTrue(bundler.is_system_dll('KERNEL32.dll'))
        self.assertTrue(bundler.is_system_dll('d3d11.dll'))
        self.assertTrue(bundler.is_system_dll('api-ms-win-crt-runtime-l1-1-0.dll'))
        self.assertFalse(bundler.is_system_dll('shiraz-win32-host.dll'))
        self.assertFalse(bundler.is_system_dll('libGLESv2.dll'))

    def test_pe_import_parser_reads_dlls(self):
        pe = _make_minimal_pe64(['KERNEL32.dll', 'shiraz-win32-host.dll', 'SDL2.dll'])
        self.assertTrue(bundler.is_valid_pe(pe))
        self.assertEqual(
            bundler.parse_pe_imports(pe),
            ['KERNEL32.dll', 'shiraz-win32-host.dll', 'SDL2.dll'],
        )
        with self.assertRaises(ValueError):
            bundler.parse_pe_imports(b'not-a-pe')

    def test_ico_from_png_container(self):
        png = _make_minimal_png(64, 64)
        ico = bundler.build_ico_from_png(png)
        reserved, kind, count = struct.unpack_from('<HHH', ico, 0)
        self.assertEqual((reserved, kind, count), (0, 1, 1))
        w, h, _, _, planes, bpp, size, offset = struct.unpack_from('<BBBBHHII', ico, 6)
        self.assertEqual((w, h, planes, bpp, size, offset), (64, 64, 1, 32, len(png), 22))
        self.assertEqual(ico[offset:], png)

    def test_msix_version_normalization(self):
        self.assertEqual(bundler.normalize_msix_version('0.353.0'), '0.353.0.0')
        self.assertEqual(bundler.normalize_msix_version('1.2.3.4'), '1.2.3.4')
        with self.assertRaises(ValueError):
            bundler.normalize_msix_version('invalid')

    def test_end_to_end_windows_portable_and_msix_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            deps = root / 'deps'
            deps.mkdir()

            # Build transitive dependency chain: Shiraz.exe -> host.dll -> codec.dll + KERNEL32.dll
            (deps / 'codec.dll').write_bytes(_make_minimal_pe64(['KERNEL32.dll', 'mfplat.dll']))
            (deps / 'shiraz-win32-host.dll').write_bytes(
                _make_minimal_pe64(['USER32.dll', 'codec.dll'])
            )
            exe = root / 'Shiraz.exe'
            exe.write_bytes(_make_minimal_pe64(['KERNEL32.dll', 'shiraz-win32-host.dll']))
            icon = root / 'icon.png'
            icon.write_bytes(_make_minimal_png(256, 256))

            out = root / 'bundle'
            bundler.build_windows_bundle(
                executable=exe,
                output_dir=out,
                version='0.353.0',
                architecture='x64',
                dll_search_dirs=[deps],
                icon_png=icon,
            )

            self.assertTrue((out / 'Shiraz.exe').is_file())
            self.assertTrue((out / 'shiraz-win32-host.dll').is_file())
            self.assertTrue((out / 'codec.dll').is_file())
            self.assertFalse((out / 'KERNEL32.dll').exists())
            self.assertTrue((out / 'Assets' / 'Shiraz.ico').is_file())

            manifest = json.loads((out / 'shiraz-manifest.json').read_text(encoding='utf-8'))
            self.assertEqual(manifest['msix_version'], '0.353.0.0')
            self.assertEqual(manifest['bundled_dlls'], ['codec.dll', 'shiraz-win32-host.dll'])
            self.assertEqual(manifest['unresolved_dlls'], [])
            self.assertIn('Shiraz.exe', manifest['files'])

            xml_root = ET.fromstring((out / 'AppxManifest.xml').read_text(encoding='utf-8'))
            self.assertTrue(xml_root.tag.endswith('Package'))


if __name__ == '__main__':
    unittest.main()
