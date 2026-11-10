# SPDX-License-Identifier: GPL-3.0-only
"""Exact compiled default MRS cache-access profile, not a TLS-use certificate.

The caller supplies executable words and independent ownership of all five
readable, non-executable tables. Normal in-body paths to the sole RET contain no
calls, TLV access or errno stores. The prologue DOES write16 bytes to the stack;
this is not pre-write safety, entry lifetime, fork or asynchronous validity proof.
Cold BLs are recorded, not authenticated or certified nonreturning. Their external
handlers remain outside this profile's memory/control-flow guarantees.
"""

import hashlib
from dataclasses import dataclass

ROWS = 4096
TABLE_BYTES = ROWS * 8
BODY_WORDS = 55
BODY_BYTES = BODY_WORDS * 4
UINT64_END = 1 << 64


@dataclass(frozen=True)
class MrsAccessTable:
    role: str
    address: int
    byte_count: int = TABLE_BYTES


@dataclass(frozen=True)
class MrsAccessEffects:
    entry: int
    body_size: int
    body_sha256: str
    tables: tuple[MrsAccessTable, ...]
    ret_pc: int
    failure_calls: tuple[tuple[int, int], ...]
    rows: int = ROWS
    original_sp_register: str = "x7"
    native_register_read: str = "TPIDRRO_EL0"
    normal_path_calls: int = 0
    stack_write_bytes: int = 16
    stack_write_offset: int = -16
    stack_read_bytes: int = 16
    stack_read_offset: int = -16
    stack_bytes_before_original_sp: int = 4096
    stack_bytes_after_original_sp: int = 256
    normal_path_clobbers: tuple[str, ...] = ("x0", *(f"x{i}" for i in range(8, 18)))
    normal_path_preserves_sp: bool = True
    normal_path_preserves_lr: bool = True
    normal_path_preserves_unlisted_gprs: bool = True
    normal_path_preserves_vectors: bool = True
    normal_path_preserves_fpcr: bool = True
    normal_path_preserves_fpsr: bool = True
    normal_path_preserves_nzcv: bool = False
    normal_path_writes_outside_stack_frame: bool = False
    failure_nonreturning_verified: bool = False


def recognize_mrs_access(read_word, entry, owned_readable_data):
    """Recognize one full observed compiler layout, otherwise return None.

    Exact fixed branches keep the normal RET separate from four final cold BLs.
    The bounded scan visits offsets0,8,...,32760 only. A nonzero native key,
    non-sentinel owner, matching key and acquire owner/key rechecks precede the
    unsigned/aligned SP/window checks and acquire nonnull guest-base load.
    LDAR/LDAPR variants are admitted only at the four exact acquire sites; no
    broader concurrency equivalence or table-content/lifetime claim follows.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > UINT64_END - BODY_BYTES:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    fixed = {
        0: 0xA9BF7BFD,  # STP x29,x30,[SP,#-16]!
        1: 0x910003FD,  # MOV x29,SP
        2: 0xD53BD069,  # MRS x9,TPIDRRO_EL0
        3: 0xB4000629,  # CBZ x9,entry+208
        4: 0xD2800008,  # MOV x8,#0
        13: 0x14000004,  # B entry+68
        14: 0x91002108,  # ADD x8,x8,#8
        15: 0xF140211F,  # CMP x8,#32768
        16: 0x54000460,  # B.EQ entry+204
        17: 0x8B08014E,  # ADD x14,x10,x8
        19: 0xF1000A1F,  # CMP x16,#2
        20: 0x54FFFF43,  # B.LO entry+56
        21: 0xF868696E,  # LDR x14,[x11,x8]
        22: 0xEB0901DF,  # CMP x14,x9
        23: 0x54FFFEE1,  # B.NE entry+56
        24: 0xF868698F,  # LDR x15,[x12,x8]
        25: 0xF86869AE,  # LDR x14,[x13,x8]
        26: 0x8B080151,  # ADD x17,x10,x8
        28: 0xEB10023F,  # CMP x17,x16
        29: 0x54FFFE21,  # B.NE entry+56
        30: 0x8B080170,  # ADD x16,x11,x8
        32: 0xEB09021F,  # CMP x16,x9
        33: 0x54FFFDA1,  # B.NE entry+56
        34: 0xF2400CFF,  # TST x7,#15
        35: 0x54000201,  # B.NE entry+204
        36: 0xEB0F00E9,  # SUBS x9,x7,x15
        37: 0x540001C3,  # B.LO entry+204
        38: 0xEB0701CA,  # SUBS x10,x14,x7
        39: 0x54000189,  # B.LS entry+204
        40: 0xF140053F,  # CMP x9,#4096
        41: 0x54000183,  # B.LO entry+212
        42: 0xF103FD5F,  # CMP x10,#255
        43: 0x54000149,  # B.LS entry+212
        46: 0x8B080128,  # ADD x8,x9,x8
        48: 0xB40000C0,  # CBZ x0,entry+216
        49: 0xA8C17BFD,  # LDP x29,x30,[SP],#16
        50: 0xD65F03C0,  # RET x30
    }
    if any(words[index] != word for index, word in fixed.items()):
        return None
    for index, base, target in ((18, 14, 16), (27, 17, 17), (31, 16, 16), (47, 8, 0)):
        if words[index] not in (0xF8BFC000 | base << 5 | target,
                                0xC8DFFC00 | base << 5 | target):
            return None
    failures = []
    for index in range(51, 55):
        word = words[index]
        if word & 0xFC000000 != 0x94000000:
            return None
        displacement = word & 0x3FFFFFF
        if displacement & 1 << 25:
            displacement -= 1 << 26
        target = entry + index * 4 + displacement * 4
        if not 0 <= target < UINT64_END or entry <= target < entry + BODY_BYTES:
            return None
        failures.append((entry + index * 4, target))
    tables = []
    spans = [(entry, entry + BODY_BYTES)]
    for index, register, role in ((5, 10, "owners"), (7, 11, "keys"),
                                  (9, 12, "low"), (11, 13, "high"), (44, 9, "bases")):
        adrp, add = words[index:index + 2]
        if adrp & 0x9F00001F != 0x90000000 | register:
            return None
        if add & 0xFFC003FF != 0x91000000 | register << 5 | register:
            return None
        displacement = ((adrp >> 5 & 0x7FFFF) << 2) | (adrp >> 29 & 3)
        if displacement & 1 << 20:
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
        tables.append(MrsAccessTable(role, address))
    return MrsAccessEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        tuple(tables), entry + 200, tuple(failures),
    )
