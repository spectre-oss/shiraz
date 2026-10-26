#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Prepare the version before Git snapshots the index; never stage other files."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--message', default=os.environ.get('MSG'))
    args = parser.parse_args()
    if not args.message:
        parser.error('pass --message or make commit MSG=...')
    with tempfile.TemporaryDirectory(prefix='shiraz-commit-') as directory:
        message = Path(directory) / 'message'
        message.write_text(args.message + '\n')
        subprocess.run(['python3', 'scripts/version.py', '--message-file', str(message), '--prepare'], check=True)
        subprocess.run(['git', 'commit', '--file', str(message)], check=True)


if __name__ == '__main__':
    main()
