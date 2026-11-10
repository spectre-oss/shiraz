# SPDX-License-Identifier: GPL-3.0-only
"""Exact byte-derived effects for observed outlined AArch64 SWP64 helpers.

The caller supplies executable words and independently owned readable,
non-executable flag data. Recognition is neither a TLS-use exemption nor a
concurrency, progress, native-memory or lifetime proof. Applying these effects
still requires the ordinary pointer, stack-alias and return-target checks.
"""

import hashlib
from dataclasses import dataclass

from guest_callee_effects import AtomicPath

BODY_WORDS = 11
BODY_BYTES = BODY_WORDS * 4
UINT64_END = 1 << 64


@dataclass(frozen=True)
class Atomic64SwapEffects:
    entry: int
    body_size: int
    body_sha256: str
    flag_address: int
    paths: tuple[AtomicPath, ...]
    memory_order: str
    operation: str = "atomic-swap64"
    memory_base: str = "x1"
    memory_payload: str = "x0"
    memory_result: str = "x0"
    rmw_width: int = 8
    flag_width: int = 1
    preserves_sp: bool = True
    preserves_lr: bool = True
    preserves_vectors: bool = True
    preserves_nzcv: bool = True


def recognize_swap64(read_word, entry, allocated_readable_data):
    """Return complete observed SWP64 effects, or None for unsupported input.

    Only the three complete relaxed/acquire/acquire-release bodies observed in
    the target are accepted. Each LSE/exclusive-load/exclusive-store order tuple
    is matched independently; a release-only or mixed-order form is not inferred.

    Both returns produce the old memory value in x0. The LL/SC path copies the
    input payload to x16 before loading x0, then retries the load/store pair while
    w17 is nonzero. Every iteration has the same conservative effects; this
    abstraction does not promise eventual success or bound the retry count.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > UINT64_END - BODY_BYTES:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(BODY_WORDS))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    fixed = {
        0: 0xD503245F,  # BTI c
        3: 0x34000070,  # CBZ w16,entry+24
        5: 0xD65F03C0,  # RET x30 (LSE)
        6: 0xAA0003F0,  # MOV x16,x0
        9: 0x35FFFFD1,  # CBNZ w17,entry+28
        10: 0xD65F03C0,  # RET x30 (LL/SC)
    }
    if any(words[index] != expected for index, expected in fixed.items()):
        return None
    orders = {
        # SWP/SWPA/SWPAL x0,x0,[x1]; LDXR/LDAXR x0,[x1];
        # STXR/STLXR w17,x16,[x1]. No release-only body was observed.
        (0xF8208020, 0xC85F7C20, 0xC8117C30): "relaxed",
        (0xF8A08020, 0xC85FFC20, 0xC8117C30): "acquire",
        (0xF8E08020, 0xC85FFC20, 0xC811FC30): "acquire-release",
    }
    memory_order = orders.get((words[4], words[7], words[8]))
    if memory_order is None:
        return None
    adrp, load = words[1:3]
    if adrp & 0x9F00001F != 0x90000010:  # ADRP x16,page
        return None
    if load & 0xFFC003FF != 0x39400210:  # LDRB w16,[x16,#imm12]
        return None
    displacement = ((adrp >> 5 & 0x7FFFF) << 2) | (adrp >> 29 & 3)
    if displacement & 1 << 20:
        displacement -= 1 << 21
    page = ((entry + 4) & ~0xFFF) + displacement * 4096
    flag = page + (load >> 10 & 0xFFF)
    if (not 0 <= page < UINT64_END or not 0 <= flag < UINT64_END
            or entry <= flag < entry + BODY_BYTES):
        return None
    if allocated_readable_data(flag, 1) is not True:
        return None
    return Atomic64SwapEffects(
        entry=entry,
        body_size=BODY_BYTES,
        body_sha256=hashlib.sha256(b"".join(word.to_bytes(4, "little") for word in words)).hexdigest(),
        flag_address=flag,
        paths=(
            AtomicPath("lse", entry + 20, ("x0", "x16")),
            AtomicPath("llsc", entry + 40, ("x0", "x16", "x17")),
        ),
        memory_order=memory_order,
    )
