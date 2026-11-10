#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise the real host DX method-handle section under concurrent interning."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dx-jar', type=Path, required=True)
    args = parser.parse_args()
    if not args.dx_jar.is_file():
        parser.error('build the pinned host dx.jar before running this check')
    jar = args.dx_jar.resolve()
    output = ROOT / 'build/test-dx-method-handles'
    output.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-cp', str(jar),
                        '-d', str(output), str(ROOT / 'tests/java/DxMethodHandlesRace.java')],
                       check=True, timeout=60)
        subprocess.run(['java', '-cp', f'{output}:{jar}', 'DxMethodHandlesRace'],
                       check=True, timeout=120)
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        parser.exit(1, f'DX method-handle concurrency check failed: {error}\n')


if __name__ == '__main__':
    main()
