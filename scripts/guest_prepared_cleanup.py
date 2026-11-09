# SPDX-License-Identifier: GPL-3.0-only
"""Exact bounded prepared-cleanup query instructions, not a lifetime lease.

The caller supplies independently executable words and complete readable,
non-executable ownership of five table spans. Roles below are inferred from
instruction use, not authenticated runtime array identities. A matching body
can still read guest-related values if its addresses name the wrong storage.
Publication, row contents, table/stack exclusion, caller facts and lifetime
remain independent requirements. No native-body purity or TLS exemption follows.
"""

import hashlib
from dataclasses import dataclass

ROWS = 4096
TABLE_BYTES = ROWS * 8
BODY_WORDS = 54
BODY_BYTES = BODY_WORDS * 4
UINT64_END = 1 << 64

# LC_FUNCTION_STARTS and the next text symbol bound the observed function to
# 216 bytes. Zero placeholders are only the five relocatable ADRP/ADD pairs.
_WORDS = (
    0x52800008, 0xD53BD069, 0xF2400C1F, 0x54000621, 0xB4000600, 0xB40005E9,
    0xD2800008, 0, 0, 0, 0, 0x14000004,
    0x91002108, 0xF140211F, 0x540004A0, 0x8B08016C, 0xF8BFC18C, 0xF100099F,
    0x54FFFF43, 0xF868694D, 0xEB0901BF, 0x54FFFEE1, 0, 0,
    0xF86869AD, 0, 0, 0xF86869CE, 0xEB0E001F, 0xFA4D3000,
    0xFA4E21A2, 0x54000282, 0, 0, 0x8B0801AE, 0xF8BFC1CE,
    0xF10005DF, 0x540001C1, 0x8B08016B, 0xF8BFC16B, 0xEB0C017F, 0x54000141,
    0x8B08014A, 0xF8BFC14A, 0xEB09015F, 0x540000C1, 0x8B0801A8, 0xF8BFC108,
    0xF100051F, 0x1A9F17E0, 0xD65F03C0, 0x52800008, 0xAA0803E0, 0xD65F03C0,
)
_TABLE_PAIRS = ((7, 11, "owners"), (9, 10, "keys"), (22, 13, "low"),
                (25, 14, "high"), (32, 13, "admission"))
_ACQUIRES = ((16, 12, 12), (35, 14, 14), (39, 11, 11), (43, 10, 10), (47, 8, 8))
_DYNAMIC = frozenset(index + delta for index, _, _ in _TABLE_PAIRS for delta in (0, 1))
_ACQUIRE_INDICES = frozenset(index for index, _, _ in _ACQUIRES)


@dataclass(frozen=True)
class PreparedCleanupTable:
    role: str
    address: int
    byte_count: int = TABLE_BYTES


@dataclass(frozen=True)
class PreparedCleanupEffects:
    entry: int
    body_size: int
    body_sha256: str
    tables: tuple[PreparedCleanupTable, ...]
    ret_pcs: tuple[int, int]
    rows: int = ROWS
    incoming_sp_register: str = "x0"
    native_register_read: str = "TPIDRRO_EL0"
    normal_path_calls: int = 0
    stack_read_bytes: int = 0
    stack_write_bytes: int = 0
    normal_path_clobbers: tuple[str, ...] = ("x0", *(f"x{i}" for i in range(8, 15)))
    normal_path_preserves_sp: bool = True
    normal_path_preserves_lr: bool = True
    normal_path_preserves_unlisted_gprs: bool = True
    normal_path_preserves_vectors: bool = True
    normal_path_preserves_fpcr: bool = True
    normal_path_preserves_fpsr: bool = True
    normal_path_preserves_nzcv: bool = False
    normal_path_writes_memory: bool = False
    boolean_result: bool = True
    return_scratch_may_contain_table_values: bool = True
    requires_matching_runtime_tables: bool = True
    requires_metadata_row_contract: bool = True
    table_identity_verified: bool = False
    metadata_publication_verified: bool = False
    table_stack_exclusion_verified: bool = False
    caller_inputs_verified: bool = False
    lifetime_verified: bool = False
    async_verified: bool = False
    unwind_verified: bool = False
    native_memory_purity_verified: bool = False
    consumer_proof: bool = False


def recognize_prepared_cleanup(read_word, entry, owned_readable_data):
    """Match only this complete leaf layout and five disjoint owned table spans.

    The scan visits offsets 0,8,...,32760. An aligned nonzero SP, opaque nonzero
    native key, nonsentinel owner and matching key precede low <= SP < high
    (with low < high), admission==1 and acquire owner/key/admission rechecks.
    Both returns produce a boolean. LDAR/LDAPR are admitted only at the five
    exact acquire sites. Loaded values are never used as memory addresses, but
    remain in scratch registers; table identities and contents are unverified.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > UINT64_END - BODY_BYTES:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    if any(word != _WORDS[index] for index, word in enumerate(words)
           if index not in _DYNAMIC and index not in _ACQUIRE_INDICES):
        return None
    for index, base, target in _ACQUIRES:
        if words[index] not in (0xF8BFC000 | base << 5 | target,
                                0xC8DFFC00 | base << 5 | target):
            return None
    tables = []
    spans = [(entry, entry + BODY_BYTES)]
    for index, register, role in _TABLE_PAIRS:
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
        tables.append(PreparedCleanupTable(role, address))
    return PreparedCleanupEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        tuple(tables), (entry + 200, entry + 212),
    )
