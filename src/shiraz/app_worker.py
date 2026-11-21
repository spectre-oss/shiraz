# SPDX-License-Identifier: GPL-3.0-only
"""Private registered Activity worker, launched by HostSession."""

import json
import os
import socket
import sys

from .apk import FormatError, inspect_apk
from .launch_settings import normalize_settings
from .package_registry import PackageRegistry
from .runtime import run_activity


def main():
    if len(sys.argv) not in (5, 6, 7, 9):
        raise ValueError('Activity worker arguments')
    control_fd = os.environ.pop('SHIRAZ_PROCESS_CONTROL_FD', None)
    settings = normalize_settings(json.loads(sys.argv[5]) if len(sys.argv) >= 6 else None)
    os.environ['SHIRAZ_GUEST_LOADER'] = '1' if settings.pop('native_code') else '0'
    embed = settings.pop('embed')
    if embed is not None:
        os.environ.update(SHIRAZ_LIVE_INPUT=embed['input'], SHIRAZ_EMBED_FRAME=embed['frame'])
    registry, package, sha256 = sys.argv[1:4]
    apk = PackageRegistry(registry).launch_apk(package, sha256)
    report = inspect_apk(apk)
    if report['apk_sha256'] != sha256 or report['manifest']['package'] != package:
        raise FormatError('Registered Activity identity changed')
    from contextlib import ExitStack

    with ExitStack() as resources:
        if control_fd is not None:
            from .process_control import supervise_process

            descriptor = int(control_fd)
            if (
                descriptor < 3
                or descriptor == int(sys.argv[4])
                or len(sys.argv) == 9
                and descriptor == int(sys.argv[7])
            ):
                raise FormatError('Invalid private process control descriptor')
            control = resources.enter_context(socket.socket(fileno=descriptor))
            settings['_process_observer'] = lambda process: supervise_process(control, process)
        broker = resources.enter_context(socket.socket(fileno=int(sys.argv[4])))
        if len(sys.argv) >= 7:
            settings['_activity'] = sys.argv[6]
        if len(sys.argv) == 9:
            descriptor, caller_uid = int(sys.argv[7]), int(sys.argv[8])
            if descriptor < 3 or descriptor == broker.fileno() or not 10000 <= caller_uid <= 19999:
                raise FormatError('Invalid activity launch channel')
            channel = resources.enter_context(socket.socket(fileno=descriptor))
            settings['_activity_channel'] = (channel, caller_uid)
        return run_activity(apk, report, registry=registry, _broker_channel=broker, **settings)


if __name__ == '__main__':
    sys.exit(main())
