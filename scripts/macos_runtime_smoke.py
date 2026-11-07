#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run the macOS fixture APK through `shiraz run` and check its first frame.

Headless by default (SDL dummy video, software renderer); --windowed uses the
Cocoa driver and requires the Metal renderer. The snapshot is the Activity's
drawn frame written by ActivityRunner, so the centre pixel proves the View
draw path ran; nothing here writes pixels. Fails nonzero on a nonzero exit,
a missing marker, a missing or wrong SDL presentation (window backend,
renderer, logical size, density), a snapshot size other than the presented
pixel size, or a wrong centre pixel. The fixture also probes http and https
URL connections, which must fail with a catchable IOException on macOS.
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / 'build/macos-apk/minimal.apk'
EXPECTED_RGB = 0x123456
MARKERS = ('MACOS_FIXTURE_CONTENT_SET', 'MACOS_FIXTURE_URL_IOEXCEPTION http', 'MACOS_FIXTURE_URL_IOEXCEPTION https',
           'SHIRAZ_RUNTIME_CLEANUP_COMPLETE')
FORBIDDEN = ('MACOS_FIXTURE_URL_CONNECTION_OPENED',)
LOGICAL = (360, 640)  # passed as --width/--height; density 160 is one pixel per dp


def native_fields(lines, tag):
    """key=value fields of every native TAG line.

    ART's host liblog can leave a NUL or its own log text in front of the next
    native stderr line, so search for the tag instead of anchoring on it.
    """
    found = []
    for line in lines:
        if tag + ' ' in line:
            tokens = line[line.index(tag + ' ') + len(tag) + 1:].split()
            found.append(dict(token.split('=', 1) for token in tokens if '=' in token))
    return found


def size(value):
    """'WxH' as (W, H), or None."""
    try:
        width, height = value.split('x')
        return int(width), int(height)
    except (AttributeError, ValueError):
        return None


