# SPDX-License-Identifier: GPL-3.0-only
"""Private host-issued job entry. No listener or guest-selectable launch API."""

import socket
import sys

from .worker_lifetime import watch_parent


def main():
    if len(sys.argv) != 9:
        raise ValueError('Job worker arguments')
    watch_parent(int(sys.argv[7]))
    from .runtime import run_job

    with (
        socket.socket(fileno=int(sys.argv[5])) as channel,
        socket.socket(fileno=int(sys.argv[6])) as broker,
    ):
        run_job(
            sys.argv[1],
            sys.argv[2],
            sys.argv[3],
            sys.argv[4],
            channel,
            broker_channel=broker,
            lifetime_fd=None if sys.argv[8] == '-1' else int(sys.argv[8]),
        )


if __name__ == '__main__':
    main()
