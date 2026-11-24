# SPDX-License-Identifier: GPL-3.0-only
"""Cross-platform per-app isolation and sandboxing policy builder & launcher.

Translates a single declarative SandboxPolicy into native host isolation
mechanisms across all three supported desktop platforms:

- Linux:   Landlock LSM filesystem rulesets + optional seccomp network deny.
- macOS:   App Sandbox entitlements (.app bundles) and Seatbelt SBPL profiles.
- Windows: Win32 Job Object limits + CreateRestrictedToken(DISABLE_MAX_PRIVILEGE).
"""

from __future__ import annotations

from contextlib import contextmanager
import ctypes
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import re
import sys
from typing import Any, Callable, Dict, Iterator, List, Optional, Sequence, Tuple

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
    extra_read_write_paths: Tuple[str, ...] = field(default_factory=tuple)
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
            *(('extra_read_write_path', p) for p in self.extra_read_write_paths),
        ):
            if not value or _CONTROL_RE.search(value):
                raise ValueError(f'Invalid {label}: {value!r}')
        if self.memory_limit_mb <= 0:
            raise ValueError(f'memory_limit_mb must be positive: {self.memory_limit_mb}')
        if self.max_processes <= 0:
            raise ValueError(f'max_processes must be positive: {self.max_processes}')


