#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build ANGLE's Metal backend (libEGL, libGLESv2, libGLESv1_CM) in-tree on macOS arm64.

OpenGL ES on macOS goes through ANGLE so native/gpu.cpp, native/egl_shim.cpp,
native/gles1_jni.cpp and native/gles2_jni.cpp keep their Linux EGL/GLES code.
No depot_tools, gclient, CIPD, Chromium clang, siso or Rust is used: ANGLE
chromium/8127 and the eight DEPS checkouts gn gen needs are fetched with plain
git at their DEPS-pinned commits, gclient's generated build/config/gclient_args.gni
is written from this script, gn is built from source at the revision ANGLE's
DEPS pins for buildtools/mac, and ninja compiles with Xcode's clang, Apple ld
and the system libc++. Chromium's build/ needs one tracked patch for Apple
clang (patches/angle-build-apple-clang.patch, BSD-3-Clause, see its header).

Outputs:
  .local/lib/angle/lib{EGL,GLESv2,GLESv1_CM}.dylib  @rpath install names,
      LC_RPATH @loader_path, ad-hoc signed; installed by copy to a temporary
      name, edit, sign, then rename (a Mach-O overwritten in place is SIGKILLed).
  .local/include/angle/{EGL,GLES,GLES2,GLES3,KHR}   Khronos + ANGLE headers. A
      subdirectory, so the ART build's CPATH=.local/include never sees them.
  .local/share/licenses/angle/                      licenses and PROVENANCE.
  build/macos/deps/angle.stamp                      makes a rerun a no-op.

Nothing is installed into the host system. Host tools used: git, xcrun (clang,
metal, ld, libtool), install_name_tool, codesign, otool, lipo, and ninja (the
in-tree .local/tools/meson-venv ninja from make macos-graphics-deps when present,
else PATH). The python3 that runs this script is also passed to gn as
--script-executable (Chromium's .gn points at a CIPD cpython3 that is never
fetched); it must be 3.11 or newer, the CPython series ANGLE's DEPS pins
(cpython3_version 3.11.9), so the OS /usr/bin/python3 3.9 is rejected.

Clean pins: every checkout must be at its pinned commit with its pinned origin
URL and an empty git status; Chromium build/ must equal its pinned commit plus
the tracked patch exactly (compared through a temporary index), so no local
edit reaches the dylibs unrecorded.

Reuse: when every checkout is at its pin, gclient_args.gni and args.gn match
(compared after normalisation, since gn rewrites args.gn), the patch is applied
and the dylibs exist, gn gen and ninja are not run again. out/Release-metal/
shiraz-build.txt records the inputs of the last ninja run (pins, gn files,
patch, clang, SDK and the python3 path and version gn runs build scripts with);
a different record means a rebuild. A tree with no record is taken over only
when `ninja -n` on the same targets prints "ninja: no work to do." (file times
alone are not trusted). gn's out/shiraz-gn.txt works the same way.

Metal caches (outside the repository, not created by this script): ANGLE
compiles its Metal shaders at run time, and Metal keeps compiled functions and
libraries per user under `getconf DARWIN_USER_CACHE_DIR`: com.apple.metal/ for
unbundled processes (build/macos/preview, dalvikvm) and
net.java.openjdk.java/com.apple.metal/ for the JVM GLES smokes. They survive
make clean and rebuilds; only the first launch after a cache is cleared pays the
shader compiles, so a cold-launch time must say whether this cache was warm.

  python3 scripts/bootstrap_angle.py          fetch, build, install (no-op when stamped)
  python3 scripts/bootstrap_angle.py check    compile tests/darwin/gles/angle_probe.c
                                              and run it on the installed dylibs
