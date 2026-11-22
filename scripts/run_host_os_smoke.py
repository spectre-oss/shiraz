#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile and run HostOsCrossPlatformSmoke on Linux, macOS, or Windows without requiring ART/dx."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

STUBS = {
    'android/system/StructUtsname.java': (
        'package android.system;\n'
        'public final class StructUtsname { public String sysname = ""; }\n'
    ),
    'android/system/Os.java': (
        'package android.system;\n'
        'public final class Os {\n'
        '  public static StructUtsname uname() { throw new UnsatisfiedLinkError("host-jvm"); }\n'
        '}\n'
    ),
    'android/net/Uri.java': (
        'package android.net;\n'
        'public class Uri {\n'
        '  public String getScheme() { return null; }\n'
        '}\n'
    ),
    'android/content/Intent.java': (
        'package android.content;\n'
        'import android.net.Uri;\n'
        'import java.util.Set;\n'
        'public class Intent {\n'
        '  public static final String ACTION_MAIN = "android.intent.action.MAIN";\n'
        '  public static final String ACTION_VIEW = "android.intent.action.VIEW";\n'
        '  public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";\n'
        '  public static final String CATEGORY_APP_BROWSER = "android.intent.category.APP_BROWSER";\n'
        '  public String getAction() { return null; }\n'
        '  public String getType() { return null; }\n'
        '  public Set<String> getCategories() { return null; }\n'
        '  public Intent getSelector() { return null; }\n'
        '  public Object getComponent() { return null; }\n'
        '  public String getPackage() { return null; }\n'
        '  public Uri getData() { return null; }\n'
        '}\n'
    ),
}


def main() -> int:
    framework_classes = ROOT / 'build' / 'framework' / 'classes'
    tmp = Path(tempfile.mkdtemp(prefix='shiraz-host-os-smoke-'))
    try:
        out_dir = tmp / 'classes'
        out_dir.mkdir(parents=True, exist_ok=True)
        if (framework_classes / 'shiraz' / 'runtime' / 'HostOs.class').is_file():
            cp = str(framework_classes)
            subprocess.run(
                [
                    'javac',
                    '--release',
                    '8',
                    '-cp',
                    cp,
                    '-d',
                    str(out_dir),
                    str(ROOT / 'tests' / 'java' / 'HostOsCrossPlatformSmoke.java'),
                ],
                check=True,
            )
            run_cp = f'{out_dir}{os.pathsep}{cp}'
        else:
            stub_root = tmp / 'stubs'
            stub_files = []
            for rel, code in STUBS.items():
                path = stub_root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(code, encoding='utf-8')
                stub_files.append(str(path))
            subprocess.run(
                [
                    'javac',
                    '--release',
                    '8',
                    '-d',
                    str(out_dir),
                    *stub_files,
                    str(ROOT / 'java' / 'shiraz' / 'runtime' / 'HostOs.java'),
                    str(ROOT / 'java' / 'shiraz' / 'runtime' / 'ExternalLauncher.java'),
                    str(ROOT / 'tests' / 'java' / 'HostOsCrossPlatformSmoke.java'),
                ],
                check=True,
            )
            run_cp = str(out_dir)

        subprocess.run(
            ['java', '-cp', run_cp, 'shiraz.runtime.HostOsCrossPlatformSmoke'],
            check=True,
        )
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    raise SystemExit(main())
