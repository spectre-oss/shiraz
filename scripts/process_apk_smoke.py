#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise fatal/successful exit through an unchanged stock-SDK APK on ART."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import zipfile
from art_smoke import HOST, environment
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/process-apk-smoke'
classes = work / 'classes'
classes.mkdir(parents=True, exist_ok=True)
sdk = ROOT / '.deps/android-sdk'
subprocess.run(['javac', '--release', '8', '-cp', str(sdk/'android.jar'), '-d', str(classes), str(ROOT/'tests/process-apk/ProcessActivity.java')], check=True)
dex = work/'classes.dex'
subprocess.run([str(HOST/'bin/dx'), '--dex', '--min-sdk-version=27', '--output='+str(dex), str(classes)], env=environment(work), check=True)
apk = work/'fixture.apk'
subprocess.run([str(sdk/'aapt2'), 'link', '-o', str(apk), '--manifest', str(ROOT/'tests/process-apk/AndroidManifest.xml'), '-I', str(sdk/'android.jar')], check=True)
with zipfile.ZipFile(apk, 'a') as archive:
    entry=zipfile.ZipInfo('classes.dex')
    padding=(-(archive.fp.tell()+30+len(entry.filename)+4))%4
    entry.extra=struct.pack('<HH',0xffff,padding)+b'\0'*padding
    archive.writestr(entry,dex.read_bytes())
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
for failure in (False, True):
    name='failure' if failure else 'success'
    env=dict(os.environ, PYTHONPATH=str(ROOT/'src'), SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software', SHIRAZ_AUDIO_DEVICE='null', SHIRAZ_PROCESS_TEST_FAIL='1' if failure else '0')
    result=subprocess.run([sys.executable,'-m','shiraz','run',str(apk),'--width','96','--height','160','--frames','3','--timeout','15'],cwd=ROOT,env=env,capture_output=True,text=True,timeout=20)
    run=ROOT/'build/runs'/digest
    evidence=json.loads((run/'result.json').read_text())
    stdout=(run/'stdout.log').read_text();stderr=(run/'stderr.log').read_text()
    (work/(name+'-stdout.log')).write_text(stdout);(work/(name+'-stderr.log')).write_text(stderr)
    assert result.returncode==(1 if failure else 0), result.stderr
    assert evidence['exit_status']==(10 if failure else 0) and evidence['apk_unchanged'], evidence
    assert 'PROCESS_WORKER_STARTED' in stdout and 'PROCESS_ACTIVITY_DESTROYED' in stdout
    assert 'PROCESS_APP_HANDLER_RETURNED' not in stderr
    assert ('SHIRAZ_RUNTIME_CLEANUP_COMPLETE' in stdout)==(not failure)
    if failure: assert 'SHIRAZ_RUNTIME_FATAL' in stderr and 'java.lang.IllegalStateException: PROCESS_CALLBACK_FAILURE' in stderr
    print('PROCESS_APK_EXIT_OK '+name)
assert hashlib.sha256(apk.read_bytes()).hexdigest()==digest
(work/'result.json').write_text(json.dumps({'success_exit':0,'fatal_exit':10,'apk_unchanged':True,'scope':'minimal APK lifecycle and fatal callback, not OsmAnd acceptance'},indent=2)+'\n')
