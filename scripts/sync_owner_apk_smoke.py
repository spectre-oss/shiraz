#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify automatic sync worker teardown through an SDK-built fixture APK."""

import hashlib
import json
import os
import platform
import selectors
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from shiraz import runtime  # noqa: E402
from shiraz.apk import inspect_apk  # noqa: E402
from shiraz.sync_adapters import inspect_sync_adapters  # noqa: E402


def service_shutdown(apk, work, environ):
    """Start the real ServiceRunner and disconnect only after worker readiness."""
    with tempfile.TemporaryDirectory(dir=work) as folder:
        launch = Path(folder)
        for directory in ("data", "app_lib", "tmp", "home", "cache", "android-data/dalvik-cache"):
            (launch / directory).mkdir(parents=True)
        metadata = runtime.application_metadata(inspect_apk(apk)["manifest"], apk, launch / "data")
        metadata["nativeLibraryDir"] = str(launch / "app_lib")
        properties = launch / "application.properties"
        properties.write_text(runtime.properties_text(metadata))
        vm = runtime.HOST / "bin/dalvikvm64"
        if not vm.exists():
            vm = runtime.HOST / "bin/dalvikvm"
        parent, channel = socket.socketpair()
        try:
            command, env = runtime.launch_command(
                platform.system(),
                root=ROOT,
                host=runtime.HOST,
                environ=environ,
                vm=vm,
                classpath=f"{ROOT / 'build/shiraz-framework.jar'}:{apk}",
                framework_resources=ROOT / "build/framework-res/framework-res.apk",
                work=launch,
                data=launch / "data",
                app_lib=launch / "app_lib",
                metadata=properties,
                package="org.shiraz.syncownerfixture",
                app="android.app.Application",
                activity="org.shiraz.syncownerfixture.SyncService",
                width=360,
                height=640,
                frames=None,
                snapshot=None,
                log_level="INFO",
                execution_options=["-Xint", "-Xusejit:false"],
                device_profile=None,
                profile_values={},
                digitalis=None,
                version="test",
                service_process="org.shiraz.syncownerfixture",
                service_fd=channel.fileno(),
            )
            with (work / "service-stderr.log").open("wb") as errors:
                child = subprocess.Popen(
                    command,
                    env=env,
                    pass_fds=(channel.fileno(),),
                    stdout=subprocess.PIPE,
                    stderr=errors,
                )
                channel.close()
                output = b""
                try:
                    deadline = time.monotonic() + 20
                    with selectors.DefaultSelector() as selector:
                        selector.register(child.stdout, selectors.EVENT_READ)
                        while not all(
                            marker in output
                            for marker in (
                                b"SYNC_APK_WORKER_ENTERED",
                                b"SHIRAZ_SERVICE_CHANNEL_READY",
                            )
                        ):
                            remaining = deadline - time.monotonic()
                            if remaining <= 0 or not selector.select(remaining):
                                raise RuntimeError("Sync service readiness timed out")
                            chunk = os.read(child.stdout.fileno(), 65536)
                            if not chunk:
                                raise RuntimeError("Sync service exited before readiness")
                            output += chunk
                    parent.close()
                    tail, _ = child.communicate(timeout=15)
                    output += tail
                    markers = [
                        b"SYNC_APK_CURSOR_OBSERVED",
                        b"SYNC_APK_PROVIDER_OBSERVED",
                        b"SYNC_APK_WORKER_ENTERED",
                        b"SYNC_APK_SERVICE_DESTROYED",
                        b"SYNC_APK_WORKER_INTERRUPTED",
                        b"SHIRAZ_RUNTIME_CLEANUP_COMPLETE",
                    ]
                    positions = [output.find(marker) for marker in markers]
                    if (
                        child.returncode
                        or any(position < 0 for position in positions)
                        or positions != sorted(positions)
                        or b"SYNC_APK_UNEXPECTED_COMPLETION" in output
                    ):
                        raise RuntimeError("Sync service shutdown failed; inspect service logs")
                finally:
                    if child.poll() is None:
                        child.kill()
                        tail, _ = child.communicate()
                        output += tail
                    (work / "service-stdout.log").write_bytes(output)
        finally:
            parent.close()
            channel.close()


def main():
    work = ROOT / "build/sync-owner-apk-smoke"
    work.mkdir(parents=True, exist_ok=True)
    result_path = work / "result.json"
    result_path.unlink(missing_ok=True)
    apk = ROOT / "build/macos-apk/sync-owner.apk"
    descriptors = inspect_sync_adapters(apk, inspect_apk(apk)["manifest"])
    expected = dict(
        component="org.shiraz.syncownerfixture.SyncService",
        authority="org.shiraz.syncownerfixture.data",
        account_type="fixture.sync",
        user_visible=False,
        supports_uploading=True,
        always_syncable=False,
        allow_parallel_syncs=False,
        settings_activity=None,
    )
    if descriptors != [expected]:
        raise SystemExit("Sync adapter binary resource discovery mismatch")
    env = {key: value for key, value in os.environ.items() if not key.startswith("SHIRAZ_")}
    env.update(PYTHONPATH=str(ROOT / "src"), SDL_VIDEODRIVER="dummy", SDL_RENDER_DRIVER="software")
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
        "SYNC_APK_CURSOR_OBSERVED",
        "SYNC_APK_PROVIDER_OBSERVED",
        "SYNC_APK_WORKER_ENTERED",
        "SYNC_APK_ACTIVITY_DESTROYED",
        "SYNC_APK_WORKER_INTERRUPTED",
        "SHIRAZ_RUNTIME_CLEANUP_COMPLETE",
    ]
    positions = [output.find(marker) for marker in markers]
    if (
        result.returncode
        or any(position < 0 for position in positions)
        or positions != sorted(positions)
        or "SYNC_APK_UNEXPECTED_COMPLETION" in output
    ):
        raise SystemExit(
            f"Sync owner APK failed (rc={result.returncode}); inspect {work / 'run.log'}"
        )
    service_shutdown(apk, work, env)
    result_path.write_text(
        json.dumps(
            {
                "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "fixture_only": True,
                "headless": True,
                "automatic_owner_cleanup": True,
                "service_parent_disconnect_cleanup": True,
                "declared_sync_adapters": descriptors,
                "markers": markers,
            },
            indent=2,
        )
        + "\n"
    )
    print("SYNC_OWNER_APK_OK")


if __name__ == "__main__":
    main()
