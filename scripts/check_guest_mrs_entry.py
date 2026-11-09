#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inspect exact MRS entry/getter code; never grant an execution or TLS exemption."""
import argparse
import dataclasses
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES, _MachO
from guest_mrs_access import BODY_BYTES as ACCESSOR_BYTES
from guest_mrs_access import recognize_mrs_access
from guest_mrs_entry import BODY_BYTES as ENTRY_BYTES
from guest_mrs_entry import recognize_mrs_entry


def check_entry(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('MRS entry image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    accessor_image = _MachO(data, symbol='_shiraz_guest_tls_access', body_bytes=ACCESSOR_BYTES)
    entry_image = _MachO(data, symbol='_shiraz_guest_tpidr_entry', body_bytes=ENTRY_BYTES)
    if (entry_image.entry < accessor_image.entry + ACCESSOR_BYTES
            and accessor_image.entry < entry_image.entry + ENTRY_BYTES):
        raise ValueError('MRS entry and accessor text overlap')
    accessor = recognize_mrs_access(accessor_image.read_word, accessor_image.entry, accessor_image.readable_data)
    entry = recognize_mrs_entry(entry_image.read_word, entry_image.entry, accessor_image.entry)
    if accessor is None or entry is None:
        raise ValueError('unsupported compiled default MRS entry/accessor pair')
    return {'image_sha256': hashlib.sha256(data).hexdigest(),
            'coordinate_system': 'file Mach-O VM addresses before slide; no live-island mapping verified',
            'entry': dataclasses.asdict(entry), 'accessor': dataclasses.asdict(accessor),
            'execution_claim': False, 'consumer_proof': False, 'islands_verified': False,
            'runtime_publication_verified': False, 'table_stack_exclusion_verified': False,
            'unwind_metadata_verified': False,
            'scope': 'exact default-kind entry/getter bodies only; runtime context, exclusion and lifetime remain required'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_entry(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_MRS_ENTRY_CODE_PASS entry_sha256=' + result['entry']['body_sha256'] +
          ' accessor_sha256=' + result['accessor']['body_sha256'] +
          ' execution_claim=0 consumer_proof=0 runtime_exclusion_verified=0')


if __name__ == '__main__':
    main()
