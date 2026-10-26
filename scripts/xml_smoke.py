#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify bounded runtime XML expansion and parser recovery under OpenJDK."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/xml-smoke'
work.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(work), str(ROOT / 'tests/java/XmlExpansionSmoke.java')], check=True)
subprocess.run(['java', '-Xcheck:jni', '-Xmx128m', '-cp', str(work) + ':' + classpath, 'XmlExpansionSmoke'], check=True, timeout=30)
