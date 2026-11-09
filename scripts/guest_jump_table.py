# SPDX-License-Identifier: GPL-3.0-only
"""Exact byte-derived recognition of bounded AArch64 byte switch sequences.

Recognition is not permission to skip memory or control-flow checks. The caller
must provide executable file words and immutable, file-backed, non-relocated
read-only table bytes, then validate target execution and retain its ordinary
TP, stack-alias, lifetime and memory-separation checks. Entering after the initial CMP or bit extraction does not acquire
these effects.
"""

import hashlib
from dataclasses import dataclass

U64_LIMIT = 1 << 64
MAX_TABLE_BYTES = 64
BODY_WORDS = 9


@dataclass(frozen=True)
class ByteJumpTableEffects:
    entry: int
    body_size: int
    body_sha256: str
    words: tuple[int, ...]
    default_target: int | None
    table_address: int
    branch_base: int
    limit: int
    index_source: str
    index_register: str
    table_register: str
    offset_register: str
    branch_register: str
    table_bytes: tuple[int, ...]
    table_sha256: str
    # Per table entry, retaining the byte/target relationship and duplicates.
    targets: tuple[int, ...]
    # This copy executes before the conditional branch in the second form.
    prebranch_move: tuple[str, str] | None = None
    # CMP Wn constrains only low bits; UXTX addressing needs an independent
    # path fact that Xn's upper 32 bits were zeroed by an executed W write.
    requires_zero_extended_index: bool = False
    index_extract: tuple[int, int] | None = None
    interleaved_and: tuple[str, str, int] | None = None


def _signed(value, bits):
    return value - (1 << bits) if value & (1 << (bits - 1)) else value


def _address(value):
    return type(value) is int and 0 <= value < U64_LIMIT


def _adr(word, pc, page):
    immediate = _signed(((word >> 5) & 0x7FFFF) << 2 | ((word >> 29) & 3), 21)
    return (pc & ~0xFFF) + (immediate << 12) if page else pc + immediate


def and32_immediate(word):
    """Decode only unshifted-width AND Wd,Wn,#logical-immediate, no SP/ZR."""
    if type(word) is not int or not 0 <= word <= 0xFFFFFFFF or word & 0xFFC00000 != 0x12000000:
        return None
    destination, source = word & 31, (word >> 5) & 31
    if destination > 28 or source > 28:
        return None
    immr, imms = (word >> 16) & 63, (word >> 10) & 63
    length = ((~imms) & 63).bit_length() - 1
    if not 1 <= length <= 5:
        return None
    width = 1 << length
    levels = width - 1
    ones, rotate = (imms & levels) + 1, immr & levels
    if ones == width:
        return None
    mask = (1 << ones) - 1
    mask = ((mask >> rotate) | (mask << (width - rotate))) & ((1 << width) - 1)
    mask = sum(mask << shift for shift in range(0, 32, width))
    return f"w{destination}", f"w{source}", mask


def _extract_table(read_word, entry, readonly_bytes):
    # UBFM Windex,Wsource,#lsb,#end is UBFX (or its LSR alias).
    words = tuple(read_word(entry + index * 4) for index in range(8))
    if any(type(word) is not int or not 0 <= word <= 0xFFFFFFFF for word in words):
        return None
    extract, page, add_table, mask_word, adr, load, add_branch, branch = words
    if extract & 0xFFC00000 != 0x53000000:
        return None
    index, source = extract & 31, (extract >> 5) & 31
    lsb, end = (extract >> 16) & 63, (extract >> 10) & 63
    if source > 28 or not 0 <= lsb <= end < 32 or end - lsb >= 6:
        return None
    limit = (1 << (end - lsb + 1)) - 1
    if page & 0x9F000000 != 0x90000000:
        return None
    table = page & 31
    if (add_table & 0xFFC00000 != 0x91000000 or add_table & 31 != table or
            (add_table >> 5) & 31 != table):
        return None
    table_address = _adr(page, entry + 4, True) + ((add_table >> 10) & 0xFFF)
    if not _address(table_address) or table_address + limit + 1 > U64_LIMIT:
        return None
    if adr & 0x9F000000 != 0x10000000:
        return None
    destination = adr & 31
    branch_base = _adr(adr, entry + 16, False)
    if not _address(branch_base) or branch_base & 3:
        return None
    if (load & 0xFFE0FC00 != 0x38606800 or
            (load >> 16) & 31 != index or (load >> 5) & 31 != table):
        return None
    offset = load & 31
    roles = {index, table, offset, destination}
    mask = and32_immediate(mask_word)
    if (len(roles) != 4 or any(register > 28 for register in roles) or mask is None or
            int(mask[0][1:]) in roles or int(mask[1][1:]) in roles):
        return None
    if (add_branch & 0xFFE0FC00 != 0x8B000800 or add_branch & 31 != destination or
            (add_branch >> 5) & 31 != destination or (add_branch >> 16) & 31 != offset or
            branch & 0xFFFFFC1F != 0xD61F0000 or (branch >> 5) & 31 != destination):
        return None
    data = readonly_bytes(table_address, limit + 1)
    if type(data) is not bytes or len(data) != limit + 1:
        return None
    targets = tuple(branch_base + byte * 4 for byte in data)
    if any(not _address(target) or target & 3 for target in targets):
        return None
    body = b"".join(word.to_bytes(4, "little") for word in words)
    return ByteJumpTableEffects(
        entry, len(body), hashlib.sha256(body).hexdigest(), words, None,
        table_address, branch_base, limit, f"w{source}", f"x{index}",
        f"x{table}", f"w{offset}", f"x{destination}", tuple(data),
        hashlib.sha256(data).hexdigest(), targets,
        index_extract=(lsb, end - lsb + 1), interleaved_and=mask,
    )


