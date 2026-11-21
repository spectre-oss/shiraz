# SPDX-License-Identifier: GPL-3.0-only
import copy
import json
import unittest
from pathlib import Path

from shiraz.job_definition import LONG_MAX, MAX_BYTES, validate


class JobDefinitionTests(unittest.TestCase):
    def test_shared_java_wire_fixture(self):
        value = json.loads((Path(__file__).parent / 'fixtures/job-definition-v1.json').read_text())
        self.assertEqual(validate(value), value)

    def job(self, **changes):
        return dict(
            version=1,
            id=17,
            component='test.jobs.Worker',
            persisted=True,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={},
            **changes,
        )

    def test_typed_roundtrip_and_independent_ownership(self):
        job = self.job()
        job['extras'] = {
            'integer': dict(type='int', value=-(1 << 31)),
            'large': dict(type='long', value=LONG_MAX),
            'nested': dict(
                type='bundle', value={'values': dict(type='string[]', value=['سلام', None, ''])}
            ),
            'nullable': dict(type='bundle', value=None),
            'double': dict(type='double', value=1.25),
            'bools': dict(type='boolean[]', value=[True, False]),
            'empty': dict(type='long[]', value=[]),
            'null-array': dict(type='double[]', value=None),
        }
        result = validate(job)
        self.assertEqual(result, job)
        result['extras']['nested']['value']['values']['value'].append('changed')
        self.assertEqual(len(job['extras']['nested']['value']['values']['value']), 3)

    def test_storage_version_is_explicit_and_strict(self):
        for required in (False, True):
            value = dict(self.job(), version=2, requires_storage_not_low=required)
            self.assertEqual(validate(value), value)
        for changes in (
            {'version': 1, 'requires_storage_not_low': True},
            {'version': 2},
            {'version': 3, 'requires_storage_not_low': True},
            *(
                {'version': 2, 'requires_storage_not_low': item}
                for item in (None, 0, 1, 'true', [])
            ),
        ):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate(dict(self.job(), **changes))

    def test_identity_constraints_and_timing_fail_closed(self):
        for changes in (
            {'uid': 10000},
            {'package': 'test.other'},
            {'network': 1},
            {'version': 2},
            {'version': True},
            {'id': True},
            {'id': 1 << 31},
            {'component': '../Worker'},
            {'component': 'x' * 1025},
            {'persisted': 1},
            {'minimum_latency_ms': -1},
            {'deadline_ms': 0.5},
            {'interval_ms': 1},
            {'flex_ms': 1},
            {'backoff_policy': 2},
            {'initial_backoff_ms': 9999},
            {'minimum_latency_ms': LONG_MAX + 1},
        ):
            with self.subTest(changes=changes):
                job = self.job()
                job.update(changes)
                before = copy.deepcopy(job)
                with self.assertRaises(ValueError):
                    validate(job)
                self.assertEqual(job, before)
        job = self.job()
        del job['deadline_ms']
        with self.assertRaises(ValueError):
            validate(job)

    def test_periodic_normalization_contract_and_extreme_durations(self):
        for interval, flex in ((900000, 300000), (900000, 900000), (LONG_MAX, LONG_MAX // 20)):
            job = self.job()
            job.update(interval_ms=interval, flex_ms=flex)
            self.assertEqual(validate(job), job)
            for invalid in (
                {'flex_ms': flex - 1} if flex == 300000 else {'flex_ms': 1},
                {'minimum_latency_ms': 1},
                {'deadline_ms': 0},
            ):
                with self.assertRaises(ValueError):
                    validate(dict(job, **invalid))

    def test_bad_extra_types_values_and_shapes(self):
        for node in (
            {'type': 'int', 'value': True},
            {'type': 'long', 'value': LONG_MAX + 1},
            {'type': 'double', 'value': float('nan')},
            {'type': 'double', 'value': float('inf')},
            {'type': 'double', 'value': 10**400},
            {'type': 'double', 'value': True},
            {'type': 'string', 'value': '\ud800'},
            {'type': 'string', 'value': 2},
            {'type': 'boolean', 'value': 1},
            {'type': 'int[]', 'value': [None]},
            {'type': 'string[]', 'value': 'text'},
            {'type': 'bundle[]', 'value': []},
            {'type': 'parcelable', 'value': {}},
            {'type': 'bundle', 'value': []},
            {'type': 'string'},
            {'type': 'string', 'value': '', 'extra': 1},
        ):
            with self.subTest(node=node), self.assertRaises(ValueError):
                validate(dict(self.job(), extras={'key': node}))

    def test_depth_count_cycles_and_utf8_byte_budget(self):
        nested = {}
        for _ in range(10):
            nested = {'next': dict(type='bundle', value=nested)}
        cycle = {}
        cycle['self'] = dict(type='bundle', value=cycle)
        for extras in (
            nested,
            cycle,
            {str(i): dict(type='int', value=i) for i in range(1025)},
            {'array': dict(type='int[]', value=[0] * 1025)},
            {str(i): dict(type='int[]', value=[0] * 1024) for i in range(4)},
            {'text': dict(type='string', value='€' * (MAX_BYTES // 2))},
        ):
            with self.assertRaises(ValueError):
                validate(dict(self.job(), extras=extras))
        self.assertEqual(
            validate(dict(self.job(), extras={'text': dict(type='string', value='€' * 100)}))['id'],
            17,
        )
