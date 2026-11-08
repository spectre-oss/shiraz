# SPDX-License-Identifier: GPL-3.0-only
"""App-local identity and usage descriptions for macOS privacy permission prompts."""

import hashlib
import os
import plistlib
import shutil
from pathlib import Path

from .apk import FormatError


def prepare_app_bundle(work: Path, vm: Path, host: Path, package: str) -> Path:
    """Copy the unchanged ART executable into an app bundle; never patch or sign it."""
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
    identity = hashlib.sha256(package.encode("utf-8")).hexdigest()[:24]
    info = {
        "CFBundleIdentifier": "org.shiraz.apk." + identity,
        "CFBundleName": "Shiraz",
        "CFBundleDisplayName": "Shiraz",
        "CFBundleExecutable": "ShirazHost",
        "CFBundlePackageType": "APPL",
        "CFBundleVersion": "1",
        "NSHighResolutionCapable": True,
        "NSCameraUsageDescription": "Shiraz provides camera access to this Android app after you grant it camera permission.",
        "NSMicrophoneUsageDescription": "Shiraz provides microphone access to this Android app after you grant it microphone permission.",
    }
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
