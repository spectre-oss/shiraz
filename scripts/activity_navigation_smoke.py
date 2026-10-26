#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Real APK/ART/SDL activity launch, covered resize, Back and input integration."""
import hashlib
import os
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'src'))
from shiraz.apk import inspect_apk
from shiraz.runtime import run_activity
os.environ.update(SDL_VIDEODRIVER='dummy',SDL_RENDER_DRIVER='software',SHIRAZ_AUDIO_DEVICE='null')
apk=ROOT/'build/apk-fixture/fixture.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
for suffix in ('', '$Handled'):
    report=inspect_apk(apk)
    target='org.shiraz.fixture.NavigationActivity'+suffix
    report['manifest']['launcher_activities']=[{'name':target,'target':target}]
    snapshot=ROOT/('build/navigation'+suffix+'.ppm')
    status=run_activity(apk,report,width=96,height=160,frames=60,timeout=30,snapshot=snapshot)
    run=ROOT/'build/runs'/digest
    output=(run/'stdout.log').read_text()
    for stream in ('stdout','stderr'):
        (ROOT/('build/navigation'+suffix+'-'+stream+'.log')).write_bytes((run/(stream+'.log')).read_bytes())
    if status or 'NAVIGATION_RUNNER_OK' not in output.splitlines():
        raise SystemExit('Activity navigation failed: '+target)
    if snapshot.read_bytes()!=b'P6\n160 96\n255\n'+b'\0\0\xff'*(160*96):
        raise SystemExit('Returned activity pixels/geometry differ')
    print('ACTIVITY_NAVIGATION_OK '+target)
