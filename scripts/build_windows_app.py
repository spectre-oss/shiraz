#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a self-contained Windows portable application layout (and MSIX AppxManifest.xml).

Stages a PE executable, resolves non-system DLL dependencies via pure-Python PE
import-table inspection, wraps an optional PNG into a valid Windows `.ico`, and
writes `shiraz-manifest.json` (SHA-256 inventory) plus `AppxManifest.xml`.
Runs on Windows, macOS, and Linux with zero external tool dependencies.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Set

_SYSTEM_DLLS: Set[str] = {
    'advapi32.dll',
    'avrt.dll',
    'bcrypt.dll',
    'comctl32.dll',
    'comdlg32.dll',
    'crypt32.dll',
    'd3d11.dll',
    'd3d12.dll',
    'dbghelp.dll',
    'dwmapi.dll',
    'dwrite.dll',
    'dxgi.dll',
    'gdi32.dll',
    'imm32.dll',
    'iphlpapi.dll',
    'kernel32.dll',
    'kernelbase.dll',
    'mf.dll',
    'mfplat.dll',
    'mfreadwrite.dll',
    'mfuuid.dll',
    'mmdevapi.dll',
    'msvcrt.dll',
    'ntdll.dll',
    'ole32.dll',
    'oleaut32.dll',
    'opengl32.dll',
    'propsys.dll',
    'psapi.dll',
    'rpcrt4.dll',
    'secur32.dll',
    'setupapi.dll',
    'shell32.dll',
    'shlwapi.dll',
    'ucrtbase.dll',
    'user32.dll',
    'userenv.dll',
    'uxtheme.dll',
    'version.dll',
    'vulkan-1.dll',
    'winhttp.dll',
    'winmm.dll',
    'ws2_32.dll',
    'wsock32.dll',
}


def is_system_dll(name: str) -> bool:
    """Return True if the DLL is provided by Windows itself."""
    lower = Path(name).name.lower()
    if lower in _SYSTEM_DLLS:
        return True
    return lower.startswith(('api-ms-win-', 'ext-ms-win-'))


def is_valid_pe(data: bytes) -> bool:
    """Validate DOS MZ header and PE\\0\\0 signature."""
    if len(data) < 0x40 or data[:2] != b'MZ':
        return False
    (pe_offset,) = struct.unpack_from('<I', data, 0x3C)
    if pe_offset + 24 > len(data):
        return False
    return data[pe_offset : pe_offset + 4] == b'PE\x00\x00'


def parse_pe_imports(data: bytes) -> List[str]:
    """Parse imported DLL names from a PE32 or PE32+ binary without executing it."""
    if not is_valid_pe(data):
        raise ValueError('Not a valid Windows PE binary')
    (pe_offset,) = struct.unpack_from('<I', data, 0x3C)
    _, num_sections, _, _, _, opt_size, _ = struct.unpack_from('<HHIIIHH', data, pe_offset + 4)
    opt_off = pe_offset + 24
    if opt_off + opt_size > len(data) or opt_size < 2:
        raise ValueError('Truncated PE optional header')
    (magic,) = struct.unpack_from('<H', data, opt_off)
    if magic == 0x20B:  # PE32+ (64-bit)
        dd_off = opt_off + 112
    elif magic == 0x10B:  # PE32 (32-bit)
        dd_off = opt_off + 96
    else:
        raise ValueError(f'Unsupported PE optional header magic: 0x{magic:x}')

    if dd_off + 16 > opt_off + opt_size:
        return []
    import_rva, import_size = struct.unpack_from('<II', data, dd_off + 8)
    if import_rva == 0 or import_size == 0:
        return []

    sections = []
    sec_off = opt_off + opt_size
    for i in range(num_sections):
        entry = sec_off + i * 40
        if entry + 40 > len(data):
            break
        vsize, vaddr, raw_size, raw_ptr = struct.unpack_from('<IIII', data, entry + 8)
        sections.append((vaddr, max(vsize, raw_size), raw_ptr, raw_size))

    def rva_to_offset(rva: int) -> Optional[int]:
        for vaddr, span, raw_ptr, raw_size in sections:
            if vaddr <= rva < vaddr + span:
                delta = rva - vaddr
                if delta < raw_size and raw_ptr + delta < len(data):
                    return raw_ptr + delta
        return None

    table_off = rva_to_offset(import_rva)
    if table_off is None:
        return []

    imports: List[str] = []
    seen: Set[str] = set()
    cursor = table_off
    while cursor + 20 <= len(data):
        orig_first, ts, fwd, name_rva, first_thunk = struct.unpack_from('<IIIII', data, cursor)
        if orig_first == 0 and ts == 0 and fwd == 0 and name_rva == 0 and first_thunk == 0:
            break
        name_off = rva_to_offset(name_rva)
        if name_off is not None:
            end = data.find(b'\x00', name_off)
            if end > name_off:
                dll = data[name_off:end].decode('ascii', errors='replace')
                if dll and dll.lower() not in seen:
                    seen.add(dll.lower())
                    imports.append(dll)
        cursor += 20
    return imports


