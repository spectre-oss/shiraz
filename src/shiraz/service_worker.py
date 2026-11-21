# SPDX-License-Identifier: GPL-3.0-only
"""Private broker worker; inherited socket and argv are issued by the host."""

import socket
import sys

from .runtime import run_service


def main():
    if len(sys.argv) != 7:
        raise ValueError('Service worker arguments')
    with (
        socket.socket(fileno=int(sys.argv[5])) as channel,
        socket.socket(fileno=int(sys.argv[6])) as broker,
    ):
        run_service(
            sys.argv[1],
            sys.argv[2],
            sys.argv[3],
            sys.argv[4],
            channel,
            shared=True,
            broker_channel=broker,
        )


if __name__ == '__main__':
    main()
