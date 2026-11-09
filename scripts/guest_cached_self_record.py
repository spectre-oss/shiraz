# SPDX-License-Identifier: GPL-3.0-only
"""Independently bind a cached pthread leaf snapshot to bounded Mach-O bytes.

The caller must authenticate/trust the exact first-party bridge identity and
runtime publication/stack-exclusion contract. This reader proves neither current
cache values, execution, asynchronous lifetime, nor a complete TLS consumer.
"""

import struct
from dataclasses import dataclass

from guest_cached_self import (
    BODY_BYTES,
    TABLE_BYTES,
    CachedSelfEffects,
    recognize_cached_self,
)

ABI = "prepared/pthread-self-noargs"
SYMBOL = "_shiraz_guest_cached_pthread_self"
ROLES = ("owners", "keys", "low", "high")
UINT64_END = 1 << 64
MAX_BYTES = 256 << 20


@dataclass(frozen=True)
class CachedSelfRecordTable:
    role: str
    offset: int
    byte_count: int


@dataclass(frozen=True)
class CachedSelfRecord:
    entry_offset: int
    body_hex: str
    tables: tuple[CachedSelfRecordTable, ...]
    stack_exclusion_profile: int = 1


@dataclass(frozen=True)
class ValidatedCachedSelf:
    record: CachedSelfRecord
    effects: CachedSelfEffects


def _span(start, size, end=UINT64_END):
    return type(start) is int and type(size) is int and 0 <= start < end and 0 < size <= end - start


def _name(raw):
    head, _, tail = raw.partition(b"\0")
    if any(tail):
        raise ValueError("invalid Mach-O name padding")
    try:
        return head.decode("ascii")
    except UnicodeDecodeError as error:
        raise ValueError("non-ASCII Mach-O name") from error


