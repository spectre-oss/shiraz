# SPDX-License-Identifier: GPL-3.0-only
"""Passive host route/link observations for future job policy.

Not Internet validation, policy-route/VPN resolution, metering or roaming data.
Unsupported/ambiguous observations return None, distinct from no connected link.
No packets or DNS queries are sent. Job network admission is not enabled here.
"""

import re
import sys
from dataclasses import dataclass
from pathlib import Path

LIMIT = 1024 * 1024
INTERFACE = re.compile(r'[A-Za-z0-9_.:-]{1,15}')


@dataclass(frozen=True)
class NetworkObservation:
    connected: bool
    interface: str | None = None
    transport: str | None = None
    metered: bool | None = None
    validated: bool | None = None


def text(path):
    with path.open('rb') as stream:
        value = stream.read(LIMIT + 1)
    if len(value) > LIMIT:
        raise ValueError('Host network table limit')
    return value.decode('ascii')


def number(value, radix):
    pattern = r'[0-9]+' if radix == 10 else r'[0-9a-fA-F]+'
    if not re.fullmatch(pattern, value) or len(value) > 10:
        raise ValueError('Invalid route integer')
    result = int(value, radix)
    if result > 0xFFFFFFFF:
        raise ValueError('Route integer overflow')
    return result


def interface_name(value):
    if not INTERFACE.fullmatch(value) or value in ('.', '..'):
        raise ValueError('Invalid route interface')
    return value


def read_linux(proc_net, sys_net):
    """Injectable paths are host tests only, never supplied by an APK."""
    routes = []
    found = False
    for family, filename in ((4, 'route'), (6, 'ipv6_route')):
        try:
            contents = text(Path(proc_net) / filename)
        except FileNotFoundError:
            continue
        found = True
        for line in contents.splitlines():
            fields = line.split()
            if not fields or (family == 4 and fields[0] == 'Iface'):
                continue
            if len(fields) != (11 if family == 4 else 10):
                raise ValueError('Malformed route row')
            if family == 4:
                if fields[1] != '00000000' or fields[7] != '00000000':
                    continue
                name, metric, flags = fields[0], number(fields[6], 10), number(fields[3], 16)
            else:
                if fields[:4] != ['0' * 32, '00', '0' * 32, '00']:
                    continue
                name, metric, flags = fields[9], number(fields[5], 16), number(fields[8], 16)
            if flags & 1 and not flags & 0x200:
                routes.append((family, metric, interface_name(name)))
    if not found:
        raise OSError('Route tables unavailable')
    # Match the legacy Java adapter's deterministic IPv4-first selection; this
    # does not infer destination-specific route priority across address families.
    for _, _, name in sorted(routes):
        link = Path(sys_net) / name
        raw_flags = text(link / 'flags').strip()
        flags = number(raw_flags[2:] if raw_flags.startswith('0x') else raw_flags, 16)
        if not flags & 1 or flags & 8:
            continue
        state = text(link / 'operstate').strip()
        if state not in (
            'up',
            'unknown',
            'down',
            'dormant',
            'lowerlayerdown',
            'notpresent',
            'testing',
        ):
            raise ValueError('Unknown interface operational state')
        if state not in ('up', 'unknown'):
            continue
        carrier = text(link / 'carrier').strip()
        if carrier not in ('0', '1'):
            raise ValueError('Invalid carrier state')
        if carrier == '0':
            continue
        if (link / 'tun_flags').exists():
            raise ValueError('Tunnel route requires policy-aware observation')
        if (link / 'wireless').is_dir():
            transport = 'wifi'
        elif text(link / 'type').strip() == '1':
            transport = 'ethernet'
        else:
            raise ValueError('Unsupported route transport')
        return NetworkObservation(True, name, transport)
    return NetworkObservation(False)


def observe_network():
    if sys.platform == 'darwin':
        from .job_network_darwin import observe

        return observe()
    if not sys.platform.startswith('linux'):
        return None
    try:
        return read_linux(Path('/proc/net'), Path('/sys/class/net'))
    except (OSError, ValueError):
        return None
