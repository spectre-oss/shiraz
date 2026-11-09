# SPDX-License-Identifier: GPL-3.0-only
"""Exact A64 register-offset halfword accesses; no memory/provenance permission.

Only integer LDRH/STRH register-offset encodings are described. The caller must
independently validate decoded effects, executable bytes, source provenance,
address arithmetic and the eventual memory access. An exact index supplies no
stack, image, object, exclusion or lifetime capability.
"""

from dataclasses import dataclass

MAX_DISPLACEMENT = 1 << 20


@dataclass(frozen=True)
class IndexedHalfword:
    operation: str
    base: str
    index: str
    data_register: str
    index_width: int
    extension: str
    shift: int
    width: int = 2
    writeback: bool = False


def recognize_indexed_halfword(raw):
    """Decode exact four-byte integer unsigned-halfword load/store forms.

    Option encodings 010/011/110/111 select UXTW/LSL/SXTW/SXTX. Other options,
    SIMD, signed loads, immediate addressing and pre/post-indexing are refused.
    Register31 is SP only in the base field, and ZR in index/data fields.
    """
    if type(raw) is not bytes or len(raw) != 4:
        return None
    word = int.from_bytes(raw, "little")
    opcode = word & 0xFFE00C00
    if opcode not in (0x78200800, 0x78600800):
        return None
    option = (word >> 13) & 7
    extensions = {2: (32, "uxtw"), 3: (64, "lsl"),
                  6: (32, "sxtw"), 7: (64, "sxtx")}
    if option not in extensions:
        return None
    bits, extension = extensions[option]
    base, index, data = (word >> 5) & 31, (word >> 16) & 31, word & 31
    return IndexedHalfword(
        "ldrh" if opcode == 0x78600800 else "strh",
        "sp" if base == 31 else f"x{base}",
        ("wzr" if bits == 32 else "xzr") if index == 31 else f"{'w' if bits == 32 else 'x'}{index}",
        "wzr" if data == 31 else f"w{data}", bits, extension,
        (word >> 12) & 1,
    )


def exact_halfword_displacement(profile, index_value):
    """Return a bounded mathematical offset, refusing loss or shift wrap.

    index_value is the exact architectural index-register bit pattern, already
    narrowed to its declared width. Signed forms sign-extend before shifting;
    unsigned forms never reinterpret a high bit as a negative offset. The final
    base addition and all pointer/provenance checks remain the caller's job.
    """
    if not isinstance(profile, IndexedHalfword):
        return None
    expected = {"uxtw": 32, "lsl": 64, "sxtw": 32, "sxtx": 64}
    if (profile.extension not in expected or profile.index_width != expected[profile.extension]
            or type(profile.shift) is not int or profile.shift not in (0, 1)
            or type(index_value) is not int or not 0 <= index_value < 1 << profile.index_width):
        return None
    value = index_value
    signed = profile.extension in ("sxtw", "sxtx")
    if signed and value & (1 << (profile.index_width - 1)):
        value -= 1 << profile.index_width
    displacement = value << profile.shift
    # SXTW's sign extension is also a 64-bit architectural operation.
    if signed:
        if not -(1 << 63) <= displacement < 1 << 63:
            return None
    elif not 0 <= displacement < 1 << 64:
        return None
    if not -MAX_DISPLACEMENT <= displacement <= MAX_DISPLACEMENT:
        return None
    return displacement
