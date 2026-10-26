#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Commit staged source with a Conventional Commit message; CI owns VERSION."""
import argparse
import os
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--message', default=os.environ.get('MSG'))
    args = parser.parse_args()
    if not args.message:
        parser.error('pass --message or make commit MSG=...')
    subprocess.run(['git', 'commit', '-m', args.message], check=True)


if __name__ == '__main__':
    main()
