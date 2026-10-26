#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise the real ALSA adapter with an explicit file PCM, never a physical speaker."""
import array
import os
from pathlib import Path
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/audio-output'
work.mkdir(exist_ok=True)
encoded = work / 'effect.wav'
with wave.open(str(encoded), 'wb') as sound:
    sound.setparams((1, 2, 48000, 4800, 'NONE', 'not compressed'))
    sound.writeframes(b'\x00\x20' * 4800)
output = work / 'output.raw'
config = work / 'alsa.conf'
config.write_text('pcm.null { type null }\npcm.shiraz_test { type file slave.pcm "null" file "' + str(output) + '" format "raw" }\n')
env = {**os.environ, 'ALSA_CONFIG_PATH': str(config), 'SHIRAZ_AUDIO_DEVICE': 'shiraz_test'}
# Ownership stress opens file sinks repeatedly; append separate PCM output for the sample assertion.
subprocess.run([str(ROOT / 'build/test-audio-output'), str(encoded), 'available'], env=env, check=True, timeout=10)
# A separate single-play helper mode preserves the initial output rather than the final stress-open file.
subprocess.run([str(ROOT / 'build/test-audio-output'), str(encoded), 'single'], env=env, check=True, timeout=10)
values = array.array('f')
values.frombytes(output.read_bytes())
if len(values) != 9600 or any(abs(value - (0.25 if index % 2 == 0 else 0.125)) > 0.00001 for index, value in enumerate(values)):
    raise SystemExit('ALSA file PCM did not receive the expected stereo waveform')
env['SHIRAZ_AUDIO_DEVICE'] = 'shiraz_missing'
subprocess.run([str(ROOT / 'build/test-audio-output'), str(encoded), 'unavailable'], env=env, check=True, timeout=10)
print('ALSA_FILE_PCM_WAVEFORM_OK (no audible output tested)')
