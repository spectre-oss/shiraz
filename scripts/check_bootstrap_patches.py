#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Replay registered patches from pinned Git blobs without editing dependencies."""
import argparse
import hashlib
import platform
import subprocess
import tempfile
from pathlib import Path

import bootstrap_runtime as bootstrap


def replay(directory, revision, recipes, patch_root, work):
    bootstrap.validate_patch_recipes(recipes)
    names = {name for _, files in recipes for name in files}
    with tempfile.TemporaryDirectory(prefix='patch-replay-', dir=work) as temporary:
        tree = Path(temporary)
        subprocess.run(['git', 'init', '-q', str(tree)], check=True)
        for name in sorted(names):
            entry = subprocess.check_output(['git', 'ls-tree', '-z', revision, '--', name], cwd=directory)
            if not entry:
                continue  # A registered patch creates this path.
            mode, kind, _ = entry.split(b'\t', 1)[0].split()
            if kind != b'blob' or mode not in {b'100644', b'100755'}:
                raise ValueError(f'unsupported pinned patch input: {name}')
            data = subprocess.check_output(['git', 'show', f'{revision}:{name}'], cwd=directory)
            target = tree / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            target.chmod(0o755 if mode == b'100755' else 0o644)
        for patch, files in recipes:
            source = patch_root / patch
            subprocess.run(['git', 'apply', '--check', str(source)], cwd=tree, check=True)
            subprocess.run(['git', 'apply', str(source)], cwd=tree, check=True)
            for name, expected in files.items():
                target = tree / name
                if target.is_symlink():
                    raise ValueError(f'unsupported symlink patch result: {patch}: {name}')
                actual = hashlib.sha256(target.read_bytes()).hexdigest() if target.is_file() else None
                if actual != expected:
                    raise ValueError(f'patch postimage differs from recipe: {patch}: {name}')
    return len(recipes), len(names)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', choices=('Linux', 'Darwin'), default=platform.system())
    args = parser.parse_args()
    work = bootstrap.ROOT / 'build'
    work.mkdir(exist_ok=True)
    try:
        for name, (_, revision) in bootstrap.SOURCES.items():
            recipes = bootstrap.PATCHES.get(name, [])
            if args.host == 'Darwin':
                recipes = recipes + bootstrap.PATCHES_DARWIN.get(name, [])
            if not recipes:
                continue
            count, files = replay(bootstrap.ROOT / 'context' / name, revision, recipes,
                                  bootstrap.ROOT / 'patches', work)
            print(f'BOOTSTRAP_PATCH_REPLAY_PASS dependency={name} host={args.host} patches={count} files={files}')
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'bootstrap patch replay failed: {error}\n')


if __name__ == '__main__':
    main()
