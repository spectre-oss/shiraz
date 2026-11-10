# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic atomic integration; no TLS, native ordering or APK acceptance."""
import sys
import unittest
from dataclasses import replace
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_namespace import STRIDE, NamespaceDecoder
from guest_swap_effects import recognize_swap64
from test_guest_swap_effects import fixture
from test_guest_tpidr_audit import I, M, R, audit, instruction


def effect(entry=128):
    words = fixture(entry=entry, flag=4096)
    return recognize_swap64({entry + i*4: w for i, w in enumerate(words)}.get, entry,
                            lambda address, width: address == 4096 and width == 1)


class SwapAuditTests(unittest.TestCase):
    def report(self, target='x17', displacement=40, stack_payload=False):
        graph = {item.address: item for item in (
            instruction(0, 'mrs', R(target)),
            instruction(4, 'mov', R('x1'), R('sp')),
            instruction(8, 'bl', I(128)),
            instruction(12, 'ldr', R('x8'), M('x17', displacement)),
            instruction(16, 'mov', R('x17'), R('xzr')), instruction(20, 'ret'),
            instruction(128, 'bti'), instruction(148, 'ret'), instruction(168, 'ret'))}
        if target == 'x1':
            graph[4] = instruction(4, 'nop')
        if stack_payload:
            graph[4] = instruction(4, 'mov', R('x0'), R('sp'))
        summary = effect()
        class Decoder:
            def __call__(self, pc): return graph.get(pc)
            def swap64(self, pc): return summary if pc == 128 else None
        return audit.Audit(Decoder()).site(0)

    def test_lse_preserves_x17_provenance_while_llsc_clobbers_it(self):
        result = self.report()
        self.assertEqual(result['classification'], 'guard-only', result['reasons'])
        self.assertEqual(result['accesses'][0]['offset'], 40)
        row = result['callee_effects'][0]
        self.assertEqual(row['kind'], 'exact-atomic-swap64')
        self.assertEqual(row['memory_order'], 'acquire')
        self.assertEqual(len(row['paths']), 2)
        result = self.report(displacement=8)
        self.assertEqual(result['classification'], 'unknown')
        self.assertIn('non-guard-or-unmodelled-tls-memory-access', {r['reason'] for r in result['reasons']})

    def test_payload_base_and_stack_address_are_not_waived(self):
        for args, reason in (({'target': 'x0'}, 'thread-pointer-atomic-payload'),
                             ({'target': 'x1'}, 'non-guard-tls-atomic-access'),
                             ({'stack_payload': True}, 'stack-address-atomic-payload')):
            with self.subTest(args=args):
                row = self.report(**args)
                self.assertEqual(row['classification'], 'unknown')
                self.assertIn(reason, {r['reason'] for r in row['reasons']})

    def test_wrapping_stack_alias_cannot_launder_atomic_result(self):
        graph = {item.address: item for item in (
            instruction(0, 'mrs', R('x19')),
            instruction(4, 'sub', R('sp'), R('sp'), I(64)),
            instruction(8, 'str', R('x19'), M('sp', 16)),
            instruction(12, 'add', R('x1'), R('sp'), I(16)),
            instruction(16, 'mov', R('x9'), I(-(1 << 63))),
            instruction(20, 'add', R('x1'), R('x1'), R('x9')),
            instruction(24, 'add', R('x1'), R('x1'), R('x9')),
            instruction(28, 'mov', R('x0'), R('xzr')),
            instruction(32, 'bl', I(128)),
            instruction(36, 'str', R('xzr'), M('sp', 16)),
            instruction(40, 'mov', R('x19'), R('xzr')),
            instruction(44, 'ldr', R('x8'), M('x0', 8)), instruction(48, 'ret'),
            instruction(128, 'bti'), instruction(148, 'ret'), instruction(168, 'ret'))}
        summary = effect()
        class Decoder:
            def __call__(self, pc): return graph.get(pc)
            def swap64(self, pc): return summary if pc == 128 else None
            def mrs_stack_window(self, pc): return {'before': 4096, 'after': 256}
            def owned_image_data(self, address, width): return address == 4096 and width == 1
        row = audit.Audit(Decoder()).site(0)
        self.assertEqual(row['classification'], 'unknown')
        self.assertIn('stack-address-offset-limit', {r['reason'] for r in row['reasons']})

    def test_atomic_entry_defensively_rejects_injected_wrapping_metadata(self):
        graph = {item.address: item for item in (
            instruction(0, 'mrs', R('x19')), instruction(4, 'bl', I(128)),
            instruction(8, 'str', R('xzr'), M('sp', 16)),
            instruction(12, 'mov', R('x19'), R('xzr')),
            instruction(16, 'ldr', R('x8'), M('x0', 8)),
            instruction(20, 'add', R('sp'), R('sp'), I(64)), instruction(24, 'ret'),
            instruction(128, 'bti'), instruction(148, 'ret'), instruction(168, 'ret'))}
        initial = audit.State(sp=-64)
        initial.set_alias('x1', frozenset({-(1 << 64) - 48}))
        initial.stack[-48] = frozenset({0})
        summary = effect()
        class Decoder:
            def __call__(self, pc): return graph.get(pc)
            def caller_frame(self, pc): return initial, {'status': 'injected-invalid-fixture'}
            def swap64(self, pc): return summary if pc == 128 else None
            def mrs_stack_window(self, pc): return {'before': 4096, 'after': 256}
            def owned_image_data(self, address, width): return address == 4096 and width == 1
        row = audit.Audit(Decoder()).site(0, entry_context=True)
        self.assertEqual(row['classification'], 'unknown')
        self.assertIn('atomic-stack-range-unproved', {r['reason'] for r in row['reasons']})

    def test_namespace_rebases_helper_flag_and_every_return(self):
        decoder = NamespaceDecoder.__new__(NamespaceDecoder)
        summary = effect()
        decoder.locate = lambda pc: (2, 128, SimpleNamespace(swap64=lambda local: summary))
        row = decoder.swap64(2*STRIDE+128)
        self.assertEqual(row, replace(summary, entry=2*STRIDE+128, flag_address=2*STRIDE+4096,
                                     paths=tuple(replace(p, ret_pc=2*STRIDE+p.ret_pc) for p in summary.paths)))
        decoder.locate = lambda pc: (2, 128, SimpleNamespace())
        self.assertIsNone(decoder.swap64(2*STRIDE+128))

    def test_decoder_requires_owned_nonexec_flag_data(self):
        decoder = audit.ElfDecoder.__new__(audit.ElfDecoder)
        words = fixture(entry=128, flag=4096)
        memory = {128+i*4: word.to_bytes(4, 'little') for i, word in enumerate(words)}
        decoder.word = memory.get
        decoder.bindings = SimpleNamespace(loads=[(4096, 8192, 0, 0, 4)])
        self.assertIsNotNone(decoder.swap64(128))
        decoder.bindings.loads[0] = (4096, 8192, 0, 0, 5)
        self.assertIsNone(decoder.swap64(128))
        decoder.bindings = None
        self.assertIsNone(decoder.swap64(128))


if __name__ == '__main__':
    unittest.main()
