#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile private bionic pthread layout facts; do not execute guest code."""

import base64
import hashlib
import json
import re
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "e4df46f213d8d363017dce7ded5d1f2159614425"
SOURCES = {
    "bionic/pthread_internal.h": "90b8831568369345c3d493c9a33b3937dbedceb9fdd8cafb799668fb23ddaa40",
    "private/bionic_elf_tls.h": "5c30947f43746c570be9866b2c82a33bcbae1518a816fb50f5d7f6e879624cbb",
    "private/bionic_lock.h": "a2fc13d09999c6669325241cd454ad8cb277c2a9d353be0d3393d30a57c7af9c",
    "private/bionic_tls.h": "59eba40a16df322e63b18a431f4014de29b005185af069652fc429e537fbced8",
    "private/bionic_futex.h": "cd95440f87a1fc24cb40fe89bae32c2230868a77c248c66f6a3eb781baa73feb",
    "private/grp_pwd.h": "dc5a46b1e41887440c4687f7eb18dfa538d0a8d61e27e3a9ae6b34a829018c75",
    "platform/bionic/macros.h": "bb3185b78099156a5977d2286f57eb2b2b15e1c2e3d5851e25613832b40cf242",
    "platform/bionic/tls.h": "5784cc489a2d3d9f45326e8cce3d4d6cc0224706f13ca4c45ab018e39d38f313",
    "platform/bionic/tls_defines.h": "5a50e0cd87e5e19930b12ba13d482152391fbd1f0427d0cf18ec5b137930ef5d",
}
FIELDS = (
    "next",
    "prev",
    "tid",
    "attr",
    "join_state",
    "cleanup_stack",
    "start_routine",
    "start_routine_arg",
    "return_value",
    "start_mask",
    "alternate_signal_stack",
    "shadow_call_stack_guard_region",
    "stack_top",
    "terminating",
    "startup_handshake_lock",
    "mmap_base",
    "mmap_size",
    "mmap_base_unguarded",
    "mmap_size_unguarded",
    "vma_name_buffer",
    "thread_local_dtors",
    "current_dlerror",
    "dlerror_buffer",
    "bionic_tls",
    "errno_value",
    "vfork_child_stack_bottom",
)
ATTR_FIELDS = (
    "flags",
    "stack_base",
    "stack_size",
    "guard_size",
    "sched_policy",
    "sched_priority",
    "__reserved",
)
EXPRESSIONS = {
    "size": "sizeof(pthread_internal_t)",
    "align": "alignof(pthread_internal_t)",
    "pointer_size": "sizeof(void*)",
    "pid_size": "sizeof(pid_t)",
    "handle_size": "sizeof(pthread_t)",
    "attr_size": "sizeof(pthread_attr_t)",
    **{field: f"__builtin_offsetof(pthread_internal_t, {field})" for field in FIELDS},
    **{f"width_{field}": f"sizeof(((pthread_internal_t*)0)->{field})" for field in FIELDS},
    **{f"attr_{field}": f"__builtin_offsetof(pthread_attr_t, {field})" for field in ATTR_FIELDS},
    **{f"attr_width_{field}": f"sizeof(((pthread_attr_t*)0)->{field})" for field in ATTR_FIELDS},
    **{
        f"attr_align_{field}": f"alignof(decltype(((pthread_attr_t*)0)->{field}))"
        for field in ATTR_FIELDS
    },
    "join_not_joined": "THREAD_NOT_JOINED",
    "join_exited_not_joined": "THREAD_EXITED_NOT_JOINED",
    "join_joined": "THREAD_JOINED",
    "join_detached": "THREAD_DETACHED",
    "attr_flag_detached": "PTHREAD_ATTR_FLAG_DETACHED",
    "attr_flag_joined": "PTHREAD_ATTR_FLAG_JOINED",
    "attr_flag_inherit": "PTHREAD_ATTR_FLAG_INHERIT",
    "attr_flag_explicit": "PTHREAD_ATTR_FLAG_EXPLICIT",
}


