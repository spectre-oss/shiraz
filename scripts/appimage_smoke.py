#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run an AppImage in a mount namespace without the checkout, host Java or Python."""
import argparse
import hashlib
import json
import os
import platform
import re
from pathlib import Path
import subprocess
import signal
import tempfile
import time
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('image',type=Path)
p.add_argument('--require-aot',action='store_true',help='Require executable packaged framework/APK OAT without fallback')
p.add_argument('--frames',type=int,help='Optional frame cap; normally close after observing all requested sizes')
p.add_argument('--test-location',help='Explicit optional mock location; ordinary acceptance defaults to no grant')
a=p.parse_args()
image=a.image.resolve()
work=Path(tempfile.mkdtemp(prefix='shiraz-appimage-smoke-'))
(work/'home').mkdir()
(work/'input.txt').touch(mode=0o600)
(work/'passwd').write_text(f'shiraz:x:{os.getuid()}:{os.getgid()}::/test/home:/bin/sh\n')
(work/'group').write_text(f'shiraz:x:{os.getgid()}:\n')
(work/'nsswitch.conf').write_text('passwd: files\ngroup: files\nhosts: files dns\n')
# The namespace is only a test harness. The AppImage itself uses ordinary host processes.
base=['bwrap','--die-with-parent','--unshare-pid','--ro-bind','/usr','/usr',
      '--symlink','usr/bin','/bin','--symlink','usr/lib','/lib','--symlink','usr/lib','/lib64',
      '--proc','/proc','--ro-bind','/sys','/sys','--dev','/dev','--tmpfs','/tmp','--dir','/etc',
      '--bind',str(work),'/test','--ro-bind',str(image),'/OsmAnd.AppImage',
      '--chdir','/test','--clearenv']
for name in ('passwd','group','nsswitch.conf'):
    base+=['--ro-bind',str(work/name),'/etc/'+name]
for name in ('hosts','resolv.conf'):
    path=Path('/etc')/name
    if path.exists():base+=['--ro-bind',str(path),str(path)]
for path in ('/usr/lib/jvm','/usr/share/fonts'):
    if Path(path).is_dir():base+=['--tmpfs',path]
for path in ('/usr/bin/python3',):
    if Path(path).exists():base+=['--ro-bind','/dev/null',str(Path(path).resolve())]
for name,value in {'PATH':'/usr/bin:/bin','HOME':'/test/home','XDG_DATA_HOME':'/test/data',
                   'XDG_CACHE_HOME':'/test/cache','LANG':'C.UTF-8','SDL_VIDEODRIVER':'dummy',
                   'SDL_RENDER_DRIVER':'software','SHIRAZ_AUDIO_DEVICE':'null',
                   'SHIRAZ_LIVE_INPUT':'/test/input.txt','SHIRAZ_LIVE_SNAPSHOT':'1',
                   'APPIMAGE_EXTRACT_AND_RUN':'1'}.items():
    base+=['--setenv',name,value]
if a.require_aot:
    base+=['--setenv','SHIRAZ_TRACE_AOT','1']
if a.test_location:
    base+=['--setenv','SHIRAZ_TEST_LOCATION',a.test_location]
started=time.monotonic()
command=base+['/OsmAnd.AppImage','--width','720','--height','1280','--snapshot','/test/frame.ppm']
# The observer sends close after the final frame. A fixed frame cap can expire
# first as relayout changes frame throughput; the 120-second deadline still bounds the run.
if a.frames is not None:command+=['--frames',str(a.frames)]
loaded = set()
# Exercise resource-bucket relayout and recreation after observing each complete frame.
# In particular, no location mock may bypass the ordinary permission-denial flow.
expected_sizes = [(720,1280),(1200,1920),(1180,1900),(1280,720),(720,1280)]
observed_sizes = []
frame_timings = []
last_resize_requested = None
ready_since = None
sent_close = False
frame = work/'frame.ppm'
def complete_frame(size):
    try:
        raw = frame.read_bytes()
        header = ('P6\n%d %d\n255\n' % size).encode()
        return raw.startswith(header) and len(raw) == len(header)+size[0]*size[1]*3
    except FileNotFoundError:
        return False

proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
def descendants(pid):
    result = [pid]
    try:
        children = Path(f'/proc/{pid}/task/{pid}/children').read_text().split()
        for child in children: result.extend(descendants(int(child)))
    except (FileNotFoundError, ProcessLookupError): pass
    return result
