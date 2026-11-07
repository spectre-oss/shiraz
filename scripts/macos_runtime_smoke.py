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

--profile gles runs build/macos-apk/gles.apk (tests/macos-apk-gles): an ES 2
GLSurfaceView (shader triangle, GLES30 vertex array object) and an ES 1
GLSurfaceView (GL10, GLUtils texture), each on its own GL thread, beside a
coloured View and a TextView. It requires every stage marker, no
MACOS_GLES_FAIL, GL thread or EGL failure line, no UnsatisfiedLinkError, the
GLES libraries not reported unavailable, ConfigurationInfo.reqGlEsVersion at
least 3.0, GL_RENDERER of both contexts containing every --gl-renderer-term
(default ANGLE and Metal), an ES 3 GL_VERSION for the client-version-2 context
and the app's exact glReadPixels results (MACOS_GLES2/1_FRAME_OK). Frame
checks: the GLES2 rectangle holds only the clear and triangle colours, with
the triangle's area and flip-sensitive samples inside and outside it; the
GLES1 rectangle likewise for its textured quad; the coloured View is uniform;
the text region has ink of the measured "Shiraz GLES" shape (GLES_TEXT_SHAPES);
background samples between them. A black GL
rectangle is reported as a GL frame that never reached the presented frame.
Headless keeps the software SDL renderer: the app's EGL contexts do not use
SDL's. --windowed sets SDL_RENDER_DRIVER to --expect-renderer (default
opengles2 for this profile), SHIRAZ_RENDERER=gpu and SHIRAZ_CANVAS=gpu, and
requires that renderer accelerated, a SHIRAZ_GPU device containing every
--gl-renderer-term and SHIRAZ_CANVAS backend=skia-ganesh; it also applies the
frame checks to SHIRAZ_PRESENT_SNAPSHOT, the frame read back from the SDL
renderer after composition. If GLES is unavailable the run fails.

The frame checks read the snapshot that ActivityRunner writes from the
framework's CPU-composited Bitmap. With --windowed on the Metal renderer they
prove what Shiraz drew and that SDL selected Metal (SHIRAZ_PRESENTATION
renderer=metal); they do not read back what Metal presented on screen.

Any windowed run on SDL's opengles2 renderer (ANGLE Metal; every --profile)
sets SHIRAZ_RENDERER=gpu, SHIRAZ_CANVAS=gpu and SHIRAZ_PRESENT_SNAPSHOT, and
requires accelerated=1, an ANGLE Metal SHIRAZ_GPU device, SHIRAZ_CANVAS
backend=skia-ganesh with no fallback, the profile's frame checks on the
presented readback, and every SHIRAZ_EGL_SURFACE line (native/presentation.cpp,
macOS only) to report an EGL window surface the size of SDL's output and of
SHIRAZ_DISPLAY pixels. The readback alone cannot see a surface larger than
SDL's output: SDL then draws into one corner of the window and reads back
the same corner (build/macos/m4/hidpi).

--gles-lower es2 (gles profile) sets SHIRAZ_TEST_GLES_LOWER=es2, so the lower
GLSurfaceView is a second ES 2 view (280x160) instead of the ES 1 one: two ES 2
contexts on two GL threads at once. It then requires two MACOS_GLES2_FRAME_OK
lines (280x280 and 280x160), two SURFACE_CREATED lines, the MACOS_GLES_LOWER_VIEW
marker, and the GLES2 colours and probes in the lower rectangle; the GLES1
markers are not required. A GL thread that throws or hangs ends the run with
SHIRAZ_RUNTIME_FATAL (GLSurfaceView rethrows it on the main thread).
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


GLES_APK = ROOT / 'build/macos-apk/gles.apk'
GLES_MARKERS = ('MACOS_GLES_DEVICE_CONFIG', 'MACOS_GLES_CONTENT_SET', 'MACOS_GLES_VIEW_DRAWN',
                'MACOS_GLES2_RENDERER', 'MACOS_GLES2_VERSION', 'MACOS_GLES2_SURFACE_CREATED', 'MACOS_GLES2_FRAME_OK',
                'MACOS_GLES1_RENDERER', 'MACOS_GLES1_VERSION', 'MACOS_GLES1_SURFACE_CREATED', 'MACOS_GLES1_FRAME_OK',
                'SHIRAZ_RUNTIME_CLEANUP_COMPLETE')
