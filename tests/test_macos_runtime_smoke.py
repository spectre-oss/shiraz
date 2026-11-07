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


if __name__ == '__main__':
    unittest.main()
