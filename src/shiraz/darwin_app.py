# SPDX-License-Identifier: GPL-3.0-only
"""App-local identity and usage descriptions for macOS privacy permission prompts."""

import hashlib
import os
import plistlib
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional

from .apk import FormatError


MAX_ICON = 4 * 1024 * 1024


def badged_icon(apk: Path, work: Path, icons: Path) -> Optional[Path]:
    """The APK's launcher icon with the Shiraz badge as .icns, or None.

    Uses build/icons/shiraz-badge (make macos-icons). The Dock icon is cosmetic:
    an APK without a supported raster icon falls back to the plain ghost, with
    the reason on stderr.
    """
    helper, mark = icons / "shiraz-badge", icons / "shiraz-mark.png"
    if not helper.is_file() or not mark.is_file():
        return None
    target, source = work / "dock.icns", work / "icon"
    if target.is_file() and target.stat().st_mtime >= max(helper.stat().st_mtime, apk.stat().st_mtime):
        return target
    from .app_icon import extract_icon
    try:
        if source.is_symlink() or target.is_symlink():
            raise FormatError("icon work paths must not be symlinks")
        extract_icon(apk, source)
        subprocess.run([str(helper), str(source), str(mark), str(target)], check=True, timeout=30,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    except (FormatError, OSError, subprocess.SubprocessError) as exc:
        print(f"SHIRAZ_DOCK_ICON fallback=ghost reason={exc}", file=sys.stderr, flush=True)
        return None
    return target


def prepare_app_bundle(work: Path, vm: Path, host: Path, package: str, icon: Optional[Path] = None,
                       name: Optional[str] = None) -> Path:
    """Copy the unchanged ART executable into an app bundle; never patch or sign it.

    `icon` is an optional .icns (the APK icon with the Shiraz badge, or the plain
    ghost) shown in the Dock and app switcher while the APK runs; `name` is the
    app label shown there. A missing icon is not an error.
    """
    vm = vm.resolve(strict=True)
    if not vm.is_file() or vm.stat().st_size > 64 * 1024 * 1024:
        raise FormatError("Invalid Darwin ART executable")
    with vm.open("rb") as source:
        if source.read(4) != b"\xcf\xfa\xed\xfe":
            raise FormatError("Darwin ART must be a 64-bit Mach-O executable")
    contents = work / "host" / "Shiraz.app" / "Contents"
    executable = contents / "MacOS" / "ShirazHost"
    for directory in (contents.parent.parent, contents.parent, contents, executable.parent):
        if directory.is_symlink():
            raise FormatError("Darwin app bundle directory must not be a symlink")
        directory.mkdir(parents=True, exist_ok=True)
    display = name.strip() if name and name.strip() and "\0" not in name else "Shiraz"
    identity = hashlib.sha256(package.encode("utf-8")).hexdigest()[:24]
    info = {
        "CFBundleIdentifier": "org.shiraz.apk." + identity,
        "CFBundleName": display[:64],
        "CFBundleDisplayName": display[:64],
        "CFBundleExecutable": "ShirazHost",
        "CFBundlePackageType": "APPL",
        "CFBundleVersion": "1",
        "NSHighResolutionCapable": True,
        "NSCameraUsageDescription": "Shiraz provides camera access to this Android app after you grant it camera permission.",
        "NSMicrophoneUsageDescription": "Shiraz provides microphone access to this Android app after you grant it microphone permission.",
    }
    if icon is not None and icon.is_file():
        if icon.stat().st_size > MAX_ICON or icon.read_bytes()[:4] != b"icns":
            raise FormatError("Darwin app icon must be an .icns file")
        resources = contents / "Resources"
        target = resources / "Shiraz.icns"
        if resources.is_symlink() or target.is_symlink():
            raise FormatError("Darwin app resources must not be symlinks")
        resources.mkdir(exist_ok=True)
        data = icon.read_bytes()
        if not target.is_file() or target.read_bytes() != data:
            fresh = resources / ".Shiraz.icns.fresh"
            fresh.write_bytes(data)
            os.replace(fresh, target)
        info["CFBundleIconFile"] = "Shiraz"
    # Atomic replacement retains a stable executable path and unchanged Mach-O bytes.
    temporary = executable.with_name(".ShirazHost.fresh")
    if temporary.is_symlink() or executable.is_symlink():
        raise FormatError("Darwin app executable must not be a symlink")
    shutil.copy2(vm, temporary)
    os.replace(temporary, executable)
    metadata = contents / "Info.plist"
    if metadata.is_symlink():
        raise FormatError("Darwin bundle metadata must not be a symlink")
    metadata.write_bytes(plistlib.dumps(info, sort_keys=True))
    libraries = contents / "lib64"
    target = (host / "lib64").resolve(strict=True)
    if libraries.is_symlink():
        if libraries.resolve() != target:
            raise FormatError("Darwin app library link points outside its ART host")
    elif libraries.exists():
        raise FormatError("Darwin app library link is occupied")
    else:
        libraries.symlink_to(target, target_is_directory=True)
    return executable
