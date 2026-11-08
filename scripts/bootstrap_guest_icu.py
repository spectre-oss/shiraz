#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build private static ICU UC/data for hardened guest-loader binaries.

Uses exactly bootstrap_darwin.py's pinned ICU release/archive. Existing shared
ICU source, build and .local installations are read-only inputs, never modified.
Original upstream source and license stay under ignored .deps.
"""

import argparse
import fcntl
import hashlib
import json
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path

import bootstrap_darwin as baseline

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/guest-icu-static"
SOURCE = OUT / "src/icu"
BUILD = OUT / "build"
INSTALL = OUT / "install"
ARCHIVES = ("lib/libicuuc.a", "lib/libicudata.a")


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            value.update(block)
    return value.hexdigest()


def archive_source():
    existing = ROOT / f".deps/icu4c-{baseline.ICU_VERSION}-sources.tgz"
    archive = existing if existing.exists() else OUT / "source.tar.gz"
    if archive.is_symlink():
        raise ValueError("ICU source archive must not be a symlink")
    if not archive.exists():
        with tempfile.NamedTemporaryFile(dir=OUT, delete=False) as pending:
            temporary = Path(pending.name)
            try:
                with urllib.request.urlopen(baseline.ICU_URL, timeout=120) as response:
                    total = 0
                    while block := response.read(1024 * 1024):
                        total += len(block)
                        if total > 64 * 1024 * 1024:
                            raise ValueError("ICU source archive exceeds size bound")
                        pending.write(block)
                pending.flush()
                if digest(temporary) != baseline.ICU_SHA256:
                    raise ValueError("unexpected ICU source archive checksum")
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
    if digest(archive) != baseline.ICU_SHA256:
        raise ValueError("modified ICU source archive")
    return archive


def extract_verified(archive):
    """Private extraction and comparison; never configure the shared ICU tree."""
    hashes = {}
    with tarfile.open(archive) as stream:
        members = stream.getmembers()
        if len(members) > 10000 or sum(m.size for m in members) > 256 * 1024 * 1024:
            raise ValueError("ICU extracted source exceeds bounds")
        for member in members:
            path = Path(member.name)
            if (
                path.is_absolute()
                or ".." in path.parts
                or not path.parts
                or path.parts[0] != "icu"
                or "\\" in member.name
                or member.issym()
                or member.islnk()
            ):
                raise ValueError("unsafe ICU archive entry")
            relative = Path(*path.parts[1:])
            if not relative.parts:
                continue
            target = SOURCE / relative
            if any(parent.is_symlink() for parent in (target, *target.parents)):
                raise ValueError("private ICU source path contains a symlink")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                data = stream.extractfile(member).read()
                expected = hashlib.sha256(data).hexdigest()
                if target.exists() and digest(target) != expected:
                    raise ValueError(f"modified private ICU source: {relative}")
                if not target.exists():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    target.chmod(member.mode & 0o777)
                hashes[relative.as_posix()] = expected
            else:
                raise ValueError("unsupported ICU source archive member")
    for path in SOURCE.rglob("*"):
        if path.is_symlink() or (
            path.is_file() and path.relative_to(SOURCE).as_posix() not in hashes
        ):
            raise ValueError(
                f"unexpected private ICU source file: {path.relative_to(SOURCE)}"
            )
    return hashes


def installed_hashes(prefix):
    if not all((prefix / name).is_file() for name in ARCHIVES):
        return {}
    if not (prefix / "include/unicode/uchar.h").is_file():
        return {}
    hashes = {}
    for path in prefix.rglob("*"):
        if path.is_symlink() or path.suffix == ".dylib":
            raise ValueError("private static ICU install contains a symlink/dylib")
        if path.is_file():
            hashes[path.relative_to(prefix).as_posix()] = digest(path)
    return hashes


def run(arguments, cwd, environment):
    subprocess.run(
        [str(item) for item in arguments], cwd=cwd, env=environment, check=True
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if args.jobs < 1 or args.jobs > 32:
        parser.error("jobs must be between 1 and 32")
    if baseline.ICU_VERSION != "78.3":
        raise ValueError(
            "review the private-static ICU recipe when the shared pin changes"
        )
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-icu-static.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if any(path.is_symlink() for path in (OUT, SOURCE, BUILD, INSTALL)):
            raise ValueError("private ICU build directories must not be symlinks")
        archive = archive_source()
        hashes = extract_verified(archive)
        environment = baseline.dependency_env()
        for key in (
            "MAKEFLAGS",
            "MAKEOVERRIDES",
            "MAKEFILES",
            "ICU_DATA",
            "ICU_PLUGINS",
        ):
            environment.pop(key, None)
        environment.update(PYTHONDONTWRITEBYTECODE="1", CPPFLAGS="", LIBS="")
        environment["CFLAGS"] += " -fPIC"
        environment["CXXFLAGS"] += " -fPIC"
        configure = [
            SOURCE / "source/configure",
            f"--prefix={INSTALL}",
            f"--libdir={INSTALL / 'lib'}",
            "--enable-static",
            "--disable-shared",
            "--with-data-packaging=static",
            "--disable-tests",
            "--disable-samples",
            "--disable-extras",
            "--disable-icuio",
            "--disable-layoutex",
            "--disable-icu-config",
            "--disable-dyload",
            "--disable-debug",
            "--enable-release",
        ]
        # ICU's tools remain enabled because pkgdata/icupkg generate the static
        # data archive. They are private build prerequisites, not installed tools.
        signature = {
            "version": baseline.ICU_VERSION,
            "archive_sha256": baseline.ICU_SHA256,
            "source_url": baseline.ICU_URL,
            "files": hashes,
            "configure": [str(item) for item in configure],
            "compiler": environment["CC"],
            "compiler_version": subprocess.check_output(
                [environment["CC"], "--version"], text=True
            ),
            "environment": {
                key: environment[key]
                for key in (
                    "CC",
                    "CXX",
                    "AR",
                    "RANLIB",
                    "CFLAGS",
                    "CXXFLAGS",
                    "LDFLAGS",
                    "SDKROOT",
                    "MACOSX_DEPLOYMENT_TARGET",
                )
            },
        }
        manifest = OUT / "provenance.json"
        if manifest.is_symlink():
            raise ValueError("private ICU provenance must not be a symlink")
        previous = json.loads(manifest.read_text()) if manifest.exists() else {}
        current_outputs = installed_hashes(INSTALL)
        if (
            previous.get("recipe") != signature
            or not current_outputs
            or previous.get("installed_files") != current_outputs
        ):
            configured = OUT / "configured.json"
            recipe_matches = (
                (BUILD / "config.status").is_file()
                and configured.is_file()
                and json.loads(configured.read_text()) == signature
            )
            if not recipe_matches:
                shutil.rmtree(BUILD, ignore_errors=True)
                BUILD.mkdir()
                run(configure, BUILD, environment)
                configured.write_text(
                    json.dumps(signature, indent=2, sort_keys=True) + "\n"
                )
            run(["/usr/bin/make", f"-j{args.jobs}"], BUILD, environment)
            # Stage only UC/data archives plus their headers/licenses. Do not
            # install tools, i18n, or anything into the existing .local prefix.
            with tempfile.TemporaryDirectory(
                dir=OUT, prefix="install-stage-"
            ) as temporary:
                stage = Path(temporary)
                for directory in ("data", "common"):
                    run(
                        ["/usr/bin/make", "install", f"DESTDIR={stage}"],
                        BUILD / directory,
                        environment,
                    )
                staged = stage / str(INSTALL).lstrip("/")
                license_dir = staged / "share/licenses/icu"
                license_dir.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(SOURCE / "LICENSE", license_dir / "LICENSE")
                (license_dir / "PROVENANCE").write_text(
                    f"ICU {baseline.ICU_VERSION}\nsource: {baseline.ICU_URL}\nsha256: {baseline.ICU_SHA256}\n"
                    "Unmodified upstream source; private static UC/data build by bootstrap_guest_icu.py.\n"
                )
                if not installed_hashes(staged):
                    raise ValueError(
                        "private ICU static archives/headers were not installed"
                    )
                retired = OUT / "retired-install"
                if retired.is_symlink():
                    raise ValueError(
                        "private ICU retired install must not be a symlink"
                    )
                shutil.rmtree(retired, ignore_errors=True)
                if INSTALL.exists():
                    INSTALL.rename(retired)
                staged.rename(INSTALL)
                shutil.rmtree(retired, ignore_errors=True)
            manifest.write_text(
                json.dumps(
                    {"recipe": signature, "installed_files": installed_hashes(INSTALL)},
                    indent=2,
                    sort_keys=True,
                )
                + "\n"
            )
    print(
        f"Private static ICU {baseline.ICU_VERSION} verified: UC/data; original LICENSE retained"
    )


if __name__ == "__main__":
    main()
