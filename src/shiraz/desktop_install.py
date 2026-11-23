# SPDX-License-Identifier: GPL-3.0-only
"""Cross-platform per-APK desktop launcher installation (Linux, macOS, Windows).

- Linux:   Freedesktop .desktop entry under ~/.local/share/applications/ + hicolor PNG icon.
- macOS:   Per-APK wrapper .app bundle under ~/Applications/<Label>.app/.
- Windows: Start Menu launcher under %APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\Shiraz\\.
"""

from __future__ import annotations

import json
import os
import plistlib
import re
import shlex
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from .platform_paths import data_home, host_os

_PACKAGE_RE = re.compile(r'^[A-Za-z][A-Za-z0-9._-]{0,199}$')
_CONTROL_RE = re.compile(r'[\x00-\x1f\x7f]')
_UNSAFE_FILENAME_RE = re.compile(r'[<>:"/\\|?*\x00-\x1f\x7f]+')
_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class DesktopInstallResult:
    os_name: str
    package_name: str
    label: str
    launcher_path: Path
    icon_path: Optional[Path] = None
    metadata_path: Optional[Path] = None

    def to_dict(self) -> Dict[str, Optional[str]]:
        return {
            'os': self.os_name,
            'package': self.package_name,
            'label': self.label,
            'launcher': str(self.launcher_path),
            'icon': str(self.icon_path) if self.icon_path else None,
            'metadata': str(self.metadata_path) if self.metadata_path else None,
        }


def _validate_package(package_name: str) -> str:
    if not package_name or not _PACKAGE_RE.match(package_name) or '..' in package_name:
        raise ValueError(f'Invalid Android package name: {package_name!r}')
    return package_name


def _sanitize_label(label: str, fallback: str) -> str:
    cleaned = _CONTROL_RE.sub(' ', (label or '').strip())
    cleaned = ' '.join(cleaned.split())[:80]
    return cleaned or fallback


def _safe_filename(label: str, fallback: str) -> str:
    name = _UNSAFE_FILENAME_RE.sub('_', label).strip(' .')
    return name[:80] or fallback


def _shiraz_version() -> str:
    version_file = _ROOT / 'VERSION'
    return version_file.read_text(encoding='utf-8').strip() if version_file.is_file() else '0.0.0'


def _desktop_exec_quote(arg: str) -> str:
    if any(ch in arg for ch in ('\n', '\r', '\0')):
        raise ValueError(f'Unsupported control character in desktop command argument: {arg!r}')
    escaped = []
    for ch in arg:
        if ch == '%':
            escaped.append('%%')
        elif ch in ('\\', '"', '`', '$'):
            escaped.append('\\' + ch)
        else:
            escaped.append(ch)
    quoted = '"' + ''.join(escaped) + '"'
    return quoted.replace('\\', '\\\\')


def _desktop_escape_string(value: str) -> str:
    return (
        value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    )


def _cmd_escape(value: str) -> str:
    if any(ch in value for ch in ('\n', '\r', '\0', '"')):
        raise ValueError(f'Unsupported character for Windows batch argument: {value!r}')
    return value.replace('%', '%%')


def _default_applications_dir(os_name: str) -> Path:
    if os_name == 'darwin':
        return Path.home() / 'Applications'
    if os_name == 'windows':
        appdata = os.environ.get('APPDATA')
        base = Path(appdata) if appdata else (Path.home() / 'AppData' / 'Roaming')
        return base / 'Microsoft' / 'Windows' / 'Start Menu' / 'Programs' / 'Shiraz'
    return data_home(system='Linux') / 'applications'


def _build_run_args(
    apk_path: Path,
    *,
    sandbox: bool = False,
    allow_network: bool = True,
) -> List[str]:
    if not allow_network and not sandbox:
        raise ValueError('allow_network=False requires sandbox=True')
    args = ['-m', 'shiraz.cli', 'run', str(apk_path)]
    if sandbox:
        args.append('--sandbox')
    if not allow_network:
        args.append('--no-network')
    return args


