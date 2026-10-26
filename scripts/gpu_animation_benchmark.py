#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Controlled OpenJDK page-composition benchmark; not a GPU-time or APK benchmark."""
from pathlib import Path
import os
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/gpu-animation-benchmark'
work.mkdir(exist_ok=True)
subprocess.run(['javac', '--release', '8', '-cp', str(ROOT/'build/framework/classes'), '-d', str(work),
                str(ROOT/'tests/java/shiraz/GpuAnimationBenchmark.java')], check=True)
for backend in ('gpu', 'software'):
    env = dict(os.environ, SDL_VIDEODRIVER='offscreen', SHIRAZ_CANVAS=backend)
    for key in ('SHIRAZ_PRESENT_SNAPSHOT', 'SHIRAZ_LIVE_SNAPSHOT', 'SHIRAZ_INPUT_SCRIPT', 'SHIRAZ_LIVE_INPUT'):
        env.pop(key, None)
    result = subprocess.run(['java', '-Djava.library.path='+str(ROOT/'build'), '-cp', str(work)+':'+str(ROOT/'build/framework/classes'),
                             'shiraz.GpuAnimationBenchmark'] + (["controls"] if os.environ.get('SHIRAZ_BENCH_CONTROLS') == '1' else []), env=env, capture_output=True, text=True, timeout=90)
    (work/(backend+'.log')).write_text(result.stdout+result.stderr)
    if result.returncode:
        raise SystemExit('Benchmark failed: '+backend)
    print(backend, result.stdout.strip())
