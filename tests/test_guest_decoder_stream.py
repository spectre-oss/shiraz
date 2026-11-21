# SPDX-License-Identifier: GPL-3.0-only
"""Validate the pinned decoder's per-call stream transformation."""

import hashlib
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import guest_decoder_stream as stream  # noqa: E402

HEADER = "typedef struct {\n\tint index;\n} SStream;\n"
STREAM = """void SStream_Init(SStream *ss)
{
 ss->index = 0;
}
void SStream_concat0(SStream *ss, const char *s)
{
}
void SStream_concat1(SStream *ss, const char c)
{
}
void SStream_concat(SStream *ss, const char *fmt, ...)
{
}
"""
SOURCE = """CAPSTONE_EXPORT
bool CAPSTONE_API cs_disasm_iter(csh ud, const uint8_t **code, size_t *size,
\t\tuint64_t *address, cs_insn *insn)
{
 SStream_Init(&ss);
 return true;
}
// return friendly name of register
remaining source
"""


class DecoderStreamTests(unittest.TestCase):
    def setUp(self):
        self.inputs = {"cs.c": SOURCE, "SStream.c": STREAM, "SStream.h": HEADER}
        hashes = {
            name: hashlib.sha256(text.encode()).hexdigest() for name, text in self.inputs.items()
        }
        patcher = patch.object(stream, "PINNED", hashes)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_per_call_rewrite_preserves_standard_api(self):
        result = stream.generate(self.inputs)
        self.assertIn("bool shiraz_suppress_text", result["SStream.h"])
        self.assertEqual(result["SStream.c"].count("if (ss->shiraz_suppress_text) return;"), 3)
        self.assertIn("ss.shiraz_suppress_text = suppress_text", result["cs.c"])
        self.assertIn("insn, false)", result["cs.c"])
        self.assertIn("insn, true)", result["cs.c"])
        self.assertTrue(
            result["cs.c"].endswith("// return friendly name of register\nremaining source\n")
        )
        self.assertEqual(result, stream.generate(self.inputs))

    def test_modified_or_missing_sources_fail_closed(self):
        for name in self.inputs:
            with self.subTest(name=name):
                changed = dict(self.inputs)
                changed[name] += "\n"
                with self.assertRaisesRegex(ValueError, "unexpected pinned decoder source"):
                    stream.generate(changed)
        with self.assertRaisesRegex(ValueError, "source set"):
            stream.generate({"cs.c": SOURCE})

    def test_ambiguous_source_shape_is_refused_even_with_matching_hash(self):
        self.inputs["SStream.c"] += STREAM
        stream.PINNED["SStream.c"] = hashlib.sha256(self.inputs["SStream.c"].encode()).hexdigest()
        with self.assertRaisesRegex(ValueError, "source shape"):
            stream.generate(self.inputs)


if __name__ == "__main__":
    unittest.main()
