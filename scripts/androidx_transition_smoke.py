#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise actual AndroidX fade/bounds transitions from a locally supplied APK on ART."""
import argparse
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk',type=Path,required=True)
a=p.parse_args();apk=a.apk.resolve()
if not apk.is_file():p.error('APK is missing; supply a local APK containing AndroidX transitions')
work=ROOT/'build/androidx-transition-smoke';work.mkdir(parents=True,exist_ok=True)
lock=(work/'.lock').open('a');fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
(work/'result.json').unlink(missing_ok=True)
classes=work/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
subprocess.run(['javac','--release','8','-cp',str(ROOT/'build/framework/classes'),'-d',str(classes),str(ROOT/'tests/java/AndroidxTransitionSmoke.java')],check=True)
env=environment(work)
dex=work/'classes.dex'
subprocess.run([str(HOST/'bin/dx'),'--dex','--min-sdk-version=27','--output='+str(dex),str(classes)],env=env,check=True)
jar=work/'smoke.jar'
with zipfile.ZipFile(jar,'w') as z:
 entry=zipfile.ZipInfo('classes.dex');padding=(-(30+len(entry.filename)+4))%4
 entry.extra=struct.pack('<HH',0xffff,padding)+b'\0'*padding;z.writestr(entry,dex.read_bytes())
vm=HOST/'bin/dalvikvm64'
if not vm.is_file():vm=HOST/'bin/dalvikvm'
command=[str(vm),'-Xbootclasspath:'+':'.join(str(HOST/'framework'/name) for name in JARS),'-Xnoimage-dex2oat','-Xcompiler:'+str(HOST/'bin/dex2oat'),'-Xcheck:jni','-Xusejit:false','-Xint','-Djava.library.path='+str(ROOT/'build'),'-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'),'-cp',':'.join(map(str,(ROOT/'build/shiraz-framework.jar',apk,jar))),'AndroidxTransitionSmoke']
r=subprocess.run(command,env=env,capture_output=True,text=True,timeout=30)
(work/'stdout.log').write_text(r.stdout);(work/'stderr.log').write_text(r.stderr)
marker='ANDROIDX_FADE_BOUNDS_TRANSITIONS_OK'
if r.returncode or marker not in r.stdout.splitlines():raise SystemExit('AndroidX transition test failed; inspect '+str(work))
(work/'result.json').write_text(json.dumps({'runtime':'art','apk_sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(),'marker':marker,'scope':'APK AndroidX library transitions; does not launch the full application'},indent=2)+'\n')
print(marker)
