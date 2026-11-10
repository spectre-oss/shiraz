# SPDX-License-Identifier: GPL-3.0-only
"""Exact byte-derived effects for bounded AArch64 outlined atomic helpers.

This is a diagnostic recognizer, never executable code or an ABI-name allowlist.
The caller supplies independently validated executable words and an ownership
check for readable, non-executable allocated image data (including BSS). It must
also retain its ordinary TP, stack-alias and return-PC memory checks when applying
these effects. Recognition alone does not establish a TLS-use proof.
"""

import hashlib
from dataclasses import dataclass


@dataclass(frozen=True)
class AtomicPath:
    name: str
    ret_pc: int
    clobbers: tuple[str, ...]
    may_write_memory: bool = True
    preserves_nzcv: bool = True


@dataclass(frozen=True)
class Atomic32Effects:
    entry: int
    body_size: int
    body_sha256: str
    flag_address: int
    paths: tuple[AtomicPath, ...]
    memory_base: str = "x1"
    memory_payload: str = "x0"
    memory_result: str = "x0"
    rmw_width: int = 4
    flag_width: int = 1
    preserves_sp: bool = True
    preserves_lr: bool = True
    preserves_vectors: bool = True


@dataclass(frozen=True)
class Atomic64CompareSwapEffects:
    entry: int
    body_size: int
    body_sha256: str
    flag_address: int
    paths: tuple[AtomicPath, ...]
    memory_order: str
    memory_base: str = "x2"
    memory_compare: str = "x0"
    memory_payload: str = "x1"
    memory_result: str = "x0"
    rmw_width: int = 8
    flag_width: int = 1
    preserves_sp: bool = True
    preserves_lr: bool = True
    preserves_vectors: bool = True
    # LSE leaves flags intact, but every LL/SC return follows a CMP.
    preserves_nzcv: bool = False