# Substrings: Shiraz's GLSurfaceView logs GL thread failures instead of ending the
# process (android/opengl/GLSurfaceView.java guardedRun), so these must fail the run.
GLES_FORBIDDEN = ('java.lang.UnsatisfiedLinkError', 'MACOS_GLES_FAIL', 'GL thread failed', 'eglMakeCurrent failed',
                  'createWindowSurface failed', 'SHIRAZ_UNSUPPORTED egl-pending-angle')
GLES_LIBRARIES = ('libshiraz-gles1.so', 'libshiraz-gles2.so')
GLES_MIN_VERSION = 0x30000
GLES_RENDERER_TERMS = ('ANGLE', 'Metal')
GLES_WINDOW_RENDERER = 'opengles2'
# Frames until the snapshot: the GL threads start after the first layout, and a
# swapped GL frame reaches the composited frame only on a later traversal.
GLES_FRAMES = 180
# tests/macos-apk-gles/GlesActivity.java, in dp of the 360x640 frame.
GLES_BACKGROUND, GLES_TEXT, GLES_VIEW = 0x101820, 0xffffff, 0x7b1fa2
GLES2_CLEAR, GLES2_TRIANGLE = 0xc02040, 0x30d050
GLES1_CLEAR, GLES1_TEXTURE = 0x2050e0, 0xf0c020
GLES_VIEW_RECT = (16, 64, 344, 120)
GLES2_RECT = (40, 136, 320, 416)
GLES1_RECT = (40, 440, 320, 600)
# (name, left, top, right, bottom, text rgb, minimum differing pixels, minimum exact text-colour pixels) at density 160.
GLES_TEXT_REGION = ('text', 16, 8, 344, 60, GLES_TEXT, 150, 15)
# Ink shape of "Shiraz GLES" (NotoSans-Regular, 28 dp) as TEXT_SHAPES, measured
# on macOS arm64 2026-11-07 from the software-canvas (headless) and Skia GPU
# canvas (windowed, ANGLE Metal) frames, which gave identical profiles, and
# checked by eye (build/macos/m4/fix/f4/gles-text-crop.png). A solid box,
# .notdef boxes or a missing string fail the box size or profiles.
GLES_TEXT_SHAPES = {'text': (150, 21, (0.11, 0.076, 0.094, 0.057, 0.11, 0.066, 0.014, 0.09, 0.134, 0.057, 0.087, 0.106),
                             (0.14, 0.097, 0.207, 0.153, 0.166, 0.237))}
# Share of each GL rectangle covered by its shape: the triangle (-0.6,-0.6),
# (0.6,-0.6), (0,0.6) is 0.72 of the 4 NDC units; the quad [-0.75,0]x[0,0.75] 0.5625.
GLES2_SHAPE_SHARE, GLES1_SHAPE_SHARE, GLES_SHAPE_TOLERANCE = 0.72 / 4, 0.5625 / 4, 0.03


def ndc(rect, x, y):
    """dp position of normalized device coordinates (x, y) in a GL view rectangle."""
    left, top, right, bottom = rect
    return left + (x + 1) / 2 * (right - left), top + (1 - y) / 2 * (bottom - top)


def ndc_inverse(rect, x, y):
    """Normalized device coordinates of dp position (x, y) in a GL view rectangle."""
    left, top, right, bottom = rect
    return (x - left) / (right - left) * 2 - 1, 1 - (y - top) / (bottom - top) * 2


