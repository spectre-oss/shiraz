# SPDX-License-Identifier: GPL-3.0-only
import plistlib
import tempfile
import unittest
from pathlib import Path

from shiraz.apk import FormatError
from shiraz.darwin_app import prepare_app_bundle


class DarwinAppTests(unittest.TestCase):
    def test_bundle_keeps_executable_bytes_and_private_host_link(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            host = root / "art"
            (host / "lib64").mkdir(parents=True)
            vm = root / "dalvikvm"
            original = b"\xcf\xfa\xed\xfe" + bytes(range(256))
            vm.write_bytes(original)
            vm.chmod(0o755)
            executable = prepare_app_bundle(root / "run", vm, host, "org.example.app")
            self.assertEqual(executable.read_bytes(), original)
            self.assertTrue(executable.stat().st_mode & 0o100)
            contents = executable.parent.parent
            self.assertEqual((contents / "lib64").resolve(), (host / "lib64").resolve())
            info = plistlib.loads((contents / "Info.plist").read_bytes())
            self.assertEqual(info["CFBundleExecutable"], executable.name)
            self.assertTrue(info["NSCameraUsageDescription"])
            self.assertTrue(info["NSMicrophoneUsageDescription"])
            self.assertEqual(prepare_app_bundle(root / "run", vm, host, "org.example.app"), executable)
            stable = plistlib.loads((contents / "Info.plist").read_bytes())["CFBundleIdentifier"]
            self.assertEqual(stable, info["CFBundleIdentifier"])
            executable.unlink()
            executable.symlink_to(vm)
            with self.assertRaises(FormatError):
                prepare_app_bundle(root / "run", vm, host, "org.example.app")

    def test_non_macho_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            vm = root / "vm"
            vm.write_bytes(b"not Mach-O")
            with self.assertRaises(FormatError):
                prepare_app_bundle(root / "run", vm, root, "org.example.app")
