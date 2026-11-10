#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inventory bounded allocator-body branch encodings, without granting effects.

This is a work list for transitive native-body validation. It scans every word
in an independently bounded function, including cold/unreachable words. It does
not decode general instructions, identify every possible control transfer,
resolve live imports/TLV calls, or prove memory accesses or exception edges safe.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

from guest_cached_self_record import MAX_BYTES
from guest_native_function import read_native_function, read_native_function_at
from guest_native_import import read_native_import

SYMBOL = '_shiraz_guest_managed_malloc_body'
UINT64_END = 1 << 64
MAX_DIRECT_TARGETS = 64
MAX_TARGET_METADATA_VISITS = 8_000_000


def _target_budget(data, count):
    if count > MAX_DIRECT_TARGETS:
        raise ValueError('native body distinct-call target budget')
    # The independently bounded function reader has validated these commands.
    # Each target performs at most three full symbol scans and one function-
    # start payload scan. Payload bytes conservatively bound start entries.
    cursor = 32
    symbols, start_bytes = None, None
    for _ in range(struct.unpack_from('<I', data, 16)[0]):
        command, size = struct.unpack_from('<II', data, cursor)
        if command == 2:
            symbols = struct.unpack_from('<I', data, cursor + 12)[0]
        elif command == 0x26:
            start_bytes = struct.unpack_from('<I', data, cursor + 12)[0]
        cursor += size
    if symbols is None or start_bytes is None:
        raise ValueError('native body lacks metadata-work bound')
    if count * (symbols * 3 + start_bytes) > MAX_TARGET_METADATA_VISITS:
        raise ValueError('native body target metadata-work budget')


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
    branches = branch_inventory(function.body, function.entry)
    targets = sorted({row['target'] for row in branches if row['kind'] == 'call'})
    _target_budget(data, len(targets))
    identities = []
    for target in targets:
        identity = read_native_function_at(data, SYMBOL, target)
        if identity is not None:
            helper = identity.function
            identities.append({
                'target': target, 'kind': 'same-file-text-symbols', 'names': identity.names,
                'entry': helper.entry, 'end': helper.end, 'body_bytes': len(helper.body),
                'body_sha256': hashlib.sha256(helper.body).hexdigest(),
                'known_branch_encodings': branch_inventory(helper.body, helper.entry),
                'semantics_verified': False,
            })
            continue
        imported = read_native_import(data, SYMBOL, target)
        if imported is not None:
            identities.append({
                'target': target, 'kind': 'declared-import-stub',
                'symbol': imported.symbol, 'library_ordinal': imported.library_ordinal,
                'slot': imported.slot,
                'stub_body_sha256': hashlib.sha256(imported.stub_body).hexdigest(),
                'live_binding_verified': False, 'semantics_verified': False,
            })
        else:
            identities.append({'target': target, 'kind': 'unresolved-direct-target',
                               'semantics_verified': False})
    return {
        'image_sha256': hashlib.sha256(data).hexdigest(),
        'coordinate_system': 'file Mach-O VM addresses before slide',
        'symbol': SYMBOL, 'entry': function.entry, 'end': function.end,
        'body_sha256': hashlib.sha256(function.body).hexdigest(),
        'body_bytes': len(function.body),
        'known_branch_encodings': branches,
        'direct_call_target_identities': identities,
        'direct_call_declared_identity_coverage': all(
            row['kind'] != 'unresolved-direct-target' for row in identities),
        'indirect_call_targets_verified': False,
        'symbol_to_next_start_extent_verified': True,
        'exclusive_instruction_provenance_verified': False,
        'complete_control_flow_verified': False,
        'memory_effects_verified': False, 'transitive_calls_verified': False,
        'exception_edges_verified': False, 'execution_claim': False,
        'native_memory_purity_verified': False, 'consumer_proof': False,
        'scope': 'bounded main/helper bytes and direct-call declarations; partial branch inventory, no transitive effects',
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
