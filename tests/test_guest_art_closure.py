# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic closure-policy failures; signing and ART execution are not tested."""

import importlib.util
import plistlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "guest_art_closure",
    Path(__file__).resolve().parents[1] / "scripts/macos_guest_hardened_closure.py",
)
closure = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(closure)


class GuestArtClosureTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.tree = self.root / "closure"
        (self.tree / "bin").mkdir(parents=True)
        (self.tree / "lib64").mkdir()
        self.main = self.tree / "bin/dalvikvm"
        self.library = self.tree / "lib64/helper.so"
        images = []
        for index, path in enumerate((self.main, self.library)):
            source = self.root / f"source{index}"
            source.write_bytes(b"synthetic native source")
            path.write_bytes(source.read_bytes())
            images.append(
                {
                    "source": str(source),
                    "path": str(path),
                    "source_sha256": closure.digest(source),
                    "prepared_sha256": closure.digest(path),
                }
            )
        self.manifest = {
            "tree": str(self.tree),
            "main": str(self.main),
            "images": images,
        }
        self.commands = {
            self.main: {
                "rpaths": ["@loader_path/../lib64"],
                "dependencies": ["@rpath/helper.so", "/usr/lib/libSystem.B.dylib"],
            },
            self.library: {"rpaths": ["@loader_path"], "dependencies": []},
        }
        self.metadata = patch.object(
            closure, "metadata", side_effect=lambda path, env: self.commands[path]
        )
        self.metadata.start()
        self.addCleanup(self.metadata.stop)

    def test_dependency_resolves_only_to_owned_regular_copy(self):
        closure.validate_closure(self.manifest, {})
        self.assertEqual(
            self.manifest["images"][0]["resolved_dependencies"],
            {"@rpath/helper.so": str(self.library)},
        )
        self.library.unlink()
        self.library.symlink_to(self.root / "source1")
        with self.assertRaisesRegex(ValueError, "owned"):
            closure.validate_closure(self.manifest, {})

    def test_external_dependency_and_search_path_are_refused(self):
        self.commands[self.main]["dependencies"] = ["/outside/helper.so"]
        with self.assertRaisesRegex(ValueError, "dependency escape"):
            closure.validate_closure(self.manifest, {})
        self.commands[self.main]["dependencies"] = ["@rpath/helper.so"]
        self.commands[self.main]["rpaths"] = ["@loader_path/../../outside"]
        with self.assertRaisesRegex(ValueError, "nonprivate"):
            closure.validate_closure(self.manifest, {})

    def test_mutation_prevents_any_signing_command(self):
        entitlements = self.root / "jit.plist"
        entitlements.write_bytes(plistlib.dumps(closure.ALLOW_JIT))
        self.library.write_bytes(b"changed after preparation")
        with patch.object(closure, "output") as signing_command:
            with self.assertRaisesRegex(ValueError, "changed before signing"):
                closure.sign_closure(self.manifest, "Public test identity", entitlements, {})
            signing_command.assert_not_called()

    def test_ad_hoc_identity_is_refused_without_signing(self):
        with patch.object(closure, "output") as signing_command:
            with self.assertRaisesRegex(ValueError, "TeamIdentifier"):
                closure.sign_closure(self.manifest, "-", self.root / "unused", {})
            signing_command.assert_not_called()