# (x, y, rgb, what) in dp; the ndc() probes are the ones GlesActivity reads back.
GLES_PIXELS = (
    (8, 8, GLES_BACKGROUND, 'background'), (352, 300, GLES_BACKGROUND, 'background'),
    (20, 300, GLES_BACKGROUND, 'background'), (180, 428, GLES_BACKGROUND, 'background'),
    (180, 620, GLES_BACKGROUND, 'background'), (352, 632, GLES_BACKGROUND, 'background'),
    (24, 72, GLES_VIEW, 'view'), (336, 112, GLES_VIEW, 'view'), (180, 92, GLES_VIEW, 'view'),
    *((*ndc(GLES2_RECT, x, y), GLES2_TRIANGLE if inside else GLES2_CLEAR, 'gles2 ' + label)
      for x, y, inside, label in ((0, 0, True, 'triangle centre'), (0.4, -0.45, True, 'triangle lower right'),
                                  (0.4, 0.45, False, 'clear beside apex (flip)'), (0, 0.8, False, 'clear above apex'),
                                  (0, -0.8, False, 'clear below base'), (-0.95, -0.95, False, 'clear corner'),
                                  (0.95, 0.95, False, 'clear corner'), (-0.95, 0.95, False, 'clear corner'),
                                  (0.95, -0.95, False, 'clear corner'))),
    *((*ndc(GLES1_RECT, x, y), GLES1_TEXTURE if inside else GLES1_CLEAR, 'gles1 ' + label)
      for x, y, inside, label in ((-0.375, 0.375, True, 'texture'), (-0.65, 0.1, True, 'texture near corner'),
                                  (-0.375, -0.375, False, 'clear below quad (flip)'),
                                  (0.375, 0.375, False, 'clear right of quad (mirror)'),
                                  (-0.95, -0.95, False, 'clear corner'), (0.95, 0.95, False, 'clear corner'))),
)


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


def ink_shape(width, data, x0, y0, x1, y1, scale, background=BACKGROUND):
    """(box width dp, box height dp, column shares, row shares) of the ink in a pixel rectangle, or None."""
    points = [(x, y) for y in range(y0, y1) for x in range(x0, x1)
              if channel_delta(pixel(width, data, x, y), background) > TEXT_INK_DELTA]
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


def text_shape_failures(name, shape, shapes=TEXT_SHAPES):
    expected = shapes[name]
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


def gles_frame_failures(width, height, data, dpi, label='snapshot', lower='es1'):
    """Frame checks for tests/macos-apk-gles at density DPI (160 is one pixel per dp).

    LOWER es2 (SHIRAZ_TEST_GLES_LOWER=es2): the lower rectangle is a second ES 2
    view drawing the same triangle, checked with the GLES2 probes and colours.
    """
    failures = []
    scale = dpi / 160

    def at(x, y):
        return min(width - 1, int(x * scale)), min(height - 1, int(y * scale))

    def inside(rect):
        (x0, y0), (x1, y1) = at(rect[0], rect[1]), at(rect[2], rect[3])
        return [(x, y) for y in range(y0, y1) for x in range(x0, x1)]
    probes = GLES_PIXELS if lower == 'es1' else tuple(
        probe for probe in GLES_PIXELS if not probe[3].startswith('gles1')) + tuple(
        (*ndc(GLES1_RECT, *ndc_inverse(GLES2_RECT, x, y)), expected, 'lower ' + what)
        for x, y, expected, what in GLES_PIXELS if what.startswith('gles2'))
    for x, y, expected, what in probes:
        px, py = at(x, y)
        actual = pixel(width, data, px, py)
        if actual != expected:
            hint = ' (black: the GL frame never reached the composited frame)' if actual == 0 and what[:4] == 'gles' else ''
            failures.append(f'{label} {what} pixel {x:g},{y:g}dp ({px},{py}) 0x{actual:06x} != 0x{expected:06x}{hint}')
    view = {pixel(width, data, x, y) for x, y in inside(GLES_VIEW_RECT)}
    if view != {GLES_VIEW}:
        failures.append(f'{label} coloured View is not uniformly 0x{GLES_VIEW:06x}: '
                        + ','.join(f'0x{value:06x}' for value in sorted(view)[:6]))
    views = (('gles2', GLES2_RECT, GLES2_CLEAR, GLES2_TRIANGLE, GLES2_SHAPE_SHARE),
             ('gles1', GLES1_RECT, GLES1_CLEAR, GLES1_TEXTURE, GLES1_SHAPE_SHARE) if lower == 'es1' else
             ('lower gles2', GLES1_RECT, GLES2_CLEAR, GLES2_TRIANGLE, GLES2_SHAPE_SHARE))
    for name, rect, clear, shape, share in views:
        points = inside(rect)
        values = [pixel(width, data, x, y) for x, y in points]
        others = sorted({value for value in values if value not in (clear, shape)})
        if others:
            failures.append(f'{label} {name} rectangle has {sum(value not in (clear, shape) for value in values)} pixels '
                            f'other than 0x{clear:06x}/0x{shape:06x}: ' + ','.join(f'0x{v:06x}' for v in others[:6]))
        covered = values.count(shape) / max(1, len(values))
        if abs(covered - share) > GLES_SHAPE_TOLERANCE:
            failures.append(f'{label} {name} shape covers {covered:.3f} of its rectangle, expected {share:.3f}')
    name, left, top, right, bottom, colour, minimum, exact = GLES_TEXT_REGION
    points = inside((left, top, right, bottom))
    differing = sum(pixel(width, data, x, y) != GLES_BACKGROUND for x, y in points)
    matching = sum(pixel(width, data, x, y) == colour for x, y in points)
    area = scale * scale
    if differing < minimum * area or matching < exact * area:
        failures.append(f'{label} text region has {differing} pixels differing from the background and {matching} of '
                        f'0x{colour:06x}; expected >= {int(minimum * area)} and >= {int(exact * area)}')
    (x0, y0), (x1, y1) = at(left, top), at(right, bottom)
    failures += [f'{label} {failure}' for failure in text_shape_failures(
        name, ink_shape(width, data, x0, y0, x1, y1, scale, GLES_BACKGROUND), GLES_TEXT_SHAPES)]
    return failures


