# SPDX-License-Identifier: GPL-3.0-only
"""Cross-platform host OS identity, standard directories, and binary naming.

Supports Linux (XDG Base Directory Specification), macOS (~/Library conventions
with explicit XDG overrides preserved for isolated test runs), and Windows
(%LOCALAPPDATA% / %APPDATA% conventions).
"""

from __future__ import annotations

import os
import platform
import re
from pathlib import Path, PurePath, PurePosixPath, PureWindowsPath
from typing import Mapping, Optional

_PACKAGE_RE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')


def host_os(system: Optional[str] = None) -> str:
    """Return normalized host OS identifier: 'linux', 'darwin', or 'windows'."""
    raw = (platform.system() if system is None else system).strip().lower()
    if raw == 'linux':
        return 'linux'
    if raw in ('darwin', 'macos', 'mac os x'):
        return 'darwin'
    if raw.startswith(('win', 'msys', 'mingw', 'cygwin')):
        return 'windows'
    raise ValueError(f'Unsupported host operating system: {system or platform.system()!r}')


def shared_library_name(stem: str, system: Optional[str] = None) -> str:
    """Return the runtime JNI library filename for a Shiraz native module.

    Linux and macOS keep the Android `.so` suffix (`lib<stem>.so`) so ART's
    `System.loadLibrary` matches APK native libraries uniformly. Windows uses
    `<stem>.dll` (or `lib<stem>.dll` only when the stem itself includes `lib`).
    """
    if not stem or '/' in stem or '\\' in stem:
        raise ValueError(f'Invalid library stem: {stem!r}')
    os_name = host_os(system)
    if os_name == 'windows':
        return f'{stem}.dll'
    return f'lib{stem}.so'


def host_shared_library_name(stem: str, system: Optional[str] = None) -> str:
    """Return the native host OS dynamic library name (.so / .dylib / .dll)."""
    if not stem or '/' in stem or '\\' in stem:
        raise ValueError(f'Invalid library stem: {stem!r}')
    os_name = host_os(system)
    if os_name == 'darwin':
        return f'lib{stem}.dylib'
    if os_name == 'windows':
        return f'{stem}.dll'
    return f'lib{stem}.so'


def executable_name(stem: str, system: Optional[str] = None) -> str:
    """Return the native executable filename (<stem>.exe on Windows)."""
    if not stem or '/' in stem or '\\' in stem:
        raise ValueError(f'Invalid executable stem: {stem!r}')
    if host_os(system) == 'windows':
        return stem if stem.lower().endswith('.exe') else f'{stem}.exe'
    return stem


def _pure_path(value: str, os_name: str) -> PurePath:
    return PureWindowsPath(value) if os_name == 'windows' else PurePosixPath(value)


def _is_absolute(value: str, os_name: str) -> bool:
    if not value:
        return False
    return _pure_path(value, os_name).is_absolute()


def _home(env: Mapping[str, str], os_name: str) -> PurePath:
    if os_name == 'windows':
        for key in ('USERPROFILE', 'HOME'):
            val = env.get(key, '')
            if _is_absolute(val, os_name):
                return PureWindowsPath(val)
        drive, path = env.get('HOMEDRIVE', ''), env.get('HOMEPATH', '')
        if drive and path and _is_absolute(drive + path, os_name):
            return PureWindowsPath(drive + path)
        raise ValueError('An absolute USERPROFILE or LOCALAPPDATA directory is required on Windows')
    home = env.get('HOME', '')
    if not _is_absolute(home, os_name):
        raise ValueError('An absolute HOME or XDG base directory is required')
    return PurePosixPath(home)


def _override(env: Mapping[str, str], xdg_key: str, os_name: str) -> Optional[PurePath]:
    for key in ('SHIRAZ_HOST_' + xdg_key, xdg_key):
        val = env.get(key, '')
        if _is_absolute(val, os_name):
            return _pure_path(val, os_name)
    return None


