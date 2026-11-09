# SPDX-License-Identifier: GPL-3.0-only
"""Exact executable-byte effects for one outlined AArch64 frame-save body.

Recognition supplies conditional frame effects, never complete caller/TLS proof.
The reader must return only independently bounded executable file words.
"""
import hashlib
from dataclasses import dataclass

# STP X28,X27,[SP,#16]; MOV X29,SP; remaining callee saves; RET X30.
WORDS = (0xA9016FFC, 0x910003FD, 0xA90267FA, 0xA9035FF8,
         0xA90457F6, 0xA9054FF4, 0xD65F03C0)
SAVES = tuple((f"x{register}", offset + part * 8)
              for first, offset in ((28, 16), (26, 32), (24, 48), (22, 64), (20, 80))
              for part, register in enumerate((first, first - 1)))


@dataclass(frozen=True)
class FramePrologueEffects:
    entry: int
    body_size: int
    body_sha256: str
    saves: tuple


def recognize(read_word, entry):
    if type(entry) is not int or entry < 0 or entry & 3 or entry + 28 > 1 << 64:
        return None
    words = tuple(read_word(entry + index * 4) for index in range(len(WORDS)))
    if words != WORDS:
        return None
    body = b"".join(word.to_bytes(4, "little") for word in words)
    return FramePrologueEffects(entry, len(body), hashlib.sha256(body).hexdigest(), SAVES)
