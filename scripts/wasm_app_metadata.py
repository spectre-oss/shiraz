#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate validated launch metadata for the Shiraz Go/WASM assembler."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'src'))
sys.path.insert(0, str(ROOT/'scripts'))
from shiraz.apk import inspect_apk
from shiraz.runtime import application_metadata, properties_text
from build_appimage import validate_report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    report = inspect_apk(args.apk)
    package, application, activity = validate_report(report, 'x86_64')
    facts = application_metadata(report['manifest'], Path('/unused.apk'), Path('/unused-data'))
    facts.pop('sourceDir')
    facts.pop('dataDir')
    metadata = {'package': package, 'application': application, 'activity': activity,
                'version': (ROOT/'VERSION').read_text().strip(),
                'application_properties': properties_text(facts)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(metadata, separators=(',', ':'))+'\n')


if __name__ == '__main__':
    main()
