# SPDX-License-Identifier: GPL-3.0-only
import argparse
import json
import os
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
    parser = argparse.ArgumentParser(
        prog='shiraz', description='Shiraz APK-to-Linux toolchain (experimental)'
    )
    parser.add_argument(
        '--version', action='version', version='Shiraz ' + (ROOT / 'VERSION').read_text().strip()
    )
    sub = parser.add_subparsers(dest='command', required=True)
    for command in (
        'inspect',
        'plan',
        'run',
        'icon',
        'label',
        'metadata',
        'signer',
        'verify-signature',
        'register-package',
    ):
        p = sub.add_parser(command)
        p.add_argument('apk', type=Path)
        if command == 'register-package':
            p.add_argument('--registry', type=Path, required=True)
            p.add_argument('--expected-package', help='Reject enrollment of a different package')
            p.add_argument('--expected-sha256', help='Require these exact verified APK bytes')
            p.add_argument(
                '--expected-current', help='Require the currently registered SHA-256, or absent'
            )
        if command == 'icon':
            p.add_argument(
                '--output-dir',
                type=Path,
                required=True,
                help='Receives icon.json and the unchanged icon image files',
            )
        if command == 'run':
            p.add_argument(
                '--registry',
                type=Path,
                help='Require exact enrollment and supervise activity and service processes',
            )
            p.add_argument(
                '--native-split',
                type=Path,
                help='Matching native-only ABI split APK; code/resource splits are unsupported',
            )
            p.add_argument('--width', type=int, default=360)
            p.add_argument('--height', type=int, default=640)
            p.add_argument('--frames', type=int)
            p.add_argument('--snapshot', type=Path, help='PPM startup frame under build/')
            p.add_argument('--timeout', type=int)
            p.add_argument(
                '--device-profile',
                type=Path,
                help='first-run template under config/; Shiraz reuses the stored per-app device profile',
            )
            p.add_argument(
                '--log-level',
                choices=['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'],
                default='INFO',
            )
            p.add_argument(
                '--sandbox',
                action='store_true',
                help='Isolate the app process using the native OS sandbox (Linux Landlock, macOS Seatbelt, Windows Job Object)',
            )
            p.add_argument(
                '--no-network',
                action='store_true',
                help='Deny network access inside the app sandbox (implies --sandbox)',
            )
        if command == 'plan':
            p.add_argument('--output', type=Path, required=True)
        if command == 'metadata':
            p.add_argument(
                '--installer', help='Package name of the app that requested the installation'
            )
    session = sub.add_parser(
        'run-session', help='Launch registered app packages with one shared service broker'
    )
    session.add_argument('--registry', type=Path, required=True)
    session.add_argument(
        '--timeout', type=int, help='Maximum lifetime in seconds for the whole session'
    )
    session.add_argument('packages', nargs='+')
    jobs = sub.add_parser(
        'serve-jobs', help='Foreground background-job host for an external service supervisor'
    )
    jobs.add_argument('--registry', type=Path, required=True)
    jobs.add_argument('--parent-pid', type=int, help=argparse.SUPPRESS)
    supervisor = sub.add_parser(
        'supervise-jobs', help='Restart the foreground job host after failure'
    )
    supervisor.add_argument('--registry', type=Path, required=True)
    unit = sub.add_parser(
        'job-service-unit', help='Print a Linux systemd user unit without installing it'
    )
    unit.add_argument('--registry', type=Path, required=True)
    unit.add_argument(
        '--python',
        type=Path,
        required=True,
        help='Absolute interpreter path on the target Linux host',
    )
    unit.add_argument(
        '--checkout',
        type=Path,
        default=ROOT,
        help='Absolute source checkout on the target Linux host',
    )
    control = sub.add_parser(
        'serve-session', help='Private launcher-owned shared host over stdin/stdout'
    )
    control.add_argument('--registry', type=Path, required=True)
    control.add_argument(
        '--library', type=Path, help='Launcher-owned library; creates a private inbox per launch'
    )
    repo = sub.add_parser(
        'fdroid', help='F-Droid repository: signed index and verified APK downloads'
    )
    actions = repo.add_subparsers(dest='action', required=True)
    sync = actions.add_parser(
        'sync', help='Download and verify the index, write <cache>/catalog.json'
    )
    sync.add_argument('--cache', type=Path, required=True)
    sync.add_argument(
        '--max-age', type=int, default=0, help='Reuse a catalog younger than this many seconds'
    )
    fetch = actions.add_parser(
        'fetch', help='Download one catalog APK, SHA-256 checked against the signed index'
    )
    fetch.add_argument('package')
    fetch.add_argument('--cache', type=Path, required=True)
    fetch.add_argument('--output', type=Path, required=True)
    desktop_in = sub.add_parser(
        'install-desktop',
        help='Install a native OS desktop launcher for an APK (Linux .desktop, macOS .app, Windows Start Menu)',
    )
    desktop_in.add_argument('apk', type=Path)
    desktop_in.add_argument('--name', help='Override launcher display name')
    desktop_in.add_argument('--icon', type=Path, help='Optional PNG or ICNS icon file')
    desktop_in.add_argument(
        '--sandbox',
        action='store_true',
        help='Launch the installed app inside the native OS sandbox',
    )
    desktop_in.add_argument(
        '--no-network',
        action='store_true',
        help='Deny network access inside the installed app sandbox (implies --sandbox)',
    )
    desktop_in.add_argument(
        '--applications-dir',
        type=Path,
        help='Override the target OS applications/launcher directory',
    )
    desktop_out = sub.add_parser(
        'uninstall-desktop',
        help='Remove a Shiraz-managed native desktop launcher by package name',
    )
    desktop_out.add_argument('package')
    desktop_out.add_argument(
        '--applications-dir',
        type=Path,
        help='Override the target OS applications/launcher directory',
    )
    args = parser.parse_args(argv)
    try:
        if args.command == 'job-service-unit':
            from .job_service_unit import render

            print(render(args.checkout, args.python, args.registry), end='')
            return 0
        if args.command == 'supervise-jobs':
            from .job_supervisor import supervise

            supervise(args.registry, sys.stdout)
            return 0
        if args.command == 'serve-jobs':
            from .job_host import serve

            serve(args.registry, sys.stdout, parent_pid=args.parent_pid)
            return 0
        if args.command == 'serve-session':
            from .host_control import serve

            serve(args.registry, sys.stdin.buffer, sys.stdout, library=args.library)
            return 0
        if args.command == 'run-session':
            from .host_session import HostSession, session_exit_status

            if len(args.packages) > 16 or len(set(args.packages)) != len(args.packages):
                raise FormatError('Session requires 1-16 distinct registered packages')
            if args.timeout is not None and args.timeout <= 0:
                raise FormatError('Session timeout must be positive')
            with HostSession(args.registry) as host:
                for package in args.packages:
                    host.start(package)
                result = host.wait(args.timeout)
            print(json.dumps(result, indent=2))
            return session_exit_status(result)
        if args.command == 'fdroid':
            if args.action == 'sync':
                catalog = fdroid.sync(args.cache, args.max_age)
                apps = catalog['apps']
                print(
                    json.dumps(
                        {
                            'catalog': str(args.cache / 'catalog.json'),
                            'apps': len(apps),
                            'compatible': sum(a['compatible'] for a in apps),
                            'timestamp': catalog['repo']['timestamp'],
                        }
                    )
                )
            else:
                print(json.dumps(fdroid.fetch(args.cache, args.package, args.output)))
            return 0
        if args.command == 'label':
            print(json.dumps({'label': resolve_label(args.apk)}, ensure_ascii=False))
            return 0
        if args.command == 'register-package':
            from .package_registry import PackageRegistry

            print(
                json.dumps(
                    PackageRegistry(args.registry).enroll(
                        args.apk,
                        expected_package=args.expected_package,
                        expected_sha256=args.expected_sha256,
                        expected_current=args.expected_current,
                    )
                )
            )
            return 0
        if args.command == 'verify-signature':
            from .apk_verifier import verify_signatures

            print(json.dumps(verify_signatures(args.apk)))
            return 0
        if args.command == 'signer':
            print(json.dumps({'certificates': certificate_digests(args.apk)}))
            return 0
        if args.command == 'uninstall-desktop':
            from .desktop_install import uninstall_desktop_app

            removed = uninstall_desktop_app(
                args.package,
                applications_dir=args.applications_dir,
            )
            print(
                json.dumps(
                    {'package': args.package, 'removed': [str(p) for p in removed]},
                    indent=2,
                    sort_keys=True,
                )
            )
            return 0
        if args.command == 'icon':
            print(json.dumps(extract_icon(args.apk, args.output_dir), indent=2, sort_keys=True))
            return 0
        report = inspect_apk(args.apk)
        if args.command == 'install-desktop':
            from .desktop_install import install_desktop_app

            label = args.name or resolve_label(args.apk) or report['package']
            use_sandbox = bool(args.sandbox or args.no_network)
            allow_network = not bool(args.no_network)
            installed = install_desktop_app(
                args.apk,
                report['package'],
                label,
                icon_path=args.icon,
                sandbox=use_sandbox,
                allow_network=allow_network,
                applications_dir=args.applications_dir,
            )
            print(json.dumps(installed.to_dict(), indent=2, sort_keys=True))
            return 0
        if args.command == 'metadata':
            if args.installer is not None and not fdroid.PACKAGE.fullmatch(args.installer):
                raise FormatError('Invalid installer package name')
            sys.stdout.write(properties_text(registry_metadata(args.apk, report, args.installer)))
            return 0
        if args.command == 'run':
            use_sandbox = bool(args.sandbox or args.no_network)
            allow_network = not bool(args.no_network)
            if args.registry is not None:
                if args.native_split is not None:
                    raise FormatError('Registered native split launch is not implemented')
                from .host_session import HostSession, session_exit_status

                native_mode = os.environ.get('SHIRAZ_GUEST_LOADER', '0')
                if native_mode not in ('0', '1'):
                    raise FormatError('SHIRAZ_GUEST_LOADER must be 0 or 1')
                settings = dict(
                    width=args.width,
                    height=args.height,
                    frames=args.frames,
                    snapshot=str(args.snapshot.resolve()) if args.snapshot else None,
                    device_profile=str(args.device_profile.resolve())
                    if args.device_profile
                    else None,
                    timeout=args.timeout,
                    log_level=args.log_level,
                    native_code=native_mode == '1',
                    sandbox=use_sandbox,
                    allow_network=allow_network,
                )
                with HostSession(args.registry) as host:
                    host.start(
                        report['manifest']['package'],
                        expected_sha256=report['apk_sha256'],
                        settings=settings,
                    )
                    print('Supervised activity logs: ' + str(host.work), flush=True)
                    result = host.wait(args.timeout)
                print(json.dumps(result, indent=2))
                return session_exit_status(result)
            return run_activity(
                args.apk,
                report,
                width=args.width,
                height=args.height,
                frames=args.frames,
                snapshot=args.snapshot,
                timeout=args.timeout,
                log_level=args.log_level,
                device_profile=args.device_profile,
                native_split=args.native_split,
                registry=args.registry,
                sandbox=use_sandbox,
                allow_network=allow_network,
            )
        if args.command == 'plan':
            output = args.output.resolve()
            local_roots = [(ROOT / name).resolve() for name in ('build', 'dist', 'context')]
            if not any(root in output.parents for root in local_roots):
                raise FormatError('write generated plans under build/, dist/, or context/')
            if output == args.apk.resolve():
                raise FormatError('output must not overwrite input APK')
            report['packaging'] = {
                'format': 'future ELF or small relocatable package',
                'art_aot': 'pending',
                'tree_shaking': 'disabled until dynamic requirements are handled',
            }
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(report, indent=2) + '\n')
        else:
            print(json.dumps(report, indent=2))
        return 0
    except (FormatError, OSError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f'shiraz: {exc}', file=sys.stderr)
        return 1
