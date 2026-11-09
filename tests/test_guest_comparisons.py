# SPDX-License-Identifier: GPL-3.0-only
"""Flag-only alias regressions; synthetic CFGs are not complete guest TLS proof."""
import dataclasses
import importlib.util
import unittest

from test_guest_logical_registers import shifted
from test_guest_tpidr_audit import I, M, R, audit, instruction


@unittest.skipUnless(importlib.util.find_spec('capstone'), 'requires pinned Capstone')
class ComparisonTests(unittest.TestCase):
    def setUp(self):
        import capstone
        from capstone import arm64
        self.adapter = audit.ElfDecoder.__new__(audit.ElfDecoder)
        self.adapter.decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
        self.adapter.decoder.detail = True
        self.adapter.arm64 = arm64
        self.adapter.control_groups, self.adapter.cache = set(), {}

    def decode(self, word):
        self.adapter.cache.clear()
        self.adapter.word = lambda address: word.to_bytes(4, 'little') if address == 0 else None
        return self.adapter(0)

    def graph(self, compare, setup=(), tail=(), initial=None):
        body = [instruction(0,'mrs',R('x19')), instruction(4,'ldr',R('x8'),M('x19',40))]
        body.extend(setup)
        body.append(dataclasses.replace(compare,address=4*len(body)))
        start = len(body)
        body.extend(dataclasses.replace(item,address=4*(start+i)) for i,item in enumerate(tail))
        body.extend([instruction(4*len(body),'mov',R('x19'),R('xzr')),
                     instruction(4*(len(body)+1),'ret')])
        graph = {item.address:item for item in body}
        if initial is None:
            return audit.Audit(graph.get).site(0)
        class Decoder:
            def __call__(self, pc):return graph.get(pc)
            def caller_frame(self, pc):return initial.copy(), {'status':'synthetic-entry-state'}
        return audit.Audit(Decoder()).site(0, entry_context=True)

    def test_real_alias_metadata_reads_both_sources_and_writes_only_flags(self):
        for base,op in [(0xeb00001f,'cmp'),(0xab00001f,'cmn'),(0xea00001f,'tst')]:
            for narrow in (False,True):
                word = (base & ~0x80000000 if narrow else base) | 2<<16 | 1<<5
                converted = self.decode(word)
                prefix = 'w' if narrow else 'x'
                self.assertEqual(converted.mnemonic,op)
                self.assertEqual(converted.reads,(prefix+'1',prefix+'2'))
                self.assertEqual(converted.writes,('nzcv',))
        for word in (0xf100043f,0xb100043f,0xf140043f,0xb140043f,0xf240003f):
            converted = self.decode(word)
            self.assertEqual(converted.reads,('x1',))
            self.assertEqual(converted.writes,('nzcv',))

    def test_first_source_pointer_comparisons_cannot_be_erased_by_alias_metadata(self):
        for base,immediate in [(0xeb00001f,False),(0xab00001f,False),(0xea00001f,False),
                               (0xf100001f,True),(0xb100001f,True),(0xf200001f,True)]:
            for narrow in (False,True):
                word = base & ~0x80000000 if narrow else base
                word |= 19<<5
                if not immediate:word |= 31<<16
                converted = self.decode(word)
                result = self.graph(converted)
                self.assertEqual(result['classification'],'unknown',result)
                self.assertIn('comparison address input',{r['reason'] for r in result['reasons']})
        # The second source and its narrow/extended views also consume TP bits.
        for word in (0xeb00001f | 19<<16 | 1<<5,
                     0x6b20001f | 19<<16 | 1<<13 | 1<<5,
                     0xea00001f | 19<<16 | 1<<5):
            result = self.graph(self.decode(word))
            self.assertIn('comparison address input',{r['reason'] for r in result['reasons']})

    def test_comparison_preserves_exact_sources_for_later_guard_address(self):
        for word in (0xeb00001f | 31<<16 | 9<<5, 0xf100001f | 9<<5 | 40<<10,
                     0xab00001f | 31<<16 | 9<<5, 0xea00001f | 31<<16 | 9<<5,
                     0x6b20001f | 2<<16 | 1<<13 | 9<<5):
            result = self.graph(self.decode(word),[instruction(8,'mov',R('x9'),I(40))],[
                instruction(0,'add',R('x20'),R('x19'),R('x9')),
                instruction(0,'ldr',R('x8'),M('x20')),
                instruction(0,'mov',R('x20'),R('xzr'))])
            self.assertEqual(result['classification'],'guard-only',result['reasons'])
            self.assertEqual([r['offset'] for r in result['accesses']],[40,40])

    def test_legal_shift_and_extension_fields_cross_decoder_and_audit(self):
        count = 0
        for width in (32,64):
            sf = (width==64)<<31
            for base in (0x6b00001f,0x2b00001f,0x6a00001f):
                op = 'tst' if base==0x6a00001f else 'cmp' if base==0x6b00001f else 'cmn'
                for shift in range(4 if op=='tst' else 3):
                    for amount in range(width):
                        converted = self.decode(sf | base | shift<<22 | 2<<16 | amount<<10 | 1<<5)
                        self.assertEqual(converted.mnemonic,op)
                        self.assertEqual(converted.writes,('nzcv',))
                        result = self.graph(converted)
                        self.assertEqual(result['classification'],'guard-only',result['reasons']);count+=1
            for base in (0x6b20001f,0x2b20001f):
                for extension in range(8):
                    for amount in range(5):
                        converted = self.decode(sf | base | 2<<16 | extension<<13 | amount<<10 | 1<<5)
                        self.assertEqual(converted.writes,('nzcv',))
                        result = self.graph(converted)
                        self.assertEqual(result['classification'],'guard-only',result['reasons']);count+=1
        self.assertEqual(count,1120)

    def test_all_logical_immediate_fields_validate_the_decoder_masks(self):
        count = 0
        for width in (32,64):
            for n in (0,1):
                for immr in range(64):
                    for imms in range(64):
                        word = (width==64)<<31 | 0x7200001f | n<<22 | immr<<16 | imms<<10 | 1<<5
                        converted = self.decode(word)
                        if converted is None:continue
                        self.assertEqual(converted.mnemonic,'tst')
                        self.assertEqual(converted.reads,('w1' if width==32 else 'x1',))
                        self.assertEqual(converted.writes,('nzcv',));count+=1
        self.assertEqual(count,11328)

    def test_malformed_and_nonarchitectural_effects_are_explicit_refusals(self):
        base = self.decode(0xeb02003f)
        variants = [dataclasses.replace(base,operands=(R('sp'),R('x2'))),
                    dataclasses.replace(base,operands=(R('x1'),shifted('x2','ror',1))),
                    dataclasses.replace(base,operands=(R('x1'),shifted('x2','lsl',64))),
                    dataclasses.replace(base,operands=(R('x1'),R('w2'))),
                    dataclasses.replace(base,writes=('nzcv','x1')),
                    dataclasses.replace(base,reads=('x1','x19')),
                    dataclasses.replace(base,writeback=True),
                    dataclasses.replace(base,operands=(R('x1'),audit.Operand('imm',4096))),
                    dataclasses.replace(base,operands=(R('x1'),audit.Operand('imm',True)))]
        for converted in variants:
            result = self.graph(converted)
            self.assertEqual(result['classification'],'unknown',result)
            self.assertTrue(any(r['reason'].startswith('comparison') for r in result['reasons']))
        for value in (0,-1,3<<2 | 1<<16):
            with self.assertRaises(ValueError):
                audit.comparison_sources((R('x1'),I(value)),8,'tst')

    def test_both_condition_successors_remain_live_after_clean_comparison(self):
        comparison = dataclasses.replace(self.decode(0xeb02003f),address=8)
        body = [instruction(0,'mrs',R('x19')),instruction(4,'ldr',R('x8'),M('x19',40)),comparison,
                instruction(12,'b.eq',I(24)),instruction(16,'ldr',R('x8'),M('x19',48)),
                instruction(20,'b',I(28)),instruction(24,'ldr',R('x8'),M('x19',40)),
                instruction(28,'mov',R('x19'),R('xzr')),instruction(32,'ret')]
        result = audit.Audit({item.address:item for item in body}.get).site(0)
        self.assertEqual(result['classification'],'unknown')
        observed = {(r['pc'],r['offset']) for r in result['accesses']}
        self.assertIn(('0x10',48),observed)
        self.assertIn(('0x18',40),observed)

    def test_narrow_views_of_image_stack_control_and_auth_inputs_are_refused(self):
        for field,value in [('aliases',frozenset({-32})),('image_addresses',0x1000),
                            ('return_values',frozenset({0x1234})),
                            ('return_signatures',audit.ReturnSignature(frozenset({0x1234}),0,'A'))]:
            for first in (True,False):
                initial = audit.State()
                getattr(initial,field)['x9'] = value
                word = 0x6b00001f | (2 if first else 9)<<16 | (9 if first else 1)<<5
                with self.subTest(field=field,first=first):
                    result = self.graph(self.decode(word),initial=initial)
                    self.assertIn('comparison address input',{r['reason'] for r in result['reasons']})
