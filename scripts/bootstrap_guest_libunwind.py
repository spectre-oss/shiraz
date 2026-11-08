#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned LLVM libunwind as an actual AArch64 Android ELF archive.

Development fixture only: unchanged upstream sources and Apache-2.0 WITH
LLVM-exception license remain under ignored .deps. Uses existing Xcode clang and an explicit LLVM archiver
and pinned in-tree AOSP bionic headers; no NDK, host library or C++ headers.
An archive build is not proof of exception interoperability with APK libc++.
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
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/guest-libunwind"
SOURCE = OUT / "src"
ARCHIVE = OUT / "lib/libunwind.a"
REVISION = "3b5b5c1ec4a3095ab096dd780e84d7ab81f3d7ff"
FILES = {'LICENSE.TXT': 'b5efebcaca80879234098e52d1725e6d9eb8fb96a19fce625d39184b705f7b6d',
 'include/__libunwind_config.h': '6d9d82e9026adafc29b8f94107de267bfa5aab4a3944c98cd5e476dc7b55a00a',
 'include/libunwind.h': '9f9a52c31dbb093e09ccef880652fdced398070b6062515a2fa118361cf8afb7',
 'include/mach-o/compact_unwind_encoding.h': '06f58e9d0058583b6da91ca2328fa663f80d089496a4979754ee7608fe9bd877',
 'include/unwind.h': 'e4a58637fc8ca1d29cd600c3c4416aafed50085edf276fa076214c8d81c263f0',
 'include/unwind_arm_ehabi.h': '80edc55ea33440f46e676e4ac805ecdc64a7222f2c9107f8ff6b1097eb8a51df',
 'include/unwind_itanium.h': 'c8cc61d806e13a2e75e93f1c15afd432f93a99283d27d15f5a28a3ebee989cba',
 'src/AddressSpace.hpp': 'c12382512621f8c749d4ecfa05964c59905a340ba1ea75adecf944d69b383def',
 'src/CompactUnwinder.hpp': '438d320fb6503e1c82160d96fe8c774e03d51a755226deb28a1b7d248bd4ef9a',
 'src/DwarfInstructions.hpp': 'cb6a9be6832b948089b2dcdb2f008fcca0e723b2269c420d9a138a227bfa1b8c',
 'src/DwarfParser.hpp': 'b468ad4ab82af4f9e9c4aae7664529a49051e22b69ffad964794ae9a9370b87c',
 'src/EHHeaderParser.hpp': '31c22a99d0a0eef3341db272812e343b08b0de00518b2372eb189d977d9dda38',
 'src/FrameHeaderCache.hpp': 'ba9d7a8ae3c936fce48639f3f0964f67657cc8ffcccdb75416d3fa7c304845e9',
 'src/RWMutex.hpp': '3d6f718c49944207310eafc2231767016a971c6be6612d31dcc673312f23e724',
 'src/Registers.hpp': 'f2bcf3dfe5d97adf93caabafecbb5c339f30460cf3d572593918ff7c3c1691d1',
 'src/Unwind-EHABI.cpp': '352ab8ec8eaa93a790c261c418ffea18fc78d55ba805601df369a310dc086417',
 'src/Unwind-EHABI.h': 'c04fdebcd31e26de7615b82751373cc64ef83d02b24dbaaf0e112d0a559acc6b',
 'src/Unwind-seh.cpp': 'fcbd1821df294fe80c4870a03f663cb1156b809a598a53ebfaf68bb57110ae19',
 'src/Unwind-sjlj.c': '708ea7f9377fea810c2df6196448763f9436aff28fb81463d79f09548c70f096',
 'src/Unwind-wasm.c': 'ce35121d9859244d31f3f45ce05b13505ea03db109b353deefa821f8f2693f6c',
 'src/UnwindCursor.hpp': '178e69cfd71ac8f1b7cb8781952628dab054c18a39a8b1a53ea0fa439ce4dfac',
 'src/UnwindLevel1-gcc-ext.c': '3bed106de18fec9d1c52cb7bc3d5db89d3225252f3c0312597d159edbbd6e802',
 'src/UnwindLevel1.c': '60713346aa7712b5292e4cc2a05c0d3400296ebe76d7c25d256ee46a7dc2a174',
 'src/UnwindRegistersRestore.S': '7c66721db60ae08b776119e0475bb4efe6aeaec79dc0386c8cd35cb4aee268f1',
 'src/UnwindRegistersSave.S': '9c7c51e705f73bb22d8113714a47fe9f1a3b84fe5a9484bb6801578523cabc5d',
 'src/Unwind_AIXExtras.cpp': 'fec55a3209fcd9ad4eac0f9820d599ceef2750a155e2aa10f66aacdf6f0fb536',
 'src/assembly.h': 'aa8bbf3a25a47749e48729785240484421391c449923d82aa6c0bc6e4ed67a5a',
 'src/cet_unwind.h': 'b561b31b9c746a41ad7811b33476147e2d0430441eb6d51349bc7a20198f3925',
 'src/config.h': '65d0561fb6af948fbbf32e9c9b087bbc7013c94faae9565d7edabc18336bce95',
 'src/dwarf2.h': 'd57f3a7a4dff483b64a8503d6a0ac8e8c3a79c52da9cdc637b8d53d52a16a1f6',
 'src/libunwind.cpp': '6897d1a337fc38d050b5742f404a623c8988ce9d6a55fe8271c4b7ceee32ef10',
 'src/libunwind_ext.h': 'ee76dbef4b8a910e6d9302e643ae4566ce5ed8f4cbe5e612804506a089b291e1'}
