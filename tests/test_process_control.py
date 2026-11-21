# SPDX-License-Identifier: GPL-3.0-only
import os
import signal
import socket
import struct
import threading
import unittest
from unittest.mock import Mock, patch

from shiraz.process_control import ProcessControl, supervise_process


class ProcessControlTests(unittest.TestCase):
    def test_partial_report_and_exact_pid_command(self):
        left, right = socket.socketpair()
        with left, right:
            owner = ProcessControl(left)
            right.sendall(b'\0\0')
            self.assertIsNone(owner.reported_pid())
            with self.assertRaises(PermissionError):
                owner.terminate(123)
            right.sendall(struct.pack('!H', 123))
            self.assertEqual(owner.reported_pid(), 123)
            with self.assertRaises(PermissionError):
                owner.terminate(124)
            owner.terminate(123)
            self.assertEqual(right.recv(1), b'K')
            owner.close()
            self.assertIsNone(owner.reported_pid())

    def test_worker_commands_only_own_group_and_joins_listener(self):
        left, right = socket.socketpair()
        stopped = threading.Event()
        with (
            left,
            right,
            patch('shiraz.process_control.os.getsid', return_value=os.getpid()),
            patch('shiraz.process_control.os.getpgrp', return_value=os.getpid()),
            patch(
                'shiraz.process_control.os.killpg', side_effect=lambda *args: stopped.set()
            ) as kill,
        ):
            with supervise_process(right, Mock(pid=123)):
                self.assertEqual(struct.unpack('!I', left.recv(4))[0], 123)
                left.sendall(b'K')
                self.assertTrue(stopped.wait(2))
                kill.assert_called_once_with(os.getpid(), signal.SIGKILL)

    def test_worker_rejects_non_dedicated_session(self):
        left, right = socket.socketpair()
        with left, right, patch('shiraz.process_control.os.getsid', return_value=-1):
            with self.assertRaises(RuntimeError), supervise_process(right, Mock(pid=123)):
                self.fail('Unsafe supervisor entered')
