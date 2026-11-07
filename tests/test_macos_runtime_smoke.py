# SPDX-License-Identifier: GPL-3.0-only
"""The macOS runtime smoke gate must reject wrong geometry and missing presentation."""
import contextlib
import importlib.util
import io
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('macos_runtime_smoke', ROOT / 'scripts/macos_runtime_smoke.py')
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)

MARKERS = b''.join(marker.encode() + b'\n' for marker in smoke.MARKERS)


def ppm(width, height, rgb=b'\x12\x34\x56'):
    return b'P6\n%d %d\n255\n' % (width, height) + rgb * (width * height)


def native(backend, renderer, pixels, density, prefix=b''):
    # ART's liblog leaves a NUL in front of the native line after an ART log line.
    return (b' E/dalvikvm( 1): log\n' + prefix + b'SHIRAZ_WINDOW backend=' + backend + b' initial=' + pixels + b'\n'
            + prefix + b'SHIRAZ_PRESENTATION renderer=' + renderer + b' accelerated=0\n'
            + b'SHIRAZ_DISPLAY logical=360x640 pixels=' + pixels + b' density=' + density + b'\n')


HEADLESS = native(b'dummy', b'software', b'360x640', b'160')


class MacosRuntimeSmokeTests(unittest.TestCase):
    def run_gate(self, image, stdout, stderr, *extra):
        build = ROOT / 'build'
        build.mkdir(exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='macos-smoke-test-', dir=build))
        self.addCleanup(shutil.rmtree, directory, True)
        snapshot = directory / 'frame.ppm'

        def run(command, **_):
            snapshot.write_bytes(image)
            return subprocess.CompletedProcess(command, 0, stdout, stderr)

        errors = io.StringIO()
        with patch.object(smoke.subprocess, 'run', run), patch.object(smoke.sys, 'platform', 'darwin'), \
                patch.object(sys, 'argv', ['smoke', '--snapshot', str(snapshot), *extra]), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
            result = smoke.main()
        return result, [line for line in errors.getvalue().splitlines() if line.startswith('MACOS_RUNTIME_SMOKE_FAIL')]

    def test_accepts_matching_frames(self):
        self.assertEqual(self.run_gate(ppm(360, 640), MARKERS, HEADLESS), (0, []))
        self.assertEqual(self.run_gate(ppm(360, 640), b'\0' + MARKERS,
                                       native(b'dummy', b'software', b'360x640', b'160', b'\0')), (0, []))
        self.assertEqual(self.run_gate(ppm(720, 1280), MARKERS, native(b'cocoa', b'metal', b'720x1280', b'320', b'\0'),
                                       '--windowed'), (0, []))

    def test_rejects_wrong_geometry_and_missing_presentation(self):
        cases = {
            'no presentation': (ppm(1, 1), MARKERS, b'', ()),
            'wrong frame size': (ppm(1, 1), MARKERS, HEADLESS, ()),
            'swapped axes': (ppm(640, 360), MARKERS, HEADLESS, ()),
            'headless density': (ppm(720, 1280), MARKERS, native(b'dummy', b'software', b'720x1280', b'320'), ()),
            'windowed on dummy': (ppm(360, 640), MARKERS, native(b'dummy', b'metal', b'360x640', b'160'), ('--windowed',)),
            'windowed software': (ppm(360, 640), MARKERS, native(b'cocoa', b'software', b'360x640', b'160'), ('--windowed',)),
            'url connection opened': (ppm(360, 640), MARKERS + b'MACOS_FIXTURE_URL_CONNECTION_OPENED http\n', HEADLESS, ()),
            'url markers missing': (ppm(360, 640), b'MACOS_FIXTURE_CONTENT_SET\nSHIRAZ_RUNTIME_CLEANUP_COMPLETE\n', HEADLESS, ()),
            'wrong centre pixel': (ppm(360, 640, b'\0\0\0'), MARKERS, HEADLESS, ()),
        }
        for name, (image, stdout, stderr, extra) in cases.items():
            with self.subTest(name):
                result, failures = self.run_gate(image, stdout, stderr, *extra)
                self.assertEqual(result, 1)
                self.assertTrue(failures)


def graphics_stdout(density=1, widths=None):
    """Fixture markers with the bundled fonts' widths (in px at DENSITY), optionally overridden in dp."""
    widths = {**smoke.TEXT_WIDTHS, **(widths or {})}
    measured = ' '.join(f'{key}={value * density}' for key, value in widths.items() if value is not None)
    return (b'SHIRAZ_HOST_UNAVAILABLE libraries=libshiraz-audio.so,libshiraz-sqlite.so reason=not-built-for-macos '
            b'first-use=UnsatisfiedLinkError\n'
            + f'MACOS_GRAPHICS_ASSETS_READ png=93 webp=60 jpeg=184 density={float(density)}\n'.encode()
            + f'MACOS_GRAPHICS_TEXT_MEASURED {measured}\n'.encode()
            + b'MACOS_GRAPHICS_IMAGES_DECODED png=8x8 webp=8x8 jpeg=16x16\n'
            b'MACOS_GRAPHICS_ROUNDTRIP_OK png=120 webp=64 jpeg=300 jpeg_delta=1\n'
            b'MACOS_GRAPHICS_CONTENT_SET\nMACOS_GRAPHICS_DRAWN width=360 height=640\n'
            b'SHIRAZ_RUNTIME_CLEANUP_COMPLETE\n')