@dataclass(frozen=True)
class SandboxLaunch:
    """Prepared per-host sandbox execution plan for a child process."""

    backend: str
    command: List[str]
    env_updates: Dict[str, str]
    plan_path: Path
    profile_path: Optional[Path] = None
    process_observer: Optional[Callable[[Any], Any]] = None


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
        '/bin',
        '/etc',
        '/etc/ssl',
        '/etc/fonts',
        '/proc',
        '/sys',
        '/dev',
        *policy.read_only_paths,
    ]
    read_write = [
        policy.data_dir,
        policy.cache_dir,
        *policy.extra_read_write_paths,
        '/tmp',
        '/dev/shm',
    ]
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
        '(allow iokit-open)',
        '(allow ipc-posix-shm)',
        '(allow sysctl-read)',
        '(allow file-read-metadata)',
        '(allow file-read*',
        '  (subpath "/System")',
        '  (subpath "/usr/lib")',
        '  (subpath "/usr/share")',
        '  (subpath "/Library/Fonts")',
        '  (subpath "/Library/Keychains")',
        '  (subpath "/Library/Preferences")',
        '  (subpath "/Library/Security")',
        '  (subpath "/private/var/db/dyld")',
        '  (subpath "/private/var/db/mds")',
        '  (subpath "/private/var/db/crls")',
        '  (subpath "/private/var/db/timezone")',
        '  (subpath "/dev")',
    ]
    for path in dict.fromkeys(policy.read_only_paths):
        lines.append(f'  (subpath "{_escape_sbpl(path)}")')
    lines.append(')')

    rw_paths = list(
        dict.fromkeys([policy.data_dir, policy.cache_dir, *policy.extra_read_write_paths])
    )
    lines.append('(allow file-read* file-write*')
    for path in rw_paths:
        lines.append(f'  (subpath "{_escape_sbpl(path)}")')
    lines.extend(
        [
            '  (literal "/dev/null")',
            '  (literal "/dev/zero")',
            '  (literal "/dev/dtracehelper")',
            '  (subpath "/dev/fd")',
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
    rw_paths = list(
        dict.fromkeys([policy.data_dir, policy.cache_dir, *policy.extra_read_write_paths])
    )
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
        'writable_directories': rw_paths,
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


# ---------------------------------------------------------------------------
# Live Windows Win32 Job Object enforcement (ctypes, zero external deps)
# ---------------------------------------------------------------------------

_JOB_OBJECT_LIMIT_ACTIVE_PROCESS = 0x00000008
_JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x00000100
_JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000

_JOB_OBJECT_INFO_BASIC_UI_RESTRICTIONS = 4
_JOB_OBJECT_INFO_EXTENDED_LIMIT = 9

_UI_LIMIT_FLAGS = {
    'JOB_OBJECT_UILIMIT_HANDLES': 0x00000001,
    'JOB_OBJECT_UILIMIT_READCLIPBOARD': 0x00000002,
    'JOB_OBJECT_UILIMIT_WRITECLIPBOARD': 0x00000004,
    'JOB_OBJECT_UILIMIT_SYSTEMPARAMETERS': 0x00000008,
    'JOB_OBJECT_UILIMIT_DISPLAYSETTINGS': 0x00000010,
    'JOB_OBJECT_UILIMIT_GLOBALATOMS': 0x00000020,
    'JOB_OBJECT_UILIMIT_DESKTOP': 0x00000040,
    'JOB_OBJECT_UILIMIT_EXITWINDOWS': 0x00000080,
}


class _IoCounters(ctypes.Structure):
    _fields_ = [
        ('ReadOperationCount', ctypes.c_uint64),
        ('WriteOperationCount', ctypes.c_uint64),
        ('OtherOperationCount', ctypes.c_uint64),
        ('ReadTransferCount', ctypes.c_uint64),
        ('WriteTransferCount', ctypes.c_uint64),
        ('OtherTransferCount', ctypes.c_uint64),
    ]


class _JobObjectBasicLimitInfo(ctypes.Structure):
    _fields_ = [
        ('PerProcessUserTimeLimit', ctypes.c_int64),
        ('PerJobUserTimeLimit', ctypes.c_int64),
        ('LimitFlags', ctypes.c_uint32),
        ('MinimumWorkingSetSize', ctypes.c_size_t),
        ('MaximumWorkingSetSize', ctypes.c_size_t),
        ('ActiveProcessLimit', ctypes.c_uint32),
        ('Affinity', ctypes.c_size_t),
        ('PriorityClass', ctypes.c_uint32),
        ('SchedulingClass', ctypes.c_uint32),
    ]


class _JobObjectExtendedLimitInfo(ctypes.Structure):
    _fields_ = [
        ('BasicLimitInformation', _JobObjectBasicLimitInfo),
        ('IoInfo', _IoCounters),
        ('ProcessMemoryLimit', ctypes.c_size_t),
        ('JobMemoryLimit', ctypes.c_size_t),
        ('PeakProcessMemoryUsed', ctypes.c_size_t),
        ('PeakJobMemoryUsed', ctypes.c_size_t),
    ]


class _JobObjectBasicUiRestrictions(ctypes.Structure):
    _fields_ = [('UIRestrictionsClass', ctypes.c_uint32)]


def is_process_in_windows_job(process: Any, job_handle: int) -> bool:
    """Return True if *process* is currently assigned to *job_handle* on Windows."""
    if sys.platform != 'win32':
        return False
    kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel32.IsProcessInJob.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_int),
    ]
    kernel32.IsProcessInJob.restype = ctypes.c_int
    proc_handle = getattr(process, '_handle', None)
    if not proc_handle:
        return False
    result = ctypes.c_int(0)
    if not kernel32.IsProcessInJob(
        ctypes.c_void_p(int(proc_handle)), ctypes.c_void_p(job_handle), ctypes.byref(result)
    ):
        return False
    return bool(result.value)


