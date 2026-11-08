#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Conservative APK class-presence audit; never claims behavioral compatibility."""
import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.apk import inspect_apk


def class_name(descriptor):
    descriptor = descriptor.lstrip('[')
    if len(descriptor) < 3 or not descriptor.startswith('L') or not descriptor.endswith(';'):
        raise ValueError('Expected a class type descriptor')
    return descriptor[1:-1]


def audit(apk, available):
    report = inspect_apk(apk)
    references = {class_name(name) for name in report['framework_types']}
    missing = sorted(references - available)
    return {'apk': str(apk), 'package': report['manifest']['package'],
            'version': report['manifest']['version_name'], 'sha256': report['apk_sha256'],
            'nativeAbis': report['native_abis'], 'referencedFrameworkClasses': len(references),
            'missingClassCount': len(missing), 'missingClasses': missing, 'compatibilityVerified': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/api-surface-audit.json')
    args = parser.parse_args()
    classes = ROOT / 'build/framework/classes'
    if not classes.is_dir():
        raise SystemExit('Build the framework before auditing its class surface')
    available = {p.relative_to(classes).as_posix()[:-6] for p in classes.rglob('*.class')}
    for module in ('core-oj', 'core-libart'):
        core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES' / (module + '-hostdex_intermediates/classes.jar')
        with zipfile.ZipFile(core) as archive:
            available.update(name[:-6] for name in archive.namelist() if name.endswith('.class'))
    result = {'kind': 'static class availability only', 'limitations': [
        'Class presence does not prove method, field, reflection, JNI, rendering or lifecycle behavior.',
        'Static references may include annotations or conditional code never executed on this API level.',
        'Missing classes are investigation candidates, not proof every one blocks launch.'],
        'apps': [audit(apk, available) for apk in args.apks]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    for app in result['apps']:
        print(f"{app['package']}: {app['missingClassCount']} absent classes among {app['referencedFrameworkClasses']} static references; runtime compatibility unverified")
    print(args.output)


if __name__ == '__main__':
    main()
