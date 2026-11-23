#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Linux child-side sandbox executor: applies Landlock LSM + seccomp network deny, then execs.

Invoked directly as a script (no PYTHONPATH required):
    python3 /path/to/sandbox_exec.py <plan.json> -- <command...>
"""

from __future__ import annotations

import ctypes
import errno
import json
import os
import platform
import struct
import sys
from pathlib import Path
from typing import Iterable, List

# Linux syscall numbers (identical on x86_64 and aarch64 for Landlock)
_NR_LANDLOCK_CREATE_RULESET = 444
_NR_LANDLOCK_ADD_RULE = 445
_NR_LANDLOCK_RESTRICT_SELF = 446

_LANDLOCK_CREATE_RULESET_VERSION = 1 << 0
_LANDLOCK_RULE_PATH_BENEATH = 1

_ACCESS_FS_EXECUTE = 1 << 0
_ACCESS_FS_WRITE_FILE = 1 << 1
_ACCESS_FS_READ_FILE = 1 << 2
_ACCESS_FS_READ_DIR = 1 << 3
_ACCESS_FS_REMOVE_DIR = 1 << 4
_ACCESS_FS_REMOVE_FILE = 1 << 5
_ACCESS_FS_MAKE_CHAR = 1 << 6
_ACCESS_FS_MAKE_DIR = 1 << 7
_ACCESS_FS_MAKE_REG = 1 << 8
_ACCESS_FS_MAKE_SOCK = 1 << 9
_ACCESS_FS_MAKE_FIFO = 1 << 10
_ACCESS_FS_MAKE_BLOCK = 1 << 11
_ACCESS_FS_MAKE_SYM = 1 << 12
_ACCESS_FS_REFER = 1 << 13
_ACCESS_FS_TRUNCATE = 1 << 14

_PR_SET_NO_NEW_PRIVS = 38
_PR_SET_SECCOMP = 22
_SECCOMP_MODE_FILTER = 2

# Seccomp BPF constants (from <linux/filter.h>, <linux/seccomp.h>, <linux/audit.h>)
_BPF_LD = 0x00
_BPF_W = 0x00
_BPF_ABS = 0x20
_BPF_JMP = 0x05
_BPF_JEQ = 0x10
_BPF_JGE = 0x30
_BPF_K = 0x00
_BPF_RET = 0x06

_SECCOMP_RET_KILL_PROCESS = 0x80000000
_SECCOMP_RET_ERRNO = 0x00050000
_SECCOMP_RET_ALLOW = 0x7FFF0000

_AUDIT_ARCH_X86_64 = 0xC000003E
_AUDIT_ARCH_AARCH64 = 0xC00000B7

_AF_UNIX = 1
_O_PATH = 0o10000000
_O_CLOEXEC = 0o2000000


class _LandlockRulesetAttr(ctypes.Structure):
    _fields_ = [('handled_access_fs', ctypes.c_uint64)]


class _LandlockPathBeneathAttr(ctypes.Structure):
    _pack_ = 1
    _fields_ = [
        ('allowed_access', ctypes.c_uint64),
        ('parent_fd', ctypes.c_int32),
    ]


class _SockFprog(ctypes.Structure):
    _fields_ = [
        ('len', ctypes.c_ushort),
        ('filter', ctypes.c_void_p),
    ]


def _libc() -> ctypes.CDLL:
    libc = ctypes.CDLL(None, use_errno=True)
    libc.syscall.restype = ctypes.c_long
    libc.prctl.restype = ctypes.c_int
    return libc


def _landlock_abi_version(libc: ctypes.CDLL) -> int:
    ctypes.set_errno(0)
    abi = libc.syscall(
        ctypes.c_long(_NR_LANDLOCK_CREATE_RULESET),
        ctypes.c_void_p(0),
        ctypes.c_size_t(0),
        ctypes.c_uint32(_LANDLOCK_CREATE_RULESET_VERSION),
    )
    return int(abi) if abi > 0 else 0


_DIR_ONLY_ACCESS = (
    _ACCESS_FS_READ_DIR
    | _ACCESS_FS_REMOVE_DIR
    | _ACCESS_FS_REMOVE_FILE
    | _ACCESS_FS_MAKE_CHAR
    | _ACCESS_FS_MAKE_DIR
    | _ACCESS_FS_MAKE_REG
    | _ACCESS_FS_MAKE_SOCK
    | _ACCESS_FS_MAKE_FIFO
    | _ACCESS_FS_MAKE_BLOCK
    | _ACCESS_FS_MAKE_SYM
    | _ACCESS_FS_REFER
)


def _add_path_rule(libc: ctypes.CDLL, ruleset_fd: int, path: str, access: int) -> int:
    if not path or not path.startswith('/'):
        return -errno.EINVAL
    if not os.path.isdir(path):
        access &= ~_DIR_ONLY_ACCESS
        if access == 0:
            return 0
    try:
        fd = os.open(path, _O_PATH | _O_CLOEXEC)
    except OSError as exc:
        return -(exc.errno or errno.ENOENT)
    try:
        attr = _LandlockPathBeneathAttr(allowed_access=access, parent_fd=fd)
        ctypes.set_errno(0)
        rc = libc.syscall(
            ctypes.c_long(_NR_LANDLOCK_ADD_RULE),
            ctypes.c_int(ruleset_fd),
            ctypes.c_uint32(_LANDLOCK_RULE_PATH_BENEATH),
            ctypes.byref(attr),
            ctypes.c_uint32(0),
        )
        return 0 if rc == 0 else -(ctypes.get_errno() or errno.EINVAL)
    finally:
        os.close(fd)


def apply_linux_landlock(
    read_only_paths: Iterable[str],
    read_write_paths: Iterable[str],
) -> int:
    """Apply Linux Landlock LSM ruleset to the current process.

    Returns 0 on success, -ENOSYS if the running kernel does not support Landlock,
    or a negative errno on error.
    """
    if sys.platform != 'linux':
        return -errno.ENOSYS
    libc = _libc()
    abi = _landlock_abi_version(libc)
    if abi <= 0:
        return -errno.ENOSYS

    read_rights = _ACCESS_FS_EXECUTE | _ACCESS_FS_READ_FILE | _ACCESS_FS_READ_DIR
    write_rights = (
        _ACCESS_FS_WRITE_FILE
        | _ACCESS_FS_REMOVE_DIR
        | _ACCESS_FS_REMOVE_FILE
        | _ACCESS_FS_MAKE_DIR
        | _ACCESS_FS_MAKE_REG
        | _ACCESS_FS_MAKE_SYM
        | _ACCESS_FS_MAKE_SOCK
        | _ACCESS_FS_MAKE_FIFO
    )
    handled = read_rights | write_rights | _ACCESS_FS_MAKE_CHAR | _ACCESS_FS_MAKE_BLOCK
    if abi >= 2:
        handled |= _ACCESS_FS_REFER
        write_rights |= _ACCESS_FS_REFER
    if abi >= 3:
        handled |= _ACCESS_FS_TRUNCATE
        write_rights |= _ACCESS_FS_TRUNCATE

    attr = _LandlockRulesetAttr(handled_access_fs=handled)
    ctypes.set_errno(0)
    ruleset_fd = int(
        libc.syscall(
            ctypes.c_long(_NR_LANDLOCK_CREATE_RULESET),
            ctypes.byref(attr),
            ctypes.c_size_t(ctypes.sizeof(attr)),
            ctypes.c_uint32(0),
        )
    )
    if ruleset_fd < 0:
        return -(ctypes.get_errno() or errno.ENOSYS)

    try:
        added = 0
        for path in read_only_paths:
            if not os.path.exists(path):
                continue
            rc = _add_path_rule(libc, ruleset_fd, path, read_rights)
            if rc == 0:
                added += 1
            elif rc != -errno.ENOENT:
                return rc
        for path in read_write_paths:
            if not os.path.exists(path):
                continue
            rc = _add_path_rule(libc, ruleset_fd, path, read_rights | write_rights)
            if rc == 0:
                added += 1
            elif rc != -errno.ENOENT:
                return rc
        if added == 0:
            return -errno.ENOENT

        ctypes.set_errno(0)
        if (
            libc.prctl(
                ctypes.c_int(_PR_SET_NO_NEW_PRIVS),
                ctypes.c_ulong(1),
                ctypes.c_ulong(0),
                ctypes.c_ulong(0),
                ctypes.c_ulong(0),
            )
            != 0
        ):
            return -(ctypes.get_errno() or errno.EPERM)
        ctypes.set_errno(0)
        rc = libc.syscall(
            ctypes.c_long(_NR_LANDLOCK_RESTRICT_SELF),
            ctypes.c_int(ruleset_fd),
            ctypes.c_uint32(0),
        )
        return 0 if rc == 0 else -(ctypes.get_errno() or errno.EPERM)
    finally:
        os.close(ruleset_fd)


def _bpf_stmt(code: int, k: int) -> bytes:
    return struct.pack('HBBI', code, 0, 0, k & 0xFFFFFFFF)


def _bpf_jump(code: int, k: int, jt: int, jf: int) -> bytes:
    return struct.pack('HBBI', code, jt, jf, k & 0xFFFFFFFF)


def apply_linux_seccomp_no_network() -> int:
    """Block non-AF_UNIX socket creation and io_uring via Linux seccomp-BPF."""
    if sys.platform != 'linux':
        return -errno.ENOSYS
    machine = platform.machine().lower()
    if machine in ('x86_64', 'amd64'):
        arch = _AUDIT_ARCH_X86_64
        nr_socket, nr_socketpair = 41, 53
        nr_io_uring = (425, 426, 427)
        reject_x32 = True
    elif machine in ('aarch64', 'arm64'):
        arch = _AUDIT_ARCH_AARCH64
        nr_socket, nr_socketpair = 198, 199
        nr_io_uring = (425, 426, 427)
        reject_x32 = False
    else:
        return -errno.ENOSYS

    # struct seccomp_data { int nr; __u32 arch; __u64 instruction_pointer; __u64 args[6]; }
    insns: List[bytes] = [
        _bpf_stmt(_BPF_LD | _BPF_W | _BPF_ABS, 4),  # arch
        _bpf_jump(_BPF_JMP | _BPF_JEQ | _BPF_K, arch, 1, 0),
        _bpf_stmt(_BPF_RET | _BPF_K, _SECCOMP_RET_KILL_PROCESS),
        _bpf_stmt(_BPF_LD | _BPF_W | _BPF_ABS, 0),  # nr
    ]
    if reject_x32:
        insns.extend(
            [
                _bpf_jump(_BPF_JMP | _BPF_JGE | _BPF_K, 0x40000000, 0, 1),
                _bpf_stmt(_BPF_RET | _BPF_K, _SECCOMP_RET_ERRNO | errno.EPERM),
            ]
        )
    for nr in nr_io_uring:
        insns.extend(
            [
                _bpf_jump(_BPF_JMP | _BPF_JEQ | _BPF_K, nr, 0, 1),
                _bpf_stmt(_BPF_RET | _BPF_K, _SECCOMP_RET_ERRNO | errno.EPERM),
            ]
        )
    insns.extend(
        [
            _bpf_jump(_BPF_JMP | _BPF_JEQ | _BPF_K, nr_socket, 1, 0),
            _bpf_jump(_BPF_JMP | _BPF_JEQ | _BPF_K, nr_socketpair, 0, 3),
            _bpf_stmt(_BPF_LD | _BPF_W | _BPF_ABS, 16),  # args[0] (domain, little-endian low word)
            _bpf_jump(_BPF_JMP | _BPF_JEQ | _BPF_K, _AF_UNIX, 1, 0),
            _bpf_stmt(_BPF_RET | _BPF_K, _SECCOMP_RET_ERRNO | errno.ENETUNREACH),
            _bpf_stmt(_BPF_RET | _BPF_K, _SECCOMP_RET_ALLOW),
        ]
    )

    buf = ctypes.create_string_buffer(b''.join(insns))
    prog = _SockFprog(len=len(insns), filter=ctypes.addressof(buf))
    libc = _libc()
    ctypes.set_errno(0)
    if (
        libc.prctl(
            ctypes.c_int(_PR_SET_NO_NEW_PRIVS),
            ctypes.c_ulong(1),
            ctypes.c_ulong(0),
            ctypes.c_ulong(0),
            ctypes.c_ulong(0),
        )
        != 0
    ):
        return -(ctypes.get_errno() or errno.EPERM)
    ctypes.set_errno(0)
    if (
        libc.prctl(
            ctypes.c_int(_PR_SET_SECCOMP),
            ctypes.c_ulong(_SECCOMP_MODE_FILTER),
            ctypes.byref(prog),
            ctypes.c_ulong(0),
            ctypes.c_ulong(0),
        )
        != 0
    ):
        return -(ctypes.get_errno() or errno.EPERM)
    return 0


def main(argv: List[str]) -> int:
    if len(argv) < 4 or argv[2] != '--':
        print('usage: sandbox_exec.py <plan.json> -- <command...>', file=sys.stderr)
        return 2
    plan_path = Path(argv[1])
    command = argv[3:]
    plan = json.loads(plan_path.read_text(encoding='utf-8'))

    if sys.platform == 'linux':
        rc = apply_linux_landlock(
            plan.get('read_only_paths', ()),
            plan.get('read_write_paths', ()),
        )
        if rc != 0:
            if rc == -errno.ENOSYS and os.environ.get('SHIRAZ_SANDBOX_ALLOW_FALLBACK') == '1':
                print('SHIRAZ_SANDBOX_WARN linux-landlock-unavailable', file=sys.stderr)
            else:
                print(f'shiraz sandbox: Landlock failed (rc={rc})', file=sys.stderr)
                return 126
        if plan.get('seccomp_deny_network'):
            rc = apply_linux_seccomp_no_network()
            if rc != 0:
                print(f'shiraz sandbox: seccomp network deny failed (rc={rc})', file=sys.stderr)
                return 126

    os.execvp(command[0], command)
    return 127


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
