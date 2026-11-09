# SPDX-License-Identifier: GPL-3.0-only
"""Exact shared MRS entry profile, conditional on the validated default context.

The caller supplies independently executable words and the exact, separately
validated accessor entry. Matching proves neither that accessor body nor context,
cache/table ownership, stack exclusion, pre-write safety, lifetime, asynchronous
behavior, unwind metadata or complete TLS consumers. Generic-kind calls remain
unverified. Guest x16/x17 restoration and the outer16-byte clear require the
independently validated return island; this body clears only its832-byte frame.
"""

import hashlib
from dataclasses import dataclass

BODY_WORDS = 220
BODY_BYTES = BODY_WORDS * 4
ACCESSOR_CALL_INDEX = 115
UINT64_END = 1 << 64

# Complete observed first-party ASM layout. Only the BL target is relocatable;
# every other save/quarantine/branch/status/restore/clear instruction is exact.
_WORDS = (
    0xD10D03FF, 0xA90007E0, 0xA9010FE2, 0xA90217E4, 0xA9031FE6, 0xA90427E8,
    0xA9052FEA, 0xA90637EC, 0xA9073FEE, 0xA9094FF2, 0xA90A57F4, 0xA90B5FF6,
    0xA90C67F8, 0xA90D6FFA, 0xA90E77FC, 0xF9007BFE, 0xF9018BF0, 0x910D03E9,
    0xA9402929, 0xA9082BE9, 0xD53B4209, 0xF9007FE9, 0xD53B4409, 0xF90083E9,
    0xD53B4429, 0xF90087E9, 0xAD0887E0, 0xAD098FE2, 0xAD0A97E4, 0xAD0B9FE6,
    0xAD0CA7E8, 0xAD0DAFEA, 0xAD0EB7EC, 0xAD0FBFEE, 0xAD10C7F0, 0xAD11CFF2,
    0xAD12D7F4, 0xAD13DFF6, 0xAD14E7F8, 0xAD15EFFA, 0xAD16F7FC, 0xAD17FFFE,
    0xB85F0209, 0x93406529, 0xD37EF529, 0xD100420A, 0x8B090149, 0xF9019BE9,
    0x910C83E9, 0xA900793D, 0x910C83FD, 0xF9418BF0, 0xB9400E09, 0x7100053F,
    0x540007E1, 0xAA1F03E0, 0xAA1F03E1, 0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4,
    0xAA1F03E5, 0xAA1F03E6, 0x910D43E7, 0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA,
    0xAA1F03EB, 0xAA1F03EC, 0xAA1F03ED, 0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0,
    0xAA1F03F1, 0xAA1F03F3, 0xAA1F03F4, 0xAA1F03F5, 0xAA1F03F6, 0xAA1F03F7,
    0xAA1F03F8, 0xAA1F03F9, 0xAA1F03FA, 0xAA1F03FB, 0xAA1F03FC, 0x6E201C00,
    0x6E211C21, 0x6E221C42, 0x6E231C63, 0x6E241C84, 0x6E251CA5, 0x6E261CC6,
    0x6E271CE7, 0x6E281D08, 0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B, 0x6E2C1D8C,
    0x6E2D1DAD, 0x6E2E1DCE, 0x6E2F1DEF, 0x6E301E10, 0x6E311E31, 0x6E321E52,
    0x6E331E73, 0x6E341E94, 0x6E351EB5, 0x6E361ED6, 0x6E371EF7, 0x6E381F18,
    0x6E391F39, 0x6E3A1F5A, 0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD, 0x6E3E1FDE,
    0x6E3F1FFF, 0x94000000, 0x14000005, 0xF9400A10, 0xAA0803E6, 0x910D43E7,
    0xD63F0200, 0xF9418BF0, 0xB9400A11, 0xF8317BE0, 0xA9482BE9, 0x910D03EB,
    0xA9002969, 0xF94083E9, 0xD51B4409, 0xF94087E9, 0xD51B4429, 0xF9407FE9,
    0xD51B4209, 0xAD4887E0, 0xAD498FE2, 0xAD4A97E4, 0xAD4B9FE6, 0xAD4CA7E8,
    0xAD4DAFEA, 0xAD4EB7EC, 0xAD4FBFEE, 0xAD50C7F0, 0xAD51CFF2, 0xAD52D7F4,
    0xAD53DFF6, 0xAD54E7F8, 0xAD55EFFA, 0xAD56F7FC, 0xAD57FFFE, 0xF9418BF0,
    0xF9400210, 0xA94007E0, 0xA9410FE2, 0xA94217E4, 0xA9431FE6, 0xA94427E8,
    0xA9452FEA, 0xA94637EC, 0xA9473FEE, 0xA9494FF2, 0xA94A57F4, 0xA94B5FF6,
    0xA94C67F8, 0xA94D6FFA, 0xA94E77FC, 0xF9407BFE, 0xF9419BF1, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xD61F0200,
)