"""
import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bootstrap_darwin import (_LEAKY_ENV, BUILD, DEPS, MACOS_MIN, PREFIX, ROOT, _dependencies,  # noqa: E402
                              _load_commands, ncpu, run, sdk, tool)

ANGLE_SRC = DEPS / 'angle'
ANGLE_URL = 'https://chromium.googlesource.com/angle/angle'
# Head of refs/heads/chromium/8127 (Chrome 158 stable for mac), 2026-11-05.
ANGLE_COMMIT = '1f6526a566c27c7c33aa73a78517450a4a2a1714'
ANGLE_BRANCH = 'chromium/8127'
# DEPS pins (each equals the gitlink in the ANGLE tree). build, buildtools,
# testing, zlib and abseil-cpp are compiled from or read by the build; gn gen
# also resolves BUILD files under jsoncpp and the SPIR-V trees with Vulkan off,
# but none of their code is linked into the three dylibs.
ANGLE_DEPS = (
    ('build', 'https://chromium.googlesource.com/chromium/src/build.git',
     '7a1d4e33658adb6757b77323b1fcb77acf534195'),
    ('buildtools', 'https://chromium.googlesource.com/chromium/src/buildtools.git',
     '2f41b66d4857c8efbfcb2a350f159944f71dbf7e'),
    ('testing', 'https://chromium.googlesource.com/chromium/src/testing',
     '1c7935b65ba426eb80ca04956aaabd6490213662'),
    ('third_party/zlib', 'https://chromium.googlesource.com/chromium/src/third_party/zlib',
     '9f9f52c60a14e8e772fb561029ae0a121fef7609'),
    ('third_party/abseil-cpp', 'https://chromium.googlesource.com/chromium/src/third_party/abseil-cpp',
     '9a0a43cbad88a77703bd73093011a62bbe0a6740'),
    ('third_party/jsoncpp', 'https://chromium.googlesource.com/chromium/src/third_party/jsoncpp',
     'f62d44704b4da6014aa231cfc116e7fd29617d2a'),
    ('third_party/spirv-headers/src', 'https://chromium.googlesource.com/external/github.com/KhronosGroup/SPIRV-Headers',
     'fbf86836a9b1966586faf7be7bb8a6440e156d62'),
    ('third_party/spirv-tools/src', 'https://chromium.googlesource.com/external/github.com/KhronosGroup/SPIRV-Tools',
     '066042ec3a9af3094c794bbbbd3600689fe7ff29'),
)
GN_SRC = DEPS / 'gn'
GN_URL = 'https://gn.googlesource.com/gn'
# git_revision of the buildtools/mac gn CIPD package in ANGLE's DEPS. Skia's own
# pin cannot be fetched or built here (scripts/bootstrap_skia.py), so Skia reuses it.
GN_COMMIT = '3ef636ac7c21b85571c86628507260ec582bef8e'
GN_BIN = GN_SRC / 'out/gn'
GN_RECORD = GN_SRC / 'out/shiraz-gn.txt'

PATCH = ROOT / 'patches/angle-build-apple-clang.patch'
OUT = ANGLE_SRC / 'out/Release-metal'
BUILD_RECORD = OUT / 'shiraz-build.txt'
LIBRARIES = ('libEGL.dylib', 'libGLESv2.dylib', 'libGLESv1_CM.dylib')
NINJA_TARGETS = ('libEGL', 'libGLESv2', 'libGLESv1_CM')
LIB_DIR = PREFIX / 'lib/angle'
INCLUDE_DIR = PREFIX / 'include/angle'
HEADER_DIRS = ('EGL', 'GLES', 'GLES2', 'GLES3', 'KHR')
LICENSE_DIR = PREFIX / 'share/licenses/angle'
# Third-party code linked into libGLESv2 besides ANGLE's own sources, per
# gn desc out/Release-metal //:libGLESv2 deps --all (M4 phase 0).
LICENSES = (
    ('LICENSE', 'LICENSE', 'ANGLE, BSD-3-Clause'),
    ('third_party/abseil-cpp/LICENSE', 'abseil-cpp.LICENSE', 'Abseil, Apache-2.0'),
    ('third_party/zlib/LICENSE', 'zlib.LICENSE', 'Chromium zlib, Zlib'),
    ('src/common/third_party/xxhash/src/LICENSE', 'xxhash.LICENSE', 'xxHash, BSD-2-Clause'),
    # third_party/zlib/google/compression_utils_portable.cc is "governed by a BSD-style
    # license ... in the Chromium source repository LICENSE file"; chromium/src is not
    # fetched, and the pinned buildtools checkout carries the Chromium Authors' text.
    ('buildtools/LICENSE', 'chromium.LICENSE',
     'Chromium Authors, BSD-3-Clause: zlib/google compression_utils_portable; text from buildtools/LICENSE'),
)
STAMP = BUILD / 'angle.stamp'
CHECK_OUT = ROOT / 'build/macos/gpu-deps-check'
PROBE = ROOT / 'tests/darwin/gles/angle_probe.c'

# Stand-in for the file gclient writes from DEPS gclient_gn_args: the DEPS
# defaults with custom_vars checkout_angle_cl_deps=False, checkout_angle_dawn_deps=False.
GCLIENT_ARGS = '''\
# Hand-written stand-in for the file gclient generates from DEPS gclient_gn_args
# (ANGLE chromium/8127 @1f6526a5). Values = DEPS defaults with custom_vars
# checkout_angle_cl_deps=False, checkout_angle_dawn_deps=False.
android_ndk_version = "2@30.0.16248370"
checkout_angle_cl_deps = false
checkout_angle_dawn_deps = false
checkout_angle_internal = false
checkout_angle_mesa = false
checkout_angle_partition_alloc = false
checkout_angle_restricted_traces = false
checkout_extra_traces = false
generate_location_tags = false
'''


def gn_args():
    """args.gn for out/Release-metal. Comments cite the default each line overrides."""
    clang = Path(tool('clang'))
    toolchain_usr = clang.parent.parent  # .../XcodeDefault.xctoolchain/usr
    clang_major = Path(subprocess.check_output([str(clang), '-print-resource-dir'], text=True).strip()).name
    return f'''\
# Written by scripts/bootstrap_angle.py. file:line references are in .deps/angle at chromium/8127.
target_os = "mac"
target_cpu = "arm64"
is_debug = false                      # BUILDCONFIG.gn
is_component_build = false            # self-contained dylibs, deps linked statically
is_official_build = false
symbol_level = 0
treat_warnings_as_errors = false      # compiler.gni:169 default true; Xcode clang != Chromium clang
dcheck_always_on = false

# Toolchain: Xcode clang instead of Chromium clang (llvmorg-24 GCS package)
is_clang = true
clang_base_path = "{toolchain_usr}"  # clang.gni:30,86
clang_use_chrome_plugins = false      # .gn default_args sets true
clang_version = "{clang_major}"                  # toolchain.gni:44 default "24"; Xcode lib/clang/<major>
use_lld = false                       # compiler.gni:80; Xcode has no ld64.lld -> Apple ld + libtool
use_custom_libcxx = false             # c++.gni:17 -> system libc++
use_custom_libcxx_for_host = false    # c++.gni:19
use_system_xcode = true               # build_overrides/build.gni:9 (skips should_use_hermetic_xcode.py)
mac_deployment_target = "{MACOS_MIN}"        # mac_sdk.gni default "13.0"
mac_min_system_version = "{MACOS_MIN}"
use_siso = false                      # .gn default_args sets true; use ninja
use_remoteexec = false
enable_rust = false                   # .gn default_args sets true
enable_rust_cxx = false
use_clang_modules = false

# ANGLE backends: Metal only
angle_enable_metal = true             # angle.gni:250 default is_apple
angle_enable_gl = false               # angle.gni:230 (would also pull CGL)
angle_enable_vulkan = false           # angle.gni:235 default true on mac (SwiftShader, glslang, spirv-*)
angle_enable_swiftshader = false
angle_enable_d3d11 = false
angle_enable_null = false             # angle.gni:247 default !is_official_build
angle_enable_wgpu = false
angle_enable_cl = false
# GLES1: no toggle exists; libGLESv1_CM target always defined (BUILD.gn:1584), GLES1 emulation built into libANGLE.

# Off: tests, samples, capture, extras
angle_build_tests = false             # angle.gni:418
build_angle_deqp_tests = false        # .gn default_args sets true
angle_has_rapidjson = false           # angle.gni:165 -> also disables frame capture
angle_has_frame_capture = false
angle_has_astc_encoder = false        # angle.gni:315
angle_has_histograms = false
angle_build_vulkan_system_info = false
angle_enable_perfetto = false         # angle.gni:357 default true with clang
angle_use_partition_alloc = false
# angle_enable_abseil left at default (true): third_party/abseil-cpp fetched.
shiraz_apple_clang = true            # patches/angle-build-apple-clang.patch: drop Chromium-clang-only flags
'''


def gn_tokens(text):
    """Tokens of a .gn/.gni assignment file: strings kept verbatim, comments dropped."""
    tokens, i = [], 0
    while i < len(text):
        c = text[i]
        if c == '#':
            while i < len(text) and text[i] != '\n':
                i += 1
        elif c.isspace():
            i += 1
        elif c == '"':
            j = i + 1
            while j < len(text) and text[j] != '"':
                j += 2 if text[j] == '\\' else 1
            tokens.append(text[i:j + 1])
            i = j + 1
        elif c in '=[],{}':
            tokens.append(c)
            i += 1
        else:
            j = i
            while j < len(text) and not text[j].isspace() and text[j] not in '#"=[],{}':
                j += 1
            tokens.append(text[i:j])
            i = j
    return tokens


def normalize_gn(text):
    """{name: value} of a gn args file, independent of comments, layout and trailing commas.

    gn gen rewrites args.gn in its own layout, so byte equality would report a
    change that is not one.
    """
    tokens = gn_tokens(text)
    result, i = {}, 0
    while i < len(tokens):
        if i + 1 >= len(tokens) or tokens[i + 1] != '=':
            raise SystemExit(f'unparsable gn assignment near {tokens[i:i + 3]}')
        name, i, depth, value = tokens[i], i + 2, 0, []
        while i < len(tokens):
            token = tokens[i]
            if depth == 0 and value and i + 1 < len(tokens) and tokens[i + 1] == '=':
                break
            depth += token in '[{'
            depth -= token in ']}'
            value.append(token)
            i += 1
        joined = ''.join(value).replace(',]', ']').replace(',}', '}')
        result[name] = joined
    return result


def log(message):
    print(f'== bootstrap_angle: {message}', flush=True)


def git(cwd, *args, check=True):
    result = subprocess.run(['git', *args], cwd=cwd, capture_output=True, text=True)
    if check and result.returncode:
        raise SystemExit(f'git {" ".join(args)} in {cwd} failed: {result.stderr.strip()}')
    return result.stdout.strip() if result.returncode == 0 else None


def tree_differs_from(path, tree):
    """True when PATH's worktree has untracked files or differs from TREE."""
    if git(path, 'ls-files', '--others', '--exclude-standard', check=False):
        return True
    return subprocess.run(['git', 'diff', '--quiet', tree, '--'], cwd=path, capture_output=True).returncode != 0