def _convert_png_to_icns(png_path: Path, icns_path: Path) -> bool:
    if sys.platform != 'darwin':
        return False
    if not (shutil.which('sips') and shutil.which('iconutil')):
        return False
    iconset = icns_path.with_suffix('.iconset')
    if iconset.exists():
        shutil.rmtree(iconset)
    iconset.mkdir(parents=True, exist_ok=True)
    try:
        sizes = [
            (16, 'icon_16x16.png'),
            (32, 'icon_16x16@2x.png'),
            (32, 'icon_32x32.png'),
            (64, 'icon_32x32@2x.png'),
            (128, 'icon_128x128.png'),
            (256, 'icon_128x128@2x.png'),
            (256, 'icon_256x256.png'),
            (512, 'icon_256x256@2x.png'),
        ]
        for px, filename in sizes:
            res = subprocess.run(
                ['sips', '-z', str(px), str(px), str(png_path), '--out', str(iconset / filename)],
                capture_output=True,
                timeout=20,
            )
            if res.returncode != 0:
                return False
        res = subprocess.run(
            ['iconutil', '-c', 'icns', str(iconset), '-o', str(icns_path)],
            capture_output=True,
            timeout=20,
        )
        return res.returncode == 0 and icns_path.is_file()
    except (OSError, subprocess.SubprocessError):
        return False
    finally:
        shutil.rmtree(iconset, ignore_errors=True)


