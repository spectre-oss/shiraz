#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check that macOS-only edits leave the Linux build path unchanged.

macOS support is added inside `#if defined(__APPLE__)` blocks. For every C/C++
source or header that differs from the base revision, the text left after
removing those blocks with `unifdef -U__APPLE__` must match the base revision
byte for byte. New files are reported but not compared.
"""
import argparse
import subprocess
import sys

SUFFIXES = ('.c', '.cc', '.cpp', '.h', '.hpp', '.inc', '.S', '.s', '.m', '.mm')


def git(*args, check=True):
    return subprocess.run(['git', *args], check=check, capture_output=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='main', help='revision holding the Linux baseline (default: main)')
    args = parser.parse_args()
    names = git('diff', '--name-only', '--diff-filter=AM', args.base, '--').decode().split()
    names += git('ls-files', '--others', '--exclude-standard').decode().split()
    failures = 0
    for name in sorted(set(n for n in names if n.endswith(SUFFIXES))):
        base = subprocess.run(['git', 'show', f'{args.base}:{name}'], capture_output=True)
        if base.returncode:
            print(f'new      {name}')
            continue
        # unifdef exits 1 when it changed the input; only 2 is an error.
        stripped = subprocess.run(['unifdef', '-U__APPLE__', name], capture_output=True)
        if stripped.returncode not in (0, 1):
            print(f'error    {name}: {stripped.stderr.decode().strip()}')
            failures += 1
        elif stripped.stdout == base.stdout:
            print(f'same     {name}')
        else:
            print(f'CHANGED  {name}: Linux path differs from {args.base}')
            failures += 1
    if failures:
        print(f'{failures} file(s) change the Linux path')
    sys.exit(1 if failures else 0)


if __name__ == '__main__':
    main()
