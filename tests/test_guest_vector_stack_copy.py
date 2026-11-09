# SPDX-License-Identifier: GPL-3.0-only
"""Exact vector stack-copy semantics; synthetic cases are not APK acceptance."""
import dataclasses
import importlib.util
import unittest

from test_guest_tpidr_audit import I, M, R, audit, instruction


def pair(source='x10', destination='x29', displacement=-80):
    return (audit.Instruction(16, 'ldp', (R('q0'), R('q1'), M(source)), width=16),
            audit.Instruction(20, 'stp', (R('q0'), R('q1'), M(destination, displacement)), width=16))


class VectorStackCopyTests(unittest.TestCase):
    def state(self):
        state = audit.State(sp=-128, fp=0)
        state.set_alias('x10', frozenset({-40}))
        state.stack[-40] = frozenset({0})
        state.stack_aliases[-32] = frozenset({-128})
        state.stack_returns[-24] = frozenset({100})
        state.stack_images[-16] = 8192
        state.stack_signatures[-24] = audit.ReturnSignature(frozenset({100}), -128, 'a')
        return state

    @unittest.skipUnless(importlib.util.find_spec('capstone'), 'requires pinned Capstone decoder')
    def test_actual_qtcore_instruction_words_copy_va_list_lanes(self):
        import capstone
        from capstone import arm64
        self.assertEqual(capstone.__version__, '5.0.3')
        decoder = audit.ElfDecoder.__new__(audit.ElfDecoder)
        decoder.cache = {}
        decoder.data = bytes.fromhex('400540ada0873dad')
        decoder.segments = [(16, 24, 0)]
        decoder.arm64 = arm64
        decoder.decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
        decoder.decoder.detail = True
        decoder.control_groups = set()
        self.assertIsNotNone(audit.paired_vector_stack_copy(decoder(16), decoder(20), self.state()))

    def test_all_lane_facts_and_vector_views_survive(self):
        state = self.state();before = state.fingerprint()
        post, source, destination = audit.paired_vector_stack_copy(*pair(), state)
        self.assertEqual((source, destination), (-40, -80))
        for name in ('stack', 'stack_aliases', 'stack_returns', 'stack_images', 'stack_signatures'):
            for slot, value in getattr(state, name).items():
                self.assertEqual(getattr(post, name)[slot - 40], value)
        for index in range(2):
            for prefix in ('b', 'h', 's', 'd', 'q', 'v'):
                self.assertEqual(post.value(f'{prefix}{index}'), audit.OPAQUE_VALUE)
        self.assertEqual(state.fingerprint(), before)
        self.assertNotEqual(post.fingerprint(), before)

    def test_overlapping_copy_snapshots_before_store_and_removes_old_facts(self):
        state = self.state();state.fp = -16
        state.stack_images[-56] = 1234
        post, _, _ = audit.paired_vector_stack_copy(*pair(displacement=-16), state)
        self.assertEqual(post.stack[-32], state.stack[-40])
        self.assertEqual(post.stack_aliases[-24], state.stack_aliases[-32])
        self.assertEqual(post.stack_returns[-16], state.stack_returns[-24])
        self.assertEqual(post.stack_images[-8], state.stack_images[-16])
        self.assertNotIn(-32, post.stack_aliases)
        self.assertEqual(post.stack_images[-56], 1234)

    def test_clean_q_write_clears_every_prior_vector_view(self):
        state = audit.State(sp=-128, fp=0)
        state.set_alias('x10', frozenset({-40}))
        for prefix in ('b', 'h', 's', 'd', 'q', 'v'):
            state.set(prefix+'0', audit.OPAQUE_VALUE)
        post, _, _ = audit.paired_vector_stack_copy(*pair(), state)
        self.assertTrue(all(not audit.tainted(post.value(prefix+'0'))
                            for prefix in ('b', 'h', 's', 'd', 'q', 'v')))

    def test_unknown_native_modified_misaligned_and_bounded_sources_refuse(self):
        for mutate in (
            lambda s: s.set_alias('x10', audit.CLEAN),
            lambda s: s.set_alias('x10', frozenset({-39})),
            lambda s: s.set('x10', frozenset({0})),
            lambda s: s.set_alias('x10', frozenset({-(1 << 64) - 40})),
            lambda s: s.set_alias('x10', frozenset({8})),
            lambda s: s.set_alias('x10', frozenset({-144})),
            lambda s: setattr(s, 'native_memory_may_contain_tp', True),
            lambda s: setattr(s, 'native_stack_clobber_below', 0),
            lambda s: s.stack.update({-39: frozenset({0})}),
            lambda s: s.stack.update({i*8+1024: audit.CLEAN for i in range(audit.MAX_STACK_SLOTS)}),
        ):
            state = self.state();mutate(state)
            self.assertIsNone(audit.paired_vector_stack_copy(*pair(), state))
        state = self.state();state.native_stack_clobber_below = 0
        state.native_stack_rewritten.update(range(-40, -8, 8))
        self.assertIsNotNone(audit.paired_vector_stack_copy(*pair(), state))

    def test_extent_bound_and_destination_partial_overlap(self):
        state = self.state();state.fp = -(1 << 64)
        self.assertIsNone(audit.paired_vector_stack_copy(*pair(), state))
        state = self.state();state.stack_returns[-79] = frozenset({100})
        self.assertIsNone(audit.paired_vector_stack_copy(*pair(), state))
        state = self.state();state.sp = -(1 << 20)
        state.set_alias('x10', frozenset({state.sp}))
        self.assertIsNotNone(audit.paired_vector_stack_copy(*pair(), state))
        state.set_alias('x10', frozenset({state.sp - 8}))
        self.assertIsNone(audit.paired_vector_stack_copy(*pair(), state))

    def test_only_exact_adjacent_architectural_pair_is_recognized(self):
        first, second = pair()
        for changed in (
            dataclasses.replace(second, address=24), dataclasses.replace(second, width=8),
            dataclasses.replace(second, writeback=True), dataclasses.replace(second, post_index=32),
            dataclasses.replace(second, control_transfer=True), dataclasses.replace(second, writes=('nzcv',)),
            dataclasses.replace(second, operands=(R('q1'), R('q0'), M('x29', -80))),
            dataclasses.replace(second, operands=(R('q0'), R('q1'), M('x29', -72))),
        ):
            self.assertIsNone(audit.paired_vector_stack_copy(first, changed, self.state()))
        self.assertIsNone(audit.paired_vector_stack_copy(first, None, self.state()))

    def test_a_later_vector_consumer_still_refuses(self):
        graph = {x.address: x for x in (
            instruction(0, 'mrs', R('x19')),
            instruction(4, 'sub', R('sp'), R('sp'), I(128)),
            instruction(8, 'str', R('x19'), M('sp', 32)),
            instruction(12, 'add', R('x10'), R('sp'), I(32)),
            audit.Instruction(16, 'ldp', (R('q0'), R('q1'), M('x10')), width=16),
            audit.Instruction(20, 'stp', (R('q0'), R('q1'), M('sp', 64)), width=16),
            instruction(24, 'mystery', R('d0'), reads=('d0',)), instruction(28, 'ret'))}
        result = audit.Audit(graph.get).site(0)
        self.assertEqual(len(result['vector_stack_copies']), 1)
        self.assertIn('unmodelled-thread-pointer-consumer:mystery', {r['reason'] for r in result['reasons']})
        self.assertEqual(result['classification'], 'unknown')


if __name__ == '__main__':
    unittest.main()