def presented_failures(profile, width, height, data, dpi, lower='es1'):
    """The profile's frame checks on SHIRAZ_PRESENT_SNAPSHOT."""
    if profile == 'gles':
        return gles_frame_failures(width, height, data, dpi, 'presented', lower)
    if profile == 'graphics':
        return [f'presented {failure}' for failure in graphics_failures(width, height, data, dpi)]
    centre = pixel(width, data, width // 2, height // 2)
    return [] if centre == EXPECTED_RGB else [f'presented centre pixel 0x{centre:06x} != 0x{EXPECTED_RGB:06x}']


def marker_text(lines, marker):
    """Rest of the first line that starts with MARKER, or None."""
    for line in lines:
        if line.startswith(marker + ' '):
            return line[len(marker) + 1:]
    return None


def gpu_device(lines):
    """Device of the SHIRAZ_GPU line (it contains spaces, so native_fields cannot read it), or None."""
    for line in lines:
        if 'SHIRAZ_GPU device=' in line:
            device = line[line.index('SHIRAZ_GPU device=') + len('SHIRAZ_GPU device='):]
            return device.split(' vendor=')[0]
    return None


def gpu_presentation_failures(lines, terms=GLES_RENDERER_TERMS):
    """Checks for a windowed opengles2 run: ANGLE Metal device, Skia GPU canvas, no fallback."""
    failures = []
    device = gpu_device(lines)
    if device is None or not all(term in device for term in terms):
        failures.append(f'SHIRAZ_GPU device {device!r} does not contain ' + ' and '.join(terms))
    canvases = [fields.get('backend') for fields in native_fields(lines, 'SHIRAZ_CANVAS')]
    if canvases != ['skia-ganesh']:
        failures.append(f'expected SHIRAZ_CANVAS backend=skia-ganesh (GPU canvas), saw {canvases!r}')
    if any('SHIRAZ_CANVAS_FALLBACK' in line or 'SHIRAZ_RENDERER_FALLBACK' in line for line in lines):
        failures.append('GPU canvas or renderer fell back to software')
    return failures


def egl_surface_failures(lines, pixels=None):
    """SDL's output size must equal its EGL window surface (SHIRAZ_EGL_SURFACE, native/presentation.cpp).

    ANGLE sizes the surface from its CAMetalLayer; with a stale layer scale it
    was twice SDL's output on a 1x display and the frame filled the bottom-left
    quarter of the window, while SHIRAZ_PRESENT_SNAPSHOT (a readback of SDL's
    output rectangle) still matched (build/macos/m4/hidpi, build/macos/m4/fix/f1).
    """
    reports = native_fields(lines, 'SHIRAZ_EGL_SURFACE')
    if not reports:
        return ['no SHIRAZ_EGL_SURFACE line: the opengles2 window surface size was not reported']
    failures = []
    for report in reports:
        output, surface = size(report.get('output')), size(report.get('egl'))
        if output is None or surface is None or output != surface:
            failures.append(f'SDL output {report.get("output")} != EGL window surface {report.get("egl")} '
                            '(the frame does not fill the window)')
        elif pixels is not None and output != pixels:
            failures.append(f'SHIRAZ_EGL_SURFACE output {report.get("output")} != SHIRAZ_DISPLAY pixels {pixels[0]}x{pixels[1]}')
    return failures


def gles_log_failures(lines, dpi=None, windowed=False, terms=GLES_RENDERER_TERMS, lower='es1'):
    """Log checks for tests/macos-apk-gles; DPI is the presented density."""
    failures = []
    shown = marker_text(lines, 'MACOS_GLES_LOWER_VIEW')
    if shown is not None and shown != lower:
        failures.append(f'MACOS_GLES_LOWER_VIEW {shown} != {lower}')
    config = native_fields(lines, 'MACOS_GLES_DEVICE_CONFIG')
    try:
        version = int(config[0]['req_gl_es_version'], 16)
    except (IndexError, KeyError, ValueError):
        version = None
        failures.append(f'MACOS_GLES_DEVICE_CONFIG has no req_gl_es_version: {config!r}')
    if version is not None and version < GLES_MIN_VERSION:
        failures.append(f'ConfigurationInfo.reqGlEsVersion 0x{version:x} < 0x{GLES_MIN_VERSION:x} (GLES not advertised)')
    density = None
    content = native_fields(lines, 'MACOS_GLES_CONTENT_SET')
    try:
        density = float(content[0]['density'])
    except (IndexError, KeyError, ValueError):
        failures.append(f'MACOS_GLES_CONTENT_SET has no density: {content!r}')
    if density is not None and dpi and abs(density - dpi / 160) > 1e-3:
        failures.append(f'app density {density} != presented density {dpi}/160')
    scale = density if density else (dpi / 160 if dpi else None)
    for name in ('MACOS_GLES2_RENDERER', 'MACOS_GLES1_RENDERER') if lower == 'es1' else ('MACOS_GLES2_RENDERER',):
        renderer = marker_text(lines, name)
        if renderer is not None and not all(term in renderer for term in terms):
            failures.append(f'{name} {renderer!r} does not contain ' + ' and '.join(terms))
    version2, version1 = marker_text(lines, 'MACOS_GLES2_VERSION'), marker_text(lines, 'MACOS_GLES1_VERSION')
    if version2 is not None and not version2.startswith('OpenGL ES 3.'):
        failures.append(f'MACOS_GLES2_VERSION {version2!r} is not OpenGL ES 3')
    if version1 is not None and not version1.startswith(('OpenGL ES 1.', 'OpenGL ES-CM 1.')):
        failures.append(f'MACOS_GLES1_VERSION {version1!r} is not OpenGL ES 1')
    if lower == 'es2':
        # Two ES 2 views on two GL threads report in either order.
        frames = native_fields(lines, 'MACOS_GLES2_FRAME_OK')
        if scale:
            wanted = sorted((str(round((rect[2] - rect[0]) * scale)), str(round((rect[3] - rect[1]) * scale)))
                            for rect in (GLES2_RECT, GLES1_RECT))
            seen = sorted((fields.get('width'), fields.get('height')) for fields in frames)
            if seen != wanted:
                failures.append(f'MACOS_GLES2_FRAME_OK sizes {seen} != {wanted} (both ES 2 views must draw)')
        surfaces = [line for line in lines if line.startswith('MACOS_GLES2_SURFACE_CREATED')]
        if len(surfaces) != 2:
            failures.append(f'{len(surfaces)} MACOS_GLES2_SURFACE_CREATED lines, expected 2')
    checked = (('MACOS_GLES2_FRAME_OK', GLES2_RECT, {'clear': GLES2_CLEAR, 'triangle': GLES2_TRIANGLE}),
               ('MACOS_GLES1_FRAME_OK', GLES1_RECT, {'clear': GLES1_CLEAR, 'texture': GLES1_TEXTURE}),
               ('MACOS_GLES_VIEW_DRAWN', GLES_VIEW_RECT, {}))
    for name, rect, values in checked:
        found = native_fields(lines, name)
        if lower == 'es2' and name == 'MACOS_GLES2_FRAME_OK':
            found = [fields for fields in found if fields.get('height') == str(round((rect[3] - rect[1]) * scale))] \
                if scale else found
        for fields in found[:1]:
            if scale:
                wanted = (round((rect[2] - rect[0]) * scale), round((rect[3] - rect[1]) * scale))
                if (fields.get('width'), fields.get('height')) != tuple(map(str, wanted)):
                    failures.append(f'{name} size {fields.get("width")}x{fields.get("height")} != {wanted[0]}x{wanted[1]}')
            for key, expected in values.items():
                if fields.get(key) != f'0x{expected:x}':
                    failures.append(f'{name} {key}={fields.get(key)} != 0x{expected:x}')
    for fields in native_fields(lines, 'SHIRAZ_HOST_UNAVAILABLE'):
        missing = [name for name in fields.get('libraries', '').split(',') if name in GLES_LIBRARIES]
        if missing:
            failures.append('GLES libraries reported unavailable: ' + ','.join(missing))
    canvases = [fields.get('backend') for fields in native_fields(lines, 'SHIRAZ_CANVAS')]
    if windowed:
        failures += gpu_presentation_failures(lines, terms)
    elif canvases != ['software']:
        failures.append(f'expected SHIRAZ_CANVAS backend=software headless, saw {canvases!r}')
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--windowed', action='store_true', help='Cocoa window (Metal renderer unless --expect-renderer)')
    parser.add_argument('--profile', choices=('minimal', 'graphics', 'gles'), default='minimal',
                        help='fixture APK and expected checks (default minimal)')
    parser.add_argument('--snapshot', type=Path,
                        help='default build/macos/runtime-smoke/frame.ppm (graphics: runtime-graphics-smoke, '
                             'gles: runtime-gles-smoke)')
    parser.add_argument('--timeout', type=int, default=240)
    parser.add_argument('--frames', type=int, help=f'default 30 windowed, 3 headless, {GLES_FRAMES} for gles')
    parser.add_argument('--expect-renderer', help='windowed SDL renderer (default metal; opengles2 for gles)')
    parser.add_argument('--gles-lower', choices=('es1', 'es2'), default='es1',
                        help='gles: the lower GL view (SHIRAZ_TEST_GLES_LOWER); es2 makes it a second ES 2 '
                             'view, so two ES 2 contexts render on two GL threads at once')
    parser.add_argument('--gl-renderer-term', action='append', dest='gl_renderer_terms',
                        help='gles: substring required in GL_RENDERER and, windowed, SHIRAZ_GPU device '
                             '(repeatable; default ' + ', '.join(GLES_RENDERER_TERMS) + ')')
    args = parser.parse_args()
    graphics, gles = args.profile == 'graphics', args.profile == 'gles'
    if args.snapshot is None:
        args.snapshot = ROOT / ('build/macos/runtime-graphics-smoke' if graphics else
                                'build/macos/runtime-gles-smoke' if gles else 'build/macos/runtime-smoke') / 'frame.ppm'
    apk, markers, forbidden = ((GRAPHICS_APK, GRAPHICS_MARKERS, GRAPHICS_FORBIDDEN) if graphics else
                               (GLES_APK, GLES_MARKERS, GLES_FORBIDDEN) if gles else (APK, MARKERS, FORBIDDEN))
    terms = tuple(args.gl_renderer_terms or GLES_RENDERER_TERMS)
    if gles and args.gles_lower == 'es2':
        markers = tuple(marker for marker in markers if not marker.startswith('MACOS_GLES1_'))
    expected_renderer = args.expect_renderer or (GLES_WINDOW_RENDERER if gles else 'metal')
    # A window on SDL's opengles2 renderer (ANGLE Metal) gets the GPU checks for every profile.
    gpu = args.windowed and (gles or expected_renderer == GLES_WINDOW_RENDERER)
    frames = args.frames or (GLES_FRAMES if gles else 30 if args.windowed else 3)
    if sys.platform != 'darwin':
        raise SystemExit('macos_runtime_smoke.py runs on macOS only')
    snapshot = args.snapshot.resolve()
    if not snapshot.is_relative_to((ROOT / 'build').resolve()) or snapshot.suffix != '.ppm':
        raise SystemExit('--snapshot must be a .ppm under build/ (shiraz run writes startup frames only there)')
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    snapshot.unlink(missing_ok=True)
    present = snapshot.with_name(snapshot.stem + '-presented.ppm')
    present.unlink(missing_ok=True)
    inherited = ('SDL_VIDEODRIVER', 'SDL_RENDER_DRIVER') + (
        ('SHIRAZ_RENDERER', 'SHIRAZ_CANVAS', 'SHIRAZ_PRESENT_SNAPSHOT') if gles or gpu else ())
    env = {key: value for key, value in os.environ.items() if key not in inherited}
    env['PYTHONPATH'] = str(ROOT / 'src')
    env.pop('SHIRAZ_TEST_GLES_LOWER', None)
    if gles and args.gles_lower != 'es1':
        env['SHIRAZ_TEST_GLES_LOWER'] = args.gles_lower
    env['SDL_RENDER_DRIVER'] = expected_renderer if args.windowed else 'software'
    if not args.windowed:
        env['SDL_VIDEODRIVER'] = 'dummy'
    elif gpu:
        # Fail in the presenter, not silently in software, when GPU presentation or the GPU canvas is unavailable.
        env.update({'SHIRAZ_RENDERER': 'gpu', 'SHIRAZ_CANVAS': 'gpu', 'SHIRAZ_PRESENT_SNAPSHOT': str(present)})
    command = [sys.executable, '-m', 'shiraz', 'run', str(apk), '--frames', str(frames),
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
    if graphics or gles:
        failures += [f'missing marker {marker}' for marker in markers
                     if not any(line == marker or line.startswith(marker + ' ') for line in lines)]
    else:
        failures += [f'missing marker {marker}' for marker in markers if marker not in lines]
    failures += [f'unexpected marker {marker}' for marker in forbidden if any(marker in line for line in lines)]
    backend, renderer, density = ('cocoa', expected_renderer, None) if args.windowed else ('dummy', 'software', 160)
    windows = native_fields(lines, 'SHIRAZ_WINDOW')
    if [window.get('backend') for window in windows] != [backend]:
        failures.append(f'expected one SHIRAZ_WINDOW backend={backend}, saw {windows!r}')
    presentations = native_fields(lines, 'SHIRAZ_PRESENTATION')
    presentation = presentations[0] if presentations else None
    if [entry.get('renderer') for entry in presentations] != [renderer]:
        failures.append(f'expected one SHIRAZ_PRESENTATION renderer={renderer}, saw {presentations!r}')
    elif gpu and presentation.get('accelerated') != '1':
        failures.append(f'SHIRAZ_PRESENTATION renderer={renderer} is not accelerated: {presentation!r}')
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
        if graphics or gles:
            if dpi and dpi > 0 and (width, height) == (LOGICAL[0] * dpi // 160, LOGICAL[1] * dpi // 160):
                failures += (gles_frame_failures(width, height, data, dpi, lower=args.gles_lower) if gles else
                             graphics_failures(width, height, data, dpi))
            else:
                failures.append(f'{args.profile} frame checks need a {LOGICAL[0]}x{LOGICAL[1]} dp frame at a known density')
        elif centre != EXPECTED_RGB:
            failures.append(f'centre pixel 0x{centre:06x} != 0x{EXPECTED_RGB:06x}')
        print(f'MACOS_RUNTIME_SMOKE frame={width}x{height} centre=0x{centre:06x}')
    print(f'MACOS_RUNTIME_SMOKE rc={result.returncode} elapsed_s={elapsed:.2f} presentation={presentation!r} log={log.relative_to(ROOT)}')
    if graphics:
        failures += graphics_log_failures(lines, dpi)
    if gles:
        failures += gles_log_failures(lines, dpi, args.windowed, terms, args.gles_lower)
    elif gpu:
        failures += gpu_presentation_failures(lines, terms)
    if args.windowed and expected_renderer == GLES_WINDOW_RENDERER:
        failures += egl_surface_failures(lines, pixels)
    if gpu:
        # What SDL presented after composition, read back from the renderer.
        if not present.is_file():
            failures.append(f'no presented frame at {present} (SHIRAZ_PRESENT_SNAPSHOT)')
        else:
            width, height, data = read_ppm(present)
            if pixels is not None and (width, height) != pixels:
                failures.append(f'presented frame {width}x{height} != presented pixels {pixels[0]}x{pixels[1]}')
            elif dpi and dpi > 0:
                failures += presented_failures(args.profile, width, height, data, dpi, args.gles_lower)
    if failures:
        for failure in failures:
            print('MACOS_RUNTIME_SMOKE_FAIL ' + failure, file=sys.stderr)
        sys.stderr.write(output[-4000:])
        return 1
    print('MACOS_RUNTIME_SMOKE_OK' + (f' profile={args.profile}' if graphics or gles else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
