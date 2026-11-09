# SPDX-License-Identifier: GPL-3.0-only
"""Snapshot/report wiring; matched records do not waive native spill risk."""
import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_binding_evidence import augment_binding_report
from test_guest_prepared_cleanup_file import image_fixture
from test_guest_prepared_cleanup_record import record_fixture
from test_guest_tpidr_audit import BindingElfFixture


class PreparedCleanupBindingTests(unittest.TestCase):
    def test_audit_requires_explicit_bridge_and_preserves_optional_absence(self):
        fixture = BindingElfFixture();image = image_fixture();report = fixture.proof()
        report['prepared_cleanup'] = record_fixture(image)
        self.assertIsNone(fixture.metadata(report).prepared_cleanup_proof)
        with tempfile.TemporaryDirectory() as directory:
            bridge = Path(directory)/'bridge.so';bridge.write_bytes(image)
            report['native_bridge'] = {'path': str(bridge.resolve()),
                                      'sha256': hashlib.sha256(image).hexdigest(),
                                      'verification': 'observed-before-and-after-process'}
            metadata = fixture.metadata(report, bridge)
            self.assertIsNotNone(metadata.prepared_cleanup_proof)
            self.assertFalse(metadata.prepared_cleanup_proof.effects.native_memory_purity_verified)
            self.assertFalse(metadata.prepared_cleanup_proof.effects.consumer_proof)
            original = metadata.resolve(0x1500)
            absent = copy.deepcopy(report);del absent['prepared_cleanup']
            without_snapshot = fixture.metadata(absent, bridge)
            self.assertIsNone(without_snapshot.prepared_cleanup_proof)
            self.assertEqual(without_snapshot.resolve(0x1500), original)

    def test_audit_refuses_null_bad_schema_and_trusted_file_mismatch(self):
        fixture = BindingElfFixture();image = image_fixture();report = fixture.proof()
        for record in (None, {}, {'profile': True}):
            report['prepared_cleanup'] = record
            with self.subTest(record=record), self.assertRaises(ValueError):fixture.metadata(report)
        with tempfile.TemporaryDirectory() as directory:
            bridge = Path(directory)/'bridge.so';bridge.write_bytes(image)
            report['native_bridge'] = {'path': str(bridge.resolve()),
                                      'sha256': hashlib.sha256(image).hexdigest(),
                                      'verification': 'observed-before-and-after-process'}
            report['prepared_cleanup'] = record_fixture(image)
            report['prepared_cleanup']['body_hex'] = '00'*216
            self.assertIsNone(fixture.metadata(report).prepared_cleanup_proof)
            with self.assertRaisesRegex(ValueError, 'mapped/file'):
                fixture.metadata(report, bridge)

    def test_augment_matches_exact_hashed_bytes_and_requires_bridge_identity(self):
        image = image_fixture()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve();elf = root/'libfixture.so';elf.write_bytes(b'fixture')
            digest = hashlib.sha256(elf.read_bytes()).hexdigest();bridge = root/'bridge.so';bridge.write_bytes(image)
            raw = root/'raw.json';output = root/'report.json'
            report = {'schema': 1, 'namespace': 'nativebridge', 'snapshot': 'post-link-before-open',
                      'prepared_cleanup': record_fixture(image),
                      'modules': [{'name': elf.name, 'file': str(elf), 'bindings': []}]}
            def run(with_bridge):
                raw.write_text(json.dumps(report))
                identity = {'native_bridge': bridge, 'bridge_sha256': hashlib.sha256(image).hexdigest()} if with_bridge else {}
                augment_binding_report(raw, output, {elf.name: digest}, root, {elf.name}, **identity)
            with self.assertRaisesRegex(ValueError, 'requires bridge identity'):run(False)
            run(True)
            self.assertEqual(json.loads(output.read_text())['prepared_cleanup'], report['prepared_cleanup'])
            report['prepared_cleanup']['tables'][0]['offset'] += 8
            with self.assertRaises(ValueError):run(True)
            report['prepared_cleanup'] = None
            with self.assertRaisesRegex(ValueError, 'object required'):run(True)


if __name__ == '__main__':
    unittest.main()
