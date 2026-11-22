# SPDX-License-Identifier: GPL-3.0-only
"""Native APK ABI selection and page-alignment policy for Linux, macOS, and Windows hosts."""

import os
import platform


_ABI_MAP = {
    'x86_64': 'x86_64',
    'amd64': 'x86_64',
    'x64': 'x86_64',
    'aarch64': 'arm64-v8a',
    'arm64': 'arm64-v8a',
}


def native_abi(machine=None):
    """Map host CPU architecture to the matching Android native ABI.

    Linux reports x86_64/aarch64; macOS on Apple Silicon reports arm64;
    Windows reports AMD64/ARM64.
    """
    raw = platform.machine() if machine is None else machine
    if not isinstance(raw, str):
        return None
    return _ABI_MAP.get(raw.strip().lower())


def supports_native_abis(abis, machine=None):
    return not abis or native_abi(machine) in abis


def host_page_size(system=None, machine=None):
    """Return the host virtual memory page size in bytes.

    Apple Silicon macOS uses 16 KiB pages; Windows and standard x86_64 Linux use
    4 KiB pages. When inspecting the active host, queries `os.sysconf` if
    available.
    """
    if system is None and machine is None and hasattr(os, 'sysconf'):
        try:
            size = int(os.sysconf('SC_PAGESIZE'))
            if size > 0:
                return size
        except (ValueError, OSError):
            pass
    sys_name = (platform.system() if system is None else system).strip().lower()
    abi = native_abi(machine)
    if sys_name == 'darwin' and abi == 'arm64-v8a':
        return 16384
    return 4096


def elf_needs_copy_load(p_align, page_size=None):
    """Return True if an ELF PT_LOAD alignment requires copy-loading on the host.

    Android arm64-v8a `.so` files built with 4 KiB (`p_align == 4096`) segments
    cannot be file-backed `mmap`'d directly on 16 KiB hosts (Apple Silicon macOS
    or 16 KiB Linux kernels) and must be copy-loaded into anonymous memory.
    """
    host_size = host_page_size() if page_size is None else int(page_size)
    if host_size <= 0:
        raise ValueError(f'Invalid host page size: {host_size}')
    if p_align is None or int(p_align) <= 0:
        return True
    return int(p_align) < host_size
