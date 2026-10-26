# SPDX-License-Identifier: GPL-3.0-only
"""Render original APK artwork and create a freedesktop application entry."""
from pathlib import Path
import subprocess
import os

ROOT = Path(__file__).resolve().parents[1]

def desktop_value(value):
    return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')

def package_desktop(apk, appdir, title):
    classes = ROOT/'build/desktop-icon-classes'
    classes.mkdir(exist_ok=True)
    core = ROOT/'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    classpath = ':'.join(map(str, [classes, ROOT/'build/framework/classes', core]))
    subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes),
                    str(ROOT/'packaging/BuildDesktopIcon.java')], check=True, timeout=60)
    subprocess.run(['java', '-Djava.library.path='+str(ROOT/'build'),
                    '-Dshiraz.frameworkResources='+str(ROOT/'build/framework-res/framework-res.apk'),
                    '-cp', classpath, 'BuildDesktopIcon', str(apk), str(appdir/'shiraz-icon.png')],
                   check=True, timeout=60, env=dict(os.environ, LD_LIBRARY_PATH=':'.join([
                       str(ROOT/'build'), str(ROOT/'context/art_standalone/out/host/linux-x86/lib64'),
                       str(ROOT/'.local/lib'), os.environ.get('LD_LIBRARY_PATH', '')])) )
    (appdir/'.DirIcon').symlink_to('shiraz-icon.png')
    (appdir/'shiraz.desktop').write_text('[Desktop Entry]\nType=Application\nVersion=1.5\nName='+
        desktop_value(title)+' (Shiraz)\nExec=AppRun\nIcon=shiraz-icon\nCategories=Utility;\nTerminal=false\n', encoding='utf-8')
