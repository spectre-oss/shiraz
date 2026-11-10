# SPDX-License-Identifier: GPL-3.0-only
"""Exact default MRS island bytes, never an execution or TLS-use certificate.

This verifies a self-consistent, separated site/island snapshot and its two
signed branches. Entry/getter code, executable mappings, prepared synchronous
lifetime, valid stack space and Image/context exclusion remain independent
requirements. Only the island's own 16-byte retired spill clear is described;
no shared/deeper-frame, pre-write, unwind, asynchronous or native-purity proof
follows from these bytes.
"""

import hashlib
import struct
from dataclasses import dataclass

ISLAND_BYTES = 64
UINT64_END = 1 << 64
UINT64_MAX = UINT64_END - 1


@dataclass(frozen=True)
class MrsIslandEffects:
    island: int
    site: int
    target: int
    replacement: int
    entry: int
    accessor: int
    continuation: int
    context: int
    return_branch_pc: int
    body_sha256: str
    body_size: int = ISLAND_BYTES
    default_kind: int = 1
    outer_stack_write_bytes: int = 16
    outer_stack_clear_bytes: int = 16
    outer_stack_offset: int = -16
    requires_verified_entry_and_accessor: bool = True
    requires_verified_code_mappings: bool = True
    requires_stack_image_context_exclusion: bool = True
    requires_prepared_synchronous_lifetime: bool = True
    execution_claim: bool = False
    consumer_proof: bool = False


def _address(value, alignment, room=0):
    return type(value) is int and 0 <= value <= UINT64_MAX - room and not value % alignment


def _branch(origin, destination):
    # Python differences must fit the architectural signed displacement before
    # encoding. Masking an out-of-range difference would forge a wrapped branch.
    displacement = destination - origin
    if displacement & 3 or not -(1 << 27) <= displacement <= (1 << 27) - 4:
        return None
    return 0x14000000 | (displacement // 4 & 0x03FFFFFF)


def validate_mrs_island(blob, *, island, site, target, replacement, entry, accessor):
    """Return immutable default-kind byte facts, or None for unsupported input.

    The site replacement and return B at island+24 are checked independently.
    XZR/no-island, Darwin-reserved x18 and generic/SVC kind0 are outside this
    profile. End-point arithmetic follows the planner's non-wrapping +4/+64
    requirements. No mapped-code or function-identity assumption is inferred
    merely from an aligned numeric entry/getter address.
    """
    if type(blob) not in (bytes, bytearray):
        return None
    blob = bytes(blob)  # Freeze mutable snapshots before bounds/unpacking.
    if (len(blob) != ISLAND_BYTES or not _address(site, 4, 4)
            or not _address(island, 16, ISLAND_BYTES)
            or type(target) is not int or not 0 <= target <= 30 or target == 18
            or type(replacement) is not int or not 0 <= replacement < 1 << 32
            or not _address(entry, 4) or entry == 0
            or not _address(accessor, 4) or accessor == 0):
        return None
    # A site inside its island would give contradictory executable snapshots.
    if site < island + ISLAND_BYTES and island < site + 4:
        return None
    outward = _branch(site, island)
    continuation = site + 4
    returning = _branch(island + 24, continuation)
    if outward is None or returning is None or replacement != outward:
        return None
    expected = struct.pack(
        "<8IQQIIQ",
        0xA9BF47F0,  # STP x16,x17,[SP,#-16]!
        0x10000130,  # ADR x16,island+40
        0x580000D1,  # LDR x17,[PC,#24] (entry pointer at island+32)
        0xD61F0220,  # BR x17
        0xA8C147F0,  # LDP x16,x17,[SP],#16
        0xA93F7FFF,  # STP xzr,xzr,[SP,#-16]
        returning,  # B site+4, from island+24
        0xD503201F,  # NOP, not the old continuation literal
        entry, island + 16, target, 1, accessor,
    )
    if blob != expected:
        return None
    return MrsIslandEffects(
        island, site, target, replacement, entry, accessor, continuation,
        island + 40, island + 24, hashlib.sha256(blob).hexdigest(),
    )
