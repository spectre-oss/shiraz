#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Measure six settled case-selection redraws in the actual probe APK, headless."""
import argparse
import json
import os
import re
import statistics
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz import runtime
from shiraz.apk import inspect_apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-framework', type=Path)
    parser.add_argument('--baseline-native', type=Path)
    parser.add_argument('--label', default='current')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9-]{1,40}', args.label):
        parser.error('--label must be a short lowercase basename')
    apk = ROOT / 'build/android-api-probe/api-probe-unsigned.apk'
    report = inspect_apk(apk)
    if report['manifest']['package'] != 'org.shiraz.apiprobe':
        raise SystemExit('Benchmark only supports the probe package')
    try:
        state = json.loads((ROOT / 'data/apps/org.shiraz.apiprobe/files/api-probe-report.json').read_text())
    except (OSError, ValueError) as error:
        raise SystemExit('Run the probe once and prepare its controlled benchmark scene') from error
    if set(state['results']) != {'runtime.reflection'} or state['groupIndex'] != 1 or state['selectedIndex'] != 0:
        raise SystemExit('Prepare a controlled scene: run only runtime.reflection, select Runtime/first case, then close the app')
    work = ROOT / 'build/probe-benchmark' / args.label
    work.mkdir(parents=True, exist_ok=True)
    input_file, log = work / 'input.txt', work / 'runtime.log'
    input_file.write_text('')
    log.write_text('')
    for key in ('SHIRAZ_LIVE_SNAPSHOT', 'SHIRAZ_PROFILE_METHODS', 'SHIRAZ_INPUT_SCRIPT'):
        os.environ.pop(key, None)
    os.environ.update(SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software', SHIRAZ_ART_MODE='interpreter',
                      SHIRAZ_TRACE_FRAMES='1', SHIRAZ_LIVE_INPUT=str(input_file))
    launch = runtime.launch_command

    def command(system, **kwargs):
        cmd, env = launch(system, **kwargs)
        if args.baseline_framework:
            index = cmd.index('-cp') + 1
            cmd[index] = cmd[index].replace(str(ROOT / 'build/shiraz-framework.jar'), str(args.baseline_framework.resolve()))
        if args.baseline_native:
            for index, value in enumerate(cmd):
                if value.startswith('-Djava.library.path='):
                    cmd[index] = '-Djava.library.path=' + str(args.baseline_native.resolve()) + ':' + value.split('=', 1)[1]
        return cmd, env

    runtime.launch_command = command
    failures = []

    def drive():
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            with log.open('rb') as stream:
                stream.seek(max(0, log.stat().st_size - 65536))
                text = stream.read().decode(errors='replace')
            if 'SHIRAZ_DRAW_TIMING' in text:
                if 'SHIRAZ_DISPLAY logical=360x640 pixels=360x640 density=160' not in text:
                    failures.append('Unexpected geometry/density; no pointer actions sent')
                    break
                time.sleep(.5)
                for x in (260, 80, 260, 80, 260, 80):
                    with input_file.open('a') as output:
                        output.write(f'down {x} 245\nup {x} 245\n')
                    time.sleep(2)
                break
            time.sleep(.05)
        else:
            failures.append('No interactive frame before deadline')
        with input_file.open('a') as output:
            output.write('close\n')

    driver = threading.Thread(target=drive, daemon=True)
    # Capture this process and its child so readiness and timing use the same log.
    with log.open('w') as stream:
        sys.stdout.flush()
        sys.stderr.flush()
        saved = os.dup(1), os.dup(2)
        try:
            os.dup2(stream.fileno(), 1)
            os.dup2(stream.fileno(), 2)
            driver.start()
            runtime.run_activity(apk, report, width=360, height=640, frames=None,
                                 snapshot=work / 'final.ppm', timeout=60)
        finally:
            sys.stdout.flush()
            sys.stderr.flush()
            os.dup2(saved[0], 1)
            os.dup2(saved[1], 2)
            os.close(saved[0])
            os.close(saved[1])
    driver.join(timeout=1)
    if failures:
        raise SystemExit('; '.join(failures))
    frames = []
    for line in log.read_text(errors='replace').splitlines():
        if 'SHIRAZ_DRAW_TIMING ' in line:
            frames.append({key: int(value) for key, value in re.findall(r'(\w+_ms)=(\d+)', line)})
    layouts = [frame for frame in frames if frame['layout_ms'] > 0][1:]
    if len(layouts) != 6:
        raise SystemExit(f'Expected six settled layout changes, observed {len(layouts)}; inspect {log}')
    totals = [sum(frame.values()) for frame in layouts]
    result = {'label': args.label, 'apkSha256': report['apk_sha256'], 'scene': 'Runtime first case, one recorded reflection result',
              'geometry': [360, 640], 'presentation': 'SDL dummy/software', 'artMode': 'interpreter',
              'liveCapture': False, 'methodProfiling': False, 'samplesMs': totals, 'frames': layouts,
              'medianMs': statistics.median(totals), 'minimumMs': min(totals), 'maximumMs': max(totals),
              'metric': 'frame setup + layout + predraw + draw + post; not input-to-photon latency'}
    (work / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
