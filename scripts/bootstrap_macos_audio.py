#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned unchanged stb_vorbis under ignored .deps, retaining its license."""

import fcntl
import hashlib
import json
import subprocess
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/stb-audio"
REVISION = "f0569113c93ad095470c54bf34a17b36646bbbb5"
FILES = {
    "stb_vorbis.c": "4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636",
    "LICENSE": "bebfe904b14301657e4e5d655c811d51fd31b97c455b9cc2d8600d6bac6cff63",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "stb-audio.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        for name, expected in FILES.items():
            target = OUT / name
            if target.is_symlink():
                raise ValueError("audio decoder source must not be a symlink")
            if target.exists():
                data = target.read_bytes()
            else:
                url = (
                    f"https://raw.githubusercontent.com/nothings/stb/{REVISION}/{name}"
                )
                with urllib.request.urlopen(url, timeout=30) as response:
                    data = response.read(1024 * 1024 + 1)
            if len(data) > 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
                raise ValueError(
                    f"modified/unexpected pinned audio decoder file: {name}"
                )
            if not target.exists():
                with tempfile.NamedTemporaryFile(dir=OUT, delete=False) as pending:
                    pending.write(data)
                    temporary = Path(pending.name)
                try:
                    temporary.replace(target)
                finally:
                    temporary.unlink(missing_ok=True)
        compiler = subprocess.check_output(["xcrun", "-f", "clang"], text=True).strip()
        sdk = subprocess.check_output(
            ["xcrun", "--sdk", "macosx", "--show-sdk-path"], text=True
        ).strip()
        version = subprocess.check_output([compiler, "--version"], text=True)
        flags = [
            "-std=c99",
            "-O2",
            "-fPIC",
            "-arch",
            "arm64",
            "-isysroot",
            sdk,
            "-DSTB_VORBIS_NO_STDIO",
            "-DSTB_VORBIS_NO_PUSHDATA_API",
        ]
        signature = {
            "revision": REVISION,
            "files": FILES,
            "compiler": version,
            "flags": flags,
        }
        manifest = OUT / "provenance.json"
        library = OUT / "libstb_vorbis.a"
        if manifest.is_symlink() or library.is_symlink():
            raise ValueError("audio decoder build outputs must not be symlinks")
        previous = json.loads(manifest.read_text()) if manifest.exists() else {}
        if (
            previous.get("build") != signature
            or not library.exists()
            or previous.get("archive_sha256") != digest(library)
        ):
            with tempfile.TemporaryDirectory(dir=OUT) as temporary:
                stage = Path(temporary)
                obj, archive = stage / "stb_vorbis.o", stage / library.name
                subprocess.run(
                    [compiler, *flags, "-c", str(OUT / "stb_vorbis.c"), "-o", str(obj)],
                    check=True,
                )
                subprocess.run(
                    ["xcrun", "ar", "rcs", str(archive), str(obj)], check=True
                )
                archive.replace(library)
            manifest.write_text(
                json.dumps(
                    {
                        "build": signature,
                        "archive_sha256": digest(library),
                        "source": "https://github.com/nothings/stb",
                        "license": "MIT OR Unlicense; original LICENSE retained",
                    },
                    indent=2,
                    sort_keys=True,
                )
                + "\n"
            )
    print(f"macOS Vorbis decoder verified: stb {REVISION}")


if __name__ == "__main__":
    main()
