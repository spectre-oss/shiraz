# SPDX-License-Identifier: GPL-3.0-only
"""Cross-platform per-app isolation and sandboxing policy builder.

Translates a single declarative SandboxPolicy into native host isolation
mechanisms across all three supported desktop platforms:

- Linux:   Landlock LSM filesystem rulesets + optional seccomp network deny.
- macOS:   App Sandbox entitlements (.app bundles) and Seatbelt SBPL profiles.
- Windows: Win32 Job Object limits + CreateRestrictedToken(DISABLE_MAX_PRIVILEGE).
"""

from __future__ import annotations

from dataclasses import dataclass, field
import re
from typing import Any, Dict, List, Optional, Sequence, Tuple

from shiraz.platform_paths import host_os

_PACKAGE_RE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')
_CONTROL_RE = re.compile(r'[\x00-\x1f\x7f]')


@dataclass(frozen=True)
class SandboxPolicy:
    """Declarative per-Android-app host isolation policy."""

    package_name: str
    data_dir: str
    cache_dir: str
    read_only_paths: Tuple[str, ...] = field(default_factory=tuple)
    allow_network: bool = True
    allow_jit: bool = True
    memory_limit_mb: int = 2048
    max_processes: int = 32

    def __post_init__(self) -> None:
        if not _PACKAGE_RE.fullmatch(self.package_name):
            raise ValueError(f'Invalid Android package name: {self.package_name!r}')
        for label, value in (
            ('data_dir', self.data_dir),
            ('cache_dir', self.cache_dir),
            *(('read_only_path', p) for p in self.read_only_paths),
        ):
            if not value or _CONTROL_RE.search(value):
                raise ValueError(f'Invalid {label}: {value!r}')
        if self.memory_limit_mb <= 0:
            raise ValueError(f'memory_limit_mb must be positive: {self.memory_limit_mb}')
        if self.max_processes <= 0:
            raise ValueError(f'max_processes must be positive: {self.max_processes}')


def resolve_sandbox_backend(system: Optional[str] = None) -> str:
    """Return the native isolation backend name for the host OS."""
    os_name = host_os(system)
    if os_name == 'linux':
        return 'landlock-seccomp'
    if os_name == 'darwin':
        return 'seatbelt-app-sandbox'
    return 'job-object-restricted-token'


def _escape_sbpl(value: str) -> str:
    if _CONTROL_RE.search(value):
        raise ValueError(f'Control characters are not permitted in SBPL paths: {value!r}')
    return value.replace('\\', '\\\\').replace('"', '\\"')


def build_linux_landlock_policy(policy: SandboxPolicy) -> Dict[str, Any]:
    """Build Linux Landlock filesystem ruleset and seccomp network policy."""
    read_only = [
        '/usr',
        '/lib',
        '/lib64',
        '/etc/ssl',
        '/etc/fonts',
        *policy.read_only_paths,
    ]
    read_write = [policy.data_dir, policy.cache_dir, '/tmp']
    return {
        'backend': 'landlock-seccomp',
        'no_new_privs': True,
        'read_only_paths': list(dict.fromkeys(read_only)),
        'read_write_paths': list(dict.fromkeys(read_write)),
        'seccomp_deny_network': not policy.allow_network,
        'rlimit_as_bytes': policy.memory_limit_mb * 1024 * 1024,
        'rlimit_nproc': policy.max_processes,
    }


def build_macos_seatbelt_profile(policy: SandboxPolicy) -> str:
    """Generate a macOS Seatbelt (SBPL v1) profile for isolated app execution."""
    lines: List[str] = [
        '(version 1)',
        f';; Shiraz per-app Seatbelt isolation profile for {policy.package_name}',
        '(deny default)',
        '(import "bsd.sb")',
        '(allow process-exec)',
        '(allow process-fork)',
        '(allow signal (target self))',
        '(allow mach-lookup)',
        '(allow sysctl-read)',
        '(allow file-read*',
        '  (subpath "/System")',
        '  (subpath "/usr/lib")',
        '  (subpath "/usr/share")',
        '  (subpath "/Library/Fonts")',
        '  (subpath "/private/var/db/dyld")',
    ]
    for path in dict.fromkeys(policy.read_only_paths):
        lines.append(f'  (subpath "{_escape_sbpl(path)}")')
    lines.append(')')

    lines.extend(
        [
            '(allow file-read* file-write*',
            f'  (subpath "{_escape_sbpl(policy.data_dir)}")',
            f'  (subpath "{_escape_sbpl(policy.cache_dir)}")',
            '  (subpath "/private/tmp")',
            '  (subpath "/tmp")',
            ')',
        ]
    )

    if policy.allow_jit:
        lines.append('(allow process-info-codesignature)')
    if policy.allow_network:
        lines.append('(allow network-outbound network-inbound network-bind)')
    else:
        lines.append('(deny network*)')
    return '\n'.join(lines) + '\n'


def build_macos_entitlements(
    policy: SandboxPolicy,
    *,
    disable_library_validation: bool = False,
) -> Dict[str, bool]:
    """Build macOS App Sandbox + Hardened Runtime entitlements plist dictionary."""
    entitlements: Dict[str, bool] = {
        'com.apple.security.app-sandbox': True,
        'com.apple.security.files.user-selected.read-write': True,
    }
    if policy.allow_jit:
        entitlements['com.apple.security.cs.allow-jit'] = True
    if policy.allow_network:
        entitlements['com.apple.security.network.client'] = True
    if disable_library_validation:
        entitlements['com.apple.security.cs.disable-library-validation'] = True
    return entitlements


def wrap_macos_seatbelt_command(command: Sequence[str], profile_path: str) -> List[str]:
    """Wrap an argv list with /usr/bin/sandbox-exec -f <profile>."""
    if not command:
        raise ValueError('Command must not be empty')
    if not profile_path or _CONTROL_RE.search(profile_path):
        raise ValueError(f'Invalid Seatbelt profile path: {profile_path!r}')
    return ['/usr/bin/sandbox-exec', '-f', profile_path, *command]


def build_windows_sandbox_config(policy: SandboxPolicy) -> Dict[str, Any]:
    """Build Win32 Job Object + Restricted Token isolation parameters."""
    return {
        'backend': 'job-object-restricted-token',
        'job_object': {
            'kill_on_job_close': True,
            'process_memory_limit_bytes': policy.memory_limit_mb * 1024 * 1024,
            'active_process_limit': policy.max_processes,
            'ui_restrictions': [
                'JOB_OBJECT_UILIMIT_DESKTOP',
                'JOB_OBJECT_UILIMIT_DISPLAYSETTINGS',
                'JOB_OBJECT_UILIMIT_EXITWINDOWS',
                'JOB_OBJECT_UILIMIT_GLOBALATOMS',
                'JOB_OBJECT_UILIMIT_SYSTEMPARAMETERS',
            ],
        },
        'restricted_token': {
            'disable_max_privilege': True,
        },
        'writable_directories': [policy.data_dir, policy.cache_dir],
        'allow_network': policy.allow_network,
    }


def sandbox_plan(policy: SandboxPolicy, system: Optional[str] = None) -> Dict[str, Any]:
    """Return the complete host-specific isolation plan for Linux, macOS, or Windows."""
    os_name = host_os(system)
    if os_name == 'linux':
        return build_linux_landlock_policy(policy)
    if os_name == 'darwin':
        return {
            'backend': 'seatbelt-app-sandbox',
            'entitlements': build_macos_entitlements(policy),
            'seatbelt_profile': build_macos_seatbelt_profile(policy),
        }
    return build_windows_sandbox_config(policy)
