# SPDX-License-Identifier: GPL-3.0-only
"""Persistent XDG AOT compilation cache and memory pre-warmer for Shiraz.

Compiles relocatable ART boot images (`boot.art`/`boot.oat`/`boot.vdex`),
`shiraz-framework.odex`, and per-APK `<sha256>.odex` into
`$XDG_CACHE_HOME/shiraz/aot/` without ever modifying APKs or the checkout tree.
"""

from __future__ import annotations

import fcntl
import hashlib
import json
import os
import platform
import shutil
import subprocess
from contextlib import contextmanager
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from .platform_paths import cache_home
from .runtime import BUNDLED_FONTS, HOST, JARS, ROOT

_ISA_MAP = {'x86_64': 'x86_64', 'aarch64': 'arm64', 'arm64': 'arm64'}


def art_isa() -> str:
    machine = platform.machine()
    isa = _ISA_MAP.get(machine)
    if isa is None:
        raise RuntimeError(f'Unsupported architecture for ART AOT: {machine}')
    return isa


def aot_cache_root(env: Optional[Dict[str, str]] = None) -> Path:
    return Path(cache_home(env=env, system='Linux')) / 'aot'


_SHA_CACHE: Dict[Tuple[str, int, int], str] = {}


def _sha256_file(path: Path) -> str:
    st = path.stat()
    key = (str(path), st.st_size, st.st_mtime_ns)
    cached = _SHA_CACHE.get(key)
    if cached is not None:
        return cached
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    digest = h.hexdigest()
    _SHA_CACHE[key] = digest
    return digest


def _file_sig(path: Path) -> Dict[str, object]:
    st = path.stat()
    return {'path': str(path), 'size': st.st_size, 'mtime_ns': st.st_mtime_ns}


def _boot_fingerprint(host: Path, isa: str) -> Dict[str, object]:
    compiler = host / 'bin/dex2oat'
    return {
        'version': 1,
        'isa': isa,
        'compiler': _file_sig(compiler),
        'jars': {name: _file_sig(host / 'framework' / name) for name in JARS},
    }


def _framework_fingerprint(root: Path, boot_fp: Dict[str, object]) -> Dict[str, object]:
    fw = root / 'build/shiraz-framework.jar'
    return {
        'version': 1,
        'boot_hash': hashlib.sha256(
            json.dumps(boot_fp, sort_keys=True).encode('utf-8')
        ).hexdigest(),
        'framework_sha256': _sha256_file(fw),
    }


