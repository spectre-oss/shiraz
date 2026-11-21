#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Audit Shiraz Android API 35 framework coverage, DEX shards, and MISSING_ANDROID_LIBRARIES.md."""

import argparse
import datetime
import hashlib
import json
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PINNED_SHA256 = "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0"
BOOT_JAR_NAMES = ("core-oj", "core-libart", "okhttp", "bouncycastle", "wolfssljni")
OPTIONAL_JARS = (
    "android.car.jar",
    "android.test.base.jar",
    "android.test.mock.jar",
    "android.test.runner.jar",
    "org.apache.http.legacy.jar",
    "uiautomator.jar",
)


def default_boot_jars():
    base = ROOT / "context/art_standalone/out/host/common/obj/JAVA_LIBRARIES"
    return [base / f"{name}-hostdex_intermediates/classes.jar" for name in BOOT_JAR_NAMES]


def verify_pinned_sdk(android_jar):
    pinned = ROOT / ".deps/android-sdk/platform-35_r02.zip"
    if not pinned.is_file() or hashlib.sha256(pinned.read_bytes()).hexdigest() != PINNED_SHA256:
        raise ValueError("Pinned Android API 35 reference archive is missing or changed")
    with zipfile.ZipFile(pinned) as archive:
        entries = [name for name in archive.namelist() if name.endswith("/android.jar")]
        if len(entries) != 1 or archive.read(entries[0]) != android_jar.read_bytes():
            raise ValueError("Android jar differs from the pinned API 35 reference")
    return pinned


def area_of(class_entry):
    return "android." + class_entry.split("/")[1] if class_entry.count("/") > 1 else "android"


def package_of(class_entry):
    return class_entry.rsplit("/", 1)[0].replace("/", ".")


def inspect_dex_jar(framework_jar):
    shards = []
    if not framework_jar.is_file():
        return shards
    raw = framework_jar.read_bytes()
    with zipfile.ZipFile(framework_jar) as archive:
        for info in archive.infolist():
            if not (info.filename.startswith("classes") and info.filename.endswith(".dex")):
                continue
            header_offset = info.header_offset
            name_len, extra_len = struct.unpack_from("<HH", raw, header_offset + 26)
            data_offset = header_offset + 30 + name_len + extra_len
            dex_bytes = archive.read(info.filename)
            method_ids = struct.unpack_from("<I", dex_bytes, 88)[0] if len(dex_bytes) >= 92 else 0
            type_ids = struct.unpack_from("<I", dex_bytes, 64)[0] if len(dex_bytes) >= 68 else 0
            class_defs = struct.unpack_from("<I", dex_bytes, 96)[0] if len(dex_bytes) >= 100 else 0
            shards.append(
                {
                    "name": info.filename,
                    "size_bytes": len(dex_bytes),
                    "data_offset": data_offset,
                    "aligned_4b": (data_offset % 4) == 0,
                    "compress_type": info.compress_type,
                    "method_ids": method_ids,
                    "type_ids": type_ids,
                    "class_defs": class_defs,
                }
            )
    return shards