def recognize_atomic32(read_word, entry, allocated_readable_data):
    """Return exact path effects, or None for any unsupported byte/context.

    ``read_word(pc)`` must return an integer word from independently validated
    executable file bytes, or None. ``allocated_readable_data(address, width)``
    must confirm an owned readable non-executable LOAD memory extent. Merely
    finding an address in file bytes is insufficient, while BSS is legitimate.

    ret_pc identifies a validated RET instruction, not its dynamic return target.
    The LLSC retry loop has the same abstract effects after every retry; this does
    not assert a concrete time bound or successful atomic progress.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > (1 << 64) - 48:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(12))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    fixed = {
        0: 0xD503245F,  # BTI c
        3: 0x34000070,  # CBZ w16, entry+24
        4: 0xB8E00020,  # LDADDAL w0,w0,[x1]
        5: 0xD65F03C0,  # RET x30
        6: 0x2A0003F0,  # MOV w16,w0
        7: 0x885FFC20,  # LDAXR w0,[x1]
        8: 0x0B100011,  # ADD w17,w0,w16 (no shift)
        9: 0x880FFC31,  # STLXR w15,w17,[x1]
        10: 0x35FFFFAF,  # CBNZ w15, entry+28
        11: 0xD65F03C0,  # RET x30
    }
    if any(words[index] != expected for index, expected in fixed.items()):
        return None
    adrp, load = words[1:3]
    if adrp & 0x9F00001F != 0x90000010:  # ADRP x16, page
        return None
    if load & 0xFFC003FF != 0x39400210:  # LDRB w16,[x16,#imm12]
        return None
    displacement = (((adrp >> 5) & 0x7FFFF) << 2) | ((adrp >> 29) & 3)
    if displacement & (1 << 20):
        displacement -= 1 << 21
    page = ((entry + 4) & ~0xFFF) + (displacement << 12)
    flag = page + ((load >> 10) & 0xFFF)
    if not 0 <= page < 1 << 64 or not 0 <= flag < 1 << 64:
        return None
    if allocated_readable_data(flag, 1) is not True:
        return None
    body = b"".join(word.to_bytes(4, "little") for word in words)
    return Atomic32Effects(
        entry=entry,
        body_size=48,
        body_sha256=hashlib.sha256(body).hexdigest(),
        flag_address=flag,
        paths=(
            AtomicPath("lse", entry + 20, ("x0", "x16")),
            AtomicPath("llsc", entry + 44, ("x0", "x15", "x16", "x17")),
        ),
    )


def recognize_cas64(read_word, entry, allocated_readable_data):
    """Recognize four complete 64-bit outlined compare-and-swap bodies.

    Input ownership and byte-reading contracts are identical to recognize_atomic32.
    The four accepted memory orders have independently matched LSE, exclusive
    load and exclusive store words. Mixed-order or changed bodies are refused.

    Every return produces the observed memory value in x0. x0 is a comparison
    input, while x1 alone is the value conditionally stored. Initial LL/SC
    mismatch preserves x17; a mismatch after a failed exclusive attempt need
    not. The retry path therefore conservatively clobbers x17 as well as the
    success path. The retry abstraction asserts no progress or retry bound.
    """
    if type(entry) is not int or entry < 0 or entry & 3 or entry > (1 << 64) - 52:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(13))
    if any(type(word) is not int or not 0 <= word < 1 << 32 for word in words):
        return None
    fixed = {
        0: 0xD503245F,  # BTI c
        3: 0x34000070,  # CBZ w16, entry+24
        5: 0xD65F03C0,  # RET x30 (LSE)
        6: 0xAA0003F0,  # MOV x16,x0
        8: 0xEB10001F,  # CMP x0,x16
        9: 0x54000061,  # B.NE entry+48
        11: 0x35FFFF91,  # CBNZ w17, entry+28
        12: 0xD65F03C0,  # RET x30 (LL/SC)
    }
    if any(words[index] != expected for index, expected in fixed.items()):
        return None
    memory_orders = {
        # CAS/CASA/CASL/CASAL x0,x1,[x2]; LDXR/LDAXR x0,[x2];
        # STXR/STLXR w17,x1,[x2]. No unobserved form is inferred.
        (0xC8A07C41, 0xC85F7C40, 0xC8117C41): "relaxed",
        (0xC8E07C41, 0xC85FFC40, 0xC8117C41): "acquire",
        (0xC8A0FC41, 0xC85F7C40, 0xC811FC41): "release",
        (0xC8E0FC41, 0xC85FFC40, 0xC811FC41): "acquire-release",
    }
    memory_order = memory_orders.get((words[4], words[7], words[10]))
    if memory_order is None:
        return None
    adrp, load = words[1:3]
    if adrp & 0x9F00001F != 0x90000010:  # ADRP x16, page
        return None
    if load & 0xFFC003FF != 0x39400210:  # LDRB w16,[x16,#imm12]
        return None
    displacement = (((adrp >> 5) & 0x7FFFF) << 2) | ((adrp >> 29) & 3)
    if displacement & (1 << 20):
        displacement -= 1 << 21
    page = ((entry + 4) & ~0xFFF) + (displacement << 12)
    flag = page + ((load >> 10) & 0xFFF)
    if not 0 <= page < 1 << 64 or not 0 <= flag < 1 << 64:
        return None
    if allocated_readable_data(flag, 1) is not True:
        return None
    body = b"".join(word.to_bytes(4, "little") for word in words)
    return Atomic64CompareSwapEffects(
        entry=entry,
        body_size=52,
        body_sha256=hashlib.sha256(body).hexdigest(),
        flag_address=flag,
        memory_order=memory_order,
        paths=(
            AtomicPath("lse", entry + 20, ("x0", "x16")),
            AtomicPath(
                "llsc-initial-mismatch", entry + 48, ("x0", "x16"), False, False
            ),
            AtomicPath(
                "llsc-success-or-retry-mismatch",
                entry + 48,
                ("x0", "x16", "x17"),
                True,
                False,
            ),
        ),
    )
