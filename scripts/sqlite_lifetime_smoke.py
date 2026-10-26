#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Stress public database/program/cursor reference ownership under OpenJDK."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/sqlite-lifetime'
work.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(work), str(ROOT / 'tests/java/SQLiteLifetimeSmoke.java')], check=True)
subprocess.run(['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(work) + ':' + classpath, 'SQLiteLifetimeSmoke', str(work / 'lifetime.db')], check=True, timeout=30)
