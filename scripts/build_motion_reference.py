#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the unsigned, permission-free Android/Shiraz motion reference APK."""
from pathlib import Path
import subprocess
import zipfile
from build_apk_fixture import tools, TOOLS
ROOT = Path(__file__).resolve().parents[1]

def main():
    tools()
    work = ROOT / 'build/android-motion-reference'
    classes = work / 'probe-classes'
    classes.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'tests/motion-reference'
    apk = work / 'motion-unsigned.apk'
    subprocess.run([str(TOOLS/'aapt2'), 'link', '-o', str(apk), '--manifest', str(source/'AndroidManifest.xml'), '-I', str(TOOLS/'android.jar')], check=True)
    subprocess.run(['javac', '--release', '8', '-cp', str(TOOLS/'android.jar'), '-d', str(classes), str(source/'MotionReferenceActivity.java')], check=True)
    dex = work / 'probe.dex'
    subprocess.run([str(ROOT/'context/art_standalone/out/host/linux-x86/bin/dx'), '--dex', '--min-sdk-version=27', '--output='+str(dex), str(classes)], check=True)
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex, 'classes.dex')
    print('MOTION_REFERENCE_APK '+str(apk))

if __name__ == '__main__':
    main()
