# SPDX-License-Identifier: GPL-3.0-only
"""Pure scalar intersection controls; no pointer merging or runtime proof."""

import itertools
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_scalar_widening import ScalarWidening


class ScalarWideningTests(unittest.TestCase):
    def test_initial_variants_stay_exact_then_all_observations_intersect(self):
        widening = ScalarWidening(1)
        states = [({'x0': 4, 'x1': 7}, {'x0', 'x1'}),
                  ({'x0': 5, 'x1': 7}, {'x0', 'x1'}),
                  ({'x0': 6, 'x1': 7}, {'x1'}),
                  ({'x0': 7, 'x1': 7}, {'x0', 'x1'})]
        for index, (constants, upper) in enumerate(states):
            result = widening.observe('pc', constants, upper)
            self.assertEqual(result.widened, index == 3)
            if index < 3:
                self.assertEqual(dict(result.constants), constants)
                self.assertEqual(result.zero_upper, upper)
                self.assertFalse(result.dropped_constants or result.dropped_zero_upper)
            else:
                self.assertEqual(result.constants, (('x1', 7),))
                self.assertEqual(result.zero_upper, frozenset({'x1'}))
                self.assertEqual(result.dropped_constants, frozenset({'x0'}))
                self.assertEqual(result.dropped_zero_upper, frozenset({'x0'}))

    def test_missing_facts_remain_unknown_and_lost_facts_never_reappear(self):
        widening = ScalarWidening(1, max_variants=2)
        widening.observe(0, {'x0': 1}, {'x0'})
        result = widening.observe(0, {'x1': 2}, {'x1'})
        self.assertEqual(result.constants, ())
        self.assertEqual(result.zero_upper, frozenset())
        for _ in range(20):
            result = widening.observe(0, {'x0': 1, 'x1': 2}, {'x0', 'x1'})
            self.assertEqual(result.constants, ())
            self.assertFalse(result.zero_upper)
            self.assertTrue(result.widened)
        self.assertEqual(widening._groups[0].variants, 2)

    def test_groups_never_mix_and_existing_groups_work_at_capacity(self):
        widening = ScalarWidening(2, max_variants=2)
        widening.observe(('pc', 'pointer-a'), {'x0': 1}, {'x0'})
        widening.observe(('pc', 'pointer-b'), {'x0': 2}, {'x0'})
        self.assertIsNone(widening.observe(('pc', 'pointer-c'), {}, set()))
        first = widening.observe(('pc', 'pointer-a'), {'x0': 3}, set())
        second = widening.observe(('pc', 'pointer-b'), {'x0': 2}, {'x0'})
        self.assertEqual(first.constants, ())
        self.assertEqual(second.constants, (('x0', 2),))
        self.assertEqual(second.zero_upper, frozenset({'x0'}))
        self.assertEqual(len(widening._groups), 2)
        self.assertIsNone(ScalarWidening(0).observe(0, {}, set()))

    def test_inputs_and_results_are_independent_immutable_snapshots(self):
        widening = ScalarWidening(1, max_variants=2)
        constants, upper = {'x1': 2, 'x0': 1}, {'x0'}
        result = widening.observe('pc', constants, upper)
        constants['x0'] = 9;constants['x2'] = 3;upper.add('x2')
        self.assertEqual(result.constants, (('x0', 1), ('x1', 2)))
        self.assertEqual(result.zero_upper, frozenset({'x0'}))
        with self.assertRaises(FrozenInstanceError):result.widened = True
        with self.assertRaises(TypeError):result.constants[0] = ('x0', 4)
        next_result = widening.observe('pc', {'x0': 1, 'x1': 2}, {'x0'})
        self.assertEqual(next_result.constants, result.constants)
        self.assertEqual(next_result.zero_upper, result.zero_upper)
        self.assertNotEqual(next_result.widened, result.widened)

    def test_exhaustive_small_states_never_invent_widened_facts(self):
        # Each fact is absent (unknown), zero, or one. Upper-bit facts vary
        # independently: only their intersection can be returned after widening.
        facts = []
        for x, y, upper_bits in itertools.product((None, 0, 1), (None, 0, 1), range(4)):
            constants = {name: value for name, value in (('x0', x), ('x1', y)) if value is not None}
            upper = {f'x{i}' for i in range(2) if upper_bits & (1 << i)}
            facts.append((constants, upper))
        for first, second in itertools.product(facts, repeat=2):
            widening = ScalarWidening(1, max_variants=2)
            widening.observe(0, *first)
            result = widening.observe(0, *second)
            expected = {name: value for name, value in first[0].items()
                        if name in second[0] and second[0][name] == value}
            self.assertEqual(dict(result.constants), expected)
            self.assertEqual(result.zero_upper, first[1] & second[1])
            # A third observation can only remove the widened facts.
            later = widening.observe(0, {'x0': 0}, {'x0'})
            self.assertLessEqual(set(later.constants), set(result.constants))
            self.assertLessEqual(later.zero_upper, result.zero_upper)

    def test_threshold_one_and_large_counts_stay_bounded(self):
        widening = ScalarWidening(1, max_variants=1)
        result = widening.observe(0, {'x0': 1}, {'x0'})
        self.assertTrue(result.widened)
        for value in range(1000):
            result = widening.observe(0, {'x0': value}, {'x0'})
        self.assertEqual(result.constants, ())
        self.assertEqual(widening._groups[0].variants, 1)
        self.assertEqual(len(widening._groups), 1)

    def test_invalid_configuration_and_inputs_do_not_mutate_groups(self):
        for bounds in ((-1, 4), (1, 0)):
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):ScalarWidening(*bounds)
        for bounds in ((True, 4), (1, False), (1.0, 4)):
            with self.subTest(bounds=bounds), self.assertRaises(TypeError):ScalarWidening(*bounds)
        widening = ScalarWidening(1)
        for key, constants, upper in (([], {}, set()), ('pc', [], set()),
                                       ('pc', {'x0': True}, set()), ('pc', {0: 1}, set()),
                                       ('pc', {}, ['x0']), ('pc', {}, {0})):
            with self.subTest(key=key, constants=constants, upper=upper), self.assertRaises(TypeError):
                widening.observe(key, constants, upper)
            self.assertFalse(widening._groups)


if __name__ == '__main__':
    unittest.main()
