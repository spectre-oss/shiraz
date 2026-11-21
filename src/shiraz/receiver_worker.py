# SPDX-License-Identifier: GPL-3.0-only
"""Private host entry for one enrolled receiver; no public listener."""

import socket
import sys

from .runtime import run_receiver


def main():
    if len(sys.argv) != 7:
        raise ValueError('Receiver worker arguments')
    with (
        socket.socket(fileno=int(sys.argv[5])) as channel,
        socket.socket(fileno=int(sys.argv[6])) as broker,
    ):
        run_receiver(
            sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], channel, broker_channel=broker
        )


if __name__ == '__main__':
    main()
