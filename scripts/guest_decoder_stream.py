# SPDX-License-Identifier: GPL-3.0-only
"""Generate a pinned Capstone detail-only iterator without changing public output."""

import hashlib

PINNED = {
    "cs.c": "aff33046383519526901203dca57eb5cbf7eaab06f3ba6afdcb586139229e92f",
    "SStream.c": "db4e88b3ee420c2bd51e87d7016a1622940d6d443f2e1aefa024d2b9a8c8e1d8",
    "SStream.h": "de6a87ecbf13fd8531142ee643e08510ac41a5d206ea3904fb353b5da321f775",
}


def _replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError("unexpected pinned decoder source shape")
    return text.replace(old, new, 1)


def generate(inputs):
    """Return modified copies only for the exact verified upstream inputs."""
    if set(inputs) != set(PINNED):
        raise ValueError("unexpected decoder source set")
    for name, expected in PINNED.items():
        if hashlib.sha256(inputs[name].encode("utf-8")).hexdigest() != expected:
            raise ValueError(f"unexpected pinned decoder source: {name}")
    header = _replace_once(
        inputs["SStream.h"], "\tint index;", "\tint index;\n\tbool shiraz_suppress_text;"
    )
    stream = _replace_once(
        inputs["SStream.c"], "ss->index = 0;", "ss->index = 0;\n\tss->shiraz_suppress_text = false;"
    )
    for signature in (
        "void SStream_concat0(SStream *ss, const char *s)",
        "void SStream_concat1(SStream *ss, const char c)",
        "void SStream_concat(SStream *ss, const char *fmt, ...)",
    ):
        marker = signature + "\n{"
        stream = _replace_once(stream, marker, marker + "\n\tif (ss->shiraz_suppress_text) return;")
    source = inputs["cs.c"]
    original = (
        "CAPSTONE_EXPORT\nbool CAPSTONE_API cs_disasm_iter(csh ud, const uint8_t **code, size_t *size,\n"
        "\t\tuint64_t *address, cs_insn *insn)\n{"
    )
    replacement = (
        "static bool shiraz_iter_impl(csh ud, const uint8_t **code, size_t *size,\n"
        "\t\tuint64_t *address, cs_insn *insn, bool suppress_text)\n{"
    )
    source = _replace_once(source, original, replacement)
    start = source.index(replacement)
    end = source.index("// return friendly name of register", start)
    block = source[start:end]
    block = _replace_once(
        block,
        "SStream_Init(&ss);",
        "SStream_Init(&ss);\n\t\tss.shiraz_suppress_text = suppress_text;",
    )
    block += (
        "CAPSTONE_EXPORT\nbool CAPSTONE_API cs_disasm_iter(csh ud, const uint8_t **code, "
        "size_t *size, uint64_t *address, cs_insn *insn) {\n"
        "\treturn shiraz_iter_impl(ud, code, size, address, insn, false);\n}\n"
        "bool shiraz_cs_disasm_detail(csh ud, const uint8_t **code, size_t *size, "
        "uint64_t *address, cs_insn *insn) {\n"
        "\treturn shiraz_iter_impl(ud, code, size, address, insn, true);\n}\n\n"
    )
    return {"cs.c": source[:start] + block + source[end:], "SStream.c": stream, "SStream.h": header}
