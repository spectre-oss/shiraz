#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pinned, minimal Ganesh build. No Android runtime, window system, or GTK dependency."""
from pathlib import Path
import os
import subprocess
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / '.deps/skia'
REVISION = '5db0949ba318d248ebc3d33c73ad1251bf95c243'
ARGS = '''is_official_build=true is_debug=false cc="gcc" cxx="g++"
skia_use_gl=true skia_use_vulkan=false skia_use_icu=false skia_use_harfbuzz=false
skia_enable_pdf=false skia_enable_svg=false skia_enable_skottie=false skia_enable_tools=false
skia_use_expat=false skia_use_libjpeg_turbo_decode=false skia_use_libjpeg_turbo_encode=false
skia_use_libpng_decode=false skia_use_libpng_encode=false skia_use_libwebp_decode=false
skia_use_libwebp_encode=false skia_use_wuffs=false skia_use_piex=false skia_use_perfetto=false
skia_use_no_png_encode=true skia_use_no_jpeg_encode=true skia_use_no_webp_encode=true
skia_use_system_freetype2=true skia_use_system_zlib=true skia_use_fontconfig=true extra_cflags=["-fPIC"]'''
def run(*args):
    subprocess.run(args, cwd=SOURCE, check=True)
def main():
    if not SOURCE.exists():
        SOURCE.mkdir(parents=True)
        run('git', 'init')
        run('git', 'fetch', '--depth=1', 'https://skia.googlesource.com/skia', REVISION)
        run('git', 'checkout', '--detach', 'FETCH_HEAD')
    if subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=SOURCE, text=True).strip() != REVISION:
        raise RuntimeError('Skia source revision differs from the pinned build')
    if not (SOURCE/'bin/gn').exists():
        # This pinned source's fetcher pins the GN binary revision as well.
        run('python3', 'bin/fetch-gn')
    run('bin/gn', 'gen', 'out/shiraz', '--args='+ARGS)
    run('ninja', '-C', 'out/shiraz', '-j'+os.environ.get('SHIRAZ_BUILD_JOBS', '4'), 'skia', 'pathkit')
# macOS arm64 (M4a). The Linux path above is unchanged; only main_darwin() runs on a Mac.
#
# gn: bin/fetch-gn's CIPD package (gn b2afae12) answers HTTP 403 from the reference
# Mac, and building that gn revision from source fails against the current libc++
# at its 10.9 deployment target. The newer gn ANGLE pins (3ef636ac), which
# scripts/bootstrap_angle.py builds from source into .deps/gn/out/gn, generates
# this Skia revision unchanged, so bin/gn is a copy of it.
#
# ARGS differ from Linux only where the Mac defaults would change the build:
#  cc/cxx clang: Xcode clang (gn/BUILDCONFIG.gn forces is_clang on mac anyway).
#  skia_gl_standard="": the mac default "gl" sets SK_ASSUME_GL, compiling out the
#    GLES interface (GrGLTypes.h), so GrGLMakeAssembledInterface would build a
#    desktop-GL interface on ANGLE's ES context; "" is runtime detection, as on Linux.
#  -DSK_R32_SHIFT=16: skia_public adds it only on Linux; native/gpu.cpp is compiled
#    with it, and the mac default is 0 (SkTypes.h).
#  fonts off (fonthost_mac, freetype, fontconfig): gpu.cpp uses no SkFont/SkTypeface,
#    and the mac default CoreText port would link CoreText/CoreGraphics.
#  -DSK_DISABLE_LEGACY_GL_MAKE_NATIVE_INTERFACE: drops GrGLMakeNativeInterface, the
#    only code that would dlopen OpenGL.framework's libGL.dylib.
#  skia_use_metal/angle false, xcode_sysroot: the Xcode SDK, given explicitly.
#  No skia_use_system_zlib: with every codec and font port off, gn reports that it
#    has no effect on mac.
#
# The checkout must be clean, at REVISION, with origin SKIA_URL (a tree fetched
# before origin was recorded is accepted when .git/FETCH_HEAD names SKIA_URL at
# REVISION, and origin is then added). A tree with no build record is taken over
# only when ninja -n prints "ninja: no work to do."; file times are not trusted.
# Skia's LICENSE (it also covers modules/skcms) and a PROVENANCE file are
# installed in .local/share/licenses/skia. The Linux main() above is unchanged
# and still fetches without an origin remote.
DARWIN_OUT = 'out/shiraz-darwin'
DARWIN_ARGS = """is_official_build=true is_debug=false cc="clang" cxx="clang++" target_os="mac" target_cpu="arm64"
skia_use_gl=true skia_gl_standard="" skia_use_metal=false skia_use_angle=false skia_use_vulkan=false
skia_use_icu=false skia_use_harfbuzz=false
skia_enable_pdf=false skia_enable_svg=false skia_enable_skottie=false skia_enable_tools=false
skia_use_expat=false skia_use_libjpeg_turbo_decode=false skia_use_libjpeg_turbo_encode=false
skia_use_libpng_decode=false skia_use_libpng_encode=false skia_use_libwebp_decode=false
skia_use_libwebp_encode=false skia_use_wuffs=false skia_use_piex=false skia_use_perfetto=false
skia_use_no_png_encode=true skia_use_no_jpeg_encode=true skia_use_no_webp_encode=true
skia_use_fonthost_mac=false skia_use_freetype=false skia_use_fontconfig=false
xcode_sysroot="{sdk}"
extra_cflags=["-fPIC","-DSK_R32_SHIFT=16","-DSK_DISABLE_LEGACY_GL_MAKE_NATIVE_INTERFACE"]"""
DARWIN_LIBRARIES = ('libskia.a', 'libpathkit.a')
SKIA_URL = 'https://skia.googlesource.com/skia'


