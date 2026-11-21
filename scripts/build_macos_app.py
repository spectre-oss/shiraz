#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build and sign a self-contained macOS .app bundle for Shiraz.

Creates Contents/{MacOS,Frameworks,Resources,Info.plist}, resolves and copies
non-system Mach-O shared library dependencies via otool -L, rewrites install
names to @rpath/<name>, adds @executable_path/../Frameworks rpath, optionally
converts a PNG icon to .icns, and signs the bundle with hardened runtime and
com.apple.security.cs.allow-jit entitlements.
"""

import argparse
from collections import deque
import os
from pathlib import Path
import plistlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SYSTEM_PREFIXES = ('/System/Library/', '/usr/lib/')
DEFAULT_MIN_MACOS = '15.0'
ICONSET_SIZES = (
    ('icon_16x16.png', 16),
    ('icon_16x16@2x.png', 32),
    ('icon_32x32.png', 32),
    ('icon_32x32@2x.png', 64),
    ('icon_128x128.png', 128),
    ('icon_128x128@2x.png', 256),
    ('icon_256x256.png', 256),
    ('icon_256x256@2x.png', 512),
    ('icon_512x512.png', 512),
    ('icon_512x512@2x.png', 1024),
)


class BundleError(RuntimeError):
    """Explicit failure while constructing or signing a macOS .app bundle."""


def read_repo_version(root=ROOT):
    version_file = Path(root) / 'VERSION'
    if not version_file.is_file():
        return '0.0.0'
    text = version_file.read_text(encoding='utf-8').strip()
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', text):
        raise BundleError(f'Invalid semantic version in {version_file}: {text!r}')
    return text


def is_system_dylib(install_name):
    return any(install_name.startswith(prefix) for prefix in SYSTEM_PREFIXES)


def parse_otool_deps(output):
    """Parse `otool -L` output into a list of referenced install names."""
    deps = []
    lines = output.splitlines()
    for line in lines[1:]:
        stripped = line.strip()
        if not stripped:
            continue
        match = re.match(r'^(.+?)\s+\(compatibility version ', stripped)
        path = match.group(1) if match else stripped.split()[0]
        if path:
            deps.append(path)
    return deps


def parse_otool_rpaths(output):
    """Parse `otool -l` output into LC_RPATH entries."""
    rpaths = []
    in_rpath = False
    for line in output.splitlines():
        stripped = line.strip()
        if stripped.startswith('cmd '):
            in_rpath = stripped == 'cmd LC_RPATH'
        elif in_rpath and stripped.startswith('path '):
            match = re.match(r'^path\s+(.+?)\s+\(offset ', stripped)
            if match:
                rpaths.append(match.group(1))
            in_rpath = False
    return rpaths


def run_tool(cmd, *, cwd=None):
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise BundleError(
            f'Command failed ({result.returncode}): {" ".join(map(str, cmd))}: {detail}'
        )
    return result.stdout


def inspect_macho(binary_path):
    deps = parse_otool_deps(run_tool(['otool', '-L', str(binary_path)]))
    rpaths = parse_otool_rpaths(run_tool(['otool', '-l', str(binary_path)]))
    # For a dylib, the first entry of `otool -L` can be its own install id.
    return deps, rpaths


def is_macho_dylib(path):
    """Return True if otool -D reports an install id (shared library)."""
    out = run_tool(['otool', '-D', str(path)])
    lines = [line.strip() for line in out.splitlines() if line.strip()]
    return len(lines) >= 2


def resolve_dependency(dep, owner_path, owner_rpaths, executable_dir, search_dirs):
    """Resolve a non-system Mach-O dependency reference to an on-disk Path."""
    if dep.startswith('@loader_path/'):
        rel = dep[len('@loader_path/') :]
        candidate = (owner_path.parent / rel).resolve()
        if candidate.is_file():
            return candidate
    elif dep.startswith('@executable_path/'):
        rel = dep[len('@executable_path/') :]
        candidate = (executable_dir / rel).resolve()
        if candidate.is_file():
            return candidate
    elif dep.startswith('@rpath/'):
        rel = dep[len('@rpath/') :]
        for rpath in owner_rpaths:
            if rpath.startswith('@loader_path'):
                suffix = rpath[len('@loader_path') :].lstrip('/')
                base = owner_path.parent / suffix if suffix else owner_path.parent
            elif rpath.startswith('@executable_path'):
                suffix = rpath[len('@executable_path') :].lstrip('/')
                base = executable_dir / suffix if suffix else executable_dir
            else:
                base = Path(rpath)
            candidate = (base / rel).resolve()
            if candidate.is_file():
                return candidate
        for directory in search_dirs:
            candidate = (directory / rel).resolve()
            if candidate.is_file():
                return candidate
    else:
        direct = Path(dep)
        if direct.is_file():
            return direct.resolve()
        for directory in search_dirs:
            candidate = (directory / direct.name).resolve()
            if candidate.is_file():
                return candidate
    return None


def build_info_plist(
    *,
    name,
    bundle_id,
    executable_name,
    version,
    min_macos=DEFAULT_MIN_MACOS,
    icon_file=None,
    camera_usage='Shiraz provides camera access when an Android app requests the camera.',
    microphone_usage='Shiraz provides microphone access when an Android app requests audio capture.',
    location_usage='Shiraz provides location updates when an Android app requests GPS or network location.',
):
    if not bundle_id or any(ch.isspace() for ch in bundle_id):
        raise BundleError(f'Invalid CFBundleIdentifier: {bundle_id!r}')
    if not executable_name or Path(executable_name).name != executable_name:
        raise BundleError(f'Invalid CFBundleExecutable: {executable_name!r}')
    plist = {
        'CFBundleDevelopmentRegion': 'en',
        'CFBundleDisplayName': name,
        'CFBundleExecutable': executable_name,
        'CFBundleIdentifier': bundle_id,
        'CFBundleInfoDictionaryVersion': '6.0',
        'CFBundleName': name,
        'CFBundlePackageType': 'APPL',
        'CFBundleShortVersionString': version,
        'CFBundleVersion': version,
        'LSMinimumSystemVersion': min_macos,
        'NSHighResolutionCapable': True,
        'NSSupportsAutomaticGraphicsSwitching': True,
        'NSCameraUsageDescription': camera_usage,
        'NSMicrophoneUsageDescription': microphone_usage,
        'NSLocationUsageDescription': location_usage,
        'NSLocationWhenInUseUsageDescription': location_usage,
    }
    if icon_file:
        plist['CFBundleIconFile'] = icon_file
    return plist


def build_entitlements_plist(*, allow_jit=True, disable_library_validation=False):
    entitlements = {}
    if allow_jit:
        entitlements['com.apple.security.cs.allow-jit'] = True
    if disable_library_validation:
        entitlements['com.apple.security.cs.disable-library-validation'] = True
    return entitlements


def generate_icns_from_png(png_path, output_icns):
    """Convert a square PNG image into a macOS .icns archive via sips + iconutil."""
    png_path = Path(png_path)
    if not png_path.is_file():
        raise BundleError(f'Icon PNG not found: {png_path}')
    with tempfile.TemporaryDirectory(prefix='shiraz-iconset-') as tmp:
        iconset = Path(tmp) / 'AppIcon.iconset'
        iconset.mkdir()
        for filename, pixels in ICONSET_SIZES:
            target = iconset / filename
            run_tool(
                [
                    'sips',
                    '-z',
                    str(pixels),
                    str(pixels),
                    str(png_path),
                    '--out',
                    str(target),
                ]
            )
        run_tool(['iconutil', '-c', 'icns', str(iconset), '-o', str(output_icns)])


def ensure_writable(path):
    mode = os.stat(path).st_mode
    if not (mode & stat.S_IWUSR):
        os.chmod(path, mode | stat.S_IWUSR)


def collect_dylib_closure(executable_src, explicit_libs, search_dirs):
    """Return {bundled_name: resolved_source_path} for all non-system dependencies."""
    executable_src = Path(executable_src).resolve()
    executable_dir = executable_src.parent
    search = [executable_dir, *(Path(d).resolve() for d in search_dirs)]

    bundled = {}
    queue = deque()

    def enqueue_binary(src_path, is_dylib):
        deps, rpaths = inspect_macho(src_path)
        own_id = deps[0] if (is_dylib and deps) else None
        for dep in deps:
            if own_id is not None and dep == own_id:
                own_id = None
                continue
            if is_system_dylib(dep):
                continue
            resolved = resolve_dependency(dep, src_path, rpaths, executable_dir, search)
            if resolved is None:
                raise BundleError(
                    f'Unresolved non-system Mach-O dependency {dep!r} referenced by {src_path}'
                )
            name = resolved.name
            if name in bundled:
                if bundled[name] != resolved:
                    raise BundleError(
                        f'Conflicting bundled library name {name!r}: {bundled[name]} vs {resolved}'
                    )
                continue
            bundled[name] = resolved
            queue.append(resolved)

    for lib in explicit_libs:
        resolved = Path(lib).resolve()
        if not resolved.is_file():
            raise BundleError(f'Explicit library not found: {lib}')
        if resolved.name not in bundled:
            bundled[resolved.name] = resolved
            queue.append(resolved)

    enqueue_binary(executable_src, is_dylib=False)
    while queue:
        current = queue.popleft()
        enqueue_binary(current, is_dylib=True)

    return bundled


def rewrite_macho_links(target_path, bundled_names, *, is_dylib, add_rpaths):
    """Rewrite bundled dependencies to @rpath/<name> and ensure required LC_RPATHs."""
    ensure_writable(target_path)
    deps, existing_rpaths = inspect_macho(target_path)
    own_id = deps[0] if (is_dylib and deps) else None
    if is_dylib:
        desired_id = f'@rpath/{target_path.name}'
        if own_id != desired_id:
            run_tool(['install_name_tool', '-id', desired_id, str(target_path)])

    seen_changes = set()
    skipped_own = False
    for dep in deps:
        if is_dylib and not skipped_own and dep == own_id:
            skipped_own = True
            continue
        if is_system_dylib(dep):
            continue
        dep_name = Path(dep).name
        if dep_name not in bundled_names:
            raise BundleError(
                f'Refusing to leave unbundled non-system dependency {dep!r} in {target_path}'
            )
        desired = f'@rpath/{dep_name}'
        if dep != desired and dep not in seen_changes:
            run_tool(['install_name_tool', '-change', dep, desired, str(target_path)])
            seen_changes.add(dep)

    for rpath in add_rpaths:
        if rpath not in existing_rpaths:
            run_tool(['install_name_tool', '-add_rpath', rpath, str(target_path)])
            existing_rpaths.append(rpath)


def sign_bundle(
    app_dir,
    frameworks_dir,
    *,
    identity='-',
    hardened_runtime=True,
    entitlements_path=None,
):
    options = ['--options', 'runtime'] if hardened_runtime else []
    for item in sorted(frameworks_dir.iterdir()):
        if item.is_file():
            run_tool(['codesign', '--force', '--sign', identity, *options, str(item)])
    cmd = ['codesign', '--force', '--sign', identity, *options]
    if entitlements_path is not None:
        cmd.extend(['--entitlements', str(entitlements_path)])
    cmd.append(str(app_dir))
    run_tool(cmd)
    run_tool(['codesign', '--verify', '--deep', '--strict', str(app_dir)])


def build_macos_app(
    *,
    executable,
    output,
    name='Shiraz',
    bundle_id='org.shiraz.app',
    version=None,
    libraries=(),
    search_dirs=(),
    resources=(),
    icon=None,
    min_macos=DEFAULT_MIN_MACOS,
    sign=True,
    sign_identity='-',
    hardened_runtime=True,
    allow_jit=True,
    entitlements=None,
):
    executable_src = Path(executable).resolve()
    if not executable_src.is_file():
        raise BundleError(f'Executable not found: {executable}')
    output_app = Path(output).resolve()
    if output_app.suffix != '.app':
        raise BundleError(f'Output bundle path must end with .app: {output_app}')
    if version is None:
        version = read_repo_version()

    bundled_libs = collect_dylib_closure(executable_src, libraries, search_dirs)
    output_app.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(
        prefix=f'.{output_app.stem}-staging-', dir=str(output_app.parent)
    ) as tmp:
        staging_app = Path(tmp) / output_app.name
        contents = staging_app / 'Contents'
        macos_dir = contents / 'MacOS'
        frameworks_dir = contents / 'Frameworks'
        resources_dir = contents / 'Resources'
        for directory in (macos_dir, frameworks_dir, resources_dir):
            directory.mkdir(parents=True, exist_ok=True)

        executable_name = name.replace(' ', '')
        if not executable_name:
            executable_name = executable_src.name
        dest_exec = macos_dir / executable_name
        shutil.copy2(executable_src, dest_exec)
        os.chmod(dest_exec, 0o755)

        for lib_name, lib_src in sorted(bundled_libs.items()):
            dest_lib = frameworks_dir / lib_name
            shutil.copy2(lib_src, dest_lib)
            os.chmod(dest_lib, 0o755)

        icon_file_key = None
        if icon is not None:
            icon_src = Path(icon).resolve()
            if not icon_src.is_file():
                raise BundleError(f'Icon file not found: {icon}')
            if icon_src.suffix.lower() == '.icns':
                shutil.copy2(icon_src, resources_dir / 'AppIcon.icns')
                icon_file_key = 'AppIcon.icns'
            elif icon_src.suffix.lower() == '.png':
                generate_icns_from_png(icon_src, resources_dir / 'AppIcon.icns')
                icon_file_key = 'AppIcon.icns'
            else:
                raise BundleError(f'Icon must be .icns or .png: {icon_src}')

        for resource in resources:
            res_src = Path(resource).resolve()
            if not res_src.exists():
                raise BundleError(f'Resource not found: {resource}')
            dest = resources_dir / res_src.name
            if res_src.is_dir():
                shutil.copytree(res_src, dest)
            else:
                shutil.copy2(res_src, dest)

        bundled_names = set(bundled_libs.keys())
        for lib_name in sorted(bundled_names):
            rewrite_macho_links(
                frameworks_dir / lib_name,
                bundled_names,
                is_dylib=True,
                add_rpaths=['@loader_path'],
            )
        rewrite_macho_links(
            dest_exec,
            bundled_names,
            is_dylib=False,
            add_rpaths=['@executable_path/../Frameworks'],
        )

        plist_data = build_info_plist(
            name=name,
            bundle_id=bundle_id,
            executable_name=executable_name,
            version=version,
            min_macos=min_macos,
            icon_file=icon_file_key,
        )
        with (contents / 'Info.plist').open('wb') as handle:
            plistlib.dump(plist_data, handle, fmt=plistlib.FMT_XML)

        if sign:
            ent_file = None
            if entitlements is not None:
                ent_file = Path(entitlements).resolve()
                if not ent_file.is_file():
                    raise BundleError(f'Entitlements plist not found: {entitlements}')
            else:
                # Hardened runtime enforces Team ID library validation; ad-hoc
                # signatures ("-") carry no Team ID, so bundled Mach-O dylibs in
                # Contents/Frameworks require disable-library-validation locally.
                # Developer ID signatures keep strict library validation enabled.
                disable_lib_val = hardened_runtime and sign_identity == '-' and bool(bundled_names)
                ent_dict = build_entitlements_plist(
                    allow_jit=allow_jit,
                    disable_library_validation=disable_lib_val,
                )
                if ent_dict:
                    ent_file = Path(tmp) / 'entitlements.plist'
                    with ent_file.open('wb') as handle:
                        plistlib.dump(ent_dict, handle, fmt=plistlib.FMT_XML)
            sign_bundle(
                staging_app,
                frameworks_dir,
                identity=sign_identity,
                hardened_runtime=hardened_runtime,
                entitlements_path=ent_file,
            )

        if output_app.exists():
            shutil.rmtree(output_app)
        shutil.move(str(staging_app), str(output_app))

    return output_app


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--executable', required=True, type=Path, help='Main Mach-O binary')
    parser.add_argument('--output', required=True, type=Path, help='Output .app bundle path')
    parser.add_argument('--name', default='Shiraz', help='Bundle display name (default: Shiraz)')
    parser.add_argument(
        '--bundle-id',
        default='org.shiraz.app',
        help='CFBundleIdentifier (default: org.shiraz.app)',
    )
    parser.add_argument('--version', default=None, help='Bundle version (default: VERSION file)')
    parser.add_argument(
        '--lib',
        dest='libraries',
        action='append',
        default=[],
        type=Path,
        help='Extra dylib/.so to bundle into Contents/Frameworks (repeatable)',
    )
    parser.add_argument(
        '--search-dir',
        dest='search_dirs',
        action='append',
        default=[],
        type=Path,
        help='Extra search directory for @rpath dependencies (repeatable)',
    )
    parser.add_argument(
        '--resource',
        dest='resources',
        action='append',
        default=[],
        type=Path,
        help='Extra file or directory to copy into Contents/Resources (repeatable)',
    )
    parser.add_argument('--icon', type=Path, default=None, help='App icon (.icns or .png)')
    parser.add_argument(
        '--min-macos', default=DEFAULT_MIN_MACOS, help='LSMinimumSystemVersion (default: 15.0)'
    )
    parser.add_argument(
        '--sign-identity',
        default='-',
        help='codesign identity (default: "-" for ad-hoc signing)',
    )
    parser.add_argument('--no-sign', action='store_true', help='Skip codesign step')
    parser.add_argument(
        '--no-hardened-runtime', action='store_true', help='Omit --options runtime when signing'
    )
    parser.add_argument(
        '--no-jit-entitlement',
        action='store_true',
        help='Omit com.apple.security.cs.allow-jit entitlement',
    )
    parser.add_argument('--entitlements', type=Path, default=None, help='Custom entitlements plist')
    args = parser.parse_args(argv)

    try:
        app_path = build_macos_app(
            executable=args.executable,
            output=args.output,
            name=args.name,
            bundle_id=args.bundle_id,
            version=args.version,
            libraries=args.libraries,
            search_dirs=args.search_dirs,
            resources=args.resources,
            icon=args.icon,
            min_macos=args.min_macos,
            sign=not args.no_sign,
            sign_identity=args.sign_identity,
            hardened_runtime=not args.no_hardened_runtime,
            allow_jit=not args.no_jit_entitlement,
            entitlements=args.entitlements,
        )
    except BundleError as exc:
        print(f'error: {exc}', file=sys.stderr)
        return 1

    print(f'MACOS_APP_BUILT {app_path}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
