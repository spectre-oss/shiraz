#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inventory bounded allocator-body branch encodings, without granting effects.

This is a work list for transitive native-body validation. It scans every word
in an independently bounded function, including cold/unreachable words. It does
not decode general instructions, identify every possible control transfer,
resolve imports/TLV calls, or prove memory accesses or exception edges safe.
"""
import argparse
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES
from guest_native_function import read_native_function

SYMBOL = '_shiraz_guest_managed_malloc_body'
UINT64_END = 1 << 64


def branch_inventory(body, entry):
    """Return syntactic known branch sites; absence is never a safety fact."""
    if (type(body) is not bytes or not body or len(body) > 65536 or len(body) & 3
            or type(entry) is not int or entry < 0 or entry & 3
            or entry > UINT64_END - len(body)):
        raise ValueError('native body inventory bounds')
    rows = []
    for offset in range(0, len(body), 4):
        word = int.from_bytes(body[offset:offset+4], 'little')
        pc = entry + offset
        kind, bits, shift = None, 0, 0
        if word & 0x7c000000 == 0x14000000:
            kind, bits = ('call' if word & 0x80000000 else 'branch'), 26
        elif word & 0xff000010 == 0x54000000:
            kind, bits, shift = 'conditional', 19, 5
        elif word & 0x7e000000 == 0x34000000:
            kind, bits, shift = 'compare', 19, 5
        elif word & 0x7e000000 == 0x36000000:
            kind, bits, shift = 'test', 14, 5
        if kind is not None:
            immediate = (word >> shift) & ((1 << bits) - 1)
            if immediate & (1 << (bits - 1)):
                immediate -= 1 << bits
            target = pc + immediate * 4
            if not 0 <= target < UINT64_END:
                raise ValueError('native branch target overflow')
            rows.append({'pc': pc, 'word': word, 'kind': kind, 'target': target,
                         'within_selected_body': entry <= target < entry + len(body)})
        elif word & 0xfffffc1f in (0xd61f0000, 0xd63f0000, 0xd65f0000):
            kind = {0xd61f0000: 'indirect-branch', 0xd63f0000: 'indirect-call',
                    0xd65f0000: 'return'}[word & 0xfffffc1f]
            rows.append({'pc': pc, 'word': word, 'kind': kind,
                         'register': (word >> 5) & 31, 'target': None})
    return tuple(rows)


def check_malloc_body(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('native body image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    function = read_native_function(data, SYMBOL)
    return {
        'image_sha256': hashlib.sha256(data).hexdigest(),
        'coordinate_system': 'file Mach-O VM addresses before slide',
        'symbol': SYMBOL, 'entry': function.entry, 'end': function.end,
        'body_sha256': hashlib.sha256(function.body).hexdigest(),
        'body_bytes': len(function.body),
        'known_branch_encodings': branch_inventory(function.body, function.entry),
        'symbol_to_next_start_extent_verified': True,
        'exclusive_instruction_provenance_verified': False,
        'complete_control_flow_verified': False,
        'memory_effects_verified': False, 'transitive_calls_verified': False,
        'exception_edges_verified': False, 'execution_claim': False,
        'native_memory_purity_verified': False, 'consumer_proof': False,
        'scope': 'complete bounded bytes; partial syntactic branch inventory only',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_malloc_body(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_MALLOC_BODY_INVENTORY body_sha256=' + result['body_sha256'] +
          ' body_bytes=' + str(result['body_bytes']) + ' native_memory_purity_verified=0')


if __name__ == '__main__':
    main()
