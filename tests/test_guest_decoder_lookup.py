# SPDX-License-Identifier: GPL-3.0-only
"""Fail-closed generation of the pinned decoder's instruction-name index."""

import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import guest_decoder_lookup as lookup  # noqa: E402

TABLE = '''static const char * const insn_name_maps[] = {
 NULL, // ARM64_INS_INVALID
#include "AArch64MappingInsnName.inc"
 "alias",
};
'''
FUNCTION = '''arm64_insn AArch64_map_insn(const char *name)
{
    return original(name);
}

'''
SUFFIX = "// map internal raw vregister\nremaining source\n"
INCLUDED = '"zeta", // a\n"alpha", // b\n"alpha", // duplicate\n'


class DecoderLookupTests(unittest.TestCase):
    def rewrite(self, source=None, included=INCLUDED, expected_count=4):
        body_hash = hashlib.sha256(FUNCTION.encode()).hexdigest()
        return lookup._rewrite(
            source or TABLE + FUNCTION + SUFFIX, included, body_hash, expected_count
        )

    def test_sorted_stable_indices_and_preservation(self):
        output = self.rewrite()
        self.assertIn("shiraz_name_order[] = {4, 2, 3, 1}", output)
        self.assertTrue(output.startswith(TABLE))
        self.assertTrue(output.endswith(SUFFIX))
        self.assertIn("ARR_SIZE(insn_name_maps) == ARR_SIZE(shiraz_name_order) + 1", output)

    def test_unexpected_source_fails_closed(self):
        with self.assertRaises(ValueError):
            self.rewrite(source=TABLE + FUNCTION.replace("original", "changed") + SUFFIX)
        with self.assertRaises(ValueError):
            self.rewrite(source=TABLE.replace("NULL,", "other,") + FUNCTION + SUFFIX)
        with self.assertRaises(ValueError):
            self.rewrite(source=TABLE + TABLE + FUNCTION + SUFFIX)

    def test_unexpected_table_fails_closed(self):
        with self.assertRaises(ValueError):
            self.rewrite(included='"valid",\nSOME_MACRO,\n')
        with self.assertRaises(ValueError):
            self.rewrite(included='/* comment */ SOME_MACRO,\n')
        with self.assertRaises(ValueError):
            self.rewrite(included='"not-ascii-✓",\n')
        with self.assertRaises(ValueError):
            self.rewrite(expected_count=5)

    def test_staging_archive_build_and_compiler_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mapping = root / "source/arch/AArch64"
            mapping.mkdir(parents=True)
            (mapping / "AArch64Mapping.c").write_text("verified mapping")
            (mapping / "AArch64MappingInsnName.inc").write_text("verified names")
            archive = root / "staging.a"
            archive.write_bytes(b"staged")
            calls = []

            def run(command, **kwargs):
                calls.append(command)
                self.assertEqual(kwargs["env"]["ZERO_AR_DATE"], "1")
                return SimpleNamespace(stdout="first.o\nAArch64Mapping.c.o\nlast.o\n")

            with (
                patch.object(lookup, "optimized_mapping", return_value="generated"),
                patch.object(lookup.subprocess, "run", side_effect=run),
            ):
                digest = lookup.build_mapping_object(
                    root / "source", archive, root / "work", "clang", "sdk"
                )
            self.assertEqual(digest, hashlib.sha256(b"generated").hexdigest())
            self.assertEqual(
                [call[1] for call in calls],
                ["-t", "-DCAPSTONE_ARM64_SUPPORT", "-r", str(archive), "-t"],
            )
            self.assertEqual((root / "work/AArch64Mapping.c").read_text(), "generated")

            calls.clear()

            def fail_compiler(command, **kwargs):
                calls.append(command)
                if command[0] == "clang":
                    raise subprocess.CalledProcessError(1, command)
                return SimpleNamespace(stdout="AArch64Mapping.c.o\n")

            with (
                patch.object(lookup, "optimized_mapping", return_value="generated"),
                patch.object(lookup.subprocess, "run", side_effect=fail_compiler),
                self.assertRaises(subprocess.CalledProcessError),
            ):
                lookup.build_mapping_object(root / "source", archive, root / "work", "clang", "sdk")
            self.assertEqual(len(calls), 2)
            self.assertEqual(archive.read_bytes(), b"staged")

    def test_rejects_unexpected_archive_members(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mapping = root / "source/arch/AArch64"
            mapping.mkdir(parents=True)
            (mapping / "AArch64Mapping.c").write_text("verified mapping")
            (mapping / "AArch64MappingInsnName.inc").write_text("verified names")
            archive = root / "staging.a"
            archive.write_bytes(b"staged")
            with (
                patch.object(lookup, "optimized_mapping", return_value="generated"),
                patch.object(
                    lookup.subprocess, "run", return_value=SimpleNamespace(stdout="wrong.o\n")
                ),
                self.assertRaises(ValueError),
            ):
                lookup.build_mapping_object(root / "source", archive, root / "work", "clang", "sdk")
            self.assertEqual(archive.read_bytes(), b"staged")


if __name__ == "__main__":
    unittest.main()
