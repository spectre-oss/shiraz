# SPDX-License-Identifier: GPL-3.0-only
"""Validate an actual end-of-main dyld snapshot against signed owned copies."""

import hashlib
import re
from pathlib import Path

from macos_guest_hardened_closure import is_system

PREFIX = "M5_NATIVE_CLOSURE_"
MAX_IMAGES = 1024
MAX_PATH_BYTES = 4096
MAX_STDOUT = 64 * 1024 * 1024


def validate_native_inventory(stdout, manifest, mandatory):
    """Prove observed routes only, never historical loads or unused seed images."""
    if len(stdout) > MAX_STDOUT:
        raise ValueError("native inventory stdout exceeds bound")
    lines = stdout.splitlines()
    starts = [
        index for index, line in enumerate(lines) if line.startswith(PREFIX + "BEGIN")
    ]
    if len(starts) != 1:
        raise ValueError("native inventory requires one complete BEGIN report")
    first = starts[0]
    begin = re.fullmatch(
        r"M5_NATIVE_CLOSURE_BEGIN count=(\d+) snapshot=end-of-main", lines[first]
    )
    if begin is None or not 1 <= int(begin[1]) <= MAX_IMAGES:
        raise ValueError("native inventory count or snapshot malformed")
    count = int(begin[1])
    end = first + count + 1
    if end >= len(lines) or lines[end] != f"M5_NATIVE_CLOSURE_END count={count}":
        raise ValueError("native inventory truncated or count mismatch")
    if any(
        line.startswith(PREFIX)
        for index, line in enumerate(lines)
        if not first <= index <= end
    ):
        raise ValueError("native inventory has additional or malformed report records")
    paths = []
    for index in range(count):
        line = lines[first + index + 1]
        match = re.fullmatch(
            r"M5_NATIVE_CLOSURE_PATH index=(\d+) hex=([0-9a-f]+)", line
        )
        if match is None or int(match[1]) != index or len(match[2]) % 2:
            raise ValueError("native inventory path record malformed")
        encoded = bytes.fromhex(match[2])
        if not 0 < len(encoded) < MAX_PATH_BYTES or b"\0" in encoded:
            raise ValueError("native inventory path exceeds bound or contains NUL")
        path = encoded.decode("utf-8", errors="strict")
        if not Path(path).is_absolute():
            raise ValueError("native inventory image path must be absolute")
        paths.append(path)
    if not manifest.get("signed") or not manifest.get("images"):
        raise ValueError("native inventory requires a signed copied-image manifest")
    owned = {item["path"]: item for item in manifest["images"]}
    if len(owned) != len(manifest["images"]):
        raise ValueError("native inventory manifest has duplicate copied paths")
    mandatory = {str(path) for path in mandatory}
    if not mandatory or not mandatory <= owned.keys():
        raise ValueError(
            "native inventory mandatory images must name exact owned copies"
        )
    observed = {}
    for name in paths:
        if is_system(name):
            continue
        item = owned.get(name)
        if item is None:
            raise ValueError(
                "native inventory observed foreign non-system image: " + name
            )
        path = Path(name)
        if path.is_symlink() or path.resolve(strict=True) != path or not path.is_file():
            raise ValueError(
                "native inventory image is not an exact regular owned copy"
            )
        expected = item.get("signed_sha256")
        if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise ValueError("native inventory copied image lacks signed hash")
        if path.stat().st_size > 256 * 1024 * 1024:
            raise ValueError("native inventory copied image exceeds hash bound")
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError(
                "native inventory copied image changed since signing: " + name
            )
        observed[name] = actual
    if not mandatory <= observed.keys():
        raise ValueError(
            "native inventory missing mandatory main/bridge/host-helper image"
        )
    return {
        "snapshot": "end-of-main",
        "scope": "stable end-of-main dyld observation; no historical load/unload claim",
        "image_count": count,
        "observed_paths": paths,
        "observed_non_system_sha256": observed,
        "mandatory": sorted(mandatory),
        "raw_report": "\n".join(lines[first : end + 1]) + "\n",
        "verified": True,
    }