# Upstream's normal ELF zero-cost source set. EHABI/SEH/SJLJ/Wasm files are
# retained and hashed, but compile to no implementation for this AArch64 ABI.
SOURCES = (
    "libunwind.cpp", "Unwind-EHABI.cpp", "Unwind-seh.cpp",
    "UnwindLevel1.c", "UnwindLevel1-gcc-ext.c", "Unwind-sjlj.c",
    "Unwind-wasm.c", "UnwindRegistersRestore.S", "UnwindRegistersSave.S",
)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_path(path):
    if any(parent.is_symlink() for parent in (path, *path.parents)):
        raise ValueError(f"guest libunwind path must not contain symlinks: {path}")


def atomic_write(path, data):
    safe_path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as pending:
        temporary = Path(pending.name)
        pending.write(data)
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def sources():
    for relative, expected in FILES.items():
        target = SOURCE / relative
        safe_path(target)
        if target.exists():
            data = target.read_bytes()
        else:
            url = (f"https://raw.githubusercontent.com/llvm/llvm-project/"
                   f"{REVISION}/libunwind/{relative}")
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read(2 * 1024 * 1024 + 1)
        if len(data) > 2 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"modified/unexpected pinned libunwind file: {relative}")
        if not target.exists():
            atomic_write(target, data)
    entries = list(SOURCE.rglob("*"))
    if any(path.is_symlink() for path in entries):
        raise ValueError("symlink in pinned libunwind source tree")
    actual = {p.relative_to(SOURCE).as_posix() for p in entries if p.is_file()}
    if actual != set(FILES):
        raise ValueError("unexpected files in pinned libunwind source tree")


def execute(arguments, environment):
    subprocess.run([str(value) for value in arguments], env=environment, check=True)


