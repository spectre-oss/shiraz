# SPDX-License-Identifier: GPL-3.0-only
"""Persistent SpectreOS Shiraz daemon, memory pre-warmer, and Freedesktop bridge.

Respects Freedesktop specifications:
- XDG Base Directory v0.8 (`XDG_CONFIG_HOME`, `XDG_DATA_HOME`, `XDG_CACHE_HOME`, `XDG_RUNTIME_DIR`)
- Desktop Entry Specification v1.5 (`$XDG_DATA_HOME/applications/<pkg>.Shiraz.desktop`)
- Icon Theme Specification v0.13 (`$XDG_DATA_HOME/icons/hicolor/256x256/apps/<pkg>.Shiraz.png`)
- MIME Apps Specification v1.0.1 (`application/vnd.android.package-archive`)
- Desktop Application Autostart v0.5 (`$XDG_CONFIG_HOME/autostart/org.spectreos.Shiraz.desktop`)
- Desktop Notifications v1.3 (`SHIRAZ_DESKTOP_ENTRY` -> `app_icon` + `desktop-entry` hint)
"""

from __future__ import annotations

import json
import mmap
import os
import shutil
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import zlib
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from .aot_cache import (
    aot_cache_root,
    app_aot_ready,
    boot_image_ready,
    collect_warm_files,
    ensure_app_aot,
    ensure_boot_and_framework_aot,
    framework_aot_ready,
)
from .apk import FormatError, inspect_apk
from .app_icon import extract_icon, resolve_label
from .host_session import HostSession
from .package_registry import PackageRegistry
from .runtime import ROOT

_DESKTOP_CONTROL_TRANSLATE = str.maketrans({'\n': ' ', '\r': ' ', '\t': ' ', '\0': ''})


def xdg_config_home(env: Optional[Dict[str, str]] = None) -> Path:
    e = os.environ if env is None else env
    val = e.get('XDG_CONFIG_HOME', '')
    if val and Path(val).is_absolute():
        return Path(val)
    return Path(e.get('HOME') or str(Path.home())) / '.config'


def xdg_data_home(env: Optional[Dict[str, str]] = None) -> Path:
    e = os.environ if env is None else env
    val = e.get('XDG_DATA_HOME', '')
    if val and Path(val).is_absolute():
        return Path(val)
    return Path(e.get('HOME') or str(Path.home())) / '.local/share'


def xdg_cache_home(env: Optional[Dict[str, str]] = None) -> Path:
    e = os.environ if env is None else env
    val = e.get('XDG_CACHE_HOME', '')
    if val and Path(val).is_absolute():
        return Path(val)
    return Path(e.get('HOME') or str(Path.home())) / '.cache'


def xdg_runtime_dir(env: Optional[Dict[str, str]] = None) -> Path:
    e = os.environ if env is None else env
    val = e.get('XDG_RUNTIME_DIR', '')
    if val and Path(val).is_absolute():
        return Path(val)
    fallback = Path(tempfile.gettempdir()) / f'shiraz-runtime-{os.getuid()}'
    fallback.mkdir(parents=True, exist_ok=True, mode=0o700)
    return fallback


def default_registry_dir(env: Optional[Dict[str, str]] = None) -> Path:
    return xdg_data_home(env) / 'shiraz' / 'registry'


def runtime_dir(env: Optional[Dict[str, str]] = None) -> Path:
    d = xdg_runtime_dir(env) / 'shiraz'
    d.mkdir(parents=True, exist_ok=True, mode=0o700)
    return d


def control_socket_path(env: Optional[Dict[str, str]] = None) -> Path:
    return runtime_dir(env) / 'control.sock'


def status_json_path(env: Optional[Dict[str, str]] = None) -> Path:
    return runtime_dir(env) / 'status.json'


def _escape_desktop_value(value: str) -> str:
    return value.translate(_DESKTOP_CONTROL_TRANSLATE).replace('\\', '\\\\').strip()


def _categories_for_package(package: str, label: str) -> str:
    low = f'{package} {label}'.lower()
    if any(
        k in low for k in ('delta', 'chat', 'signal', 'telegram', 'messenger', 'matrix', 'element')
    ):
        return 'Network;InstantMessaging;'
    if any(k in low for k in ('osmand', 'maps', 'navigation', 'gps', 'geo')):
        return 'Utility;Geography;Maps;'
    if any(k in low for k in ('music', 'audio', 'podcast', 'player', 'media', 'vlc')):
        return 'AudioVideo;Audio;'
    if any(k in low for k in ('browser', 'firefox', 'chromium', 'web')):
        return 'Network;WebBrowser;'
    if any(k in low for k in ('mail', 'k9', 'thunderbird')):
        return 'Network;Email;'
    return 'Utility;'


def _write_fallback_png(dest: Path, label: str, size: int = 128) -> None:
    """Write a crisp rounded-square fallback RGBA PNG without external dependencies."""
    seed = sum(ord(c) for c in (label or 'A')) % 6
    palette = [
        (16, 185, 129),
        (59, 130, 246),
        (245, 158, 11),
        (139, 92, 246),
        (236, 72, 153),
        (14, 165, 233),
    ]
    r, g, b = palette[seed]
    rows = bytearray()
    cx = cy = (size - 1) * 0.5
    rad = size * 0.44
    inner = size * 0.20
    for y in range(size):
        rows.append(0)
        for x in range(size):
            dx = x - cx
            dy = y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            if dist <= inner:
                rows.extend((255, 255, 255, 255))
            elif dist <= rad:
                shade = max(0.75, 1.0 - (y / size) * 0.22)
                rows.extend((int(r * shade), int(g * shade), int(b * shade), 255))
            else:
                rows.extend((0, 0, 0, 0))

    def chunk(tag: bytes, payload: bytes) -> bytes:
        crc = zlib.crc32(tag + payload) & 0xFFFFFFFF
        return struct.pack('>I', len(payload)) + tag + payload + struct.pack('>I', crc)

    ihdr = struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)
    png = (
        b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', ihdr)
        + chunk(b'IDAT', zlib.compress(bytes(rows), 6))
        + chunk(b'IEND', b'')
    )
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix('.png.tmp')
    tmp.write_bytes(png)
    os.replace(tmp, dest)


