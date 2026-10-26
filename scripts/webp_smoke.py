#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise webp static image decoding on OpenJDK and ART."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/webp-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('WebP smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', str(classes)+':'+classpath, '-d', str(classes), str(ROOT / 'tests/java/WebpSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'), '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'WebpSmoke'])]
dex = work / 'classes.dex'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], env=environment(work), check=True)
jar = work / 'smoke.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    entry = zipfile.ZipInfo('classes.dex')
    padding = (-(30 + len(entry.filename) + 4)) % 4
    entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
    archive.writestr(entry, dex.read_bytes())
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
commands.append(('art', [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                        '-Xcheck:jni', '-Xusejit:false', '-Xint', '-Xnoimage-dex2oat', '-Xcompiler:' + str(HOST / 'bin/dex2oat'),
                        '-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'), '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'WebpSmoke']))
import ctypes
codec=ctypes.CDLL('libwebp.so')
codec.WebPEncodeLosslessRGBA.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.POINTER(ctypes.c_void_p)]
codec.WebPEncodeLosslessRGBA.restype=ctypes.c_size_t
codec.WebPFree.argtypes=[ctypes.c_void_p]
raw=ctypes.create_string_buffer(bytes([255,0,0,255,0,255,0,128]))
output=ctypes.c_void_p()
length=codec.WebPEncodeLosslessRGBA(raw,2,1,8,ctypes.byref(output))
if not length: raise SystemExit('WebP fixture encoding failed')
fixture=work/'pixels.webp'
try: fixture.write_bytes(ctypes.string_at(output,length))
finally: codec.WebPFree(output)
for _, command in commands: command.append(str(fixture))
marker = 'WEBP_DECODE_OK'
for runtime, command in commands:
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit('WebP smoke failed in ' + runtime + '; inspect build/webp-smoke/' + runtime + '-stderr.log')
    print('WEBP_OK ' + runtime)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'component_only': True}, indent=2) + '\n')
