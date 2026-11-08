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
WINDOW_FIELDS = {"role", "symbol", "entry_offset", "byte_count", "prefix_hex"}
WINDOW_PROFILE_FIELDS = FIELDS | {
    "cold_call_offset", "complete_control_flow_verified", "code_windows",
}


def lower_hex(value, length):
    return (
        isinstance(value, str)
        and len(value) == length
        and all(character in "0123456789abcdef" for character in value)
    )


def validate_code_window(window, role, symbol, size):
    if not isinstance(window, dict) or set(window) not in (
        WINDOW_FIELDS, WINDOW_FIELDS | {"mapped_window_sha256"}
    ):
        raise ValueError("native target code window schema")
    if (
        window["role"] != role
        or window["symbol"] != symbol
        or type(window["byte_count"]) is not int
        or window["byte_count"] != size
        or type(window["entry_offset"]) is not int
        or not 0 <= window["entry_offset"] <= (1 << 64) - size
        or window["entry_offset"] & 3
        or not lower_hex(window["prefix_hex"], size * 2)
    ):
        raise ValueError("native target code window fields")
    digest = hashlib.sha256(bytes.fromhex(window["prefix_hex"])).hexdigest()
    if "mapped_window_sha256" in window and window["mapped_window_sha256"] != digest:
        raise ValueError("native target code window digest mismatch")
    return {**window, "mapped_window_sha256": digest}


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
    if not isinstance(record, dict) or "profile" not in record:
        raise ValueError("native target identity schema")
    profile = record.get("profile")
    if type(profile) is not int or profile not in (1, 2):
        raise ValueError("native target identity fields or unsupported claim")
    fields = FIELDS if profile == 1 else WINDOW_PROFILE_FIELDS
    if set(record) not in (fields, fields | {"mapped_prefix_sha256"}):
        raise ValueError("native target identity schema")
    if (
        record["verification"] != (
            "resolved-system-image-prefix" if profile == 1
            else "resolved-system-image-code-windows"
        )
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
    result = {**record, "mapped_prefix_sha256": digest}
    if profile == 2:
        if (
            type(record["cold_call_offset"]) is not int
            or record["cold_call_offset"] != 72
            or record["complete_control_flow_verified"] is not False
            or not isinstance(record["code_windows"], list)
            or len(record["code_windows"]) != 2
        ):
            raise ValueError("native target code windows or unsupported claim")
        entry = validate_code_window(record["code_windows"][0], "entry", "pthread_self", 256)
        cold = validate_code_window(record["code_windows"][1], "cold-error", "pthread_self.cold.1", 128)
        if (entry["entry_offset"] != record["entry_offset"]
                or entry["prefix_hex"][:128] != record["prefix_hex"]
                or not (entry["entry_offset"] + 256 <= cold["entry_offset"]
                        or cold["entry_offset"] + 128 <= entry["entry_offset"])):
            raise ValueError("native target code window linkage or overlap")
        # Check only the recorded direct edge, not the windows' complete CFG.
        # In particular, the cold window's calls, memory reads and traps grant
        # no normal-return, termination, separation or semantic-effect proof.
        word = int.from_bytes(bytes.fromhex(entry["prefix_hex"])[72:76], "little")
        immediate = word & ((1 << 26) - 1)
        if immediate & (1 << 25):
            immediate -= 1 << 26
        target = entry["entry_offset"] + 72 + immediate * 4
        if (word & 0xFC000000 != 0x94000000 or not 0 <= target <= (1 << 64) - 128
                or target != cold["entry_offset"]):
            raise ValueError("native target code window direct edge")
        result["code_windows"] = [entry, cold]
    return result
