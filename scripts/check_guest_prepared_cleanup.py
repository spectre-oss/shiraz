#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inspect the prepared cleanup query read set; never certify row semantics."""
import argparse
import dataclasses
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES, _MachO
from guest_prepared_cleanup import BODY_BYTES, recognize_prepared_cleanup

SYMBOL = '__ZN6shiraz5guest31prepared_guest_cleanup_admittedEm'


def check_prepared_cleanup(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('prepared cleanup image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    image = _MachO(data, symbol=SYMBOL, body_bytes=BODY_BYTES)
    effects = recognize_prepared_cleanup(image.read_word, image.entry, image.readable_data)
    if effects is None:
        raise ValueError('unsupported compiled prepared cleanup query')
    return {
        'image_sha256': hashlib.sha256(data).hexdigest(),
        'coordinate_system': 'file Mach-O VM addresses before slide',
        'effects': dataclasses.asdict(effects),
        'execution_claim': False, 'consumer_proof': False,
        'table_identity_verified': False, 'publication_verified': False,
        'stack_exclusion_verified': False, 'caller_guard_verified': False,
        'native_memory_purity_verified': False,
        'scope': 'exact instruction read set; inferred table roles require independent runtime binding',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_prepared_cleanup(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_PREPARED_CLEANUP_CODE_PASS body_sha256=' + result['effects']['body_sha256'] +
          ' table_identity_verified=0 execution_claim=0 consumer_proof=0')


if __name__ == '__main__':
    main()