while True:
    if len(observed_sizes) < len(expected_sizes):
        size = expected_sizes[len(observed_sizes)]
        if complete_frame(size):
            if len(frame_timings) == len(observed_sizes):
                observed = time.monotonic()
                frame_timings.append({'size':list(size),'observed_seconds':observed-started,
                                      'after_resize_seconds':None if last_resize_requested is None else observed-last_resize_requested})
            if ready_since is None: ready_since = time.monotonic()
            if time.monotonic()-ready_since >= 2:
                observed_sizes.append(size)
                ready_since = None
                with (work/'input.txt').open('a') as control:
                    if len(observed_sizes)<len(expected_sizes):
                        width,height=expected_sizes[len(observed_sizes)]
                        control.write(f'resize {width} {height}\n')
                        control.flush()
                        last_resize_requested = time.monotonic()
        else: ready_since = None
    elif not sent_close:
        if ready_since is None: ready_since = time.monotonic()
        if time.monotonic()-ready_since >= 5:
            with (work/'input.txt').open('a') as control: control.write('close\n')
            sent_close = True
    for pid in descendants(proc.pid):
        try:
            cmdline = Path(f'/proc/{pid}/cmdline').read_bytes()
            if b'/usr/art/bin/dalvikvm' not in cmdline: continue
            for line in Path(f'/proc/{pid}/maps').read_text().splitlines():
                fields = line.split(None, 5)
                if len(fields) == 6 and fields[5].startswith('/') and '.so' in fields[5]:
                    loaded.add(fields[5])
        except (FileNotFoundError, ProcessLookupError): pass
    try:
        stdout, stderr = proc.communicate(timeout=0.25)
        rc = proc.returncode
        break
    except subprocess.TimeoutExpired:
        if time.monotonic() - started > 120:
            os.killpg(proc.pid, signal.SIGKILL)
            stdout, stderr = proc.communicate()
            rc = 124
            break
(work/'launcher.stdout').write_bytes(stdout)
(work/'launcher.stderr').write_bytes(stderr)
resize_log='\n'.join(path.read_text(errors='replace') for path in (work/'cache').rglob('stderr.log'))
relayout_observed=bool(re.search(r'SHIRAZ_RESIZE applied=1180x1900 changes=0x[0-9a-f]+ recreated=false ',resize_log))
frame=work/'frame.ppm'
isa = {'x86_64': 'x86_64', 'aarch64': 'arm64'}[platform.machine()]
aot_paths = re.findall(r'Successfully loaded ([^\n\x00]+/oat/' + isa + r'/(?:shiraz-framework|original)\.odex) with executable: 1',resize_log)
aot_verified = (len(set(Path(path).name for path in aot_paths)) == 2 and
                all(path+' is kOatUpToDate with filter speed' in resize_log for path in aot_paths) and
                'Compiling dex file:' not in resize_log and 'fall back to imageless' not in resize_log)
summary={'image':str(image),'sha256':hashlib.file_digest(image.open('rb'),'sha256').hexdigest(),
         'packaged_aot_verified':aot_verified,'exit_code':rc,'seconds':time.monotonic()-started,'frame':str(frame) if frame.exists() else None,
         'evidence':str(work),'loaded_art_libraries':sorted(loaded),'scope':'headless standalone startup and resize/recreation; not full OsmAnd functionality or visible desktop acceptance',
         'observed_sizes':observed_sizes,'frame_timings':frame_timings,
         'within_bucket_relayout_observed':relayout_observed,
         'timing_scope':'fresh app data; includes AppImage extraction and snapshot I/O; host page cache uncontrolled; captured frame is not proof of interactivity; polling adds up to one interval of delay',
         'test_location':a.test_location,'normal_close_requested':sent_close,
         'isolation':['checkout absent','host Java/Python hidden','host fonts hidden','fresh app data'],
         'command':command}
(ROOT/'build/appimage').mkdir(exist_ok=True)
(ROOT/'build/appimage/smoke-result.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))
external = [path for path in loaded if not path.startswith(('/tmp/appimage_extracted_', '/test/data/'))
            and not re.fullmatch(r'/test/cache/shiraz-browser-run-[^/]+/usr/(?:lib|android-lib)/[^/]+\.so(?:\.[0-9]+)*', path)]
if a.require_aot and not aot_verified:raise SystemExit('Packaged AOT execution was not verified; inspect logs')
if not loaded or external:raise SystemExit('Runtime library audit failed: '+repr(external))
if observed_sizes != expected_sizes or not sent_close:raise SystemExit('AppImage did not complete the observed resize/recreation sequence')
if rc or not frame.exists():raise SystemExit('AppImage launch failed; inspect evidence path above')
if not relayout_observed:raise SystemExit('Packaged within-bucket resize recreated or was not observed')
raw=frame.read_bytes()
if not raw.startswith(b'P6\n720 1280\n255\n') or len(raw)!=len(b'P6\n720 1280\n255\n')+720*1280*3:
    raise SystemExit('Invalid packaged frame dimensions/length')
if len(set(raw[len(b'P6\n720 1280\n255\n'):]))<4:raise SystemExit('Packaged frame is blank')
print('APPIMAGE_ISOLATED_LAUNCH_OK')