def patched_tree(path):
    """Tree id of HEAD plus PATCH, built in a temporary index (the real index is untouched)."""
    with tempfile.TemporaryDirectory() as temporary:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'))
        for args in (['read-tree', 'HEAD'], ['apply', '--cached', str(PATCH)], ['write-tree']):
            result = subprocess.run(['git', *args], cwd=path, env=env, capture_output=True, text=True)
            if result.returncode:
                return None
        return result.stdout.strip()


def local_changes(path):
    """'' when PATH's worktree is clean (Chromium build/: clean, or HEAD plus PATCH exactly), else its status."""
    status = git(path, 'status', '--porcelain', '--untracked-files=all', '--ignore-submodules=all', check=False)
    if status is None:
        return 'git status failed'
    if not status:
        return ''
    if path == ANGLE_SRC / 'build':
        tree = patched_tree(path)
        if tree and not tree_differs_from(path, tree):
            return ''
        status += f'\n(not HEAD plus {PATCH.relative_to(ROOT)} exactly)'
    return status


def checkout_state(path, url, commit):
    """None when PATH is a clean checkout of URL at COMMIT, else the reason it is not."""
    if not (path / '.git').exists():
        return 'missing'
    origin = git(path, 'remote', 'get-url', 'origin', check=False)
    if origin != url:
        return f'origin is {origin}, expected {url}'
    head = git(path, 'rev-parse', 'HEAD', check=False)
    if head != commit:
        return f'HEAD is {head}, expected {commit}'
    changes = local_changes(path)
    return f'local changes:\n{changes}' if changes else None


