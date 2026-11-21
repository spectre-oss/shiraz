#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate a bounded lookup for the pinned Capstone AArch64 name table.

This transforms a verified upstream source file into a separate build input.
It does not modify the upstream source or replace its license notices.
"""

import hashlib
import os
import re
import subprocess

MAPPING_FUNCTION_HASH = "bbdfc3d94bc8a8d0f6ab2f6a0a4d5545bc8c19e43060d2d50e410bb77847170d"
PINNED_NAME_COUNT = 1290
_FUNCTION = "arm64_insn AArch64_map_insn(const char *name)"
_NEXT_FUNCTION = "// map internal raw vregister"
_TABLE = "static const char * const insn_name_maps[] = {"
_INCLUDE = '#include "AArch64MappingInsnName.inc"'
_ENTRY = re.compile(r'^\s*"([a-z0-9_.]+)"\s*,\s*(?://.*)?$')


def _entries(text):
    result = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or re.fullmatch(r"/\*.*\*/", stripped):
            continue
        match = _ENTRY.fullmatch(line)
        if not match:
            raise ValueError("unexpected Capstone instruction-name entry")
        result.append(match.group(1))
    return result


def _rewrite(source, included_names, expected_hash, expected_count):
    if (
        source.count(_TABLE) != 1
        or source.count(_FUNCTION) != 1
        or source.count(_NEXT_FUNCTION) != 1
    ):
        raise ValueError("unexpected Capstone mapping structure")
    table_start = source.index(_TABLE) + len(_TABLE)
    table_end = source.index("};", table_start)
    table = source[table_start:table_end]
    if table.count(_INCLUDE) != 1:
        raise ValueError("unexpected Capstone name-table include")
    prefix, suffix = table.split(_INCLUDE)
    if not re.fullmatch(r"\s*NULL,\s*// ARM64_INS_INVALID\s*", prefix):
        raise ValueError("unexpected Capstone invalid-name entry")
    names = _entries(included_names) + _entries(suffix)
    if len(names) != expected_count or len(names) >= 65536:
        raise ValueError("unexpected Capstone instruction-name count")
    start = source.index(_FUNCTION)
    end = source.index(_NEXT_FUNCTION, start)
    if hashlib.sha256(source[start:end].encode("utf-8")).hexdigest() != expected_hash:
        raise ValueError("unexpected Capstone mapping function")
    order = sorted(range(1, len(names) + 1), key=lambda i: (names[i - 1].encode("ascii"), i))
    indices = ", ".join(map(str, order))
    replacement = f'''static const unsigned short shiraz_name_order[] = {{{indices}}};
_Static_assert(ARR_SIZE(insn_name_maps) == ARR_SIZE(shiraz_name_order) + 1,
               "unexpected Capstone instruction-name table");
arm64_insn AArch64_map_insn(const char *name)
{{
    size_t lo = 0, hi = ARR_SIZE(shiraz_name_order);
    while (lo < hi) {{
        size_t mid = lo + (hi - lo) / 2;
        int comparison = strcmp(name, insn_name_maps[shiraz_name_order[mid]]);
        if (comparison > 0) lo = mid + 1;
        else hi = mid;
    }}
    if (lo < ARR_SIZE(shiraz_name_order) && !strcmp(name, insn_name_maps[shiraz_name_order[lo]]))
        return shiraz_name_order[lo];
    return ARM64_INS_INVALID;
}}

'''
    return source[:start] + replacement + source[end:]


def optimized_mapping(source, included_names):
    """Return the optimized source only for the expected pinned mapping."""
    return _rewrite(source, included_names, MAPPING_FUNCTION_HASH, PINNED_NAME_COUNT)


def build_mapping_object(source, archive, work, compiler, sdk):
    """Replace the mapping object in a private staging archive.

    The caller owns publication of the completed archive. All source inputs
    must already have been verified against the pinned upstream archive.
    """
    mapping = source / "arch/AArch64"
    generated = optimized_mapping(
        (mapping / "AArch64Mapping.c").read_text(encoding="utf-8"),
        (mapping / "AArch64MappingInsnName.inc").read_text(encoding="utf-8"),
    )
    if archive.is_symlink() or not archive.is_file() or work.is_symlink():
        raise ValueError("invalid decoder staging path")
    work.mkdir(parents=True, exist_ok=True)
    generated_source = work / "AArch64Mapping.c"
    generated_object = work / "AArch64Mapping.c.o"
    if generated_source.is_symlink() or generated_object.is_symlink():
        raise ValueError("invalid decoder generated path")
    generated_source.write_text(generated, encoding="utf-8")
    env = {**os.environ, "ZERO_AR_DATE": "1"}
    members = subprocess.run(
        ["/usr/bin/ar", "-t", str(archive)], check=True, capture_output=True, text=True, env=env
    ).stdout.splitlines()
    if members.count("AArch64Mapping.c.o") != 1:
        raise ValueError("unexpected decoder archive members")
    subprocess.run(
        [
            compiler,
            "-DCAPSTONE_ARM64_SUPPORT",
            "-DCAPSTONE_HAS_ARM64",
            "-DCAPSTONE_USE_SYS_DYN_MEM",
            "-I" + str(source / "include"),
            "-I" + str(mapping),
            "-O3",
            "-DNDEBUG",
            "-std=gnu99",
            "-arch",
            "arm64",
            "-isysroot",
            sdk,
            "-fPIC",
            "-c",
            str(generated_source),
            "-o",
            str(generated_object),
        ],
        check=True,
        env=env,
    )
    subprocess.run(["/usr/bin/ar", "-r", str(archive), str(generated_object)], check=True, env=env)
    subprocess.run(["/usr/bin/ranlib", str(archive)], check=True, env=env)
    members_after = subprocess.run(
        ["/usr/bin/ar", "-t", str(archive)], check=True, capture_output=True, text=True, env=env
    ).stdout.splitlines()
    if members_after != members:
        raise ValueError("decoder archive member order changed")
    return hashlib.sha256(generated.encode()).hexdigest()
