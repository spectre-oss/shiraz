# SPDX-License-Identifier: GPL-3.0-only
"""Unit tests for cross-platform paths, host ABI/page detection, and Darwin app metadata."""

import plistlib
import tempfile
import unittest
from pathlib import Path, PurePosixPath, PureWindowsPath

from shiraz.architecture import (
    elf_needs_copy_load,
    host_page_size,
    native_abi,
    supports_native_abis,
)
from shiraz.darwin_app import prepare_app_bundle
from shiraz.host_layout import host_out_dir, host_tag
from shiraz.platform_paths import (
    app_cache_dir,
    app_data_dir,
    cache_home,
    config_home,
    data_home,
    executable_name,
    host_os,
    host_shared_library_name,
    shared_library_name,
    state_home,
)


class PlatformPathsTest(unittest.TestCase):
    def test_host_os_normalization(self):
        self.assertEqual(host_os('Linux'), 'linux')
        self.assertEqual(host_os('Darwin'), 'darwin')
        self.assertEqual(host_os('Windows'), 'windows')
        self.assertEqual(host_os('MSYS_NT-10.0'), 'windows')
        with self.assertRaises(ValueError):
            host_os('Plan9')

    def test_binary_and_library_naming(self):
        self.assertEqual(shared_library_name('shiraz-video', 'Linux'), 'libshiraz-video.so')
        self.assertEqual(shared_library_name('shiraz-video', 'Darwin'), 'libshiraz-video.so')
        self.assertEqual(shared_library_name('shiraz-video', 'Windows'), 'shiraz-video.dll')

        self.assertEqual(host_shared_library_name('shiraz-video', 'Linux'), 'libshiraz-video.so')
        self.assertEqual(
            host_shared_library_name('shiraz-video', 'Darwin'), 'libshiraz-video.dylib'
        )
        self.assertEqual(host_shared_library_name('shiraz-video', 'Windows'), 'shiraz-video.dll')

        self.assertEqual(executable_name('shiraz-preview', 'Linux'), 'shiraz-preview')
        self.assertEqual(executable_name('shiraz-preview', 'Darwin'), 'shiraz-preview')
        self.assertEqual(executable_name('shiraz-preview', 'Windows'), 'shiraz-preview.exe')
        self.assertEqual(executable_name('shiraz-preview.exe', 'Windows'), 'shiraz-preview.exe')

    def test_linux_xdg_defaults_and_overrides(self):
        env = {'HOME': '/home/alice'}
        self.assertEqual(data_home(env, 'Linux'), PurePosixPath('/home/alice/.local/share/shiraz'))
        self.assertEqual(cache_home(env, 'Linux'), PurePosixPath('/home/alice/.cache/shiraz'))
        self.assertEqual(config_home(env, 'Linux'), PurePosixPath('/home/alice/.config/shiraz'))
        self.assertEqual(state_home(env, 'Linux'), PurePosixPath('/home/alice/.local/state/shiraz'))
        self.assertEqual(
            app_data_dir('net.osmand.plus', env, 'Linux'),
            PurePosixPath('/home/alice/.local/share/shiraz/net.osmand.plus'),
        )
        override = {'HOME': '/home/alice', 'XDG_DATA_HOME': '/srv/data'}
        self.assertEqual(data_home(override, 'Linux'), PurePosixPath('/srv/data/shiraz'))

    def test_macos_library_defaults_and_override(self):
        env = {'HOME': '/Users/alice'}
        self.assertEqual(
            data_home(env, 'Darwin'),
            PurePosixPath('/Users/alice/Library/Application Support/Shiraz'),
        )
        self.assertEqual(
            cache_home(env, 'Darwin'),
            PurePosixPath('/Users/alice/Library/Caches/Shiraz'),
        )
        self.assertEqual(
            app_data_dir('net.osmand.plus', env, 'Darwin'),
            PurePosixPath('/Users/alice/Library/Application Support/Shiraz/net.osmand.plus'),
        )
        self.assertEqual(
            app_cache_dir('net.osmand.plus', env, 'Darwin'),
            PurePosixPath('/Users/alice/Library/Caches/Shiraz/net.osmand.plus'),
        )
        isolated = {'HOME': '/Users/alice', 'SHIRAZ_HOST_XDG_DATA_HOME': '/tmp/shiraz-test'}
        self.assertEqual(data_home(isolated, 'Darwin'), PurePosixPath('/tmp/shiraz-test/shiraz'))

    def test_windows_appdata_defaults_and_fallback(self):
        env = {
            'LOCALAPPDATA': r'C:\Users\Alice\AppData\Local',
            'APPDATA': r'C:\Users\Alice\AppData\Roaming',
            'USERPROFILE': r'C:\Users\Alice',
        }
        self.assertEqual(
            data_home(env, 'Windows'),
            PureWindowsPath(r'C:\Users\Alice\AppData\Local\Shiraz'),
        )
        self.assertEqual(
            cache_home(env, 'Windows'),
            PureWindowsPath(r'C:\Users\Alice\AppData\Local\Shiraz\Cache'),
        )
        self.assertEqual(
            config_home(env, 'Windows'),
            PureWindowsPath(r'C:\Users\Alice\AppData\Roaming\Shiraz'),
        )
        self.assertEqual(
            state_home(env, 'Windows'),
            PureWindowsPath(r'C:\Users\Alice\AppData\Local\Shiraz\State'),
        )
        self.assertEqual(
            app_data_dir('net.osmand.plus', env, 'Windows'),
            PureWindowsPath(r'C:\Users\Alice\AppData\Local\Shiraz\net.osmand.plus'),
        )
        fallback = {'USERPROFILE': r'C:\Users\Bob'}
        self.assertEqual(
            data_home(fallback, 'Windows'),
            PureWindowsPath(r'C:\Users\Bob\AppData\Local\Shiraz'),
        )

    def test_rejects_invalid_package_names(self):
        env = {'HOME': '/Users/alice'}
        for bad in ('', 'noperiod', '../escape', 'com.example/evil'):
            with self.assertRaises(ValueError):
                app_data_dir(bad, env, 'Darwin')

    def test_cross_platform_abi_and_copy_load(self):
        self.assertEqual(native_abi('x86_64'), 'x86_64')
        self.assertEqual(native_abi('AMD64'), 'x86_64')
        self.assertEqual(native_abi('x64'), 'x86_64')
        self.assertEqual(native_abi('aarch64'), 'arm64-v8a')
        self.assertEqual(native_abi('arm64'), 'arm64-v8a')
        self.assertEqual(native_abi('ARM64'), 'arm64-v8a')
        self.assertTrue(supports_native_abis(['arm64-v8a'], 'ARM64'))
        self.assertTrue(supports_native_abis(['x86_64'], 'AMD64'))

        self.assertEqual(host_page_size('Darwin', 'arm64'), 16384)
        self.assertEqual(host_page_size('Windows', 'AMD64'), 4096)
        self.assertEqual(host_page_size('Linux', 'x86_64'), 4096)
        self.assertTrue(elf_needs_copy_load(4096, page_size=16384))
        self.assertFalse(elf_needs_copy_load(16384, page_size=16384))
        self.assertFalse(elf_needs_copy_load(4096, page_size=4096))

    def test_host_layout_tags(self):
        self.assertEqual(host_tag('Linux'), 'linux-x86')
        self.assertEqual(host_tag('Darwin'), 'darwin-x86')
        self.assertEqual(host_tag('Windows'), 'windows-x86')
        self.assertEqual(host_out_dir(system='Windows'), Path('out/host/windows-x86'))

    def test_darwin_app_bundle_includes_location_and_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            vm = root / 'dalvikvm64'
            vm.write_bytes(b'\xcf\xfa\xed\xfe' + b'\x00' * 128)
            host = root / 'host'
            (host / 'lib64').mkdir(parents=True)
            exe = prepare_app_bundle(root / 'work', vm, host, 'net.osmand.plus', name='OsmAnd')
            plist = plistlib.loads((exe.parent.parent / 'Info.plist').read_bytes())
            self.assertIn('NSLocationUsageDescription', plist)
            self.assertIn('NSLocationWhenInUseUsageDescription', plist)
            self.assertIn('CFBundleShortVersionString', plist)
            self.assertTrue(plist['CFBundleShortVersionString'])


if __name__ == '__main__':
    unittest.main()
