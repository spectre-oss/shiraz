#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify Android speech queue, voice selection, callbacks and failures with ALSA file output."""
import fcntl
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/tts-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('TextToSpeech smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', str(classes)+':'+classpath, '-d', str(classes), str(ROOT / 'tests/java/TextToSpeechSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'), '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'TextToSpeechSmoke', str(ROOT/'.local/share'), 'complete', str(work/'reference.raw')])]
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
                        '-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'), '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'TextToSpeechSmoke', str(ROOT/'.local/share'), 'complete', str(work/'reference.raw')]))
marker = 'TTS_OK'
config=work/'alsa.conf'
output=work/'output.raw'
config.write_text('pcm.null { type null }\npcm.speech_test { type file slave.pcm "null" file "'+str(output)+'" format "raw" }\n')
for runtime, base in commands:
    for mode in ['complete', 'unavailable']:
        output.unlink(missing_ok=True)
        command=list(base);command[-2]=mode
        env={**environment(work),'ALSA_CONFIG_PATH':str(config),'SHIRAZ_AUDIO_DEVICE':'missing_speech_device' if mode=='unavailable' else 'speech_test'}
        result=subprocess.run(command,env=env,capture_output=True,text=True,timeout=30)
        (work/(runtime+'-'+mode+'-stdout.log')).write_text(result.stdout)
        (work/(runtime+'-'+mode+'-stderr.log')).write_text(result.stderr)
        if result.returncode or marker+' '+mode not in result.stdout.splitlines():
            raise SystemExit('TextToSpeech failed: '+runtime+' '+mode+'; inspect build/tts-smoke')
        if mode=='complete' and (not output.exists() or output.stat().st_size==0):
            raise SystemExit('Speech produced no ALSA PCM output')
        print('TTS_OK '+runtime+' '+mode)
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'component_only': True, 'queue_callbacks_verified': True, 'audible_output_verified': False}, indent=2) + '\n')
