# SPDX-License-Identifier: GPL-3.0-only
"""Private host-to-worker control, never inherited by APK/ART processes."""

import os
import signal
import socket
import struct
import threading
from contextlib import contextmanager


class ProcessControl:
    def __init__(self, channel):
        self.channel = channel
        self.channel.setblocking(False)
        self.buffer = bytearray()
        self.pid = None
        self.ended = False
        self.group_termination_requested = False

    def reported_pid(self):
        if self.ended:
            return None
        while len(self.buffer) < 4:
            try:
                data = self.channel.recv(4 - len(self.buffer))
            except BlockingIOError:
                return None
            except OSError:
                self.ended = True
                return None
            if not data:
                self.ended = True
                return None
            self.buffer.extend(data)
        if self.pid is None:
            self.pid = struct.unpack('!I', self.buffer)[0]
            if not 1 <= self.pid <= 0x7FFFFFFF:
                raise ValueError('Invalid trusted process report')
        return self.pid

    def terminate(self, expected_pid):
        if type(expected_pid) is not int or self.reported_pid() != expected_pid:
            raise PermissionError('Process identity unavailable or changed')
        # No caller-selected signal or host PID reaches the worker.
        self.group_termination_requested = True
        try:
            self.channel.sendall(b'K')
        except BaseException:
            self.group_termination_requested = False
            raise

    def close(self):
        self.ended = True
        self.channel.close()


@contextmanager
def supervise_process(channel, process):
    """Called by the trusted worker after spawning ART with close_fds enabled.

    Termination signals the worker's own session group. Its live group leader
    prevents reuse; no externally supplied PID is ever passed to killpg.
    """
    if os.getsid(0) != os.getpid() or os.getpgrp() != os.getpid():
        raise RuntimeError('Process control requires a dedicated worker session')
    if process.pid <= 0 or process.pid > 0x7FFFFFFF:
        raise ValueError('Invalid child PID')
    channel.sendall(struct.pack('!I', process.pid))

    def listen():
        try:
            command = channel.recv(1)
            if command == b'K':
                os.killpg(os.getpgrp(), signal.SIGKILL)
        except OSError:
            pass  # Owner closes the private channel during normal cleanup.

    listener = threading.Thread(target=listen, daemon=True)
    listener.start()
    try:
        yield
    finally:
        try:
            channel.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        listener.join(timeout=3)
        if listener.is_alive():
            raise RuntimeError('Process control listener did not stop')