GRAPHICS_STDOUT = graphics_stdout()
# Top-left dp corner of each text's ink box in the bundled-font render.
TEXT_ORIGINS = {'latin': (27, 33), 'persian': (24, 119), 'hebrew': (25, 187)}


def text_ink(name, mirror=False, shapes=None):
    """dp pixels whose column and row ink shares follow smoke.TEXT_SHAPES[NAME] (optionally mirrored)."""
    width, height, columns, rows = (shapes or smoke.TEXT_SHAPES)[name]
    columns = columns[::-1] if mirror else columns
    total = 4 * width * height // 10
    cells = {}
    for x in range(width):
        for y in range(height):
            cells.setdefault((x * smoke.TEXT_COLUMNS // width, y * smoke.TEXT_ROWS // height), []).append((x, y))
    ink = set()
    for (column, row), pixels in cells.items():
        # Corner pixels first in the outer cells, so the ink box spans width x height.
        pixels.sort(key=lambda point: (abs(point[0] - (0 if column == 0 else width - 1 if column == smoke.TEXT_COLUMNS - 1
                                                       else point[0])),
                                       abs(point[1] - (0 if row == 0 else height - 1 if row == smoke.TEXT_ROWS - 1
                                                       else point[1]))))
        ink.update(pixels[:max(1, min(len(pixels), round(columns[column] * rows[row] * total)))])
    return ink


def graphics_ppm(scale=1, change=None):
    """A synthetic frame with the graphics fixture's shapes at their dp positions.

    CHANGE(name) may return a replacement colour (or False to skip the shape).
    """
    width, height = 360 * scale, 640 * scale
    data = bytearray(smoke.BACKGROUND.to_bytes(3, 'big') * (width * height))

    def colour(name, default):
        value = change(name) if change else None
        return default if value is None else value

    def paint(name, rgb, left, top, right, bottom, inside=lambda x, y: True):
        rgb = colour(name, rgb)
        if rgb is False:
            return
        for py in range(int(top * scale), int(bottom * scale)):
            for px in range(int(left * scale), int(right * scale)):
                if inside((px + .5) / scale, (py + .5) / scale):
                    index = (py * width + px) * 3
                    data[index:index + 3] = rgb.to_bytes(3, 'big')

    def text(name, rgb):
        rgb = colour(name, rgb)
        if rgb is False:
            return
        mirror = rgb == 'mirror'
        rgb = default_text[name] if mirror else rgb
        left, top = TEXT_ORIGINS[name]
        for x, y in text_ink(name, mirror):
            paint(name + '-ink', rgb, left + x, top + y, left + x + 1, top + y + 1)
    default_text = {'latin': 0xffffff, 'persian': 0xffd54f, 'hebrew': 0x80deea}

    def band(x, y):
        # Distance from the rounded rect (196,248)-(332,360), radius 24; the 8 dp stroke is centred on it.
        cx, cy = min(max(x, 220), 308), min(max(y, 272), 336)
        return abs(((x - cx) ** 2 + (y - cy) ** 2) ** .5 - 24) <= 4 if (cx != x or cy != y) else min(
            x - 196, 332 - x, y - 248, 360 - y) <= 4
    for name, rgb in default_text.items():
        text(name, rgb)
    paint('circle', smoke.CIRCLE, 40, 248, 152, 360, lambda x, y: (x - 96) ** 2 + (y - 304) ** 2 <= 56 ** 2)
    paint('stroke', smoke.STROKE, 190, 242, 338, 366, band)
    for left, colours, name in ((24, smoke.images.QUADRANTS_PNG, 'png'), (140, smoke.images.QUADRANTS_WEBP, 'webp')):
        for index, value in enumerate(colours):
            x, y = left + 40 * (index % 2), 384 + 40 * (index // 2)
            paint(name, value & 0xffffff, x, y, x + 40, y + 40)
    paint('jpeg', smoke.images.JPEG_RGB + 0x010000, 256, 384, 336, 464)
    for left in (24, 140):
        for cell, value in enumerate(smoke.images.roundtrip_pixels()):
            if value >> 24:
                x, y = left + 20 * (cell % 4), 488 + 20 * (cell // 4)
                paint('roundtrip', value & 0xffffff, x, y, x + 20, y + 20)
    return b'P6\n%d %d\n255\n' % (width, height) + bytes(data)


class MacosRuntimeGraphicsSmokeTests(MacosRuntimeSmokeTests):
    def setUp(self):
        self.frame = graphics_ppm()

    def graphics_gate(self, image, stdout=GRAPHICS_STDOUT, stderr=HEADLESS, *extra):
        return self.run_gate(image, stdout, stderr, '--profile', 'graphics', *extra)

    def test_accepts_matching_frames(self):
        self.assertEqual(self.graphics_gate(self.frame), (0, []))
        self.assertEqual(self.graphics_gate(graphics_ppm(2), graphics_stdout(2),
                                            native(b'cocoa', b'metal', b'720x1280', b'320', b'\0'), '--windowed'), (0, []))

    def test_text_shapes_detect_mirrored_text(self):
        """Each golden column profile is far enough from its mirror image for the tolerance to catch reversal."""
        for name, (_, _, columns, _) in smoke.TEXT_SHAPES.items():
            with self.subTest(name):
                self.assertGreater(smoke.profile_distance(columns[::-1], columns), smoke.TEXT_PROFILE_TOLERANCE)

    def test_rejects_wrong_geometry_and_missing_presentation(self):
        self.assertEqual(self.graphics_gate(graphics_ppm(2), GRAPHICS_STDOUT, HEADLESS)[0], 1)
        self.assertEqual(self.graphics_gate(self.frame, GRAPHICS_STDOUT, b'')[0], 1)

    def test_rejects_log_failures(self):
        cases = {
            'unsatisfied link': GRAPHICS_STDOUT + b'java.lang.UnsatisfiedLinkError: no shiraz-text\n',
            'text library unavailable': GRAPHICS_STDOUT.replace(b'libshiraz-sqlite.so', b'libshiraz-text.so'),
            'path library unavailable': GRAPHICS_STDOUT.replace(b'libshiraz-sqlite.so', b'libshiraz-path.so'),
            'zero text width': graphics_stdout(widths={'width': 0}),
            'zero rtl width': graphics_stdout(widths={'rtl_width': 0}),
            'host Arial hebrew width': graphics_stdout(widths={'rtl_width': 64.078}),
            'notdef latin width': graphics_stdout(widths={'width': 201.6}),
            'no persian width': graphics_stdout(widths={'persian_width': None}),
            'widths at the wrong density': graphics_stdout(2).replace(b'density=2.0', b'density=1.0'),
            'minimal markers only': MARKERS,
        }
        for marker in smoke.GRAPHICS_MARKERS:
            cases['missing ' + marker] = b'\n'.join(line for line in GRAPHICS_STDOUT.split(b'\n')
                                                    if not line.startswith(marker.encode()))
        for name, stdout in cases.items():
            with self.subTest(name):
                result, failures = self.graphics_gate(self.frame, stdout)
                self.assertEqual(result, 1)
                self.assertTrue(failures)

    def test_rejects_wrong_pixels(self):
        cases = {
            'minimal frame': ppm(360, 640),
            'no circle': graphics_ppm(change=lambda name: False if name == 'circle' else None),
            'circle colour': graphics_ppm(change=lambda name: 0xe53936 if name == 'circle' else None),
            'no stroke': graphics_ppm(change=lambda name: False if name == 'stroke' else None),
            'png off by one': graphics_ppm(change=lambda name: 0x3366cd if name == 'png' else None),
            'webp off by one': graphics_ppm(change=lambda name: 0x20c011 if name == 'webp' else None),
            'jpeg out of tolerance': graphics_ppm(change=lambda name: smoke.images.JPEG_RGB + 9 if name == 'jpeg' else None),
            'no round trip': graphics_ppm(change=lambda name: False if name == 'roundtrip' else None),
            'no latin text': graphics_ppm(change=lambda name: False if name == 'latin' else None),
            'no persian text': graphics_ppm(change=lambda name: False if name == 'persian' else None),
            'hebrew wrong colour': graphics_ppm(change=lambda name: 0x80deeb if name == 'hebrew' else None),
            'hebrew reversed': graphics_ppm(change=lambda name: 'mirror' if name == 'hebrew' else None),
            'persian reversed': graphics_ppm(change=lambda name: 'mirror' if name == 'persian' else None),
            'latin reversed': graphics_ppm(change=lambda name: 'mirror' if name == 'latin' else None),
            'notdef boxes': tofu_ppm(),
        }
        filled = bytearray(self.frame)
        header = len(b'P6\n360 640\n255\n')
        index = header + (304 * 360 + 264) * 3
        filled[index:index + 3] = smoke.STROKE.to_bytes(3, 'big')
        cases['stroke interior filled'] = bytes(filled)
        for name, image in cases.items():
            with self.subTest(name):
                result, failures = self.graphics_gate(image)
                self.assertEqual(result, 1)
                self.assertTrue(failures)


def tofu_ppm():
    """The text regions replaced by hollow .notdef-style boxes in the text colours, as a missing-coverage font draws."""
    def boxes(name):
        return False if name in ('latin', 'persian', 'hebrew') else None
    header = len(b'P6\n360 640\n255\n')
    data = bytearray(graphics_ppm(change=boxes))
    for (left, top, count, width, height), rgb in (((24, 30, 6, 28, 40), 0xffffff), ((24, 110, 5, 18, 30), 0xffd54f),
                                                   ((24, 184, 4, 16, 26), 0x80deea)):
        for box in range(count):
            x0 = left + box * (width + 6)
            for y in range(top, top + height):
                for x in range(x0, x0 + width):
                    if min(x - x0, x0 + width - 1 - x, y - top, top + height - 1 - y) < 2:
                        index = header + (y * 360 + x) * 3
                        data[index:index + 3] = rgb.to_bytes(3, 'big')
    return bytes(data)


GLES_DEVICE = b'ANGLE (Apple, ANGLE Metal Renderer: Apple M2 Pro, Version 27.2 (Build 26B123))'


def gles_stdout(density=1, replace=None, drop=()):
    """GLES fixture markers at DENSITY; REPLACE maps a marker to its new line, DROP removes markers."""
    def size(rect):
        return f'width={round((rect[2] - rect[0]) * density)} height={round((rect[3] - rect[1]) * density)}'
    version = 'OpenGL ES 3.0 (ANGLE 2.1.1 git hash: 1f6526a566c2)'
    lines = {
        'MACOS_GLES_DEVICE_CONFIG': 'req_gl_es_version=0x30000 gl_es_version=3.0',
        'MACOS_GLES_CONTENT_SET': f'density={float(density)}',
        'MACOS_GLES_VIEW_DRAWN': size(smoke.GLES_VIEW_RECT),
        'MACOS_GLES2_VENDOR': 'Google Inc. (Apple)',
        'MACOS_GLES2_RENDERER': GLES_DEVICE.decode(),
        'MACOS_GLES2_VERSION': version,
        'MACOS_GLES2_SURFACE_CREATED': 'vao=1',
        'MACOS_GLES2_FRAME_OK': size(smoke.GLES2_RECT) + ' clear=0xc02040 triangle=0x30d050 vao=1',
        'MACOS_GLES1_RENDERER': GLES_DEVICE.decode(),
        'MACOS_GLES1_VERSION': 'OpenGL ES 1.1 (ANGLE 2.1.1 git hash: 1f6526a566c2)',
        'MACOS_GLES1_SURFACE_CREATED': 'texture=1',
        'MACOS_GLES1_FRAME_OK': size(smoke.GLES1_RECT) + ' clear=0x2050e0 texture=0xf0c020',
    }
    lines.update(replace or {})
    text = (b'SHIRAZ_HOST_UNAVAILABLE libraries=libshiraz-audio.so,libshiraz-sqlite.so reason=not-built-for-macos '
            b'first-use=UnsatisfiedLinkError\n')
    for name, value in lines.items():
        if name not in drop:
            text += f'{name} {value}\n'.encode()
    return text + (b'' if 'SHIRAZ_RUNTIME_CLEANUP_COMPLETE' in drop else b'SHIRAZ_RUNTIME_CLEANUP_COMPLETE\n')


def gles_native(windowed=False, renderer=b'opengles2', device=GLES_DEVICE, canvas=b'skia-ganesh gpu=1 antialias=coverage'):
    if not windowed:
        return HEADLESS + b'SHIRAZ_CANVAS backend=software gpu=0\n'
    return (b' E/dalvikvm( 1): log\n\0SHIRAZ_WINDOW backend=cocoa initial=720x1280\n'
            b'\0SHIRAZ_PRESENTATION renderer=' + renderer + b' accelerated=1 vsync=1 requested=gpu\n'
            b'SHIRAZ_GPU device=' + device + b' vendor=Google Inc. (Apple)\n'
            b'SHIRAZ_CANVAS backend=' + canvas + b'\n'
            b'SHIRAZ_DISPLAY logical=360x640 pixels=720x1280 density=320\n' + egl_surface())


def egl_surface(output=b'720x1280', surface=None):
    """native/presentation.cpp's macOS SHIRAZ_EGL_SURFACE line for an opengles2 window."""
    return (b'SHIRAZ_EGL_SURFACE output=' + output + b' drawable=' + output + b' egl=' + (surface or output)
            + b' match=' + (b'1' if surface in (None, output) else b'0') + b'\n')


def gles_ppm(scale=1, change=None):
    """A synthetic frame with the GLES fixture's views at their dp positions.

    CHANGE(name) may return a replacement value: False drops a shape, 'flip'
    mirrors a GL rectangle vertically, 'swizzle' swaps red and blue in it,
    'black' leaves it unpublished.
    """
    width, height = 360 * scale, 640 * scale
    data = bytearray(smoke.GLES_BACKGROUND.to_bytes(3, 'big') * (width * height))

    def option(name):
        return change(name) if change else None

    def put(px, py, rgb):
        index = (py * width + px) * 3
        data[index:index + 3] = rgb.to_bytes(3, 'big')

    def fill(rect, rgb, inside=lambda x, y: True):
        left, top, right, bottom = rect
        for py in range(int(top * scale), int(bottom * scale)):
            for px in range(int(left * scale), int(right * scale)):
                if inside((px + .5) / scale, (py + .5) / scale):
                    put(px, py, rgb)

    def gl_view(name, rect, clear, shape, inside_ndc):
        mode = option(name)
        left, top, right, bottom = rect
        if mode == 'black':
            fill(rect, 0)
            return

        def colour(rgb):
            return ((rgb & 0xff) << 16 | (rgb & 0xff00) | rgb >> 16) if mode == 'swizzle' else rgb
        fill(rect, colour(clear))
        if option(name + '-shape') is False:
            return

        def inside(x, y):
            nx, ny = (x - left) / (right - left) * 2 - 1, 1 - (y - top) / (bottom - top) * 2
            return inside_ndc(nx, -ny if mode == 'flip' else ny)
        fill(rect, colour(shape), inside)

    def triangle(x, y):
        return -0.6 <= y <= 0.6 and abs(x) <= 0.6 * (0.6 - y) / 1.2

    text = option('text')
    if text == 'box':
        fill((20, 20, 36, 36), smoke.GLES_TEXT)  # one 16 dp white square, no glyphs
    elif text is not False:
        for x, y in text_ink('text', text == 'mirror', smoke.GLES_TEXT_SHAPES):
            fill((20 + x, 24 + y, 21 + x, 25 + y), smoke.GLES_TEXT)
    if option('view') is not False:
        fill(smoke.GLES_VIEW_RECT, option('view') or smoke.GLES_VIEW)
    gl_view('gles2', smoke.GLES2_RECT, smoke.GLES2_CLEAR, option('triangle') or smoke.GLES2_TRIANGLE, triangle)
    gl_view('gles1', smoke.GLES1_RECT, smoke.GLES1_CLEAR, smoke.GLES1_TEXTURE,
            lambda x, y: -0.75 <= x <= 0 and 0 <= y <= 0.75)
    return b'P6\n%d %d\n255\n' % (width, height) + bytes(data)


class MacosRuntimeGlesSmokeTests(unittest.TestCase):
    def gate(self, image, stdout=None, stderr=None, *extra, present=None, windowed=False):
        """Run the gles profile; PRESENT is the frame written to SHIRAZ_PRESENT_SNAPSHOT (default IMAGE)."""
        build = ROOT / 'build'
        build.mkdir(exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='macos-gles-smoke-test-', dir=build))
        self.addCleanup(shutil.rmtree, directory, True)
        snapshot = directory / 'frame.ppm'
        stdout = gles_stdout(2 if windowed else 1) if stdout is None else stdout
        stderr = gles_native(windowed) if stderr is None else stderr
        seen = {}

        def run(command, env=None, **_):
            seen.update(command=command, env=env)
            snapshot.write_bytes(image)
            target = (env or {}).get('SHIRAZ_PRESENT_SNAPSHOT')
            if target and present is not False:
                Path(target).write_bytes(image if present is None else present)
            return subprocess.CompletedProcess(command, 0, stdout, stderr)

        errors = io.StringIO()
        arguments = ['smoke', '--profile', 'gles', '--snapshot', str(snapshot), *(['--windowed'] if windowed else []), *extra]
        with patch.object(smoke.subprocess, 'run', run), patch.object(smoke.sys, 'platform', 'darwin'), \
                patch.object(sys, 'argv', arguments), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
            result = smoke.main()
        self.seen = seen
        return result, [line for line in errors.getvalue().splitlines() if line.startswith('MACOS_RUNTIME_SMOKE_FAIL')]

    def assertRejected(self, result, why=None):
        code, failures = result
        self.assertEqual(code, 1)
        self.assertTrue(failures)
        if why:
            self.assertTrue(any(why in failure for failure in failures), failures)

    def test_accepts_matching_frames(self):
        self.assertEqual(self.gate(gles_ppm()), (0, []))
        env = self.seen['env']
        self.assertEqual((env['SDL_VIDEODRIVER'], env['SDL_RENDER_DRIVER']), ('dummy', 'software'))
        self.assertNotIn('SHIRAZ_RENDERER', env)
        self.assertIn(str(smoke.GLES_FRAMES), self.seen['command'])
        self.assertEqual(self.gate(gles_ppm(2), windowed=True), (0, []))
        env = self.seen['env']
        self.assertNotIn('SDL_VIDEODRIVER', env)
        self.assertEqual((env['SDL_RENDER_DRIVER'], env['SHIRAZ_RENDERER'], env['SHIRAZ_CANVAS']), ('opengles2', 'gpu', 'gpu'))
        self.assertTrue(env['SHIRAZ_PRESENT_SNAPSHOT'].endswith('frame-presented.ppm'))

    def test_frame_checks_scale_with_density(self):
        for scale in (1, 2):
            with self.subTest(scale=scale):
                image = gles_ppm(scale)
                header = len(b'P6\n%d %d\n255\n' % (360 * scale, 640 * scale))
                self.assertEqual(smoke.gles_frame_failures(360 * scale, 640 * scale, image[header:], 160 * scale), [])

    def test_rejects_frames_without_the_gl_content(self):
        cases = {
            'no triangle': (gles_ppm(change=lambda name: False if name == 'gles2-shape' else None), 'gles2'),
            'flipped triangle': (gles_ppm(change=lambda name: 'flip' if name == 'gles2' else None), 'flip'),
            'swizzled gles2': (gles_ppm(change=lambda name: 'swizzle' if name == 'gles2' else None), 'gles2'),
            'gles2 never published': (gles_ppm(change=lambda name: 'black' if name == 'gles2' else None), 'never reached'),
            'triangle colour off by one': (gles_ppm(change=lambda name: 0x30d051 if name == 'triangle' else None), 'gles2'),
            'no gles1 quad': (gles_ppm(change=lambda name: False if name == 'gles1-shape' else None), 'gles1'),
            'flipped gles1 quad': (gles_ppm(change=lambda name: 'flip' if name == 'gles1' else None), 'flip'),
            'gles1 never published': (gles_ppm(change=lambda name: 'black' if name == 'gles1' else None), 'never reached'),
            'no coloured view': (gles_ppm(change=lambda name: False if name == 'view' else None), 'View'),
            'view colour': (gles_ppm(change=lambda name: 0x7b1fa3 if name == 'view' else None), 'View'),
            'no text': (gles_ppm(change=lambda name: False if name == 'text' else None), 'text region'),
            # build/macos/m4/review-honesty/text/text-threshold.out: a white square passed the old count-only check.
            'white box instead of text': (gles_ppm(change=lambda name: 'box' if name == 'text' else None), 'ink box'),
            'mirrored text': (gles_ppm(change=lambda name: 'mirror' if name == 'text' else None), 'column ink profile'),
            'minimal frame': (ppm(360, 640), None),
        }
        for name, (image, why) in cases.items():
            with self.subTest(name):
                self.assertRejected(self.gate(image), why)

    def test_rejects_a_presented_frame_without_the_triangle(self):
        """The Shiraz snapshot is right but what SDL presented lost the GL content."""
        no_triangle = gles_ppm(2, change=lambda name: False if name == 'gles2-shape' else None)
        self.assertRejected(self.gate(gles_ppm(2), windowed=True, present=no_triangle), 'presented gles2')
        self.assertRejected(self.gate(gles_ppm(2), windowed=True, present=False), 'no presented frame')
        self.assertRejected(self.gate(gles_ppm(2), windowed=True, present=gles_ppm()), 'presented frame 360x640')

    def test_rejects_log_failures(self):
        unavailable = gles_stdout().replace(b'libshiraz-sqlite.so', b'libshiraz-gles2.so')
        cases = {
            'gles disabled': (gles_stdout(replace={'MACOS_GLES_DEVICE_CONFIG': 'req_gl_es_version=0x0 gl_es_version=0.0'}),
                              'reqGlEsVersion'),
            'gles 2 only': (gles_stdout(replace={'MACOS_GLES_DEVICE_CONFIG': 'req_gl_es_version=0x20000 gl_es_version=2.0'}),
                            'reqGlEsVersion'),
            'not ANGLE': (gles_stdout(replace={'MACOS_GLES2_RENDERER': 'Apple M2 Pro'}), 'MACOS_GLES2_RENDERER'),
            'gles1 software': (gles_stdout(replace={'MACOS_GLES1_RENDERER': 'ANGLE (Google, SwiftShader Device)'}),
                               'MACOS_GLES1_RENDERER'),
            'es2 context only': (gles_stdout(replace={'MACOS_GLES2_VERSION': 'OpenGL ES 2.0 (ANGLE)'}), 'not OpenGL ES 3'),
            'wrong readback': (gles_stdout(replace={'MACOS_GLES2_FRAME_OK': 'width=280 height=280 clear=0x4020c0 '
                                                    'triangle=0x30d050 vao=1'}), 'clear='),
            'wrong surface size': (gles_stdout(replace={'MACOS_GLES1_FRAME_OK': 'width=280 height=280 clear=0x2050e0 '
                                                        'texture=0xf0c020'}), 'MACOS_GLES1_FRAME_OK size'),
            'gl thread failed': (gles_stdout() + b'E/GLSurfaceView: GL thread failed\n', 'GL thread failed'),
            'app failure marker': (gles_stdout() + b'MACOS_GLES_FAIL stage=gles2-draw error=x\n', 'MACOS_GLES_FAIL'),
            'make current failed': (gles_stdout() + b'W/GLSurfaceView: eglMakeCurrent failed: 12298\n', 'eglMakeCurrent'),
            'unsatisfied link': (gles_stdout() + b'java.lang.UnsatisfiedLinkError: no shiraz-gles2\n', 'UnsatisfiedLinkError'),
            'gles library unavailable': (unavailable, 'GLES libraries'),
            'egl pending angle': (gles_stdout() + b'\0SHIRAZ_UNSUPPORTED egl-pending-angle Surface EGL contexts need '
                                  b'ANGLE-Metal\n', 'egl-pending-angle'),
            'wrong density': (gles_stdout(2), 'density'),
            'graphics markers only': (GRAPHICS_STDOUT, 'missing marker'),
        }
        for marker in smoke.GLES_MARKERS:
            cases['missing ' + marker] = (gles_stdout(drop=(marker,)), 'missing marker ' + marker)
        for name, (stdout, why) in cases.items():
            with self.subTest(name):
                self.assertRejected(self.gate(gles_ppm(), stdout), why)

    def test_rejects_wrong_presentation(self):
        frame, stdout = gles_ppm(2), gles_stdout(2)
        cases = {
            'metal renderer': gles_native(True, renderer=b'metal'),
            'software renderer': gles_native(True, renderer=b'software'),
            'not accelerated': gles_native(True).replace(b'accelerated=1', b'accelerated=0'),
            'native GL device': gles_native(True, device=b'Apple M2 Pro'),
            'software canvas': gles_native(True, canvas=b'software gpu=0'),
            'canvas fallback': gles_native(True) + b'SHIRAZ_CANVAS_FALLBACK reason=skia-context\n',
            'headless lines in a window run': gles_native(False),
        }
        for name, stderr in cases.items():
            with self.subTest(name):
                self.assertRejected(self.gate(frame, stdout, stderr, windowed=True))
        self.assertRejected(self.gate(gles_ppm(), gles_stdout(), HEADLESS), 'SHIRAZ_CANVAS')
        self.assertRejected(self.gate(gles_ppm(), gles_stdout(), gles_native(True)))
        # A different expected renderer is a parameter, not a code change.
        self.assertEqual(self.gate(frame, stdout, gles_native(True, renderer=b'metal'), '--expect-renderer', 'metal',
                                   windowed=True), (0, []))
        self.assertEqual(self.seen['env']['SDL_RENDER_DRIVER'], 'metal')
        self.assertRejected(self.gate(frame, stdout, gles_native(True), '--gl-renderer-term', 'Vulkan', windowed=True),
                            'Vulkan')


    def test_rejects_an_egl_surface_larger_than_the_sdl_output(self):
        """The stale-layer-scale defect: SDL draws 1x into a 2x ANGLE surface, so the window shows one quarter.

        The lines are the ones build/macos/m4/fix/f1/preview-{unpatched,patched}.out printed on the 1x display
        (shiraz-preview, opengles2, SDL 2.32.10 without and with patches/sdl-cocoa-gles-layer-scale.patch).
        """
        unpatched = b'SHIRAZ_EGL_SURFACE output=360x640 drawable=360x640 egl=720x1280 match=0\n'
        patched = b'SHIRAZ_EGL_SURFACE output=360x640 drawable=360x640 egl=360x640 match=1\n'
        self.assertTrue(smoke.egl_surface_failures(unpatched.decode().splitlines(), (360, 640)))
        self.assertEqual(smoke.egl_surface_failures(patched.decode().splitlines(), (360, 640)), [])
        frame, stdout = gles_ppm(2), gles_stdout(2)
        base = gles_native(True).replace(egl_surface(), b'')
        cases = {
            'surface twice the output': (base + egl_surface(b'720x1280', b'1440x2560'), 'EGL window surface'),
            'surface changed later': (base + egl_surface() + egl_surface(b'720x1280', b'1440x2560'), 'EGL window surface'),
            'no surface report': (base, 'SHIRAZ_EGL_SURFACE'),
            'output is not the display': (base + egl_surface(b'360x640'), 'SHIRAZ_DISPLAY pixels'),
        }
        for name, (stderr, why) in cases.items():
            with self.subTest(name):
                self.assertRejected(self.gate(frame, stdout, stderr, windowed=True), why)
        # The Metal renderer has no EGL surface and is not asked for one.
        self.assertEqual(self.gate(frame, stdout, gles_native(True, renderer=b'metal').replace(egl_surface(), b''),
                                   '--expect-renderer', 'metal', windowed=True), (0, []))


class MacosRuntimeGpuWindowSmokeTests(MacosRuntimeSmokeTests):
    """--expect-renderer opengles2 for the minimal and graphics profiles gets the GPU checks too."""

    def gpu_native(self, canvas=b'skia-ganesh gpu=1 antialias=coverage'):
        return (native(b'cocoa', b'opengles2', b'720x1280', b'320', b'\0').replace(b'accelerated=0', b'accelerated=1')
                + b'SHIRAZ_GPU device=' + GLES_DEVICE + b' vendor=Google Inc. (Apple)\n'
                + b'SHIRAZ_CANVAS backend=' + canvas + b'\n' + egl_surface())

    def gpu_gate(self, image, stdout, stderr, *extra, present=None):
        self.seen = {}

        def run(command, env=None, **_):
            self.seen.update(env=env)
            Path(command[command.index('--snapshot') + 1]).write_bytes(image)
            if env and env.get('SHIRAZ_PRESENT_SNAPSHOT') and present is not False:
                Path(env['SHIRAZ_PRESENT_SNAPSHOT']).write_bytes(image if present is None else present)
            return subprocess.CompletedProcess(command, 0, stdout, stderr)

        build = ROOT / 'build'
        build.mkdir(exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='macos-gpu-smoke-test-', dir=build))
        self.addCleanup(shutil.rmtree, directory, True)
        errors = io.StringIO()
        arguments = ['smoke', '--snapshot', str(directory / 'frame.ppm'), '--windowed', '--expect-renderer', 'opengles2', *extra]
        with patch.object(smoke.subprocess, 'run', run), patch.object(smoke.sys, 'platform', 'darwin'), \
                patch.object(sys, 'argv', arguments), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
            result = smoke.main()
        return result, [line for line in errors.getvalue().splitlines() if line.startswith('MACOS_RUNTIME_SMOKE_FAIL')]

    def test_accepts_and_requires_the_gpu_canvas(self):
        for profile, frame, stdout in (('minimal', ppm(720, 1280), MARKERS),
                                       ('graphics', graphics_ppm(2), graphics_stdout(2))):
            with self.subTest(profile):
                self.assertEqual(self.gpu_gate(frame, stdout, self.gpu_native(), '--profile', profile), (0, []))
                env = self.seen['env']
                self.assertEqual((env['SDL_RENDER_DRIVER'], env['SHIRAZ_RENDERER'], env['SHIRAZ_CANVAS']),
                                 ('opengles2', 'gpu', 'gpu'))
                # build/macos/m4/review-honesty/canvas: SHIRAZ_CANVAS=software passed the old gate.
                result, failures = self.gpu_gate(frame, stdout, self.gpu_native(b'software gpu=0'), '--profile', profile)
                self.assertEqual(result, 1)
                self.assertTrue(any('skia-ganesh' in failure for failure in failures), failures)
                result, failures = self.gpu_gate(frame, stdout, self.gpu_native().replace(b'accelerated=1', b'accelerated=0'),
                                                 '--profile', profile)
                self.assertTrue(any('not accelerated' in failure for failure in failures), failures)
                stderr = self.gpu_native().replace(egl_surface(), egl_surface(b'720x1280', b'1440x2560'))
                result, failures = self.gpu_gate(frame, stdout, stderr, '--profile', profile)
                self.assertTrue(any('EGL window surface' in failure for failure in failures), failures)
                result, failures = self.gpu_gate(frame, stdout, self.gpu_native(), '--profile', profile, present=False)
                self.assertTrue(any('no presented frame' in failure for failure in failures), failures)
                result, failures = self.gpu_gate(frame, stdout, self.gpu_native(), '--profile', profile,
                                                 present=ppm(720, 1280, b'\0\0\0'))
                self.assertTrue(any(failure.startswith('MACOS_RUNTIME_SMOKE_FAIL presented') for failure in failures),
                                failures)


class MacosGraphicsImageTests(unittest.TestCase):
    def test_encoders_are_deterministic_and_well_formed(self):
        first, second = smoke.images.images(), smoke.images.images()
        self.assertEqual(first, second)
        self.assertEqual(set(first), smoke.images.ASSETS)
        webp, jpeg = first['quadrants.webp'], first['flat.jpg']
        self.assertEqual((webp[:4], webp[8:16]), (b'RIFF', b'WEBPVP8L'))
        self.assertEqual(int.from_bytes(webp[4:8], 'little'), len(webp) - 8)
        self.assertEqual((jpeg[:3], jpeg[-2:]), (b'\xff\xd8\xff', b'\xff\xd9'))
        with self.assertRaises(ValueError):
            smoke.images.webp_lossless(3, 1, [0xff000000, 0xff010000, 0xff020000])  # three red values

    @unittest.skipUnless(sys.platform == 'darwin' and shutil.which('sips'), 'needs macOS ImageIO (sips)')
    def test_images_decode_with_an_independent_decoder(self):
        """macOS ImageIO decodes the hand-written WebP and JPEG to the documented colours."""
        (ROOT / 'build').mkdir(exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='macos-graphics-images-', dir=ROOT / 'build'))
        self.addCleanup(shutil.rmtree, directory, True)
        smoke.images.write(directory)
        expected = {'quadrants.png': smoke.images.QUADRANTS_PNG, 'quadrants.webp': smoke.images.QUADRANTS_WEBP}
        for name in sorted(smoke.images.ASSETS):
            with self.subTest(name):
                out = directory / (name + '.bmp')
                # sips cannot write PPM; an uncompressed BMP is simple to read back.
                subprocess.run(['sips', '-s', 'format', 'bmp', str(directory / name), '--out', str(out)],
                               check=True, capture_output=True, timeout=60)
                width, height, pixels = read_bmp(out.read_bytes())
                if name == 'flat.jpg':
                    self.assertEqual((width, height), (16, 16))
                    for value in set(pixels):
                        self.assertLessEqual(smoke.channel_delta(value, smoke.images.JPEG_RGB), smoke.JPEG_TOLERANCE)
                else:
                    self.assertEqual((width, height), (8, 8))
                    self.assertEqual([pixels[i] for i in (0, 7, 56, 63)], [c & 0xffffff for c in expected[name]])


def read_bmp(data):
    """(width, height, top-down 0xRRGGBB pixels) of an uncompressed 24/32-bit BMP."""
    offset, width, height, bits = (int.from_bytes(data[10:14], 'little'), int.from_bytes(data[18:22], 'little', signed=True),
                                   int.from_bytes(data[22:26], 'little', signed=True), int.from_bytes(data[28:30], 'little'))
    if bits not in (24, 32) or int.from_bytes(data[30:34], 'little') not in (0, 3):
        raise AssertionError(f'unsupported BMP: {bits} bits')
    step, stride = bits // 8, ((width * bits + 31) // 32) * 4
    rows = []
    for row in range(abs(height)):
        start = offset + row * stride
        rows.append([int.from_bytes(bytes(reversed(data[start + x * step:start + x * step + 3])), 'big') for x in range(width)])
    if height > 0:
        rows.reverse()
    return width, abs(height), [value for row in rows for value in row]


if __name__ == '__main__':
    unittest.main()
