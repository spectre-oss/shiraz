# SPDX-License-Identifier: GPL-3.0-only
"""A64 integer transfer tests; these do not establish complete guest TLS safety."""
import dataclasses
import importlib.util
import unittest

from test_guest_tpidr_audit import I, M, R, audit, instruction


def shifted(name, kind='', amount=0):
    return audit.Operand('reg', name, shift=kind, shift_amount=amount)


def logical(pc, op, destination, left, right, **kwargs):
    return audit.Instruction(pc, op, (R(destination), R(left), right),
        reads=(left, right.value), writes=(destination,),
        width=4 if destination.startswith('w') else 8, **kwargs)


class LogicalRegisterTests(unittest.TestCase):
    def result(self, op, a, b, shift='', amount=0, width=64):
        state = audit.State()
        prefix = 'w' if width == 32 else 'x'
        state.set_scalar(prefix + '1', a)
        state.set_scalar(prefix + '2', b)
        return audit.scalar_logical_result(op,
            (R(prefix + '0'), R(prefix + '1'), shifted(prefix + '2', shift, amount)), state)

    def test_truth_tables_width_wrap_shift_sign_and_inversion(self):
        for width in (32, 64):
            mask = (1 << width) - 1
            sign = 1 << (width - 1)
            for op, expected in [('and', 0), ('bic', 0x55), ('orr', 0xff),
                                 ('orn', mask ^ 0xaa), ('eor', 0xff), ('eon', mask ^ 0xff)]:
                self.assertEqual(self.result(op, 0x55, 0xaa, width=width), expected)
            self.assertEqual(self.result('orr', 0, 1, 'lsl', width - 1, width), sign)
            self.assertEqual(self.result('orr', 0, sign, 'lsr', width - 1, width), 1)
            self.assertEqual(self.result('orr', 0, sign, 'asr', width - 1, width), mask)
            self.assertEqual(self.result('orr', 0, sign, 'ror', 0, width), sign)
            self.assertEqual(self.result('orr', 0, 1, 'ror', 1, width), sign)
            self.assertEqual(self.result('eon', mask, sign, 'asr', 1, width), sign | (sign >> 1))
            self.assertEqual(self.result('orr', 0, mask, 'lsl', 1, width), mask - 1)
            self.assertEqual(self.result('orr', 1 << width, 0, width=width), 0)

    def test_unknown_clean_is_not_zero_and_sources_must_not_carry_addresses(self):
        operands = (R('x0'), R('x1'), shifted('x2', 'lsl', 1))
        self.assertIsNone(audit.scalar_logical_result('orr', operands, audit.State()))
        for prefix in ('w', 'x'):
            for field, value in [('registers', frozenset({0})), ('aliases', frozenset({0})),
                                 ('return_values', frozenset({4})), ('image_addresses', 0x1000),
                                 ('return_signatures', audit.ReturnSignature(frozenset({4}), 0, 'A'))]:
                state = audit.State()
                getattr(state, field)['x2'] = value
                with self.subTest(prefix=prefix, field=field), self.assertRaises(ValueError):
                    audit.scalar_logical_result('orr', (R(prefix+'0'), R(prefix+'1'),
                        shifted(prefix+'2', 'lsl', 1)), state)

    def test_malformed_forms_rejected(self):
        base = [R('x0'), R('x1'), shifted('x2', 'lsl', 1)]
        variants = [(0, R('sp')), (1, R('w1')), (2, R('q2')), (2, shifted('x2', 'lsl', 64)),
                    (2, shifted('x2', 'ror', -1)), (2, shifted('x2', '', 1)),
                    (2, shifted('x2', 'unknown', 1)), (2, shifted('x2', 'lsl', True)),
                    (2, dataclasses.replace(base[2], extend='uxtw')),
                    (1, shifted('x1', 'lsl', 1)), (0, shifted('x0', 'lsl', 1))]
        for index, replacement in variants:
            operands = base.copy(); operands[index] = replacement
            with self.subTest(operands=operands), self.assertRaises(ValueError):
                audit.scalar_logical_result('orr', operands, audit.State())

    def graph(self, operation, setup=(), tail=()):
        body = [instruction(0, 'mrs', R('x19')), instruction(4, 'ldr', R('x8'), M('x19', 40))]
        body.extend(setup)
        body.append(dataclasses.replace(operation, address=4*len(body)))
        tail_start = len(body)
        body.extend(dataclasses.replace(item, address=4*(tail_start+i)) for i, item in enumerate(tail))
        body.extend([instruction(4*len(body), 'mov', R('x19'), R('xzr')),
                     instruction(4*(len(body)+1), 'ret')])
        graph = {item.address: item for item in body}
        return audit.Audit(graph.get).site(0)

    def test_transfer_snapshots_aliases_clears_old_facts_and_w_zero_extends(self):
        setup = [instruction(8, 'mov', R('x9'), I((1 << 40) | 20))]
        result = self.graph(logical(0, 'orr', 'w9', 'w9', shifted('w9', 'lsl', 1)), setup, [
            instruction(0, 'add', R('x20'), R('x19'), R('x9')),
            instruction(0, 'ldr', R('x8'), M('x20', -20)),
            instruction(0, 'mov', R('x20'), R('xzr'))])
        self.assertEqual(result['classification'], 'guard-only', result['reasons'])
        self.assertEqual([row['offset'] for row in result['accesses']], [40, 40])
        result = self.graph(logical(0, 'orr', 'x9', 'x0', shifted('x1', 'lsl', 1)),
            [instruction(8, 'mov', R('x9'), I(40))], [
                instruction(0, 'add', R('x20'), R('x19'), R('x9'))])
        self.assertEqual(result['classification'], 'unknown')
        self.assertIn('register-arithmetic-index-unproved', {x['reason'] for x in result['reasons']})
        # An overwritten FP must not retain the old SP alias.
        result = self.graph(logical(0, 'orr', 'x29', 'x0', R('x1')),
            [instruction(8, 'str', R('x19'), M('sp', -8)),
             instruction(12, 'mov', R('x29'), R('sp'))],
            [instruction(0, 'ldr', R('x0'), M('x29', -8))])
        self.assertIn('unmodelled-load-may-alias-thread-pointer-stack-spill',
            {x['reason'] for x in result['reasons']})

    def test_pointer_inputs_and_extra_effect_metadata_stop_the_path(self):
        for operation in [logical(0, 'orr', 'x0', 'x19', shifted('x1', 'lsl', 1)),
                          logical(0, 'orr', 'w0', 'w19', shifted('w1', 'lsl', 1)),
                          dataclasses.replace(logical(0, 'orr', 'x0', 'x1', R('x2')), writes=('x0','nzcv')),
                          dataclasses.replace(logical(0, 'orr', 'x0', 'x1', R('x2')), reads=('x1','x2','x19')),
                          dataclasses.replace(logical(0, 'orr', 'x0', 'x1', R('x2')), width=4)]:
            result = self.graph(operation)
            self.assertEqual(result['classification'], 'unknown', result)
            self.assertTrue(any(x['reason'].startswith('logical register') for x in result['reasons']))

    @unittest.skipUnless(importlib.util.find_spec('capstone'), 'requires pinned Capstone')
    def test_real_encoding_fields_decode_with_all_legal_shifts(self):
        import capstone
        from capstone import arm64
        decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
        decoder.detail = True
        adapter = audit.ElfDecoder.__new__(audit.ElfDecoder)
        adapter.decoder, adapter.arm64 = decoder, arm64
        adapter.control_groups, adapter.cache = set(), {}
        for width in (32,64):
            prefix = 'w' if width == 32 else 'x'
            for op, opc, invert in [('and',0,0),('bic',0,1),('orr',1,0),
                                    ('orn',1,1),('eor',2,0),('eon',2,1)]:
                for kind in range(4):
                    for amount in range(width):
                        word = ((width==64)<<31 | opc<<29 | 0x0a000000 | kind<<22 |
                                invert<<21 | 2<<16 | amount<<10 | 1<<5)
                        decoded = next(decoder.disasm(word.to_bytes(4,'little'),0))
                        self.assertEqual(decoded.mnemonic,op)
                        operand = decoded.operands[2]
                        self.assertEqual(decoded.reg_name(operand.reg),prefix+'2')
                        self.assertEqual(operand.shift.value,amount)
                        expected = arm64.ARM64_SFT_INVALID if kind==0 and amount==0 else [
                            arm64.ARM64_SFT_LSL,arm64.ARM64_SFT_LSR,
                            arm64.ARM64_SFT_ASR,arm64.ARM64_SFT_ROR][kind]
                        self.assertEqual(operand.shift.type, expected)
                        adapter.cache.clear()
                        adapter.word = lambda address, word=word: word.to_bytes(4, 'little') if address == 0 else None
                        converted = adapter(0)
                        self.assertEqual(converted.mnemonic, op)
                        self.assertEqual(converted.width, width // 8)
                        self.assertEqual(converted.operands[2], shifted(prefix+'2',
                            '' if expected == arm64.ARM64_SFT_INVALID else
                            ['lsl','lsr','asr','ror'][kind], amount))
                        result = self.graph(converted)
                        self.assertEqual(result['classification'], 'guard-only', result['reasons'])
