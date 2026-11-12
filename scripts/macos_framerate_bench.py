#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run an APK windowed for a fixed time and report the presenter's frame statistics.

The default APK is the frame-rate fixture (build/macos-apk/framerate.apk). The run
closes through the live-input channel so the presenter prints its SHIRAZ_FRAME_STATS
summary; --min-fps turns the achieved present rate into a pass/fail check.
"""

import argparse
import os
import re
import subprocess
import sys
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', nargs='?', default=str(ROOT / 'build/macos-apk/framerate.apk'))
    parser.add_argument('--seconds', type=float, default=20)
    parser.add_argument('--min-fps', type=float, default=0)
    parser.add_argument('--out', default=str(ROOT / 'build/macos/framerate-bench'))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    live = out / 'input'
    live.write_text('')
    frames = out / 'frames.txt'
    env = {
        **os.environ,
        'PYTHONPATH': str(ROOT / 'src'),
        'SHIRAZ_FRAME_STATS': str(frames),
        'SHIRAZ_LIVE_INPUT': str(live),
    }
    env.pop('SHIRAZ_LIVE_SNAPSHOT', None)  # per-frame readback would bias the timing

    def close():
        with live.open('a') as handle:
            handle.write('close\n')

    timer = threading.Timer(args.seconds, close)
    timer.start()
    try:
        result = subprocess.run(
            [sys.executable, '-m', 'shiraz', 'run', '--timeout', str(args.seconds + 60), args.apk],
            env=env,
            capture_output=True,
            text=True,
            timeout=args.seconds + 120,
        )
    finally:
        timer.cancel()
    (out / 'run.log').write_text(result.stdout + result.stderr)
    summary = next(
        (
            line
            for line in (result.stdout + result.stderr).splitlines()
            if line.startswith('SHIRAZ_FRAME_STATS ')
        ),
        None,
    )
    if result.returncode or not summary:
        raise SystemExit('Frame-rate run failed; inspect ' + str(out / 'run.log'))
    print(summary)
    fps = float(re.search(r'present_fps=([0-9.]+)', summary).group(1))
    if fps < args.min_fps:
        raise SystemExit(f'present_fps={fps:.1f} below --min-fps {args.min_fps}')


if __name__ == '__main__':
    main()
