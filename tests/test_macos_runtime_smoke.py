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


def text_ink(name, mirror=False):
    """dp pixels whose column and row ink shares follow smoke.TEXT_SHAPES[NAME] (optionally mirrored)."""
    width, height, columns, rows = smoke.TEXT_SHAPES[name]
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
