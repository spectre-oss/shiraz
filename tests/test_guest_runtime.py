# SPDX-License-Identifier: GPL-3.0-only
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.runtime import DARWIN_LIBRARIES, darwin_guest_bridge, launch_command


class GuestRuntimeTests(unittest.TestCase):
    def command(self, **changes):
        root = Path("/runtime")
        arguments = {
            "root": root,
            "host": root / "art",
            "environ": {},
            "vm": root / "art/bin/dalvikvm",
            "classpath": "/framework.jar:/app.apk",
            "framework_resources": root / "framework.apk",
            "work": root / "work",
            "data": root / "data",
            "app_lib": root / "work/app_lib",
            "metadata": root / "work/application.properties",
            "package": "test.app",
            "app": "test.App",
            "activity": "test.Activity",
            "width": 360,
            "height": 640,
            "frames": 1,
            "snapshot": None,
            "log_level": "INFO",
            "execution_options": ["-Xint"],
            "device_profile": None,
            "profile_values": {},
            "digitalis": None,
            "version": "test",
        }
        arguments.update(changes)
        return launch_command("Darwin", **arguments)

    def test_explicit_bridge_routes_apk_elfs_without_host_preload(self):
        bridge = Path("/runtime/build/macos/libshiraz-guest.so")
        command, env = self.command(
            guest_bridge=bridge,
            gles=True,
            environ={
                "DYLD_INSERT_LIBRARIES": "/untrusted",
                "LD_PRELOAD": "/untrusted",
                "SHIRAZ_GUEST_EGL_SHIM": "/untrusted",
            },
        )
        self.assertIn("-XX:NativeBridge=" + str(bridge), command)
        self.assertIn("-Xforce-nb-testing", command)
        path = next(
            value for value in command if value.startswith("-Djava.library.path=")
        )
        self.assertIn("/runtime/work/app_lib", path.split(":"))
        self.assertEqual(
            env["SHIRAZ_GUEST_EGL_SHIM"], "/runtime/build/macos/libEGL_shim.so"
        )
        self.assertEqual(env["SHIRAZ_EGL_LIBRARY"], "/runtime/build/macos/libEGL.dylib")
        self.assertFalse(any(key.startswith(("LD_", "DYLD_")) for key in env))

    def test_disabled_bridge_does_not_expose_guest_elf_directory(self):
        command, env = self.command(environ={"SHIRAZ_GUEST_EGL_SHIM": "/untrusted"})
        self.assertFalse(any("NativeBridge=" in value for value in command))
        path = next(
            value for value in command if value.startswith("-Djava.library.path=")
        )
        self.assertNotIn("/runtime/work/app_lib", path)
        self.assertNotIn("SHIRAZ_GUEST_EGL_SHIM", env)

    def test_bridge_selection_rejects_missing_build_and_wrong_abi(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch("shiraz.runtime.native_abi", return_value="arm64-v8a"):
                with self.assertRaisesRegex(FormatError, "not built"):
                    darwin_guest_bridge(
                        root, ["arm64-v8a"], {"SHIRAZ_GUEST_LOADER": "1"}
                    )
                with self.assertRaisesRegex(FormatError, "arm64-v8a"):
                    darwin_guest_bridge(root, ["x86_64"], {"SHIRAZ_GUEST_LOADER": "1"})
                library = root / DARWIN_LIBRARIES / "libshiraz-guest.so"
                library.parent.mkdir(parents=True)
                library.touch()  # Selection only; real ART gates verify executable behavior.
                self.assertEqual(
                    darwin_guest_bridge(
                        root, ["arm64-v8a"], {"SHIRAZ_GUEST_LOADER": "1"}
                    ),
                    library.resolve(),
                )
                self.assertIsNone(darwin_guest_bridge(root, ["arm64-v8a"], {}))
            with self.assertRaisesRegex(FormatError, "0 or 1"):
                darwin_guest_bridge(root, [], {"SHIRAZ_GUEST_LOADER": "unknown"})


if __name__ == "__main__":
    unittest.main()