def fetch_checkout(path, url, commit, name):
    """Shallow fetch of exactly COMMIT (googlesource serves any reachable sha)."""
    state = checkout_state(path, url, commit)
    if state is None:
        return
    if state != 'missing':
        if git(path, 'remote', 'get-url', 'origin', check=False) != url:
            raise SystemExit(f'{path}: {state}. Remove it and rerun.')
        # At the pin, only the exact tracked patch is allowed (local_changes); before a
        # re-fetch to another commit, any change at all (the patch included) stops it.
        changes = (state.removeprefix('local changes:\n') if state.startswith('local changes') else
                   git(path, 'status', '--porcelain', '--untracked-files=all', '--ignore-submodules=all',
                       check=False))
        if changes:
            raise SystemExit(f'{path}: {state.splitlines()[0]}; the checkout has local changes. '
                             f'Remove it (or revert them) and rerun:\n{changes}')
    log(f'fetch {name} {commit}')
    path.mkdir(parents=True, exist_ok=True)
    if state == 'missing':
        git(path, 'init', '-q')
        git(path, 'remote', 'add', 'origin', url)
    git(path, 'fetch', '-q', '--depth', '1', 'origin', commit)
    git(path, 'checkout', '-q', '--detach', 'FETCH_HEAD')
    if checkout_state(path, url, commit) is not None:
        raise SystemExit(f'{path}: {checkout_state(path, url, commit)} after fetch')


