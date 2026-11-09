# SPDX-License-Identifier: GPL-3.0-only
"""Exact byte-derived effects for one bounded prepared pthread identity leaf.

No ABI name or producer schema grants recognition. The caller supplies executable
words and independent ownership of readable, non-executable table extents. It
must additionally establish prepared-entry lifetime, table/stack separation and
runtime publication semantics before applying these effects. This module does
not certify current table contents, TLS consumers, fork, asynchronous signals or
that BRK is uncatchable. Unsupported compiler layouts remain unrecognized.
"""

import hashlib
from dataclasses import dataclass

ROWS = 4096
TABLE_BYTES = ROWS * 8
BODY_WORDS = 38
BODY_BYTES = BODY_WORDS * 4
UINT64_END = 1 << 64


@dataclass(frozen=True)
class CachedSelfTable:
    role: str
    address: int
    byte_count: int = TABLE_BYTES


@dataclass(frozen=True)
class CachedSelfEffects:
    entry: int
    body_size: int
    body_sha256: str
    tables: tuple[CachedSelfTable, ...]
    ret_pc: int
    trap_pc: int
    rows: int = ROWS
    clobbers: tuple[str, ...] = ("x0", *(f"x{i}" for i in range(8, 17)))
    sp_copy_register: str = "x9"
    native_register_read: str = "TPIDRRO_EL0"
    may_write_memory: bool = False
    preserves_sp: bool = True
    preserves_lr: bool = True
    preserves_unlisted_gprs: bool = True
    preserves_vectors: bool = True
    preserves_fpcr: bool = True
    preserves_fpsr: bool = True
    preserves_nzcv: bool = False


def recognize_cached_self(read_word, entry, owned_readable_data):
    """Return effects for the complete observed leaf, otherwise None.

    The only ordinary return follows matching non-sentinel owner/key and an SP
    interval test, then acquire owner/key rechecks. Counter values at each load
    are exactly 0, 8, ..., 32760: the increment checks 32768 before another load.
    Exhaustion reaches BRK rather than returning a fabricated handle. Exact
    instruction matching includes every branch target/condition and all operand
    widths. LDAR and LDAPR scalar acquire forms have the same effects here; no
    broader concurrency equivalence or ordering proof is asserted.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > UINT64_END - BODY_BYTES:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    fixed = {
        0: 0xD2800008,  # MOV x8,#0
        1: 0x910003E9,  # MOV x9,SP
        2: 0xD53BD06A,  # MRS x10,TPIDRRO_EL0 (never TPIDR_EL0)
        11: 0x14000004,  # B entry+60
        12: 0x91002108,  # ADD x8,x8,#8
        13: 0xF140211F,  # CMP x8,#32768
        14: 0x540002E0,  # B.EQ entry+148, before next load
        15: 0x8B08016F,  # ADD x15,x11,x8
        17: 0xF100081F,  # CMP x0,#2 (null/claiming sentinel excluded)
        18: 0xFA402944,  # CCMP x10,#0,#4,HS (zero native key excluded)
        19: 0x54FFFF20,  # B.EQ entry+48
        20: 0xF868698F,  # LDR x15,[x12,x8], unshifted byte index
        21: 0xEB0A01FF,  # CMP x15,x10
        22: 0x54FFFEC1,  # B.NE entry+48
        23: 0xF86869AF,  # LDR x15,[x13,x8]
        24: 0xF86869D0,  # LDR x16,[x14,x8]
        25: 0xEB0F013F,  # CMP x9,x15
        26: 0xFA502122,  # CCMP x9,x16,#2,HS
        27: 0x54FFFE22,  # B.HS entry+48: require low<=SP<high
        28: 0x8B08016F,  # ADD x15,x11,x8
        30: 0xEB0001FF,  # CMP x15,x0
        31: 0x54FFFDA1,  # B.NE entry+48
        32: 0x8B08018F,  # ADD x15,x12,x8
        34: 0xEB0A01FF,  # CMP x15,x10
        35: 0x54FFFD21,  # B.NE entry+48
        36: 0xD65F03C0,  # RET x30
        37: 0xD4200020,  # BRK #1
    }
    if any(words[index] != word for index, word in fixed.items()):
        return None
    for index, target in ((16, 0), (29, 15), (33, 15)):
        if words[index] not in (0xF8BFC000 | 15 << 5 | target,
                                0xC8DFFC00 | 15 << 5 | target):
            return None
    tables = []
    spans = [(entry, entry + BODY_BYTES)]
    for index, register, role in ((3, 11, "owners"), (5, 12, "keys"),
                                  (7, 13, "low"), (9, 14, "high")):
        adrp, add = words[index:index + 2]
        # Reject ADR, shifted ADD, W operands, flags, different source/registers.
        if adrp & 0x9F00001F != 0x90000000 | register:
            return None
        if add & 0xFFC003FF != 0x91000000 | register << 5 | register:
            return None
        displacement = ((adrp >> 5 & 0x7FFFF) << 2) | (adrp >> 29 & 3)
        if displacement & (1 << 20):
            displacement -= 1 << 21
        address = ((entry + index * 4) & ~0xFFF) + displacement * 4096 + (add >> 10 & 0xFFF)
        if address < 0 or address & 7 or address > UINT64_END - TABLE_BYTES:
            return None
        end = address + TABLE_BYTES
        if any(address < high and low < end for low, high in spans):
            return None
        if owned_readable_data(address, TABLE_BYTES) is not True:
            return None
        spans.append((address, end))
        tables.append(CachedSelfTable(role, address))
    return CachedSelfEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        tuple(tables), entry + 144, entry + 148,
    )
