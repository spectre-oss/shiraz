# SPDX-License-Identifier: GPL-3.0-only
"""Private job worker lifetime tied to its direct owning host process."""

import os
import signal
import threading


def watch_parent(parent_pid):
    """Kill this dedicated worker group if its kernel parent changes.

    This does not release job claims or authorize recovery. The worker must be
    a process-group leader created with start_new_session by the trusted host.
    """
    if type(parent_pid) is not int or parent_pid <= 1 or os.getpgrp() != os.getpid():
        raise ValueError('Invalid job worker parent/group')

    def watch():
        while True:
            if os.getppid() != parent_pid:
                os.killpg(os.getpid(), signal.SIGKILL)
                return
            # A daemon thread is intentional: normal worker exit needs no join.
            threading.Event().wait(0.25)

    thread = threading.Thread(target=watch, name='shiraz-job-parent-watch', daemon=True)
    thread.start()
    return thread
