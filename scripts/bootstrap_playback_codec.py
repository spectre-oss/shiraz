#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned GPL-2.0-or-later FAAD2 locally; never install on the host."""
from pathlib import Path
import hashlib
import subprocess
import tarfile
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
URL='https://github.com/knik0/faad2/archive/refs/tags/2.11.2.tar.gz'
SHA256='3fcbd305e4abd34768c62050e18ca0986f7d9c5eca343fb98275418013065c0e'
def main():
    directory=ROOT/'.deps/playback';directory.mkdir(parents=True,exist_ok=True)
    archive=directory/'faad2-2.11.2.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(URL,timeout=45) as response: data=response.read(4*1024*1024)
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA256: raise SystemExit('Playback codec source hash mismatch')
    source=directory/'faad2-2.11.2'
    if not source.is_dir():
        with tarfile.open(archive) as tar: tar.extractall(directory,filter='data')
    if not (ROOT/'.local/lib/libfaad.so').exists():
        include=ROOT/'.local/include';include.mkdir(parents=True,exist_ok=True)
        (ROOT/'.local/lib').mkdir(parents=True,exist_ok=True)
        (include/'neaacdec.h').write_bytes((source/'include/neaacdec.h').read_bytes())
        (include/'faad.h').write_text((source/'include/faad.h.in').read_text().replace('@VERSION@','2.11.2'))
        flags=['-O2','-fPIC','-ffloat-store','-DHAVE_INTTYPES_H=1','-DHAVE_MEMCPY=1','-DHAVE_STRING_H=1','-DHAVE_STRINGS_H=1','-DHAVE_SYS_STAT_H=1','-DHAVE_SYS_TYPES_H=1','-DHAVE_LRINTF=1','-DAPPLY_DRC','-DPACKAGE_VERSION="2.11.2"','-I'+str(include),'-I'+str(source/'libfaad')]
        # Match the ordinary floating-point faad target in upstream CMakeLists.txt.
        subprocess.run(['cc',*flags,'-shared','-Wl,-soname,libfaad.so.2',*map(str,sorted((source/'libfaad').glob('*.c'))),'-lm','-o',str(ROOT/'.local/lib/libfaad.so.2')],check=True)
        (ROOT/'.local/lib/libfaad.so').symlink_to('libfaad.so.2')
    destination=ROOT/'.local/share/shiraz/playback-codec';destination.mkdir(parents=True,exist_ok=True)
    (destination/'COPYING').write_bytes((source/'COPYING').read_bytes())
    (destination/'SOURCE.txt').write_text(f'FAAD2 2.11.2\n{URL}\nSHA-256: {SHA256}\nLicense: GPL-2.0-or-later; original notices in COPYING.\n')
if __name__=='__main__':main()
