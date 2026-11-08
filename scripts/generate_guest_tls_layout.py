#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile pinned private bionic TLS constants; never execute or sign code."""
import base64
import hashlib
import json
import re
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "e4df46f213d8d363017dce7ded5d1f2159614425"
URL = f"https://android.googlesource.com/platform/bionic/+/{COMMIT}/libc/platform/bionic/tls_defines.h?format=TEXT"
SHA256 = "5a50e0cd87e5e19930b12ba13d482152391fbd1f0427d0cf18ec5b137930ef5d"
EXPRESSIONS = {
    "TlsMinSlot": "MIN_TLS_SLOT", "TlsMaxSlot": "MAX_TLS_SLOT",
    "TlsSlotCount": "BIONIC_TLS_SLOTS", "TlsStackGuardSlot": "TLS_SLOT_STACK_GUARD",
    "TlsPointerBytes": "sizeof(void*)", "TlsStackGuardOffset": "TLS_SLOT_STACK_GUARD * sizeof(void*)",
}


def parse_layout_ir(ir):
    rows = re.findall(r"^@layout_(\w+) = .*?constant i64 (-?\d+)", ir, re.MULTILINE)
    values = {name: int(value) for name, value in rows}
    if len(rows) != len(values) or set(values) != set(EXPRESSIONS):
        raise ValueError("compiler must emit each TLS fact exactly once")
    minimum, maximum = values["TlsMinSlot"], values["TlsMaxSlot"]
    guard, pointer = values["TlsStackGuardSlot"], values["TlsPointerBytes"]
    if not (-16 <= minimum <= 0 <= guard <= maximum <= 63
            and values["TlsSlotCount"] == maximum - minimum + 1
            and pointer == 8 and values["TlsStackGuardOffset"] == guard * pointer):
        raise ValueError("unsupported compiled arm64 TLS layout")
    return values


def main():
    directory = ROOT / ".deps/bionic-tls"
    directory.mkdir(parents=True, exist_ok=True)
    header = directory / "tls_defines.h"
    if not header.exists():
        with urllib.request.urlopen(URL, timeout=30) as response:
            encoded = response.read(65537)
        if len(encoded) > 65536:
            raise ValueError("TLS header download byte bound")
        data = base64.b64decode(encoded, validate=True)
        if hashlib.sha256(data).hexdigest() != SHA256:
            raise ValueError("pinned bionic TLS header checksum mismatch")
        pending = header.with_suffix(".pending")
        pending.write_bytes(data)
        pending.replace(header)
    if not header.is_file() or header.is_symlink() or header.stat().st_size > 32768:
        raise ValueError("invalid pinned TLS header file")
    if hashlib.sha256(header.read_bytes()).hexdigest() != SHA256:
        raise ValueError("pinned bionic TLS header checksum mismatch")
    out = ROOT / "build/macos/m5"
    out.mkdir(parents=True, exist_ok=True)
    source = out / "tls-layout.c"
    source.write_text('#include "tls_defines.h"\n' + "\n".join(
        f"const long long layout_{name} = (long long)({value});" for name, value in EXPRESSIONS.items()) + "\n")
    command = ["xcrun", "clang", "--target=aarch64-linux-android24", "-ffreestanding", "-nostdinc",
               "-I", str(directory), "-O2", "-S", "-emit-llvm", str(source), "-o", "-"]
    ir = subprocess.check_output(command, text=True, timeout=30)
    values = parse_layout_ir(ir)
    generated = "// Compiler-derived pinned bionic arm64 TLS constants; local output.\n#pragma once\n#include <cstdint>\nnamespace shiraz::guest::bionic {\n"
    generated += "\n".join(f"inline constexpr int64_t k{name} = {value};" for name, value in values.items())
    generated += "\n}\n"
    target = out / "guest_tls_layout.hpp"
    if not target.exists() or target.read_text() != generated:
        target.write_text(generated)
    report = {"bionic_commit": COMMIT, "url": URL, "header_sha256": SHA256,
              "license": "original BSD notice retained in local header",
              "values": values, "command": command,
              "compiler_version": subprocess.check_output(["xcrun", "clang", "--version"], text=True),
              "ir_sha256": hashlib.sha256(ir.encode()).hexdigest(),
              "generator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "scope": "layout facts only; private slots and ELF TLS are not implemented"}
    target.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    (directory / "provenance.json").write_text(json.dumps({k: report[k] for k in
        ("bionic_commit", "url", "header_sha256", "license")}, indent=2) + "\n")
    print("M5_TLS_LAYOUT_COMPILED guard_offset=" + str(values["TlsStackGuardOffset"]) + " execution=0 signing=0")


if __name__ == "__main__":
    main()
