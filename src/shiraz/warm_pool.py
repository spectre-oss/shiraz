# SPDX-License-Identifier: GPL-3.0-only
"""Pre-warmed standalone ART standby pool (Zygote-style) and direct process control."""

from __future__ import annotations

import array
import os
import platform
import signal
import socket
import struct
import subprocess
import threading
import time
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from .runtime import (
    DARWIN_FONTCONFIG_FILE,
    DARWIN_JARS,
    DARWIN_LIBRARIES,
    HOST,
    JARS,
    ROOT,
    art_execution_options,
    host_system,
    runtime_inputs,
)


class DirectProcessControl:
    """ProcessControl-compatible wrapper when HostSession owns dalvikvm directly."""

    def __init__(self, pid: int) -> None:
        self.pid = int(pid)
        self.ended = False
        self.group_termination_requested = False

    def reported_pid(self) -> Optional[int]:
        return None if self.ended else self.pid

    def terminate(self, expected_pid: int) -> None:
        if type(expected_pid) is not int or self.reported_pid() != expected_pid:
            raise PermissionError('Process identity unavailable or changed')
        self.group_termination_requested = True
        try:
            os.killpg(self.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except BaseException:
            self.group_termination_requested = False
            raise

    def close(self) -> None:
        self.ended = True


def encode_handoff_payload(
    *,
    data_dir: str,
    app_lib_dir: str,
    cp_apk: str,
    env_pairs: Sequence[Tuple[str, str]],
    prop_pairs: Sequence[Tuple[str, str]],
    activity_args: Sequence[str],
) -> bytes:
    """Pack a length-prefixed binary launch payload for WarmPoolRunner."""
    parts: List[bytes] = []

    def add_str(value: str) -> None:
        raw = value.encode('utf-8')
        parts.append(struct.pack('>I', len(raw)))
        parts.append(raw)

    add_str(data_dir)
    add_str(app_lib_dir)
    add_str(cp_apk)
    parts.append(struct.pack('>I', len(env_pairs)))
    for k, v in env_pairs:
        add_str(k)
        add_str(v)
    parts.append(struct.pack('>I', len(prop_pairs)))
    for k, v in prop_pairs:
        add_str(k)
        add_str(v)
    parts.append(struct.pack('>I', len(activity_args)))
    for arg in activity_args:
        add_str(arg)
    return b''.join(parts)


def warm_pool_available(root: Path = ROOT, system: Optional[str] = None) -> bool:
    os_name = host_system() if system is None else system
    if os_name == 'Linux':
        lib = root / 'build/libshiraz-warm-pool.so'
    elif os_name == 'Darwin':
        lib = root / DARWIN_LIBRARIES / 'libshiraz-warm-pool.so'
    else:
        return False
    return lib.is_file() and (
        (root / 'build/shiraz-warm-pool.jar').is_file()
        or (root / 'build/shiraz-framework.jar').is_file()
    )


def build_standby_command(
    *,
    root: Path = ROOT,
    host: Path = HOST,
    aot_dir: Optional[Path] = None,
    environ: Optional[Dict[str, str]] = None,
    control_fd: int,
    system: Optional[str] = None,
) -> Tuple[List[str], Dict[str, str]]:
    """Build the command and base env for one idle pre-warmed standby dalvikvm."""
    env_in = dict(os.environ if environ is None else environ)
    os_name = host_system() if system is None else system
    darwin = os_name == 'Darwin'
    execution_mode = env_in.get('SHIRAZ_ART_MODE', 'interpreter' if darwin else 'jit')
    if darwin:
        execution_mode = 'interpreter'
    execution_options = art_execution_options(execution_mode)
    vm, framework, framework_resources, _, _ = runtime_inputs(os_name, root, host, execution_mode)
    boot = ':'.join(str(host / 'framework' / name) for name in (DARWIN_JARS if darwin else JARS))
    local_lib_dir = env_in.get('SHIRAZ_LOCAL_LIB_DIR', '').strip() or str(root / '.local/lib')
    native_lib_dir = str(root / DARWIN_LIBRARIES) if darwin else str(root / 'build')

    standby_work = root / ('build/macos/warm-pool' if darwin else 'build/warm-pool')
    for sub in ('android-data/dalvik-cache', 'cache', 'tmp', 'home'):
        (standby_work / sub).mkdir(parents=True, exist_ok=True)

    env: Dict[str, str] = {
        **env_in,
        'ANDROID_ROOT': str(host),
        'ANDROID_DATA': str(standby_work / 'android-data'),
        'XDG_CACHE_HOME': str(standby_work / 'cache'),
        'BOOTCLASSPATH': boot,
        'SHIRAZ_FONT_DIR': str(root / '.local/share/shiraz/fonts'),
        'SHIRAZ_ANDROID_FONT_DIR': str(root / '.local/share/shiraz/fonts'),
        'XDG_DATA_DIRS': f'{root / ".local/share"}:/usr/share',
    }
    if darwin:
        env.pop('LD_PRELOAD', None)
        env.pop('DYLD_INSERT_LIBRARIES', None)
        fc_file = root / DARWIN_FONTCONFIG_FILE
        env['FONTCONFIG_FILE'] = str(fc_file)
        env['FONTCONFIG_PATH'] = str(fc_file.parent)
        angle_egl = root / DARWIN_LIBRARIES / 'libEGL.dylib'
        if angle_egl.is_file() and not env.get('SHIRAZ_EGL_LIBRARY'):
            env['SHIRAZ_EGL_LIBRARY'] = str(angle_egl)
        egl_shim = root / DARWIN_LIBRARIES / 'libEGL_shim.so'
        if egl_shim.is_file():
            env.setdefault('SHIRAZ_GUEST_EGL_SHIM', str(egl_shim))
        molten = root / '.local/lib/libMoltenVK.dylib'
        if (
            molten.is_file()
            and env.get('SHIRAZ_VULKAN_DISABLE') != '1'
            and not env.get('SHIRAZ_VULKAN_ICD')
        ):
            env['SHIRAZ_VULKAN_ICD'] = str(molten.resolve())
        env.setdefault('SHIRAZ_GUEST_ANDROID_LIB_DIR', native_lib_dir)
    else:
        env['LD_LIBRARY_PATH'] = f'{host / "lib64"}:{local_lib_dir}:{native_lib_dir}'
    for key in ('SDL_VIDEODRIVER', 'SDL_RENDER_DRIVER'):
        if key in env:
            env.setdefault('SHIRAZ_HOST_' + key, env.pop(key))
    for strip_key in (
        'SHIRAZ_BROKER_FD',
        'SHIRAZ_PACKAGE_UID',
        'SHIRAZ_SERVICE_CALLER_UID',
        'SHIRAZ_ACTIVITY_FD',
        'SHIRAZ_ACTIVITY_CALLER_UID',
        'SHIRAZ_PROCESS_CONTROL_FD',
    ):
        env.pop(strip_key, None)

    if not darwin and not env.get('SHIRAZ_AUDIO_DEVICE'):
        xdg_rt = env.get('XDG_RUNTIME_DIR', '')
        if xdg_rt and (Path(xdg_rt) / 'pipewire-0').is_socket():
            for plugin_dir in (
                Path('/usr/lib/x86_64-linux-gnu/alsa-lib'),
                Path('/usr/lib64/alsa-lib'),
                Path('/usr/lib/alsa-lib'),
                root / '.local/lib/alsa-lib',
            ):
                if (plugin_dir / 'libasound_module_pcm_pipewire.so').is_file():
                    env['SHIRAZ_AUDIO_DEVICE'] = 'pipewire'
                    break

    if not darwin:
        platform_libraries = (
            root / 'build/libshiraz-android-process.so',
            root / 'build/libandroid.so',
            root / 'build/libshiraz-resources.so',
            root / 'build/libshiraz-android-fonts.so',
        )
        env['LD_PRELOAD'] = ':'.join(map(str, platform_libraries)) + (
            ':' + env['LD_PRELOAD'] if env.get('LD_PRELOAD') else ''
        )

    cp_framework = framework
    aot_args: List[str] = []
    aot_root_str = str(aot_dir) if aot_dir is not None else env_in.get('SHIRAZ_AOT_DIR', '').strip()
    if aot_root_str and '-Xint' not in execution_options:
        isa = {'x86_64': 'x86_64', 'aarch64': 'arm64', 'arm64': 'arm64'}.get(platform.machine())
        aot_path = Path(aot_root_str)
        boot_art_dir = aot_path / 'boot'
        if isa and (boot_art_dir / isa / 'boot.art').is_file():
            logical_boot = ':'.join('/shiraz/boot/' + name for name in JARS)
            aot_args = [
                '-Xbootclasspath-locations:' + logical_boot,
                '-Ximage:' + str(boot_art_dir / 'boot.art'),
            ]
        stage = aot_path / 'stage'
        if isa:
            staged_fw = stage / 'shiraz-framework.jar'
            if (
                staged_fw.exists()
                and (stage / 'oat' / isa / 'shiraz-framework.odex').is_file()
                and staged_fw.resolve() == framework.resolve()
            ):
                cp_framework = staged_fw

    gc_args: List[str] = []
    if env_in.get('SHIRAZ_FAST_GC', '1') == '1':
        heap_mb = env_in.get('SHIRAZ_ART_HEAP_MB', '512')
        if heap_mb.isdigit() and 128 <= int(heap_mb) <= 1024:
            gc_args.append('-Xmx' + heap_mb + 'm')
        gc_args.extend(['-Xgc:CMS', '-XX:BackgroundGC=CMS', '-XX:DisableHSpaceCompactForOOM'])
    check_jni_args = [] if env_in.get('SHIRAZ_CHECK_JNI', '0') == '0' else ['-Xcheck:jni']
    trace_aot_args = ['-verbose:oat'] if env_in.get('SHIRAZ_TRACE_AOT') == '1' else []
    trust_store = (
        ['-Djavax.net.ssl.trustStore=' + env_in['SHIRAZ_JAVA_TRUST_STORE']]
        if env_in.get('SHIRAZ_JAVA_TRUST_STORE')
        else []
    )
    warm_jar = root / 'build/shiraz-warm-pool.jar'
    classpath = f'{cp_framework}:{warm_jar}' if warm_jar.is_file() else str(cp_framework)

    compiler_args = [] if darwin else ['-Xcompiler:' + str(host / 'bin/dex2oat')]
    bridge_args: List[str] = []
    if darwin and env_in.get('SHIRAZ_GUEST_LOADER') == '1':
        guest_bridge = root / DARWIN_LIBRARIES / 'libshiraz-guest.so'
        if guest_bridge.is_file():
            bridge_args.append('-XX:NativeBridge=' + str(guest_bridge.resolve()))

    command: List[str] = [
        str(vm),
        *gc_args,
        '-Xbootclasspath:' + boot,
        '-Xnoimage-dex2oat',
        *aot_args,
        *execution_options,
        *check_jni_args,
        *trace_aot_args,
        *compiler_args,
        *bridge_args,
        *trust_store,
        '-Djava.library.path=' + f'{host / "lib64"}:{native_lib_dir}',
        '-Dshiraz.frameworkResources=' + str(framework_resources),
        '-Dshiraz.speechData=' + str(root / '.local/share'),
        '-cp',
        classpath,
        'shiraz.runtime.WarmPoolRunner',
        str(control_fd),
        # Trailing marker so ServiceProcess.configure() recognizes the ART command prefix.
        'shiraz.runtime.ActivityRunner',
    ]
    if not darwin and (loader := env_in.get('SHIRAZ_RUNTIME_LOADER')):
        library_path = env.pop('LD_LIBRARY_PATH') + ':' + str(Path(loader).parent)
        if prepend := env_in.get('SHIRAZ_PREPEND_LIBRARY_PATH', '').strip():
            library_path = prepend + ':' + library_path
        preload = env.pop('LD_PRELOAD', '')
        command = [
            loader,
            '--library-path',
            library_path,
            *(['--preload', preload] if preload else []),
            *command,
        ]
    return command, env


class WarmArtPool:
    """Maintains 1 pre-booted idle dalvikvm standby for near-instant app launches."""

    def __init__(
        self,
        *,
        root: Path = ROOT,
        host: Path = HOST,
        aot_dir: Optional[Path] = None,
        replenish_delay: float = 2.0,
    ) -> None:
        self.root = Path(root)
        self.host = Path(host)
        self.aot_dir = Path(aot_dir) if aot_dir is not None else None
        self.replenish_delay = max(0.0, float(replenish_delay))
        self._lock = threading.Lock()
        self._closed = False
        self._proc: Optional[subprocess.Popen] = None
        self._sock: Optional[socket.socket] = None
        self._ready = False
        self._spawning = False

    @property
    def ready(self) -> bool:
        with self._lock:
            return (
                not self._closed
                and self._ready
                and self._proc is not None
                and self._proc.poll() is None
                and self._sock is not None
            )

    def ensure_standby_async(self, *, delay: float = 0.0) -> None:
        if not warm_pool_available(self.root):
            return
        with self._lock:
            if self._closed or self._spawning:
                return
            if self._proc is not None and self._proc.poll() is None:
                return
            self._spawning = True

        def worker() -> None:
            try:
                if delay > 0.0:
                    deadline = time.monotonic() + delay
                    while time.monotonic() < deadline:
                        with self._lock:
                            if self._closed:
                                return
                        time.sleep(min(0.1, max(0.0, deadline - time.monotonic())))
                self._spawn_standby()
            finally:
                with self._lock:
                    self._spawning = False

        threading.Thread(target=worker, name='shiraz-warm-art-pool', daemon=True).start()

    def _spawn_standby(self) -> None:
        with self._lock:
            if self._closed:
                return
            if self._proc is not None and self._proc.poll() is None:
                return
        sock_type = (
            socket.SOCK_SEQPACKET
            if host_system() == 'Linux' and hasattr(socket, 'SOCK_SEQPACKET')
            else socket.SOCK_STREAM
        )
        parent_sock, child_sock = socket.socketpair(socket.AF_UNIX, sock_type)
        proc: Optional[subprocess.Popen] = None
        try:
            command, env = build_standby_command(
                root=self.root,
                host=self.host,
                aot_dir=self.aot_dir,
                control_fd=child_sock.fileno(),
            )
            darwin = host_system() == 'Darwin'
            standby_log = self.root / (
                'build/macos/warm-pool/standby.log' if darwin else 'build/warm-pool/standby.log'
            )
            standby_log.parent.mkdir(parents=True, exist_ok=True)
            with child_sock, standby_log.open('wb') as log_out:
                proc = subprocess.Popen(
                    command,
                    cwd=self.root,
                    env=env,
                    stdin=subprocess.DEVNULL,
                    stdout=log_out,
                    stderr=log_out,
                    pass_fds=(child_sock.fileno(),),
                    start_new_session=True,
                )
            parent_sock.settimeout(12.0 if darwin else 8.0)
            banner = parent_sock.recv(64)
            if banner != b'READY\n' or proc.poll() is not None:
                raise RuntimeError(f'Warm standby failed readiness handshake: {banner!r}')
            parent_sock.settimeout(2.0)
            with self._lock:
                if self._closed:
                    raise RuntimeError('Warm pool closed during standby spawn')
                self._proc = proc
                self._sock = parent_sock
                self._ready = True
                proc = None
                parent_sock = None
        except BaseException:
            if parent_sock is not None:
                try:
                    parent_sock.close()
                except OSError:
                    pass
            if proc is not None and proc.poll() is None:
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                try:
                    proc.wait(timeout=1.0)
                except subprocess.TimeoutExpired:
                    pass

    def claim_and_dispatch(
        self,
        *,
        data_dir: Path,
        app_lib_dir: Path,
        cp_apk: Path,
        env_pairs: Sequence[Tuple[str, str]],
        prop_pairs: Sequence[Tuple[str, str]],
        activity_args: Sequence[str],
        broker_fd: int,
        stdout_fd: int,
        stderr_fd: int,
    ) -> Optional[subprocess.Popen]:
        """Hand a launch directly to the warm standby dalvikvm, or return None if unavailable."""
        with self._lock:
            if (
                self._closed
                or not self._ready
                or self._proc is None
                or self._sock is None
                or self._proc.poll() is not None
            ):
                return None
            proc = self._proc
            sock = self._sock
            self._proc = None
            self._sock = None
            self._ready = False

        payload = encode_handoff_payload(
            data_dir=str(data_dir),
            app_lib_dir=str(app_lib_dir),
            cp_apk=str(cp_apk),
            env_pairs=env_pairs,
            prop_pairs=prop_pairs,
            activity_args=activity_args,
        )
        try:
            fds = array.array('i', [int(broker_fd), int(stdout_fd), int(stderr_fd)])
            sock.sendmsg([payload], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, fds)])
        except OSError:
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            try:
                proc.wait(timeout=1.0)
            except subprocess.TimeoutExpired:
                pass
            self.ensure_standby_async(delay=self.replenish_delay)
            return None
        finally:
            try:
                sock.close()
            except OSError:
                pass

        # Replenish the standby after a short delay so the newly launched app gets full CPU first.
        self.ensure_standby_async(delay=self.replenish_delay)
        return proc

    def close(self) -> None:
        with self._lock:
            self._closed = True
            proc = self._proc
            sock = self._sock
            self._proc = None
            self._sock = None
            self._ready = False
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
        if proc is not None and proc.poll() is None:
            try:
                os.killpg(proc.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                proc.wait(timeout=1.0)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                try:
                    proc.wait(timeout=1.0)
                except subprocess.TimeoutExpired:
                    pass
