#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fetch hash-pinned, unmodified real app APKs into ignored context/apks/."""
import json
import sys
from pathlib import Path

from build_apk_fixture import fetch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.apk import inspect_apk


def main():
    catalog = json.loads((ROOT / 'config/compatibility-apks.json').read_text())
    output = ROOT / 'context/apks'
    output.mkdir(parents=True, exist_ok=True)
    receipts = []
    for entry in catalog['apks']:
        filename = entry['file']
        if Path(filename).name != filename or not filename.endswith('.apk'):
            raise ValueError('APK catalog filename must be a simple .apk basename')
        path = output / filename
        fetch(path, entry['url'], entry['sha256'], entry['maxBytes'])
        report = inspect_apk(path)
        manifest = report['manifest']
        if manifest['package'] != entry['package'] or str(manifest['version_code']) != str(entry['versionCode']):
            raise ValueError(f'APK identity mismatch: {filename}')
        if path.stat().st_size != entry['bytes']:
            raise ValueError(f'APK length mismatch: {filename}')
        receipts.append({**entry, 'nativeAbis': report['native_abis'],
                         'verification': 'SHA-256, size and package/version checked; signer trust not independently verified',
                         'compatibilityVerified': False})
        print(f"{entry['name']} {entry['version']}: {path.relative_to(ROOT)} (hash/identity checked)")
    (output / 'receipts.json').write_text(json.dumps(receipts, indent=2) + '\n')


if __name__ == '__main__':
    main()
