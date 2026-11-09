# SPDX-License-Identifier: GPL-3.0-only
"""Exact indexed active-stack controls; no general indexed-memory proof."""
import dataclasses
import unittest

from test_guest_tpidr_audit import I, M, R, audit, instruction

RAW = (0x78747AC8).to_bytes(4, 'little')


def operation():
    return audit.Instruction(16, 'ldrh', (R('w8'), audit.Operand('mem', base='x22', index='x20')),
                             reads=('x22', 'x20'), writes=('w8',), width=2)


def prepared():
    state = audit.State(sp=-128)
    state.aliases['x22'] = frozenset({-64})
    state.set_scalar('x20', 1)
    return state


class IndexedHalfwordAuditTests(unittest.TestCase):
    def test_exact_slot_normalization_does_not_mutate_state(self):
        state = prepared(); before = state.fingerprint()
        self.assertEqual(audit.exact_indexed_halfword(operation(), RAW, state),
                         (R('w8'), M('x22', 2)))
        self.assertEqual(before, state.fingerprint())

    def test_store_and_signed_index_normalization(self):
        for option, index in ((3, 1), (6, (1 << 32)-1), (7, (1 << 64)-1)):
            word = 0x78200800 | option << 13 | 1 << 12 | 22 << 5 | 20 << 16
            index_name = 'w20' if option == 6 else 'x20'
            item = audit.Instruction(16, 'strh', (R('w0'), audit.Operand('mem', base='x22', index=index_name)),
                                     reads=('w0', 'x22', index_name), writes=(), width=2)
            state = prepared(); state.set_scalar('x20', index)
            expected = 2 if option == 3 else -2
            self.assertEqual(audit.exact_indexed_halfword(item, word.to_bytes(4, 'little'), state),
                             (R('w0'), M('x22', expected)))

    def test_index_provenance_and_unknown_values_refuse(self):
        for field, value in [('registers', frozenset({0})), ('aliases', frozenset({0})),
                             ('return_values', frozenset({4})), ('image_addresses', 0),
                             ('return_signatures', audit.ReturnSignature(frozenset({4}), 0, 'A'))]:
            state = prepared(); getattr(state, field)['x20'] = value
            self.assertIsNone(audit.exact_indexed_halfword(operation(), RAW, state))
        for name, value in [('return_values', frozenset({4})), ('image_addresses', 0),
                            ('return_signatures', audit.ReturnSignature(frozenset({4}), 0, 'A'))]:
            state = prepared(); getattr(state, name)['x22'] = value
            self.assertIsNone(audit.exact_indexed_halfword(operation(), RAW, state))
        state = prepared(); state.constants.clear()
        self.assertIsNone(audit.exact_indexed_halfword(operation(), RAW, state))

    def test_active_frame_bounds_and_metadata_refuse(self):
        for base in (None, 0, 112, -132, 1 << 64):
            state = prepared(); state.aliases['x22'] = frozenset({base})
            self.assertIsNone(audit.exact_indexed_halfword(operation(), RAW, state))
        for changes in ({'writes': ('x9',)}, {'reads': ('x22',)}, {'width': 8},
                        {'writeback': True}, {'control_transfer': True}, {'post_index': 2},
                        {'operands': (R('w8'), audit.Operand('mem', base='x22', index='x21'))}):
            self.assertIsNone(audit.exact_indexed_halfword(dataclasses.replace(operation(), **changes), RAW, prepared()))

    def test_existing_spill_overlap_checks_still_apply(self):
        for spill in (False, True):
            body = [instruction(0, 'mrs', R('x19')),
                    instruction(4, 'ldr', R('x8'), M('x19', 40)),
                    instruction(8, 'sub', R('sp'), R('sp'), I(128)),
                    instruction(12, 'add', R('x22'), R('sp'), I(64)),
                    instruction(16, 'mov', R('x20'), I(1))]
            if spill:
                body.append(instruction(20, 'str', R('x19'), M('x22')))
            pc = len(body) * 4
            body.extend([dataclasses.replace(operation(), address=pc),
                         instruction(pc+4, 'mov', R('x19'), R('xzr')),
                         instruction(pc+8, 'add', R('sp'), R('sp'), I(128)),
                         instruction(pc+12, 'ret')])
            class Decoder:
                def __call__(self, address, body=body):return {row.address: row for row in body}.get(address)
                def word(self, address, pc=pc):return RAW if address == pc else None
            result = audit.Audit(Decoder()).site(0)
            if spill:
                self.assertEqual(result['classification'], 'unknown')
                self.assertIn('partial-overlap-with-thread-pointer-stack-spill',
                              {row['reason'] for row in result['reasons']})
            else:
                self.assertEqual(result['classification'], 'guard-only', result['reasons'])


if __name__ == '__main__':
    unittest.main()
