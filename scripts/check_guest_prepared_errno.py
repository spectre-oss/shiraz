#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check prepared errno query bytes/read spans, not cell or native-body lifetime."""
import argparse
import dataclasses
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES, _MachO
from guest_native_function import read_native_function
from guest_prepared_errno import BODY_BYTES, recognize_prepared_errno

SYMBOL = '__ZN6shiraz5guest29prepared_guest_errno_locationEm'


def check_prepared_errno(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('prepared errno image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    function = read_native_function(data, SYMBOL)
    if len(function.body) != BODY_BYTES:
        raise ValueError('prepared errno independent function extent mismatch')
    image = _MachO(data, symbol=SYMBOL, body_bytes=BODY_BYTES)
    effects = recognize_prepared_errno(image.read_word, image.entry, image.readable_data)
    if effects is None:
        raise ValueError('unsupported compiled prepared errno read set')
    return {
        'image_sha256': hashlib.sha256(data).hexdigest(),
        'coordinate_system': 'file Mach-O VM addresses before slide',
        'effects': dataclasses.asdict(effects),
        'symbol_to_next_start_extent_verified': True,
        'table_identity_verified': False, 'publication_verified': False,
        'cell_ownership_verified': False, 'cell_lifetime_verified': False,
        'stack_exclusion_verified': False, 'execution_claim': False,
        'native_memory_purity_verified': False, 'consumer_proof': False,
        'scope': 'exact query instructions and inferred read spans; independent cell/row contracts required',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_prepared_errno(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_PREPARED_ERRNO_CODE_PASS body_sha256=' + result['effects']['body_sha256'] +
          ' table_identity_verified=0 cell_lifetime_verified=0 consumer_proof=0')


if __name__ == '__main__':
    main()
