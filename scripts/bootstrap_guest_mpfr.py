#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned, private static GMP/MPFR for the Darwin guest math bridge.

Upstream source and original license files remain under ignored .deps. No host
package manager, shared library, or existing research install is modified.
"""

import argparse
import fcntl
import hashlib
import json
import os
import platform
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path

import bootstrap_darwin as baseline

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/guest-mpfr-static"
INSTALL = OUT / "install"
PACKAGES = (
    (
        "gmp-6.3.0",
        "https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz",
        "a3c2b80201b89e68616f4ad30bc66aee4927c3ce50e33929ca819d5c43538898",
    ),
    (
        "mpfr-4.2.2",
        "https://www.mpfr.org/mpfr-4.2.2/mpfr-4.2.2.tar.xz",
        "b67ba0383ef7e8a8563734e2e889ef5ec3c3b898a01d00fa0a6869ad81c6ce01",
    ),
)
FILES = ("include/gmp.h", "include/mpfr.h", "lib/libgmp.a", "lib/libmpfr.a")


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            value.update(block)
    return value.hexdigest()


def fetch(stem, url, expected):
    archive = OUT / (stem + ".tar.xz")
    if archive.is_symlink():
        raise ValueError("source archive must not be a symlink")
    if not archive.exists():
        research = ROOT / ".deps/guest-mpfr-research" / archive.name
        with tempfile.NamedTemporaryFile(dir=OUT, delete=False) as pending:
            temporary = Path(pending.name)
            try:
                if (
                    research.is_file()
                    and not research.is_symlink()
                    and digest(research) == expected
                ):
                    with research.open("rb") as source:
                        shutil.copyfileobj(source, pending)
                else:
                    with urllib.request.urlopen(url, timeout=120) as source:
                        total = 0
                        while block := source.read(1024 * 1024):
                            total += len(block)
                            if total > 16 * 1024 * 1024:
                                raise ValueError("source archive exceeds bound")
                            pending.write(block)
                pending.flush()
                if digest(temporary) != expected:
                    raise ValueError("source archive checksum mismatch")
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
    if digest(archive) != expected:
        raise ValueError("modified source archive")
    return archive


def extract(archive, stem):
    source = OUT / "src" / stem
    hashes = {}
    with tarfile.open(archive) as stream:
        members = stream.getmembers()
        if len(members) > 5000 or sum(member.size for member in members) > 64 * 1024 * 1024:
            raise ValueError("source archive exceeds extraction bounds")
        for member in members:
            path = Path(member.name)
            if (
                path.is_absolute()
                or ".." in path.parts
                or not path.parts
                or path.parts[0] != stem
                or "\\" in member.name
                or not (member.isfile() or member.isdir())
            ):
                raise ValueError("unsafe source archive entry")
            relative = Path(*path.parts[1:])
            if not relative.parts:
                continue
            target = source / relative
            if any(parent.is_symlink() for parent in (target, *target.parents)):
                raise ValueError("source path contains a symlink")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                data = stream.extractfile(member).read()
                expected = hashlib.sha256(data).hexdigest()
                if target.exists() and digest(target) != expected:
                    raise ValueError(f"modified private source: {stem}/{relative}")
                if not target.exists():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    target.chmod(member.mode & 0o777)
                # Release tarballs contain generated manuals newer than their
                # inputs. Preserve upstream times so make does not require a
                # host Texinfo installation merely because we extracted them.
                os.utime(target, (member.mtime, member.mtime))
                hashes[relative.as_posix()] = expected
    for path in source.rglob("*"):
        if path.is_symlink() or (
            path.is_file() and path.relative_to(source).as_posix() not in hashes
        ):
            raise ValueError(f"unexpected private source: {stem}/{path.relative_to(source)}")
    return hashes


def installed_hashes(directory):
    if not all((directory / name).is_file() for name in FILES):
        return {}
    result = {}
    for path in directory.rglob("*"):
        if path.is_symlink() or path.suffix == ".dylib":
            raise ValueError("private math install contains a symlink or dynamic library")
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = digest(path)
    return result


def run(args, cwd, environment):
    subprocess.run([str(item) for item in args], cwd=cwd, env=environment, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if not 1 <= args.jobs <= 32:
        parser.error("jobs must be between 1 and 32")
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        raise ValueError("private guest math build requires Apple Silicon macOS")
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-mpfr-static.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if any(path.is_symlink() for path in (OUT, OUT / "src", OUT / "work", INSTALL)):
            raise ValueError("private math directories must not be symlinks")
        sources = {
            stem: extract(fetch(stem, url, expected), stem) for stem, url, expected in PACKAGES
        }
        env = baseline.dependency_env()
        for key in (
            "MAKEFLAGS",
            "MAKEOVERRIDES",
            "MAKEFILES",
            "GMP_CHECK_RANDOMIZE",
            "MPFR_CHECK_EXTERNAL",
        ):
            env.pop(key, None)
        env.update(
            CPPFLAGS="",
            LIBS="",
            M4="/usr/bin/m4",
            PYTHONDONTWRITEBYTECODE="1",
            PATH="/usr/bin:/bin:/usr/sbin:/sbin",
        )
        # Hiding every symbol at compile time also hides GMP's configure test
        # executables' main on Darwin. Final link recipes hide the math symbols
        # explicitly; configure and build programs retain a valid entry point.
        env["CFLAGS"] += " -fPIC"
        recipe = {
            "packages": PACKAGES,
            "sources": sources,
            "gmp_options": ["--disable-shared", "--enable-static", "--disable-assembly"],
            "mpfr_options": ["--disable-shared", "--enable-static", "--enable-thread-safe"],
            "compiler_version": subprocess.check_output([env["CC"], "--version"], text=True),
            "environment": {
                key: env[key]
                for key in (
                    "CC",
                    "AR",
                    "RANLIB",
                    "CFLAGS",
                    "LDFLAGS",
                    "SDKROOT",
                    "MACOSX_DEPLOYMENT_TARGET",
                    "PATH",
                )
            },
        }
        # JSON round-trip canonicalizes tuples so subsequent reads compare equal.
        recipe = json.loads(json.dumps(recipe, sort_keys=True))
        manifest = OUT / "provenance.json"
        if manifest.is_symlink():
            raise ValueError("private math provenance must not be a symlink")
        previous = json.loads(manifest.read_text()) if manifest.exists() else {}
        current = installed_hashes(INSTALL)
        if (
            previous.get("recipe") == recipe
            and current
            and previous.get("installed_files") == current
        ):
            print("Private static GMP/MPFR verified; original licenses retained")
            return
        work = OUT / "work"
        shutil.rmtree(work, ignore_errors=True)
        gmp_build = work / "gmp-build"
        mpfr_build = work / "mpfr-build"
        gmp_prefix = work / "gmp-prefix"
        gmp_build.mkdir(parents=True)
        mpfr_build.mkdir(parents=True)
        run(
            [OUT / "src/gmp-6.3.0/configure", f"--prefix={gmp_prefix}", *recipe["gmp_options"]],
            gmp_build,
            env,
        )
        run(["/usr/bin/make", f"-j{args.jobs}"], gmp_build, env)
        run(["/usr/bin/make", "install"], gmp_build, env)
        run(
            [
                OUT / "src/mpfr-4.2.2/configure",
                f"--prefix={INSTALL}",
                f"--with-gmp={gmp_prefix}",
                *recipe["mpfr_options"],
            ],
            mpfr_build,
            env,
        )
        run(["/usr/bin/make", f"-j{args.jobs}"], mpfr_build, env)
        with tempfile.TemporaryDirectory(dir=OUT, prefix="install-stage-") as temporary:
            stage = Path(temporary)
            run(["/usr/bin/make", "install", f"DESTDIR={stage}"], mpfr_build, env)
            staged = stage / str(INSTALL).lstrip("/")
            for name in ("include/gmp.h", "lib/libgmp.a"):
                (staged / name).parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(gmp_prefix / name, staged / name)
            for stem, licenses in (
                ("gmp-6.3.0", ("COPYING", "COPYING.LESSERv3", "COPYINGv2", "COPYINGv3")),
                ("mpfr-4.2.2", ("COPYING", "COPYING.LESSER")),
            ):
                destination = staged / "share/licenses" / stem
                destination.mkdir(parents=True, exist_ok=True)
                for name in licenses:
                    shutil.copyfile(OUT / "src" / stem / name, destination / name)
            if not installed_hashes(staged):
                raise ValueError("private math installation incomplete")
            retired = OUT / "retired-install"
            if retired.is_symlink():
                raise ValueError("retired installation must not be a symlink")
            shutil.rmtree(retired, ignore_errors=True)
            if INSTALL.exists():
                INSTALL.rename(retired)
            staged.rename(INSTALL)
            shutil.rmtree(retired, ignore_errors=True)
        manifest.write_text(
            json.dumps(
                {"recipe": recipe, "installed_files": installed_hashes(INSTALL)},
                indent=2,
                sort_keys=True,
            )
            + "\n"
        )
        print("Private static GMP/MPFR built; original licenses retained")


if __name__ == "__main__":
    main()
