#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise the actual patched libartbase with exhausted MAP_32BIT address space."""
from pathlib import Path
import os
import subprocess
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'context/art_standalone'
HOST=ART/'out/host/linux-x86'
work=ROOT/'build/art-low4g-smoke'
work.mkdir(parents=True,exist_ok=True)
subprocess.run(['c++','-std=c++17','-O2','-Wall','-Wextra','-Werror',
 '-isystem',str(ART/'art/libartbase'),'-isystem',str(ART/'libbase/include'),
 str(ROOT/'tests/art_low4g.cpp'),'-L'+str(HOST/'lib64'),'-lartbase','-lcutils','-Wl,-rpath-link,'+str(ROOT/'.local/lib'),'-o',str(work/'pressure')],check=True)
env={**os.environ,'LD_LIBRARY_PATH':str(HOST/'lib64')+':'+str(ROOT/'.local/lib')}
r=subprocess.run([str(work/'pressure')],env=env,capture_output=True,text=True,timeout=20)
(work/'stdout.log').write_text(r.stdout);(work/'stderr.log').write_text(r.stderr)
if r.returncode or 'ART_LOW4G_PRESSURE_OK' not in r.stdout:raise SystemExit('ART low4G pressure failed; inspect '+str(work))
print(r.stdout.strip())
