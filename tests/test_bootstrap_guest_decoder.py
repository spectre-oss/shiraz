# SPDX-License-Identifier: GPL-3.0-only
"""Atomic publication and recovery for the decoder build."""

import contextlib
import hashlib
import io
import json
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import bootstrap_guest_decoder as bootstrap  # noqa: E402


class DecoderBootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.out = Path(self.temporary.name) / "capstone"
        self.out.mkdir()
        archive = self.out / "source.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            for name in ("AArch64Mapping.c", "AArch64MappingInsnName.inc"):
                data = b"verified fixture"
                member = tarfile.TarInfo("upstream/arch/AArch64/" + name)
                member.size = len(data)
                stream.addfile(member, io.BytesIO(data))
            for name in ("cs.c", "SStream.c", "SStream.h"):
                data = b"verified stream fixture"
                member = tarfile.TarInfo("upstream/" + name)
                member.size = len(data)
                stream.addfile(member, io.BytesIO(data))
        self.original_out, self.original_hash = bootstrap.OUT, bootstrap.SHA256
        bootstrap.OUT = self.out
        bootstrap.SHA256 = hashlib.sha256(archive.read_bytes()).hexdigest()
        self.addCleanup(setattr, bootstrap, "OUT", self.original_out)
        self.addCleanup(setattr, bootstrap, "SHA256", self.original_hash)
        self.builds = 0

    def check_output(self, command, **_kwargs):
        if command[:2] == ["xcrun", "-f"]:
            return "/test/clang\n"
        if command[0] == "xcrun":
            return "/test/sdk\n"
        return "clang test version\n"

    def run_command(self, command, **kwargs):
        self.assertEqual(kwargs["env"]["ZERO_AR_DATE"], "1")
        if command[:2] == ["cmake", "--build"]:
            self.builds += 1
            (Path(command[2]) / "libcapstone.a").write_bytes(b"unoptimized staging")
        elif command[0] == "cmake":
            Path(command[command.index("-B") + 1]).mkdir(parents=True, exist_ok=True)
        return SimpleNamespace()

    @staticmethod
    def overlay(_source, archive, _work, _compiler, _sdk):
        archive.write_bytes(b"optimized archive")
        return hashlib.sha256(b"generated mapping").hexdigest()

    def run_bootstrap(self, overlay=None):
        with (
            patch.object(bootstrap, "optimized_mapping", return_value="generated mapping"),
            patch.object(
                bootstrap,
                "generate_stream",
                return_value={
                    "cs.c": "generated",
                    "SStream.c": "generated",
                    "SStream.h": "generated",
                },
            ),
            patch.object(bootstrap, "build_mapping_object", side_effect=overlay or self.overlay),
            patch.object(bootstrap.subprocess, "check_output", side_effect=self.check_output),
            patch.object(bootstrap.subprocess, "run", side_effect=self.run_command),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            bootstrap.main()

    def test_reuse_failed_build_and_repair(self):
        archive = self.out / "build/libcapstone.a"
        stamp = self.out / "provenance.json"
        self.run_bootstrap()
        self.assertEqual(archive.read_bytes(), b"optimized archive")
        self.assertEqual(self.builds, 1)
        self.run_bootstrap()
        self.assertEqual(self.builds, 1)
        state = json.loads(stamp.read_text())
        state["lookup_generator_sha256"] = "force rebuild"
        stamp.write_text(json.dumps(state))

        def fail(*_args):
            raise RuntimeError("compiler failure")

        with self.assertRaisesRegex(RuntimeError, "compiler failure"):
            self.run_bootstrap(fail)
        self.assertEqual(archive.read_bytes(), b"optimized archive")

        def wrong_hash(_source, staged, _work, _compiler, _sdk):
            staged.write_bytes(b"unverified archive")
            return "wrong hash"

        with self.assertRaisesRegex(ValueError, "generation changed"):
            self.run_bootstrap(wrong_hash)
        self.assertEqual(archive.read_bytes(), b"optimized archive")
        archive.write_bytes(b"corrupted")
        self.run_bootstrap()
        self.assertEqual(archive.read_bytes(), b"optimized archive")
        self.assertEqual(self.builds, 4)
        self.assertFalse(list(self.out.glob("tmp*")))

    def test_rejects_symlinked_published_archive(self):
        (self.out / "build").mkdir()
        target = self.out / "external"
        target.write_bytes(b"untouched")
        (self.out / "build/libcapstone.a").symlink_to(target)
        with self.assertRaisesRegex(ValueError, "symlink"):
            self.run_bootstrap()
        self.assertEqual(target.read_bytes(), b"untouched")

    def test_staging_refuses_extra_symlinks_without_replacing_archive(self):
        self.run_bootstrap()
        archive = self.out / "build/libcapstone.a"
        original = archive.read_bytes()
        (self.out / "src/unexpected-link").symlink_to(self.out / "external")
        stamp = self.out / "provenance.json"
        state = json.loads(stamp.read_text())
        state["stream_generator_sha256"] = "force rebuild"
        stamp.write_text(json.dumps(state))
        with self.assertRaisesRegex(ValueError, "source contains a symlink"):
            self.run_bootstrap()
        self.assertEqual(archive.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
