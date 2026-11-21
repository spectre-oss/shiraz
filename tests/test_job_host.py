# SPDX-License-Identifier: GPL-3.0-only
import json
import selectors
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from shiraz.job_host import ownership
from shiraz.package_registry import PackageRegistry


class JobHostTests(unittest.TestCase):
    def test_foreground_lifetime_singleton_and_restarts(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = Path(directory) / 'registry'
            command = [sys.executable, '-m', 'shiraz', 'serve-jobs', '--registry', str(registry)]
            for abrupt in (False, True, False):
                host = subprocess.Popen(
                    command,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                )
                try:
                    with selectors.DefaultSelector() as selector:
                        selector.register(host.stdout, selectors.EVENT_READ)
                        self.assertTrue(selector.select(10), 'Host readiness timed out')
                    ready = json.loads(host.stdout.readline())
                    self.assertEqual(ready, dict(event='ready', service='jobs', pid=host.pid))
                    self.assertIsNone(host.poll())  # stdin EOF and no apps do not stop the service.
                    duplicate = subprocess.run(
                        command,
                        stdin=subprocess.DEVNULL,
                        capture_output=True,
                        text=True,
                        timeout=10,
                    )
                    self.assertEqual(duplicate.returncode, 1)
                    self.assertIn('already running', duplicate.stderr)
                    self.assertIsNone(host.poll())
                    host.kill() if abrupt else host.terminate()
                    stdout, stderr = host.communicate(timeout=15)
                    self.assertEqual(host.returncode, -9 if abrupt else 0, stderr)
                    if not abrupt:
                        self.assertEqual(json.loads(stdout), dict(event='stopped', service='jobs'))
                finally:
                    if host.poll() is None:
                        host.kill()
                        host.wait(timeout=5)
                    host.stdout.close()
                    host.stderr.close()

    def test_unsafe_lock_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = PackageRegistry(Path(directory) / 'registry')
            target = Path(directory) / 'other'
            target.write_text('untouched')
            (registry.root / 'job-host.lock').symlink_to(target)
            with self.assertRaises(OSError):
                with ownership(registry):
                    pass
            self.assertEqual(target.read_text(), 'untouched')