def _verify_elf(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 16:
        return False
    try:
        with path.open('rb') as stream:
            return stream.read(4) == b'\x7fELF'
    except OSError:
        return False


def _expected_boot_files(boot_dir: Path, isa: str) -> List[Path]:
    images = boot_dir / isa
    out: List[Path] = []
    for index, name in enumerate(JARS):
        stem = 'boot' if index == 0 else 'boot-' + Path(name).stem
        for suffix in ('.art', '.oat', '.vdex'):
            out.append(images / (stem + suffix))
    return out


def boot_image_ready(
    cache_root: Optional[Path] = None,
    *,
    host: Path = HOST,
) -> bool:
    try:
        isa = art_isa()
        cache = Path(cache_root) if cache_root is not None else aot_cache_root()
        boot_dir = cache / 'boot'
        marker = boot_dir / 'fingerprint.json'
        if not marker.is_file():
            return False
        stored = json.loads(marker.read_text(encoding='utf-8'))
        if stored != _boot_fingerprint(host, isa):
            return False
        for path in _expected_boot_files(boot_dir, isa):
            if not path.is_file() or path.stat().st_size == 0:
                return False
            if path.suffix == '.oat' and not _verify_elf(path):
                return False
        return True
    except (OSError, ValueError, RuntimeError):
        return False


def framework_aot_ready(
    cache_root: Optional[Path] = None,
    *,
    root: Path = ROOT,
    host: Path = HOST,
) -> bool:
    try:
        if not boot_image_ready(cache_root, host=host):
            return False
        isa = art_isa()
        cache = Path(cache_root) if cache_root is not None else aot_cache_root()
        stage = cache / 'stage'
        marker = stage / 'framework.fingerprint.json'
        if not marker.is_file():
            return False
        boot_fp = _boot_fingerprint(host, isa)
        stored = json.loads(marker.read_text(encoding='utf-8'))
        if stored != _framework_fingerprint(root, boot_fp):
            return False
        fw_link = stage / 'shiraz-framework.jar'
        odex = stage / 'oat' / isa / 'shiraz-framework.odex'
        vdex = stage / 'oat' / isa / 'shiraz-framework.vdex'
        return (
            fw_link.exists()
            and fw_link.resolve() == (root / 'build/shiraz-framework.jar').resolve()
            and _verify_elf(odex)
            and vdex.is_file()
            and vdex.stat().st_size > 0
        )
    except (OSError, ValueError, RuntimeError):
        return False


def app_aot_ready(
    apk_sha256: str,
    cache_root: Optional[Path] = None,
    *,
    root: Path = ROOT,
    host: Path = HOST,
) -> bool:
    try:
        if not framework_aot_ready(cache_root, root=root, host=host):
            return False
        isa = art_isa()
        cache = Path(cache_root) if cache_root is not None else aot_cache_root()
        stage = cache / 'stage'
        marker = stage / f'{apk_sha256}.fingerprint.json'
        if not marker.is_file():
            return False
        fw_fp = json.loads((stage / 'framework.fingerprint.json').read_text(encoding='utf-8'))
        stored = json.loads(marker.read_text(encoding='utf-8'))
        expected_fw_hash = hashlib.sha256(
            json.dumps(fw_fp, sort_keys=True).encode('utf-8')
        ).hexdigest()
        if (
            stored.get('apk_sha256') != apk_sha256
            or stored.get('framework_hash') != expected_fw_hash
        ):
            return False
        odex = stage / 'oat' / isa / f'{apk_sha256}.odex'
        vdex = stage / 'oat' / isa / f'{apk_sha256}.vdex'
        apk_link = stage / f'{apk_sha256}.apk'
        return (
            apk_link.exists() and _verify_elf(odex) and vdex.is_file() and vdex.stat().st_size > 0
        )
    except (OSError, ValueError, RuntimeError):
        return False


@contextmanager
def _aot_lock(cache: Path):
    cache.mkdir(parents=True, exist_ok=True, mode=0o700)
    lock_path = cache / '.aot.lock'
    with lock_path.open('a') as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        yield


def _dex2oat_env(work: Path, host: Path, root: Path, physical_boot: str) -> Dict[str, str]:
    for name in ('android-data/dalvik-cache', 'cache', 'tmp'):
        (work / name).mkdir(parents=True, exist_ok=True)
    local_lib_dir = os.environ.get('SHIRAZ_LOCAL_LIB_DIR', '').strip() or str(root / '.local/lib')
    lib_dirs = [str(host / 'lib64'), local_lib_dir, str(root / 'build')]
    existing = os.environ.get('LD_LIBRARY_PATH')
    if existing:
        lib_dirs.append(existing)
    return dict(
        os.environ,
        ANDROID_ROOT=str(host),
        ANDROID_DATA=str(work / 'android-data'),
        XDG_CACHE_HOME=str(work / 'cache'),
        BOOTCLASSPATH=physical_boot,
        LD_LIBRARY_PATH=':'.join(lib_dirs),
    )


def _atomic_symlink(link_path: Path, target: Path) -> None:
    tmp = link_path.with_name(f'.{link_path.name}.tmp.{os.getpid()}')
    tmp.unlink(missing_ok=True)
    os.symlink(target.resolve(), tmp)
    os.replace(tmp, link_path)


def _atomic_write_json(path: Path, data: object) -> None:
    tmp = path.with_name(f'.{path.name}.tmp.{os.getpid()}')
    tmp.write_text(json.dumps(data, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    os.replace(tmp, path)


def _common_dex2oat_flags(host: Path, isa: str) -> List[str]:
    return [
        str(host / 'bin/dex2oat'),
        '--runtime-arg',
        '-Xms64m',
        '--runtime-arg',
        '-Xmx512m',
        '--instruction-set=' + isa,
        '--avoid-storing-invocation',
        '--no-generate-debug-info',
        '--generate-mini-debug-info',
    ]


def _wrap_dex2oat(cmd: List[str], env: Dict[str, str]) -> Tuple[List[str], Dict[str, str]]:
    """Invoke dex2oat through SHIRAZ_RUNTIME_LOADER when running on NixOS/private glibc."""
    loader = env.get('SHIRAZ_RUNTIME_LOADER', '').strip()
    if not loader:
        return cmd, env
    run_env = dict(env)
    lib_path = run_env.pop('LD_LIBRARY_PATH', '')
    loader_dir = str(Path(loader).parent)
    lib_path = f'{lib_path}:{loader_dir}' if lib_path else loader_dir
    prepend = run_env.get('SHIRAZ_PREPEND_LIBRARY_PATH', '').strip()
    if prepend:
        lib_path = f'{prepend}:{lib_path}'
    run_env.pop('LD_PRELOAD', None)
    return [loader, '--library-path', lib_path, *cmd], run_env


def ensure_boot_and_framework_aot(
    cache_root: Optional[Path] = None,
    *,
    root: Path = ROOT,
    host: Path = HOST,
    timeout: int = 600,
) -> Dict[str, object]:
    """Ensure shared ART boot image and `shiraz-framework.odex` are up to date."""
    isa = art_isa()
    cache = Path(cache_root) if cache_root is not None else aot_cache_root()
    compiler = host / 'bin/dex2oat'
    if not compiler.is_file():
        raise RuntimeError(f'dex2oat binary not found at {compiler}')
    fw_jar = root / 'build/shiraz-framework.jar'
    if not fw_jar.is_file():
        raise RuntimeError(f'shiraz-framework.jar not found at {fw_jar}')

    physical = ':'.join(str(host / 'framework' / name) for name in JARS)
    locations = ['/shiraz/boot/' + name for name in JARS]

    with _aot_lock(cache):
        boot_dir = cache / 'boot'
        images = boot_dir / isa
        stage = cache / 'stage'
        oat_dir = stage / 'oat' / isa
        work = cache / 'work'
        boot_compiled = False
        framework_compiled = False

        if not boot_image_ready(cache, host=host):
            if images.exists():
                shutil.rmtree(images)
            images.mkdir(parents=True, exist_ok=True)
            env = _dex2oat_env(work, host, root, physical)
            cmd, run_env = _wrap_dex2oat(
                _common_dex2oat_flags(host, isa)
                + [
                    '--compiler-filter=speed',
                    '--base=0x70000000',
                    '--image=' + str(images / 'boot.art'),
                    '--oat-file=' + str(images / 'boot.oat'),
                    *['--dex-file=' + str(host / 'framework' / name) for name in JARS],
                    *['--dex-location=' + loc for loc in locations],
                ],
                env,
            )
            log_path = work / 'boot.log'
            with log_path.open('w', encoding='utf-8') as log:
                res = subprocess.run(
                    cmd, env=run_env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout
                )
            if res.returncode != 0:
                raise RuntimeError(f'Boot image AOT compilation failed; inspect {log_path}')
            for path in _expected_boot_files(boot_dir, isa):
                if not path.is_file() or path.stat().st_size == 0:
                    raise RuntimeError(f'Missing boot image AOT artifact: {path}')
            _atomic_write_json(boot_dir / 'fingerprint.json', _boot_fingerprint(host, isa))
            boot_compiled = True

        if not framework_aot_ready(cache, root=root, host=host):
            oat_dir.mkdir(parents=True, exist_ok=True)
            fw_link = stage / 'shiraz-framework.jar'
            _atomic_symlink(fw_link, fw_jar)
            env = _dex2oat_env(work, host, root, physical)
            cmd, run_env = _wrap_dex2oat(
                _common_dex2oat_flags(host, isa)
                + [
                    '--runtime-arg',
                    '-Xbootclasspath:' + physical,
                    '--runtime-arg',
                    '-Xbootclasspath-locations:' + ':'.join(locations),
                    '--boot-image=' + str(boot_dir / 'boot.art'),
                    '--dex-file=' + str(fw_link),
                    '--dex-location=shiraz-framework.jar',
                    '--oat-file=' + str(oat_dir / 'shiraz-framework.odex'),
                    '--compiler-filter=speed',
                    '--host',
                    '--class-loader-context=PCL[]',
                    '--classpath-dir=' + str(stage),
                ],
                env,
            )
            log_path = work / 'framework.log'
            with log_path.open('w', encoding='utf-8') as log:
                res = subprocess.run(
                    cmd, env=run_env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout
                )
            if res.returncode != 0:
                raise RuntimeError(f'Framework AOT compilation failed; inspect {log_path}')
            if not _verify_elf(oat_dir / 'shiraz-framework.odex'):
                raise RuntimeError('Framework AOT output is invalid ELF')
            boot_fp = _boot_fingerprint(host, isa)
            _atomic_write_json(
                stage / 'framework.fingerprint.json',
                _framework_fingerprint(root, boot_fp),
            )
            framework_compiled = True

        return {
            'aot_dir': str(cache),
            'isa': isa,
            'boot_ready': True,
            'framework_ready': True,
            'boot_compiled': boot_compiled,
            'framework_compiled': framework_compiled,
        }


def ensure_app_aot(
    apk_path: Path,
    apk_sha256: str,
    cache_root: Optional[Path] = None,
    *,
    root: Path = ROOT,
    host: Path = HOST,
    compiler_filter: str = 'speed-profile',
    timeout: int = 600,
) -> Dict[str, object]:
    """Compile an APK's DEX classes against the shared boot image and framework."""
    isa = art_isa()
    cache = Path(cache_root) if cache_root is not None else aot_cache_root()
    ensure_boot_and_framework_aot(cache, root=root, host=host, timeout=timeout)

    if app_aot_ready(apk_sha256, cache, root=root, host=host):
        return {
            'apk_sha256': apk_sha256,
            'compiled': False,
            'ready': True,
            'odex': str(cache / 'stage' / 'oat' / isa / f'{apk_sha256}.odex'),
        }

    # Note: speed-profile without a profile falls back to verify on ART; use 'speed'
    # (or 'quicken' fallback on huge APKs if speed fails) for genuine fast cold start.
    effective_filter = 'speed' if compiler_filter == 'speed-profile' else compiler_filter
    physical = ':'.join(str(host / 'framework' / name) for name in JARS)
    locations = ['/shiraz/boot/' + name for name in JARS]

    with _aot_lock(cache):
        if app_aot_ready(apk_sha256, cache, root=root, host=host):
            return {
                'apk_sha256': apk_sha256,
                'compiled': False,
                'ready': True,
                'odex': str(cache / 'stage' / 'oat' / isa / f'{apk_sha256}.odex'),
            }
        boot_dir = cache / 'boot'
        stage = cache / 'stage'
        oat_dir = stage / 'oat' / isa
        oat_dir.mkdir(parents=True, exist_ok=True)
        work = cache / 'work'
        apk_link = stage / f'{apk_sha256}.apk'
        _atomic_symlink(apk_link, Path(apk_path))
        odex_path = oat_dir / f'{apk_sha256}.odex'
        env = _dex2oat_env(work, host, root, physical)

        last_log = work / f'app-{apk_sha256[:12]}.log'
        succeeded = False
        for flt in (effective_filter, 'quicken'):
            cmd, run_env = _wrap_dex2oat(
                _common_dex2oat_flags(host, isa)
                + [
                    '--runtime-arg',
                    '-Xbootclasspath:' + physical,
                    '--runtime-arg',
                    '-Xbootclasspath-locations:' + ':'.join(locations),
                    '--boot-image=' + str(boot_dir / 'boot.art'),
                    '--dex-file=' + str(apk_link),
                    '--dex-location=' + apk_link.name,
                    '--oat-file=' + str(odex_path),
                    '--compiler-filter=' + flt,
                    '--host',
                    '--class-loader-context=PCL[shiraz-framework.jar]',
                    '--classpath-dir=' + str(stage),
                ],
                env,
            )
            with last_log.open('w', encoding='utf-8') as log:
                res = subprocess.run(
                    cmd, env=run_env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout
                )
            if res.returncode == 0 and _verify_elf(odex_path):
                succeeded = True
                used_filter = flt
                break
        if not succeeded:
            odex_path.unlink(missing_ok=True)
            (oat_dir / f'{apk_sha256}.vdex').unlink(missing_ok=True)
            raise RuntimeError(
                f'App AOT compilation failed for {apk_sha256[:12]}; inspect {last_log}'
            )

        fw_fp = json.loads((stage / 'framework.fingerprint.json').read_text(encoding='utf-8'))
        fw_hash = hashlib.sha256(json.dumps(fw_fp, sort_keys=True).encode('utf-8')).hexdigest()
        _atomic_write_json(
            stage / f'{apk_sha256}.fingerprint.json',
            {
                'version': 1,
                'apk_sha256': apk_sha256,
                'framework_hash': fw_hash,
                'compiler_filter': used_filter,
            },
        )
        return {
            'apk_sha256': apk_sha256,
            'compiled': True,
            'ready': True,
            'compiler_filter': used_filter,
            'odex': str(odex_path),
        }


def collect_warm_files(
    cache_root: Optional[Path] = None,
    *,
    root: Path = ROOT,
    host: Path = HOST,
    max_bytes: int = 384 * 1024 * 1024,
) -> List[Path]:
    """Return prioritized runtime, AOT, library, and font files to warm into page cache."""
    cache = Path(cache_root) if cache_root is not None else aot_cache_root()
    candidates: List[Path] = []

    # 1. ART VM binaries and core shared libraries
    for name in ('dalvikvm64', 'dalvikvm'):
        p = host / 'bin' / name
        if p.is_file():
            candidates.append(p)
    host_lib = host / 'lib64'
    if host_lib.is_dir():
        candidates.extend(
            sorted(p for p in host_lib.glob('*.so*') if p.is_file() and not p.is_symlink())
        )

    # 2. Shiraz native JNI + Skia + graphics/audio/notification libraries
    build_dir = root / 'build'
    if build_dir.is_dir():
        candidates.extend(
            sorted(p for p in build_dir.glob('*.so*') if p.is_file() and not p.is_symlink())
        )

    # 3. Boot jars, framework jar & resources
    candidates.extend(host / 'framework' / name for name in JARS)
    candidates.append(root / 'build/shiraz-framework.jar')
    candidates.append(root / 'build/framework-res/framework-res.apk')

    # 4. Precompiled AOT boot images, framework odex, and installed app odex files
    try:
        isa = art_isa()
        for directory in (cache / 'boot' / isa, cache / 'stage' / 'oat' / isa):
            if directory.is_dir():
                candidates.extend(
                    sorted(
                        p
                        for p in directory.iterdir()
                        if p.is_file() and p.suffix in ('.art', '.oat', '.odex', '.vdex')
                    )
                )
    except RuntimeError:
        pass

    # 5. Bundled fonts (needed on first frame)
    font_dir = root / '.local/share/shiraz/fonts'
    for name in BUNDLED_FONTS:
        if name.endswith('.ttf'):
            candidates.append(font_dir / name)

    # 6. Pre-extracted native app libraries (e.g. libnative-utils.so, libosmand.so)
    runs_dir = root / 'build/runs'
    if runs_dir.is_dir():
        for app_lib in sorted(runs_dir.glob('*/processes/*/app_lib')):
            if app_lib.is_dir():
                candidates.extend(
                    sorted(p for p in app_lib.glob('*.so*') if p.is_file() and not p.is_symlink())
                )

    # 7. Remaining bundled host runtime shared libraries (skip unused software-GL giants
    #    like libLLVM / swrast_dri / libz3 unless software rendering is explicitly forced,
    #    so they never crowd out app odex, fonts, or native app_lib/*.so from the RAM budget).
    local_lib = root / '.local/lib'
    software_gl = os.environ.get('LIBGL_ALWAYS_SOFTWARE') == '1'
    if local_lib.is_dir():
        for p in sorted(local_lib.glob('*.so*')):
            if not p.is_file() or p.is_symlink():
                continue
            if not software_gl and p.name.startswith(
                ('libLLVM', 'swrast_dri', 'llvmpipe', 'libz3')
            ):
                continue
            candidates.append(p)

    # Deduplicate by resolved path and cap total memory budget
    seen = set()
    selected: List[Path] = []
    total = 0
    for raw in candidates:
        try:
            if not raw.is_file():
                continue
            resolved = raw.resolve()
            if resolved in seen:
                continue
            size = resolved.stat().st_size
            if size == 0:
                continue
            if total + size > max_bytes:
                continue
            seen.add(resolved)
            selected.append(resolved)
            total += size
        except OSError:
            continue
    return selected
