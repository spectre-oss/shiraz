# SPDX-License-Identifier: GPL-3.0-only
"""Byte-exact conditional outlined frame tests, not app/TLS acceptance."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_guest_tpidr as a
import guest_frame_effects as f
from test_guest_tpidr_audit import I, M, R, instruction


class FrameEffectsTests(unittest.TestCase):
    def decoder(self, *, frame=96, target=256, words=f.WORDS):
        graph = {
            0: instruction(0, "sub", R("sp"), R("sp"), I(frame)),
            4: instruction(4, "mov", R("x28"), R("sp")),
            8: instruction(8, "bl", I(target)),
            12: instruction(12, "sub", R("sp"), R("sp"), I(1200)),
        }
        encoded = dict(enumerate(words, target // 4))

        class Decoder:
            def __call__(self, pc):
                return graph.get(pc)

            def frame_prologue(self, pc):
                return f.recognize(lambda address: encoded.get(address // 4), pc)

        return Decoder()

    def test_exact_body_preserves_saved_aliases_and_sets_frame(self):
        evidence = []
        state = a.caller_prologue(self.decoder(), 0, 16, helper_evidence=evidence)
        self.assertEqual((state.sp, state.fp), (-1296, -96))
        self.assertEqual(state.stack_aliases[-80], frozenset({-96}))
        self.assertEqual(len(state.stack), 10)
        self.assertEqual(evidence[0]["entry"], "0x100")
        self.assertEqual(evidence[0]["instructions"], 7)
        self.assertEqual(len(evidence[0]["body_sha256"]), 64)
        self.assertEqual(state.alias("x30"), a.CLEAN)

    def test_every_instruction_bit_mutation_and_truncation_rejected(self):
        for index in range(7):
            for bit in range(32):
                words = list(f.WORDS)
                words[index] ^= 1 << bit
                with self.subTest(index=index, bit=bit), self.assertRaises(a.MetadataUnavailable):
                    a.caller_prologue(self.decoder(words=words), 0, 16)
        for size in range(7):
            with self.assertRaises(a.MetadataUnavailable):
                a.caller_prologue(self.decoder(words=f.WORDS[:size]), 0, 16)

    def test_unallocated_misaligned_and_overlapping_frames_rejected(self):
        for arguments in ({"frame": 80}, {"frame": 0}, {"frame": 104}, {"target": 4}):
            evidence = []
            with self.assertRaises(a.MetadataUnavailable):
                a.caller_prologue(self.decoder(**arguments), 0, 16, helper_evidence=evidence)
            self.assertEqual(evidence, [])

    def test_saved_alias_overwrite_and_lr_clobber(self):
        decoder = self.decoder()
        graph = {
            4: instruction(4, "mov", R("x30"), R("sp")),
            8: instruction(8, "str", R("x30"), M("sp", 16)),
            12: instruction(12, "bl", I(256)),
        }

        class Decoder:
            frame_prologue = decoder.frame_prologue

            def __call__(self, pc):
                return graph.get(pc, decoder(pc))

        state = a.caller_prologue(Decoder(), 0, 16)
        self.assertEqual(state.alias("x30"), a.CLEAN)
        self.assertEqual(state.stack_aliases[-80], a.CLEAN)

    def test_helper_instruction_and_stack_slot_limits_are_enforced(self):
        from unittest.mock import patch
        for name, limit in (("MAX_CALLER_PRELUDE", 10), ("MAX_STACK_SLOTS", 9)):
            with patch.object(a, name, limit), self.assertRaises(a.MetadataUnavailable):
                a.caller_prologue(self.decoder(), 0, 16)

    def test_real_decoder_requires_whole_executable_body(self):
        decoder = object.__new__(a.ElfDecoder)
        decoder.data = b"".join(word.to_bytes(4, "little") for word in f.WORDS)
        decoder.segments = [(256, 284, 0)]
        self.assertIsNotNone(decoder.frame_prologue(256))
        decoder.segments = [(256, 280, 0)]
        self.assertIsNone(decoder.frame_prologue(256))
        self.assertIsNone(decoder.frame_prologue(257))

    def test_namespace_rebases_helper_evidence_only(self):
        from guest_namespace import STRIDE, NamespaceDecoder
        state = a.State(sp=-96)
        evidence = {"entry": "0x10", "outlined_helpers": [
            {"call": "0x18", "entry": "0x100", "body_size": 28,
             "body_sha256": "unchanged"}]}

        class Decoder:
            def caller_frame(self, pc):
                return state, evidence

        namespace = object.__new__(NamespaceDecoder)
        namespace.locate = lambda pc: (2, 32, Decoder())
        namespace.names = ["root", "one", "two"]
        result, proof = namespace.caller_frame(2 * STRIDE + 32)
        self.assertEqual(result.sp, -96)
        self.assertEqual(proof["outlined_helpers"][0]["call"], hex(2 * STRIDE + 24))
        self.assertEqual(proof["outlined_helpers"][0]["entry"], hex(2 * STRIDE + 256))
        self.assertEqual(proof["outlined_helpers"][0]["body_sha256"], "unchanged")
        self.assertEqual(evidence["outlined_helpers"][0]["entry"], "0x100")

    def test_unknown_decoder_still_refuses_calls(self):
        decoder = self.decoder()
        with self.assertRaises(a.MetadataUnavailable):
            a.caller_prologue(decoder.__call__, 0, 16)

    def test_failed_later_instruction_publishes_no_partial_evidence(self):
        decoder = self.decoder()

        class FailsLater:
            frame_prologue = decoder.frame_prologue

            def __call__(self, pc):
                return instruction(pc, "ldr", R("x0"), M("x0")) if pc == 12 else decoder(pc)

        evidence = []
        with self.assertRaises(a.MetadataUnavailable):
            a.caller_prologue(FailsLater(), 0, 16, helper_evidence=evidence)
        self.assertEqual(evidence, [])


if __name__ == "__main__":
    unittest.main()
