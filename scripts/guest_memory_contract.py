# SPDX-License-Identifier: GPL-3.0-only
"""Exact first-party byte-leaf binding identities; not caller range proofs.

The trusted bridge implements argument-only memory accesses and no calls, SP/TLS
accesses or hidden spill frames. Sources/destinations can still carry TP facts;
recognition alone must never authorize an unknown range or a safe TLS consumer.
"""
ABIS = {"memory/byte-copy-v1": "memcpy", "memory/byte-move-v1": "memmove"}


def byte_memory_binding(binding):
    symbol = ABIS.get(binding.get("abi"))
    if symbol is None:
        return False
    if (
        binding.get("symbol") != symbol
        or binding.get("version") not in ("", "LIBC")
        or binding.get("provider") != "libc.so"
        or binding.get("kind") != "function"
        or binding.get("target") is not None
        or binding.get("provider_elf_sha256") is not None
        or binding.get("nonreturning", False) is not False
        or "native_target" in binding
        or type(binding.get("addend")) is not int or binding["addend"] != 0
        or type(binding.get("relocation_type")) is not int
        or binding["relocation_type"] not in (1025, 1026)
    ):
        raise ValueError("byte memory contract requires exact native binding")
    return True
