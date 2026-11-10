#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate synthetic WAV/AAC data for CoreAudio regression tests, without capture."""
import math
import struct
import subprocess
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    if sys.platform != "darwin":
        raise SystemExit("This fixture uses the macOS system AAC encoder")
    work = ROOT / "build/darwin-playback-fixture"
    work.mkdir(parents=True, exist_ok=True)
    source = work / "tone.wav"
    with wave.open(str(source), "wb") as output:
        output.setparams((1, 2, 44100, 22050, "NONE", "not compressed"))
        output.writeframes(b"".join(struct.pack("<h", int(4000 * math.sin(i * 2 * math.pi * 440 / 44100))) for i in range(22050)))
    encoded = work / "tone.m4a"
    encoded.unlink(missing_ok=True)
    subprocess.run(["/usr/bin/afconvert", "-f", "m4af", "-d", "aac", "-b", "64000", str(source), str(encoded)], check=True)
    subprocess.run([str(ROOT / "build/macos/extract-aac-fixture"), str(encoded), str(work / "packets.bin")], check=True)
    print("SYNTHETIC_DARWIN_AAC_FIXTURE_OK")


if __name__ == "__main__":
    main()
