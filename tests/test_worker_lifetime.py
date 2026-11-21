# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import selectors
import signal
import subprocess
import sys
import time
import unittest
from unittest.mock import patch

from shiraz.worker_lifetime import watch_parent


class WorkerLifetimeTests(unittest.TestCase):
    def test_rejects_nonisolated_group_and_invalid_parent(self):
        with (
            patch('shiraz.worker_lifetime.os.getpgrp', return_value=2),
            patch('shiraz.worker_lifetime.os.getpid', return_value=3),
        ):
            with self.assertRaises(ValueError):
                watch_parent(10)
        for parent in (0, 1, True, '10'):
            with self.assertRaises(ValueError):
                watch_parent(parent)

    def test_host_death_kills_worker_and_descendant(self):
        worker = '''import json,os,subprocess,sys,time
from shiraz.worker_lifetime import watch_parent
watch_parent(int(sys.argv[1]))
child=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'],stdout=subprocess.DEVNULL)
print(json.dumps([os.getpid(),child.pid]),flush=True)
time.sleep(60)
'''
        host = '''import os,subprocess,sys,time
worker=subprocess.Popen([sys.executable,'-c',sys.argv[1],str(os.getpid())],start_new_session=True,stdout=subprocess.PIPE,text=True)
print(worker.stdout.readline(),end='',flush=True)
time.sleep(60)
'''
        process = subprocess.Popen(
            [sys.executable, '-c', host, worker],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        pids = []
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                self.assertTrue(selector.select(10), 'worker did not start')
            pids = json.loads(process.stdout.readline())
            self.assertIsNone(process.poll())
            for pid in pids:
                self.assertTrue(self.live(pid))
            process.kill()
            process.wait(timeout=5)
            deadline = time.monotonic() + 5
            while any(self.live(pid) for pid in pids) and time.monotonic() < deadline:
                time.sleep(0.05)
            self.assertFalse(
                any(self.live(pid) for pid in pids), 'orphan worker group remained executing'
            )
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            if pids and self.live(pids[0]):
                os.killpg(pids[0], signal.SIGKILL)
            process.stdout.close()
            process.stderr.close()

    @staticmethod
    def live(pid):
        result = subprocess.run(
            ['ps', '-o', 'stat=', '-p', str(pid)], capture_output=True, text=True, timeout=5
        )
        state = result.stdout.strip()
        # Orphans may briefly remain as zombies until the system reaps them.
        return bool(state) and not state.startswith('Z')
