#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Stage and optionally sign a private, closed native ART fixture tree.

No credential discovery: the caller supplies a public codesign identity. Preparing
the tree never signs or runs it. Strict library validation remains enabled.
"""

import hashlib
import json
import plistlib
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

LOAD_COMMANDS = {
    "LC_LOAD_DYLIB", "LC_LOAD_WEAK_DYLIB", "LC_REEXPORT_DYLIB",
    "LC_LOAD_UPWARD_DYLIB", "LC_LAZY_LOAD_DYLIB",
}
MAX_IMAGES = 128
MAX_IMAGE_BYTES = 256 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
ALLOW_JIT = {"com.apple.security.cs.allow-jit": True}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def output(command, env):
    return subprocess.check_output(list(map(str, command)), env=env, text=True,
                                   stderr=subprocess.STDOUT, timeout=60)


def metadata(path, env):
    """Read only load commands, not human-oriented otool -L version suffixes."""
    text = output(["otool", "-l", path], env)
    dependencies, rpaths = [], []
    identity = None
    command = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("Load command "):
            command = None
        elif line.startswith("cmd "):
            command = line[4:]
        elif command in LOAD_COMMANDS and line.startswith("name "):
            dependencies.append(line[5:].rsplit(" (offset ", 1)[0])
        elif command == "LC_ID_DYLIB" and line.startswith("name "):
            identity = line[5:].rsplit(" (offset ", 1)[0]
        elif command == "LC_RPATH" and line.startswith("path "):
            rpaths.append(line[5:].rsplit(" (offset ", 1)[0])
    return {"dependencies": dependencies, "rpaths": rpaths, "identity": identity}


def is_system(name):
    path = Path(name)
    return (path.is_absolute() and ".." not in path.parts and
            (name.startswith(("/usr/lib/", "/System/Library/"))))


def within(path, roots):
    return any(path == root or root in path.parents for root in roots)


def regular_macho(path):
    if not path.is_file() or path.stat().st_size > MAX_IMAGE_BYTES:
        raise ValueError(f"missing/oversized native image: {path}")
    with path.open("rb") as stream:
        header = stream.read(8)
    if header != b"\xcf\xfa\xed\xfe\x0c\x00\x00\x01":
        # Current ART gate is arm64-only. Do not silently stage ELF, scripts or
        # unexamined fat slices, whose load commands differ by architecture.
        raise ValueError(f"strict closure requires thin 64-bit Mach-O: {path}")


def stage_closure(root, host, work, env, *, extra_images=(), main_executable=None):
    """Copy first, then rewrite only owned copies; originals remain untouched."""
    root, host, work = root.resolve(), host.resolve(), work.resolve()
    tree = Path(tempfile.mkdtemp(prefix="hardened-closure-", dir=work))
    (tree / "bin").mkdir()
    (tree / "lib64").mkdir()
    # Framework JARs/images are data; no native library directory is symlinked.
    (tree / "framework").symlink_to(host / "framework", target_is_directory=True)
    source_roots = tuple(path.resolve() for path in (
        host / "bin", host / "lib64", root / "build/macos", root / ".local/lib",
    ))
    images, by_source, by_name = [], {}, {}
    total_bytes = 0

    def add(source, executable=False):
        nonlocal total_bytes
        source = source.resolve(strict=True)
        if source in by_source:
            return by_source[source]
        if not within(source, source_roots):
            raise ValueError(f"native dependency outside approved source roots: {source}")
        regular_macho(source)
        if len(images) >= MAX_IMAGES:
            raise ValueError("native closure exceeds image limit")
        source_hash = digest(source)
        destination = tree / ("bin" if executable else "lib64") / source.name
        if destination.name in by_name:
            other = by_name[destination.name]
            if other["source_sha256"] != source_hash:
                raise ValueError(f"ambiguous native dependency basename: {source.name}")
            by_source[source] = other
            return other
        total_bytes += source.stat().st_size
        if total_bytes > MAX_TOTAL_BYTES:
            raise ValueError("native closure exceeds byte limit")
        shutil.copy2(source, destination)
        copied_hash = digest(destination)
        if copied_hash != source_hash:
            raise ValueError(f"native closure copy mismatch: {source}")
        item = {"source": str(source), "path": str(destination),
                "source_sha256": source_hash, "copy_sha256": copied_hash,
                "source_load_commands": metadata(source, env), "rewrites": {}}
        images.append(item)
        by_source[source] = item
        by_name[destination.name] = item
        return item

    main = add(Path(main_executable) if main_executable is not None else host / "bin/dalvikvm",
               executable=True)
    # ART dynamically opens some libraries without a static load command. Copy
    # every current lib64 image, including those libraries, as an explicit seed.
    for source in sorted((host / "lib64").iterdir()):
        if source.is_dir():
            raise ValueError(f"unexpected directory in ART native library seed: {source}")
        add(source)
    bridge = add(root / "build/macos/libshiraz-guest.so")
    fixture = add(root / "build/macos/m5/host-fixture.so")
    extras = {}
    for index, source in enumerate(extra_images):
        if index >= MAX_IMAGES:
            raise ValueError("too many extra native closure seeds")
        source = Path(source).absolute()
        extras[str(source)] = add(source)["path"]

    def expand(value, source):
        if value == "@loader_path":
            return source.parent
        if value == "@executable_path":
            return Path(main["source"]).parent
        if value.startswith("@loader_path/"):
            return source.parent / value.removeprefix("@loader_path/")
        if value.startswith("@executable_path/"):
            return Path(main["source"]).parent / value.removeprefix("@executable_path/")
        if value.startswith("/"):
            return Path(value)
        raise ValueError(f"unsupported native dependency/rpath: {value}")

    index = 0
    while index < len(images):
        item = images[index]
        source = Path(item["source"])
        commands = item["source_load_commands"]
        # Known roots are explicit caller inputs, not a dyld/host search path.
        # @rpath candidates must exist in their original declared location or
        # the ART seed, and ambiguous distinct contents are rejected.
        for dependency in commands["dependencies"]:
            if is_system(dependency):
                continue
            if dependency.startswith("@rpath/"):
                suffix = dependency.removeprefix("@rpath/")
                if Path(suffix).name != suffix:
                    raise ValueError(f"nested @rpath dependency is unsupported: {dependency}")
                candidates = [expand(value, source) / suffix
                              for value in commands["rpaths"]]
                # The main executable's ART rpath can be inherited by DSOs.
                candidates.append(host / "lib64" / suffix)
            else:
                candidates = [expand(dependency, source)]
            found = set()
            for candidate in candidates:
                if candidate.exists():
                    resolved = candidate.resolve(strict=True)
                    if not within(resolved, source_roots):
                        raise ValueError(f"native dependency escapes approved roots: {dependency}")
                    found.add(resolved)
            if not found:
                raise ValueError(f"unresolved native dependency in {source.name}: {dependency}")
            if len({digest(path) for path in found}) != 1:
                raise ValueError(f"ambiguous native dependency in {source.name}: {dependency}")
            target = add(min(found))
            item["rewrites"][dependency] = "@rpath/" + Path(target["path"]).name
        index += 1

    # Short relative load names fit existing Mach-O header space. LC_ID_DYLIB
    # is a library identity, not a runtime search command, so retain it unchanged.
    # Only canonical private rpaths survive; a library without an own rpath can
    # use the executable's explicitly validated lib64 rpath.
    needs_main_rpath = any(item["rewrites"] for item in images)
    for item in images:
        destination = Path(item["path"])
        command = ["install_name_tool"]
        for original, replacement in item["rewrites"].items():
            if original != replacement:
                command += ["-change", original, replacement]
        rpaths = item["source_load_commands"]["rpaths"]
        desired = "@loader_path/../lib64" if item is main else "@loader_path"
        if desired in rpaths:
            for rpath in rpaths:
                if rpath != desired:
                    command += ["-delete_rpath", rpath]
        elif rpaths:
            command += ["-rpath", rpaths[0], desired]
            for rpath in rpaths[1:]:
                command += ["-delete_rpath", rpath]
        elif item is main and needs_main_rpath:
            command += ["-add_rpath", desired]
        if len(command) > 1:
            try:
                output([*command, destination], env)
            except subprocess.CalledProcessError as error:
                raise ValueError(
                    f"private native closure fixup failed for {destination.name}; "
                    "existing header space must fit canonical private load names/rpaths; "
                    "source binaries were not rebuilt or changed: " + error.output[-4096:]
                ) from error
        item["staged_load_commands"] = metadata(destination, env)
        item["prepared_sha256"] = digest(destination)
    manifest = {"schema": 1, "tree": str(tree), "main": main["path"],
                "bridge": bridge["path"], "host_fixture": fixture["path"],
                "extra_images": extras,
                "framework_data_symlink": str(host / "framework"),
                "runtime_routes": {"ANDROID_ROOT": str(tree),
                                   "java.library.path": str(tree / "lib64"),
                                   "NativeBridge": bridge["path"],
                                   "host_fixture": fixture["path"]},
                "identity": None, "team_identifier": None,
                "signed": False, "execution_verified": False, "images": images}
    validate_closure(manifest, env)
    write_manifest(manifest)
    return manifest


def validate_closure(manifest, env):
    tree = Path(manifest["tree"])
    owned = {Path(item["path"]) for item in manifest["images"]}
    main = Path(manifest["main"])
    all_commands = {path: metadata(path, env) for path in owned}

    def private_rpaths(path):
        expected = "@loader_path/../lib64" if path == main else "@loader_path"
        directories = []
        for value in all_commands[path]["rpaths"]:
            if value != expected:
                raise ValueError(f"native closure retains nonprivate runtime search path: {path}: {value}")
            suffix = value.removeprefix("@loader_path")
            directory = (path.parent / suffix.lstrip("/")).resolve()
            if directory != tree / "lib64":
                raise ValueError(f"native closure rpath resolves outside owned lib64: {path}: {value}")
            directories.append(directory)
        return directories

    main_rpaths = private_rpaths(main)
    for item in manifest["images"]:
        path = Path(item["path"])
        if digest(Path(item["source"])) != item["source_sha256"]:
            raise ValueError(f"source native image changed while staging: {item['source']}")
        if path.is_symlink() or path.resolve() != path or not within(path, (tree,)):
            raise ValueError(f"native closure image is not an owned regular copy: {path}")
        search = private_rpaths(path) + main_rpaths
        resolved = {}
        for dependency in all_commands[path]["dependencies"]:
            if is_system(dependency):
                continue
            if not dependency.startswith("@rpath/"):
                raise ValueError(f"native closure dependency escape: {path}: {dependency}")
            name = dependency.removeprefix("@rpath/")
            if Path(name).name != name or name in ("", ".", ".."):
                raise ValueError(f"native closure dependency is not a single owned basename: {dependency}")
            candidates = {directory / name for directory in search if (directory / name).exists()}
            if not candidates or any(candidate not in owned or candidate.is_symlink()
                                     or candidate.resolve() != candidate for candidate in candidates):
                raise ValueError(f"native closure dependency lacks an exact owned resolution: {path}: {dependency}")
            if len(candidates) != 1:
                raise ValueError(f"native closure dependency resolution is ambiguous: {path}: {dependency}")
            resolved[dependency] = str(next(iter(candidates)))
        item["resolved_dependencies"] = resolved


def write_manifest(manifest):
    (Path(manifest["tree"]) / "closure.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n")


def sign_closure(manifest, identity, entitlements, env):
    if not identity or identity == "-" or "\n" in identity or "\0" in identity:
        raise ValueError("strict hardened ART requires an explicit public signing identity with a TeamIdentifier")
    if plistlib.loads(entitlements.read_bytes()) != ALLOW_JIT:
        raise ValueError("hardened ART main entitlements must be exactly allow-jit")
    validate_closure(manifest, env)
    for item in manifest["images"]:
        if digest(Path(item["path"])) != item["prepared_sha256"]:
            raise ValueError("prepared native image changed before signing: " + item["path"])
    # Sign dependencies before main; only the private copies are modified.
    ordered = sorted(manifest["images"], key=lambda item: item["path"] == manifest["main"])
    teams = set()
    for item in ordered:
        path = Path(item["path"])
        command = ["codesign", "--force", "--sign", identity, "--options", "runtime", "--timestamp=none"]
        if item["path"] == manifest["main"]:
            command += ["--entitlements", entitlements]
        output([*command, path], env)
        output(["codesign", "--verify", "--strict", path], env)
        signature = output(["codesign", "--display", "--verbose=4", path], env)
        team = re.search(r"^TeamIdentifier=(.+)$", signature, re.MULTILINE)
        flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
        if team is None or team[1] in ("not set", "-", ""):
            raise ValueError(f"native closure lacks actual TeamIdentifier: {path}")
        if flags is None or not int(flags[1], 16) & 0x10000:
            raise ValueError(f"native closure lacks actual hardened runtime flag: {path}")
        displayed = subprocess.run(["codesign", "--display", "--entitlements", "-", "--xml", str(path)],
                                   env=env, capture_output=True, check=True, timeout=60).stdout
        actual = plistlib.loads(displayed) if displayed.strip() else {}
        expected = ALLOW_JIT if item["path"] == manifest["main"] else {}
        if actual != expected:
            raise ValueError(f"unexpected entitlements on native closure image: {path}")
        teams.add(team[1])
        item["signature"] = signature
        item["team_identifier"] = team[1]
        item["signed_sha256"] = digest(path)
    if len(teams) != 1:
        raise ValueError("native closure libraries do not share one actual TeamIdentifier")
    validate_closure(manifest, env)
    manifest.update(identity=identity, team_identifier=next(iter(teams)), signed=True)
    write_manifest(manifest)
    return manifest


def sign_staged_adhoc_closure(manifest, entitlements, env):
    """Route check only: sign fresh owned copies, without hardened runtime."""
    if plistlib.loads(entitlements.read_bytes()) != ALLOW_JIT:
        raise ValueError("staged ad-hoc main entitlements must be exactly allow-jit")
    validate_closure(manifest, env)
    manifest.update(
        mode="staged-adhoc",
        identity=None,
        team_identifier=None,
        signed=False,
        execution_verified=False,
        hardened_verified=False,
        route_check_only=True,
    )
    write_manifest(manifest)
    for item in manifest["images"]:
        if (
            hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest()
            != item["prepared_sha256"]
        ):
            raise ValueError(
                "prepared native image changed before ad-hoc route signing"
            )
    ordered = sorted(
        manifest["images"], key=lambda item: item["path"] == manifest["main"]
    )
    for item in ordered:
        path = Path(item["path"])
        # Numeric zero clears inherited options; verification below rejects an
        # actual runtime flag. No library-validation-disabling entitlement is used.
        command = [
            "codesign",
            "--force",
            "--sign",
            "-",
            "--options",
            "0",
            "--timestamp=none",
        ]
        if item["path"] == manifest["main"]:
            command += ["--entitlements", entitlements]
        output([*command, path], env)
        output(["codesign", "--verify", "--strict", path], env)
        signature = subprocess.run(
            ["codesign", "--display", "--verbose=4", str(path)],
            env=env,
            capture_output=True,
            text=True,
            check=True,
            timeout=60,
        ).stderr
        flags = re.search(r"flags=0x([0-9a-fA-F]+)", signature)
        team = re.search(r"^TeamIdentifier=(.+)$", signature, re.MULTILINE)
        if (
            flags is None
            or int(flags[1], 16) & 0x10000
            or not int(flags[1], 16) & 0x2
            or not re.search(r"^Signature=adhoc$", signature, re.MULTILINE)
            or team is None
            or team[1] != "not set"
        ):
            raise ValueError(
                "route-check closure must be ad-hoc without runtime flag or TeamIdentifier"
            )
        displayed = subprocess.run(
            ["codesign", "--display", "--entitlements", "-", "--xml", str(path)],
            env=env,
            capture_output=True,
            check=True,
            timeout=60,
        ).stdout
        actual = plistlib.loads(displayed) if displayed.strip() else {}
        expected = ALLOW_JIT if item["path"] == manifest["main"] else {}
        if actual != expected:
            raise ValueError("unexpected entitlements on staged ad-hoc route image")
        item.update(
            signature=signature,
            team_identifier=None,
            signed_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        )
    validate_closure(manifest, env)
    manifest.update(
        mode="staged-adhoc",
        identity="-",
        team_identifier=None,
        signed=True,
        execution_verified=False,
        hardened_verified=False,
        route_check_only=True,
    )
    write_manifest(manifest)
    return manifest

