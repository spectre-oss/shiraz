# SPDX-License-Identifier: GPL-3.0-only
"""Host-specific output layout of the art_standalone AOSP make build."""
from pathlib import Path
import platform


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
