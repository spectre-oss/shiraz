#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check persistent channel policy, caller I/O policy and service lifetimes."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/notification-policy-smoke'
work.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
classpath = str(framework) + ':' + str(core)
subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(work), str(ROOT / 'tests/java/NotificationPolicySmoke.java')], check=True)
subprocess.run(['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(work) + ':' + classpath, 'NotificationPolicySmoke', str(work / 'policy.db')], check=True, timeout=30)
