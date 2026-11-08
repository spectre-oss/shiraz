# SPDX-License-Identifier: GPL-3.0-only
"""Host-specific output layout of the art_standalone AOSP make build."""
import hashlib
import os
import platform
from pathlib import Path


def host_tag(system=None):
    """AOSP HOST_PREBUILT_TAG of the build host.

    The darwin-arm64 combo keeps the historical darwin-x86 tag, exactly as
    linux-arm64 hosts keep linux-x86, so the make rules stay unchanged.
    """
    return 'darwin-x86' if (system or platform.system()) == 'Darwin' else 'linux-x86'


def host_out_dir(tree=None, system=None):
    """out/host/<tag>, relative, or below the given art_standalone tree."""
    relative = Path('out/host') / host_tag(system)
    return Path(tree) / relative if tree is not None else relative


def darwin_socket_directory(root):
    """Keep the 32-hex emulated abstract socket name below Darwin's 104-byte limit.

    Short checkouts retain their namespace. Long roots use a deterministic
    per-UID/per-checkout directory; LocalSocket creates it with mode 0700 and
    rejects symlinks, other owners and unsafe permissions before binding.
    This selects a path only and never creates or changes a host directory.
    """
    root = Path(root).resolve()
    preferred = root / '.local/sockets'
    if len(os.fsencode(preferred)) + 1 + 32 < 104:
        return preferred
    digest = hashlib.sha256(os.fsencode(root)).hexdigest()[:16]
    return Path('/tmp') / f'shiraz-local-{os.getuid()}-{digest}'
