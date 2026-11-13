# SPDX-License-Identifier: GPL-3.0-only
import argparse
import json
import sys
import zipfile
from pathlib import Path
from .apk import FormatError, inspect_apk
from .apk_signer import certificate_digests
from .app_icon import extract_icon, resolve_label
from . import fdroid
from .runtime import properties_text, registry_metadata, run_activity

ROOT = Path(__file__).resolve().parents[2]


def main(argv=None):
    parser = argparse.ArgumentParser(prog='shiraz', description='Shiraz APK-to-Linux toolchain (experimental)')
    parser.add_argument('--version', action='version', version='Shiraz ' + (ROOT / 'VERSION').read_text().strip())
    sub = parser.add_subparsers(dest='command', required=True)
    for command in ('inspect', 'plan', 'run', 'icon', 'label', 'metadata', 'signer'):
        p = sub.add_parser(command)
        p.add_argument('apk', type=Path)
        if command == 'icon':
            p.add_argument('--output-dir', type=Path, required=True, help='Receives icon.json and the unchanged icon image files')
        if command == 'run':
            p.add_argument('--native-split', type=Path, help='Matching native-only ABI split APK; code/resource splits are unsupported')
            p.add_argument('--width', type=int, default=360)
            p.add_argument('--height', type=int, default=640)
            p.add_argument('--frames', type=int)
            p.add_argument('--snapshot', type=Path, help='PPM startup frame under build/')
            p.add_argument('--timeout', type=int)
            p.add_argument('--device-profile', type=Path, help='first-run template under config/; Shiraz reuses the stored per-app device profile')
            p.add_argument('--log-level', choices=['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'], default='INFO')
        if command == 'plan':
            p.add_argument('--output', type=Path, required=True)
        if command == 'metadata':
            p.add_argument('--installer', help='Package name of the app that requested the installation')
    repo = sub.add_parser('fdroid', help='F-Droid repository: signed index and verified APK downloads')
    actions = repo.add_subparsers(dest='action', required=True)
    sync = actions.add_parser('sync', help='Download and verify the index, write <cache>/catalog.json')
    sync.add_argument('--cache', type=Path, required=True)
    sync.add_argument('--max-age', type=int, default=0, help='Reuse a catalog younger than this many seconds')
    fetch = actions.add_parser('fetch', help='Download one catalog APK, SHA-256 checked against the signed index')
    fetch.add_argument('package')
    fetch.add_argument('--cache', type=Path, required=True)
    fetch.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == 'fdroid':
            if args.action == 'sync':
                catalog = fdroid.sync(args.cache, args.max_age)
                apps = catalog['apps']
                print(json.dumps({'catalog': str(args.cache / 'catalog.json'), 'apps': len(apps),
                                  'compatible': sum(a['compatible'] for a in apps), 'timestamp': catalog['repo']['timestamp']}))
            else:
                print(json.dumps(fdroid.fetch(args.cache, args.package, args.output)))
            return 0
        if args.command == 'label':
            print(json.dumps({'label': resolve_label(args.apk)}, ensure_ascii=False))
            return 0
        if args.command == 'signer':
            print(json.dumps({'certificates': certificate_digests(args.apk)}))
            return 0
        if args.command == 'icon':
            print(json.dumps(extract_icon(args.apk, args.output_dir), indent=2, sort_keys=True))
            return 0
        report = inspect_apk(args.apk)
        if args.command == 'metadata':
            if args.installer is not None and not fdroid.PACKAGE.fullmatch(args.installer):
                raise FormatError('Invalid installer package name')
            sys.stdout.write(properties_text(registry_metadata(args.apk, report, args.installer)))
            return 0
        if args.command == 'run':
            return run_activity(args.apk, report, width=args.width, height=args.height, frames=args.frames, snapshot=args.snapshot, timeout=args.timeout, log_level=args.log_level, device_profile=args.device_profile, native_split=args.native_split)
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
