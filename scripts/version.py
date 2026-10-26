#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Version the index from the final Conventional Commit message, relative to HEAD."""
import argparse
import re
import subprocess
from pathlib import Path


def bump(current, message):
    match = re.match(r'^(feat|fix|perf|docs|build|refactor|chore|test|style|ci)(\([^\r\n()]+\))?(!)?: [^\r\n]+', message)
    if not match:
        raise ValueError('Conventional Commit required: feat(scope): summary')
    major, minor, patch = map(int, current.strip().split('.'))
    if match[3] or re.search(r'^BREAKING[ -]CHANGE: ', message, re.M):
        return f'{major + 1}.0.0'
    if match[1] == 'feat':
        return f'{major}.{minor + 1}.0'
    if match[1] in ('fix', 'perf'):
        return f'{major}.{minor}.{patch + 1}'
    return f'{major}.{minor}.{patch}'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--message-file', type=Path)
    parser.add_argument('--prepare', action='store_true')
    args = parser.parse_args()
    current = Path('VERSION').read_text().strip()
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', current):
        parser.error('VERSION must contain a semantic version')
    if args.message_file:
        result = subprocess.run(['git', 'show', 'HEAD:VERSION'], capture_output=True, text=True)
        base = result.stdout.strip() if result.returncode == 0 else '0.0.0'
        try:
            version = bump(base, args.message_file.read_text())
        except ValueError as exc:
            parser.error(str(exc))
        staged = subprocess.run(['git', 'show', ':VERSION'], capture_output=True, text=True).stdout.strip()
        if not args.prepare and staged == version:
            return
        Path('VERSION').write_text(version + '\n')
        subprocess.run(['git', 'add', '--', 'VERSION'], check=True)
        print('Shiraz version:', version)
        if not args.prepare:
            raise SystemExit('VERSION was staged for this message. Retry the commit, or use make commit MSG=... to prepare it first.')


if __name__ == '__main__':
    main()
