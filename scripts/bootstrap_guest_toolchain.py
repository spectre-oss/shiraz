#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Stage an existing Rust LLVM linker in-tree for freestanding M5 ELF fixtures.

No package installation or NDK download. Use --rust-sysroot to select an existing
Rust toolchain; otherwise ask rustup for stable. Binaries and their licenses stay
under ignored .deps. This is a development tool, never an app runtime dependency.
"""

import argparse
import fcntl
import hashlib
import json
import os
import platform
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / ".deps/guest-toolchain"
LLVM_REVISION = "52ed14fcd56afc30f9cccd8ca8ce237c2eef7e04"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def stage():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rust-sysroot", type=Path)
    args = parser.parse_args()
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        parser.error("this fixture toolchain currently requires Darwin arm64")
    source = args.rust_sysroot
    if source is None:
        source = Path(
            subprocess.check_output(
                ["rustup", "run", "stable", "rustc", "--print", "sysroot"],
                text=True,
            ).strip()
        )
    source = source.resolve(strict=True)
    linker = source / "lib/rustlib/aarch64-apple-darwin/bin/rust-lld"
    library = source / "lib/libLLVM.dylib"
    env = dict(os.environ, DYLD_LIBRARY_PATH=str(source / "lib"))
    version = subprocess.check_output(
        [str(linker), "-flavor", "gnu", "--version"],
        text=True,
        env=env,
    ).strip()
    if not version.startswith("LLD 22.1.8 ") or LLVM_REVISION not in version:
        parser.error(f"unsupported fixture LLVM build: {version}")
    files = {linker: "bin/ld.lld", library: "lib/libLLVM.dylib"}
    for name in ("Apache-2.0.txt", "LLVM-exception.txt", "NCSA.txt"):
        files[source / "share/doc/rust/licenses" / name] = f"licenses/{name}"
    files[source / "share/doc/rust/COPYRIGHT.html"] = "licenses/COPYRIGHT.html"
    # Invalidate the completion record first: interrupted staging cannot pass.
    DESTINATION.mkdir(parents=True, exist_ok=True)
    stamp = DESTINATION / "provenance.json"
    stamp.unlink(missing_ok=True)
    hashes = {}
    for original, relative in files.items():
        target = DESTINATION / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        original_hash = sha256(original)
        # Never rewrite a mapped executable inode: macOS can kill concurrent
        # invocations when its code signature pages change underneath them.
        if not target.is_file() or sha256(target) != original_hash:
            with tempfile.NamedTemporaryFile(
                dir=target.parent, prefix=".stage-", delete=False
            ) as pending:
                temporary = Path(pending.name)
            try:
                shutil.copy2(original, temporary)
                if sha256(temporary) != original_hash:
                    raise RuntimeError(f"toolchain copy mismatch: {relative}")
                temporary.replace(target)
            finally:
                temporary.unlink(missing_ok=True)
        if sha256(target) != original_hash:
            raise RuntimeError(f"toolchain copy mismatch: {relative}")
        hashes[relative] = original_hash
    staged_version = subprocess.check_output(
        [str(DESTINATION / "bin/ld.lld"), "--version"],
        text=True,
        env={k: v for k, v in os.environ.items() if not k.startswith("DYLD_")},
    ).strip()
    if staged_version != version:
        raise RuntimeError("staged linker version mismatch")
    stamp.write_text(
        json.dumps(
            {
                "llvm_revision": LLVM_REVISION,
                "version": version,
                "source_sysroot": str(source),
                "files_sha256": hashes,
                "source": "https://github.com/rust-lang/llvm-project",
                "scope": "development fixture linker; not redistributed",
            },
            indent=2,
            sort_keys=True,
        )
        + "\n"
    )
    print(f"Guest fixture linker staged: {version}")


def main():
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    with (DESTINATION.parent / "guest-toolchain.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        stage()


if __name__ == "__main__":
    main()
