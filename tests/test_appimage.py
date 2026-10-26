# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
from pathlib import Path
import tempfile
import struct
import unittest
from unittest.mock import patch
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_appimage', ROOT/'scripts/build_appimage.py')
packaging = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packaging)

class AppImageTests(unittest.TestCase):
    def report(self):
        return {'host_native_candidate': True, 'native_abis': [], 'manifest': {'package': 'org.example.app', 'min_sdk': '21',
                'launcher_activities': [{'target': 'org.example.app.Main'}]}}

    def test_rejects_unlaunchable_or_incompatible_apks(self):
        self.assertEqual(packaging.validate_report(self.report()),
                         ['org.example.app', 'android.app.Application', 'org.example.app.Main'])
        for minimum in ('28', 'not-an-api'):
            report = self.report(); report['manifest']['min_sdk'] = minimum
            with self.assertRaises(packaging.FormatError): packaging.validate_report(report)
        for key, value in [('application_enabled', 'false'), ('package', 'bad\npackage'), ('launcher_activities', [])]:
            report = self.report(); report['manifest'][key] = value
            with self.assertRaises(packaging.FormatError): packaging.validate_report(report)
        report = self.report(); report['native_abis'] = ['arm64-v8a']
        with self.assertRaises(packaging.FormatError): packaging.validate_report(report)

    def test_arm64_requires_matching_native_abi(self):
        report = self.report(); report['native_abis'] = ['arm64-v8a']
        self.assertEqual(packaging.validate_report(report, 'aarch64')[0], 'org.example.app')
        report['native_abis'] = ['x86_64']
        with self.assertRaises(packaging.FormatError): packaging.validate_report(report, 'aarch64')

    def test_rejects_mixed_architecture_package_elf(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work)/'library.so'
            header = bytearray(64); header[:6] = b'\x7fELF\x02\x01'
            struct.pack_into('<H', header, 18, 183); path.write_bytes(header)
            packaging.require_architecture(path, 'aarch64')
            with self.assertRaisesRegex(RuntimeError, 'architecture mismatch'):
                packaging.require_architecture(path, 'x86_64')

    def test_cached_tool_must_match_pinned_hash_before_execution(self):
        with tempfile.TemporaryDirectory() as work:
            cache = Path(work); (cache/'appimagetool').write_bytes(b'not the trusted tool')
            with patch.object(packaging.urllib.request, 'urlopen') as network:
                with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                    packaging.fetch('appimagetool', cache)
                network.assert_not_called()

    def test_conflicting_dependency_names_fail_instead_of_silent_replacement(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); first = root/'first'; second = root/'second'
            first.write_bytes(b'one'); second.write_bytes(b'two')
            libraries = packaging.Libraries(root/'AppDir')
            with patch.object(libraries, 'scan'):
                libraries.add(first, 'libx.so')
                libraries.add(first, 'libx-alias.so')
                self.assertEqual((libraries.directory/'libx-alias.so').read_bytes(), b'one')
                self.assertFalse((libraries.directory/'libx-alias.so').readlink().is_absolute())
                with self.assertRaisesRegex(RuntimeError, 'Conflicting library'):
                    libraries.add(second, 'libx.so')
                with self.assertRaisesRegex(RuntimeError, 'Invalid library'):
                    libraries.add(first, '../outside')

    def test_runtime_library_path_overrides_embedded_build_paths(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); local = root/'runtime'; local.mkdir()
            selected = local/'libdep.so.0'; selected.write_bytes(b'\x7fELFpayload')
            source = root/'libcaller.so'; source.write_bytes(b'\x7fELFcaller')
            libraries = packaging.Libraries(root/'AppDir')
            libraries.env['LD_LIBRARY_PATH'] = str(local)
            result = SimpleNamespace(stdout='libdep.so.0 => /system/libdep.so.0 (0x123)\n')
            with patch.object(packaging.subprocess, 'run', return_value=result), patch.object(libraries, 'add') as add:
                libraries.scan(source)
                add.assert_called_once_with(selected, 'libdep.so.0')

    def test_host_egl_is_not_replaced_by_android_lookup_alias(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); build = root/'build'; system = root/'system'
            build.mkdir(); system.mkdir()
            alias = build/'libEGL.so.1'; alias.write_bytes(b'\x7fELFbridge')
            driver = system/'libEGL.so.1'; driver.write_bytes(b'\x7fELFdriver')
            caller = root/'caller.so'; caller.write_bytes(b'\x7fELFcaller')
            with patch.dict('os.environ', SHIRAZ_SYSTEM_LIBRARY_PATH=str(system)):
                libraries = packaging.Libraries(root/'AppDir')
                libraries.env['LD_LIBRARY_PATH'] = str(build)
                result = SimpleNamespace(stdout=f'libEGL.so.1 => {alias} (0x123)\n')
                with patch.object(packaging.subprocess, 'run', return_value=result):
                    libraries.scan(caller)
                    libraries.system('libEGL.so.1')
            self.assertEqual((libraries.directory/'libEGL.so.1').read_bytes(), driver.read_bytes())

    def test_explicit_system_roots_also_control_transitive_resolution(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); system = root/'selected-system'; system.mkdir()
            selected = system/'libudev.so.1'; selected.write_bytes(b'\x7fELFselected')
            source = root/'caller.so'; source.write_bytes(b'\x7fELFcaller')
            with patch.dict('os.environ', SHIRAZ_SYSTEM_LIBRARY_PATH=str(system)):
                libraries = packaging.Libraries(root/'AppDir')
            result = SimpleNamespace(stdout='libudev.so.1 => /other-system/libudev.so.1 (0x123)\n')
            with patch.object(packaging.subprocess, 'run', return_value=result), patch.object(libraries, 'add') as add:
                libraries.scan(source)
                add.assert_called_once_with(selected, 'libudev.so.1')

    def test_transitive_reference_reuses_selected_soname(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); first = root/'first'; second = root/'second'
            first.write_bytes(b'one'); second.write_bytes(b'two')
            libraries = packaging.Libraries(root/'AppDir')
            with patch.object(libraries, 'scan'):
                libraries.add(first, 'libdep.so.0')
            caller = root/'caller.so'; caller.write_bytes(b'\x7fELFcaller')
            result = SimpleNamespace(stdout=f'libdep.so.0 => {second} (0x123)\n')
            with patch.object(packaging.subprocess, 'run', return_value=result):
                libraries.scan(caller)
            self.assertEqual((libraries.directory/'libdep.so.0').read_bytes(), b'one')
            with self.assertRaisesRegex(RuntimeError, 'Conflicting library'):
                libraries.add(second, 'libdep.so.0')

    def test_remove_read_only_staging_preserves_symlink_targets(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work); stage = root/'stage'; outside = root/'outside'
            outside.mkdir(); (outside/'keep').write_text('keep')
            nested = stage/'share'/'xkb'; nested.mkdir(parents=True)
            (nested/'data').write_text('data')
            (stage/'external').symlink_to(outside, target_is_directory=True)
            nested.chmod(0o555); nested.parent.chmod(0o555)
            packaging.remove_staging_tree(stage)
            self.assertFalse(stage.exists())
            self.assertEqual((outside/'keep').read_text(), 'keep')
            stage.symlink_to(outside, target_is_directory=True)
            with self.assertRaisesRegex(RuntimeError, 'symlink'):
                packaging.remove_staging_tree(stage)
