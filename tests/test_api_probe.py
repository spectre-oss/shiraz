# SPDX-License-Identifier: GPL-3.0-only
"""Probe report correctness, catalog completeness and stock-JVM assertion checks."""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


compare = module('compare_api_probe')


def report(status='PASS', mode='automatic', expected='same'):
    return {'schemaVersion': 1, 'suiteVersion': '1', 'results': {
        'test': {'id': 'test', 'status': status, 'mode': mode, 'expected': expected, 'durationMs': 1}}}


class ApiSurfaceNames(unittest.TestCase):
    def test_array_descriptors_normalize_to_element_class(self):
        audit = module('audit_apk_api_surface')
        for name in ('Landroid/view/View;', '[Landroid/view/View;', '[[Landroid/view/View;'):
            self.assertEqual(audit.class_name(name), 'android/view/View')
        for name in ('[I', 'android/view/View', 'L;'):
            with self.assertRaises(ValueError):
                audit.class_name(name)


class ProbeReports(unittest.TestCase):
    def test_regression(self):
        for status in ('FAIL', 'UNSUPPORTED', 'TIMEOUT'):
            with self.subTest(status=status):
                self.assertEqual(compare.compare(report(), report(status))['cases'][0]['comparison'], 'regression')

    def test_skipped_is_lost_coverage(self):
        self.assertEqual(compare.compare(report(), report('SKIPPED'))['cases'][0]['comparison'], 'coverage_lost')

    def test_manual_pass_is_not_automatic_proof(self):
        self.assertEqual(compare.compare(report(mode='manual'), report(mode='manual'))['cases'][0]['comparison'], 'manual_observation')

    def test_definition_change_is_not_regression(self):
        self.assertEqual(compare.compare(report(), report('FAIL', expected='changed'))['cases'][0]['comparison'], 'definition_changed')

    def test_changed_implementation_is_not_comparable(self):
        left, right = report(), report('FAIL')
        left['suiteFingerprint'], right['suiteFingerprint'] = 'old', 'new'
        self.assertEqual(compare.compare(left, right)['cases'][0]['comparison'], 'definition_changed')

    def test_missing_is_not_pass(self):
        other = report(); other['results'] = {}
        self.assertEqual(compare.compare(report(), other)['cases'][0]['candidate'], 'NOT_RUN')
        self.assertEqual(compare.compare(report(), other)['cases'][0]['comparison'], 'coverage_lost')

    def test_reject_malformed_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'report.json'
            for bad in ({}, {'schemaVersion': 9, 'results': {}}, report('UNKNOWN')):
                path.write_text(json.dumps(bad))
                with self.assertRaises(ValueError):
                    compare.load_report(path)
            for duration in (-1, float('inf'), True):
                bad = report(); bad['results']['test']['durationMs'] = duration
                path.write_text(json.dumps(bad))
                with self.assertRaises(ValueError):
                    compare.load_report(path)
            path.write_bytes(b' ' * (compare.LIMIT + 1))
            with self.assertRaises(ValueError):
                compare.load_report(path)

    def test_catalog_covers_dispatch_exactly(self):
        import re
        catalog = json.loads((ROOT / 'tests/api-probe/catalog.json').read_text())
        ids = [entry['id'] for entry in catalog]
        self.assertEqual(len(ids), len(set(ids)))
        automatic = {e['id'] for e in catalog if e['mode'] == 'automatic'}
        dispatch = (ROOT / 'tests/api-probe/ProbeDispatch.java').read_text()
        self.assertEqual(automatic, set(re.findall(r'case "([a-z0-9_]+\.[a-z0-9_]+)"', dispatch)))
        self.assertTrue(all(e['expected'] and e['category'] for e in catalog))

    def test_jvm_file_reports_and_pure_assertions(self):
        classes = ROOT / 'build/android-api-probe/work/classes'
        sdk = ROOT / '.deps/android-sdk/android.jar'
        core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
        if not all(p.exists() for p in (classes, sdk, core)):
            self.skipTest('make api-probe-apk and ART class interfaces required')
        cp = ':'.join(map(str, (classes, core, sdk)))
        source = ROOT / 'tests/api-probe-host/ProbeHostTest.java'
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as directory:
            subprocess.run(['javac', '--release', '8', '-cp', cp, '-d', directory, str(source)], check=True, timeout=60)
            subprocess.run(['java', '-cp', directory + ':' + cp, 'org.shiraz.apiprobe.ProbeHostTest', directory], check=True, timeout=30)


if __name__ == '__main__':
    unittest.main()