def collect_audit(android_jar, classes_dir, boot_jars, framework_jar):
    pinned = verify_pinned_sdk(android_jar)
    if not classes_dir.is_dir():
        raise ValueError("Build the framework before running audit_framework_apis.py")

    with zipfile.ZipFile(android_jar) as archive:
        all_ref = [name for name in archive.namelist() if name.endswith(".class")]

    ref_android = {
        name
        for name in all_ref
        if name.startswith("android/")
        and name != "android/R.class"
        and not name.startswith("android/R$")
    }
    ref_other = {name for name in all_ref if not name.startswith("android/")}

    framework_classes = {
        path.relative_to(classes_dir).as_posix() for path in classes_dir.rglob("*.class")
    }
    boot_classes = set()
    for bj in boot_jars:
        if bj.is_file():
            with zipfile.ZipFile(bj) as archive:
                boot_classes |= {name for name in archive.namelist() if name.endswith(".class")}

    present_all = framework_classes | boot_classes
    present_android = ref_android & present_all

    areas = {}
    for entry in sorted(ref_android):
        area = area_of(entry)
        row = areas.setdefault(
            area,
            {"area": area, "present": 0, "reference": 0, "missing": 0, "missing_classes": []},
        )
        row["reference"] += 1
        if entry in present_android:
            row["present"] += 1
        else:
            row["missing"] += 1
            short = entry[len(area.replace(".", "/")) + 1 : -len(".class")].replace("/", ".")
            row["missing_classes"].append(short)

    packages = {}
    for entry in sorted(ref_android):
        pkg = package_of(entry)
        row = packages.setdefault(pkg, {"package": pkg, "present": 0, "reference": 0, "missing": 0})
        row["reference"] += 1
        if entry in present_android:
            row["present"] += 1
        else:
            row["missing"] += 1

    other_packages = {}
    for entry in sorted(ref_other):
        pkg = package_of(entry)
        row = other_packages.setdefault(pkg, {"package": pkg, "present": 0, "reference": 0})
        row["reference"] += 1
        row["present"] += int(entry in present_all)

    optional_rows = []
    with zipfile.ZipFile(pinned) as archive:
        names = archive.namelist()
        for opt_name in OPTIONAL_JARS:
            matches = [n for n in names if n.endswith("/" + opt_name)]
            if not matches:
                continue
            with archive.open(matches[0]) as raw_opt, zipfile.ZipFile(raw_opt) as opt_zip:
                opt_classes = {n for n in opt_zip.namelist() if n.endswith(".class")}
            optional_rows.append(
                {
                    "jar": opt_name,
                    "present": len(opt_classes & present_all),
                    "reference": len(opt_classes),
                }
            )

    return {
        "present": len(present_android),
        "framework_present": len(ref_android & framework_classes),
        "boot_present": len(ref_android & boot_classes),
        "reference": len(ref_android),
        "missing": len(ref_android) - len(present_android),
        "percent": round(100.0 * len(present_android) / len(ref_android), 2),
        "areas": sorted(areas.values(), key=lambda r: (-r["missing"], r["area"])),
        "packages": sorted(packages.values(), key=lambda r: r["package"]),
        "other_packages": sorted(other_packages.values(), key=lambda r: r["package"]),
        "optional_jars": optional_rows,
        "dex_shards": inspect_dex_jar(framework_jar),
    }


def extract_ndk_section(existing_doc):
    text = existing_doc.read_text()
    marker_start = "## 5. NDK native libraries\n"
    marker_end = "\n## 6. Every missing class\n"
    start = text.index(marker_start)
    end = text.index(marker_end)
    return text[start:end].rstrip() + "\n"


