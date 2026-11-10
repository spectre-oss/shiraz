# SPDX-License-Identifier: GPL-3.0-only
"""Exercise pinned packaging downloads against a real bounded HTTP server."""
import hashlib
import http.server
import importlib.util
import tempfile
import threading
import unittest
import urllib.error
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_appimage_download_test', ROOT / 'scripts/build_appimage.py')
packaging = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packaging)


@contextmanager
def server(statuses, body):
    requests = []

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            requests.append(self.path)
            status = statuses[min(len(requests) - 1, len(statuses) - 1)]
            self.send_response(status)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    httpd = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    worker = threading.Thread(target=httpd.serve_forever, daemon=True)
    worker.start()
    try:
        yield f'http://127.0.0.1:{httpd.server_port}/tool', requests
    finally:
        httpd.shutdown()
        httpd.server_close()
        worker.join()


class AppImageDownloadTests(unittest.TestCase):
    def test_transient_504_then_verified_body_is_published(self):
        body = b'pinned packaging fixture\n'
        with tempfile.TemporaryDirectory() as directory, server([504, 200], body) as (url, requests), \
                patch.dict(packaging.DOWNLOADS, {'test-tool': (url, hashlib.sha256(body).hexdigest())}), \
                patch.object(packaging.time, 'sleep'):
            cache = Path(directory)
            result = packaging.fetch('test-tool', cache)
            self.assertEqual(result.read_bytes(), body)
            self.assertEqual(len(requests), 2)
            self.assertTrue(result.stat().st_mode & 0o111)
            self.assertFalse((cache / 'test-tool.download').exists())

    def test_transient_failure_has_three_attempt_limit_and_no_output(self):
        body = b'gateway error'
        with tempfile.TemporaryDirectory() as directory, server([504], body) as (url, requests), \
                patch.dict(packaging.DOWNLOADS, {'test-tool': (url, hashlib.sha256(body).hexdigest())}), \
                patch.object(packaging.time, 'sleep'):
            cache = Path(directory)
            with self.assertRaises(urllib.error.HTTPError):
                packaging.fetch('test-tool', cache)
            self.assertEqual(len(requests), 3)
            self.assertEqual(list(cache.iterdir()), [])

    def test_permanent_404_is_not_retried(self):
        body = b'not found'
        with tempfile.TemporaryDirectory() as directory, server([404], body) as (url, requests), \
                patch.dict(packaging.DOWNLOADS, {'test-tool': (url, hashlib.sha256(body).hexdigest())}), \
                patch.object(packaging.time, 'sleep'):
            cache = Path(directory)
            with self.assertRaises(urllib.error.HTTPError):
                packaging.fetch('test-tool', cache)
            self.assertEqual(len(requests), 1)
            self.assertEqual(list(cache.iterdir()), [])

    def test_checksum_failure_is_not_retried_or_published(self):
        body = b'incorrect content'
        with tempfile.TemporaryDirectory() as directory, server([200], body) as (url, requests), \
                patch.dict(packaging.DOWNLOADS, {'test-tool': (url, '0' * 64)}), \
                patch.object(packaging.time, 'sleep'):
            cache = Path(directory)
            with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                packaging.fetch('test-tool', cache)
            self.assertEqual(len(requests), 1)
            self.assertEqual(list(cache.iterdir()), [])

    def test_bad_cached_tool_stays_fail_closed_without_network(self):
        body = b'cached content'
        with tempfile.TemporaryDirectory() as directory, server([200], body) as (url, requests), \
                patch.dict(packaging.DOWNLOADS, {'test-tool': (url, '0' * 64)}):
            cache = Path(directory)
            (cache / 'test-tool').write_bytes(body)
            with self.assertRaisesRegex(RuntimeError, 'Cached packaging tool checksum mismatch'):
                packaging.fetch('test-tool', cache)
            self.assertEqual(requests, [])
            self.assertEqual((cache / 'test-tool').read_bytes(), body)
