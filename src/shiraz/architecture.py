# SPDX-License-Identifier: GPL-3.0-only
"""Native APK ABI selection for supported Linux and macOS hosts."""
import platform


def native_abi(machine=None):
    # Linux reports aarch64; macOS on Apple silicon reports arm64.
    return {'x86_64': 'x86_64', 'aarch64': 'arm64-v8a', 'arm64': 'arm64-v8a'}.get(
        platform.machine() if machine is None else machine)


def supports_native_abis(abis, machine=None):
    return not abis or native_abi(machine) in abis
