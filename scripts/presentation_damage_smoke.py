#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import os
from pathlib import Path
import re
import subprocess
ROOT=Path(__file__).resolve().parents[1]
work=ROOT/'build/presentation-damage-smoke';work.mkdir(exist_ok=True)
for tracked in (True,False):
    mode='tracked' if tracked else 'untracked'
    result=subprocess.run([str(ROOT/'build/test-presentation-damage'),*([] if tracked else ['untracked'])],env=dict(os.environ,SDL_VIDEODRIVER='dummy',SDL_RENDER_DRIVER='software',SHIRAZ_TRACE_PRESENT='1'),capture_output=True,text=True,timeout=10)
    (work/(mode+'.log')).write_text(result.stdout+result.stderr)
    match=re.search(r'SHIRAZ_WINDOW_PRESENT presents=(\d+) unchanged=(\d+)',result.stderr)
    assert result.returncode==0 and 'PRESENTATION_EXPOSE_RESIZE_PIXELS_OK' in result.stdout and match,result.stderr
    presents,skipped=map(int,match.groups())
    assert (6<=presents<=8 and skipped>=22) if tracked else (presents==30 and skipped==0),(mode,presents,skipped)
    uploads=re.search(r'SHIRAZ_TEXTURE_UPLOAD uploads=(\d+)',result.stderr)
    assert uploads and int(uploads[1])>=4
    print(mode,presents,skipped)
