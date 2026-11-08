# SPDX-License-Identifier: GPL-3.0-only
"""Typed ELF-site validation of producer MRS installation records.

This checks a claim against source ELF bytes. It does not authenticate bridge
semantics, prove execution, or certify a complete spill footprint. Consumers
still need the explicitly trusted first-party bridge and live domain contract.
"""

import struct
from dataclasses import dataclass

MAX_MRS_SITES = 131072


@dataclass(frozen=True)
class MrsWindowRecord:
    profile: int
    before: int
    after: int
    sites: frozenset[int]
    image_stack_exclusion_profile: int | None = None


def validate_mrs_window_record(metadata, record):
    fields = {"profile", "before", "after", "verification", "execution_claim", "sites"}
    if type(record) is not dict or set(record) not in (
        fields,
        fields | {"image_stack_exclusion_profile"},
    ):
        raise ValueError("invalid MRS window record fields")
    exclusion = record.get("image_stack_exclusion_profile")
    if "image_stack_exclusion_profile" in record and (
        type(exclusion) is not int or exclusion != 1
    ):
        raise ValueError("unsupported image/native-stack exclusion profile")
    if any(type(record[key]) is not int for key in ("profile", "before", "after")) or (
        record["profile"],
        record["before"],
        record["after"],
    ) != (1, 4096, 256):
        raise ValueError("unsupported MRS window profile")
    if (
        record["verification"] != "mapped-code-default-accessor"
        or record["execution_claim"] is not False
    ):
        raise ValueError("MRS window record is not an installation-only snapshot")
    sites = record["sites"]
    if type(sites) is not list or len(sites) > MAX_MRS_SITES:
        raise ValueError("MRS window site count bound")
    seen = set()
    for site in sites:
        if (
            type(site) is not int
            or not 0 <= site <= (1 << 64) - 4
            or site & 3
            or site in seen
        ):
            raise ValueError("invalid or duplicate MRS window site")
        matches = sum(
            bool(flags & 1) and begin <= site and site + 4 <= begin + file_size
            for begin, _, _, file_size, flags in metadata.loads
        )
        if matches != 1:
            raise ValueError("MRS window site lacks executable file bytes")
        word = struct.unpack("<I", metadata.read(site, 4))[0]
        if word & 0xFFFFFFE0 != 0xD53BD040 or word & 31 in {18, 31}:
            raise ValueError("MRS window site is not a value-producing MRS")
        seen.add(site)
    return MrsWindowRecord(1, 4096, 256, frozenset(seen), exclusion)
