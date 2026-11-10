# SPDX-License-Identifier: GPL-3.0-only
"""Exact clean SIMD transfer controls, not complete TLS consumer proof."""
import dataclasses
import unittest

from test_guest_tpidr_audit import M, R, audit, instruction

WORD = 0x4E211C42
RAW = WORD.to_bytes(4, 'little')


def operation():
    return audit.Instruction(8, 'and', (R('v2'), R('v2'), R('v1')),
                             reads=('v2', 'v1'), writes=('v2',))


class VectorAndTests(unittest.TestCase):
    def test_clean_unknown_sources_and_inplace_destination(self):
        state = audit.State()
        state.constants['q2'] = 45
        state.registers['x19'] = frozenset({0})
        before = state.fingerprint()
        post = audit.clean_vector_and(operation(), RAW, state)
        self.assertIsNotNone(post)
        self.assertEqual(state.fingerprint(), before)
        self.assertEqual(post.value('x19'), frozenset({0}))
        self.assertNotIn('q2', post.constants)
        self.assertFalse(post.native_memory_may_contain_tp)

    def test_full_destination_overwrite_and_register_aliases(self):
        for destination, left, right in ((3, 1, 2), (31, 1, 1), (1, 1, 1)):
            names = tuple(f'v{i}' for i in (destination, left, right))
            item = dataclasses.replace(operation(), operands=tuple(R(name) for name in names),
                                       reads=tuple(set(names[1:])), writes=(names[0],))
            word = 0x4E201C00 | destination | left << 5 | right << 16
            state = audit.State()
            if destination not in (left, right):
                for prefix in ('b', 'h', 's', 'd', 'q', 'v'):
                    view = f'{prefix}{destination}'
                    state.registers[view] = frozenset({0})
                    state.aliases[view] = frozenset({0})
                    state.return_values[view] = frozenset({4})
                    state.image_addresses[view] = 0
                    state.return_signatures[view] = audit.ReturnSignature(frozenset({4}), 0, 'A')
            post = audit.clean_vector_and(item, word.to_bytes(4, 'little'), state)
            self.assertIsNotNone(post)
            for mapping in (post.registers, post.aliases, post.return_values,
                            post.image_addresses, post.return_signatures):
                self.assertFalse(any(f'{prefix}{destination}' in mapping
                                     for prefix in ('b', 'h', 's', 'd', 'q', 'v')))

    def test_every_source_family_provenance_refuses(self):
        for source in (1, 2):
            for prefix in ('b', 'h', 's', 'd', 'q', 'v'):
                for field, value in (
                        ('registers', frozenset({0})), ('aliases', frozenset({0})),
                        ('return_values', frozenset({4})), ('image_addresses', 0),
                        ('return_signatures', audit.ReturnSignature(frozenset({4}), 0, 'A'))):
                    state = audit.State()
                    getattr(state, field)[f'{prefix}{source}'] = value
                    with self.subTest(source=source, prefix=prefix, field=field):
                        self.assertIsNone(audit.clean_vector_and(operation(), RAW, state))

    def test_bytes_and_effect_metadata_are_independent_requirements(self):
        for bit in range(32):
            with self.subTest(bit=bit):
                self.assertIsNone(audit.clean_vector_and(operation(), (WORD ^ (1 << bit)).to_bytes(4, 'little'), audit.State()))
        variants = [{'mnemonic': 'eor'}, {'width': 16}, {'width': 4},
                    {'writeback': True}, {'post_index': 16}, {'control_transfer': True},
                    {'reads': ('v1',)}, {'writes': ('v2', 'nzcv')},
                    {'operands': (R('v2'), R('v2'), audit.Operand('reg', 'v1', shift='lsl', shift_amount=1))},
                    {'operands': (R('v2'), R('q2'), R('v1'))}]
        for change in variants:
            self.assertIsNone(audit.clean_vector_and(dataclasses.replace(operation(), **change), RAW, audit.State()))
        for raw in (None, b'', RAW[:3], bytearray(RAW), RAW + b'\0'):
            self.assertIsNone(audit.clean_vector_and(operation(), raw, audit.State()))

    def test_audit_continues_only_with_independent_instruction_bytes(self):
        body = [instruction(0, 'mrs', R('x19')),
                instruction(4, 'ldr', R('x8'), M('x19', 40)), operation(),
                instruction(12, 'mov', R('x19'), R('xzr')), instruction(16, 'ret')]
        class Decoder:
            def __call__(self, pc):return {row.address: row for row in body}.get(pc)
            def word(self, pc):return RAW if pc == 8 else None
        self.assertEqual(audit.Audit(Decoder()).site(0)['classification'], 'guard-only')
        refused = audit.Audit({row.address: row for row in body}.get).site(0)
        self.assertEqual(refused['classification'], 'unknown')
        self.assertIn('vector-and-bytes-effects-or-provenance-unproved', {row['reason'] for row in refused['reasons']})


if __name__ == '__main__':
    unittest.main()