def all_checkouts():
    return [(ANGLE_SRC, ANGLE_URL, ANGLE_COMMIT, f'angle {ANGLE_BRANCH}')] + [
        (ANGLE_SRC / rel, url, commit, rel) for rel, url, commit in ANGLE_DEPS] + [
        (GN_SRC, GN_URL, GN_COMMIT, 'gn')]


def sha256(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode()).hexdigest()


def patch_state():
    """'applied', 'unapplied' or None when the build/ tree matches neither."""
    tree = ANGLE_SRC / 'build'
    if subprocess.run(['git', 'apply', '--check', '-R', str(PATCH)], cwd=tree, capture_output=True).returncode == 0:
        return 'applied'
    if subprocess.run(['git', 'apply', '--check', str(PATCH)], cwd=tree, capture_output=True).returncode == 0:
        return 'unapplied'
    return None


def apply_patch():
    state = patch_state()
    if state == 'unapplied':
        log(f'apply {PATCH.relative_to(ROOT)}')
        run(['git', 'apply', PATCH], cwd=ANGLE_SRC / 'build')
    elif state is None:
        raise SystemExit(f'{PATCH.relative_to(ROOT)} neither applies to nor is applied in {ANGLE_SRC / "build"}')


def write_if_changed(path, text):
    """Rewrite a gn file only when its normalised assignments differ."""
    if path.is_file() and normalize_gn(path.read_text()) == normalize_gn(text):
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return True


def python_identity():
    """The interpreter gn passes Chromium's build scripts to (--script-executable)."""
    return f'python3 {sys.executable} {platform.python_version()}'


def toolchain_identity():
    clang = tool('clang')
    return '\n'.join([clang, subprocess.check_output([clang, '--version'], text=True).strip(), sdk(),
                      python_identity()])


def build_key(args_text):
    """Inputs of the ninja build: pins, normalised gn files, patch, compiler, SDK and python3."""
    material = [f'{name} {url} {commit}' for _, url, commit, name in all_checkouts()]
    material += [repr(sorted(normalize_gn(GCLIENT_ARGS).items())), repr(sorted(normalize_gn(args_text).items())),
                 sha256(PATCH.read_bytes()), toolchain_identity()]
    return sha256('\n'.join(material))


def gn_key():
    return sha256('\n'.join([GN_URL, GN_COMMIT, toolchain_identity()]))


def build_env():
    env = {key: value for key, value in os.environ.items()
           if key not in _LEAKY_ENV and key not in ('PYTHONPATH', 'PYTHONHOME', 'SDKROOT')}
    env['MACOSX_DEPLOYMENT_TARGET'] = MACOS_MIN
    return env


def ninja():
    venv = PREFIX / 'tools/meson-venv/bin/ninja'
    if venv.is_file():
        return str(venv)
    found = shutil.which('ninja')
    if not found:
        raise SystemExit('ninja is required as a build tool: run make macos-graphics-deps (in-tree ninja) '
                         'or put ninja on PATH')
    return found


def record_matches(record, key):
    return record.is_file() and record.read_text().splitlines()[:1] == [key]


def ninja_reports_current(directory, targets):
    """True only when `ninja -n` on TARGETS prints "ninja: no work to do." (rc is 0 either way).

    A dry run executes nothing; a stale manifest makes it print the gn regeneration
    step and stop, and a tree without .ninja_log lists every command, so neither passes.
    """
    if not (directory / 'build.ninja').is_file():
        return False
    result = subprocess.run([ninja(), '-n', '-C', str(directory), *targets], capture_output=True, text=True,
                            env=build_env())
    return result.returncode == 0 and 'ninja: no work to do.' in result.stdout.splitlines()