def read_ppm(path):
    """Return (width, height, pixels) of a binary 8-bit P6 PPM."""
    data = path.read_bytes()
    fields, pos = [], 0
    while len(fields) < 4:
        while pos < len(data) and data[pos:pos + 1].isspace():
            pos += 1
        if data[pos:pos + 1] == b'#':
            while pos < len(data) and data[pos:pos + 1] != b'\n':
                pos += 1
            continue
        start = pos
        while pos < len(data) and not data[pos:pos + 1].isspace():
            pos += 1
        if start == pos:
            raise SystemExit(f'{path}: truncated PPM header')
        fields.append(data[start:pos])
    pos += 1
    if fields[0] != b'P6' or fields[3] != b'255':
        raise SystemExit(f'{path}: not an 8-bit P6 PPM')
    width, height = int(fields[1]), int(fields[2])
    pixels = data[pos:]
    if len(pixels) != width * height * 3:
        raise SystemExit(f'{path}: PPM has {len(pixels)} pixel bytes, expected {width * height * 3}')
    return width, height, pixels


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--windowed', action='store_true', help='Cocoa window with the Metal renderer')
    parser.add_argument('--snapshot', type=Path, default=ROOT / 'build/macos/runtime-smoke/frame.ppm')
    parser.add_argument('--timeout', type=int, default=240)
    args = parser.parse_args()
    if sys.platform != 'darwin':
        raise SystemExit('macos_runtime_smoke.py runs on macOS only')
    snapshot = args.snapshot.resolve()
    if not snapshot.is_relative_to((ROOT / 'build').resolve()) or snapshot.suffix != '.ppm':
        raise SystemExit('--snapshot must be a .ppm under build/ (shiraz run writes startup frames only there)')
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    snapshot.unlink(missing_ok=True)
    env = {key: value for key, value in os.environ.items() if key not in ('SDL_VIDEODRIVER', 'SDL_RENDER_DRIVER')}
    env['PYTHONPATH'] = str(ROOT / 'src')
    env['SDL_RENDER_DRIVER'] = 'metal' if args.windowed else 'software'
    if not args.windowed:
        env['SDL_VIDEODRIVER'] = 'dummy'
    command = [sys.executable, '-m', 'shiraz', 'run', str(APK), '--frames', '30' if args.windowed else '3',
               '--width', str(LOGICAL[0]), '--height', str(LOGICAL[1]),
               '--snapshot', str(snapshot), '--timeout', str(args.timeout)]
    started = time.monotonic()
    result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, timeout=args.timeout + 30)
    elapsed = time.monotonic() - started
    output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    log = snapshot.with_suffix('.log')
    log.write_text(output)
    # Strip the NUL bytes liblog leaves at the start of the line after an ART log line.
    lines = output.replace('\0', '').splitlines()
    failures = []
    if result.returncode:
        failures.append(f'shiraz run exited {result.returncode}')
    failures += [f'missing marker {marker}' for marker in MARKERS if marker not in lines]
    failures += [f'unexpected marker {marker}' for marker in FORBIDDEN if any(marker in line for line in lines)]
    backend, renderer, density = ('cocoa', 'metal', None) if args.windowed else ('dummy', 'software', 160)
    windows = native_fields(lines, 'SHIRAZ_WINDOW')
    if [window.get('backend') for window in windows] != [backend]:
        failures.append(f'expected one SHIRAZ_WINDOW backend={backend}, saw {windows!r}')
    presentations = native_fields(lines, 'SHIRAZ_PRESENTATION')
    presentation = presentations[0] if presentations else None
    if [entry.get('renderer') for entry in presentations] != [renderer]:
        failures.append(f'expected one SHIRAZ_PRESENTATION renderer={renderer}, saw {presentations!r}')
    displays = native_fields(lines, 'SHIRAZ_DISPLAY')
    pixels = None
    if not displays:
        failures.append('no SHIRAZ_DISPLAY line')
    for display in displays:
        logical, shown = size(display.get('logical')), size(display.get('pixels'))
        try:
            dpi = int(display.get('density', ''))
        except ValueError:
            dpi = None
        if logical != LOGICAL:
            failures.append(f'SHIRAZ_DISPLAY logical={display.get("logical")} != {LOGICAL[0]}x{LOGICAL[1]}')
        if dpi is None or dpi <= 0 or (density is not None and dpi != density):
            failures.append(f'SHIRAZ_DISPLAY density={display.get("density")} unexpected'
                            + (f' (headless requires {density})' if density else ''))
        elif shown != (LOGICAL[0] * dpi // 160, LOGICAL[1] * dpi // 160):
            failures.append(f'SHIRAZ_DISPLAY pixels={display.get("pixels")} != logical size at density {dpi}')
        if pixels is not None and shown != pixels:
            failures.append(f'SHIRAZ_DISPLAY pixel size changed {pixels} -> {shown} without a resize')
        pixels = pixels or shown
    centre = None
    if not snapshot.is_file():
        failures.append(f'no snapshot at {snapshot}')
    else:
        width, height, data = read_ppm(snapshot)
        if pixels is not None and (width, height) != pixels:
            failures.append(f'snapshot {width}x{height} != presented pixels {pixels[0]}x{pixels[1]}')
        index = ((height // 2) * width + width // 2) * 3
        centre = int.from_bytes(data[index:index + 3], 'big')
        if centre != EXPECTED_RGB:
            failures.append(f'centre pixel 0x{centre:06x} != 0x{EXPECTED_RGB:06x}')
        print(f'MACOS_RUNTIME_SMOKE frame={width}x{height} centre=0x{centre:06x}')
    print(f'MACOS_RUNTIME_SMOKE rc={result.returncode} elapsed_s={elapsed:.2f} presentation={presentation!r} log={log.relative_to(ROOT)}')
    if failures:
        for failure in failures:
            print('MACOS_RUNTIME_SMOKE_FAIL ' + failure, file=sys.stderr)
        sys.stderr.write(output[-4000:])
        return 1
    print('MACOS_RUNTIME_SMOKE_OK')
    return 0


if __name__ == '__main__':
    sys.exit(main())
