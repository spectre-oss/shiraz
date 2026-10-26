#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Commit a Conventional Commit version bump during a main-branch workflow."""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
from urllib.request import Request, urlopen

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


def signed_version_commit(version, head):
    token = os.environ['GITHUB_TOKEN']
    repository = os.environ['GITHUB_REPOSITORY']
    query = ('mutation($input: CreateCommitOnBranchInput!) {'
             ' createCommitOnBranch(input: $input) { commit { oid } } }')
    payload = {
        'query': query,
        'variables': {'input': {
            'branch': {'repositoryNameWithOwner': repository, 'branchName': 'main'},
            'expectedHeadOid': head,
            'message': {'headline': f'chore(release): bump version to {version}'},
            'fileChanges': {'additions': [{
                'path': 'VERSION',
                'contents': base64.b64encode((version + '\n').encode()).decode(),
            }]},
        }},
    }
    request = Request('https://api.github.com/graphql',
                      data=json.dumps(payload).encode(),
                      headers={'Authorization': f'Bearer {token}',
                               'Content-Type': 'application/json',
                               'Accept': 'application/vnd.github+json'})
    with urlopen(request, timeout=30) as response:
        result = json.load(response)
    if result.get('errors') or not result.get('data', {}).get('createCommitOnBranch'):
        raise RuntimeError(f'GitHub signed version commit failed: {result.get("errors", result)}')
    return result['data']['createCommitOnBranch']['commit']['oid']


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
        sha = signed_version_commit(version, run('rev-parse', 'HEAD'))
    else:
        sha = run('rev-parse', 'HEAD')
    if output := os.environ.get('GITHUB_OUTPUT'):
        with Path(output).open('a') as stream:
            stream.write(f'version={version}\nsha={sha}\n')
    print(f'Build version {version} at {sha}')


if __name__ == '__main__':
    main()