class _MachO:
    """Only thin little-endian ARM64 images with bounded ordinary segments."""

    def __init__(self, source):
        if not isinstance(source, (bytes, bytearray)) or not 32 <= len(source) <= MAX_BYTES:
            raise ValueError("cached self bridge byte bound")
        self.data = bytes(source)
        magic, cpu, _, kind, commands, command_bytes, _, _ = struct.unpack_from("<IiiIIIII", self.data)
        if (magic != 0xFEEDFACF or cpu != 0x100000C or kind not in (2, 6)
                or not 0 < commands <= 4096 or not commands * 8 <= command_bytes <= 16 << 20
                or command_bytes > len(self.data) - 32):
            raise ValueError("unsupported cached self Mach-O header")
        self.segments, self.sections = [], []
        symtab = None
        cursor, end = 32, 32 + command_bytes
        for _ in range(commands):
            if cursor + 8 > end:
                raise ValueError("truncated Mach-O command")
            command, size = struct.unpack_from("<II", self.data, cursor)
            if size < 8 or size & 7 or size > end - cursor:
                raise ValueError("invalid Mach-O command extent")
            if command == 0x19:  # LC_SEGMENT_64
                if size < 72:
                    raise ValueError("short Mach-O segment command")
                fields = struct.unpack_from("<II16sQQQQiiII", self.data, cursor)
                _, _, raw_name, address, memory_size, offset, file_size, maximum, initial, count, flags = fields
                name = _name(raw_name)
                # SG_READ_ONLY changes post-fixup protection, not segment
                # address/file layout. SG_HIGHVM and other layouts are refused.
                if (count > 4096 or size != 72 + count * 80 or flags & ~0x10
                        or flags & 0x10 and initial & 4
                        or not 0 <= initial <= 7 or not 0 <= maximum <= 7 or initial & ~maximum
                        or memory_size and not _span(address, memory_size)
                        or file_size > memory_size or offset > len(self.data)
                        or file_size > len(self.data) - offset):
                    raise ValueError("invalid Mach-O segment fields")
                if memory_size and any(address < a + n and a < address + memory_size
                                       for a, n, _, _, _, _ in self.segments):
                    raise ValueError("overlapping Mach-O segment memory")
                if file_size and any(offset < o + n and o < offset + file_size
                                     for _, _, o, n, _, _ in self.segments if n):
                    raise ValueError("overlapping Mach-O segment file bytes")
                segment = (address, memory_size, offset, file_size, initial, name)
                self.segments.append(segment)
                for index in range(count):
                    section = struct.unpack_from("<16s16sQQIIIIIIII", self.data, cursor + 72 + index * 80)
                    sect, owner, start, length, file_offset, alignment, relocations, relocation_count, attributes, _, _, _ = section
                    section_name = _name(sect)
                    if (_name(owner) != name or alignment > 63 or start < address
                            or start - address > memory_size or length > memory_size - (start - address)
                            or relocation_count > 1_000_000 or relocations > len(self.data)
                            or relocation_count * 8 > len(self.data) - relocations):
                        raise ValueError("invalid Mach-O section extent")
                    zerofill = attributes & 0xFF in (1, 0xC, 0x12)
                    if not zerofill and length and (
                            start - address > file_size or length > file_size - (start - address)
                            or file_offset != offset + start - address):
                        raise ValueError("Mach-O section/file mapping mismatch")
                    if len(self.sections) >= 4096:
                        raise ValueError("Mach-O section count bound")
                    self.sections.append((start, length, file_offset, initial, section_name, name, attributes, zerofill))
            elif command == 2:  # LC_SYMTAB
                if size != 24 or symtab is not None:
                    raise ValueError("invalid or duplicate Mach-O symbol table")
                symtab = struct.unpack_from("<IIII", self.data, cursor + 8)
            cursor += size
        if cursor != end:
            raise ValueError("Mach-O command count/size mismatch")
        headers = [a for a, _, off, size, prot, name in self.segments
                   if name == "__TEXT" and off == 0 and size >= end and prot & 5 == 5 and not prot & 2]
        if len(headers) != 1 or symtab is None:
            raise ValueError("cached self lacks independent text header/symbol table")
        self.base = headers[0]
        symbol_offset, symbol_count, strings_offset, strings_size = symtab
        if (not 0 < symbol_count <= 1_000_000 or not 0 < strings_size <= 64 << 20
                or symbol_offset > len(self.data) or symbol_count * 16 > len(self.data) - symbol_offset
                or strings_offset > len(self.data) or strings_size > len(self.data) - strings_offset):
            raise ValueError("Mach-O symbol/string table bound")
        def linkedit_span(offset, size):
            return sum(name == "__LINKEDIT" and protection & 1 and not protection & 4
                       and offset >= start and offset - start <= length
                       and size <= length - (offset - start)
                       for _, _, start, length, protection, name in self.segments) == 1
        if (not linkedit_span(symbol_offset, symbol_count * 16)
                or not linkedit_span(strings_offset, strings_size)
                or (symbol_offset < strings_offset + strings_size
                    and strings_offset < symbol_offset + symbol_count * 16)):
            raise ValueError("Mach-O symbols/strings lack independent linkedit extents")
        found = []
        for index in range(symbol_count):
            string, symbol_type, section, description, value = struct.unpack_from("<IBBHQ", self.data, symbol_offset + index * 16)
            if string >= strings_size:
                raise ValueError("Mach-O symbol string offset")
            begin = strings_offset + string
            stop = self.data.find(b"\0", begin, min(strings_offset + strings_size, begin + 4097))
            if stop < 0:
                raise ValueError("unbounded Mach-O symbol string")
            if self.data[begin:stop] != SYMBOL.encode():
                continue
            # Linked debug builds can retain an N_FUN/STABS observation with
            # the same name. It neither defines nor independently bounds code.
            if symbol_type & 0xE0:
                continue
            if (symbol_type & 0xE != 0xE or description & 0x100
                    or not 1 <= section <= len(self.sections)):
                raise ValueError("cached self symbol is undefined/indirect/debug/resolver")
            selected = self.sections[section - 1]
            if not self._text(selected, value, BODY_BYTES):
                raise ValueError("cached self symbol is not complete RX text")
            found.append(value)
        if len(found) != 1:
            raise ValueError("missing or duplicate cached self text symbol")
        self.entry = found[0]

    @staticmethod
    def _text(section, address, size):
        start, length, _, protection, name, segment, flags, zerofill = section
        return (name == "__text" and segment == "__TEXT" and not zerofill
                and protection & 5 == 5 and not protection & 2
                and flags & 0x80000000 and address >= start and address - start <= length
                and size <= length - (address - start))

    def read_word(self, address):
        spans = [s for s in self.sections if self._text(s, address, 4)]
        if len(spans) != 1:
            return None
        section = spans[0]
        return struct.unpack_from("<I", self.data, section[2] + address - section[0])[0]

    def readable_data(self, address, size):
        if not _span(address, size):
            return False
        return sum(bool(protection & 1 and not protection & 4)
                   and address >= start and address - start <= length
                   and size <= length - (address - start)
                   for start, length, _, _, protection, _ in self.segments) == 1


