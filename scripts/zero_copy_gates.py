#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""M4b zero-copy gates (build/macos/m4b/design/DESIGN-v2.md sections 7.4 and 9).

Runs one windowed `shiraz run` of a GLES fixture APK with SHIRAZ_TRACE_ZERO_COPY=1
in the given SHIRAZ_EGL_ZERO_COPY arm, then evaluates the named gates from the
SHIRAZ_ZC_* lines, the presenter sample file and its PPMs, printing PASS/FAIL per
gate. `--log DIR/run.log` re-evaluates an earlier run without running again.

  T1 (R)  every GL window mode=iosurface, zero-copy queues > 0, no present-path CPU
          reads (shim-readback, materialize, gpu-read), app-glreadpixels equals the
          fixture's own probe count, no SHIRAZ_RUNTIME_FATAL, rc 0.
  T2 (R)  gpu_draws > 0; no cpu_publishes and no consumer fallbacks after the first
          zero-copy queue line (SHIRAZ_ZC_EVENT order).
  T4 (R)  swap p50 <= 1.0 ms, p90 <= 3.0 ms, gl_blocked p99 <= --frame-ms. Under
          --expect-fail T4 the negative arm must also show swap p50 >= 5 ms.
  T3s     samples (needs --samples): (a) valid ids + orientation after the first
          frame, (b) ids never decrease, (c) id >= last queued v - 2, (d) distinct
          presented / produced >= 0.90, (e) queue->present latency p50 <= 20 ms and
          p90 <= 34 ms, (f) full PPMs upright.
  T3f     T3s (a)-(c) and (f); ratio and latency reported only.

