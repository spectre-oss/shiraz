#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Stress native APK-resource ownership with OpenJDK; APK behavior is checked separately in ART."""
import os
from pathlib import Path
import subprocess
import zipfile
from shiraz.manifest import inspect_manifest

ROOT = Path(__file__).resolve().parents[1]
apk = ROOT / 'build/apk-fixture/fixture.apk'
with zipfile.ZipFile(apk) as archive:
    resource = inspect_manifest(archive.read('AndroidManifest.xml'))['application_label_resource']
if not resource:
    raise SystemExit('Fixture requires a compiled resource label')
classes = ROOT / 'build/resource-smoke'
classes.mkdir(exist_ok=True)
framework = ROOT / 'build/framework/classes'
subprocess.run(['javac', '--release', '8', '-cp', str(framework), '-d', str(classes),
                str(ROOT / 'tests/java/ResourceLifetime.java'), str(ROOT / 'tests/java/ColorResourceFailures.java')], check=True)
env = {**os.environ, 'LD_LIBRARY_PATH': str(ROOT / 'context/art_standalone/out/host/linux-x86/lib64') + ':' + str(ROOT / '.local/lib')}
core_classes = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
for resources in (None, ROOT / 'build/framework-res/framework-res.apk'):
    options = ['-Dshiraz.frameworkResources=' + str(resources)] if resources else []
    subprocess.run(['java', '-Xcheck:jni', *options, '-Djava.library.path=' + str(ROOT / 'build'), '-cp', ':'.join(map(str, (classes, framework, core_classes))),
                    'ResourceLifetime', str(apk), str(resource)], env=env, check=True, timeout=30)

# A separate resource-only ZIP tests corrupt binary XML without changing the stock fixture APK.
malformed = classes / 'malformed-colors.apk'
with zipfile.ZipFile(apk) as original, zipfile.ZipFile(malformed, 'w') as output:
    # The first stored entry's data starts at offset44, preserving table alignment.
    output.writestr('resources.arsc', original.read('resources.arsc'))
    output.writestr('AndroidManifest.xml', original.read('AndroidManifest.xml'))
    name = 'res/color/selector_plain.xml'
    data = bytearray(original.read(name)); data[:8] = b'\0' * 8
    output.writestr(name, data)
subprocess.run(['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', ':'.join(map(str, (classes, framework, core_classes))),
                'ColorResourceFailures', str(malformed)], env=env, check=True, timeout=30)