def install_desktop_app(
    apk_path: Path,
    package_name: str,
    label: str,
    *,
    icon_path: Optional[Path] = None,
    sandbox: bool = False,
    allow_network: bool = True,
    applications_dir: Optional[Path] = None,
    python_executable: Optional[str] = None,
    system: Optional[str] = None,
) -> DesktopInstallResult:
    """Install a native OS desktop launcher for an Android APK."""
    pkg = _validate_package(package_name)
    display_label = _sanitize_label(label, fallback=pkg)
    safe_name = _safe_filename(display_label, fallback=pkg)
    apk = Path(apk_path).resolve()
    if not apk.is_file():
        raise FileNotFoundError(f'APK not found: {apk}')

    os_name = host_os(system)
    apps_dir = (
        Path(applications_dir)
        if applications_dir is not None
        else _default_applications_dir(os_name)
    )
    apps_dir.mkdir(parents=True, exist_ok=True)
    py_exe = python_executable or sys.executable
    run_args = _build_run_args(apk, sandbox=sandbox, allow_network=allow_network)
    src_dir = _ROOT / 'src'
    pythonpath = str(src_dir) if src_dir.is_dir() else ''

    if os_name == 'linux':
        desktop_id = f'{pkg}.Shiraz'
        desktop_file = apps_dir / f'{desktop_id}.desktop'
        if desktop_file.is_symlink():
            raise ValueError(f'Refusing to overwrite symlink: {desktop_file}')

        installed_icon: Optional[Path] = None
        if icon_path is not None and Path(icon_path).is_file():
            icons_dir = apps_dir.parent / 'icons' / 'hicolor' / '256x256' / 'apps'
            icons_dir.mkdir(parents=True, exist_ok=True)
            target_icon = icons_dir / f'{desktop_id}.png'
            if target_icon.is_symlink():
                raise ValueError(f'Refusing to overwrite icon symlink: {target_icon}')
            shutil.copy2(icon_path, target_icon)
            installed_icon = target_icon

        exec_parts = ['env', f'PYTHONPATH={pythonpath}'] if pythonpath else []
        exec_parts.extend([py_exe, *run_args])
        exec_line = ' '.join(_desktop_exec_quote(part) for part in exec_parts)
        lines = [
            '[Desktop Entry]',
            'Type=Application',
            'Version=1.0',
            f'Name={_desktop_escape_string(display_label)}',
            f'Comment=Android application ({pkg}) via Shiraz',
            f'Exec={exec_line}',
            f'Icon={_desktop_escape_string(str(installed_icon)) if installed_icon else "application-x-executable"}',
            'Terminal=false',
            'Categories=Utility;',
            f'StartupWMClass={_desktop_escape_string(pkg)}',
            'X-Purism-FormFactor=Workstation;Mobile;',
            f'X-Shiraz-Package={_desktop_escape_string(pkg)}',
            f'X-Shiraz-APK={_desktop_escape_string(str(apk))}',
        ]
        tmp = desktop_file.with_suffix('.desktop.tmp')
        tmp.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        os.replace(tmp, desktop_file)
        return DesktopInstallResult(
            os_name='linux',
            package_name=pkg,
            label=display_label,
            launcher_path=desktop_file,
            icon_path=installed_icon,
        )

    if os_name == 'darwin':
        bundle = apps_dir / f'{safe_name}.app'
        if bundle.is_symlink():
            raise ValueError(f'Refusing to overwrite symlink: {bundle}')
        contents = bundle / 'Contents'
        macos_dir = contents / 'MacOS'
        resources_dir = contents / 'Resources'
        macos_dir.mkdir(parents=True, exist_ok=True)
        resources_dir.mkdir(parents=True, exist_ok=True)

        installed_icon = None
        icon_file_key: Optional[str] = None
        if icon_path is not None and Path(icon_path).is_file():
            src_icon = Path(icon_path)
            if src_icon.suffix.lower() == '.icns':
                target_icns = resources_dir / 'AppIcon.icns'
                shutil.copy2(src_icon, target_icns)
                installed_icon = target_icns
                icon_file_key = 'AppIcon'
            else:
                target_icns = resources_dir / 'AppIcon.icns'
                if system is None and _convert_png_to_icns(src_icon, target_icns):
                    installed_icon = target_icns
                    icon_file_key = 'AppIcon'
                else:
                    target_png = resources_dir / 'AppIcon.png'
                    shutil.copy2(src_icon, target_png)
                    installed_icon = target_png
                    icon_file_key = 'AppIcon.png'

        version = _shiraz_version()
        info: Dict[str, object] = {
            'CFBundleIdentifier': f'org.shiraz.app.{pkg}',
            'CFBundleName': display_label[:64],
            'CFBundleDisplayName': display_label[:64],
            'CFBundleExecutable': 'launch',
            'CFBundlePackageType': 'APPL',
            'CFBundleShortVersionString': version,
            'CFBundleVersion': version,
            'NSHighResolutionCapable': True,
            'ShirazManaged': True,
            'ShirazPackageName': pkg,
            'ShirazApkPath': str(apk),
            'NSCameraUsageDescription': (
                'Shiraz provides camera access to this Android app after you grant it camera permission.'
            ),
            'NSMicrophoneUsageDescription': (
                'Shiraz provides microphone access to this Android app after you grant it microphone permission.'
            ),
            'NSLocationUsageDescription': (
                'Shiraz provides location access to this Android app after you grant it location permission.'
            ),
            'NSLocationWhenInUseUsageDescription': (
                'Shiraz provides location access to this Android app while in use after you grant it location permission.'
            ),
        }
        if icon_file_key:
            info['CFBundleIconFile'] = icon_file_key

        plist_path = contents / 'Info.plist'
        plist_tmp = contents / '.Info.plist.tmp'
        plist_tmp.write_bytes(plistlib.dumps(info, fmt=plistlib.FMT_XML, sort_keys=True))
        os.replace(plist_tmp, plist_path)

        cmd_quoted = ' '.join(shlex.quote(arg) for arg in [py_exe, *run_args])
        script_lines = ['#!/bin/sh', 'set -eu']
        if pythonpath:
            script_lines.append(f'export PYTHONPATH={shlex.quote(pythonpath)}:"${{PYTHONPATH:-}}"')
        script_lines.append(f'exec {cmd_quoted} "$@"')
        launcher_script = macos_dir / 'launch'
        script_tmp = macos_dir / '.launch.tmp'
        script_tmp.write_text('\n'.join(script_lines) + '\n', encoding='utf-8')
        os.chmod(script_tmp, 0o755)
        os.replace(script_tmp, launcher_script)

        return DesktopInstallResult(
            os_name='darwin',
            package_name=pkg,
            label=display_label,
            launcher_path=bundle,
            icon_path=installed_icon,
            metadata_path=plist_path,
        )

    # Windows Start Menu launcher (.cmd + metadata sidecar)
    launcher_cmd = apps_dir / f'{safe_name}.cmd'
    if launcher_cmd.is_symlink():
        raise ValueError(f'Refusing to overwrite symlink: {launcher_cmd}')
    installed_icon = None
    if icon_path is not None and Path(icon_path).is_file():
        target_icon = apps_dir / f'{safe_name}{Path(icon_path).suffix or ".png"}'
        shutil.copy2(icon_path, target_icon)
        installed_icon = target_icon

    cmd_args = ' '.join(f'"{_cmd_escape(arg)}"' for arg in [py_exe, *run_args])
    cmd_lines = ['@echo off', 'setlocal']
    if pythonpath:
        cmd_lines.append(f'set "PYTHONPATH={_cmd_escape(pythonpath)};%PYTHONPATH%"')
    cmd_lines.append(f'{cmd_args} %*')
    tmp_cmd = apps_dir / f'.{safe_name}.cmd.tmp'
    tmp_cmd.write_text('\r\n'.join(cmd_lines) + '\r\n', encoding='utf-8')
    os.replace(tmp_cmd, launcher_cmd)

    meta_path = apps_dir / f'{pkg}.shiraz.json'
    meta = {
        'managed': True,
        'package': pkg,
        'label': display_label,
        'apk': str(apk),
        'launcher': str(launcher_cmd),
        'icon': str(installed_icon) if installed_icon else None,
        'sandbox': sandbox,
        'allow_network': allow_network,
    }
    meta_path.write_text(json.dumps(meta, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return DesktopInstallResult(
        os_name='windows',
        package_name=pkg,
        label=display_label,
        launcher_path=launcher_cmd,
        icon_path=installed_icon,
        metadata_path=meta_path,
    )


def uninstall_desktop_app(
    package_name: str,
    *,
    applications_dir: Optional[Path] = None,
    system: Optional[str] = None,
) -> List[Path]:
    """Remove a Shiraz-managed desktop launcher for *package_name*."""
    pkg = _validate_package(package_name)
    os_name = host_os(system)
    apps_dir = (
        Path(applications_dir)
        if applications_dir is not None
        else _default_applications_dir(os_name)
    )
    removed: List[Path] = []
    if not apps_dir.is_dir():
        return removed

    if os_name == 'linux':
        desktop_id = f'{pkg}.Shiraz'
        desktop_file = apps_dir / f'{desktop_id}.desktop'
        if desktop_file.is_file() and not desktop_file.is_symlink():
            text = desktop_file.read_text(encoding='utf-8', errors='replace')
            if f'X-Shiraz-Package={pkg}' in text:
                desktop_file.unlink()
                removed.append(desktop_file)
        icon_file = apps_dir.parent / 'icons' / 'hicolor' / '256x256' / 'apps' / f'{desktop_id}.png'
        if icon_file.is_file() and not icon_file.is_symlink():
            icon_file.unlink()
            removed.append(icon_file)
        return removed

    if os_name == 'darwin':
        for entry in sorted(apps_dir.glob('*.app')):
            if entry.is_symlink() or not entry.is_dir():
                continue
            plist_path = entry / 'Contents' / 'Info.plist'
            if not plist_path.is_file():
                continue
            try:
                info = plistlib.loads(plist_path.read_bytes())
            except Exception:
                continue
            if info.get('ShirazManaged') is True and info.get('ShirazPackageName') == pkg:
                shutil.rmtree(entry)
                removed.append(entry)
        return removed

    meta_path = apps_dir / f'{pkg}.shiraz.json'
    if meta_path.is_file() and not meta_path.is_symlink():
        try:
            meta = json.loads(meta_path.read_text(encoding='utf-8'))
        except Exception:
            meta = {}
        if meta.get('managed') is True and meta.get('package') == pkg:
            for key in ('launcher', 'icon'):
                target = meta.get(key)
                if not target:
                    continue
                target_path = Path(target)
                if (
                    target_path.parent.resolve() == apps_dir.resolve()
                    and target_path.is_file()
                    and not target_path.is_symlink()
                ):
                    target_path.unlink()
                    removed.append(target_path)
            meta_path.unlink()
            removed.append(meta_path)
    return removed
