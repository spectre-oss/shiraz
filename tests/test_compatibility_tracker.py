# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
spec = importlib.util.spec_from_file_location("tracker", ROOT / "scripts/update_compatibility_tracker.py")
tracker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tracker)


class CompatibilityTrackerTests(unittest.TestCase):
    def test_class_inventory_counts_names_not_resource_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "android.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                for name in ("android/R.class", "android/R$id.class", "android/Manifest.class",
                             "android/view/View.class", "android/view/Surface.class"):
                    archive.writestr(name, b"reference")
            ours = root / "classes/android/view"
            ours.mkdir(parents=True)
            (ours / "View.class").write_bytes(b"implementation")
            result = tracker.class_inventory(jar, root / "classes")
            self.assertEqual((result["present"], result["reference"]), (1, 3))
            self.assertEqual(result["packages"][0], {"package": "android", "present": 0, "reference": 1})

    def fixture(self, root):
        cases = [{"id": "network.a", "category": "Network", "mode": "automatic", "expected": "a"},
                 {"id": "network.b", "category": "Network", "mode": "automatic", "expected": "b"}]
        (root / "catalog.json").write_text(json.dumps(cases))
        result = {**cases[0], "status": "PASS", "durationMs": 1, "detail": "PRIVATE_RESULT_DETAIL"}
        report = {"schemaVersion": 1, "suiteVersion": "fixture", "suiteFingerprint": tracker.fingerprint(root),
                  "catalogSize": 2, "device": {"model": "PRIVATE_DEVICE"}, "results": {"network.a": result}}
        return report

    def test_snapshot_keeps_unrun_separate_and_drops_private_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            report = self.fixture(source)
            path = root / "report.json"
            path.write_text(json.dumps(report))
            result = tracker.probe_snapshot(path, source)
            self.assertEqual(result["categories"][0]["counts"],
                             {"PASS": 1, "FAIL": 0, "UNSUPPORTED": 0, "SKIPPED": 0, "TIMEOUT": 0, "NOT_RUN": 1})
            self.assertNotIn("PRIVATE_", json.dumps(result))
            (source / "changed.java").write_text("different source")
            with self.assertRaisesRegex(ValueError, "exact source"):
                tracker.probe_snapshot(path, source)

    def test_changed_definition_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            report = self.fixture(source)
            report["results"]["network.a"]["expected"] = "different assertion"
            path = root / "report.json"
            path.write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, "definition mismatch"):
                tracker.probe_snapshot(path, source)