def recognize_byte_jump_table(read_word, entry, readonly_bytes):
    """Recognize a checked compare or extract byte-table sequence, or None.

    The first form is CMP/B.HI/MOV-W/ADRP/ADD/ADR/LDRB/ADD/BR. The second
    is CMP/MOV-X/B.HI followed by the same six instructions, using the CMP
    register directly as its X index. The caller must separately prove that
    index zero-extended and apply the MOV-X to both branch outcomes.

    read_word(pc) returns an exact executable uint32 word or None. The trusted
    readonly_bytes(address, count) returns exactly count immutable bytes after
    independent permissions, file-backing and relocation checks, or None.
    The third form is eight words: bounded W bit extraction, ADRP/ADD,
    an independent W AND-immediate, ADR/LDRB/ADD/BR. It has no default branch
    and preserves flags. No optional instructions or authenticated branches
    are admitted.
    """
    if not _address(entry) or entry & 3 or entry + 4 > U64_LIMIT:
        return None
    first = read_word(entry)
    if type(first) is int and first & 0xFFC00000 == 0x53000000:
        return _extract_table(read_word, entry, readonly_bytes) if entry + 32 <= U64_LIMIT else None
    if entry + BODY_WORDS * 4 > U64_LIMIT:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word <= 0xFFFFFFFF for word in words):
        return None
    compare, second, third, page, add_table, adr, load, add_branch, branch = words
    # SUBS WZR, Wn, #imm12 (CMP), with no shifted immediate.
    if compare & 0xFFC0001F != 0x7100001F:
        return None
    source = (compare >> 5) & 31
    limit = (compare >> 10) & 0xFFF
    if source > 28 or limit >= MAX_TABLE_BYTES:
        return None
    prebranch_move = None
    requires_zero_extended_index = False
    if second & 0xFF00001F == 0x54000008:
        conditional, conditional_offset, move = second, 4, third
        # ORR Windex, WZR, Wn (MOV). Its W write zero-extends the X index.
        if move & 0xFFE0FFE0 != 0x2A0003E0 or (move >> 16) & 31 != source:
            return None
        index = move & 31
    elif third & 0xFF00001F == 0x54000008:
        conditional, conditional_offset, move = third, 8, second
        # Exact unshifted ORR Xdst, XZR, Xsrc (MOV), without FP/LR/ZR.
        if move & 0xFFE0FFE0 != 0xAA0003E0:
            return None
        move_destination, move_source = move & 31, (move >> 16) & 31
        if move_destination > 28 or move_source > 28 or move_destination == source:
            return None
        prebranch_move = (f"x{move_destination}", f"x{move_source}")
        requires_zero_extended_index = True
        index = source
    else:
        return None
    # Unsigned HI, not HS/GT or a flag-independent branch.
    default = entry + conditional_offset + (_signed((conditional >> 5) & 0x7FFFF, 19) << 2)
    if not _address(default) or entry <= default < entry + BODY_WORDS * 4:
        return None
    if page & 0x9F000000 != 0x90000000:
        return None
    table = page & 31
    if (add_table & 0xFFC00000 != 0x91000000
            or add_table & 31 != table or (add_table >> 5) & 31 != table):
        return None
    table_address = _adr(page, entry + 12, True) + ((add_table >> 10) & 0xFFF)
    if not _address(table_address) or table_address + limit + 1 > U64_LIMIT:
        return None
    if adr & 0x9F000000 != 0x10000000:
        return None
    destination = adr & 31
    branch_base = _adr(adr, entry + 20, False)
    if not _address(branch_base) or branch_base & 3:
        return None
    # LDRB Woffset, [Xtable, Xindex], UXTX, unscaled, no writeback.
    if (load & 0xFFE0FC00 != 0x38606800
            or (load >> 16) & 31 != index or (load >> 5) & 31 != table):
        return None
    offset = load & 31
    roles = {index, table, offset, destination}
    if (len(roles) != 4 or any(register > 28 for register in roles)
            or not requires_zero_extended_index and source in roles):
        return None
    # ADD Xbranch, Xbranch, Xoffset, LSL #2; BR Xbranch.
    if (add_branch & 0xFFE0FC00 != 0x8B000800
            or add_branch & 31 != destination
            or (add_branch >> 5) & 31 != destination
            or (add_branch >> 16) & 31 != offset
            or branch & 0xFFFFFC1F != 0xD61F0000
            or (branch >> 5) & 31 != destination):
        return None
    data = readonly_bytes(table_address, limit + 1)
    if type(data) is not bytes or len(data) != limit + 1:
        return None
    targets = tuple(branch_base + byte * 4 for byte in data)
    if any(not _address(target) or target & 3 for target in targets):
        return None
    body = b"".join(word.to_bytes(4, "little") for word in words)
    return ByteJumpTableEffects(
        entry, BODY_WORDS * 4, hashlib.sha256(body).hexdigest(), words,
        default, table_address, branch_base, limit, f"w{source}", f"x{index}",
        f"x{table}", f"w{offset}", f"x{destination}", tuple(data),
        hashlib.sha256(data).hexdigest(), targets,
        prebranch_move, requires_zero_extended_index,
    )
