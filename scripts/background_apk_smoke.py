#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise actual ActivityRunner handoff to a windowless service with a fixture APK."""

import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from shiraz.apk import inspect_apk  # noqa: E402


def main():
    work = ROOT / "build/background-apk-smoke"
    work.mkdir(parents=True, exist_ok=True)
    result_path = work / "result.json"
    result_path.unlink(missing_ok=True)
    apk = ROOT / "build/macos-apk/background.apk"
    manifest = inspect_apk(apk)["manifest"]
    worker = next(
        (
            service
            for service in manifest["services"]
            if service["name"] == "org.shiraz.backgroundfixture.BackgroundWorker"
        ),
        {},
    )
    required = {
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        "android.permission.FOREGROUND_SERVICE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.FOREGROUND_SERVICE_CAMERA",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    }
    if (
        manifest.get("target_sdk") != "34"
        or worker.get("foregroundServiceType") != 201
        or not required.issubset(manifest.get("permissions", []))
    ):
        raise SystemExit(
            "Rebuild background APK: expected API 34, location/data-sync/capture types and permissions"
        )
    apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    env = dict(os.environ)
    for key in list(env):
        if key.startswith("SHIRAZ_"):
            del env[key]
    env.update(PYTHONPATH=str(ROOT / "src"), SDL_VIDEODRIVER="dummy", SDL_RENDER_DRIVER="software")
    env.update(SHIRAZ_PACKAGE_UID="19999", SHIRAZ_SERVICE_CALLER_UID="19998")
    command = [
        sys.executable,
        "-m",
        "shiraz",
        "run",
        str(apk),
        "--timeout",
        "30",
        "--width",
        "360",
        "--height",
        "640",
    ]
    result = subprocess.run(
        command, cwd=ROOT, env=env, capture_output=True, timeout=60, check=False
    )
    output = (result.stdout + result.stderr).decode("utf-8", errors="replace")
    (work / "run.log").write_text(output)
    markers = [
        "BACKGROUND_APK_ACTIVITY_DESTROYED",
        "SHIRAZ_BACKGROUND_ENTERED",
        "BACKGROUND_APK_WORK_AFTER_ACTIVITY",
        "BACKGROUND_APK_SERVICE_DESTROYED",
        "BACKGROUND_APK_JOB_FINISHED",
        "BACKGROUND_APK_JOB_DESTROYED",
        "SHIRAZ_RUNTIME_CLEANUP_COMPLETE",
    ]
    positions = [output.find(marker) for marker in markers]
    if (
        result.returncode
        or any(position < 0 for position in positions)
        or positions != sorted(positions)
    ):
        raise SystemExit(
            f"Background APK handoff failed (rc={result.returncode}); inspect {work / 'run.log'}"
        )
    if "BACKGROUND_APK_CAPTURE_PROMOTION_DENIED" not in output:
        raise SystemExit("Missing APK capture promotion denial evidence")
    if f"BACKGROUND_APK_UID={os.getuid()}" not in output:
        raise SystemExit("Unregistered launch accepted inherited logical UID")
    if "SHIRAZ_RUNTIME_CLEANUP_COMPLETE" not in output:
        raise SystemExit("Runtime cleanup marker missing")
    finite = subprocess.run(
        command + ["--frames", "1"], cwd=ROOT, env=env, capture_output=True, timeout=60, check=False
    )
    finite_output = (finite.stdout + finite.stderr).decode("utf-8", errors="replace")
    (work / "finite-run.log").write_text(finite_output)
    if (
        finite.returncode
        or "SHIRAZ_BACKGROUND_ENTERED" in finite_output
        or "SHIRAZ_RUNTIME_CLEANUP_COMPLETE" not in finite_output
        or "BACKGROUND_APK_SERVICE_DESTROYED" not in finite_output
    ):
        raise SystemExit("Finite-frame run did not tear down; inspect finite-run.log")
    location_env = dict(env, SHIRAZ_BACKGROUND_LOCATION_TEST="1", SHIRAZ_TEST_LOCATION="1,2,5")
    location = subprocess.run(
        command, cwd=ROOT, env=location_env, capture_output=True, timeout=60, check=False
    )
    location_output = (location.stdout + location.stderr).decode("utf-8", errors="replace")
    (work / "location-run.log").write_text(location_output)
    location_markers = (
        markers[:3]
        + [
            "BACKGROUND_APK_LOCATION_AFTER_ACTIVITY",
            "BACKGROUND_APK_RETAINED_NOTIFICATION_REMOVED",
            "BACKGROUND_APK_LOCATION_DEMOTED",
            "BACKGROUND_APK_LOCATION_WRONG_TYPE_BLOCKED",
            "BACKGROUND_APK_LOCATION_RESUMED",
            "BACKGROUND_APK_NOTIFICATION_DETACHED",
            markers[3],
            "BACKGROUND_APK_DETACHED_NOTIFICATION_CLEANED",
        ]
        + markers[4:]
    )
    positions = [location_output.find(marker) for marker in location_markers]
    if (
        location.returncode
        or any(position < 0 for position in positions)
        or positions != sorted(positions)
    ):
        raise SystemExit("Windowless foreground location failed; inspect location-run.log")
    result_path.write_text(
        json.dumps(
            {
                "apk_sha256": apk_sha256,
                "target_sdk": 34,
                "declared_foreground_type": 201,
                "fixture_only": True,
                "headless": True,
                "finite_run_cleanup": True,
                "mock_location_handoff": True,
                "modern_typed_location_handoff": True,
                "capture_promotion_denied": True,
                "notification_lifetime_handoff": True,
                "markers": markers,
                "location_markers": location_markers,
            },
            indent=2,
        )
        + "\n"
    )
    print("BACKGROUND_APK_HANDOFF_OK")


if __name__ == "__main__":
    main()
