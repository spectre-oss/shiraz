# SPDX-License-Identifier: GPL-3.0-only
"""Exact prepared-errno query instructions, not a cell validity/lifetime lease.

The caller supplies executable words and complete readable, non-executable
ownership of five inferred table spans. Matching does not authenticate actual
arrays, published values, stack exclusion or the returned cell. No dereference
of the returned pointer, native-memory purity or complete TLS consumer follows.
"""
import hashlib
from dataclasses import dataclass

ROWS = 4096
TABLE_BYTES = ROWS * 8
BODY_WORDS = 63
BODY_BYTES = BODY_WORDS * 4
UINT64_END = 1 << 64

# Independently bounded compiled 252-byte function. Only the five ADRP/ADD
# pairs are relocatable. Acquire loads admit their exact LDAR/LDAPR variants.
_WORDS = (
    0xAA0003E8, 0xD2800000, 0xD53BD069, 0xF2400D1F, 0x54000741, 0xB4000728,
    0xB4000709, 0xD280000A, 0, 0, 0, 0,
    0x14000004, 0x9100214A, 0xF140215F, 0x540005C0, 0x8B0A018D, 0xF8BFC1AD,
    0xF10009BF, 0x54FFFF43, 0xF86A696E, 0xEB0901DF, 0x54FFFEE1, 0,
    0, 0xF86A69CF, 0, 0, 0xF86A69D0, 0xEB10011F,
    0xFA4F3100, 0xFA5021E2, 0x540003A2, 0, 0, 0x8B0A01C8,
    0xF8BFC108, 0xB4000308, 0xD2800000, 0xB100151F, 0x540002C8, 0x92400511,
    0xB5000291, 0xEB10011F, 0x54000082, 0x91001110, 0xEB1001FF, 0x540001C3,
    0x8B0A018C, 0xF8BFC18C, 0xEB0D019F, 0x54000141, 0x8B0A016B, 0xF8BFC16B,
    0xEB09017F, 0x540000C1, 0x8B0A01C9, 0xF8BFC129, 0xEB08013F, 0x9A9F0100,
    0xD65F03C0, 0xD2800000, 0xD65F03C0,
)
_TABLE_PAIRS = ((8, 12, 'owners'), (10, 11, 'keys'), (23, 14, 'low'),
                (26, 14, 'high'), (33, 14, 'errno-cells'))
_ACQUIRES = ((17, 13, 13), (36, 8, 8), (49, 12, 12), (53, 11, 11), (57, 9, 9))
_DYNAMIC = frozenset(index + delta for index, _, _ in _TABLE_PAIRS for delta in (0, 1))
_ACQUIRE_INDICES = frozenset(index for index, _, _ in _ACQUIRES)


@dataclass(frozen=True)
class PreparedErrnoTable:
    role: str
    address: int
    byte_count: int = TABLE_BYTES


@dataclass(frozen=True)
class PreparedErrnoEffects:
    entry: int
    body_size: int
    body_sha256: str
    tables: tuple[PreparedErrnoTable, ...]
    ret_pcs: tuple[int, int]
    rows: int = ROWS
    incoming_sp_register: str = 'x0'
    native_register_read: str = 'TPIDRRO_EL0'
    normal_path_calls: int = 0
    stack_read_bytes: int = 0
    stack_write_bytes: int = 0
    normal_path_clobbers: tuple[str, ...] = ('x0', *(f'x{i}' for i in range(8, 18)))
    normal_path_preserves_sp: bool = True
    normal_path_preserves_lr: bool = True
    normal_path_preserves_unlisted_gprs: bool = True
    normal_path_preserves_vectors: bool = True
    normal_path_preserves_fpcr: bool = True
    normal_path_preserves_fpsr: bool = True
    normal_path_preserves_nzcv: bool = False
    normal_path_writes_memory: bool = False
    pointer_result: bool = True
    returned_cell_read_bytes: int = 0
    returned_cell_write_bytes: int = 0
    nonnull_result_cell_alignment: int = 4
    nonnull_result_cell_bytes: int = 4
    nonnull_result_disjoint_row_interval: bool = True
    return_scratch_may_contain_table_values: bool = True
    requires_matching_runtime_tables: bool = True
    requires_metadata_row_contract: bool = True
    table_identity_verified: bool = False
    metadata_publication_verified: bool = False
    table_stack_exclusion_verified: bool = False
    caller_inputs_verified: bool = False
    returned_cell_readability_verified: bool = False
    returned_cell_writability_verified: bool = False
    lifetime_verified: bool = False
    async_verified: bool = False
    unwind_verified: bool = False
    native_memory_purity_verified: bool = False
    consumer_proof: bool = False


def recognize_prepared_errno(read_word, entry, owned_readable_data):
    """Match this complete leaf and its five disjoint readable table spans.

    The loop visits offsets 0,8,...,32760. A nonzero aligned incoming SP and
    opaque native key select a nonsentinel owner/key row with low<=SP<high and
    low<high. Its nonzero 4-aligned cell address has a representable four-byte
    extent disjoint from that row interval; owner/key/cell acquire rechecks
    precede a cell-or-null result. Loaded addresses are never dereferenced.
    Actual table identities, row contents and cell ownership remain unverified.
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
        tables.append(PreparedErrnoTable(role, address))
    return PreparedErrnoEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b''.join(word.to_bytes(4, 'little') for word in words)).hexdigest(),
        tuple(tables), (entry + 240, entry + 248),
    )
