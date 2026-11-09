# SPDX-License-Identifier: GPL-3.0-only
"""Exact allocator assembly entry, conditional on an intact normal-return frame.

The caller supplies independently executable words and the exact, separately
validated native-body address. This authenticates no native body, allocator,
object or stack ownership, pre-write safety, unwind metadata, asynchronous
behavior, lifetime, or complete TLS consumers. Only this entry's 160-byte saved
frame is cleared; deeper native frames and copies remain outside the profile.
"""

import hashlib
from dataclasses import dataclass

BODY_WORDS = 143
BODY_BYTES = BODY_WORDS * 4
BODY_CALL_INDEX = 72
UINT64_END = 1 << 64

# Complete observed first-party assembly layout. Only the external BL immediate
# is relocatable; every save, quarantine, restore, clear and RET word is exact.
_WORDS = (
    0xD10283FF, 0xA90053F3, 0xA9015BF5, 0xA90263F7, 0xA9036BF9, 0xA90473FB,
    0xA9057BFD, 0x6D0627E8, 0x6D072FEA, 0x6D0837EC, 0x6D093FEE, 0xAA1F03E1,
    0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4, 0xAA1F03E5, 0xAA1F03E6, 0xAA1F03E7,
    0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA, 0xAA1F03EB, 0xAA1F03EC, 0xAA1F03ED,
    0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0, 0xAA1F03F1, 0xAA1F03F3, 0xAA1F03F4,
    0xAA1F03F5, 0xAA1F03F6, 0xAA1F03F7, 0xAA1F03F8, 0xAA1F03F9, 0xAA1F03FA,
    0xAA1F03FB, 0xAA1F03FC, 0xAA1F03FD, 0x6E201C00, 0x6E211C21, 0x6E221C42,
    0x6E231C63, 0x6E241C84, 0x6E251CA5, 0x6E261CC6, 0x6E271CE7, 0x6E281D08,
    0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B, 0x6E2C1D8C, 0x6E2D1DAD, 0x6E2E1DCE,
    0x6E2F1DEF, 0x6E301E10, 0x6E311E31, 0x6E321E52, 0x6E331E73, 0x6E341E94,
    0x6E351EB5, 0x6E361ED6, 0x6E371EF7, 0x6E381F18, 0x6E391F39, 0x6E3A1F5A,
    0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD, 0x6E3E1FDE, 0x6E3F1FFF, 0x910283E1,
    0x94000000, 0xAA1F03E1, 0xAA1F03E2, 0xAA1F03E3, 0xAA1F03E4, 0xAA1F03E5,
    0xAA1F03E6, 0xAA1F03E7, 0xAA1F03E8, 0xAA1F03E9, 0xAA1F03EA, 0xAA1F03EB,
    0xAA1F03EC, 0xAA1F03ED, 0xAA1F03EE, 0xAA1F03EF, 0xAA1F03F0, 0xAA1F03F1,
    0x6E201C00, 0x6E211C21, 0x6E221C42, 0x6E231C63, 0x6E241C84, 0x6E251CA5,
    0x6E261CC6, 0x6E271CE7, 0x6E281D08, 0x6E291D29, 0x6E2A1D4A, 0x6E2B1D6B,
    0x6E2C1D8C, 0x6E2D1DAD, 0x6E2E1DCE, 0x6E2F1DEF, 0x6E301E10, 0x6E311E31,
    0x6E321E52, 0x6E331E73, 0x6E341E94, 0x6E351EB5, 0x6E361ED6, 0x6E371EF7,
    0x6E381F18, 0x6E391F39, 0x6E3A1F5A, 0x6E3B1F7B, 0x6E3C1F9C, 0x6E3D1FBD,
    0x6E3E1FDE, 0x6E3F1FFF, 0x6D4627E8, 0x6D472FEA, 0x6D4837EC, 0x6D493FEE,
    0xA94053F3, 0xA9415BF5, 0xA94263F7, 0xA9436BF9, 0xA94473FB, 0xA9457BFD,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF,
    0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xA8817FFF, 0xD65F03C0,
)


@dataclass(frozen=True)
class MallocEntryEffects:
    entry: int
    body_size: int
    body_sha256: str
    native_body_target: int
    native_body_call_pc: int
    ret_pc: int
    native_body_calls: int = 1
    size_register: str = "x0"
    original_sp_register: str = "x1"
    original_sp_from_saved_frame_sp: int = 160
    quarantined_gprs_at_body_call: tuple[str, ...] = (
        *(f"x{i}" for i in range(2, 18)), *(f"x{i}" for i in range(19, 30)),
    )
    quarantined_vectors_at_body_call: tuple[str, ...] = tuple(f"v{i}" for i in range(32))
    preserved_gprs_on_normal_return: tuple[str, ...] = tuple(f"x{i}" for i in range(19, 31))
    preserved_low_vector_halves: tuple[str, ...] = tuple(f"d{i}" for i in range(8, 16))
    cleared_return_gprs: tuple[str, ...] = tuple(f"x{i}" for i in range(1, 18))
    cleared_return_vectors: tuple[str, ...] = (
        *(f"q{i}" for i in range(8)), *(f"q{i}" for i in range(16, 32)),
    )
    cleared_return_vector_upper_halves: tuple[str, ...] = tuple(f"q{i}" for i in range(8, 16))
    native_x18_untouched_by_entry: bool = True
    result_register: str = "x0"
    body_result_passed_through: bool = True
    saved_frame_bytes: int = 160
    saved_frame_clear_stores: int = 10
    saved_frame_clear_interval_from_original_sp: tuple[int, int] = (-160, 0)
    saved_frame_cleared_on_normal_return: bool = True
    normal_return_preserves_sp: bool = True
    requires_clean_size: bool = True
    requires_conforming_native_body: bool = True
    requires_intact_saved_frame: bool = True
    native_body_verified: bool = False
    allocator_verified: bool = False
    object_validity_verified: bool = False
    stack_ownership_verified: bool = False
    prewrite_safety_verified: bool = False
    unwind_verified: bool = False
    async_verified: bool = False
    lifetime_verified: bool = False
    native_memory_purity_verified: bool = False
    consumer_proof: bool = False


def recognize_malloc_entry(read_word, entry, expected_body):
    """Match the whole fixed entry and its exact, external signed BL target.

    x0 remains size at admission; x1 is the original SP rather than a zeroed
    register. Native-body normal return and an intact saved frame are required
    for the described restores and ten post-indexed zero stores. x0 passes
    through without proving allocator semantics or return-object validity.
    """
    if (type(entry) is not int or entry < 0 or entry & 3
            or entry > UINT64_END - BODY_BYTES
            or type(expected_body) is not int or expected_body < 0
            or expected_body & 3 or expected_body > UINT64_END - 4
            or entry <= expected_body < entry + BODY_BYTES):
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    if any(word != _WORDS[index] for index, word in enumerate(words)
           if index != BODY_CALL_INDEX):
        return None
    call = words[BODY_CALL_INDEX]
    if call & 0xFC000000 != 0x94000000:
        return None
    displacement = call & 0x3FFFFFF
    if displacement & (1 << 25):
        displacement -= 1 << 26
    call_pc = entry + BODY_CALL_INDEX * 4
    target = call_pc + displacement * 4
    if not 0 <= target <= UINT64_END - 4 or target != expected_body:
        return None
    return MallocEntryEffects(
        entry, BODY_BYTES,
        hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        target, call_pc, entry + (BODY_WORDS - 1) * 4,
    )
