#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify real bridge/sigchain callbacks after a single-thread owner ref drop."""

import argparse
import json
import os
import re
import resource
import subprocess
from pathlib import Path

from art_smoke import HOST, environment
from macos_guest_hardened_closure import (
    sign_closure,
    sign_staged_adhoc_closure,
    stage_closure,
    write_manifest,
)
from native_closure_inventory import validate_native_inventory

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/macos/m5/signal-lifetime"
MARKER = "M5_SIGNAL_LIFETIME_PASS claimed=1 declined=2 forwarded=2 owner_reference_dropped=1"
REFUSAL_MARKER = "M5_SIGNAL_OWNER_REFUSAL_PASS foreign_handler_preserved=1 callback_advertised=0"


def no_child_core():
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("both", "adhoc", "hardened"), default="both")
    parser.add_argument("--identity", default=os.environ.get("SHIRAZ_CODESIGN_IDENTITY"))
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    env = {key: value for key, value in environment(WORK).items()
           if not key.startswith(("DYLD_", "LD_", "LINKER_", "BIONIC_", "BERBERIS_"))}
    modes = ("adhoc", "hardened") if args.mode == "both" else (args.mode,)
    for mode in modes:
        evidence = {
            "scope": "single-thread real bridge/sigchain owner-reference drop; not ART unload quiescence",
            "mode": mode, "command": None, "returncode": None, "phase": "preparing",
            "execution_verified": False, "hardened_verified": False, "native_closure": None,
            "foreign_owner_refusal_verified": False, "exhausted_keys_first_signal_verified": False,
        }
        # Invalidate prior success before staging/signing too, not just before
        # execution. Every failure must leave evidence of this attempt.
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        (WORK / f"{mode}.log").write_text("")
        (WORK / f"{mode}.foreign-owner.log").write_text("")
        (WORK / f"{mode}.key-exhaustion.log").write_text("")
        closure = stage_closure(
            ROOT, HOST, WORK, env,
            main_executable=ROOT / "build/macos/m5/signal-lifetime-probe",
        )
        evidence.update(phase="signing", native_closure=str(Path(closure["tree"]) / "closure.json"))
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        entitlements = ROOT / "tests/darwin/guest/jit.entitlements"
        if mode == "hardened":
            if not args.identity:
                raise SystemExit("hardened signal lifetime gate requires a public signing identity")
            sign_closure(closure, args.identity, entitlements, env)
        else:
            sign_staged_adhoc_closure(closure, entitlements, env)
        chain = Path(closure["tree"]) / "lib64/libsigchain.so"
        command = [closure["main"], closure["bridge"], str(chain)]
        evidence.update(command=command, phase="running-reference-drop")
        # Invalidate any prior success before starting a process. A timeout or
        # launch failure must leave this run's false evidence, never stale PASS.
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        (WORK / f"{mode}.log").write_text("")
        result = subprocess.run(command, cwd=WORK, env=env, text=True,
                                capture_output=True, timeout=60, check=False)
        (WORK / f"{mode}.log").write_text(result.stdout + result.stderr)
        evidence["returncode"] = result.returncode
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        if result.returncode or MARKER not in result.stdout.splitlines():
            raise SystemExit(f"signal lifetime failed; inspect {WORK / (mode + '.log')}")
        inventory = validate_native_inventory(
            result.stdout, closure, [closure["main"], closure["bridge"], chain]
        )
        refusal_command = [*command, "--foreign-owner"]
        evidence.update(refusal_command=refusal_command, refusal_returncode=None, phase="checking-foreign-owner")
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        (WORK / f"{mode}.foreign-owner.log").write_text("")
        refusal = subprocess.run(refusal_command, cwd=WORK, env=env, text=True,
                                 capture_output=True, timeout=60, check=False)
        (WORK / f"{mode}.foreign-owner.log").write_text(refusal.stdout + refusal.stderr)
        evidence["refusal_returncode"] = refusal.returncode
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        if refusal.returncode or REFUSAL_MARKER not in refusal.stdout.splitlines():
            raise SystemExit("signal lifetime fixture did not preserve a foreign host signal owner")
        refusal_inventory = validate_native_inventory(
            refusal.stdout, closure, [closure["main"], closure["bridge"], chain]
        )
        exhaustion_command = [*command, "--exhaust-keys"]
        evidence.update(exhaustion_command=exhaustion_command, exhaustion_returncode=None,
                        phase="checking-first-signal-with-keys-exhausted")
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        (WORK / f"{mode}.key-exhaustion.log").write_text("")
        exhaustion = subprocess.run(exhaustion_command, cwd=WORK, env=env, text=True,
                                    capture_output=True, timeout=60, check=False,
                                    preexec_fn=no_child_core)
        (WORK / f"{mode}.key-exhaustion.log").write_text(exhaustion.stdout + exhaustion.stderr)
        evidence["exhaustion_returncode"] = exhaustion.returncode
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        key_marker = re.search(r"^M5_SIGNAL_KEY_EXHAUSTION_PASS keys=(\d+) first_signal_delivered=1$",
                               exhaustion.stdout, re.MULTILINE)
        if (exhaustion.returncode or key_marker is None or int(key_marker[1]) > 65536
                or MARKER not in exhaustion.stdout.splitlines()):
            raise SystemExit("sigchain first-signal delivery failed with remaining pthread keys exhausted")
        exhaustion_inventory = validate_native_inventory(
            exhaustion.stdout, closure, [closure["main"], closure["bridge"], chain]
        )
        closure.update(execution_verified=True, hardened_verified=mode == "hardened",
                       native_image_inventory=inventory)
        write_manifest(closure)
        evidence.update(execution_verified=True, hardened_verified=mode == "hardened", phase="verified",
                        native_image_inventory=inventory, foreign_owner_refusal_verified=True,
                        refusal_returncode=refusal.returncode, refusal_native_image_inventory=refusal_inventory,
                        exhausted_keys_first_signal_verified=True, exhaustion_returncode=exhaustion.returncode,
                        exhaustion_native_image_inventory=exhaustion_inventory,
                        exhausted_fixture_key_count=int(key_marker[1]))
        (WORK / f"{mode}.result.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print(f"M5_SIGNAL_LIFETIME_MODE_PASS mode={mode}")


if __name__ == "__main__":
    main()
