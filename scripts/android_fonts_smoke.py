#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise process-scoped Android font stdio mapping and rejected resource targets."""
from pathlib import Path
import json
import os
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/android-fonts-smoke'
work.mkdir(exist_ok=True)
(work / 'result.json').unlink(missing_ok=True)
with tempfile.TemporaryDirectory(prefix='files-', dir=work) as temporary:
    directory = Path(temporary)
    for name in ('android-fonts.xml', 'NotoSans-Regular.ttf', 'Roboto-Regular.ttf', 'DroidSansMono.ttf', 'NotoSerif-Regular.ttf', 'NotoNaskhArabic-Regular.ttf', 'unrelated'):
        (directory / name).write_bytes(b'FONT fixture\n')
    (directory / 'NotoSans-Bold.ttf').symlink_to(directory / 'unrelated')
    (directory / 'NotoSansArabic-Regular.ttf').mkdir()
    os.mkfifo(directory / 'NotoSansHebrew-Regular.ttf')
    with (directory / 'NotoEmoji[wght].ttf').open('wb') as out:
        out.truncate(64 * 1024 * 1024 + 1)
    env = dict(os.environ, SHIRAZ_ANDROID_FONT_DIR=str(directory))
    env.pop('LD_PRELOAD', None)
    result = subprocess.run(['/lib64/ld-linux-x86-64.so.2', '--preload',
        str(ROOT / 'build/libshiraz-android-fonts.so'), str(ROOT / 'build/test-android-font-files'),
        str(directory / 'unrelated')], env=env, capture_output=True, text=True, timeout=10)
    (work / 'stdout.log').write_text(result.stdout)
    (work / 'stderr.log').write_text(result.stderr)
    marker = 'ANDROID_FONT_PATHS_READONLY_BOUNDS_OK'
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('Android font file smoke failed; inspect build/android-fonts-smoke')
    if (directory / 'NotoSans-Regular.ttf').read_bytes() != b'FONT fixture\n':
        raise SystemExit('Font resource was modified')
(work / 'result.json').write_text(json.dumps({'marker': marker, 'exit': result.returncode}, indent=2) + '\n')
print(marker)