def parse_layout_ir(ir):
    rows = re.findall(r"^@layout_(\w+) = .*?constant i64 (\d+)", ir, re.MULTILINE)
    values = {name: int(value) for name, value in rows}
    if len(rows) != len(values) or set(values) != set(EXPRESSIONS):
        raise ValueError("compiler must emit each pthread layout fact exactly once")
    size, alignment = values["size"], values["align"]
    if not (0 < size <= 65536 and alignment in (4, 8, 16) and size % alignment == 0):
        raise ValueError("unsupported pthread object size or alignment")
    if values["next"] != 0 or values["prev"] != 8 or values["tid"] != 16:
        raise ValueError("unexpected arm64 pthread identity prefix")
    if (values["pointer_size"], values["pid_size"], values["handle_size"]) != (8, 4, 8):
        raise ValueError("unexpected pthread ABI widths")
    if not 0 < values["attr_size"] <= size - values["attr"]:
        raise ValueError("invalid pthread attribute extent")
    for index, field in enumerate(FIELDS):
        start, width = values[field], values[f"width_{field}"]
        end = values[FIELDS[index + 1]] if index + 1 < len(FIELDS) else size
        if not width or start >= end or width > end - start:
            raise ValueError("pthread field overlaps another field or object boundary")
    if values["width_attr"] != values["attr_size"]:
        raise ValueError("pthread attribute size mismatch")
    if [
        values[f"width_{field}"] for field in ("next", "prev", "tid", "join_state", "stack_top")
    ] != [8, 8, 4, 4, 8]:
        raise ValueError("unexpected pthread identity or state field widths")
    for index, field in enumerate(ATTR_FIELDS):
        start, width = values[f"attr_{field}"], values[f"attr_width_{field}"]
        alignment = values[f"attr_align_{field}"]
        end = (
            values[f"attr_{ATTR_FIELDS[index + 1]}"]
            if index + 1 < len(ATTR_FIELDS)
            else values["attr_size"]
        )
        if not width or not alignment or start % alignment or start >= end or width > end - start:
            raise ValueError("pthread attribute overlaps another field or attribute boundary")
    if [values[f"attr_width_{field}"] for field in ATTR_FIELDS] != [4, 8, 8, 8, 4, 4, 16]:
        raise ValueError("unexpected pthread attribute field widths")
    if [values[f"attr_align_{field}"] for field in ATTR_FIELDS] != [4, 8, 8, 8, 4, 4, 1]:
        raise ValueError("unexpected pthread attribute field alignments")
    if [
        values[name]
        for name in ("join_not_joined", "join_exited_not_joined", "join_joined", "join_detached")
    ] != [0, 1, 2, 3]:
        raise ValueError("unexpected pthread join states")
    if [
        values[name]
        for name in (
            "attr_flag_detached",
            "attr_flag_joined",
            "attr_flag_inherit",
            "attr_flag_explicit",
        )
    ] != [1, 2, 4, 8]:
        raise ValueError("unexpected pthread attribute flags")
    return values


def verified_header(directory, path, digest):
    target = directory / path
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        url = f"https://android.googlesource.com/platform/bionic/+/{COMMIT}/libc/{path}?format=TEXT"
        with urllib.request.urlopen(url, timeout=30) as response:
            encoded = response.read(131073)
        if len(encoded) > 131072:
            raise ValueError("pthread source download byte bound")
        data = base64.b64decode(encoded, validate=True)
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError("pinned pthread source checksum mismatch")
        pending = target.with_suffix(target.suffix + ".pending")
        pending.write_bytes(data)
        pending.replace(target)
    if not target.is_file() or target.is_symlink() or target.stat().st_size > 65536:
        raise ValueError("invalid pinned pthread source")
    if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
        raise ValueError("pinned pthread source checksum mismatch")


def main():
    directory = ROOT / ".deps/bionic-pthread-layout"
    for path, digest in SOURCES.items():
        verified_header(directory, path, digest)
    headers = ROOT / ".deps/bionic-headers/sysroot/include"
    provenance = json.loads((ROOT / ".deps/bionic-headers/provenance.json").read_text())
    if provenance.get("commit") != COMMIT:
        raise ValueError("bionic public and private headers must share the pinned revision")
    out = ROOT / "build/macos/m5"
    out.mkdir(parents=True, exist_ok=True)
    source = out / "pthread-layout.cpp"
    source.write_text(
        '#include "bionic/pthread_internal.h"\nextern "C" {\n'
        + "\n".join(
            f"extern const unsigned long long layout_{name} = (unsigned long long)({expr});"
            for name, expr in EXPRESSIONS.items()
        )
        + "\n}\n"
    )
    command = [
        "xcrun",
        "clang++",
        "--target=aarch64-linux-android24",
        "-std=c++17",
        "-Wno-invalid-" + "offsetof",
        "-isystem",
        str(headers),
        "-I",
        str(directory),
        "-O2",
        "-S",
        "-emit-llvm",
        str(source),
        "-o",
        "-",
    ]
    ir = subprocess.check_output(command, text=True, timeout=30)
    if 'target triple = "aarch64-unknown-linux-android' not in ir:
        raise ValueError("pthread layout requires the Android arm64 compiler target")
    values = parse_layout_ir(ir)
    output = "// Compiler-derived pinned private bionic arm64 facts; layout only.\n#pragma once\n#include <cstdint>\nnamespace shiraz::guest::bionic {\n"
    output += "\n".join(
        f"inline constexpr uint64_t kPthread_{name} = {value};" for name, value in values.items()
    )
    output += "\n}\n"
    target = out / "guest_pthread_layout.hpp"
    if not target.exists() or target.read_text() != output:
        target.write_text(output)
    target.with_suffix(".json").write_text(
        json.dumps(
            {
                "bionic_commit": COMMIT,
                "sources_sha256": SOURCES,
                "license": "original notices retained in local source headers",
                "values": values,
                "command": command,
                "compiler_version": subprocess.check_output(
                    ["xcrun", "clang++", "--version"], text=True
                ),
                "ir_sha256": hashlib.sha256(ir.encode()).hexdigest(),
                "scope": "layout facts only; pthread thread objects and private fields are not implemented",
            },
            indent=2,
        )
        + "\n"
    )
    print("M5_PTHREAD_LAYOUT_COMPILED fields=" + str(len(FIELDS)) + " execution=0 identity=0")


if __name__ == "__main__":
    main()
