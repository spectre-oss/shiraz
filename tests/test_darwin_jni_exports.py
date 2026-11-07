# SPDX-License-Identifier: GPL-3.0-only
"""scripts/darwin_jni_exports.py: JNI export lists derived from Java sources."""
import contextlib
import io
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/darwin_jni_exports.py'
sys.path.insert(0, str(SCRIPT.parent))
import darwin_jni_exports as exports  # noqa: E402


class DarwinJniExportsTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.dir = Path(folder.name)

    def java(self, name, text):
        path = self.dir / name
        path.write_text(text, encoding='utf-8')
        return path

    def run_script(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], capture_output=True, text=True)

    def test_short_names_skip_comments_strings_and_java_overloads(self):
        path = self.java('Native_Thing.java', '''package a.b_c;
            // public static native void commented(int x);
            final class Native_Thing {
                static String s = "native fake(";
                public static void draw(int x) { draw(x, 0); }
                public static native void draw(int x, int y);
                private static native int[] read_all(byte[] data);
            }''')
        self.assertEqual(exports.symbols(path), ['_Java_a_b_1c_Native_1Thing_draw', '_Java_a_b_1c_Native_1Thing_read_1all'])

    def test_overloaded_natives_and_nested_types_are_rejected(self):
        overloaded = self.java('A.java', 'package p; class A { native void f(); native void f(int x); }')
        with self.assertRaisesRegex(SystemExit, 'overloaded native methods need JNI long names: f'):
            exports.symbols(overloaded)
        nested = self.java('B.java', 'package p; class B { static class C { native void g(); } }')
        with self.assertRaisesRegex(SystemExit, 'no nested types'):
            exports.symbols(nested)

    def test_check_compares_list_and_nm_output(self):
        source = self.java('A.java', 'package p; class A { static native void f(); static native void g(); }')
        listing = self.dir / 'a.exports'
        listing.write_text('# generated\n_Java_p_A_f\n_Java_p_A_g\n')
        nm = self.dir / 'a.nm'
        nm.write_text('0000000000000700 T _Java_p_A_f\n0000000000000800 T _Java_p_A_g\n')
        self.assertEqual(self.run_script('--check', listing, '--symbols', nm, source).returncode, 0)
        nm.write_text(nm.read_text() + '0000000000000900 T _png_read_info\n')
        result = self.run_script('--check', listing, '--symbols', nm, source)
        self.assertEqual(result.returncode, 1)
        self.assertIn("extra ['_png_read_info']", result.stderr)
        listing.write_text('_Java_p_A_f\n')
        result = self.run_script('--check', listing, source)
        self.assertEqual(result.returncode, 1)
        self.assertIn("missing ['_Java_p_A_g']", result.stderr)

    def test_repository_lists_match_java_declarations(self):
        for name, sources in (('text', ('java/shiraz/runtime/NativeText.java', 'java/android/graphics/FontFamily.java')),
                              ('path', ('java/shiraz/runtime/NativePath.java',)),
                              ('image', ('java/shiraz/runtime/NativeImage.java',))):
            with self.subTest(name):
                result = self.run_script('--check', ROOT / f'native/darwin/{name}.exports', *(ROOT / s for s in sources))
                self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
