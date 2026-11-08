# SPDX-License-Identifier: GPL-3.0-only
"""Validate native entry identity snapshots without granting semantic effects.

A mapped prefix and Mach-O UUID identify the producer's resolved target. They
neither authenticate an arbitrary producer nor prove complete control flow,
memory effects, execution or native TLS separation.
"""

import hashlib

SELF_ABI = "native/pthread-self-noargs"
SELF_IMAGE = "/usr/lib/system/libsystem_pthread.dylib"
FIELDS = {
    "profile", "verification", "image", "image_uuid", "symbol", "entry_offset",
    "prefix_hex", "execution_claim", "semantic_effects_verified",
}


def lower_hex(value, length):
    return (
        isinstance(value, str)
        and len(value) == length
        and all(character in "0123456789abcdef" for character in value)
    )


def validate_native_target(binding):
    """Return a copy with a checked prefix digest, or None for other adapters."""
    record = binding.get("native_target")
    if "native_target" not in binding and binding.get("abi") != SELF_ABI:
        return None
    if (
        binding.get("abi") != SELF_ABI
        or binding.get("symbol") != "pthread_self"
        or binding.get("version") not in ("", "LIBC")
        or binding.get("provider") != "libc.so"
        or binding.get("kind") != "function"
        or binding.get("target") is not None
        or binding.get("provider_elf_sha256") is not None
        or binding.get("nonreturning", False) is not False
        or type(binding.get("addend")) is not int
        or binding["addend"] != 0
        or type(binding.get("relocation_type")) is not int
        or binding["relocation_type"] not in (1025, 1026)
    ):
        raise ValueError("native target requires exact pthread self binding")
    if not isinstance(record, dict) or set(record) not in (
        FIELDS, FIELDS | {"mapped_prefix_sha256"}
    ):
        raise ValueError("native target identity schema")
    if (
        type(record["profile"]) is not int
        or record["profile"] != 1
        or record["verification"] != "resolved-system-image-prefix"
        or record["image"] != SELF_IMAGE
        or record["symbol"] != "pthread_self"
        or not lower_hex(record["image_uuid"], 32)
        or record["image_uuid"] == "0" * 32
        or type(record["entry_offset"]) is not int
        or not 0 <= record["entry_offset"] <= (1 << 64) - 64
        or record["entry_offset"] & 3
        or not lower_hex(record["prefix_hex"], 128)
        or record["execution_claim"] is not False
        or record["semantic_effects_verified"] is not False
    ):
        raise ValueError("native target identity fields or unsupported claim")
    digest = hashlib.sha256(bytes.fromhex(record["prefix_hex"])).hexdigest()
    if "mapped_prefix_sha256" in record and record["mapped_prefix_sha256"] != digest:
        raise ValueError("native target prefix digest mismatch")
    return {**record, "mapped_prefix_sha256": digest}
