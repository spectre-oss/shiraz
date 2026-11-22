# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import plistlib
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    'build_macos_app', ROOT / 'scripts/build_macos_app.py'
)
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


def write_tiny_png(path, width=64, height=64, rgba=(0x20, 0x60, 0xA0, 0xFF)):
    """Write a valid RGBA PNG without third-party imaging packages."""

    def chunk(kind, data):
        crc = zlib.crc32(kind + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', crc)

    raw = b''.join(b'\x00' + bytes(rgba) * width for _ in range(height))
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png = (
        b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', ihdr)
        + chunk(b'IDAT', zlib.compress(raw))
        + chunk(b'IEND', b'')
    )
    Path(path).write_bytes(png)


class BuildMacosAppUnitTest(unittest.TestCase):
    def test_parse_otool_deps(self):
        sample = (
            'build/macos/shiraz-preview:\n'
            '\t@rpath/libandroid.so (compatibility version 0.0.0, current version 0.0.0)\n'
            '\t/System/Library/Frameworks/Metal.framework/Versions/A/Metal '
            '(compatibility version 1.0.0, current version 368.1.0)\n'
            '\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1351.0.0)\n'
        )
        self.assertEqual(
            builder.parse_otool_deps(sample),
            [
                '@rpath/libandroid.so',
                '/System/Library/Frameworks/Metal.framework/Versions/A/Metal',
                '/usr/lib/libSystem.B.dylib',
            ],
        )

    def test_parse_otool_rpaths(self):
        sample = (
            'Load command 15\n'
            '          cmd LC_RPATH\n'
            '      cmdsize 32\n'
            '         path @loader_path (offset 12)\n'
            'Load command 16\n'
            '          cmd LC_RPATH\n'
            '      cmdsize 48\n'
            '         path @executable_path/../Frameworks (offset 12)\n'
        )
        self.assertEqual(
            builder.parse_otool_rpaths(sample),
            ['@loader_path', '@executable_path/../Frameworks'],
        )

    def test_is_system_dylib(self):
        self.assertTrue(builder.is_system_dylib('/usr/lib/libSystem.B.dylib'))
        self.assertTrue(
            builder.is_system_dylib('/System/Library/Frameworks/Metal.framework/Versions/A/Metal')
        )
        self.assertFalse(builder.is_system_dylib('@rpath/libandroid.so'))
        self.assertFalse(builder.is_system_dylib('/opt/homebrew/lib/libfoo.dylib'))

    def test_build_info_plist_contains_required_usage_strings(self):
        plist = builder.build_info_plist(
            name='Shiraz',
            bundle_id='org.shiraz.app',
            executable_name='Shiraz',
            version='0.353.0',
            icon_file='AppIcon.icns',
        )
        self.assertEqual(plist['CFBundleIdentifier'], 'org.shiraz.app')
        self.assertEqual(plist['CFBundleExecutable'], 'Shiraz')
        self.assertEqual(plist['CFBundleShortVersionString'], '0.353.0')
        self.assertEqual(plist['CFBundleIconFile'], 'AppIcon.icns')
        self.assertEqual(plist['LSMinimumSystemVersion'], '15.0')
        self.assertTrue(plist['NSHighResolutionCapable'])
        for key in (
            'NSCameraUsageDescription',
            'NSMicrophoneUsageDescription',
            'NSLocationUsageDescription',
            'NSLocationWhenInUseUsageDescription',
        ):
            self.assertTrue(plist[key], f'Missing {key}')

    def test_build_entitlements_allow_jit(self):
        self.assertEqual(
            builder.build_entitlements_plist(allow_jit=True),
            {'com.apple.security.cs.allow-jit': True},
        )
        self.assertEqual(builder.build_entitlements_plist(allow_jit=False), {})


@unittest.skipUnless(sys.platform == 'darwin', 'macOS-only end-to-end bundle signing test')
class BuildMacosAppDarwinIntegrationTest(unittest.TestCase):
    def test_end_to_end_signed_relocatable_app_bundle(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-app-test-') as tmp:
            tmp_path = Path(tmp)
            src_dir = tmp_path / 'src'
            src_dir.mkdir()

            # Build a tiny helper dylib with an absolute install_name and a main binary linking it.
            helper_c = src_dir / 'helper.c'
            helper_c.write_text('int shiraz_helper_value(void) { return 42; }\n', encoding='utf-8')
            helper_dylib = src_dir / 'libshiraz_test_helper.dylib'
            subprocess.run(
                [
                    'cc',
                    '-dynamiclib',
                    '-install_name',
                    str(helper_dylib),
                    str(helper_c),
                    '-o',
                    str(helper_dylib),
                ],
                check=True,
            )

            main_c = src_dir / 'main.c'
            main_c.write_text(
                '#include <stdio.h>\n'
                'extern int shiraz_helper_value(void);\n'
                'int main(void) {\n'
                '    printf("SHIRAZ_BUNDLE_VALUE=%d\\n", shiraz_helper_value());\n'
                '    return 0;\n'
                '}\n',
                encoding='utf-8',
            )
            main_bin = src_dir / 'shiraz-test-bin'
            subprocess.run(
                ['cc', str(main_c), str(helper_dylib), '-o', str(main_bin)],
                check=True,
            )

            icon_png = src_dir / 'icon.png'
            write_tiny_png(icon_png)
            extra_res = src_dir / 'sample.txt'
            extra_res.write_text('hello-resource\n', encoding='utf-8')

            out_app = tmp_path / 'out' / 'Shiraz.app'
            built = builder.build_macos_app(
                executable=main_bin,
                output=out_app,
                name='Shiraz',
                bundle_id='org.shiraz.test.app',
                resources=[extra_res],
                icon=icon_png,
            )
            self.assertEqual(built.resolve(), out_app.resolve())

            # Verify Info.plist fields and VERSION sync.
            plist = plistlib.loads((out_app / 'Contents' / 'Info.plist').read_bytes())
            self.assertEqual(plist['CFBundleIdentifier'], 'org.shiraz.test.app')
            self.assertEqual(plist['CFBundleShortVersionString'], builder.read_repo_version())
            self.assertEqual(plist['CFBundleIconFile'], 'AppIcon.icns')
            self.assertTrue((out_app / 'Contents' / 'Resources' / 'AppIcon.icns').is_file())
            self.assertEqual(
                (out_app / 'Contents' / 'Resources' / 'sample.txt').read_text(encoding='utf-8'),
                'hello-resource\n',
            )

            # Verify dylib closure and @rpath rewriting.
            bundled_dylib = out_app / 'Contents' / 'Frameworks' / helper_dylib.name
            self.assertTrue(bundled_dylib.is_file())
            exec_path = out_app / 'Contents' / 'MacOS' / 'Shiraz'
            exec_deps, exec_rpaths = builder.inspect_macho(exec_path)
            self.assertIn(f'@rpath/{helper_dylib.name}', exec_deps)
            self.assertNotIn(str(helper_dylib), exec_deps)
            self.assertIn('@executable_path/../Frameworks', exec_rpaths)

            # Delete original build directory to prove complete relocatability!
            for child in src_dir.iterdir():
                child.unlink()
            src_dir.rmdir()

            # Verify strict deep codesign and JIT entitlement.
            subprocess.run(
                ['codesign', '--verify', '--deep', '--strict', str(out_app)],
                check=True,
            )
            ent_out = subprocess.run(
                ['codesign', '--display', '--entitlements', '-', '--xml', str(out_app)],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertIn('com.apple.security.cs.allow-jit', ent_out.stdout)

            # Run the relocated, signed bundle executable and verify it loads the bundled dylib!
            run = subprocess.run([str(exec_path)], check=True, capture_output=True, text=True)
            self.assertEqual(run.stdout.strip(), 'SHIRAZ_BUNDLE_VALUE=42')


if __name__ == '__main__':
    unittest.main()
