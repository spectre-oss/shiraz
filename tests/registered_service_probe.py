# SPDX-License-Identifier: GPL-3.0-only
"""Trusted test launcher; argv values originate from signed fixture enrollment."""

import socket
import sys

from shiraz.runtime import run_service

if __name__ == '__main__':
    with socket.socket(fileno=int(sys.argv[5])) as channel:
        run_service(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], channel, timeout=30)
