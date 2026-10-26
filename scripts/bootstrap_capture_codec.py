#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned Apache-2.0 VisualOn AAC in the local prefix, without host installs."""
from pathlib import Path
import hashlib
import subprocess
import tarfile
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
VERSION='0.1.3'
URL='https://downloads.sourceforge.net/opencore-amr/vo-aacenc-0.1.3.tar.gz'
SHA256='e51a7477a359f18df7c4f82d195dab4e14e7414cbd48cf79cc195fc446850f36'
def main():
    directory=ROOT/'.deps/capture'
    directory.mkdir(parents=True,exist_ok=True)
    archive=directory/f'vo-aacenc-{VERSION}.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(URL,timeout=45) as response:
            data=response.read(2*1024*1024)
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA256:
        raise SystemExit('Capture codec source hash mismatch')
    source=directory/f'vo-aacenc-{VERSION}'
    if not source.is_dir():
        with tarfile.open(archive) as tar:tar.extractall(directory,filter='data')
    if not (ROOT/'.local/lib/libvo-aacenc.so').exists():
        subprocess.run(['./configure','--prefix='+str(ROOT/'.local'),'--disable-static'],cwd=source,check=True)
        subprocess.run(['make','-j4'],cwd=source,check=True)
        subprocess.run(['make','install'],cwd=source,check=True)
    destination=ROOT/'.local/share/shiraz/capture-codec'
    destination.mkdir(parents=True,exist_ok=True)
    for name in ('COPYING','NOTICE'):
        (destination/name).write_bytes((source/name).read_bytes())
    (destination/'SOURCE.txt').write_text(f'VisualOn AAC {VERSION}\n{URL}\nSHA-256: {SHA256}\nLicense: Apache-2.0; original notices in COPYING and NOTICE.\n')
if __name__=='__main__':main()
