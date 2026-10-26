#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Measure a 720x1280 Canvas color/antialiased rectangle pair under ART."""
import argparse
import fcntl
import hashlib
import json
import subprocess
from art_smoke import ROOT, HOST, JARS, environment
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--label',choices=['before','after'],default='after')
args=parser.parse_args()
work=ROOT/'build/fill-benchmark'
work.mkdir(exist_ok=True)
lock=(work/'.lock').open('a');fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
classes=work/'classes';classes.mkdir(exist_ok=True)
framework=ROOT/'build/shiraz-framework.jar'
(work/(args.label+'.json')).unlink(missing_ok=True)
subprocess.run(['javac','--release','8','-cp',str(ROOT/'build/framework/classes'),'-d',str(classes),str(ROOT/'tests/java/FillBenchmark.java')],check=True)
jar=work/'benchmark.jar'
subprocess.run([str(HOST/'bin/dx'),'--dex','--min-sdk-version=27','--output='+str(jar),str(classes)],env=environment(work),check=True)
vm=HOST/'bin/dalvikvm64'
if not vm.exists():vm=HOST/'bin/dalvikvm'
command=[str(vm),'-Xbootclasspath:'+':'.join(str(HOST/'framework'/name) for name in JARS),'-Xint','-Xusejit:false','-Xnoimage-dex2oat','-Xcompiler:'+str(HOST/'bin/dex2oat'),'-Djava.library.path='+str(ROOT/'build'),'-cp',str(framework)+':'+str(jar),'FillBenchmark']
result=subprocess.run(command,env=environment(work),capture_output=True,text=True,timeout=120)
(work/(args.label+'-stdout.log')).write_text(result.stdout);(work/(args.label+'-stderr.log')).write_text(result.stderr)
if result.returncode:raise SystemExit('Fill benchmark failed; inspect build/fill-benchmark logs')
values=dict(line.split('=',1) for line in result.stdout.splitlines() if line.startswith('FILL_'))
if int(values['FILL_PAIR_MEDIAN_NS'])<=0:raise SystemExit('Invalid timing')
report={'runtime':'art-interpreter','size':[720,1280],'warmups':2,'samples':5,'component_only':True,'framework_sha256':hashlib.sha256(framework.read_bytes()).hexdigest(),'native_bitmap_sha256':hashlib.sha256((ROOT/'build/libshiraz-bitmap.so').read_bytes()).hexdigest(),**values}
(work/(args.label+'.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