@dataclass(frozen=True)
class MrsEntryEffects:
    entry: int
    body_size: int
    body_sha256: str
    accessor_target: int
    accessor_call_pc: int
    generic_call_pc: int
    exit_branch_pc: int
    default_kind: int = 1
    original_sp_register: str = "x7"
    original_sp_from_shared_sp: int = 848
    native_fp_register: str = "x29"
    native_fp_from_shared_sp: int = 800
    quarantined_gprs: tuple[str, ...] = (
        *(f"x{i}" for i in range(7)), *(f"x{i}" for i in range(8, 18)),
        *(f"x{i}" for i in range(19, 29)),
    )
    quarantined_vectors: tuple[str, ...] = tuple(f"v{i}" for i in range(32))
    native_x18_preserved: bool = True
    shared_stack_bytes: int = 832
    island_stack_bytes: int = 16
    shared_clear_stores: int = 52
    shared_clear_interval_from_original_sp: tuple[int, int] = (-848, -16)
    shared_frame_cleared_on_normal_default_return: bool = True
    shared_exit_preserves_incoming_sp: bool = True
    guest_pc_carrier: str = "x17"
    guest_pc_saved_offset: int = 816
    context_saved_offset: int = 784
    default_getter_calls: int = 1
    requires_validated_default_context: bool = True
    requires_validated_return_island: bool = True
    requires_island_restore_x16_x17: bool = True
    accessor_body_verified: bool = False
    generic_path_effects_verified: bool = False
    context_ownership_verified: bool = False
    cache_ownership_verified: bool = False
    stack_exclusion_verified: bool = False
    prewrite_safety_verified: bool = False
    lifetime_verified: bool = False
    async_verified: bool = False
    unwind_verified: bool = False
    consumer_proof: bool = False


def recognize_mrs_entry(read_word, entry, expected_accessor):
    """Recognize the whole fixed body and one exact external BL target.

    Default-kind1 takes the quarantined call and skips the generic BLR. Saved
    guest PC is reconstructed by sign-extending the return island's imm26 before
    scaling. All guest state is restored before52post-index zero stores clear
    the shared frame and restore its incoming SP. The final BR uses x16; the
    return island must restore x16/x17, erase its outer spill and resume the guest.
    These are conditional instruction effects, not permission to waive aliases.
    """
    if (type(entry) is not int or entry < 0 or entry & 3
            or entry > UINT64_END - BODY_BYTES
            or type(expected_accessor) is not int or expected_accessor < 0
            or expected_accessor & 3 or expected_accessor > UINT64_END - 4
            or entry <= expected_accessor < entry + BODY_BYTES):
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    if any(word != _WORDS[index] for index, word in enumerate(words)
           if index != ACCESSOR_CALL_INDEX):
        return None
    call = words[ACCESSOR_CALL_INDEX]
    if call & 0xFC000000 != 0x94000000:
        return None
    displacement = call & 0x3FFFFFF
    if displacement & 1 << 25:
        displacement -= 1 << 26
    call_pc = entry + ACCESSOR_CALL_INDEX * 4
    target = call_pc + displacement * 4
    if not 0 <= target <= UINT64_END - 4 or target != expected_accessor:
        return None
    return MrsEntryEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        target, call_pc, entry + 120 * 4, entry + 219 * 4,
    )