def data_home(
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return the persistent Shiraz application data root for the host OS.

    - Linux:   $XDG_DATA_HOME/shiraz (default: ~/.local/share/shiraz)
    - macOS:   ~/Library/Application Support/Shiraz (unless XDG override is set)
    - Windows: %LOCALAPPDATA%\\Shiraz (unless XDG override is set)
    """
    env_map = os.environ if env is None else env
    os_name = host_os(system)
    override = _override(env_map, 'XDG_DATA_HOME', os_name)
    if override is not None:
        return override / 'shiraz'
    if os_name == 'darwin':
        return _home(env_map, os_name) / 'Library' / 'Application Support' / 'Shiraz'
    if os_name == 'windows':
        local = env_map.get('LOCALAPPDATA', '')
        if _is_absolute(local, os_name):
            return PureWindowsPath(local) / 'Shiraz'
        return _home(env_map, os_name) / 'AppData' / 'Local' / 'Shiraz'
    return _home(env_map, os_name) / '.local' / 'share' / 'shiraz'


def cache_home(
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return the Shiraz cache root for the host OS.

    - Linux:   $XDG_CACHE_HOME/shiraz (default: ~/.cache/shiraz)
    - macOS:   ~/Library/Caches/Shiraz (unless XDG override is set)
    - Windows: %LOCALAPPDATA%\\Shiraz\\Cache (unless XDG override is set)
    """
    env_map = os.environ if env is None else env
    os_name = host_os(system)
    override = _override(env_map, 'XDG_CACHE_HOME', os_name)
    if override is not None:
        return override / 'shiraz'
    if os_name == 'darwin':
        return _home(env_map, os_name) / 'Library' / 'Caches' / 'Shiraz'
    if os_name == 'windows':
        local = env_map.get('LOCALAPPDATA', '')
        base = (
            PureWindowsPath(local)
            if _is_absolute(local, os_name)
            else _home(env_map, os_name) / 'AppData' / 'Local'
        )
        return base / 'Shiraz' / 'Cache'
    return _home(env_map, os_name) / '.cache' / 'shiraz'


def config_home(
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return the Shiraz configuration root for the host OS."""
    env_map = os.environ if env is None else env
    os_name = host_os(system)
    override = _override(env_map, 'XDG_CONFIG_HOME', os_name)
    if override is not None:
        return override / 'shiraz'
    if os_name == 'darwin':
        return _home(env_map, os_name) / 'Library' / 'Application Support' / 'Shiraz' / 'Config'
    if os_name == 'windows':
        roaming = env_map.get('APPDATA', '')
        if _is_absolute(roaming, os_name):
            return PureWindowsPath(roaming) / 'Shiraz'
        return _home(env_map, os_name) / 'AppData' / 'Roaming' / 'Shiraz'
    return _home(env_map, os_name) / '.config' / 'shiraz'


def state_home(
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return the Shiraz state/audit root for the host OS."""
    env_map = os.environ if env is None else env
    os_name = host_os(system)
    override = _override(env_map, 'XDG_STATE_HOME', os_name)
    if override is not None:
        return override / 'shiraz'
    if os_name == 'darwin':
        return _home(env_map, os_name) / 'Library' / 'Application Support' / 'Shiraz' / 'State'
    if os_name == 'windows':
        local = env_map.get('LOCALAPPDATA', '')
        base = (
            PureWindowsPath(local)
            if _is_absolute(local, os_name)
            else _home(env_map, os_name) / 'AppData' / 'Local'
        )
        return base / 'Shiraz' / 'State'
    return _home(env_map, os_name) / '.local' / 'state' / 'shiraz'


def _validate_package(package: str) -> str:
    if not isinstance(package, str) or not _PACKAGE_RE.fullmatch(package):
        raise ValueError(f'Invalid Android package name: {package!r}')
    return package


def app_data_dir(
    package: str,
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return per-APK persistent data directory on the host OS."""
    return data_home(env=env, system=system) / _validate_package(package)


def app_cache_dir(
    package: str,
    env: Optional[Mapping[str, str]] = None,
    system: Optional[str] = None,
) -> PurePath:
    """Return per-APK cache directory on the host OS."""
    return cache_home(env=env, system=system) / _validate_package(package)


def host_path(pure: PurePath) -> Path:
    """Convert a PurePath returned by this module to a concrete host Path."""
    return Path(str(pure))
