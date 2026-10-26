#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Commit a Conventional Commit version bump during a main-branch workflow."""
import argparse
import os
from pathlib import Path
import subprocess

from version import bump


def run(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def planned_version(previous, current, message):
    if previous is None or message.startswith('chore(release): bump version to '):
        return current, False
    expected = bump(previous, message)
    if current == expected:
        return current, False  # Source commit already carried the expected version.
    if current != previous:
        raise ValueError(f'VERSION {current} differs from both previous {previous} and expected {expected}')
    return expected, True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    current = Path('VERSION').read_text().strip()
    message = run('log', '-1', '--format=%B')
    parent = subprocess.run(['git', 'rev-parse', 'HEAD^'], capture_output=True, text=True)
    previous = run('show', 'HEAD^:VERSION') if parent.returncode == 0 else None
    version, needs_commit = planned_version(previous, current, message)
    if needs_commit:
        if not args.apply:
            print(f'Would commit VERSION {version}')
            return
        Path('VERSION').write_text(version + '\n')
        subprocess.run(['git', 'add', '--', 'VERSION'], check=True)
        subprocess.run(['git', '-c', 'user.name=github-actions[bot]',
                        '-c', 'user.email=41898282+github-actions[bot]@users.noreply.github.com',
                        'commit', '-m', f'chore(release): bump version to {version}'], check=True)
        subprocess.run(['git', 'push', 'origin', 'HEAD:main'], check=True)
    sha = run('rev-parse', 'HEAD')
    if output := os.environ.get('GITHUB_OUTPUT'):
        with Path(output).open('a') as stream:
            stream.write(f'version={version}\nsha={sha}\n')
    print(f'Build version {version} at {sha}')


if __name__ == '__main__':
    main()
