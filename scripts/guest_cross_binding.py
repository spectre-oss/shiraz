# SPDX-License-Identifier: GPL-3.0-only
"""Independent, bounded verification of one recorded cross-ELF function edge.

The caller supplies metadata parsed from explicitly selected, hash-checked files.
This module neither opens a report-selected path nor resolves a namespace itself.
A verified edge only permits body traversal; it is not a callee-effect summary.
"""

import hashlib
import struct
import weakref

MAX_SYMBOLS = 2 << 20
MAX_ELF_BYTES = 256 << 20
VERSYM = 0x6FFFFFF0
_digests = weakref.WeakKeyDictionary()


def _provider_digest(metadata):
    data = metadata.data
    if type(data) is bytes:
        try:
            cached = _digests.get(metadata)
        except TypeError:
            cached = None
        if cached is not None and cached[0] is data:
            return cached[1]
    digest = hashlib.sha256(data).hexdigest()
    if type(data) is bytes:
        try:
            _digests[metadata] = (data, digest)
        except TypeError:
            pass
    return digest


def _number(value):
    if isinstance(value, str) and len(value) <= 18 and value.startswith("0x"):
        value = int(value, 16)
    if type(value) is not int or not 0 <= value < 1 << 64:
        raise ValueError("invalid cross-binding target")
    return value


def _symbol(metadata, index):
    if (
        type(metadata.symbol_count) is not int
        or not 0 < metadata.symbol_count <= MAX_SYMBOLS
        or type(index) is not int
        or not 0 <= index < metadata.symbol_count
    ):
        raise ValueError("cross-binding symbol count/index bound")
    return struct.unpack(
        "<IBBHQQ", metadata.read(metadata.tags[6] + index * 24, 24)
    )


def _version_index(metadata, index):
    if VERSYM not in metadata.tags:
        return 1, False
    if not metadata.tags[VERSYM]:
        raise ValueError("zero cross-binding VERSYM table")
    value = struct.unpack("<H", metadata.read(metadata.tags[VERSYM] + index * 2, 2))[0]
    return value & 0x7FFF, bool(value & 0x8000)


def _matches_version(metadata, index, requested):
    version, hidden = _version_index(metadata, index)
    if version == 0:
        return False
    if requested:
        # Do not guess bionic's unversioned-provider or index-one fallback.
        return version > 1 and metadata.defined_version(version) == requested
    if hidden:
        return False
    return version == 1 or metadata.defined_version(version) is not None


def verify_guest_target(importer_metadata, slot, provider_metadata):
    """Return one independently verified ELF-relative edge, or a refusal.

    Metadata must implement the bounded parser interface used by
    audit_guest_tpidr.ElfBindingMetadata. The ordinary importer resolver first
    checks relocation ownership and source symbol/version agreement with the
    actual NativeBridge report. Only its external/preempted refusal is extended.
    Inconsistent evidence raises ValueError; unsupported or ambiguous edges
    retain target=None. No native provider address can become a guest target.
    """
    initial = importer_metadata.resolve(slot)
    if initial.get("reason") != "external-or-preempted-namespace-binding":
        return initial

    def refused(reason):
        return {**initial, "target": None, "reason": reason}

    index, addend, kind = importer_metadata.relocations[slot]
    _, info, visibility, section, _, _ = _symbol(importer_metadata, index)
    proof = importer_metadata.proof[slot]
    if (
        type(proof.get("addend")) is not int
        or proof["addend"] != addend
        or type(proof.get("relocation_type")) is not int
        or proof["relocation_type"] != kind
    ):
        raise ValueError("cross-binding relocation evidence mismatch")
    if kind not in {1025, 1026} or addend != 0:
        return refused("cross-binding-relocation-or-addend-unsupported")
    if (
        info & 15 != 2
        or info >> 4 not in {1, 2}
        or visibility & 3
        or proof.get("kind") != "function"
        or proof.get("abi") != "guest-AAPCS64"
        or proof.get("target") is None
        or proof.get("provider_elf_sha256") is None
        or proof.get("nonreturning", False)
    ):
        return refused("cross-binding-not-exported-guest-function")
    if proof["provider"] != provider_metadata.name:
        raise ValueError("cross-binding provider name mismatch")
    if not 0 < len(provider_metadata.data) <= MAX_ELF_BYTES:
        raise ValueError("cross-binding provider ELF byte bound")
    digest = _provider_digest(provider_metadata)
    if proof["provider_elf_sha256"] != digest:
        raise ValueError("cross-binding provider ELF hash mismatch")
    target = _number(proof["target"])
    if target & 3:
        raise ValueError("unaligned cross-binding function target")
    requested = proof["version"]
    version, _ = _version_index(importer_metadata, index)
    if requested and version > 1 and not 0 < section < 0xFF00:
        requirement = importer_metadata.needed_version(version)
        if requirement is None:
            return refused("cross-binding-required-version-unsupported")
        if requirement != (requested, provider_metadata.name):
            return refused("cross-binding-required-provider-mismatch")

    candidates = []
    if (
        type(provider_metadata.symbol_count) is not int
        or not 0 < provider_metadata.symbol_count <= MAX_SYMBOLS
    ):
        raise ValueError("cross-binding symbol count/index bound")
    for ordinal in range(provider_metadata.symbol_count):
        name, symbol_info, other, symbol_section, value, _ = _symbol(
            provider_metadata, ordinal
        )
        if (
            symbol_info & 15 != 2
            or symbol_info >> 4 not in {1, 2}
            or other & 3 not in {0, 3}
            or not 0 < symbol_section < 0xFF00
            or provider_metadata.string(name) != proof["symbol"]
            or not _matches_version(provider_metadata, ordinal, requested)
        ):
            continue
        if symbol_section >= len(provider_metadata.sections):
            raise ValueError("cross-binding function section index bound")
        executable = provider_metadata.sections[symbol_section]
        if (
            executable[1] != 1
            or not executable[2] & 4
            or not executable[3] <= value
            or value + 4 > executable[3] + executable[5]
        ):
            continue
        if value & 3:
            raise ValueError("unaligned cross-binding definition")
        provider_metadata.offset(value, 4, executable=True)
        candidates.append(value)
        if len(candidates) == 2:
            return refused("cross-binding-definition-missing-or-ambiguous")
    if len(candidates) != 1:
        return refused("cross-binding-definition-missing-or-ambiguous")
    if candidates[0] != target:
        raise ValueError("cross-binding target disagrees with exported definition")
    return {
        **initial,
        "target": target,
        "provider": provider_metadata.name,
        "elf_sha256": digest,
        "version": requested,
        "reason": "cross-module-production-binding",
    }