def _convert_image_to_png(src: Path, dest: Path) -> bool:
    """Convert PNG/WebP/JPEG icon bytes to a valid PNG file at `dest`."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    header = src.read_bytes()[:12]
    if header.startswith(b'\x89PNG\r\n\x1a\n'):
        tmp = dest.with_suffix('.png.tmp')
        shutil.copy2(src, tmp)
        os.replace(tmp, dest)
        return True
    try:
        from PIL import Image  # type: ignore

        with Image.open(src) as img:
            rgba = img.convert('RGBA')
            tmp = dest.with_suffix('.png.tmp')
            rgba.save(tmp, format='PNG')
            os.replace(tmp, dest)
            return True
    except Exception:
        pass
    for tool, build_cmd in (
        ('dwebp', lambda s, d: ['dwebp', str(s), '-o', str(d)]),
        ('magick', lambda s, d: ['magick', str(s), str(d)]),
        ('convert', lambda s, d: ['convert', str(s), str(d)]),
        ('ffmpeg', lambda s, d: ['ffmpeg', '-y', '-loglevel', 'error', '-i', str(s), str(d)]),
    ):
        if shutil.which(tool):
            tmp = dest.with_suffix('.png.tmp')
            try:
                res = subprocess.run(build_cmd(src, tmp), capture_output=True, timeout=15)
                if (
                    res.returncode == 0
                    and tmp.is_file()
                    and tmp.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
                ):
                    os.replace(tmp, dest)
                    return True
            except (OSError, subprocess.SubprocessError):
                pass
            finally:
                tmp.unlink(missing_ok=True)
    return False


def extract_launcher_icon_png(apk: Path, dest_png: Path, label: str = '') -> Path:
    """Extract the highest-density APK launcher icon as a hicolor PNG, with clean fallback."""
    dest_png.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='shiraz-icon-') as tmp:
        out_dir = Path(tmp)
        try:
            meta = extract_icon(apk, out_dir)
            candidates: List[Path] = []
            if meta.get('kind') == 'bitmap':
                img_name = meta.get('image') or meta.get('file')
                if isinstance(img_name, str) and img_name:
                    candidates.append(out_dir / img_name)
            elif meta.get('kind') == 'adaptive':
                for layer in ('foreground', 'background'):
                    item = meta.get(layer)
                    if isinstance(item, str) and item:
                        candidates.append(out_dir / item)
                    elif isinstance(item, dict):
                        fname = item.get('image') or item.get('file')
                        if isinstance(fname, str) and fname:
                            candidates.append(out_dir / fname)
            for cand in candidates:
                if cand.is_file() and _convert_image_to_png(cand, dest_png):
                    return dest_png
        except Exception:
            pass
    _write_fallback_png(dest_png, label or apk.stem)
    return dest_png


def _resolve_wrapper_exec(tool_name: str, fallback_subcmd: List[str]) -> str:
    found = shutil.which(tool_name)
    if found:
        return found
    local_bin = ROOT / 'bin' / tool_name
    if local_bin.is_file() and os.access(local_bin, os.X_OK):
        return str(local_bin)
    return (
        f'env SHIRAZ_HOME={ROOT} PYTHONPATH={ROOT / "src"} '
        f'{sys.executable} -m shiraz ' + ' '.join(fallback_subcmd)
    )


def write_desktop_entry(
    package: str,
    label: str,
    *,
    version_name: str = 'Android',
    aot_compiled: bool = False,
    env: Optional[Dict[str, str]] = None,
) -> Path:
    """Write a Freedesktop Desktop Entry v1.5 launcher for an enrolled Android package."""
    apps_dir = xdg_data_home(env) / 'applications'
    apps_dir.mkdir(parents=True, exist_ok=True)
    desktop_id = f'{package}.Shiraz'
    desktop_path = apps_dir / f'{desktop_id}.desktop'
    if desktop_path.is_symlink():
        raise FormatError(f'Refusing to overwrite symlink: {desktop_path}')

    launch_cmd = _resolve_wrapper_exec('shiraz-launch', ['launch-app'])
    ctl_cmd = _resolve_wrapper_exec('shiraz-ctl', ['ctl'])
    settings_cmd = shutil.which('spectre-settings') or 'spectre-settings'

    safe_label = _escape_desktop_value(label or package)
    safe_ver = _escape_desktop_value(version_name or 'Android')
    categories = _categories_for_package(package, safe_label)

    lines = [
        '[Desktop Entry]',
        'Type=Application',
        'Version=1.5',
        f'Name={safe_label}',
        'GenericName=Android App',
        f'Comment=Android application ({package}) powered by Shiraz',
        f'Exec={launch_cmd} {package}',
        f'Icon={desktop_id}',
        'Terminal=false',
        'StartupNotify=true',
        f'StartupWMClass={desktop_id}',
        f'Categories={categories}',
        f'Keywords=android;shiraz;{package};',
        'X-Purism-FormFactor=Workstation;Mobile;',
        f'X-Shiraz-Package={package}',
        f'X-Shiraz-Version={safe_ver}',
        f'X-Shiraz-AOT={"true" if aot_compiled else "false"}',
        'Actions=force-stop;app-info;',
        '',
        '[Desktop Action force-stop]',
        'Name=Force stop',
        f'Exec={ctl_cmd} stop {package}',
        '',
        '[Desktop Action app-info]',
        'Name=Shiraz settings',
        f'Exec={settings_cmd} --page shiraz',
        '',
    ]
    tmp = desktop_path.with_suffix('.desktop.tmp')
    tmp.write_text('\n'.join(lines), encoding='utf-8')
    os.replace(tmp, desktop_path)
    return desktop_path


def ensure_mime_apk_handler(env: Optional[Dict[str, str]] = None) -> Dict[str, str]:
    """Register `application/vnd.android.package-archive` via Freedesktop MIME Apps v1.0.1."""
    apps_dir = xdg_data_home(env) / 'applications'
    apps_dir.mkdir(parents=True, exist_ok=True)
    installer_desktop = apps_dir / 'org.spectreos.ShirazInstaller.desktop'
    install_cmd = _resolve_wrapper_exec('shiraz-install-apk', ['install-app'])
    body = '\n'.join(
        [
            '[Desktop Entry]',
            'Type=Application',
            'Version=1.5',
            'Name=Shiraz APK Installer',
            'Comment=Install Android APK packages into SpectreOS & Wisp Launcher',
            f'Exec={install_cmd} %f',
            'Icon=application-vnd.android.package-archive',
            'MimeType=application/vnd.android.package-archive;',
            'Terminal=false',
            'NoDisplay=true',
            'Categories=System;PackageManager;',
            'X-Purism-FormFactor=Workstation;Mobile;',
            '',
        ]
    )
    tmp = installer_desktop.with_suffix('.desktop.tmp')
    tmp.write_text(body, encoding='utf-8')
    os.replace(tmp, installer_desktop)

    cfg_dir = xdg_config_home(env)
    cfg_dir.mkdir(parents=True, exist_ok=True)
    mimeapps = cfg_dir / 'mimeapps.list'
    mime_key = 'application/vnd.android.package-archive'
    desktop_id = 'org.spectreos.ShirazInstaller.desktop'
    sections: Dict[str, List[str]] = {}
    section_order: List[str] = []
    if mimeapps.is_file():
        current = None
        for raw in mimeapps.read_text(encoding='utf-8', errors='replace').splitlines():
            line = raw.strip()
            if line.startswith('[') and line.endswith(']'):
                current = line[1:-1]
                if current not in sections:
                    sections[current] = []
                    section_order.append(current)
            elif current is not None and line:
                sections[current].append(line)

    for sec in ('Default Applications', 'Added Associations'):
        if sec not in sections:
            sections[sec] = []
            section_order.append(sec)
        updated = False
        for i, entry in enumerate(sections[sec]):
            if entry.startswith(mime_key + '='):
                existing_val = entry.split('=', 1)[1]
                items = [x for x in existing_val.split(';') if x]
                if desktop_id not in items:
                    items.insert(0, desktop_id)
                sections[sec][i] = f'{mime_key}=' + ';'.join(items) + ';'
                updated = True
                break
        if not updated:
            sections[sec].append(f'{mime_key}={desktop_id};')

    out_lines: List[str] = []
    for sec in section_order:
        out_lines.append(f'[{sec}]')
        out_lines.extend(sections[sec])
        out_lines.append('')
    tmp_mime = mimeapps.with_suffix('.list.tmp')
    tmp_mime.write_text('\n'.join(out_lines), encoding='utf-8')
    os.replace(tmp_mime, mimeapps)
    return {'installer_desktop': str(installer_desktop), 'mimeapps': str(mimeapps)}


def sync_autostart_entry(enabled: bool, env: Optional[Dict[str, str]] = None) -> Path:
    """Create or remove XDG Autostart v0.5 entry for Shiraz warm daemon."""
    autostart_dir = xdg_config_home(env) / 'autostart'
    autostart_dir.mkdir(parents=True, exist_ok=True)
    path = autostart_dir / 'org.spectreos.Shiraz.desktop'
    if not enabled:
        path.unlink(missing_ok=True)
        return path
    daemon_cmd = _resolve_wrapper_exec('shiraz-daemon', ['daemon'])
    body = '\n'.join(
        [
            '[Desktop Entry]',
            'Type=Application',
            'Version=1.5',
            'Name=Shiraz Android Runtime',
            'Comment=Pre-warm Shiraz Android compatibility runtime in memory',
            f'Exec={daemon_cmd}',
            'Hidden=false',
            'X-GNOME-Autostart-enabled=true',
            'OnlyShowIn=Wisp;SpectreOS;Phosh;',
            '',
        ]
    )
    tmp = path.with_suffix('.desktop.tmp')
    tmp.write_text(body, encoding='utf-8')
    os.replace(tmp, path)
    return path


def install_app(
    apk_path: Path,
    *,
    registry_dir: Optional[Path] = None,
    compile_aot: bool = True,
    env: Optional[Dict[str, str]] = None,
) -> Dict[str, object]:
    """Enroll an APK in PackageRegistry, extract Freedesktop icon, write .desktop, and AOT compile."""
    apk = Path(apk_path).resolve()
    if not apk.is_file():
        raise FormatError(f'APK not found: {apk}')
    reg_dir = Path(registry_dir) if registry_dir is not None else default_registry_dir(env)
    registry = PackageRegistry(reg_dir)
    enrolled = registry.enroll(apk)
    package = enrolled['package']
    sha256 = enrolled['apk_sha256']
    enrolled_apk = registry.launch_apk(package, sha256)
    report = inspect_apk(enrolled_apk)
    manifest = report['manifest']
    version_name = str(manifest.get('version_name') or manifest.get('version_code') or 'Android')
    try:
        label = resolve_label(enrolled_apk)
    except Exception:
        label = manifest.get('application_label') or package
    if not label or str(label).startswith('@'):
        label = package

    icon_dest = (
        xdg_data_home(env) / 'icons' / 'hicolor' / '256x256' / 'apps' / f'{package}.Shiraz.png'
    )
    extract_launcher_icon_png(enrolled_apk, icon_dest, label=str(label))

    aot_ok = False
    aot_error = None
    if compile_aot:
        try:
            ensure_app_aot(enrolled_apk, sha256, cache_root=aot_cache_root(env))
            aot_ok = True
        except Exception as exc:
            aot_error = str(exc)

    desktop_path = write_desktop_entry(
        package,
        str(label),
        version_name=version_name,
        aot_compiled=aot_ok,
        env=env,
    )
    ensure_mime_apk_handler(env)

    # Notify running daemon if active so it refreshes status.json & page-cache warming.
    try:
        if control_socket_path(env).exists():
            ctl_request({'cmd': 'refresh'}, env=env, timeout=2.0)
    except Exception:
        pass

    return {
        'package': package,
        'label': str(label),
        'version': version_name,
        'apk_sha256': sha256,
        'uid': enrolled['uid'],
        'desktop_id': desktop_path.name,
        'desktop_file': str(desktop_path),
        'icon_file': str(icon_dest),
        'aot_compiled': aot_ok,
        'aot_error': aot_error,
    }


def uninstall_app(
    package: str,
    *,
    registry_dir: Optional[Path] = None,
    env: Optional[Dict[str, str]] = None,
) -> Dict[str, object]:
    """Stop if running, remove Freedesktop launcher/icon, and retire package from registry."""
    try:
        if control_socket_path(env).exists():
            ctl_request({'cmd': 'stop', 'package': package}, env=env, timeout=3.0)
    except Exception:
        pass

    removed: List[str] = []
    desktop_path = xdg_data_home(env) / 'applications' / f'{package}.Shiraz.desktop'
    icon_path = (
        xdg_data_home(env) / 'icons' / 'hicolor' / '256x256' / 'apps' / f'{package}.Shiraz.png'
    )
    for path in (desktop_path, icon_path):
        if path.exists() and not path.is_symlink():
            path.unlink()
            removed.append(str(path))

    reg_dir = Path(registry_dir) if registry_dir is not None else default_registry_dir(env)
    retired = False
    if reg_dir.is_dir():
        try:
            reg = PackageRegistry(reg_dir)
            ident = reg.identity(package)
            reg.retire(package, expected_sha256=ident['sha256'], expected_uid=ident['uid'])
            retired = True
        except Exception:
            pass

    try:
        if control_socket_path(env).exists():
            ctl_request({'cmd': 'refresh'}, env=env, timeout=2.0)
    except Exception:
        pass

    return {'package': package, 'retired': retired, 'removed': removed}


_LABEL_CACHE: Dict[Tuple[str, str], str] = {}


def list_installed_apps(
    *,
    registry_dir: Optional[Path] = None,
    running_packages: Optional[List[str]] = None,
    env: Optional[Dict[str, str]] = None,
) -> List[Dict[str, object]]:
    """List enrolled launcher-capable Android apps from PackageRegistry + AOT state."""
    reg_dir = Path(registry_dir) if registry_dir is not None else default_registry_dir(env)
    running = set(running_packages or ())
    cache = aot_cache_root(env)
    out: List[Dict[str, object]] = []
    if not (reg_dir / 'registry.sqlite').is_file():
        return out
    reg = PackageRegistry(reg_dir)
    with reg.connect() as db:
        rows = db.execute(
            'SELECT package, sha256, manifest FROM packages ORDER BY package'
        ).fetchall()
    for row in rows:
        pkg = row['package']
        sha256 = row['sha256']
        manifest = json.loads(row['manifest'])
        if not manifest.get('launcher_activities'):
            continue
        version_name = str(
            manifest.get('version_name') or manifest.get('version_code') or 'Android'
        )
        label = manifest.get('application_label')
        if not label or str(label).startswith('@'):
            cache_key = (pkg, sha256)
            label = _LABEL_CACHE.get(cache_key)
            if label is None:
                try:
                    label = resolve_label(reg.launch_apk(pkg, sha256))
                except Exception:
                    label = pkg
                _LABEL_CACHE[cache_key] = str(label)
        out.append(
            {
                'package': pkg,
                'label': str(label),
                'desktop_id': f'{pkg}.Shiraz.desktop',
                'version': version_name,
                'apk_sha256': sha256,
                'aot_compiled': app_aot_ready(sha256, cache),
                'running': pkg in running,
            }
        )
    out.sort(key=lambda item: str(item['label']).lower())
    return out


class MemoryWarmer:
    """Keeps critical ART, framework, AOT, and native library pages warm via mmap + MADV_WILLNEED."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._handles: List[Tuple[int, mmap.mmap, int]] = []
        self._bytes = 0

    @property
    def warm_ram_mb(self) -> int:
        with self._lock:
            return int(round(self._bytes / (1024 * 1024)))

    @property
    def warm_files(self) -> int:
        with self._lock:
            return len(self._handles)

    def warm(self, paths: List[Path]) -> Tuple[int, int]:
        new_handles: List[Tuple[int, mmap.mmap, int]] = []
        total_bytes = 0
        for path in paths:
            fd = -1
            try:
                fd = os.open(path, os.O_RDONLY | os.O_CLOEXEC)
                size = os.fstat(fd).st_size
                if size <= 0:
                    os.close(fd)
                    continue
                mapping = mmap.mmap(fd, size, access=mmap.ACCESS_READ)
                if hasattr(mmap, 'MADV_WILLNEED'):
                    try:
                        mapping.madvise(mmap.MADV_WILLNEED)
                    except OSError:
                        pass
                new_handles.append((fd, mapping, size))
                total_bytes += size
            except OSError:
                if fd >= 0:
                    try:
                        os.close(fd)
                    except OSError:
                        pass
        with self._lock:
            old = self._handles
            self._handles = new_handles
            self._bytes = total_bytes
        for fd, mapping, _ in old:
            try:
                mapping.close()
            except OSError:
                pass
            try:
                os.close(fd)
            except OSError:
                pass
        return len(new_handles), total_bytes

    def release(self) -> None:
        with self._lock:
            old = self._handles
            self._handles = []
            self._bytes = 0
        for fd, mapping, _ in old:
            try:
                if hasattr(mmap, 'MADV_DONTNEED'):
                    try:
                        mapping.madvise(mmap.MADV_DONTNEED)
                    except OSError:
                        pass
                mapping.close()
            except OSError:
                pass
            try:
                os.close(fd)
            except OSError:
                pass


