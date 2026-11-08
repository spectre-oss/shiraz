#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Export bounded, non-private Android class/probe inventory for the website."""
import argparse
import datetime
import hashlib
import json
import math
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUSES = ("PASS", "FAIL", "UNSUPPORTED", "SKIPPED", "TIMEOUT", "NOT_RUN")


def load_report(path):
    if path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError("Probe report exceeds 2 MiB")
    report = json.loads(path.read_bytes())
    if not isinstance(report, dict) or report.get("schemaVersion") != 1 or not isinstance(report.get("results"), dict):
        raise ValueError("Unsupported probe report schema")
    for key, result in report["results"].items():
        if not isinstance(result, dict):
            raise ValueError("Invalid probe result")  # noqa: TRY004 - invalid report schema
        duration = result.get("durationMs")
        if key != result.get("id") or result.get("status") not in STATUSES[:-1]:
            raise ValueError("Invalid probe result identity/status")
        if isinstance(duration, bool) or not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration < 0:
            raise ValueError("Invalid result duration")
        if result.get("mode") not in ("automatic", "manual") or not isinstance(result.get("expected"), str):
            raise ValueError("Invalid assertion metadata")
    return report


def catalog(path):
    if path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError("Probe catalog exceeds 2 MiB")
    data = json.loads(path.read_bytes())
    if not isinstance(data, list) or len(data) > 10000:
        raise ValueError("Invalid probe catalog")
    ids = set()
    for case in data:
        if case["id"] in ids or case["mode"] not in ("automatic", "manual"):
            raise ValueError("Duplicate identity or invalid probe mode")
        ids.add(case["id"])
    return data


def fingerprint(directory):
    digest = hashlib.sha256()
    total = 0
    files = 0
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError("Probe source snapshot contains symbolic links")
        if path.is_file():
            size = path.stat().st_size
            total += size
            files += 1
            if size > 4 * 1024 * 1024 or total > 32 * 1024 * 1024 or files > 10000:
                raise ValueError("Probe source snapshot exceeds bounds")
            digest.update(path.relative_to(directory).as_posix().encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())
            digest.update(b"\0")
    return digest.hexdigest()


def probe_snapshot(report_path, source):
    report = load_report(report_path)
    cases = catalog(source / "catalog.json")
    if fingerprint(source) != report.get("suiteFingerprint") or len(cases) != report.get("catalogSize"):
        raise ValueError("Probe report does not match its exact source snapshot")
    definitions = {case["id"]: case for case in cases}
    for key, result in report["results"].items():
        case = definitions.get(key)
        if not case or any(result.get(field) != case[field] for field in ("expected", "mode", "category")):
            raise ValueError("Probe result definition mismatch")
    rows = {}
    for case in cases:
        counts = rows.setdefault(case["category"], dict.fromkeys(STATUSES, 0))
        counts[report["results"].get(case["id"], {}).get("status", "NOT_RUN")] += 1
    return {
        "label": "Historical targeted network run",
        "suiteVersion": report["suiteVersion"],
        "suiteFingerprint": report["suiteFingerprint"],
        "catalogSize": len(cases),
        "host": "Host platform not recorded in the exported report",
        "note": "This older targeted run is not the current full suite. Unrun cases are not passes or skips.",
        "categories": [{"name": name, "counts": counts} for name, counts in sorted(rows.items())],
    }


def class_inventory(jar, classes):
    if not classes.is_dir():
        raise ValueError("Build the framework before generating its class inventory")
    with zipfile.ZipFile(jar) as archive:
        reference = {name for name in archive.namelist() if name.startswith("android/")
                     and name.endswith(".class") and name != "android/R.class" and not name.startswith("android/R$")}
    ours = {path.relative_to(classes).as_posix() for path in (classes / "android").rglob("*.class")}
    if not ours:
        raise ValueError("Built Android framework class directory is empty")
    rows = {}
    for name in reference:
        group = "android." + name.split("/")[1] if name.count("/") > 1 else "android"
        row = rows.setdefault(group, {"package": group, "present": 0, "reference": 0})
        row["reference"] += 1
        row["present"] += int(name in ours)
    return {
        "apiLevel": 35, "androidVersion": "15", "referenceArchive": "platform-35_r02.zip",
        "referenceJarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "definition": "Matching android.* class names in the built Java framework versus the pinned Android API 35 SDK jar. android.R resource classes excluded. No method, signature, behavior or CTS compatibility is implied.",
        "present": len(reference & ours), "reference": len(reference),
        "packages": sorted(rows.values(), key=lambda row: row["package"]),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", type=Path, default=ROOT / ".deps/android-sdk/android.jar")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/framework/classes")
    parser.add_argument("--output", type=Path, default=ROOT / "landing/src/lib/android-inventory.json")
    parser.add_argument("--source-ref", default="HEAD", help="Source baseline; local changes remain explicitly marked")
    parser.add_argument("--probe-report", type=Path)
    parser.add_argument("--probe-source", type=Path)
    args = parser.parse_args()
    if bool(args.probe_report) != bool(args.probe_source):
        parser.error("--probe-report requires --probe-source, and vice versa")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9/_.-]{0,199}", args.source_ref):
        parser.error("Invalid source baseline reference")
    if not (ROOT / "tests/api-probe/catalog.json").is_file():
        parser.error("Regenerate in the runtime development checkout containing tests/api-probe")
    # Verify the API level instead of silently comparing against another SDK.
    pinned = ROOT / ".deps/android-sdk/platform-35_r02.zip"
    if not pinned.is_file() or hashlib.sha256(pinned.read_bytes()).hexdigest() != "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0":
        raise ValueError("Pinned Android API 35 reference archive is missing or changed")
    with zipfile.ZipFile(pinned) as archive:
        entries = [name for name in archive.namelist() if name.endswith("/android.jar")]
        if len(entries) != 1 or archive.read(entries[0]) != args.android_jar.read_bytes():
            raise ValueError("Android jar differs from the pinned API 35 reference")
    cases = catalog(ROOT / "tests/api-probe/catalog.json")
    groups = {}
    for case in cases:
        row = groups.setdefault(case["category"], {"name": case["category"], "automatic": 0, "manual": 0})
        row[case["mode"]] += 1
    previous = json.loads(args.output.read_bytes()) if args.output.exists() else {}
    result = {
        "schemaVersion": 1, "updated": datetime.datetime.now(datetime.timezone.utc).date().isoformat(),  # noqa: UP017 - Python 3.10
        "runtimeVersion": (ROOT / "VERSION").read_text().strip(),
        "sourceCommit": subprocess.check_output(["git", "rev-parse", "--verify", args.source_ref + "^{commit}"], cwd=ROOT, text=True).strip(),
        "scope": "Development snapshot; includes local framework changes. Android 8.1 / API 27 is the runtime's advertised level, not a certification.",
        "inventory": class_inventory(args.android_jar, args.classes),
        "probeCatalog": {"total": len(cases), "categories": sorted(groups.values(), key=lambda row: row["name"])},
        "probeSnapshot": probe_snapshot(args.probe_report, args.probe_source) if args.probe_report else previous.get("probeSnapshot"),
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Exported {result['inventory']['present']}/{result['inventory']['reference']} class names and {len(cases)} planned assertions; no whole-platform compatibility score")


if __name__ == "__main__":
    main()
