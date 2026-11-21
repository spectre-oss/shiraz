# SPDX-License-Identifier: GPL-3.0-only
"""Bounded JSON control messages using NativeIpc's SCM_RIGHTS envelope.

Received descriptors are owned by read_packet's caller; write_packet borrows them.
A malformed message closes every received descriptor before raising.
"""

import array
import json
import os
import socket
import struct
import time

MAX_FRAME = 65536
MAGIC = 0x315A4853
HEADER = struct.Struct('<III')
MAX_DESCRIPTORS = 1


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate broker field')
        result[key] = value
    return result


def read_packet(channel, idle_timeout=None, *, max_descriptors=MAX_DESCRIPTORS):
    payload, owned = read_payload(channel, idle_timeout, max_descriptors=max_descriptors)
    try:
        return json.loads(payload.decode('utf-8'), object_pairs_hook=_object), owned
    except BaseException:
        for descriptor in owned:
            os.close(descriptor)
        raise


def read_payload(
    channel, idle_timeout=None, *, max_descriptors=MAX_DESCRIPTORS, max_bytes=MAX_FRAME
):
    if not 1 <= max_bytes <= 4 * 1024 * 1024:
        raise ValueError('Payload limit')
    if not 0 <= max_descriptors <= MAX_DESCRIPTORS:
        raise ValueError('Broker descriptor limit')
    owned = []
    deadline = None
    channel.settimeout(idle_timeout)

    def receive(size):
        nonlocal deadline
        data = bytearray()
        while len(data) < size:
            if deadline is not None:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError('Broker frame deadline')
                channel.settimeout(remaining)
            chunk, ancillary, flags, _ = channel.recvmsg(
                size - len(data),
                socket.CMSG_SPACE(128 * array.array('i').itemsize) + socket.CMSG_SPACE(12),
                getattr(socket, 'MSG_CMSG_CLOEXEC', 0),
            )
            bad = bool(flags & (socket.MSG_CTRUNC | socket.MSG_TRUNC))
            for level, kind, value in ancillary:
                if level == socket.SOL_SOCKET and kind == socket.SCM_RIGHTS:
                    descriptors = array.array('i')
                    usable = len(value) - len(value) % descriptors.itemsize
                    descriptors.frombytes(value[:usable])
                    owned.extend(descriptors)
                    bad |= usable != len(value)
                elif level != socket.SOL_SOCKET or kind != getattr(socket, 'SCM_CREDENTIALS', -1):
                    bad = True
            # No host launch inherits arbitrary fds (Popen uses pass_fds). Set
            # CLOEXEC here too for platforms without atomic MSG_CMSG_CLOEXEC.
            for descriptor in owned:
                os.set_inheritable(descriptor, False)
            if bad or len(owned) > max_descriptors:
                raise ValueError('Invalid broker ancillary data')
            if not chunk:
                raise EOFError('Broker channel closed')
            if deadline is None:
                deadline = time.monotonic() + 10
            data.extend(chunk)
        return bytes(data)

    try:
        magic, size, count = HEADER.unpack(receive(HEADER.size))
        if magic != MAGIC or not 1 <= size <= max_bytes or count > max_descriptors:
            raise ValueError('Broker frame header')
        payload = receive(size)
        if count != len(owned):
            raise ValueError('Broker descriptor count mismatch')
        return payload, owned
    except BaseException:
        for descriptor in owned:
            os.close(descriptor)
        raise


def write_packet(channel, value, descriptors=()):
    data = json.dumps(value, separators=(',', ':'), allow_nan=False).encode('utf-8')
    write_payload(channel, data, descriptors)


def write_payload(channel, data, descriptors=(), *, max_bytes=MAX_FRAME):
    if (
        not 1 <= max_bytes <= 4 * 1024 * 1024
        or not 1 <= len(data) <= max_bytes
        or len(descriptors) > MAX_DESCRIPTORS
    ):
        raise ValueError('Broker reply size or descriptor count')
    channel.settimeout(10)
    header = HEADER.pack(MAGIC, len(data), len(descriptors))
    ancillary = (
        [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', descriptors))]
        if descriptors
        else []
    )
    sent = channel.sendmsg([header], ancillary)
    if sent <= 0:
        raise EOFError('Broker channel closed')
    channel.sendall(header[sent:] + data)