--expect-fail G1,G2 inverts the named gates (negative control): the run passes
only if each named gate FAILS. The exit status is 0 only when every gate passes.
This script cannot prove GPU sync strength inside SDL/Skia (design section 9).
"""
import argparse
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = {
    # apk, probes the fixture itself reads back with glReadPixels
    'gles': (ROOT / 'build/macos-apk/gles.apk', 12),
    'gles-anim': (ROOT / 'build/macos-apk/gles-anim.apk', 0),
}
PRESENT_SITES = ('shim-readback', 'materialize', 'gpu-read')
FIELD = re.compile(r'(\S+?)=(\S+)')


def fields(line):
    return dict(FIELD.findall(line))


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(p / 100.0 * (len(ordered) - 1))))]


def read_ppm(path):
    data = path.read_bytes()
    parts = data.split(maxsplit=4)
    if parts[0] != b'P6':
        raise ValueError(f'{path}: not a P6 PPM')
    width, height = int(parts[1]), int(parts[2])
    return width, height, parts[4][-width * height * 3:]


def run(args, out):
    apk = FIXTURES[args.fixture][0]
    if not apk.is_file():
        raise SystemExit(f'{apk} missing: make macos-apk-{args.fixture}-fixture')
    env = {key: value for key, value in os.environ.items()
           if key not in ('SDL_VIDEODRIVER', 'SHIRAZ_PRESENT_SNAPSHOT', 'SHIRAZ_PRESENT_SAMPLES', 'SHIRAZ_TEST_GLES_LOWER')}
    env.update({'PYTHONPATH': str(ROOT / 'src'), 'SDL_RENDER_DRIVER': 'opengles2', 'SHIRAZ_RENDERER': 'gpu',
                'SHIRAZ_CANVAS': 'gpu', 'SHIRAZ_EGL_ZERO_COPY': args.arm, 'SHIRAZ_TRACE_ZERO_COPY': '1'})
    if args.anim_mode:
        env['SHIRAZ_TEST_ANIM_MODE'] = args.anim_mode
    if args.gles_lower:
        env['SHIRAZ_TEST_GLES_LOWER'] = args.gles_lower
    samples = out.resolve() / 'samples.txt'
    for old in [samples, *out.glob('samples.txt-present-*.ppm')]:
        old.unlink(missing_ok=True)
    if args.samples:
        env['SHIRAZ_PRESENT_SAMPLES'] = f'{samples},full_every={args.full_every}'
    command = [sys.executable, '-m', 'shiraz', 'run', str(apk), '--frames', str(args.frames),
               '--width', str(args.width), '--height', str(args.height), '--timeout', str(args.timeout)]
    result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, timeout=args.timeout + 60)
    text = (result.stdout + result.stderr).decode('utf-8', errors='replace').replace('\0', '')
    log = out / 'run.log'
    log.write_text(f'# command: {" ".join(command)}\n# arm: SHIRAZ_EGL_ZERO_COPY={args.arm} rc={result.returncode}\n' + text)
    return log


def evaluate(args, log):
    text = log.read_text(errors='replace')
    lines = text.splitlines()
    rc = None
    match = re.search(r'^# arm: .* rc=(-?\d+)', text, re.M)
    if match:
        rc = int(match.group(1))
    counters = {}
    for line in lines:
        if line.startswith('SHIRAZ_ZC_COUNTERS'):
            counters = fields(line)
    number = lambda key: float(counters.get(key, 0) or 0)  # noqa: E731
    modes = [fields(line).get('mode') for line in lines if line.startswith('SHIRAZ_EGL_ZERO_COPY window=')]
    queues = [fields(line) for line in lines if line.startswith('SHIRAZ_ZC_QUEUE ')]
    zc_queues = [q for q in queues if q.get('path') != 'readback']
    events = [(line.split()[1], fields(line).get('window'), int(fields(line)['t_ns']))
              for line in lines if line.startswith('SHIRAZ_ZC_EVENT ')]
    fatal = any('SHIRAZ_RUNTIME_FATAL' in line for line in lines)
    produced = sum(1 for line in lines if line.startswith('SHIRAZ_ZC_PRODUCED '))
    results = {}
    report = []

    def gate(name, ok, detail):
        results[name] = (ok, detail)

    reads = {site: number(f'read.{site}') for site in PRESENT_SITES}
    app_reads = number('observer.app-glreadpixels')
    expected_reads = FIXTURES[args.fixture][1]
    t1 = [f'modes={modes}', f'zc_queues={len(zc_queues)}', f'reads={reads}',
          f'app-glreadpixels={app_reads:g} (fixture {expected_reads})', f'fatal={fatal}', f'rc={rc}']
    gate('T1', bool(modes) and all(m == 'iosurface' for m in modes) and zc_queues and not any(reads.values())
         and app_reads == expected_reads and not fatal and rc == 0, ' '.join(t1))
    # Per window: events after that window's first zero-copy queue line.
    first_by_window = {}
    for q in zc_queues:
        first_by_window.setdefault(q.get('window'), int(q['t_ns']))
    first = min(first_by_window.values(), default=None)
    late = lambda kind: sum(1 for name, window, t in events  # noqa: E731
                            if name == kind and window in first_by_window and t > first_by_window[window])
    late_publishes, late_fallbacks = late('cpu_publish'), late('consumer_fallback')
    gate('T2', number('gpu_draws') > 0 and first is not None and late_publishes == 0 and late_fallbacks == 0,
         f'gpu_draws={number("gpu_draws"):g} cpu_publishes={number("cpu_publishes"):g} '
         f'after_first_queue: cpu_publishes={late_publishes} consumer_fallbacks={late_fallbacks} first_queue={first is not None}')
    p50, p90 = counters.get('swap_ms_p50'), counters.get('swap_ms_p90')
    blocked = counters.get('gl_blocked_ms_p99')
    p50f = float(p50) if p50 else None
    t4_ok = (p50 is not None and p90 is not None and blocked is not None and float(p50) <= 1.0 and float(p90) <= 3.0
             and float(blocked) <= args.frame_ms and modes and all(m == 'iosurface' for m in modes))
    gate('T4', t4_ok, f'swap_ms p50={p50} p90={p90} p99={counters.get("swap_ms_p99")} n={counters.get("swap_ms_n")} '
         f'gl_blocked_ms p99={blocked} (limit {args.frame_ms}) modes={modes}')
    report.append(f'timing swap_ms p50={p50} p90={p90} gl_blocked_ms p99={blocked} produced={produced} '
                  f'queues={len(queues)} presents_sampled=?')

    samples_path = log.parent / 'samples.txt'
    if 'T3s' in args.gates or 'T3f' in args.gates:
        samples = []
        if samples_path.is_file():
            for line in samples_path.read_text().splitlines():
                parts = line.split()
                if len(parts) == 5:
                    samples.append((int(parts[0]), int(parts[1]), int(parts[2], 16), parts[3], parts[4]))
        decoded, bad, started = [], [], False
        for seq, t, centre, tl, br in samples:
            r, g, b = centre >> 16, (centre >> 8) & 0xff, centre & 0xff
            ident = r | g << 8
            valid = ident > 0 and b == (0x40 | (bin(ident).count('1') & 0x3f)) and tl == 'ffffff' and br == 'ff00ff'
            started = started or valid
            if started and not valid:
                bad.append((seq, f'{centre:06x}', tl, br))
            if valid:
                decoded.append((seq, t, ident))
        decreases = [(a, b) for a, b in zip(decoded, decoded[1:]) if b[2] < a[2]]
        window_queues = sorted(((int(q['t_ns']), int(q['v'])) for q in queues), key=lambda item: item[0])
        lag_bad = []
        for seq, t, ident in decoded:
            last = None
            for qt, v in window_queues:
                if qt <= t:
                    last = v
                else:
                    break
            if last is not None and ident < last - 2:
                lag_bad.append((seq, ident, last))
        distinct = len({ident for _, _, ident in decoded})
        span = [v for qt, v in window_queues if decoded and decoded[0][1] - 50_000_000 <= qt <= decoded[-1][1]]
        ratio = distinct / len(span) if span else 0.0
        first_shown = {}
        for _, t, ident in decoded:
            first_shown.setdefault(ident, t)
        queued_at = {v: qt for qt, v in window_queues}
        latencies = [(first_shown[i] - queued_at[i]) / 1e6 for i in first_shown if i in queued_at]
        l50, l90 = percentile(latencies, 50), percentile(latencies, 90)
        ppm_bad = []
        ppms = sorted(log.parent.glob('samples.txt-present-*.ppm'))
        for ppm in ppms:
            width, height, data = read_ppm(ppm)
            pixel = lambda x, y: data[(y * width + x) * 3:(y * width + x) * 3 + 3].hex()  # noqa: E731
            upright = pixel(32, 32) == 'ffffff' and pixel(width - 16, height - 48) == 'ff00ff' and pixel(width // 2, 0) == '00ffff'
            if not upright:
                ppm_bad.append(ppm.name)
        common = (bool(decoded) and not bad and not decreases and not lag_bad and bool(ppms) and not ppm_bad)
        detail = (f'samples={len(samples)} valid={len(decoded)} invalid_after_start={bad[:3]} decreases={decreases[:3]} '
                  f'lag_violations={lag_bad[:3]} distinct={distinct} produced_in_span={len(span)} ratio={ratio:.3f} '
                  f'latency_ms p50={l50 if l50 is None else round(l50, 2)} p90={l90 if l90 is None else round(l90, 2)} '
                  f'n={len(latencies)} ppms={len(ppms)} ppm_not_upright={ppm_bad[:3]}')
        gate('T3s', common and ratio >= 0.90 and l50 is not None and l50 <= 20 and l90 <= 34, detail)
        gate('T3f', common, detail + ' (ratio/latency reported only)')

    expect_fail = set(filter(None, args.expect_fail.split(',')))
    overall = True
    for name in args.gates:
        if name not in results:
            print(f'FAIL {name} unknown gate')
            overall = False
            continue
        ok, detail = results[name]
        if name in expect_fail:
            negative = not ok
            if name == 'T4':
                negative = negative and p50f is not None and p50f >= 5.0
                detail += f' (negative control requires swap p50 >= 5 ms: {p50f})'
            print(f'{"PASS" if negative else "FAIL"} {name} expect-fail gate {"failed as required" if negative else "did not fail"}: {detail}')
            overall &= negative
        else:
            print(f'{"PASS" if ok else "FAIL"} {name}: {detail}')
            overall &= bool(ok)
    for line in report:
        print(f'INFO {line}')
    print(f'ZERO_COPY_GATES {"OK" if overall else "FAILED"} arm={args.arm} fixture={args.fixture} log={log}')
    return 0 if overall else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--fixture', choices=sorted(FIXTURES), default='gles')
    parser.add_argument('--arm', choices=('require', '1', '0'), default='require', help='SHIRAZ_EGL_ZERO_COPY')
    parser.add_argument('--gates', default='T1,T2')
    parser.add_argument('--expect-fail', default='')
    parser.add_argument('--out', type=Path, help='output directory (run.log, samples)')
    parser.add_argument('--log', type=Path, help='evaluate this run.log instead of running')
    parser.add_argument('--frames', type=int, default=600)
    parser.add_argument('--width', type=int, default=360)
    parser.add_argument('--height', type=int, default=640)
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--samples', action='store_true', help='SHIRAZ_PRESENT_SAMPLES (not for counter/timing runs)')
    parser.add_argument('--full-every', type=int, default=100)
    parser.add_argument('--anim-mode', choices=('paced', 'free'))
    parser.add_argument('--gles-lower', choices=('es2',))
    parser.add_argument('--frame-ms', type=float, default=16.7, help='T4 gl_blocked p99 limit (one consumer frame)')
    args = parser.parse_args()
    args.gates = [gate for gate in args.gates.split(',') if gate]
    if args.frames > 600:
        raise SystemExit('shiraz run --frames is at most 600')
    if args.log:
        return evaluate(args, args.log)
    if not args.out:
        raise SystemExit('--out or --log is required')
    args.out.mkdir(parents=True, exist_ok=True)
    return evaluate(args, run(args, args.out))


if __name__ == '__main__':
    sys.exit(main())
