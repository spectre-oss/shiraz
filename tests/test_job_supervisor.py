# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import selectors
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

from shiraz.job_supervisor import restart_delay


class JobSupervisorTests(unittest.TestCase):
    def test_backoff_is_bounded(self):
        self.assertEqual([restart_delay(n) for n in range(1, 8)], [1, 2, 4, 8, 16, 30, 30])
        self.assertEqual(restart_delay(100000), 30)

    def test_child_restart_duplicate_exclusion_and_supervisor_death(self):
        with tempfile.TemporaryDirectory() as directory:
            command = [
                sys.executable,
                '-m',
                'shiraz',
                'supervise-jobs',
                '--registry',
                str(Path(directory) / 'registry'),
            ]
            for abrupt in (False, True):
                supervisor = subprocess.Popen(
                    command,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    bufsize=0,
                )
                child_pid = None
                try:

                    def receive():
                        with selectors.DefaultSelector() as selector:
                            selector.register(supervisor.stdout, selectors.EVENT_READ)
                            self.assertTrue(selector.select(10), 'Supervisor output timed out')
                        return json.loads(supervisor.stdout.readline())

                    ready = receive()
                    self.assertEqual(ready['event'], 'ready')
                    child_pid = ready['pid']
                    duplicate = subprocess.run(
                        command, stdin=subprocess.DEVNULL, capture_output=True, timeout=10
                    )
                    self.assertEqual(duplicate.returncode, 1)
                    self.assertIn(b'supervisor already running', duplicate.stderr)
                    if not abrupt:
                        started = time.monotonic()
                        os.kill(child_pid, signal.SIGKILL)
                        restart = receive()
                        self.assertEqual(
                            restart,
                            dict(event='restart', service='jobs', exit_status=-9, delay_seconds=1),
                        )
                        ready = receive()
                        self.assertGreaterEqual(time.monotonic() - started, 1)
                        self.assertNotEqual(ready['pid'], child_pid)
                        child_pid = ready['pid']
                    supervisor.kill() if abrupt else supervisor.terminate()
                    supervisor.wait(timeout=25)
                    self.assertEqual(supervisor.returncode, -9 if abrupt else 0)
                    deadline = time.monotonic() + 5
                    while self.live(child_pid) and time.monotonic() < deadline:
                        time.sleep(0.05)
                    self.assertFalse(self.live(child_pid), 'Job host survived its supervisor')
                finally:
                    if supervisor.poll() is None:
                        supervisor.kill()
                        supervisor.wait(timeout=5)
                    if child_pid is not None and self.live(child_pid):
                        os.kill(child_pid, signal.SIGKILL)
                    supervisor.stdout.close()
                    supervisor.stderr.close()

    @staticmethod
    def live(pid):
        status = subprocess.run(
            ['ps', '-o', 'stat=', '-p', str(pid)], capture_output=True, text=True, timeout=5
        ).stdout.strip()
        return bool(status) and not status.startswith('Z')