def darwin_checkout_problem(angle):
    """None when SOURCE is a clean checkout of SKIA_URL at REVISION, else why not."""
    origin = angle.git(SOURCE, 'remote', 'get-url', 'origin', check=False)
    if origin is None:
        fetch_head = SOURCE / '.git/FETCH_HEAD'
        lines = fetch_head.read_text().splitlines() if fetch_head.is_file() else []
        if lines and lines[0].split()[0] == REVISION and lines[0].endswith(f' of {SKIA_URL}'):
            angle.git(SOURCE, 'remote', 'add', 'origin', SKIA_URL)
            origin = SKIA_URL
    if origin != SKIA_URL:
        return f'origin is {origin}, expected {SKIA_URL}'
    head = angle.git(SOURCE, 'rev-parse', 'HEAD', check=False)
    if head != REVISION:
        return f'HEAD is {head}, expected {REVISION}'
    changes = angle.local_changes(SOURCE)
    return f'local changes:\n{changes}' if changes else None


def main_darwin():
    import hashlib
    import platform
    import shutil
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import bootstrap_angle as angle
    if platform.machine() != 'arm64':
        raise SystemExit('the macOS Skia build is for arm64 hosts only')
    if sys.version_info < (3, 11):
        raise SystemExit(f'{sys.executable} {platform.python_version()} is too old: gn runs Skia build scripts '
                         'with it; use python3 3.11 or newer (the macOS /usr/bin/python3 3.9 is too old)')
    stamp = angle.BUILD / 'skia.stamp'
    out = SOURCE / DARWIN_OUT
    outputs = [out / name for name in DARWIN_LIBRARIES]
    license_dir = angle.PREFIX / 'share/licenses/skia'
    installed = [license_dir / 'LICENSE', license_dir / 'PROVENANCE']
    args = DARWIN_ARGS.format(sdk=angle.sdk())
    script_sha = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    gn_sha = hashlib.sha256(angle.GN_BIN.read_bytes()).hexdigest() if angle.GN_BIN.is_file() else 'missing'
    normalized = repr(sorted(angle.normalize_gn(args.replace('\n', ' ')).items()))
    build_key = angle.sha256('\n'.join([REVISION, normalized, gn_sha, angle.toolchain_identity()]))
    key = angle.sha256('\n'.join([build_key, script_sha]))
    if (stamp.is_file() and stamp.read_text().splitlines()[:1] == [key]
            and all(p.is_file() for p in outputs + installed)):
        print('== bootstrap_skia: up to date (stamp matches)', flush=True)
        return
    stamp.unlink(missing_ok=True)
    if not SOURCE.exists():
        SOURCE.mkdir(parents=True)
        run('git', 'init', '-q')
        run('git', 'remote', 'add', 'origin', SKIA_URL)
        run('git', 'fetch', '-q', '--depth=1', 'origin', REVISION)
        run('git', 'checkout', '-q', '--detach', 'FETCH_HEAD')
    problem = darwin_checkout_problem(angle)
    if problem:
        raise SystemExit(f'{SOURCE}: {problem}\nRemove it (or revert the changes) and rerun.')
    angle.ensure_gn()
    gn_sha = hashlib.sha256(angle.GN_BIN.read_bytes()).hexdigest()
    build_key = angle.sha256('\n'.join([REVISION, normalized, gn_sha, angle.toolchain_identity()]))
    key = angle.sha256('\n'.join([build_key, script_sha]))
    gn = SOURCE / 'bin/gn'
    if not gn.is_file() or hashlib.sha256(gn.read_bytes()).hexdigest() != gn_sha:
        temporary = gn.with_name('gn.tmp')
        shutil.copyfile(angle.GN_BIN, temporary)
        temporary.chmod(0o755)
        os.replace(temporary, gn)  # a new vnode: never rewrite a Mach-O in place
    record = out / 'shiraz-build.txt'
    current = out / 'args.gn'
    args_match = current.is_file() and angle.normalize_gn(current.read_text()) == angle.normalize_gn(args)
    if args_match and angle.record_matches(record, build_key) and all(p.is_file() for p in outputs):
        print(f'== bootstrap_skia: {DARWIN_OUT} is current (build record matches); gn gen and ninja skipped',
              flush=True)
    elif (args_match and not record.exists() and all(p.is_file() for p in outputs)
          and angle.ninja_reports_current(out, ['skia', 'pathkit'])):
        print(f'== bootstrap_skia: take over {DARWIN_OUT}: no build record, ninja -n reports no work to do; '
              'gn gen and ninja skipped', flush=True)
        record.write_text(f'{build_key}\ntaken over: ninja -n reported no work to do\n')
    else:
        record.unlink(missing_ok=True)
        env = angle.build_env()
        subprocess.run([str(gn), 'gen', DARWIN_OUT, '--args=' + args, f'--script-executable={sys.executable}'],
                       cwd=SOURCE, env=env, check=True)
        subprocess.run([angle.ninja(), '-C', DARWIN_OUT, f'-j{angle.ncpu()}', 'skia', 'pathkit'],
                       cwd=SOURCE, env=env, check=True)
        record.write_text(f'{build_key}\nbuilt by scripts/bootstrap_skia.py\n')
    shutil.rmtree(license_dir, ignore_errors=True)
    license_dir.mkdir(parents=True)
    shutil.copyfile(SOURCE / 'LICENSE', license_dir / 'LICENSE')
    (license_dir / 'PROVENANCE').write_text('\n'.join([
        f'Skia {REVISION}', f'source: {SKIA_URL} (git, shallow, unmodified)',
        f'gn {angle.GN_URL} {angle.GN_COMMIT} sha256 {gn_sha} (build tool only, built by bootstrap_angle.py)',
        f'gn --script-executable: {angle.python_identity()}',
        'LICENSE: BSD-3-Clause, Google Inc.; it also covers modules/skcms, whose sources point at it.',
        f'Built by scripts/bootstrap_skia.py main_darwin() into {DARWIN_OUT} ({", ".join(DARWIN_LIBRARIES)});',
        'normalised gn args:', *(f'  {name} = {value}' for name, value in sorted(angle.normalize_gn(
            args.replace('\n', ' ')).items())), '']))
    angle.BUILD.mkdir(parents=True, exist_ok=True)
    sizes = ' '.join(f'{p.name}={p.stat().st_size}' for p in outputs)
    stamp.write_text(f'{key}\nskia {REVISION}\ngn {angle.GN_COMMIT} sha256 {gn_sha}\n'
                     f'args sha256 {angle.sha256(normalized)}\nscript sha256 {script_sha}\n'
                     f'outputs {DARWIN_OUT} {sizes}\n')
    print(f'== bootstrap_skia: {DARWIN_OUT} ready', flush=True)


if __name__ == '__main__':
    import platform
    main_darwin() if platform.system() == 'Darwin' else main()
