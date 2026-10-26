# SPDX-License-Identifier: GPL-3.0-only
import argparse
import json
import sys
import zipfile
from pathlib import Path
from .apk import FormatError, inspect_apk
from .runtime import run_activity

ROOT = Path(__file__).resolve().parents[2]


def main(argv=None):
    parser = argparse.ArgumentParser(prog='shiraz', description='Shiraz APK-to-Linux toolchain (experimental)')
    parser.add_argument('--version', action='version', version='Shiraz ' + (ROOT / 'VERSION').read_text().strip())
    sub = parser.add_subparsers(dest='command', required=True)
    for command in ('inspect', 'plan', 'run'):
        p = sub.add_parser(command)
        p.add_argument('apk', type=Path)
        if command == 'run':
            p.add_argument('--width', type=int, default=360)
            p.add_argument('--height', type=int, default=640)
            p.add_argument('--frames', type=int)
            p.add_argument('--snapshot', type=Path, help='PPM startup frame under build/')
            p.add_argument('--timeout', type=int)
            p.add_argument('--device-profile', type=Path, help='first-run template under config/; Shiraz reuses the stored per-app device profile')
            p.add_argument('--log-level', choices=['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'], default='INFO')
        if command == 'plan':
            p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = inspect_apk(args.apk)
        if args.command == 'run':
            return run_activity(args.apk, report, width=args.width, height=args.height, frames=args.frames, snapshot=args.snapshot, timeout=args.timeout, log_level=args.log_level, device_profile=args.device_profile)
        if args.command == 'plan':
            output = args.output.resolve()
            local_roots = [(ROOT / name).resolve() for name in ('build', 'dist', 'context')]
            if not any(root in output.parents for root in local_roots):
                raise FormatError('write generated plans under build/, dist/, or context/')
            if output == args.apk.resolve():
                raise FormatError('output must not overwrite input APK')
            report['packaging'] = {'format': 'future ELF or small relocatable package', 'art_aot': 'pending', 'tree_shaking': 'disabled until dynamic requirements are handled'}
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(report, indent=2) + '\n')
        else:
            print(json.dumps(report, indent=2))
        return 0
    except (FormatError, OSError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f'shiraz: {exc}', file=sys.stderr)
        return 1
