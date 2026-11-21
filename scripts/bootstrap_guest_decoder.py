#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build a pinned AArch64-only Capstone decoder in ignored .deps.

Original source/license notices stay intact. This script does not install any
host packages and does not relabel the third-party library as GPL-only.
"""

import fcntl
import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path

from guest_decoder_lookup import build_mapping_object, optimized_mapping
from guest_decoder_stream import generate as generate_stream
from script_common import sha256_file as digest

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/capstone"
REVISION = "5cca00533dadfe53181f1de3525f859769f69b65"
SHA256 = "ca0ef4998ec8a682b519a65d75e83e0fff5b0797c6e28942059976379fad5bc4"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-decoder.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        archive = OUT / "source.tar.gz"
        if not archive.exists():
            url = f"https://codeload.github.com/capstone-engine/capstone/tar.gz/{REVISION}"
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read(32 * 1024 * 1024 + 1)
            if len(data) > 32 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != SHA256:
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
                if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk():
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
        sdk = subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip()
        mapping_dir = source / "arch/AArch64"
        generated = optimized_mapping(
            (mapping_dir / "AArch64Mapping.c").read_text(encoding="utf-8"),
            (mapping_dir / "AArch64MappingInsnName.inc").read_text(encoding="utf-8"),
        )
        stream_inputs = {
            name: (source / name).read_text(encoding="utf-8")
            for name in ("cs.c", "SStream.c", "SStream.h")
        }
        generated_stream = generate_stream(stream_inputs)
        recipe = {
            "revision": REVISION,
            "archive_sha256": SHA256,
            "files": hashes,
            "compiler": compiler,
            "compiler_version": version,
            "sdk": sdk,
            "bootstrap_sha256": digest(Path(__file__)),
            "lookup_generator_sha256": digest(ROOT / "scripts/guest_decoder_lookup.py"),
            "stream_generator_sha256": digest(ROOT / "scripts/guest_decoder_stream.py"),
            "generated_mapping_sha256": hashlib.sha256(generated.encode("utf-8")).hexdigest(),
            "generated_stream_sha256": {
                name: hashlib.sha256(value.encode("utf-8")).hexdigest()
                for name, value in generated_stream.items()
            },
            "scope": "runtime AArch64 decoder; original LICENSE.TXT/notices retained",
        }
        stamp = OUT / "provenance.json"
        library = OUT / "build/libcapstone.a"
        if stamp.is_symlink() or library.is_symlink() or library.parent.is_symlink():
            raise ValueError("decoder build path is a symlink")
        previous = json.loads(stamp.read_text()) if stamp.exists() else {}
        if (
            not library.is_file()
            or any(previous.get(k) != v for k, v in recipe.items())
            or previous.get("library_sha256") != digest(library)
        ):
            build_env = {**os.environ, "ZERO_AR_DATE": "1"}
            with tempfile.TemporaryDirectory(prefix="decoder-stage-", dir=OUT) as temporary:
                stage = Path(temporary)
                staged_source = stage / "src"
                work = stage / "build"
                work_library = work / "libcapstone.a"
                # Do not follow unexpected links outside the verified tree.
                for root, directories, files in os.walk(source, followlinks=False):
                    if any((Path(root) / name).is_symlink() for name in directories + files):
                        raise ValueError("decoder source contains a symlink")
                shutil.copytree(source, staged_source)
                for name, value in generated_stream.items():
                    (staged_source / name).write_text(value, encoding="utf-8")
                subprocess.run(
                    [
                        "cmake",
                        "-S",
                        str(staged_source),
                        "-B",
                        str(work),
                        "-DCMAKE_BUILD_TYPE=Release",
                        f"-DCMAKE_C_COMPILER={compiler}",
                        "-DCMAKE_OSX_ARCHITECTURES=arm64",
                        f"-DCMAKE_OSX_SYSROOT={sdk}",
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
                    env=build_env,
                )
                subprocess.run(
                    ["cmake", "--build", str(work), "--parallel", "2"], check=True, env=build_env
                )
                generated_hash = build_mapping_object(
                    staged_source, work_library, work / "shiraz-lookup", compiler, sdk
                )
                if generated_hash != recipe["generated_mapping_sha256"]:
                    raise ValueError("decoder lookup generation changed during build")
                library.parent.mkdir(parents=True, exist_ok=True)
                work_library.replace(library)
        recipe["library_sha256"] = digest(library)
        pending = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", dir=OUT, delete=False
            ) as file:
                pending = Path(file.name)
                file.write(json.dumps(recipe, indent=2, sort_keys=True) + "\n")
            pending.replace(stamp)
        finally:
            if pending is not None:
                pending.unlink(missing_ok=True)
        print(f"Guest AArch64 decoder verified: Capstone {REVISION}")


if __name__ == "__main__":
    main()