def ensure_gn():
    """Build gn at GN_COMMIT unless out/gn already came from that commit and toolchain."""
    fetch_checkout(GN_SRC, GN_URL, GN_COMMIT, 'gn')
    key = gn_key()
    if record_matches(GN_RECORD, key) and GN_BIN.is_file():
        return
    if GN_BIN.is_file() and not GN_RECORD.exists() and ninja_reports_current(GN_SRC / 'out', ['gn']):
        # Built before this script recorded builds, from the clean checkout verified above.
        log(f'take over {GN_BIN.relative_to(ROOT)}: ninja -n reports no work to do')
    else:
        log(f'build gn {GN_COMMIT}')
        # --no-last-commit-position: a shallow clone has no tag for git describe.
        run([sys.executable, 'build/gen.py', '--no-last-commit-position', '--out-path', 'out'],
            cwd=GN_SRC, env=build_env())
        run([ninja(), '-C', 'out', f'-j{ncpu()}', 'gn'], cwd=GN_SRC, env=build_env())
    GN_RECORD.write_text(f'{key}\ngn {GN_COMMIT}\n')


def build_angle():
    """Fetch, configure and build unless out/Release-metal already holds this build."""
    for path, url, commit, name in all_checkouts()[:-1]:
        fetch_checkout(path, url, commit, name)
    write_if_changed(ANGLE_SRC / 'build/config/gclient_args.gni', GCLIENT_ARGS)
    apply_patch()
    changes = local_changes(ANGLE_SRC / 'build')
    if changes:
        raise SystemExit(f'{ANGLE_SRC / "build"} is not its pin plus {PATCH.relative_to(ROOT)} exactly:\n{changes}')
    ensure_gn()
    args_text = gn_args()
    key = build_key(args_text)
    args_changed = write_if_changed(OUT / 'args.gn', args_text)
    outputs = [OUT / name for name in LIBRARIES]
    if not args_changed and record_matches(BUILD_RECORD, key) and all(path.is_file() for path in outputs):
        log('out/Release-metal is current (build record matches); gn gen and ninja skipped')
        return
    if (not args_changed and not BUILD_RECORD.exists() and all(path.is_file() for path in outputs)
            and ninja_reports_current(OUT, NINJA_TARGETS)):
        log('take over out/Release-metal: no build record, ninja -n reports no work to do; gn gen and ninja '
            'skipped')
        BUILD_RECORD.write_text(f'{key}\ntaken over: ninja -n reported no work to do\n')
        return
    BUILD_RECORD.unlink(missing_ok=True)
    env = build_env()
    log('gn gen out/Release-metal')
    run([GN_BIN, 'gen', 'out/Release-metal', f'--script-executable={sys.executable}'], cwd=ANGLE_SRC, env=env)
    log('ninja ' + ' '.join(NINJA_TARGETS))
    run([ninja(), '-C', 'out/Release-metal', f'-j{ncpu()}', *NINJA_TARGETS], cwd=ANGLE_SRC, env=env)
    BUILD_RECORD.write_text(f'{key}\nbuilt by scripts/bootstrap_angle.py\n')


def rpaths(path):
    lines = _load_commands(path).splitlines()
    found = []
    for index, line in enumerate(lines):
        if line.strip() == 'cmd LC_RPATH':
            for following in lines[index + 1:index + 4]:
                if following.strip().startswith('path '):
                    found.append(following.strip().split()[1])
    return found


def install_name(path):
    return subprocess.check_output(['otool', '-D', str(path)], text=True).splitlines()[1:]


def verify_library(path, name):
    if install_name(path) != [f'@rpath/{name}']:
        raise SystemExit(f'{path}: install name {install_name(path)}, expected @rpath/{name}')
    for dependency in _dependencies(path):
        if dependency == f'@rpath/{name}':
            continue
        if dependency.startswith('@rpath/'):
            if os.path.basename(dependency) not in LIBRARIES:
                raise SystemExit(f'{path}: unexpected @rpath dependency {dependency}')
        elif not dependency.startswith(('/usr/lib/', '/System/Library/')):
            raise SystemExit(f'{path}: non-relocatable dependency {dependency}')
    if rpaths(path) != ['@loader_path']:
        raise SystemExit(f'{path}: LC_RPATH {rpaths(path)}, expected [@loader_path]')
    architectures = subprocess.check_output(['lipo', '-archs', str(path)], text=True).split()
    if architectures != ['arm64']:
        raise SystemExit(f'{path}: architectures {architectures}, expected arm64')
    run(['codesign', '--verify', path])


