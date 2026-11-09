#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inspect allocator entry bytes and same-file call identity, never native purity."""
import argparse
import dataclasses
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES, _MachO
from guest_malloc_entry import BODY_BYTES, recognize_malloc_entry


def check_malloc_entry(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('allocator entry image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    image = _MachO(data, symbol='_shiraz_guest_managed_malloc_entry', body_bytes=BODY_BYTES)
    target = _MachO(data, symbol='_shiraz_guest_managed_malloc_body', body_bytes=4)
    effects = recognize_malloc_entry(image.read_word, image.entry, target.entry)
    if effects is None:
        raise ValueError('unsupported compiled allocator entry or call identity')
    return {
        'image_sha256': hashlib.sha256(data).hexdigest(),
        'coordinate_system': 'file Mach-O VM addresses before slide',
        'entry': dataclasses.asdict(effects),
        'native_body': {'entry': target.entry, 'identity': 'same-file executable symbol only',
                        'body_semantics_verified': False},
        'execution_claim': False, 'consumer_proof': False,
        'caller_inputs_verified': False, 'runtime_publication_verified': False,
        'unwind_metadata_verified': False, 'native_memory_purity_verified': False,
        'scope': 'exact assembly entry only; native body, object/domain validity and lifetime remain unverified',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_malloc_entry(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_MALLOC_ENTRY_CODE_PASS body_sha256=' + result['entry']['body_sha256'] +
          ' body_identity_only=1 execution_claim=0 consumer_proof=0')


if __name__ == '__main__':
    main()
