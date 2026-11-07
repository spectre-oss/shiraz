#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""macOS port of scripts/gpu_canvas_smoke.py: GPU Canvas and its software fallback.

Runs tests/java/shiraz/GpuCanvasSmoke.java unchanged on the host OpenJDK and on
the darwin ART host tree, in the cases that exist on macOS:
  gpu       Cocoa window, SDL opengles2 renderer on ANGLE Metal: Skia Ganesh canvas
  fallback  SDL dummy video driver (no GL context): software canvas
  forced    the gpu case with SHIRAZ_CANVAS=software: software canvas
  metal     Cocoa window, SDL Metal renderer (no GL context): software canvas
The Linux offscreen case (SDL offscreen driver with a surfaceless EGL GL
context) is replaced by gpu, and the Linux cpu-driver case
(LIBGL_ALWAYS_SOFTWARE, a Mesa llvmpipe device that presentation.cpp rejects by
name) has no macOS counterpart: the in-tree ANGLE has only the Metal backend,
no software GL device. --offscreen-probe also runs SDL's offscreen driver on
ANGLE and records what it gives, without asserting a backend.
The JVM gets -XstartOnFirstThread (Cocoa needs the process main thread; ART's
dalvikvm already runs main there) and a directory of lib*.dylib names for the
build/macos lib*.so files, since OpenJDK maps System.loadLibrary("x") to libx.dylib.
"""
import argparse
import fcntl
import json
import os
import shutil
import struct
import subprocess
import time
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, environment

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'build/macos'
WORK = NATIVE / 'gpu-canvas-smoke'
MARKER = 'GPU_CANVAS_OK'


def java_home():
    return Path(subprocess.run(['/usr/libexec/java_home'], check=True, capture_output=True, text=True).stdout.strip())


def jvm_native(directory):
    """lib*.dylib symlinks to build/macos/lib*.so (dyld resolves @loader_path through them)."""
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)
    for library in sorted(NATIVE.glob('lib*.so')):
        (directory / (library.stem + '.dylib')).symlink_to(os.path.relpath(library, directory))


def angle(env, renderer):
    env['SHIRAZ_EGL_LIBRARY'] = str(NATIVE / 'libEGL.dylib')
    env['SDL_VIDEO_EGL_DRIVER'] = str(NATIVE / 'libEGL.dylib')
    env['SDL_VIDEO_GL_DRIVER'] = str(NATIVE / 'libGLESv2.dylib')
    if renderer:
        env['SDL_RENDER_DRIVER'] = renderer


def cases(offscreen_probe):
    def cocoa(renderer, **extra):
        def setup(env):
            for name in ('SDL_VIDEODRIVER', 'SHIRAZ_HOST_SDL_VIDEODRIVER', 'SHIRAZ_HOST_SDL_RENDER_DRIVER', 'SHIRAZ_CANVAS'):
                env.pop(name, None)
            angle(env, renderer)
            env.update(extra)
        return setup

    def dummy(env):
        env.pop('SHIRAZ_CANVAS', None)
        angle(env, None)
        env['SDL_VIDEODRIVER'] = 'dummy'
        env.pop('SDL_RENDER_DRIVER', None)

    def offscreen(env):
        env.pop('SHIRAZ_CANVAS', None)
        angle(env, None)
        env['SDL_VIDEODRIVER'] = 'offscreen'
        env.pop('SDL_RENDER_DRIVER', None)

    table = [('gpu', cocoa('opengles2'), 'gpu', 'opengles2'),
             ('fallback', dummy, 'software', 'software'),
             ('forced', cocoa('opengles2', SHIRAZ_CANVAS='software'), 'software', 'opengles2'),
             ('metal', cocoa('metal'), 'software', 'metal')]
    if offscreen_probe:
        table.append(('offscreen-probe', offscreen, None, None))
    return table


def presentation(stderr, prefix):
    for line in stderr.splitlines():
        if prefix in line:
            return line[line.index(prefix):].strip()
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--offscreen-probe', action='store_true', help='also record what SDL offscreen on ANGLE gives (no assertion)')
    parser.add_argument('--timeout', type=int, default=60)
    parser.add_argument('--runtime', choices=('openjdk', 'art', 'both'), default='both')
    args = parser.parse_args()
    if not DARWIN:
        parser.exit(1, 'macOS only; use make gpu-canvas-smoke on Linux\n')
    WORK.mkdir(parents=True, exist_ok=True)
    lock = (WORK / '.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'GPU Canvas smoke is already running\n')
    (WORK / 'result.json').unlink(missing_ok=True)
    required = [NATIVE / name for name in ('libshiraz-gpu.so', 'libshiraz-jni.so', 'libandroid.so', 'libEGL.dylib', 'libGLESv2.dylib',
                                            'libEGL_shim.so', 'libshiraz-bitmap.so', 'libshiraz-path.so', 'libshiraz-text.so')]
    required += [ROOT / 'build/shiraz-framework.jar', ROOT / 'build/framework/classes/android/view/Window.class', HOST / 'bin/dx', HOST / 'bin/dalvikvm']
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        parser.exit(1, 'GPU Canvas smoke prerequisites missing:\n' + '\n'.join(missing) + '\n')
    jdk = java_home()
    classes = WORK / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    classpath = str(ROOT / 'build/framework/classes') + ':' + str(core)
    subprocess.run([str(jdk / 'bin/javac'), '--release', '8', '-Xlint:-options', '-cp', classpath, '-d', str(classes),
                    str(ROOT / 'tests/java/shiraz/GpuCanvasSmoke.java')], check=True)
    links = WORK / 'jvm-native'
    jvm_native(links)
    commands = []
    if args.runtime in ('openjdk', 'both'):
        commands.append(('openjdk', [str(jdk / 'bin/java'), '-XstartOnFirstThread', '--enable-native-access=ALL-UNNAMED', '-Xcheck:jni',
                                     '-Djava.library.path=' + str(links), '-cp', str(classes) + ':' + classpath, 'shiraz.GpuCanvasSmoke']))
    if args.runtime in ('art', 'both'):
        dex = WORK / 'classes.dex'
        subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], env=environment(WORK), check=True)
        jar = WORK / 'smoke.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            entry = zipfile.ZipInfo('classes.dex')
            padding = (-(30 + len(entry.filename) + 4)) % 4
            entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
            archive.writestr(entry, dex.read_bytes())
        commands.append(('art', [str(HOST / 'bin/dalvikvm'), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                                 '-Xcheck:jni', '-Xusejit:false', '-Xint', '-Xnoimage-dex2oat',
                                 '-Djava.library.path=' + str(HOST / 'lib64') + ':' + str(NATIVE),
                                 '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'shiraz.GpuCanvasSmoke']))
    replay = WORK / 'resize.txt'
    replay.write_text('1 resize 192 160\n2 resize 160 192\n')
    results = []
    failures = []
    for backend, setup, expect, renderer in cases(args.offscreen_probe):
        for runtime, command in commands:
            label = backend + '-' + runtime
            env = dict(environment(WORK), SHIRAZ_INPUT_SCRIPT=str(replay))
            setup(env)
            if expect:
                env['EXPECT_CANVAS'] = expect
            else:
                env.pop('EXPECT_CANVAS', None)
            started = time.monotonic()
            try:
                result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=args.timeout, cwd=WORK)
                code, stdout, stderr = result.returncode, result.stdout, result.stderr
            except subprocess.TimeoutExpired as expired:
                code = 'timeout'
                stdout = expired.stdout.decode() if isinstance(expired.stdout, bytes) else (expired.stdout or '')
                stderr = expired.stderr.decode() if isinstance(expired.stderr, bytes) else (expired.stderr or '')
            elapsed = round(time.monotonic() - started, 3)
            (WORK / (label + '-stdout.log')).write_text(stdout)
            (WORK / (label + '-stderr.log')).write_text(stderr)
            canvas = presentation(stderr, 'SHIRAZ_CANVAS ')
            shown = presentation(stderr, 'SHIRAZ_PRESENTATION ')
            gpu = presentation(stderr, 'SHIRAZ_GPU ')
            record = {'case': label, 'rc': code, 'seconds': elapsed, 'canvas': canvas, 'presentation': shown, 'gpu': gpu,
                      'marker': MARKER in stdout.splitlines()}
            results.append(record)
            if expect is None:
                print('GPU_CANVAS_PROBE ' + label + ' ' + json.dumps(record))
                continue
            problems = []
            if code != 0 or not record['marker']:
                problems.append(f'rc={code} marker={record["marker"]}')
            if 'WARNING in native method' in stderr:
                problems.append('JNI warning')
            if not shown or ('renderer=' + renderer + ' ') not in shown + ' ':
                problems.append(f'presentation {shown!r} is not renderer={renderer}')
            wanted = 'backend=skia-ganesh gpu=1' if expect == 'gpu' else 'backend=software gpu=0'
            if not canvas or wanted not in canvas:
                problems.append(f'canvas {canvas!r} lacks {wanted!r}')
            if expect == 'gpu' and (not gpu or 'ANGLE' not in gpu or 'Metal' not in gpu):
                problems.append(f'GPU device {gpu!r} is not ANGLE Metal')
            if problems:
                failures.append(label + ': ' + '; '.join(problems))
                print('GPU_CANVAS_FAIL ' + label + ' ' + '; '.join(problems))
            else:
                print(f'GPU_CANVAS_OK {label} {elapsed}s')
    (WORK / 'result.json').write_text(json.dumps({'cases': results, 'failures': failures, 'component_only': True,
                                                   'not_ported': {'cpu-driver': 'no software GL device in the Metal-only ANGLE build (Linux uses Mesa llvmpipe via LIBGL_ALWAYS_SOFTWARE)'}},
                                                  indent=2) + '\n')
    if failures:
        raise SystemExit('GPU Canvas smoke failed: ' + ' | '.join(failures) + '; inspect ' + str(WORK.relative_to(ROOT)))


if __name__ == '__main__':
    main()