def render_missing_doc(audit, existing_doc):
    today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()  # noqa: UP017
    commit = subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], cwd=ROOT, text=True
    ).strip()
    version = (ROOT / "VERSION").read_text().strip()
    ndk_section = extract_ndk_section(existing_doc)

    empty_pkgs = sorted(
        [p for p in audit["packages"] if p["present"] == 0],
        key=lambda r: (-r["reference"], r["package"]),
    )
    partial_pkgs = sorted(
        [p for p in audit["packages"] if 0 < p["present"] < p["reference"]],
        key=lambda r: (-r["missing"], r["package"]),
    )
    complete_pkgs = sorted(
        [p["package"] for p in audit["packages"] if p["present"] == p["reference"]]
    )

    lines = [
        "# Missing Android libraries",
        "",
        f"Generated {today} from the working tree at `{commit}` "
        f"(VERSION {version}, with uncommitted framework changes) against the pinned "
        "Android 15 / API 35 SDK jar (`platform-35_r02.zip`).",
        "",
        '"Missing" means the class name is absent from both `build/framework/classes` and the ART '
        "boot classpath jars the runtime loads (core-oj, core-libart, okhttp, bouncycastle, "
        f"wolfssljni from the local art_standalone build). The boot jars supply "
        f"{audit['boot_present']} of the present classes, mostly `android.icu` and "
        "`android.system`. A class being present says nothing by itself about full CTS behavior, "
        'so the "partly present" packages below are not complete packages. `android.R` resource '
        "classes are excluded. `java.*` and `javax.*` come from the ART core libraries, not this "
        "tree, and are not measured here.",
        "",
        f"**{audit['present']:,} of {audit['reference']:,} `android.*` class names present "
        f"({100.0 * audit['present'] / audit['reference']:.1f}%); "
        f"{audit['missing']:,} missing.**",
        "",
        "Sections: [framework areas](#1-framework-areas) · "
        "[packages with nothing](#2-packages-with-no-classes-at-all) · "
        "[packages partly present](#3-packages-partly-present) · "
        "[other namespaces and optional jars](#4-other-namespaces-and-optional-sdk-jars) · "
        "[NDK libraries](#5-ndk-native-libraries) · "
        "[every missing class](#6-every-missing-class)",
        "",
        "## 1. Framework areas",
        "",
        "Sorted by number of missing classes.",
        "",
        "| Area | Present | Reference | Missing |",
        "|---|--:|--:|--:|",
    ]
    for row in audit["areas"]:
        lines.append(
            f"| `{row['area']}` | {row['present']} | {row['reference']} | {row['missing']} |"
        )

    empty_total = sum(p["reference"] for p in empty_pkgs)
    lines.extend(
        [
            "",
            "## 2. Packages with no classes at all",
            "",
            f"{len(empty_pkgs)} of {len(audit['packages'])} packages, {empty_total:,} classes.",
            "",
            "| Package | Classes |",
            "|---|--:|",
        ]
    )
    for row in empty_pkgs:
        lines.append(f"| `{row['package']}` | {row['reference']} |")

    partial_missing = sum(p["missing"] for p in partial_pkgs)
    lines.extend(
        [
            "",
            "## 3. Packages partly present",
            "",
            f"{len(partial_pkgs)} packages, {partial_missing:,} classes missing.",
            "",
            "| Package | Present | Reference | Missing |",
            "|---|--:|--:|--:|",
        ]
    )
    for row in partial_pkgs:
        lines.append(
            f"| `{row['package']}` | {row['present']} | {row['reference']} | {row['missing']} |"
        )

    complete_list = ", ".join(f"`{name}`" for name in complete_pkgs)
    lines.extend(
        [
            "",
            f"Every class name present in {len(complete_pkgs)} packages: {complete_list}.",
            "",
            "## 4. Other namespaces and optional SDK jars",
            "",
            "Non-`android.*` packages shipped in the SDK `android.jar`, matched against the same "
            "framework directory and boot jars.",
            "",
            "| Package | Present | Reference |",
            "|---|--:|--:|",
        ]
    )
    for row in audit["other_packages"]:
        lines.append(f"| `{row['package']}` | {row['present']} | {row['reference']} |")

    lines.extend(
        [
            "",
            "Optional SDK libraries (apps request them with `<uses-library>`):",
            "",
            "| Jar | Present | Reference |",
            "|---|--:|--:|",
        ]
    )
    for row in audit["optional_jars"]:
        lines.append(f"| `{row['jar']}` | {row['present']} | {row['reference']} |")

    lines.extend(
        [
            "",
            "Not covered: AndroidX, Material, Play services and other libraries that apps bundle "
            "in their own DEX, and Google proprietary APIs (`com.google.android.gms.*`).",
            "",
            ndk_section.rstrip(),
            "",
            "",
            "## 6. Every missing class",
            "",
            "Nested classes appear as `Outer$Inner`.",
            "",
        ]
    )
    for row in sorted(audit["areas"], key=lambda r: r["area"]):
        if row["missing"] == 0:
            continue
        joined = ", ".join(f"`{cls}`" for cls in row["missing_classes"])
        lines.extend(
            [
                f"<details><summary><code>{row['area']}</code>: "
                f"{row['missing']} of {row['reference']} missing</summary>",
                "",
                joined,
                "",
                "</details>",
                "",
            ]
        )
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", type=Path, default=ROOT / ".deps/android-sdk/android.jar")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/framework/classes")
    parser.add_argument("--framework-jar", type=Path, default=ROOT / "build/shiraz-framework.jar")
    parser.add_argument(
        "--missing-doc", type=Path, default=ROOT / "docs/MISSING_ANDROID_LIBRARIES.md"
    )
    parser.add_argument("--write-missing-doc", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    audit = collect_audit(args.android_jar, args.classes, default_boot_jars(), args.framework_jar)
    for shard in audit["dex_shards"]:
        if not shard["aligned_4b"] or shard["compress_type"] != zipfile.ZIP_STORED:
            raise ValueError(f"Unaligned or compressed DEX shard in jar: {shard}")

    if args.write_missing_doc:
        args.missing_doc.write_text(render_missing_doc(audit, args.missing_doc))

    if args.json:
        summary = {k: v for k, v in audit.items() if k not in ("packages", "other_packages")}
        for row in summary["areas"]:
            row.pop("missing_classes", None)
        print(json.dumps(summary, indent=2))
        return

    complete_areas = [r["area"] for r in audit["areas"] if r["missing"] == 0]
    print(
        f"API 35 android.* classes: {audit['present']}/{audit['reference']} "
        f"({audit['percent']:.2f}%) [framework={audit['framework_present']}, "
        f"boot={audit['boot_present']}, missing={audit['missing']}]"
    )
    print(
        f"100% areas ({len(complete_areas)}/{len(audit['areas'])}): {', '.join(sorted(complete_areas))}"
    )
    for shard in audit["dex_shards"]:
        print(
            f"DEX shard {shard['name']}: {shard['size_bytes']} bytes, "
            f"methods={shard['method_ids']}/65536, types={shard['type_ids']}, "
            f"classes={shard['class_defs']}, offset={shard['data_offset']} (4B aligned={shard['aligned_4b']})"
        )


if __name__ == "__main__":
    main()
