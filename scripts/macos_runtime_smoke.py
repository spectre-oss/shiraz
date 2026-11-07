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

--profile graphics runs build/macos-apk/graphics.apk (tests/macos-apk-graphics)
instead: every stage marker, no UnsatisfiedLinkError, the text/path/image
libraries not reported unavailable, measured Latin, Persian and Hebrew widths
equal (in dp, within 1.5%) to the advances of the bundled Noto fonts, and frame
checks at the fixture's dp positions scaled by the reported density:
background, circle interior and outside-corner, rounded-rect stroke, stroke
interior and rounded corner, exact PNG and WebP quadrant colours, the JPEG
colour within a tolerance, the Bitmap.compress round-trip cells, a minimum
count of pixels that differ from the background (and of exact text-colour
pixels) in each text region, and each text's ink shape: its bounding box and
its column and row ink profiles against the profile of the bundled-font render.
The width and shape checks reject a host or fallback font, .notdef boxes, and
reversed glyph order (the column profile is not mirror-symmetric); joining is
covered only as far as it changes the width and the profile.

The frame checks read the snapshot that ActivityRunner writes from the
framework's CPU-composited Bitmap. With --windowed they prove what Shiraz drew
and that SDL selected the Metal renderer (SHIRAZ_PRESENTATION renderer=metal);
they do not read back what Metal presented on screen.
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import macos_graphics_images as images  # noqa: E402

APK = ROOT / 'build/macos-apk/minimal.apk'
EXPECTED_RGB = 0x123456
MARKERS = ('MACOS_FIXTURE_CONTENT_SET', 'MACOS_FIXTURE_URL_IOEXCEPTION http', 'MACOS_FIXTURE_URL_IOEXCEPTION https',
           'SHIRAZ_RUNTIME_CLEANUP_COMPLETE')
FORBIDDEN = ('MACOS_FIXTURE_URL_CONNECTION_OPENED',)
LOGICAL = (360, 640)  # passed as --width/--height; density 160 is one pixel per dp

GRAPHICS_APK = ROOT / 'build/macos-apk/graphics.apk'
# Prefixes: the stage lines carry key=value fields.
GRAPHICS_MARKERS = ('MACOS_GRAPHICS_ASSETS_READ', 'MACOS_GRAPHICS_TEXT_MEASURED', 'MACOS_GRAPHICS_IMAGES_DECODED',
                    'MACOS_GRAPHICS_ROUNDTRIP_OK', 'MACOS_GRAPHICS_CONTENT_SET', 'MACOS_GRAPHICS_DRAWN',
                    'SHIRAZ_RUNTIME_CLEANUP_COMPLETE')
