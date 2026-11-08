#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inventory the pinned ART JNI table and emit local adapter registrations."""

import argparse
import hashlib
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('header', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    source = args.header.read_text()
    body = source.split('struct JNINativeInterface {', 1)[1].split('\n};', 1)[0]
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.DOTALL)
    fields = re.findall(r'\(\*(\w+)\)\s*\((.*?)\);', body, re.DOTALL)
    if len(fields) != 229 or len({name for name, _ in fields}) != 229:
        raise ValueError('unexpected pinned JNI table layout')
    lines = [
        '// Generated from ART jni.h; do not edit. Output stays local.',
        '// API declarations originate in AOSP, Apache-2.0; see source header.',
        'static_assert(sizeof(JNINativeInterface) == 233 * sizeof(void*));',
    ]
    for index, (name, params) in enumerate(fields, 4):
        lines.append(f'static_assert(offsetof(JNINativeInterface, {name}) == {index} * sizeof(void*));')
        call = re.fullmatch(r'(NewObject|Call(Nonvirtual|Static)?(Object|Boolean|Byte|Char|Short|Int|Long|Float|Double|Void)Method)(V?)', name)
        if call:
            base, family, kind, variant = call.groups()
            family = 'Constructor' if base == 'NewObject' else family or 'Instance'
            kind = kind or 'Object'
            variant = 'List' if variant == 'V' else 'Ellipsis'
            lines.append(f'SHIRAZ_ENV_CALL({name}, {base}A, {family}, {kind}, {variant})')
        else:
            if '...' in params:
                raise ValueError(f'unhandled variadic JNI slot: {name}')
            lines.append(f'SHIRAZ_ENV_SLOT({name})')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text('\n'.join(lines) + '\n')
    args.output.with_suffix('.json').write_text(json.dumps({
        'header_sha256': hashlib.sha256(args.header.read_bytes()).hexdigest(),
        'slots': [name for name, _ in fields], 'reserved': 4,
    }, indent=2) + '\n')
    print(f'Guest JNI inventory: {len(fields)} callable slots, four reserved slots')


if __name__ == '__main__':
    main()
