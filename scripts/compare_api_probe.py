#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compare exported API-probe reports without treating skips as compatibility."""
import argparse
import json
import math
from pathlib import Path

STATUSES = {'PASS', 'FAIL', 'UNSUPPORTED', 'SKIPPED', 'TIMEOUT'}
LIMIT = 2 * 1024 * 1024


def load_report(path):
    with Path(path).open('rb') as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise ValueError('report exceeds 2 MiB')
    report = json.loads(data)
    if not isinstance(report, dict):
        raise ValueError('report must be an object')  # noqa: TRY004 - invalid document schema
    if report.get('schemaVersion') != 1 or not isinstance(report.get('results'), dict):
        raise ValueError('unsupported report schema')
    for key, result in report['results'].items():
        if not isinstance(result, dict) or key != result.get('id') or result.get('status') not in STATUSES:
            raise ValueError('invalid result identity or status')
        duration = result.get('durationMs')
        if isinstance(duration, bool) or not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration < 0:
            raise ValueError('invalid duration')
        if result.get('mode') not in ('automatic', 'manual') or not isinstance(result.get('expected'), str):
            raise ValueError('invalid test metadata')
    return report


def compare(baseline, candidate):
    rows = []
    left, right = baseline['results'], candidate['results']
    for test_id in sorted(left.keys() | right.keys()):
        a, b = left.get(test_id), right.get(test_id)
        state = 'uncompared'
        if a and not b and a['mode'] == 'automatic' and a['status'] == 'PASS':
            state = 'coverage_lost'
        if a and b:
            if (a['expected'] != b['expected'] or a['mode'] != b['mode']
                    or (baseline.get('suiteFingerprint') and candidate.get('suiteFingerprint')
                        and baseline['suiteFingerprint'] != candidate['suiteFingerprint'])):
                state = 'definition_changed'
            elif a['mode'] == 'manual':
                state = 'manual_observation'
            elif a['status'] == 'PASS' and b['status'] in {'FAIL', 'UNSUPPORTED', 'TIMEOUT'}:
                state = 'regression'
            elif a['status'] == 'PASS' and b['status'] == 'SKIPPED':
                state = 'coverage_lost'
            elif b['status'] == 'PASS' and a['status'] in {'FAIL', 'UNSUPPORTED', 'TIMEOUT'}:
                state = 'improvement'
            elif a['status'] == b['status'] == 'PASS':
                state = 'both_pass'
            else:
                state = 'unresolved'
        rows.append({'id': test_id, 'baseline': a['status'] if a else 'NOT_RUN',
                     'candidate': b['status'] if b else 'NOT_RUN', 'comparison': state})
    return {'baselineVersion': baseline.get('suiteVersion'), 'candidateVersion': candidate.get('suiteVersion'),
            'note': 'Only recorded assertions are compared; this is not whole-platform compatibility.', 'cases': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    args = parser.parse_args()
    result = compare(load_report(args.baseline), load_report(args.candidate))
    print(json.dumps(result, indent=2))
    return int(any(row['comparison'] in ('regression', 'coverage_lost') for row in result['cases']))


if __name__ == '__main__':
    raise SystemExit(main())
