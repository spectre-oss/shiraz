#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise stock-SDK PNG/resource decoding and native bounds/lifetime failures on OpenJDK."""
import fcntl
import os
from pathlib import Path
import shutil
import subprocess
ROOT=Path(__file__).resolve().parents[1]
work=ROOT/'build/png-smoke'
work.mkdir(exist_ok=True)
lock=(work/'.lock').open('a')
try:
    fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('PNG smoke is already running')
classes=work/'classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir()
framework=ROOT/'build/framework/classes'
core=ROOT/'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
subprocess.run(['javac','--release','8','-cp',str(ROOT/'.deps/android-sdk/android.jar'),'-d',str(classes),str(ROOT/'tests/apk/src/org/shiraz/fixture/PngFixture.java')],check=True)
classpath=':'.join(map(str,(classes,framework,core)))
subprocess.run(['javac','--release','8','-cp',classpath,'-d',str(classes),str(ROOT/'tests/java/PngSmoke.java'),str(ROOT/'tests/java/ResourceLifetime.java')],check=True)
result=subprocess.run(['java','-Xcheck:jni','-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'),'-Djava.library.path='+str(ROOT/'build'),'-cp',classpath,'PngSmoke',str(ROOT/'build/apk-fixture/fixture.apk')],env={**os.environ,'LD_LIBRARY_PATH':str(ROOT/'context/art_standalone/out/host/linux-x86/lib64')+':'+str(ROOT/'.local/lib')},capture_output=True,text=True,timeout=30)
(work/'stdout.log').write_text(result.stdout)
(work/'stderr.log').write_text(result.stderr)
if result.returncode or 'PNG_NATIVE_ARGUMENTS_AND_LIFETIME_OK' not in result.stdout:
    raise SystemExit('PNG smoke failed; inspect build/png-smoke/stderr.log')
print('PNG_OPENJDK_OK')
