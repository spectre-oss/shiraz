# SPDX-License-Identifier: GPL-3.0-only
import os
import selectors
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

from shiraz.job_lifetime import create, probe


class JobLifetimeTests(unittest.TestCase):
    def test_lock_and_missing_file_semantics(self):
        with tempfile.TemporaryDirectory() as directory:
            token = 'a' * 32
            with self.assertRaises(FileNotFoundError):
                probe(directory, token)
            with create(directory, token) as owner:
                self.assertIsNone(probe(directory, token))
                with self.assertRaises(FileExistsError):
                    create(directory, token)
                self.assertFalse(os.get_inheritable(owner.fileno()))
            with probe(directory, token):
                self.assertIsNone(probe(directory, token))
            owner.close()
            with self.assertRaises(ValueError):
                owner.fileno()

    def test_inherited_descendant_keeps_lock_after_host_and_worker_close(self):
        with tempfile.TemporaryDirectory() as directory:
            token = 'b' * 32
            owner = create(directory, token)
            script = '''import subprocess,sys
fd=int(sys.argv[1])
child=subprocess.Popen([sys.executable,'-c','import sys;sys.stdin.buffer.read(1)'],pass_fds=(fd,))
print('READY',flush=True)
'''
            worker = subprocess.Popen(
                [sys.executable, '-c', script, str(owner.fileno())],
                pass_fds=(owner.fileno(),),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(worker.stdout, selectors.EVENT_READ)
                    self.assertTrue(selector.select(5))
                self.assertEqual(worker.stdout.readline().strip(), 'READY')
                worker.wait(timeout=5)
                owner.close()
                self.assertIsNone(probe(directory, token), 'descendant lost inherited lock')
                worker.stdin.close()
                deadline = time.monotonic() + 5
                acquired = None
                while acquired is None and time.monotonic() < deadline:
                    acquired = probe(directory, token)
                    if acquired is None:
                        time.sleep(0.02)
                self.assertIsNotNone(acquired, 'lock retained after descendant exit')
                acquired.close()
            finally:
                owner.close()
                if not worker.stdin.closed:
                    worker.stdin.close()
                if worker.poll() is None:
                    worker.kill()
                    worker.wait(timeout=5)
                worker.stdout.close()
                worker.stderr.close()

    def test_rejects_unsafe_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            token = 'c' * 32
            with self.assertRaises(ValueError):
                create(directory, '../bad')
            with create(directory, token):
                pass
            path = Path(directory) / 'job-lifetimes' / (token + '.lock')
            path.chmod(0o644)
            with self.assertRaises(PermissionError):
                probe(directory, token)
            path.chmod(0o600)
            os.link(path, path.with_suffix('.linked'))
            with self.assertRaises(PermissionError):
                probe(directory, token)
            link = path.parent / ('d' * 32 + '.lock')
            link.symlink_to(path)
            with self.assertRaises(OSError):
                probe(directory, 'd' * 32)

    def test_rejects_shared_or_symlink_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'job-lifetimes'
            path.mkdir(mode=0o755)
            path.chmod(0o755)
            with self.assertRaises(PermissionError):
                create(directory, 'e' * 32)
            path.rmdir()
            target = Path(directory) / 'other'
            target.mkdir(mode=0o700)
            path.symlink_to(target, target_is_directory=True)
            with self.assertRaises(PermissionError):
                create(directory, 'e' * 32)