def build_ico_from_png(png_bytes: bytes) -> bytes:
    """Wrap a valid PNG image inside a single-entry Windows `.ico` container."""
    if len(png_bytes) < 24 or png_bytes[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Icon input must be a valid PNG file')
    width, height = struct.unpack_from('>II', png_bytes, 16)
    if width == 0 or height == 0:
        raise ValueError('Invalid PNG dimensions')
    w_byte = 0 if width >= 256 else width
    h_byte = 0 if height >= 256 else height
    header = struct.pack('<HHH', 0, 1, 1)  # reserved=0, type=1 (ICO), count=1
    entry = struct.pack(
        '<BBBBHHII',
        w_byte,
        h_byte,
        0,  # color count
        0,  # reserved
        1,  # planes
        32,  # bpp
        len(png_bytes),
        6 + 16,  # image data offset
    )
    return header + entry + png_bytes


def normalize_msix_version(version: str) -> str:
    """Convert semantic version `X.Y.Z` into 4-part Windows MSIX version `X.Y.Z.0`."""
    match = re.match(r'^(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?', version.strip())
    if not match:
        raise ValueError(f'Invalid version string for Windows manifest: {version!r}')
    major, minor, patch, build = match.groups()
    return f'{int(major)}.{int(minor)}.{int(patch)}.{int(build or 0)}'


def build_appx_manifest(
    *,
    name: str,
    identity_name: str,
    publisher: str,
    version: str,
    executable: str,
    architecture: str = 'x64',
    has_icon: bool = False,
) -> str:
    """Render an MSIX AppxManifest.xml with full-trust and Android host capabilities."""
    msix_version = normalize_msix_version(version)
    if architecture not in ('x64', 'arm64'):
        raise ValueError(f'Unsupported Windows MSIX architecture: {architecture!r}')
    logo = 'Assets\\Shiraz.ico' if has_icon else 'Assets\\Logo.png'

    ns = 'http://schemas.microsoft.com/appx/manifest/foundation/windows10'
    uap = 'http://schemas.microsoft.com/appx/manifest/uap/windows10'
    rescap = (
        'http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities'
    )
    ET.register_namespace('', ns)
    ET.register_namespace('uap', uap)
    ET.register_namespace('rescap', rescap)

    pkg = ET.Element(f'{{{ns}}}Package')
    ET.SubElement(
        pkg,
        f'{{{ns}}}Identity',
        {
            'Name': identity_name,
            'Publisher': publisher,
            'Version': msix_version,
            'ProcessorArchitecture': architecture,
        },
    )
    props = ET.SubElement(pkg, f'{{{ns}}}Properties')
    ET.SubElement(props, f'{{{ns}}}DisplayName').text = name
    ET.SubElement(props, f'{{{ns}}}PublisherDisplayName').text = 'Shiraz'
    ET.SubElement(props, f'{{{ns}}}Logo').text = logo

    deps = ET.SubElement(pkg, f'{{{ns}}}Dependencies')
    ET.SubElement(
        deps,
        f'{{{ns}}}TargetDeviceFamily',
        {
            'Name': 'Windows.Desktop',
            'MinVersion': '10.0.19041.0',
            'MaxVersionTested': '10.0.26100.0',
        },
    )

    apps = ET.SubElement(pkg, f'{{{ns}}}Applications')
    app = ET.SubElement(
        apps,
        f'{{{ns}}}Application',
        {
            'Id': 'Shiraz',
            'Executable': executable,
            'EntryPoint': 'Windows.FullTrustApplication',
        },
    )
    ET.SubElement(
        app,
        f'{{{uap}}}VisualElements',
        {
            'DisplayName': name,
            'Description': 'Shiraz native Android application host for Windows',
            'BackgroundColor': 'transparent',
            'Square150x150Logo': logo,
            'Square44x44Logo': logo,
        },
    )

    caps = ET.SubElement(pkg, f'{{{ns}}}Capabilities')
    ET.SubElement(caps, f'{{{ns}}}Capability', {'Name': 'internetClient'})
    ET.SubElement(caps, f'{{{rescap}}}Capability', {'Name': 'runFullTrust'})
    ET.SubElement(caps, f'{{{ns}}}DeviceCapability', {'Name': 'location'})
    ET.SubElement(caps, f'{{{ns}}}DeviceCapability', {'Name': 'microphone'})
    ET.SubElement(caps, f'{{{ns}}}DeviceCapability', {'Name': 'webcam'})

    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(pkg, encoding='unicode') + '\n'


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        while chunk := handle.read(65536):
            digest.update(chunk)
    return digest.hexdigest()


def _find_dll(dll_name: str, search_dirs: Sequence[Path]) -> Optional[Path]:
    target_lower = dll_name.lower()
    for directory in search_dirs:
        if not directory.is_dir():
            continue
        direct = directory / dll_name
        if direct.is_file() and not direct.is_symlink():
            return direct
        for candidate in directory.iterdir():
            if candidate.name.lower() == target_lower and candidate.is_file():
                return candidate
    return None


def build_windows_bundle(
    *,
    executable: Path,
    output_dir: Path,
    name: str = 'Shiraz',
    identity_name: str = 'org.shiraz.host',
    publisher: str = 'CN=Shiraz',
    version: Optional[str] = None,
    architecture: str = 'x64',
    dll_search_dirs: Iterable[Path] = (),
    extra_dlls: Iterable[Path] = (),
    icon_png: Optional[Path] = None,
) -> Path:
    """Assemble a self-contained portable Windows application directory."""
    executable = executable.resolve(strict=True)
    if not executable.is_file():
        raise ValueError(f'Executable not found: {executable}')
    exe_bytes = executable.read_bytes()
    if not is_valid_pe(exe_bytes):
        raise ValueError(f'Executable is not a valid Windows PE binary: {executable}')

    if version is None:
        version_file = Path(__file__).resolve().parents[1] / 'VERSION'
        version = (
            version_file.read_text(encoding='utf-8').strip() if version_file.is_file() else '0.0.0'
        )

    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    exe_name = (
        executable.name if executable.name.lower().endswith('.exe') else executable.name + '.exe'
    )
    staged_exe = output_dir / exe_name
    shutil.copy2(executable, staged_exe)

    search_list = [executable.parent, *[Path(p) for p in dll_search_dirs]]
    queue: List[Path] = [staged_exe]
    for extra in extra_dlls:
        extra_path = Path(extra).resolve(strict=True)
        if not is_valid_pe(extra_path.read_bytes()):
            raise ValueError(f'Extra DLL is not a valid PE binary: {extra_path}')
        dest = output_dir / extra_path.name
        if not dest.exists():
            shutil.copy2(extra_path, dest)
            queue.append(dest)

    staged_dlls: Set[str] = {p.name.lower() for p in queue if p != staged_exe}
    missing_dlls: List[str] = []

    while queue:
        current = queue.pop(0)
        for dep in parse_pe_imports(current.read_bytes()):
            if is_system_dll(dep):
                continue
            dep_lower = dep.lower()
            if dep_lower in staged_dlls:
                continue
            found = _find_dll(dep, search_list)
            if found is None:
                if dep not in missing_dlls:
                    missing_dlls.append(dep)
                continue
            dest = output_dir / found.name
            shutil.copy2(found, dest)
            staged_dlls.add(dep_lower)
            queue.append(dest)

    has_icon = False
    if icon_png is not None:
        assets_dir = output_dir / 'Assets'
        assets_dir.mkdir(exist_ok=True)
        ico_bytes = build_ico_from_png(icon_png.read_bytes())
        (assets_dir / 'Shiraz.ico').write_bytes(ico_bytes)
        has_icon = True

    manifest_xml = build_appx_manifest(
        name=name,
        identity_name=identity_name,
        publisher=publisher,
        version=version,
        executable=exe_name,
        architecture=architecture,
        has_icon=has_icon,
    )
    (output_dir / 'AppxManifest.xml').write_text(manifest_xml, encoding='utf-8')

    files_inventory = {}
    for root, _, filenames in os.walk(output_dir):
        for fname in sorted(filenames):
            fpath = Path(root) / fname
            rel = fpath.relative_to(output_dir).as_posix()
            files_inventory[rel] = _sha256_file(fpath)

    bundle_meta = {
        'name': name,
        'identity': identity_name,
        'version': version,
        'msix_version': normalize_msix_version(version),
        'architecture': architecture,
        'executable': exe_name,
        'bundled_dlls': sorted(staged_dlls),
        'unresolved_dlls': sorted(missing_dlls),
        'files': files_inventory,
    }
    (output_dir / 'shiraz-manifest.json').write_text(
        json.dumps(bundle_meta, indent=2, sort_keys=True) + '\n',
        encoding='utf-8',
    )
    return output_dir


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--executable', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--name', default='Shiraz')
    parser.add_argument('--identity', default='org.shiraz.host')
    parser.add_argument('--publisher', default='CN=Shiraz')
    parser.add_argument('--version', default=None)
    parser.add_argument('--arch', default='x64', choices=('x64', 'arm64'))
    parser.add_argument('--dll-dir', action='append', default=[], type=Path)
    parser.add_argument('--extra-dll', action='append', default=[], type=Path)
    parser.add_argument('--icon-png', default=None, type=Path)
    args = parser.parse_args(argv)

    bundle = build_windows_bundle(
        executable=args.executable,
        output_dir=args.output,
        name=args.name,
        identity_name=args.identity,
        publisher=args.publisher,
        version=args.version,
        architecture=args.arch,
        dll_search_dirs=args.dll_dir,
        extra_dlls=args.extra_dll,
        icon_png=args.icon_png,
    )
    print(f'WINDOWS_BUNDLE_OK path={bundle}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