def build(environment):
    safe_path(ARCHIVE)
    headers = ROOT / ".deps/bionic-headers/sysroot/include"
    header_record = ROOT / ".deps/bionic-headers/provenance.json"
    if not headers.is_dir() or not header_record.is_file():
        raise ValueError("run scripts/bootstrap_bionic_headers.py first")
    record = json.loads(header_record.read_text())
    if record.get("tag") != "android-14.0.0_r1" or record.get("commit") != "e4df46f213d8d363017dce7ded5d1f2159614425":
        raise ValueError("unexpected bionic header pin")
    header_hashes = {}
    for path in sorted(headers.rglob("*")):
        safe_path(path)
        if path.is_file():
            header_hashes[path.relative_to(headers).as_posix()] = digest(path)
    # Capture actual input bytes as well as the upstream header revision. The
    # normal bionic bootstrap owns source archive and NOTICE validation.
    headers_digest = hashlib.sha256(json.dumps(header_hashes, sort_keys=True).encode()).hexdigest()
    tools = {name: subprocess.check_output(["xcrun", "--find", name], text=True, env=environment).strip()
             for name in ("clang", "clang++")}
    archiver = environment.get("GUEST_LLVM_AR") or shutil.which("llvm-ar", path=environment.get("PATH"))
    if not archiver:
        raise ValueError("ELF archives require LLVM ar; set GUEST_LLVM_AR to an installed llvm-ar")
    tools["ar"] = str(Path(archiver).resolve(strict=True))
    archiver_version = subprocess.check_output([tools["ar"], "--version"], text=True, env=environment).strip()
    if "LLVM" not in archiver_version:
        raise ValueError("GUEST_LLVM_AR must be a real LLVM archiver")
    compiler = subprocess.check_output([tools["clang"], "--version"], text=True, env=environment).strip()
    common = ["--target=aarch64-linux-android24", "-ffreestanding", "-fPIC",
              "-mno-outline-atomics", "-fno-stack-protector", "-funwind-tables",
              "-fno-exceptions", "-fno-rtti", "-O2", "-DNDEBUG",
              "-D_LIBUNWIND_IS_NATIVE_ONLY", "-I" + str(SOURCE / "include"),
              "-I" + str(SOURCE / "src"), "-isystem", str(headers)]
    inputs = {"revision": REVISION, "sources_sha256": FILES,
              "bionic_revision": record["commit"], "bionic_input_sha256": headers_digest,
              "compiler": compiler, "tools": tools, "flags": common,
              "archiver_version": archiver_version, "archiver_sha256": digest(Path(tools["ar"])),
              "cxx_flags": ["-nostdinc++", "-std=c++17"],
              "c_flags": ["-std=c99"], "threading": "actual pthread rwlocks",
              "frame_discovery": "ELF dl_iterate_phdr through guest ABI"}
    stamp = OUT / "build.json"
    safe_path(stamp)
    if ARCHIVE.is_file() and stamp.is_file():
        old = json.loads(stamp.read_text())
        if old.get("inputs") == inputs and old.get("archive_sha256") == digest(ARCHIVE):
            print(f"Guest libunwind archive verified: {ARCHIVE}")
            return
    stamp.unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(dir=OUT, prefix="build-") as temporary:
        work = Path(temporary)
        objects = []
        commands = []
        for source in SOURCES:
            is_cxx = source.endswith(".cpp")
            flags = (["-nostdinc++", "-std=c++17"] if is_cxx else
                     ["-std=c99"] if source.endswith(".c") else [])
            target = work / (source + ".o")
            command = [tools["clang++"] if is_cxx else tools["clang"],
                       *common, *flags, "-c", str(SOURCE / "src" / source), "-o", str(target)]
            execute(command, environment)
            commands.append(command)
            if target.read_bytes()[:20] != bytes.fromhex("7f454c460201010000000000000000000100b700"):
                raise ValueError(f"compiler did not produce AArch64 ELF relocatable: {source}")
            objects.append(target)
        archive = work / "libunwind.a"
        execute([tools["ar"], "rcsD", archive, *objects], environment)
        if archive.read_bytes()[:8] != b"!<arch>\n":
            raise ValueError("archive tool did not produce a real ar archive")
        atomic_write(ARCHIVE, archive.read_bytes())
        atomic_write(stamp, (json.dumps({"inputs": inputs, "commands": commands,
                      "archive_sha256": digest(ARCHIVE), "execution_verified": False},
                     indent=2, sort_keys=True) + "\n").encode())
    print(f"Guest libunwind archive built (execution unverified): {ARCHIVE}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-only", action="store_true")
    args = parser.parse_args()
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        parser.error("this guest fixture bootstrap requires Darwin arm64")
    safe_path(OUT)
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-libunwind.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        sources()
        atomic_write(OUT / "provenance.json", (json.dumps({
            "revision": REVISION, "files_sha256": FILES,
            "source": "https://github.com/llvm/llvm-project/tree/" + REVISION + "/libunwind",
            "license": "Apache-2.0 WITH LLVM-exception; LICENSE.TXT retained",
            "scope": "development guest exception fixture; not a host fallback",
        }, indent=2, sort_keys=True) + "\n").encode())
        if not args.source_only:
            environment = {k: v for k, v in os.environ.items()
                           if not k.startswith(("DYLD_", "LD_")) and k not in {
                               "SDKROOT", "CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH",
                               "LIBRARY_PATH", "MACOSX_DEPLOYMENT_TARGET"}}
            build(environment)
    print(f"Guest libunwind: {len(FILES)} unchanged source files verified at {REVISION}")


if __name__ == "__main__":
    main()
