#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check absent-vs-unsupported sensor discovery using local fixture sysfs trees."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/sensor-smoke'
work.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
subprocess.run(['javac', '--release', '8', '-cp', str(framework), '-d', str(work), str(ROOT / 'tests/java/SensorDiscoverySmoke.java')], check=True)
subprocess.run(['java', '-cp', str(work) + ':' + str(framework), 'SensorDiscoverySmoke', str(work)], check=True, timeout=10)
