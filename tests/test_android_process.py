# SPDX-License-Identifier: GPL-3.0-only
"""The process bridge exposes Android build facts and Dart FFI allocators."""
import ctypes
import os
from pathlib import Path
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]


class AndroidProcessBridgeTests(unittest.TestCase):
    def test_build_display_and_allocator_exports(self):
        library = ctypes.CDLL(str(ROOT / 'build/libshiraz-android-process.so'))
        property_get = getattr(library, '__system_property_get')
        property_get.argtypes = (ctypes.c_char_p, ctypes.c_void_p)
        property_get.restype = ctypes.c_int
        display = b'sdk_gphone_x86_64-userdebug 8.1.0 OPM1.171019.011 4944403 dev-keys'
        with patch.dict(os.environ, SHIRAZ_ANDROID_DISPLAY_ID=display.decode(), SHIRAZ_ANDROID_RELEASE='8.1.0'):
            for name, expected in ((b'ro.build.display.id', display),
                                   (b'ro.build.version.release', b'8.1.0'),
                                   (b'ro.build.version.sdk', b'27')):
                output = ctypes.create_string_buffer(92)
                self.assertEqual(property_get(name, output), len(expected))
                self.assertEqual(output.value, expected)
        library.bionic_malloc.argtypes = (ctypes.c_size_t,)
        library.bionic_malloc.restype = ctypes.c_void_p
        library.bionic_free.argtypes = (ctypes.c_void_p,)
        pointer = library.bionic_malloc(32)
        self.assertTrue(pointer)
        library.bionic_free(pointer)


if __name__ == '__main__':
    unittest.main()