def _record(binding):
    fields = {"profile", "verification", "execution_claim", "entry_offset", "body_hex", "tables", "stack_exclusion_profile"}
    record = binding.get("cached_self")
    if not isinstance(record, dict) or set(record) != fields:
        raise ValueError("cached self record schema")
    if (type(record["profile"]) is not int or record["profile"] != 1
            or type(record["stack_exclusion_profile"]) is not int or record["stack_exclusion_profile"] != 1
            or record["verification"] != "mapped-first-party-code-and-cache-ranges"
            or record["execution_claim"] is not False
            or not _span(record["entry_offset"], BODY_BYTES)
            or record["entry_offset"] & 3):
        raise ValueError("cached self record fields")
    body = record["body_hex"]
    if not isinstance(body, str) or len(body) != BODY_BYTES * 2 or any(c not in "0123456789abcdef" for c in body):
        raise ValueError("cached self body hex bound")
    rows = record["tables"]
    if not isinstance(rows, list) or len(rows) != 4:
        raise ValueError("cached self table count")
    tables, spans = [], [(record["entry_offset"], record["entry_offset"] + BODY_BYTES)]
    for role, row in zip(ROLES, rows):
        if (not isinstance(row, dict) or set(row) != {"role", "offset", "bytes"}
                or row["role"] != role or type(row["bytes"]) is not int or row["bytes"] != TABLE_BYTES
                or not _span(row["offset"], TABLE_BYTES) or row["offset"] & 7):
            raise ValueError("cached self table fields")
        start, end = row["offset"], row["offset"] + TABLE_BYTES
        if any(start < high and low < end for low, high in spans):
            raise ValueError("cached self table/code overlap")
        spans.append((start, end))
        tables.append(CachedSelfRecordTable(role, start, TABLE_BYTES))
    return CachedSelfRecord(record["entry_offset"], body, tuple(tables))


def validate_cached_self_binding(binding, bridge_bytes=None):
    """Return independently checked effects only with exact trusted bridge bytes.

    Missing optional records remain unavailable. Without bridge bytes a present
    record is schema-checked but does not authorize native effects. Malformed or
    contradictory claims raise ValueError; callers must not silently waive them.
    """
    if not isinstance(binding, dict):
        raise TypeError("cached self binding object")
    if "cached_self" not in binding and binding.get("abi") != ABI:
        return None
    required = {"abi", "symbol", "version", "provider", "kind", "target",
                "provider_elf_sha256", "nonreturning", "addend", "relocation_type"}
    if (not required <= set(binding) or binding.get("abi") != ABI or binding.get("symbol") != "pthread_self"
            or binding.get("version") not in ("", "LIBC") or binding.get("provider") != "libc.so"
            or binding.get("kind") != "function" or binding.get("target") is not None
            or binding.get("provider_elf_sha256") is not None or binding.get("nonreturning", False) is not False
            or type(binding.get("addend")) is not int or binding["addend"] != 0
            or type(binding.get("relocation_type")) is not int or binding["relocation_type"] not in (1025, 1026)):
        raise ValueError("cached self requires exact prepared pthread binding")
    if "cached_self" not in binding:
        return None
    record = _record(binding)
    if bridge_bytes is None:
        return None
    image = _MachO(bridge_bytes)
    if image.base > UINT64_END - record.entry_offset or image.entry != image.base + record.entry_offset:
        raise ValueError("cached self record disagrees with independent symbol")
    words = [image.read_word(image.entry + i * 4) for i in range(BODY_BYTES // 4)]
    if any(word is None for word in words) or b"".join(w.to_bytes(4, "little") for w in words).hex() != record.body_hex:
        raise ValueError("cached self mapped/file body mismatch")
    effects = recognize_cached_self(image.read_word, image.entry, image.readable_data)
    if effects is None:
        raise ValueError("unsupported cached self complete body/table effects")
    expected = []
    for table in record.tables:
        if image.base > UINT64_END - table.offset:
            raise ValueError("cached self table normalization overflow")
        expected.append((table.role, image.base + table.offset, table.byte_count))
    if [(t.role, t.address, t.byte_count) for t in effects.tables] != expected:
        raise ValueError("cached self instruction/record table mismatch")
    return ValidatedCachedSelf(record, effects)
