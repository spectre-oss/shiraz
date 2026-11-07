# SPDX-License-Identifier: GPL-3.0-only
"""scripts/build_apk_fixture.fetch: per-candidate fallback and cache recovery."""
import hashlib
import http.server
from pathlib import Path
import socket
import sys
import tempfile
import threading
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
try:
    import build_apk_fixture
finally:
    sys.path.remove(str(SCRIPTS))

BODY = b'x' * 5000
SHA256 = hashlib.sha256(BODY).hexdigest()


class Handler(http.server.BaseHTTPRequestHandler):
    requests = []

    def log_message(self, *args):
        pass

    def do_GET(self):
        Handler.requests.append(self.path)
        mode = self.path.split('/')[2]
        if self.path.startswith('/mirror/') or mode == 'good':
            self.send_response(200); self.send_header('Content-Length', str(len(BODY))); self.end_headers()
            self.wfile.write(BODY)
        elif mode == 'truncated':
            self.send_response(200); self.send_header('Content-Length', str(len(BODY))); self.end_headers()
            self.wfile.write(BODY[:100]); self.wfile.flush(); self.connection.shutdown(socket.SHUT_RDWR)
        elif mode == 'wrong':
            self.send_response(200); self.send_header('Content-Length', '5'); self.end_headers()
            self.wfile.write(b'hello')
        elif mode == 'oversized':
            self.send_response(200); self.end_headers(); self.wfile.write(b'y' * 20000)
        else:
            self.send_error(404)


class FetchTests(unittest.TestCase):
    def setUp(self):
        self.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        base = f'http://127.0.0.1:{self.server.server_port}/'
        self.primary = base + 'p/'
        for name, value in (('GOOGLE_PRIMARY', self.primary), ('GOOGLE_MIRROR', base + 'mirror/')):
            self.addCleanup(setattr, build_apk_fixture, name, getattr(build_apk_fixture, name))
            setattr(build_apk_fixture, name, value)
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.folder = Path(folder.name)
        Handler.requests = []

    def fetch(self, mode, target, checksum=SHA256, verify=None):
        build_apk_fixture.fetch(target, self.primary + mode + '/f', checksum, 10000, timeout=5, verify=verify)

    def test_rejected_primary_bodies_fall_back_to_the_mirror(self):
        for mode in ('missing', 'truncated', 'wrong', 'oversized'):
            with self.subTest(mode=mode):
                target = self.folder / mode
                Handler.requests = []
                self.fetch(mode, target)
                self.assertEqual(target.read_bytes(), BODY)
                self.assertEqual(Handler.requests, [f'/p/{mode}/f', f'/mirror/{mode}/f'])
                self.assertEqual(sorted(path.name for path in self.folder.glob(mode + '*')), [mode])

    def test_all_candidates_failing_reports_each_and_leaves_no_file(self):
        target = self.folder / 'f'
        with self.assertRaisesRegex(SystemExit, r'truncated body: 100 of 5000 bytes'):
            # Not under GOOGLE_PRIMARY, so there is no mirror candidate.
            build_apk_fixture.fetch(target, 'http://127.0.0.1:%d/other/truncated/f' % self.server.server_port,
                                    SHA256, 10000, timeout=5)
        self.assertEqual(Handler.requests, ['/other/truncated/f'])
        self.assertEqual(list(self.folder.iterdir()), [])

    def test_cached_file_that_fails_verification_is_fetched_again(self):
        target = self.folder / 'f'
        target.write_bytes(BODY[:100])
        self.fetch('good', target)
        self.assertEqual(target.read_bytes(), BODY)
        rejected = []

        def verify(path):
            rejected.append(path.name)
            if path.read_bytes() != BODY:
                raise ValueError('content mismatch')
        target.write_bytes(b'portal')
        self.fetch('good', target, checksum=None, verify=verify)
        self.assertEqual(target.read_bytes(), BODY)
        self.assertEqual(rejected, ['f', 'f.download'])


if __name__ == '__main__':
    unittest.main()
