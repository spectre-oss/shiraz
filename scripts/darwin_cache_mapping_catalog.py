#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Emit bounded dyld cache mapping metadata for guest process snapshots.

Format facts: https://github.com/apple-oss-distributions/dyld/blob/main/include/mach-o/dyld_cache_format.h
The default legacy catalog contains no paths; --with-paths emits version 2.
A file's matching UUID and declared VM mapping
do not alone prove that a current process region is still backed by that file.
"""

import argparse
import ctypes
import os
import re
import stat
import struct
import sys

HEADER_SIZE = 464
UUID_OFFSET = 88
MAPPING_OFFSET = 16
SHARED_REGION_START = 224
SUBCACHE_ARRAY = 392
SUBTYPE_OFFSET = 456
MAX_SUBCACHES = 256
MAX_MAPPINGS = 64
MAX_FILE_MAPPINGS = 4096
ENTRY = struct.Struct("<QQQII")
OUTPUT = struct.Struct("<QQQQQ")


def exact(fd, size, offset):
    data = os.pread(fd, size, offset)
    if len(data) != size:
        raise ValueError("short cache metadata")
    return data


def open_regular(path):
    fd = os.open(path, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_size < 104:
            raise ValueError("cache file is not regular")
        return fd, before
    except BaseException:
        os.close(fd)
        raise


def unchanged(before, after):
    return (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
        before.st_ctime_ns,
    ) == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns)


def read_mappings(fd, file_stat, header, expected_uuid):
    if not header.startswith(b"dyld_v") or header[UUID_OFFSET : UUID_OFFSET + 16] != expected_uuid:
        raise ValueError("cache UUID or magic mismatch")
    offset, count = struct.unpack_from("<II", header, MAPPING_OFFSET)
    if (
        not count
        or count > MAX_MAPPINGS
        or offset > file_stat.st_size
        or count * ENTRY.size > file_stat.st_size - offset
    ):
        raise ValueError("invalid cache mapping table")
    raw = exact(fd, count * ENTRY.size, offset)
    mappings = []
    for start, size, file_offset, _max_prot, _init_prot in ENTRY.iter_unpack(raw):
        if (
            not size
            or start + size > (1 << 64) - 1
            or file_offset > file_stat.st_size
            or size > file_stat.st_size - file_offset
        ):
            raise ValueError("invalid cache mapping range")
        mappings.append((start, size, file_offset, file_stat.st_dev, file_stat.st_ino))
    if not unchanged(file_stat, os.fstat(fd)):
        raise ValueError("cache changed while being read")
    return mappings


NAMED_MAGIC = b"SHZMAP2\0"
MAX_CATALOG_BYTES = 8 * 1024 * 1024


def named_catalog(uuid, mappings, paths):
    if (
        len(uuid) != 16
        or not any(uuid)
        or not 0 < len(mappings) <= MAX_FILE_MAPPINGS
        or len(paths) != len(mappings)
    ):
        raise ValueError("invalid named catalog")
    output = bytearray(NAMED_MAGIC + uuid + struct.pack("<I", len(mappings)))
    for entry, path in zip(mappings, paths):
        if (
            not isinstance(path, bytes)
            or not path.startswith(b"/")
            or b"\0" in path
            or len(path) > 4096
        ):
            raise ValueError("invalid cache pathname")
        if not 0 <= entry[3] <= 0xFFFFFFFF or not entry[4]:
            raise ValueError("invalid cache file identity")
        if len(output) + OUTPUT.size + 4 + len(path) > MAX_CATALOG_BYTES:
            raise ValueError("named catalog too large")
        output.extend(OUTPUT.pack(*entry))
        output.extend(struct.pack("<I", len(path)))
        output.extend(path)
    return bytes(output)


def verify_path(path, file_stat):
    named = os.stat(path, follow_symlinks=False)
    if not stat.S_ISREG(named.st_mode) or not unchanged(file_stat, named):
        raise ValueError("cache pathname changed")


def build_catalog(include_paths=False):
    dyld = ctypes.CDLL(None)
    get_path = dyld.dyld_shared_cache_file_path
    get_path.restype = ctypes.c_char_p
    get_uuid = dyld._dyld_get_shared_cache_uuid
    get_uuid.argtypes = [ctypes.POINTER(ctypes.c_ubyte)]
    get_uuid.restype = ctypes.c_bool
    cache_uuid = (ctypes.c_ubyte * 16)()
    path = get_path()
    if not path or not get_uuid(cache_uuid) or not any(cache_uuid):
        raise ValueError("dyld cache identity unavailable")
    expected = bytes(cache_uuid)
    fd, st = open_regular(path)
    try:
        if st.st_size < HEADER_SIZE:
            raise ValueError("cache header too short")
        header = exact(fd, HEADER_SIZE, 0)
        mappings = read_mappings(fd, st, header, expected)
        paths = [path] * len(mappings)
        if include_paths:
            verify_path(path, st)
        map_offset = struct.unpack_from("<I", header, MAPPING_OFFSET)[0]
        if map_offset <= SUBCACHE_ARRAY + 8:
            raise ValueError("cache format lacks subcache metadata")
        sub_offset, sub_count = struct.unpack_from("<II", header, SUBCACHE_ARRAY)
        if not sub_count or sub_count > MAX_SUBCACHES:
            raise ValueError("unsupported subcache count")
        modern = map_offset > SUBTYPE_OFFSET
        entry_size = 56 if modern else 24
        if sub_offset > st.st_size or sub_count * entry_size > st.st_size - sub_offset:
            raise ValueError("invalid subcache table")
        entries = exact(fd, sub_count * entry_size, sub_offset)
        shared_start = struct.unpack_from("<Q", header, SHARED_REGION_START)[0]
        if not shared_start or not unchanged(st, os.fstat(fd)):
            raise ValueError("invalid or changed main cache")
    finally:
        os.close(fd)
    for index in range(sub_count):
        record = entries[index * entry_size : (index + 1) * entry_size]
        sub_uuid = record[:16]
        relative_vm = struct.unpack_from("<Q", record, 16)[0]
        suffix = record[24:].split(b"\0", 1)[0] if modern else b"." + str(index + 1).encode("ascii")
        if (
            not any(sub_uuid)
            or not re.fullmatch(rb"\.[A-Za-z0-9_.-]{1,30}", suffix)
            or b".." in suffix
        ):
            raise ValueError("invalid subcache identity")
        sub_fd, sub_stat = open_regular(path + suffix)
        try:
            sub_header = exact(sub_fd, 104, 0)
            part = read_mappings(sub_fd, sub_stat, sub_header, sub_uuid)
            if (
                relative_vm > (1 << 64) - 1 - shared_start
                or part[0][0] != shared_start + relative_vm
            ):
                raise ValueError("subcache VM origin mismatch")
            if include_paths:
                verify_path(path + suffix, sub_stat)
            mappings.extend(part)
            paths.extend([path + suffix] * len(part))
            if len(mappings) > MAX_FILE_MAPPINGS:
                raise ValueError("too many cache mappings")
        finally:
            os.close(sub_fd)
    if include_paths:
        return named_catalog(expected, mappings, paths)
    return (
        expected
        + struct.pack("<I", len(mappings))
        + b"".join(OUTPUT.pack(*entry) for entry in mappings)
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--with-paths", action="store_true")
    args = parser.parse_args()
    try:
        data = build_catalog(args.with_paths)
        sys.stdout.buffer.write(data)
        sys.stdout.buffer.flush()
    except (AttributeError, OSError, ValueError, struct.error, OverflowError):
        print("SHIRAZ_CACHE_FILE_HELPER status=unavailable", file=sys.stderr)
        return 1
    print(
        "SHIRAZ_CACHE_FILE_HELPER status=ok mappings="
        + str(
            struct.unpack_from("<I", data, 24)[0]
            if args.with_paths
            else (len(data) - 20) // OUTPUT.size
        ),
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
