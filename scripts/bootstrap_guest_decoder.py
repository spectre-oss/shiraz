#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a pinned AArch64-only Capstone decoder in ignored .deps.

Original source/license notices stay intact. This script does not install any
host packages and does not relabel the third-party library as GPL-only.
"""

import fcntl
import hashlib
import json
import subprocess
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/capstone"
REVISION = "5cca00533dadfe53181f1de3525f859769f69b65"
SHA256 = "ca0ef4998ec8a682b519a65d75e83e0fff5b0797c6e28942059976379fad5bc4"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-decoder.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        archive = OUT / "source.tar.gz"
        if not archive.exists():
            url = f"https://codeload.github.com/capstone-engine/capstone/tar.gz/{REVISION}"
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read(32 * 1024 * 1024 + 1)
            if (
                len(data) > 32 * 1024 * 1024
                or hashlib.sha256(data).hexdigest() != SHA256
            ):
                raise ValueError("unexpected Capstone archive")
            archive.write_bytes(data)
        if archive.is_symlink() or digest(archive) != SHA256:
            raise ValueError("modified Capstone archive")
        source = OUT / "src"
        hashes = {}
        with tarfile.open(archive) as stream:
            members = stream.getmembers()
            if len(members) > 10000 or sum(m.size for m in members) > 128 * 1024 * 1024:
                raise ValueError("Capstone source bounds")
            for member in members:
                path = Path(member.name)
                if (
                    path.is_absolute()
                    or ".." in path.parts
                    or member.issym()
                    or member.islnk()
                ):
                    raise ValueError("unsafe Capstone archive path")
                relative = Path(*path.parts[1:])
                if not relative.parts:
                    continue
                target = source / relative
                if target.is_symlink():
                    raise ValueError("Capstone source symlink")
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                elif member.isfile():
                    data = stream.extractfile(member).read()
                    expected = hashlib.sha256(data).hexdigest()
                    if target.exists() and digest(target) != expected:
                        raise ValueError(f"modified Capstone source: {relative}")
                    if not target.exists():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(data)
                        target.chmod(member.mode & 0o777)
                    hashes[relative.as_posix()] = expected
                else:
                    raise ValueError("unsupported Capstone archive entry")
        compiler = subprocess.check_output(["xcrun", "-f", "clang"], text=True).strip()
        version = subprocess.check_output([compiler, "--version"], text=True)
        recipe = {
            "revision": REVISION,
            "archive_sha256": SHA256,
            "files": hashes,
            "compiler": compiler,
            "compiler_version": version,
            "scope": "runtime AArch64 decoder; original LICENSE.TXT/notices retained",
        }
        stamp = OUT / "provenance.json"
        library = OUT / "build/libcapstone.a"
        previous = json.loads(stamp.read_text()) if stamp.exists() else {}
        if (
            not library.is_file()
            or any(previous.get(k) != v for k, v in recipe.items())
            or previous.get("library_sha256") != digest(library)
        ):
            subprocess.run(
                [
                    "cmake",
                    "-S",
                    str(source),
                    "-B",
                    str(OUT / "build"),
                    "-DCMAKE_BUILD_TYPE=Release",
                    f"-DCMAKE_C_COMPILER={compiler}",
                    "-DCMAKE_OSX_ARCHITECTURES=arm64",
                    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DCAPSTONE_BUILD_MACOS_THIN=ON",
                    "-DCAPSTONE_ARCHITECTURE_DEFAULT=OFF",
                    "-DCAPSTONE_ARM64_SUPPORT=ON",
                    "-DCAPSTONE_BUILD_TESTS=OFF",
                    "-DCAPSTONE_BUILD_CSTOOL=OFF",
                    "-DCAPSTONE_BUILD_CSTEST=OFF",
                    "-DCAPSTONE_INSTALL=OFF",
                ],
                check=True,
            )
            subprocess.run(
                ["cmake", "--build", str(OUT / "build"), "--parallel", "2"], check=True
            )
        recipe["library_sha256"] = digest(library)
        stamp.write_text(json.dumps(recipe, indent=2, sort_keys=True) + "\n")
        print(f"Guest AArch64 decoder verified: Capstone {REVISION}")


if __name__ == "__main__":
    main()
