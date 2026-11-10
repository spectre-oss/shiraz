#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check one first-party compiled MRS accessor; never grant TLS-use exemptions."""
import argparse
import dataclasses
import hashlib
import json
from pathlib import Path

from guest_cached_self_record import MAX_BYTES, _MachO
from guest_mrs_access import BODY_BYTES, recognize_mrs_access


def check_accessor(path):
    if not 32 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('MRS accessor image size bound')
    with path.open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    image = _MachO(data, symbol='_shiraz_guest_tls_access', body_bytes=BODY_BYTES)
    effects = recognize_mrs_access(image.read_word, image.entry, image.readable_data)
    if effects is None:
        raise ValueError('unsupported compiled MRS accessor profile')
    return {'image_sha256': hashlib.sha256(data).hexdigest(), 'effects': dataclasses.asdict(effects),
            'execution_claim': False, 'consumer_proof': False,
            'scope': 'exact compiled body; normal in-body RET paths only; no failure-handler or lifetime certificate'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check_accessor(args.image)
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('M5_MRS_ACCESSOR_CODE_PASS body_sha256=' + result['effects']['body_sha256'] +
          ' calls_on_normal_in_body_return=0 execution_claim=0 consumer_proof=0')


if __name__ == '__main__':
    main()
