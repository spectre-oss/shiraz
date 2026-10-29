#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compare bounded MPEG decoding with and without duration metadata."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/audio-mpeg-smoke'
work.mkdir(exist_ok=True)
for name, header in [('estimated', '0'), ('indexed', '1')]:
    encoded = work / (name + '.mp3')
    subprocess.run([
        'ffmpeg', '-v', 'error', '-f', 'lavfi', '-i',
        'sine=frequency=440:sample_rate=44100:duration=1.8',
        '-c:a', 'libmp3lame', '-b:a', '128k', '-write_xing', header,
        '-y', str(encoded),
    ], check=True, timeout=20)
    reference = subprocess.run([
        'ffmpeg', '-v', 'error', '-i', str(encoded), '-f', 'f32le',
        '-acodec', 'pcm_f32le', '-ac', '1', '-',
    ], check=True, capture_output=True, timeout=20).stdout
    result = subprocess.run([str(ROOT / 'build/test-audio'), str(encoded)],
                            capture_output=True, text=True, timeout=20)
    (work / (name + '.log')).write_text(result.stdout + result.stderr)
    if result.returncode:
        raise SystemExit('MPEG decode failed: ' + name)
    match = re.search(r'AUDIO_FILE_DECODE frames=(\d+) channels=1 rate=44100 peak=([\d.]+)', result.stdout)
    if not match or int(match[1]) * 4 != len(reference) or not 0 < float(match[2]) <= 1:
        raise SystemExit('MPEG sample count or signal mismatch: ' + name)
    print('AUDIO_MPEG_EOF_OK', name, 'frames=' + match[1])
