# SPDX-License-Identifier: GPL-3.0-only
"""Conditional cached-leaf application, never complete TLS/runtime proof."""
import copy
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_namespace import STRIDE, NamespaceDecoder
from test_guest_cached_self_record import fixture as macho_fixture
from test_guest_native_target import self_fixture
from test_guest_tpidr_audit import I, M, R, audit, instruction


class CachedSelfResolverTests(unittest.TestCase):
    def test_effects_need_exact_bridge_and_independent_body(self):
        image, row = macho_fixture()
        fixture = self_fixture()
        row['relocation'] = 0x1500
        proof = fixture.proof();proof['modules'][0]['bindings'] = [row]
        self.assertEqual(fixture.metadata(proof).resolve(0x1500)['reason'], 'cached-self-contract-untrusted')
        with tempfile.TemporaryDirectory() as directory:
            bridge = Path(directory) / 'bridge.so';bridge.write_bytes(image)
            proof['native_bridge'] = {'path': str(bridge.resolve()), 'sha256': hashlib.sha256(image).hexdigest(),
                                      'verification': 'observed-before-and-after-process'}
            selected = fixture.metadata(proof, bridge).resolve(0x1500)
            self.assertEqual(selected['effect'], 'cached-self')
            self.assertEqual(selected['rows'], 4096)
            self.assertEqual(len(selected['tables']), 4)
            missing = copy.deepcopy(proof);del missing['modules'][0]['bindings'][0]['cached_self']
            self.assertEqual(fixture.metadata(missing, bridge).resolve(0x1500)['reason'], 'cached-self-body-profile-unavailable')
            wrong = copy.deepcopy(proof);wrong['modules'][0]['bindings'][0]['cached_self']['body_hex'] = '00' * 152
            with self.assertRaises(ValueError):fixture.metadata(wrong, bridge)


class CachedSelfFlowTests(unittest.TestCase):
    def test_namespace_rebases_only_the_guest_got_slot(self):
        decoder = NamespaceDecoder.__new__(NamespaceDecoder)
        decoder.plt_cache = {};decoder.names = ['root.so', 'other.so']
        class Local:
            def plt(self, pc):
                return {'effect': 'cached-self', 'read_slot': 0x1200, 'target': None,
                        'tables': [{'native_vmaddr': 0x9000, 'bytes': 32768}]}
        decoder.locate = lambda address: (1, 0x100, Local())
        row = decoder.plt(STRIDE + 0x100)
        self.assertEqual(row['read_slot'], STRIDE + 0x1200)
        self.assertEqual(row['tables'][0]['native_vmaddr'], 0x9000)

    def report(self, *, window=True, owned=True, exclusion=1, tail=False, before=4096, latent=False, forwarded=False):
        body = [instruction(0, 'mrs', R('x19')), instruction(4, 'sub', R('sp'), R('sp'), I(128)),
                instruction(8, 'str', R('x30'), M('sp', 120)), instruction(12, 'str', R('x19'), M('sp', 16)),
                instruction(16, 'mov', R('x1'), R('x19')), instruction(20, 'mov', R('x17'), R('x19')),
                instruction(24, 'b' if tail else 'bl', I(256)), instruction(28, 'add', R('x2'), R('x9'), I(16)),
                instruction(32, 'ldr', R('x20'), M('x2')), instruction(36, 'ldr', R('x8'), M('x20', 40)),
                instruction(40, 'ldr', R('x8'), M('x1', 40)), instruction(44, 'str', R('xzr'), M('sp', 16)),
                instruction(48, 'mov', R('x19'), R('xzr')), instruction(52, 'mov', R('x1'), R('xzr')),
                instruction(56, 'mov', R('x20'), R('xzr')), instruction(60, 'ldr', R('x30'), M('sp', 120)),
                instruction(64, 'add', R('sp'), R('sp'), I(128)), instruction(68, 'ret'),
                instruction(256, 'adrp', R('x16'), I(0))]
        graph = {item.address: item for item in body}
        if forwarded:
            graph[24] = instruction(24, 'bl', I(128))
            graph[128] = instruction(128, 'b', I(256))

        class Decoder:
            def __call__(self, pc):return graph.get(pc)
            def plt(self, pc):
                return {'effect': 'cached-self', 'read_slot': 8192, 'stack_exclusion_profile': exclusion,
                        'clobbers': ['x0', *(f'x{i}' for i in range(8, 17))], 'sp_copy_register': 'x9'} if pc == 256 else None
            def owned_image_data(self, address, width):return owned and address == 8192 and width == 8
            def mrs_stack_window(self, pc):return {'before': before, 'after': 256} if window else None
            def caller_frame(self, pc):return audit.State(native_memory_may_contain_tp=latent), {'status': 'synthetic'}

        return audit.Audit(Decoder()).site(0, entry_context=latent)

    def test_exact_leaf_preserves_sp_alias_and_untouched_tp_register(self):
        report = self.report()
        self.assertEqual(report['classification'], 'guard-only', report['reasons'])
        self.assertEqual([x['pc'] for x in report['accesses']], ['0x24', '0x28'])
        self.assertEqual(report['callee_effects'][0]['kind'], 'conditional-cached-pthread-self')

    def test_explicitly_traversed_tail_helper_keeps_original_bl_return(self):
        report = self.report(forwarded=True)
        self.assertEqual(report['classification'], 'guard-only', report['reasons'])
        effect = report['callee_effects'][0]
        self.assertTrue(effect['decoded_tail_forwarding'])
        self.assertEqual(effect['call_origin'], '0x18')

    def test_separation_return_and_latent_copy_requirements_are_not_waived(self):
        for arguments, reason in (({'window': False}, 'cached-self-table-stack-separation-unproved'),
                                  ({'owned': False}, 'cached-self-table-stack-separation-unproved'),
                                  ({'exclusion': 0}, 'cached-self-table-stack-separation-unproved'),
                                  ({'before': 16}, 'cached-self-table-stack-separation-unproved'),
                                  ({'tail': True}, 'cached-self-return-continuation-unproved'),
                                  ({'latent': True}, 'cached-self-latent-native-copies-unproved')):
            report = self.report(**arguments)
            with self.subTest(arguments=arguments):
                self.assertEqual(report['classification'], 'unknown')
                self.assertIn(reason, {row['reason'] for row in report['reasons']})
                self.assertEqual(report['callee_effects'], [])
