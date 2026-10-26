#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Real ART/SDL activity resizing, configuration lifecycle, clicks and final pixels."""
import hashlib
import os
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'src'))
from shiraz.apk import inspect_apk
from shiraz.runtime import run_activity
os.environ.update(SDL_VIDEODRIVER='dummy',SDL_RENDER_DRIVER='software',SHIRAZ_AUDIO_DEVICE='null')
for key in list(os.environ):
    if key.startswith('SHIRAZ_FIXTURE_'): del os.environ[key]
apk=ROOT/'build/apk-fixture/fixture.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
for suffix,kind in (('', 'recreated'),('$Handled','handled'),('$Requested','requested')):
    report=inspect_apk(apk)
    target='org.shiraz.fixture.ResizeActivity'+suffix
    # Select another activity declared in this unchanged test APK.
    report['manifest']['launcher_activities']=[{'name':target,'target':target}]
    snapshot=ROOT/('build/resize-'+kind+'.ppm')
    status=run_activity(apk,report,width=96,height=160,frames=40,timeout=30,snapshot=snapshot)
    output=(ROOT/'build/runs'/digest/'stdout.log').read_text()
    (ROOT/('build/resize-'+kind+'-stdout.log')).write_text(output)
    (ROOT/('build/resize-'+kind+'-stderr.log')).write_bytes((ROOT/'build/runs'/digest/'stderr.log').read_bytes())
    if status or 'SHIRAZ_RESIZE_ACTIVITY_OK '+kind not in output.splitlines():raise SystemExit('Resize activity failed: '+kind)
    if output.splitlines().count('SHIRAZ_FRAGMENT_RESTORED_OK') != (0 if kind == 'handled' else 2):raise SystemExit('Fragment recreation state missing: '+kind)
    if output.splitlines().count('SHIRAZ_FRAGMENT_CONFIGURATION_OK') != (2 if kind == 'handled' else 0):raise SystemExit('Fragment configuration dispatch differs: '+kind)
    for marker in ('SHIRAZ_ATTACH_LIVE_OK', 'SHIRAZ_DETACH_LIVE_OK'):
        if output.splitlines().count(marker) != (1 if kind == 'handled' else 3):raise SystemExit('Attach/detach lifecycle differs: '+kind)
    if output.splitlines().count('WINDOW_INSETS_DESKTOP_OK') != 3:raise SystemExit('Inset resize delivery differs: '+kind)
    expected=b'P6\n96 160\n255\n'+b'\0\0\xff'*(96*160)
    if snapshot.read_bytes()!=expected:raise SystemExit('Resized frame pixels/dimensions differ: '+kind)
    if hashlib.sha256(apk.read_bytes()).hexdigest()!=digest:raise SystemExit('APK changed')
    print('ACTIVITY_RESIZE_OK '+kind)