class ShirazDaemon:
    """Long-lived Shiraz warm runtime daemon managed by Wisp or systemd --user."""

    def __init__(
        self,
        *,
        registry_dir: Optional[Path] = None,
        prewarm_memory: bool = True,
        aot_enabled: bool = True,
        sandbox: bool = False,
        width: int = 400,
        height: int = 765,
        scale: float = 1.0,
    ) -> None:
        self.registry_dir = (
            Path(registry_dir) if registry_dir is not None else default_registry_dir()
        )
        self.aot_root = aot_cache_root()
        self.sock_path = control_socket_path()
        self.status_path = status_json_path()
        self.prewarm_memory = prewarm_memory
        self.aot_enabled = aot_enabled
        self.sandbox = sandbox
        self.width = max(120, min(8192, int(width)))
        self.height = max(120, min(8192, int(height)))
        self.scale = float(scale)
        self.wayland_display = os.environ.get('WAYLAND_DISPLAY', '')
        self.dbus_address = os.environ.get('DBUS_SESSION_BUS_ADDRESS', '')

        self.state = 'starting'
        self.error_message = ''
        self.last_launch_ms: Optional[int] = None
        self.warmer = MemoryWarmer()
        self.session: Optional[HostSession] = None
        self._lock = threading.RLock()
        self._stop_event = threading.Event()
        self._server_sock: Optional[socket.socket] = None
        self._optimize_thread: Optional[threading.Thread] = None

    def _running_packages(self) -> List[str]:
        if self.session is None:
            return []
        with self.session.lock:
            running = []
            for pkg, (proc, _) in list(self.session.apps.items()):
                if proc.poll() is None:
                    running.append(pkg)
            return sorted(running)

    def status_snapshot(self) -> Dict[str, object]:
        with self._lock:
            running = self._running_packages()
            apps = list_installed_apps(
                registry_dir=self.registry_dir,
                running_packages=running,
            )
            return {
                'available': True,
                'state': self.state,
                'pid': os.getpid(),
                'warm_ram_mb': self.warmer.warm_ram_mb,
                'warm_files': self.warmer.warm_files,
                'aot_boot_ready': boot_image_ready(self.aot_root),
                'aot_framework_ready': framework_aot_ready(self.aot_root),
                'prewarm_memory': self.prewarm_memory,
                'aot_enabled': self.aot_enabled,
                'sandbox': self.sandbox,
                'last_launch_ms': self.last_launch_ms,
                'error': self.error_message,
                'running_packages': running,
                'installed_apps': apps,
            }

    def _publish_status(self) -> None:
        snap = self.status_snapshot()
        self.status_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        tmp = self.status_path.with_suffix('.json.tmp')
        tmp.write_text(json.dumps(snap, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        os.replace(tmp, self.status_path)

    def _run_optimization(self) -> None:
        with self._lock:
            self.state = 'warming'
            self.error_message = ''
        self._publish_status()
        try:
            # 1. Immediately warm existing ART/native/framework files so first launch is fast right away.
            if self.prewarm_memory:
                self.warmer.warm(collect_warm_files(self.aot_root))
                self._publish_status()
            else:
                self.warmer.release()

            # 2. Ensure shared AOT boot image + shiraz-framework.odex + installed app .odex files,
            #    plus warm inspect_apk binary caches and pre-extract native app_lib/*.so so the
            #    very first tap skips ZIP extraction and APK parsing completely.
            if self.aot_enabled and not self._stop_event.is_set():
                ensure_boot_and_framework_aot(self.aot_root)
                self._publish_status()
                reg = PackageRegistry(self.registry_dir)
                prev_fast = os.environ.get('SHIRAZ_FAST_INSPECT')
                os.environ['SHIRAZ_FAST_INSPECT'] = '1'
                try:
                    import hashlib
                    from .architecture import native_abi
                    from .native_payload import extract_native
                    from .runtime import activity_process_name

                    target_arch = 'x86_64' if native_abi() == 'x86_64' else 'aarch64'
                    for app in list_installed_apps(registry_dir=self.registry_dir):
                        if self._stop_event.is_set():
                            break
                        pkg = str(app['package'])
                        sha = str(app['apk_sha256'])
                        try:
                            apk = reg.launch_apk(pkg, sha)
                            report = inspect_apk(apk)
                            # Pre-extract native libs into the exact work/processes/<hash>/app_lib
                            # directory that runtime.py reuses when SHIRAZ_CACHE_NATIVE_LIBS=1.
                            manifest = report.get('manifest') or {}
                            launchers = manifest.get('launcher_activities') or []
                            if report.get('native_abis') and launchers:
                                proc_name = activity_process_name(manifest, launchers[0]['target'])
                                proc_hash = hashlib.sha256(proc_name.encode('utf-8')).hexdigest()
                                work = ROOT / 'build/runs' / sha / 'processes' / proc_hash
                                app_lib = work / 'app_lib'
                                stamp = work / '.app_lib.stamp'
                                expected = f'{sha}:{target_arch}'
                                if not (
                                    app_lib.is_dir()
                                    and stamp.is_file()
                                    and stamp.read_text(encoding='ascii', errors='ignore').strip()
                                    == expected
                                    and any(app_lib.iterdir())
                                ):
                                    work.mkdir(parents=True, exist_ok=True)
                                    if app_lib.exists():
                                        shutil.rmtree(app_lib)
                                    extract_native(apk, app_lib, target_arch)
                                    stamp.write_text(expected + '\n', encoding='ascii')
                            if not app_aot_ready(sha, self.aot_root):
                                ensure_app_aot(apk, sha, cache_root=self.aot_root)
                                write_desktop_entry(
                                    pkg,
                                    str(app['label']),
                                    version_name=str(app['version']),
                                    aot_compiled=True,
                                )
                        except Exception:
                            # Individual app AOT/pre-extract failure should not block runtime readiness.
                            pass
                finally:
                    if prev_fast is None:
                        os.environ.pop('SHIRAZ_FAST_INSPECT', None)
                    else:
                        os.environ['SHIRAZ_FAST_INSPECT'] = prev_fast

            # 3. Re-warm page cache to include newly generated AOT boot.art/oat/odex files.
            if self.prewarm_memory and not self._stop_event.is_set():
                self.warmer.warm(collect_warm_files(self.aot_root))

            with self._lock:
                if not self._stop_event.is_set():
                    self.state = 'ready'
        except Exception as exc:
            with self._lock:
                self.error_message = str(exc)
                # Keep daemon usable in JIT mode even if AOT failed.
                self.state = 'ready'
        self._publish_status()

    def _trigger_optimize_async(self) -> None:
        with self._lock:
            if self._optimize_thread is not None and self._optimize_thread.is_alive():
                return
            t = threading.Thread(
                target=self._run_optimization, name='shiraz-optimizer', daemon=True
            )
            self._optimize_thread = t
            t.start()

    def _reaper_watcher(self) -> None:
        while not self._stop_event.is_set():
            if self.session is not None:
                fired = self.session.changed.wait(timeout=1.0)
                if not fired:
                    continue
                self.session.changed.clear()
                if not self._stop_event.is_set():
                    try:
                        self.session.collect()
                    except Exception:
                        pass
                    self._publish_status()
            else:
                self._stop_event.wait(timeout=0.5)

    def _launch_package(self, req: Dict[str, object]) -> Dict[str, object]:
        package = str(req.get('package') or '').strip()
        if not package:
            raise FormatError('launch requires package')
        if self.session is None:
            raise RuntimeError('HostSession not initialized')

        # Collect any completed launches before starting a fresh one.
        try:
            self.session.collect()
        except Exception:
            pass

        with self.session.lock:
            existing = self.session.apps.get(package)
            if existing is not None and existing[0].poll() is None:
                return {
                    'ok': True,
                    'package': package,
                    'already_running': True,
                    'pid': existing[0].pid,
                }

        width = int(req.get('width') or self.width)
        height = int(req.get('height') or self.height)
        frames = req.get('frames')
        snapshot = req.get('snapshot')
        timeout = req.get('timeout')
        sandbox = bool(req['sandbox']) if 'sandbox' in req else self.sandbox

        # Apply SpectreOS Wayland + Freedesktop + AOT fast-launch environment before spawn.
        saved_env: Dict[str, Optional[str]] = {}
        launch_env = {
            'SHIRAZ_ART_MODE': 'jit',
            'SHIRAZ_AOT_DIR': str(self.aot_root) if self.aot_enabled else '',
            'SHIRAZ_CHECK_JNI': '0',
            'SHIRAZ_FAST_GC': '1',
            'SHIRAZ_FAST_INSPECT': '1',
            'SHIRAZ_CACHE_NATIVE_LIBS': '1',
            'SHIRAZ_FORM_FACTOR': os.environ.get('SHIRAZ_FORM_FACTOR', 'mobile'),
            'SHIRAZ_DESKTOP_ENTRY': f'{package}.Shiraz',
            'SDL_VIDEO_WAYLAND_WMCLASS': f'{package}.Shiraz',
        }
        if self.wayland_display:
            launch_env['WAYLAND_DISPLAY'] = self.wayland_display
            if (
                'SDL_VIDEODRIVER' not in os.environ
                and 'SHIRAZ_HOST_SDL_VIDEODRIVER' not in os.environ
            ):
                launch_env['SDL_VIDEODRIVER'] = 'wayland'
        if self.dbus_address:
            launch_env['DBUS_SESSION_BUS_ADDRESS'] = self.dbus_address

        t0 = time.monotonic()
        with self._lock:
            for k, v in launch_env.items():
                saved_env[k] = os.environ.get(k)
                if v:
                    os.environ[k] = v
                else:
                    os.environ.pop(k, None)
            try:
                settings: Dict[str, object] = {
                    'width': width,
                    'height': height,
                    'sandbox': sandbox,
                    'allow_network': True,
                }
                if frames is not None:
                    settings['frames'] = int(frames)
                if snapshot is not None:
                    settings['snapshot'] = str(snapshot)
                if timeout is not None:
                    settings['timeout'] = int(timeout)
                proc = self.session.start(package, settings=settings)
            finally:
                for k, old_v in saved_env.items():
                    if old_v is None:
                        os.environ.pop(k, None)
                    else:
                        os.environ[k] = old_v

        launch_ms = int((time.monotonic() - t0) * 1000)
        with self._lock:
            self.last_launch_ms = launch_ms
        self._publish_status()
        return {
            'ok': True,
            'package': package,
            'pid': proc.pid,
            'launch_ms': launch_ms,
            'desktop_id': f'{package}.Shiraz.desktop',
            'aot_ready': boot_image_ready(self.aot_root) and framework_aot_ready(self.aot_root),
        }

    def _stop_package(self, package: str) -> Dict[str, object]:
        if self.session is None:
            return {'ok': True, 'stopped': False}
        stopped = False
        with self.session.lock:
            entry = self.session.apps.get(package)
            if entry is not None and entry[0].poll() is None:
                proc = entry[0]
                try:
                    os.killpg(proc.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    proc.wait(timeout=2.5)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                stopped = True
        try:
            self.session.collect()
        except Exception:
            pass
        self._publish_status()
        return {'ok': True, 'package': package, 'stopped': stopped}

    def _stop_all(self) -> Dict[str, object]:
        stopped = []
        for pkg in self._running_packages():
            res = self._stop_package(pkg)
            if res.get('stopped'):
                stopped.append(pkg)
        return {'ok': True, 'stopped': stopped}

    def _handle_request(self, req: Dict[str, object]) -> Dict[str, object]:
        cmd = str(req.get('cmd') or '').strip().lower()
        if cmd == 'status':
            return {'ok': True, 'status': self.status_snapshot()}
        if cmd == 'configure':
            need_opt = False
            with self._lock:
                if 'prewarm_memory' in req:
                    val = bool(req['prewarm_memory'])
                    if val != self.prewarm_memory:
                        self.prewarm_memory = val
                        need_opt = True
                if 'aot_enabled' in req:
                    val = bool(req['aot_enabled'])
                    if val != self.aot_enabled:
                        self.aot_enabled = val
                        need_opt = True
                if 'sandbox' in req:
                    self.sandbox = bool(req['sandbox'])
                if 'width' in req and int(req['width']) > 0:
                    self.width = max(120, min(8192, int(req['width'])))
                if 'height' in req and int(req['height']) > 0:
                    self.height = max(120, min(8192, int(req['height'])))
                if 'scale' in req:
                    self.scale = float(req['scale'])
                if 'wayland_display' in req and req['wayland_display']:
                    self.wayland_display = str(req['wayland_display'])
                if 'dbus_address' in req and req['dbus_address']:
                    self.dbus_address = str(req['dbus_address'])
            if need_opt:
                self._trigger_optimize_async()
            else:
                self._publish_status()
            return {'ok': True, 'status': self.status_snapshot()}
        if cmd == 'launch':
            return self._launch_package(req)
        if cmd == 'stop':
            pkg = str(req.get('package') or '').strip()
            if not pkg:
                raise FormatError('stop requires package')
            return self._stop_package(pkg)
        if cmd in ('stop_all', 'stop-all'):
            return self._stop_all()
        if cmd == 'install':
            apk = Path(str(req.get('apk') or ''))
            compile_aot = bool(req.get('compile_aot', self.aot_enabled))
            res = install_app(apk, registry_dir=self.registry_dir, compile_aot=compile_aot)
            self._trigger_optimize_async()
            return {'ok': True, 'installed': res}
        if cmd == 'uninstall':
            pkg = str(req.get('package') or '').strip()
            res = uninstall_app(pkg, registry_dir=self.registry_dir)
            self._publish_status()
            return {'ok': True, 'uninstalled': res}
        if cmd == 'refresh':
            self._trigger_optimize_async()
            return {'ok': True, 'status': self.status_snapshot()}
        if cmd == 'shutdown':
            self._stop_event.set()
            return {'ok': True, 'shutting_down': True}
        raise FormatError(f'Unknown daemon command: {cmd!r}')

    def _serve_client(self, conn: socket.socket) -> None:
        with conn:
            conn.settimeout(30.0)
            data = b''
            while b'\n' not in data and len(data) < 65536:
                chunk = conn.recv(4096)
                if not chunk:
                    break
                data += chunk
            line = data.split(b'\n', 1)[0].decode('utf-8', errors='replace').strip()
            if not line:
                return
            try:
                req = json.loads(line)
                if not isinstance(req, dict):
                    raise FormatError('JSON request must be an object')
                resp = self._handle_request(req)
            except Exception as exc:
                resp = {'ok': False, 'error': str(exc)}
            try:
                conn.sendall((json.dumps(resp) + '\n').encode('utf-8'))
            except OSError:
                pass

    def serve_forever(self) -> int:
        ensure_mime_apk_handler()
        self.registry_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.session = HostSession(self.registry_dir)

        if self.sock_path.exists():
            self.sock_path.unlink(missing_ok=True)
        srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        srv.bind(str(self.sock_path))
        os.chmod(self.sock_path, 0o600)
        sock_ino = self.sock_path.stat().st_ino
        srv.listen(16)
        srv.settimeout(0.5)
        self._server_sock = srv

        def _handle_sig(_signum, _frame):
            self._stop_event.set()

        if threading.current_thread() is threading.main_thread():
            signal.signal(signal.SIGTERM, _handle_sig)
            signal.signal(signal.SIGINT, _handle_sig)

        self._publish_status()
        self._trigger_optimize_async()

        watcher = threading.Thread(
            target=self._reaper_watcher, name='shiraz-reaper-watch', daemon=True
        )
        watcher.start()

        try:
            while not self._stop_event.is_set():
                try:
                    conn, _ = srv.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break
                threading.Thread(
                    target=self._serve_client, args=(conn,), name='shiraz-ipc', daemon=True
                ).start()
        finally:
            self._stop_event.set()
            try:
                srv.close()
            except OSError:
                pass
            try:
                if self.sock_path.exists() and self.sock_path.stat().st_ino == sock_ino:
                    self.sock_path.unlink(missing_ok=True)
            except OSError:
                pass
            try:
                self._stop_all()
            except Exception:
                pass
            if self.session is not None:
                try:
                    self.session.close()
                except Exception:
                    pass
                self.session = None
            self.warmer.release()
            with self._lock:
                self.state = 'disabled'
                self.error_message = ''
            try:
                # Only overwrite status.json if no newer daemon instance has taken over.
                should_write = True
                if self.status_path.is_file():
                    try:
                        cur = json.loads(self.status_path.read_text(encoding='utf-8'))
                        if cur.get('pid') not in (None, os.getpid()):
                            should_write = False
                    except (OSError, ValueError):
                        pass
                if should_write:
                    self._publish_status()
            except OSError:
                pass
        return 0


def ctl_request(
    payload: Dict[str, object],
    *,
    env: Optional[Dict[str, str]] = None,
    timeout: float = 15.0,
) -> Dict[str, object]:
    """Send one newline-delimited JSON IPC command to the running Shiraz daemon."""
    sock_path = control_socket_path(env)
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.settimeout(timeout)
        client.connect(str(sock_path))
        client.sendall((json.dumps(payload) + '\n').encode('utf-8'))
        buf = b''
        while b'\n' not in buf and len(buf) < 2 * 1024 * 1024:
            chunk = client.recv(8192)
            if not chunk:
                break
            buf += chunk
    text = buf.split(b'\n', 1)[0].decode('utf-8', errors='replace').strip()
    if not text:
        raise RuntimeError('Empty reply from Shiraz daemon')
    resp = json.loads(text)
    if not isinstance(resp, dict):
        raise RuntimeError('Invalid JSON reply from Shiraz daemon')
    return resp


def ensure_daemon_running(
    *,
    env: Optional[Dict[str, str]] = None,
    wait_seconds: float = 8.0,
) -> bool:
    """Start the background Shiraz daemon if its IPC socket is not yet listening."""
    sock_path = control_socket_path(env)
    if sock_path.exists():
        try:
            resp = ctl_request({'cmd': 'status'}, env=env, timeout=1.5)
            if resp.get('ok'):
                return True
        except OSError:
            pass
    spawn_env = dict(os.environ if env is None else env)
    spawn_env['PYTHONPATH'] = str(ROOT / 'src') + (
        ':' + spawn_env['PYTHONPATH'] if spawn_env.get('PYTHONPATH') else ''
    )
    spawn_env['SHIRAZ_HOME'] = str(ROOT)
    subprocess.Popen(
        [sys.executable, '-m', 'shiraz', 'daemon'],
        cwd=str(ROOT),
        env=spawn_env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        if sock_path.exists():
            try:
                resp = ctl_request({'cmd': 'status'}, env=env, timeout=1.0)
                if resp.get('ok'):
                    return True
            except OSError:
                pass
        time.sleep(0.1)
    return False


def launch_app_via_daemon(
    package: str,
    *,
    width: Optional[int] = None,
    height: Optional[int] = None,
    frames: Optional[int] = None,
    snapshot: Optional[str] = None,
    timeout: Optional[int] = None,
    env: Optional[Dict[str, str]] = None,
) -> Dict[str, object]:
    """Launch an installed Android app via the warm Shiraz daemon (auto-starting if needed)."""
    if not ensure_daemon_running(env=env):
        raise RuntimeError('Could not start Shiraz daemon')
    payload: Dict[str, object] = {'cmd': 'launch', 'package': package}
    if width is not None:
        payload['width'] = int(width)
    if height is not None:
        payload['height'] = int(height)
    if frames is not None:
        payload['frames'] = int(frames)
    if snapshot is not None:
        payload['snapshot'] = str(snapshot)
    if timeout is not None:
        payload['timeout'] = int(timeout)
    resp = ctl_request(payload, env=env, timeout=30.0)
    if not resp.get('ok'):
        raise RuntimeError(str(resp.get('error') or 'Launch failed'))
    return resp
