# SPDX-License-Identifier: GPL-3.0-only
"""Exact first-party allocator ABI contract; not native-body authentication."""

MALLOC_ABI = "heap/managed-malloc-v1"


def managed_malloc_binding(binding):
    if binding.get("abi") != MALLOC_ABI:
        return False
    if (
        binding.get("symbol") != "malloc"
        or binding.get("version") not in ("", "LIBC")
        or binding.get("provider") != "libc.so"
        or binding.get("kind") != "function"
        or binding.get("target") is not None
        or binding.get("provider_elf_sha256") is not None
        or binding.get("nonreturning", False) is not False
        or "native_target" in binding
        or type(binding.get("addend")) is not int
        or binding["addend"] != 0
        or type(binding.get("relocation_type")) is not int
        or binding["relocation_type"] not in (1025, 1026)
    ):
        raise ValueError("managed malloc contract requires exact native binding")
    return True


ASSUMPTIONS = (
    "conforming native allocator and private C++ runtime objects",
    "no guest callbacks from the native allocator",
    "stable explicit TLS cells through synchronous handler and destructors",
    "valid native ABI frames/control flow; normal returns only, no pre-write safety proof",
)
