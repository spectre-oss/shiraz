# SPDX-License-Identifier: GPL-3.0-only
"""Scalar abstraction controls; synthetic CFGs never prove APK acceptance."""
import sys
import unittest
from dataclasses import replace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import test_guest_jump_table as jump_tests
from test_guest_tpidr_audit import I, M, R, audit, instruction


def numeric_loop(tail):
    body = [instruction(0, 'mrs', R('x19')), instruction(4, 'mov', R('w8'), I(0)),
            instruction(8, 'add', R('w8'), R('w8'), I(1)),
            instruction(12, 'cbnz', R('w0'), I(8)), *tail]
    return {item.address: item for item in body}


class ScalarAuditTests(unittest.TestCase):
    def test_numeric_loop_converges_without_raising_limits(self):
        graph = numeric_loop([instruction(16, 'ldr', R('x9'), M('x19', 40)),
                              instruction(20, 'mov', R('x19'), R('xzr')), instruction(24, 'ret')])
        before = audit.Audit(graph.get, max_contexts=64, max_steps=256, scalar_widening=False).site(0)
        self.assertEqual(before['classification'], 'unknown')
        self.assertIn('context-limit', {r['reason'] for r in before['reasons']})
        after = audit.Audit(graph.get, max_contexts=64, max_steps=256).site(0)
        self.assertEqual(after['classification'], 'guard-only', after['reasons'])
        self.assertLess(after['state_partitions'], 64)
        self.assertTrue(after['scalar_widening'])
        self.assertTrue(any('x8' in row['constants_dropped'] for row in after['scalar_widenings']))

    def test_lost_index_still_refuses_pointer_arithmetic(self):
        graph = numeric_loop([instruction(16, 'add', R('x20'), R('x19'), R('x8')),
                              instruction(20, 'ldr', R('x9'), M('x20', 40)),
                              instruction(24, 'mov', R('x19'), R('xzr')),
                              instruction(28, 'mov', R('x20'), R('xzr')), instruction(32, 'ret')])
        row = audit.Audit(graph.get, max_contexts=128, max_steps=512).site(0)
        self.assertEqual(row['classification'], 'unknown')
        self.assertIn('register-arithmetic-index-unproved', {r['reason'] for r in row['reasons']})
        self.assertTrue(row['scalar_widenings'])

    def test_different_pointer_paths_and_unsafe_partitions_survive(self):
        graph = {item.address: item for item in (
            instruction(0, 'mrs', R('x19')), instruction(4, 'mov', R('w8'), I(0)),
            instruction(8, 'cbz', R('w0'), I(20)), instruction(12, 'add', R('x19'), R('x19'), I(8)),
            instruction(16, 'b', I(20)), instruction(20, 'add', R('w8'), R('w8'), I(1)),
            instruction(24, 'cbnz', R('w1'), I(20)), instruction(28, 'ldr', R('x9'), M('x19', 40)),
            instruction(32, 'mov', R('x19'), R('xzr')), instruction(36, 'ret'))}
        row = audit.Audit(graph.get, max_contexts=256, max_steps=512).site(0)
        self.assertEqual(row['classification'], 'unknown')
        self.assertEqual({x['offset'] for x in row['accesses']}, {40, 48})
        self.assertIn('non-guard-or-unmodelled-tls-memory-access', {r['reason'] for r in row['reasons']})
        self.assertNotIn('context-limit', {r['reason'] for r in row['reasons']})
        self.assertTrue(row['scalar_widenings'])

    def test_widened_byte_copy_size_never_becomes_zero(self):
        body = [instruction(0, 'mrs', R('x19')),
                instruction(4, 'sub', R('sp'), R('sp'), I(128)),
                instruction(8, 'str', R('x30'), M('sp', 120)),
                instruction(12, 'mov', R('w2'), I(0)),
                instruction(16, 'add', R('w2'), R('w2'), I(1)),
                instruction(20, 'cbnz', R('w0'), I(16)),
                instruction(24, 'add', R('x0'), R('sp'), I(16)),
                instruction(28, 'add', R('x1'), R('sp'), I(64)),
                instruction(32, 'bl', I(256)),
                instruction(36, 'ldr', R('x9'), M('x19', 40)),
                instruction(40, 'ldr', R('x30'), M('sp', 120)),
                instruction(44, 'mov', R('x0'), R('xzr')),
                instruction(48, 'mov', R('x19'), R('xzr')),
                instruction(52, 'add', R('sp'), R('sp'), I(128)),
                instruction(56, 'ret'), instruction(256, 'adrp', R('x16'), I(0))]
        graph = {item.address: item for item in body}
        class Decoder:
            def __call__(self, pc): return graph.get(pc)
            def plt(self, pc):
                return {'effect': 'byte-memory', 'abi': 'memory/byte-copy-v1',
                        'read_slot': 8192, 'target': None} if pc == 256 else None
            def mrs_stack_window(self, pc): return {'before': 4096, 'after': 256}
            def owned_image_data(self, address, width): return address == 8192 and width == 8
        row = audit.Audit(Decoder(), max_contexts=256, max_steps=512).site(0)
        self.assertEqual(row['classification'], 'unknown')
        self.assertIn('byte-memory-size-unproved-or-bound', {r['reason'] for r in row['reasons']})
        self.assertTrue(row['scalar_widenings'])
        self.assertTrue(all(copy['bytes'] > 0 for copy in row['conditional_byte_copies']))

    def test_lost_upper_bit_evidence_cannot_authorize_jump_dispatch(self):
        body = [instruction(0, 'mrs', R('x19')), instruction(4, 'mov', R('w8'), I(0)),
                instruction(8, 'cbz', R('w0'), I(20)),
                instruction(12, 'mov', R('w10'), R('w1')), instruction(16, 'b', I(24)),
                instruction(20, 'mov', R('x10'), R('x1')),
                instruction(24, 'add', R('w8'), R('w8'), I(1)),
                instruction(28, 'cbnz', R('w2'), I(24)),
                instruction(32, 'cmp', R('w10'), I(1))]
        summary = replace(jump_tests.ByteJumpTableTests.recognize(), entry=32,
                          default_target=128, table_address=4096, branch_base=192,
                          index_source='w10', index_register='x10', table_register='x11',
                          offset_register='w13', branch_register='x12', limit=1,
                          table_bytes=(0, 0), targets=(192, 192), requires_zero_extended_index=True)
        for pc in (128, 192):
            body.extend([instruction(pc, 'ldr', R('x9'), M('x19', 40)),
                         instruction(pc+4, 'mov', R('x19'), R('xzr')), instruction(pc+8, 'ret')])
        graph = {item.address: item for item in body}
        class Decoder:
            def __call__(self, pc): return graph.get(pc)
            def byte_jump_table(self, pc): return summary if pc == 32 else None
            def owned_image_data(self, address, width): return address == 4096 and width == 2
        row = audit.Audit(Decoder(), max_contexts=256, max_steps=512).site(0)
        self.assertEqual(row['classification'], 'unknown')
        self.assertIn('jump-table-index-upper-bits-unproved', {r['reason'] for r in row['reasons']})
        self.assertTrue(row['scalar_widenings'])

    def test_only_scalar_facts_can_share_a_group_key(self):
        state = audit.State(constants={'x8': 7}, zero_upper={'x8'})
        other = state.copy();other.constants = {'x9': 123};other.zero_upper = {'x9'}
        self.assertNotEqual(state.fingerprint(), other.fingerprint())
        self.assertEqual(state.fingerprint(include_scalars=False), other.fingerprint(include_scalars=False))
        signature = audit.ReturnSignature(frozenset({100}), -16, 'a')
        mutations = (
            lambda s: s.registers.update(x19=frozenset({0})),
            lambda s: s.aliases.update(x19=frozenset({-16})),
            lambda s: s.return_values.update(x30=frozenset({100})),
            lambda s: s.image_addresses.update(x20=8192),
            lambda s: s.return_signatures.update(x30=signature),
            lambda s: s.stack.update({-16: audit.CLEAN}),
            lambda s: s.stack_aliases.update({-16: frozenset({-32})}),
            lambda s: s.stack_returns.update({-16: frozenset({100})}),
            lambda s: s.stack_images.update({-16: 8192}),
            lambda s: s.stack_signatures.update({-16: signature}),
            lambda s: setattr(s, 'sp', -16), lambda s: setattr(s, 'fp', -16),
            lambda s: setattr(s, 'native_memory_may_contain_tp', True),
            lambda s: setattr(s, 'native_stack_clobber_below', -16),
            lambda s: s.native_stack_rewritten.add(-16),
        )
        for index, mutate in enumerate(mutations):
            with self.subTest(domain=index):
                changed = state.copy();mutate(changed)
                self.assertNotEqual(state.fingerprint(include_scalars=False),
                                    changed.fingerprint(include_scalars=False))


if __name__ == '__main__':
    unittest.main()