def install_library(name):
    """Copy, retarget and re-sign under a temporary name, then rename into place.

    The installed file is a new vnode each time: XNU caches code signatures per
    vnode and SIGKILLs a Mach-O whose bytes changed in place.
    """
    source, target = OUT / name, LIB_DIR / name
    temporary = LIB_DIR / f'.{name}.tmp'
    temporary.unlink(missing_ok=True)
    shutil.copyfile(source, temporary)
    temporary.chmod(0o755)
    changes = ['-id', f'@rpath/{name}']
    for dependency in _dependencies(temporary):
        leaf = os.path.basename(dependency)
        if leaf in LIBRARIES and leaf != name and dependency != f'@rpath/{leaf}':
            changes += ['-change', dependency, f'@rpath/{leaf}']
    # gn links each dylib with LC_RPATH @executable_path/ (the ANGLE out dir's
    # loaders); installed, libGLESv1_CM must find libGLESv2 next to itself.
    existing = rpaths(temporary)
    if existing != ['@loader_path']:
        if existing:
            changes += ['-rpath', existing[0], '@loader_path']
            for extra in existing[1:]:
                changes += ['-delete_rpath', extra]
        else:
            changes += ['-add_rpath', '@loader_path']
    run(['install_name_tool', *changes, temporary])
    # The identifier would otherwise come from the temporary file name.
    run(['codesign', '--force', '--sign', '-', '--identifier', name, temporary])
    verify_library(temporary, name)
    os.replace(temporary, target)


def install_headers():
    staging = INCLUDE_DIR.with_name('.angle.tmp')
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    for directory in HEADER_DIRS:
        shutil.copytree(ANGLE_SRC / 'include' / directory, staging / directory)
    shutil.rmtree(INCLUDE_DIR, ignore_errors=True)
    staging.rename(INCLUDE_DIR)


def install_licenses(args_text):
    shutil.rmtree(LICENSE_DIR, ignore_errors=True)
    LICENSE_DIR.mkdir(parents=True)
    for source, target, _ in LICENSES:
        shutil.copyfile(ANGLE_SRC / source, LICENSE_DIR / target)
    lines = [f'ANGLE {ANGLE_BRANCH} {ANGLE_COMMIT}', f'source: {ANGLE_URL}', '', 'DEPS checkouts (git, shallow):']
    lines += [f'  {rel} {url} {commit}' for rel, url, commit in ANGLE_DEPS]
    lines += [f'gn {GN_URL} {GN_COMMIT} (build tool only)',
              f'gn --script-executable: {python_identity()}', '',
              'Licenses here: ' + '; '.join(f'{target} ({note})' for _, target, note in LICENSES) + '.',
              'gn desc out/Release-metal //:libGLESv2 deps --all (M4 phase 0) lists only abseil-cpp, zlib and',
              "xxhash besides ANGLE's own sources; libEGL and libGLESv1_CM were not described separately.",
              'abseil-cpp has no NOTICE file. zlib/google (compression_utils_portable) is under the Chromium',
              "Authors' BSD-3-Clause license (chromium.LICENSE); ANGLE's src/common/base/anglebase is Chromium",
              "base code under BSD-3-Clause with ANGLE's LICENSE as its license file (README.chromium).",
              "The dylibs also link Xcode's libclang_rt.osx.a (compiler-rt builtins; upstream Apache-2.0 WITH",
              'LLVM-exception, whose exception covers object code embedded by compilation).',
              'The installed Khronos headers (include/angle/EGL, GLES*, KHR) carry their own',
              'Apache-2.0 (SPDX) and Khronos MIT-style notices.', '',
              f'Chromium build/ modified by {PATCH.relative_to(ROOT)} (BSD-3-Clause, sha256 '
              f'{sha256(PATCH.read_bytes())});', 'ANGLE sources themselves are unmodified.',
              'Built by scripts/bootstrap_angle.py with Xcode clang; args.gn:', '', args_text]
    (LICENSE_DIR / 'PROVENANCE').write_text('\n'.join(lines))


