# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic validator failure cases; actual dyld observation requires main's gate."""

import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from native_closure_inventory import validate_native_inventory


def report(paths):
    lines = [f"M5_NATIVE_CLOSURE_BEGIN count={len(paths)} snapshot=end-of-main"]
    lines.extend(
        f"M5_NATIVE_CLOSURE_PATH index={index} hex={str(path).encode().hex()}"
        for index, path in enumerate(paths)
    )
    lines.append(f"M5_NATIVE_CLOSURE_END count={len(paths)}")
    return "\n".join(lines) + "\n"


class NativeClosureInventoryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.paths = []
        images = []
        for name in ("main", "bridge.so", "helper.so", "unused-seed.so"):
            path = self.root / name
            path.write_bytes(name.encode())
            self.paths.append(path)
            images.append(
                {
                    "path": str(path),
                    "signed_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                }
            )
        self.manifest = {"signed": True, "images": images}
        self.mandatory = self.paths[:3]
        self.observed = [*self.mandatory, Path("/usr/lib/libSystem.B.dylib")]

    def test_requires_observed_mandatory_but_not_unused_seed(self):
        result = validate_native_inventory(
            report(self.observed), self.manifest, self.mandatory
        )
        self.assertEqual(result["snapshot"], "end-of-main")
        self.assertEqual(
            set(result["observed_non_system_sha256"]), set(map(str, self.mandatory))
        )
        self.assertNotIn(str(self.paths[3]), result["observed_non_system_sha256"])

    def test_foreign_late_loaded_image_rejected(self):
        with self.assertRaisesRegex(ValueError, "foreign non-system"):
            validate_native_inventory(
                report([*self.observed, self.root / "foreign.so"]),
                self.manifest,
                self.mandatory,
            )

    def test_truncated_report_rejected(self):
        truncated = report(self.observed).rsplit("M5_NATIVE_CLOSURE_END", 1)[0]
        with self.assertRaisesRegex(ValueError, "truncated"):
            validate_native_inventory(truncated, self.manifest, self.mandatory)

    def test_missing_mandatory_helper_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing mandatory"):
            validate_native_inventory(
                report(self.observed[:2]), self.manifest, self.mandatory
            )

    def test_tampered_signed_copy_rejected(self):
        self.paths[1].write_bytes(b"tampered-after-signing")
        with self.assertRaisesRegex(ValueError, "changed since signing"):
            validate_native_inventory(
                report(self.observed), self.manifest, self.mandatory
            )


if __name__ == "__main__":
    unittest.main()
