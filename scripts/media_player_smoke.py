#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise MediaPlayer descriptor regions, lifecycle and PCM output on OpenJDK and ART."""
import array
import fcntl
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from art_smoke import environment, HOST, JARS
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/media-player-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Audio playback smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', str(classes)+':'+classpath, '-d', str(classes), str(ROOT / 'tests/java/MediaPlayerSmoke.java')], check=True)
commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'MediaPlayerSmoke'])]
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
                        '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'MediaPlayerSmoke']))
marker = 'MEDIA_PLAYER_OK'
for runtime, command in commands:
    apk=os.environ.get("SHIRAZ_TEST_AUDIO_APK")
    if apk: command.insert(1,"-Dshiraz.testAudioApk="+str(Path(apk).resolve()))
    asset=os.environ.get("SHIRAZ_TEST_AUDIO_ASSET")
    if asset: command.insert(1,"-Dshiraz.testAudioAsset="+str(Path(asset).resolve()))
    data=work/runtime
    if data.exists():shutil.rmtree(data)
    data.mkdir()
    command.insert(1,'-Dshiraz.captureTestData='+str(data))
    command.insert(1, '-Dshiraz.frameworkResources=' + str(ROOT / 'build/framework-res/framework-res.apk'))
    pcm = data / 'output.pcm'
    config = data / 'alsa.conf'
    config.write_text('pcm.null { type null }\npcm.shiraz_test { type file slave.pcm "null" file "' + str(pcm) + '" format "raw" }\n')
    result = subprocess.run(command, env={**environment(work), "ALSA_CONFIG_PATH": str(config), "SHIRAZ_AUDIO_DEVICE": "shiraz_test"}, capture_output=True, text=True, timeout=30)
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout:
        raise SystemExit('Audio playback smoke failed in ' + runtime + '; inspect build/media-player-smoke/' + runtime + '-stderr.log')
    samples = array.array('h', pcm.read_bytes())
    if len(samples) < 4410 or sum(sample * sample for sample in samples) / len(samples) < 1000000:
        raise SystemExit('Host audio sink did not receive the decoded signal: ' + runtime)
    print('MEDIA_PLAYER_OK ' + runtime + ' host_sink_samples=' + str(len(samples)))
(work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'component_only': True}, indent=2) + '\n')