def outputs():
    return ([LIB_DIR / name for name in LIBRARIES] + [INCLUDE_DIR / 'EGL/egl.h', INCLUDE_DIR / 'GLES2/gl2.h',
            INCLUDE_DIR / 'GLES3/gl3.h', INCLUDE_DIR / 'GLES/gl.h', INCLUDE_DIR / 'KHR/khrplatform.h',
            LICENSE_DIR / 'PROVENANCE'])


def stamp_key(args_text):
    return sha256('\n'.join([build_key(args_text), sha256(Path(__file__).read_bytes()), str(PREFIX)]))


def build():
    args_text = gn_args()
    key = stamp_key(args_text)
    if STAMP.is_file() and STAMP.read_text().splitlines()[:1] == [key] and all(p.exists() for p in outputs()):
        log('up to date (stamp matches)')
        return
    STAMP.unlink(missing_ok=True)
    build_angle()
    LIB_DIR.mkdir(parents=True, exist_ok=True)
    for name in LIBRARIES:
        install_library(name)
    for stale in LIB_DIR.iterdir():
        if stale.name not in LIBRARIES:
            stale.unlink()
    install_headers()
    install_licenses(args_text)
    BUILD.mkdir(parents=True, exist_ok=True)
    sizes = ' '.join(f'{name}={(LIB_DIR / name).stat().st_size}' for name in LIBRARIES)
    STAMP.write_text(f'{key}\nangle {ANGLE_BRANCH} {ANGLE_COMMIT}\ngn {GN_COMMIT}\n'
                     f'patch {PATCH.name} sha256 {sha256(PATCH.read_bytes())}\n'
                     f'args.gn sha256 {sha256(args_text)}\nscript sha256 {sha256(Path(__file__).read_bytes())}\n'
                     f'installed {LIB_DIR.relative_to(ROOT)} {sizes}\n')
    log(f'installed {LIB_DIR.relative_to(ROOT)} and {INCLUDE_DIR.relative_to(ROOT)}')


def check():
    """Compile the probe against the installed headers and run it on the installed dylibs."""
    if not STAMP.is_file() or not all(p.exists() for p in outputs()):
        raise SystemExit('ANGLE is not installed: run make macos-gpu-deps (python3 scripts/bootstrap_angle.py)')
    CHECK_OUT.mkdir(parents=True, exist_ok=True)
    binary = CHECK_OUT / 'angle_probe'
    run([tool('clang'), '-arch', 'arm64', '-isysroot', sdk(), f'-mmacosx-version-min={MACOS_MIN}', '-O1',
         '-Wall', '-Wextra', '-Werror', f'-I{INCLUDE_DIR}', PROBE, '-o', binary])
    env = {key: value for key, value in os.environ.items() if not key.startswith('DYLD_')}
    # cwd / so nothing is found relative to the checkout by accident.
    result = subprocess.run([str(binary), str(LIB_DIR)], cwd='/', env=env, capture_output=True, text=True)
    report = CHECK_OUT / 'angle-probe.log'
    report.write_text(result.stdout + result.stderr)
    sys.stdout.write(result.stdout + result.stderr)
    if result.returncode != 0 or 'RESULT failures=0' not in result.stdout.splitlines():
        raise SystemExit(f'ANGLE probe failed (rc={result.returncode}); see {report.relative_to(ROOT)}')
    print(f'ANGLE dependency check passed ({report.relative_to(ROOT)})')


def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('bootstrap_angle.py builds for macOS arm64 hosts only')
    if sys.version_info < (3, 11):
        raise SystemExit(f'{sys.executable} {platform.python_version()} is too old: it also runs Chromium build '
                         'scripts as gn --script-executable, and ANGLE pins CPython 3.11; run this script with '
                         'python3 3.11 or newer (the macOS /usr/bin/python3 3.9 is too old)')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('stage', choices=['build', 'check'], default='build', nargs='?')
    args = parser.parse_args()
    DEPS.mkdir(exist_ok=True)
    lock = (DEPS / 'angle-build.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'Another ANGLE bootstrap owns the build lock. Wait for it to finish.\n')
    if args.stage == 'build':
        build()
    else:
        check()


if __name__ == '__main__':
    main()
