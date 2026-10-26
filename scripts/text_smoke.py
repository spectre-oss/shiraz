#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check shared native font state under OpenJDK; the SDK APK gate separately uses ART."""
import os
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/text-smoke'
work.mkdir(parents=True, exist_ok=True)
classes = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
subprocess.run(['javac', '--release', '8', '-cp', str(classes), '-d', str(work), str(ROOT / 'tests/java/TextSmoke.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/TextBoundsFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/StaticLayoutFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/BoringLayoutFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/TextDirectionFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/TextScaleFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/HintingFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/FakeBoldFixture.java'), str(ROOT / 'tests/apk/src/org/shiraz/fixture/TextSkewFixture.java')], check=True)
subprocess.run(['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(work) + ':' + str(classes) + ':' + str(core), 'TextSmoke'],
               env={**os.environ, 'XDG_CACHE_HOME': str(work / 'cache')}, check=True, timeout=30)