def windows_sandbox_observer(config: Dict[str, Any]) -> Callable[[Any], Any]:
    """Return a process_observer context manager enforcing Win32 Job Object limits."""
    job_cfg = config.get('job_object', {})

    @contextmanager
    def _observe(process: Any) -> Iterator[int]:
        if sys.platform != 'win32':
            yield 0
            return
        kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p]
        kernel32.CreateJobObjectW.restype = ctypes.c_void_p
        kernel32.SetInformationJobObject.argtypes = [
            ctypes.c_void_p,
            ctypes.c_int,
            ctypes.c_void_p,
            ctypes.c_uint32,
        ]
        kernel32.SetInformationJobObject.restype = ctypes.c_int
        kernel32.AssignProcessToJobObject.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        kernel32.AssignProcessToJobObject.restype = ctypes.c_int
        kernel32.CloseHandle.argtypes = [ctypes.c_void_p]
        kernel32.CloseHandle.restype = ctypes.c_int

        job = kernel32.CreateJobObjectW(None, None)
        if not job:
            raise OSError(ctypes.get_last_error(), 'CreateJobObjectW failed')
        try:
            ext = _JobObjectExtendedLimitInfo()
            flags = 0
            if job_cfg.get('kill_on_job_close', True):
                flags |= _JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            max_procs = int(job_cfg.get('active_process_limit', 0))
            if max_procs > 0:
                flags |= _JOB_OBJECT_LIMIT_ACTIVE_PROCESS
                ext.BasicLimitInformation.ActiveProcessLimit = max_procs
            mem_limit = int(job_cfg.get('process_memory_limit_bytes', 0))
            if mem_limit > 0:
                flags |= _JOB_OBJECT_LIMIT_PROCESS_MEMORY
                ext.ProcessMemoryLimit = mem_limit
            ext.BasicLimitInformation.LimitFlags = flags
            if not kernel32.SetInformationJobObject(
                job,
                _JOB_OBJECT_INFO_EXTENDED_LIMIT,
                ctypes.byref(ext),
                ctypes.sizeof(ext),
            ):
                raise OSError(
                    ctypes.get_last_error(), 'SetInformationJobObject(ExtendedLimit) failed'
                )

            ui_mask = 0
            for flag_name in job_cfg.get('ui_restrictions', ()):
                ui_mask |= _UI_LIMIT_FLAGS.get(flag_name, 0)
            if ui_mask:
                ui = _JobObjectBasicUiRestrictions(UIRestrictionsClass=ui_mask)
                if not kernel32.SetInformationJobObject(
                    job,
                    _JOB_OBJECT_INFO_BASIC_UI_RESTRICTIONS,
                    ctypes.byref(ui),
                    ctypes.sizeof(ui),
                ):
                    raise OSError(
                        ctypes.get_last_error(), 'SetInformationJobObject(UIRestrictions) failed'
                    )

            proc_handle = getattr(process, '_handle', None)
            if not proc_handle:
                raise RuntimeError(
                    'Child process handle unavailable for Win32 Job Object assignment'
                )
            if not kernel32.AssignProcessToJobObject(job, ctypes.c_void_p(int(proc_handle))):
                raise OSError(ctypes.get_last_error(), 'AssignProcessToJobObject failed')
            yield int(job)
        finally:
            kernel32.CloseHandle(job)

    return _observe


def prepare_sandbox_launch(
    policy: SandboxPolicy,
    command: Sequence[str],
    work_dir: Path,
    *,
    system: Optional[str] = None,
    sandbox_exec_path: str = '/usr/bin/sandbox-exec',
    python_executable: Optional[str] = None,
) -> SandboxLaunch:
    """Materialize host sandbox artifacts under *work_dir* and wrap *command*."""
    if not command:
        raise ValueError('Command must not be empty')
    work = Path(work_dir)
    work.mkdir(parents=True, exist_ok=True)
    os_name = host_os(system)
    plan = sandbox_plan(policy, system=system)
    plan_path = work / 'sandbox-plan.json'
    plan_path.write_text(json.dumps(plan, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    env_updates = {
        'SHIRAZ_SANDBOX_BACKEND': plan['backend'],
        'SHIRAZ_SANDBOX_NETWORK': '1' if policy.allow_network else '0',
    }

    if os_name == 'darwin':
        if system is None and not os.path.isfile(sandbox_exec_path):
            raise RuntimeError(f'macOS Seatbelt binary not found: {sandbox_exec_path}')
        profile_path = work / 'sandbox.sb'
        profile_path.write_text(plan['seatbelt_profile'], encoding='utf-8')
        wrapped = [sandbox_exec_path, '-f', str(profile_path), *command]
        return SandboxLaunch(
            backend=plan['backend'],
            command=wrapped,
            env_updates=env_updates,
            plan_path=plan_path,
            profile_path=profile_path,
        )

    if os_name == 'linux':
        py = python_executable or sys.executable
        helper = str(Path(__file__).with_name('sandbox_exec.py'))
        wrapped = [py, helper, str(plan_path), '--', *command]
        return SandboxLaunch(
            backend=plan['backend'],
            command=wrapped,
            env_updates=env_updates,
            plan_path=plan_path,
        )

    return SandboxLaunch(
        backend=plan['backend'],
        command=list(command),
        env_updates=env_updates,
        plan_path=plan_path,
        process_observer=windows_sandbox_observer(plan),
    )