GRAPHICS_FORBIDDEN = ('java.lang.UnsatisfiedLinkError',)
GRAPHICS_LIBRARIES = ('libshiraz-text.so', 'libshiraz-path.so', 'libshiraz-image.so')
# tests/macos-apk-graphics/GraphicsActivity.java, in dp of the 360x640 frame.
BACKGROUND = 0x102030
CIRCLE, STROKE = 0xe53935, 0x43a047
JPEG_TOLERANCE = 8
# (x, y, rgb, tolerance)
GRAPHICS_PIXELS = (
    (8, 8, BACKGROUND, 0), (352, 632, BACKGROUND, 0), (180, 600, BACKGROUND, 0),
    (96, 304, CIRCLE, 0), (96, 256, CIRCLE, 0), (44, 252, BACKGROUND, 0),
    (264, 248, STROKE, 0), (196, 304, STROKE, 0), (264, 304, BACKGROUND, 0), (197, 249, BACKGROUND, 0),
    *((left + 20 + 40 * (index % 2), 404 + 40 * (index // 2), colour & 0xffffff, 0)
      for left, colours in ((24, images.QUADRANTS_PNG), (140, images.QUADRANTS_WEBP))
      for index, colour in enumerate(colours)),
    (296, 424, images.JPEG_RGB, JPEG_TOLERANCE), (264, 392, images.JPEG_RGB, JPEG_TOLERANCE),
    # Bitmap.compress round trips drawn as 4x4 grids of 20 dp cells; cell 5 is transparent.
    *((left + 10 + 20 * (cell % 4), 498 + 20 * (cell // 4),
       BACKGROUND if cell == 5 else images.roundtrip_pixels()[cell] & 0xffffff, 0)
      for left in (24, 140) for cell in (0, 5, 6, 15)),
)
# (name, left, top, right, bottom, text rgb, minimum differing pixels, minimum exact text-colour pixels) at density 160.
TEXT_REGIONS = (
    ('latin', 16, 16, 344, 98, 0xffffff, 300, 40),
    ('persian', 16, 100, 344, 176, 0xffd54f, 150, 15),
    ('hebrew', 16, 176, 344, 228, 0x80deea, 150, 15),
)
# MACOS_GRAPHICS_TEXT_MEASURED key -> width in dp of the fixture string drawn
# with the bundled fonts that .local/etc/fonts/fonts.conf prefers:
# NotoSans-Regular "Shiraz" at 56 dp, NotoSansHebrew-Regular at 32 dp and
# NotoNaskhArabic-Regular at 36 dp (measured on macOS arm64, 2026-11-07). The
# host Arial gives 64.08 for the Hebrew string, and Noto Sans .notdef boxes
# give about 201 for "Shiraz".
TEXT_WIDTHS = {'width': 159.547, 'rtl_width': 71.266, 'persian_width': 73.641}
TEXT_WIDTH_TOLERANCE = 0.015
# Ink shape of each text region in the same render (checked by eye: Latin
# "Shiraz", Persian sheen-yeh-reh joined then alef and zain, right to left,
# Hebrew shin to final mem, right to left): ink is a pixel more than
# TEXT_INK_DELTA from the background; (bounding box width and height in dp,
# share of ink in TEXT_COLUMNS equal columns of the box, left to right, and in
# TEXT_ROWS equal rows, top to bottom).
TEXT_INK_DELTA = 48
TEXT_COLUMNS, TEXT_ROWS = 12, 6
TEXT_SHAPES = {
    'latin': (154, 44, (0.111, 0.103, 0.129, 0.032, 0.066, 0.102, 0.094, 0.032, 0.092, 0.093, 0.063, 0.082),
              (0.09, 0.115, 0.218, 0.197, 0.155, 0.224)),
    'persian': (71, 31, (0.041, 0.151, 0.102, 0.064, 0.052, 0.112, 0.06, 0.098, 0.05, 0.066, 0.118, 0.085),
                (0.054, 0.114, 0.131, 0.218, 0.278, 0.205)),
    'hebrew': (68, 25, (0.137, 0.062, 0.097, 0.066, 0.104, 0.043, 0.069, 0.093, 0.076, 0.083, 0.064, 0.106),
               (0.022, 0.249, 0.171, 0.18, 0.154, 0.223)),
}
# Largest accepted L1 distance between profiles, and box size difference (the
# larger of 3 dp and 6%). The host Arial render differs by 0.47 (Persian) and
# 0.18 (Hebrew) in columns; mirrored Hebrew differs by 0.19.
TEXT_PROFILE_TOLERANCE = 0.12
TEXT_BOX_TOLERANCE = (3, 0.06)


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


def pixel(width, data, x, y):
    index = (y * width + x) * 3
    return int.from_bytes(data[index:index + 3], 'big')


def channel_delta(left, right):
    return max(abs(((left >> shift) & 0xff) - ((right >> shift) & 0xff)) for shift in (0, 8, 16))


def ink_shape(width, data, x0, y0, x1, y1, scale):
    """(box width dp, box height dp, column shares, row shares) of the ink in a pixel rectangle, or None."""
    points = [(x, y) for y in range(y0, y1) for x in range(x0, x1)
              if channel_delta(pixel(width, data, x, y), BACKGROUND) > TEXT_INK_DELTA]
    if not points:
        return None
    left, right = min(x for x, _ in points), max(x for x, _ in points) + 1
    top, bottom = min(y for _, y in points), max(y for _, y in points) + 1
    columns, rows = [0] * TEXT_COLUMNS, [0] * TEXT_ROWS
    for x, y in points:
        columns[(x - left) * TEXT_COLUMNS // (right - left)] += 1
        rows[(y - top) * TEXT_ROWS // (bottom - top)] += 1
    return ((right - left) / scale, (bottom - top) / scale,
            [value / len(points) for value in columns], [value / len(points) for value in rows])


def profile_distance(left, right):
    return sum(abs(a - b) for a, b in zip(left, right))


def text_shape_failures(name, shape):
    expected = TEXT_SHAPES[name]
    if shape is None:
        return [f'text region {name} has no ink']
    failures = []
    for label, actual, wanted in (('width', shape[0], expected[0]), ('height', shape[1], expected[1])):
        if abs(actual - wanted) > max(TEXT_BOX_TOLERANCE[0], TEXT_BOX_TOLERANCE[1] * wanted):
            failures.append(f'text region {name} ink box {label} {actual:.1f} dp != {wanted} dp')
    for label, actual, wanted in (('column', shape[2], expected[2]), ('row', shape[3], expected[3])):
        distance = profile_distance(actual, wanted)
        if distance > TEXT_PROFILE_TOLERANCE:
            failures.append(f'text region {name} {label} ink profile differs from the bundled-font render by '
                            f'{distance:.3f} > {TEXT_PROFILE_TOLERANCE}: ' + ','.join(f'{v:.3f}' for v in actual))
    return failures


def graphics_failures(width, height, data, dpi):
    """Frame checks for tests/macos-apk-graphics at density DPI (160 is one pixel per dp)."""
    failures = []
    scale = dpi / 160

    def at(x, y):
        return min(width - 1, int(x * scale)), min(height - 1, int(y * scale))
    for x, y, expected, tolerance in GRAPHICS_PIXELS:
        px, py = at(x, y)
        actual = pixel(width, data, px, py)
        if channel_delta(actual, expected) > tolerance:
            failures.append(f'pixel {x},{y}dp ({px},{py}) 0x{actual:06x} != 0x{expected:06x}'
                            + (f' +-{tolerance}' if tolerance else ''))
    for name, left, top, right, bottom, colour, minimum, exact in TEXT_REGIONS:
        (x0, y0), (x1, y1) = at(left, top), at(right, bottom)
        differing = matching = 0
        for y in range(y0, y1):
            for x in range(x0, x1):
                value = pixel(width, data, x, y)
                differing += value != BACKGROUND
                matching += value == colour
        area = scale * scale
        if differing < minimum * area or matching < exact * area:
            failures.append(f'text region {name} has {differing} pixels differing from the background and {matching} of '
                            f'0x{colour:06x}; expected >= {int(minimum * area)} and >= {int(exact * area)}')
        failures += text_shape_failures(name, ink_shape(width, data, x0, y0, x1, y1, scale))
    return failures


def graphics_log_failures(lines, dpi=None):
    """Log checks; DPI is the presented density, which the app's density must match."""
    failures = []
    density = None
    assets = native_fields(lines, 'MACOS_GRAPHICS_ASSETS_READ')
    try:
        density = float(assets[0]['density'])
    except (IndexError, KeyError, ValueError):
        failures.append(f'MACOS_GRAPHICS_ASSETS_READ has no density: {assets!r}')
    if density is not None and dpi and abs(density - dpi / 160) > 1e-3:
        failures.append(f'app density {density} != presented density {dpi}/160')
    measured = native_fields(lines, 'MACOS_GRAPHICS_TEXT_MEASURED')
    for key, expected in TEXT_WIDTHS.items():
        try:
            value = float(measured[0][key])
        except (IndexError, KeyError, ValueError):
            failures.append(f'MACOS_GRAPHICS_TEXT_MEASURED has no {key}: {measured!r}')
            continue
        if density and density > 0 and abs(value / density - expected) > TEXT_WIDTH_TOLERANCE * expected:
            failures.append(f'MACOS_GRAPHICS_TEXT_MEASURED {key}={value} is {value / density:.3f} dp, not the bundled '
                            f'fonts\' {expected} dp (+-{TEXT_WIDTH_TOLERANCE:.1%})')
    for fields in native_fields(lines, 'SHIRAZ_HOST_UNAVAILABLE'):
        missing = [name for name in fields.get('libraries', '').split(',') if name in GRAPHICS_LIBRARIES]
        if missing:
            failures.append('graphics libraries reported unavailable: ' + ','.join(missing))
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--windowed', action='store_true', help='Cocoa window with the Metal renderer')
    parser.add_argument('--profile', choices=('minimal', 'graphics'), default='minimal',
                        help='fixture APK and expected checks (default minimal)')
    parser.add_argument('--snapshot', type=Path,
                        help='default build/macos/runtime-smoke/frame.ppm (graphics: runtime-graphics-smoke)')
    parser.add_argument('--timeout', type=int, default=240)
    args = parser.parse_args()
    graphics = args.profile == 'graphics'
    if args.snapshot is None:
        args.snapshot = ROOT / ('build/macos/runtime-graphics-smoke' if graphics else 'build/macos/runtime-smoke') / 'frame.ppm'
    apk, markers, forbidden = (GRAPHICS_APK, GRAPHICS_MARKERS, GRAPHICS_FORBIDDEN) if graphics else (APK, MARKERS, FORBIDDEN)
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
    command = [sys.executable, '-m', 'shiraz', 'run', str(apk), '--frames', '30' if args.windowed else '3',
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
    if graphics:
        failures += [f'missing marker {marker}' for marker in markers
                     if not any(line == marker or line.startswith(marker + ' ') for line in lines)]
    else:
        failures += [f'missing marker {marker}' for marker in markers if marker not in lines]
    failures += [f'unexpected marker {marker}' for marker in forbidden if any(marker in line for line in lines)]
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
    dpi = None
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
        if graphics:
            if dpi and dpi > 0 and (width, height) == (LOGICAL[0] * dpi // 160, LOGICAL[1] * dpi // 160):
                failures += graphics_failures(width, height, data, dpi)
            else:
                failures.append(f'graphics frame checks need a {LOGICAL[0]}x{LOGICAL[1]} dp frame at a known density')
        elif centre != EXPECTED_RGB:
            failures.append(f'centre pixel 0x{centre:06x} != 0x{EXPECTED_RGB:06x}')
        print(f'MACOS_RUNTIME_SMOKE frame={width}x{height} centre=0x{centre:06x}')
    print(f'MACOS_RUNTIME_SMOKE rc={result.returncode} elapsed_s={elapsed:.2f} presentation={presentation!r} log={log.relative_to(ROOT)}')
    if graphics:
        failures += graphics_log_failures(lines, dpi)
    if failures:
        for failure in failures:
            print('MACOS_RUNTIME_SMOKE_FAIL ' + failure, file=sys.stderr)
        sys.stderr.write(output[-4000:])
        return 1
    print('MACOS_RUNTIME_SMOKE_OK' + (' profile=graphics' if graphics else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
