#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Real APK relayout within size buckets and recreation across qualifier boundaries."""
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
    if key.startswith('SHIRAZ_FIXTURE_'):del os.environ[key]
apk=ROOT/'build/apk-fixture/fixture.apk';digest=hashlib.sha256(apk.read_bytes()).hexdigest()
report=inspect_apk(apk);target='org.shiraz.fixture.BucketResizeActivity'
report['manifest']['launcher_activities']=[{'name':target,'target':target}]
snapshot=ROOT/'build/bucket-resize.ppm'
status=run_activity(apk,report,width=96,height=160,frames=60,timeout=30,snapshot=snapshot)
work=ROOT/'build/runs'/report['apk_sha256'];output=(work/'stdout.log').read_text()
if status or 'BUCKET_RESIZE_RELAYOUT_RECREATION_AND_INPUT_OK' not in output.splitlines():raise SystemExit('Bucket resize activity failed; inspect '+str(work))
expected=b'P6\n119 167\n255\n'+b'\0\0\xff'*(119*167)
if snapshot.read_bytes()!=expected:raise SystemExit('Final bucket-resize geometry/pixels differ')
if hashlib.sha256(apk.read_bytes()).hexdigest()!=digest:raise SystemExit('Fixture APK changed')
print('BUCKET_RESIZE_APK_OK')
