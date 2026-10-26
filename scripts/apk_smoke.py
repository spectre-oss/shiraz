#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Execute the unchanged stock-SDK fixture APK through the public run command."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(locale, toast_mode='show', teardown=False, preference_mode=''):
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    original = hashlib.sha256(apk.read_bytes()).hexdigest()
    with zipfile.ZipFile(apk) as archive:
        large = archive.getinfo('assets/large-stream.dat')
        if large.compress_type != zipfile.ZIP_DEFLATED or large.file_size <= 16 * 1024 * 1024:
            raise SystemExit('Fixture must exercise large compressed asset streaming')
        if archive.getinfo('assets/region.txt').compress_type != zipfile.ZIP_STORED or archive.getinfo('assets/compressed.dat').compress_type != zipfile.ZIP_DEFLATED:
            raise SystemExit('Fixture must exercise stored and compressed asset methods')
    output = ROOT / 'build/apk-fixture/frame.ppm'
    output.unlink(missing_ok=True)
    env = {**os.environ, 'SHIRAZ_FIXTURE_LINGER': '1', 'SHIRAZ_AUDIO_DEVICE': 'null', 'SHIRAZ_FIXTURE_PREFERENCES': preference_mode, 'SHIRAZ_FIXTURE_ASYNC_TEARDOWN': '1' if teardown else '0', 'SHIRAZ_FIXTURE_TOAST_MODE': toast_mode, 'SHIRAZ_FIXTURE_INPUT': '1', 'SHIRAZ_FIXTURE_LOCALE': locale, 'PYTHONPATH': str(ROOT / 'src'), 'SDL_VIDEODRIVER': 'dummy', 'SDL_RENDER_DRIVER': 'software'}
    env.pop('SHIRAZ_FIXTURE_DIALOG', None)
    env.pop('SHIRAZ_FIXTURE_ASYNC_FAIL', None)
    env.pop('SHIRAZ_FIXTURE_CALLBACK_FAIL', None)
    env.pop('SHIRAZ_FIXTURE_POINTER_FAIL', None)
    command = [sys.executable, '-m', 'shiraz', 'run', str(apk), '--width', '96', '--height', '160',
               '--frames', str(160 if toast_mode in ('expire', 'queue') else 12), '--snapshot', str(output), '--timeout', '30']
    env.pop('SHIRAZ_LIVE_SNAPSHOT', None)
    if toast_mode == 'expire':
        env['SHIRAZ_LIVE_SNAPSHOT'] = '1'
        observed_live = False
        with subprocess.Popen(command, cwd=ROOT, env=env) as process:
            deadline = time.monotonic() + 40
            while process.poll() is None and time.monotonic() < deadline:
                if output.exists():
                    data = output.read_bytes()
                    header = b'P6\n96 160\n255\n'
                    if not data.startswith(header) or len(data) != len(header) + 96 * 160 * 3:
                        raise AssertionError('Live capture exposed a partial frame')
                    observed_live = True
                time.sleep(.01)
            if process.wait(timeout=5) != 0:
                raise AssertionError('Live capture fixture failed')
        if not observed_live:
            raise AssertionError('No app frame observed while the process was running')
    else:
        subprocess.run(command, cwd=ROOT, env=env, check=True)
    if hashlib.sha256(apk.read_bytes()).hexdigest() != original:
        raise SystemExit('Fixture APK was modified')
    work = ROOT / 'build/runs' / original
    result = json.loads((work / 'result.json').read_text())
    if result['exit_status'] != 0 or not result['apk_unchanged'] or result['runtime'] != 'standalone-art':
        raise SystemExit('Fixture did not exit successfully through ART')
    stdout = (work / 'stdout.log').read_text()
    for marker in ('SHIRAZ_FIXTURE_LINGERING_WORKER_STARTED', 'SHIRAZ_RUNTIME_CLEANUP_COMPLETE'):
        if marker not in stdout.splitlines(): raise SystemExit('Missing process-shutdown marker: ' + marker)
    expected_identity = 'SHIRAZ_FIXTURE_BUILD_IDENTITY Shiraz/shiraz/linux:8.1/shiraz/' + (ROOT / 'VERSION').read_text().strip() + ':host/unsigned'
    if expected_identity not in stdout.splitlines(): raise SystemExit('Incorrect runtime build identity/version')
    for marker in ('TEXT_BOUNDS_GLYPH_PIXELS_OK', 'STATIC_LAYOUT_WRAP_METRICS_PIXELS_OK', 'BORING_LAYOUT_METRICS_RENDER_OK', 'TEXT_DIRECTION_HEURISTICS_OK', 'TEXT_SCALE_METRICS_RASTER_OK', 'PAINT_HINTING_RASTER_OK', 'ACCESSIBILITY_DELEGATE_STATE_ACTIONS_OK', 'VIEW_CONSTRUCTOR_STYLE_CASCADE_OK', 'VIEW_ANIMATION_TRAVERSAL_OK', 'ACTIVITY_TITLE_SDL_OK', 'TEXTVIEW_RENDER_LAYOUT_INPUT_OK', 'TEXTVIEW_XML_OK', 'WINDOW_INSETS_VALUES_OK', 'WINDOW_INSETS_DESKTOP_OK', 'SHIRAZ_ATTACH_LIVE_OK', 'SHIRAZ_DETACH_LIVE_OK', 'SHIRAZ_FOREGROUND_QUEUE_OK', 'SHIRAZ_FOREGROUND_XML_OK', 'SHIRAZ_FIXTURE_LINEAR_XML_OK', 'SHIRAZ_FIXTURE_LINEARLAYOUT_OK', 'SHIRAZ_FRAGMENT_TRANSACTIONS_OK', 'SHIRAZ_FRAGMENT_LIFECYCLE_OK', 'SHIRAZ_FIXTURE_ACTIVITY_IDENTITY_OK', 'SHIRAZ_FIXTURE_STYLED_ATTRIBUTES_OK', 'SHIRAZ_FIXTURE_ACTIVITY_INFO_OK', 'SHIRAZ_FIXTURE_WINDOW_OK', 'SHIRAZ_FIXTURE_SAVED_STATE_OK', 'SHIRAZ_FIXTURE_CONFIGURATION_CONTEXT_OK', 'SHIRAZ_FIXTURE_CONFIGURATION_MERGE_OK', 'SHIRAZ_FIXTURE_SERVICE_METADATA_OK', 'SHIRAZ_FIXTURE_STARTED_SERVICES_OK', 'SHIRAZ_FIXTURE_SERVICE_TEARDOWN_OK', 'SHIRAZ_FIXTURE_ACTIVITY_HIDDEN_OK', 'SHIRAZ_FIXTURE_COMPONENT_CALLBACKS_OK', 'SHIRAZ_FIXTURE_COMPONENT_HIDDEN_OK', 'SHIRAZ_FIXTURE_INFLATER_OK', 'SHIRAZ_FIXTURE_COMPILED_XML_OK', 'SHIRAZ_FIXTURE_TREE_OBSERVER_OK', 'SHIRAZ_FIXTURE_TREE_OBSERVER_LIVE_OK', 'SHIRAZ_FIXTURE_BLE_VALUES_OK', 'SHIRAZ_FIXTURE_SYSTEM_RESOURCES_OK', 'SHIRAZ_FIXTURE_LAYOUT_LISTENERS_OK', 'SHIRAZ_FIXTURE_COLOR_MATRIX_OK', 'SHIRAZ_FIXTURE_PATH_MEASURE_OK', 'SHIRAZ_FIXTURE_ROUNDED_GEOMETRY_OK', 'SHIRAZ_FIXTURE_DASH_EFFECT_OK', 'SHIRAZ_FIXTURE_FAKE_BOLD_OK', 'SHIRAZ_FIXTURE_LAYERS_OK', 'SHIRAZ_FIXTURE_TEXT_PAINT_OK', 'SHIRAZ_FIXTURE_VECTORS_OK', 'SHIRAZ_FIXTURE_PATHS_OK', 'SHIRAZ_FIXTURE_PNG_IMAGES_OK', 'SHIRAZ_FIXTURE_PNG_CONTEXT_OK', 'SHIRAZ_FIXTURE_TYPED_RESOURCES_OK', 'SHIRAZ_FIXTURE_COLOR_FILTERS_OK', 'SHIRAZ_FIXTURE_COLOR_FILTER_VIEW_OK', 'SHIRAZ_FIXTURE_FRAMELAYOUT_OK', 'SHIRAZ_FIXTURE_IMAGEVIEW_QUEUE_OK', 'SHIRAZ_FIXTURE_IMAGEVIEW_OK', 'SHIRAZ_FIXTURE_DRAWABLE_DETACH_OK', 'SHIRAZ_FIXTURE_DRAWABLES_OK', 'SHIRAZ_FIXTURE_DRAWABLE_VIEW_OK', 'SHIRAZ_FIXTURE_LRU_CACHE_OK', 'SHIRAZ_FIXTURE_KEY_VALUES_OK', 'SHIRAZ_FIXTURE_BITMAPS_OK', 'SHIRAZ_FIXTURE_BROADCASTS_OK', 'SHIRAZ_FIXTURE_NOTIFICATION_CHANNELS_OK', 'SHIRAZ_FIXTURE_SQLITE_API_OK', 'SHIRAZ_FIXTURE_XML_OK', 'SHIRAZ_FIXTURE_LOCATION_VALUES_OK', 'SHIRAZ_FIXTURE_SENSORS_OK', 'SHIRAZ_FIXTURE_DISPLAY_OK', 'SHIRAZ_FIXTURE_TYPEFACE_OK', 'SHIRAZ_FIXTURE_STROKE_OK', 'SHIRAZ_FIXTURE_SOUNDPOOL_OK', 'SHIRAZ_FIXTURE_ASSETS_OK', 'SHIRAZ_FIXTURE_AUDIO_ATTRIBUTES_OK', 'SHIRAZ_FIXTURE_COLOR_RESOURCES_OK', 'SHIRAZ_FIXTURE_BUNDLE_OK', 'SHIRAZ_FIXTURE_THEME_OK', 'SHIRAZ_FIXTURE_DATE_FORMAT_OK', 'SHIRAZ_FIXTURE_CONFIGURATION_OK', 'SHIRAZ_FIXTURE_STORAGE_OK', 'SHIRAZ_FIXTURE_PREFERENCES_OK', 'SHIRAZ_FIXTURE_TEXT_UTILS_OK', 'SHIRAZ_FIXTURE_COLOR_OK'):
        if marker not in stdout.splitlines(): raise SystemExit('Missing APK callback marker: ' + marker)
    if preference_mode == 'read' and 'SHIRAZ_FIXTURE_NOTIFICATION_PERSISTED' not in stdout.splitlines():
        raise SystemExit('Notification channels were not verified across ART sessions')
    if preference_mode == 'read' and 'SHIRAZ_FIXTURE_SQLITE_PERSISTED' not in stdout.splitlines():
        raise SystemExit('SQLite persistence was not verified across ART sessions')
    if preference_mode == 'read' and 'SHIRAZ_FIXTURE_PREFERENCES_PERSISTED' not in stdout.splitlines():
        raise SystemExit('Preferences were not verified across ART sessions')
    if preference_mode == 'read' and 'SHIRAZ_FIXTURE_STORAGE_PERSISTED' not in stdout.splitlines():
        raise SystemExit('App storage was not verified across ART sessions')
    for marker in ('SHIRAZ_FIXTURE_APPLICATION_OK', 'SHIRAZ_FIXTURE_DRAW_OK', 'SHIRAZ_FIXTURE_LIFECYCLE_OK', 'SHIRAZ_FIXTURE_PROCESS_OK', 'SHIRAZ_FIXTURE_PACKAGE_OK', 'SHIRAZ_FIXTURE_RESOURCES_OK', 'SHIRAZ_FIXTURE_STRICTMODE_OK', 'SHIRAZ_FIXTURE_QUEUE_OK', 'SHIRAZ_FIXTURE_INPUT_OK', 'SHIRAZ_FIXTURE_TEXT_OK', 'SHIRAZ_FIXTURE_TOAST_OK', 'SHIRAZ_FIXTURE_ASYNC_OK'):
        if marker not in stdout.splitlines():
            raise SystemExit('Missing APK callback marker: ' + marker)
    if 'SHIRAZ_FIXTURE_KEYS_OK' not in stdout.splitlines():
        raise SystemExit('Native keyboard input did not reach the focused SDK View')
    if 'D/ShirazFixture: SHIRAZ_FIXTURE_LOG_OK' not in (work / 'stderr.log').read_text():
        raise SystemExit('Android logging did not reach the application log')
    if 'W/ShirazFixture: SHIRAZ_FIXTURE_LOG_POLICY_OK' not in (work / 'stderr.log').read_text():
        raise SystemExit('Logger did not preserve output under an active I/O policy')
    if 'D/StrictMode: policy violation: disk read' not in (work / 'stderr.log').read_text():
        raise SystemExit('StrictMode did not log an actual disk read')
    if 'SHIRAZ_FIXTURE_LOG_FILTERED' in (work / 'stderr.log').read_text():
        raise SystemExit('Android logging did not filter a verbose message')
    image = output.read_bytes()
    header = b'P6\n96 160\n255\n'
    if not image.startswith(header) or len(image) != len(header) + 96 * 160 * 3:
        raise SystemExit('Invalid native surface frame capture')
    pixels = image[len(header):]
    for x, y, expected in [(0, 0, (136, 16, 24)), (47, 79, (136, 16, 24)), (48, 80, (16, 32, 48)), (95, 159, (16, 32, 48))]:
        index = (y * 96 + x) * 3
        if tuple(pixels[index:index + 3]) != expected:
            raise SystemExit(f'Canvas clipping/alpha pixel mismatch at {x},{y}')
    changed = 0
    for y in range(96, 128):
        for x in range(16, 80):
            index = (y * 96 + x) * 3
            if tuple(pixels[index:index + 3]) != (16, 32, 48): changed += 1
    visible = toast_mode in ('show', 'end', 'queue', 'update')
    if (visible and changed == 0) or (not visible and changed != 0):
        raise SystemExit('Toast overlay visibility/cancel/expiry did not match captured pixels')
    if teardown and any((ROOT / ('data/apps/org.shiraz.fixture/cache/async-teardown-' + str(i))).read_bytes() != b'ok\n' for i in range(2)):
        raise SystemExit('Session teardown did not interrupt/close an active AsyncTask worker')
    print('UNCHANGED_APK_ACTIVITY_OK ' + locale + ' toast=' + toast_mode)
    return pixels


def dialog_run():
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    output = ROOT / 'build/apk-fixture/dialog-dismissed.ppm'
    output.unlink(missing_ok=True)
    env = {**os.environ, 'SHIRAZ_AUDIO_DEVICE': 'null', 'PYTHONPATH': str(ROOT / 'src'),
           'SDL_VIDEODRIVER': 'dummy', 'SDL_RENDER_DRIVER': 'software', 'SHIRAZ_FIXTURE_DIALOG': '1',
           'SHIRAZ_FIXTURE_INPUT': '0', 'SHIRAZ_FIXTURE_LOCALE': 'en', 'SHIRAZ_FIXTURE_TOAST_MODE': 'cancel',
           'SHIRAZ_FIXTURE_ASYNC_TEARDOWN': '0', 'SHIRAZ_FIXTURE_PREFERENCES': ''}
    for key in ('SHIRAZ_FIXTURE_ASYNC_FAIL', 'SHIRAZ_FIXTURE_CALLBACK_FAIL', 'SHIRAZ_FIXTURE_POINTER_FAIL'):
        env.pop(key, None)
    subprocess.run([sys.executable, '-m', 'shiraz', 'run', str(apk), '--width', '96', '--height', '160',
                    '--frames', '12', '--snapshot', str(output), '--timeout', '30'], cwd=ROOT, env=env, check=True)
    work = ROOT / 'build/runs' / digest
    result = json.loads((work / 'result.json').read_text())
    stdout = (work / 'stdout.log').read_text()
    if result['exit_status'] != 0 or not result['apk_unchanged'] or hashlib.sha256(apk.read_bytes()).hexdigest() != digest:
        raise SystemExit('Dialog fixture APK identity/execution failure')
    if 'SHIRAZ_FIXTURE_DIALOG_APK_OK' not in stdout.splitlines():
        raise SystemExit('SDK dialog did not draw, receive native SDL pointer and dismiss')
    image = output.read_bytes()
    header = b'P6\n96 160\n255\n'
    if not image.startswith(header) or len(image) != len(header) + 96*160*3:
        raise SystemExit('Invalid modal dismissal frame')
    index = len(header) + (80*96+48)*3
    if image[index:index+3] != bytes((16,32,48)):
        raise SystemExit('Dialog did not reveal the underlying Activity after SDL click')
    (ROOT / 'build/dialog-apk-stdout.log').write_text(stdout)
    (ROOT / 'build/dialog-apk-stderr.log').write_bytes((work / 'stderr.log').read_bytes())
    print('UNCHANGED_APK_DIALOG_SDL_INPUT_OK')


def callback_failure(pointer=False):
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    original = hashlib.sha256(apk.read_bytes()).hexdigest()
    env = {**os.environ, 'SHIRAZ_AUDIO_DEVICE': 'null', 'PYTHONPATH': str(ROOT / 'src'), 'SDL_VIDEODRIVER': 'dummy',
           'SDL_RENDER_DRIVER': 'software', 'SHIRAZ_FIXTURE_LOCALE': 'en',
           'SHIRAZ_FIXTURE_ASYNC_TEARDOWN': '0', 'SHIRAZ_FIXTURE_CALLBACK_FAIL': '1' if not pointer else '', 'SHIRAZ_FIXTURE_INPUT': '1' if pointer else '0'}
    env.pop('SHIRAZ_FIXTURE_ASYNC_FAIL', None)
    env.pop('SHIRAZ_FIXTURE_TOAST_MODE', None)
    env.pop('SHIRAZ_FIXTURE_POINTER_FAIL', None)
    if pointer:
        env.pop('SHIRAZ_FIXTURE_CALLBACK_FAIL')
        env['SHIRAZ_FIXTURE_POINTER_FAIL'] = '1'
    result = subprocess.run([sys.executable, '-m', 'shiraz', 'run', str(apk), '--width', '96', '--height', '160',
                             '--frames', '4', '--timeout', '30'], cwd=ROOT, env=env, capture_output=True, text=True, timeout=35)
    if result.returncode != 1:
        raise SystemExit('A Java frame callback exception did not fail the activity')
    work = ROOT / 'build/runs' / original
    evidence = json.loads((work / 'result.json').read_text())
    stderr = (work / 'stderr.log').read_text()
    if evidence['exit_status'] != 10 or not evidence['apk_unchanged'] or ('java.lang.AssertionError: SHIRAZ_FIXTURE_EXPECTED_' + ('POINTER' if pointer else 'CALLBACK') + '_FAILURE') not in stderr:
        raise SystemExit('JNI presentation did not preserve the original Java exception')
    print('POINTER_CALLBACK_EXCEPTION_OK' if pointer else 'FRAME_CALLBACK_EXCEPTION_OK')


def async_failure(kind):
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    original = hashlib.sha256(apk.read_bytes()).hexdigest()
    env = {**os.environ, 'SHIRAZ_AUDIO_DEVICE': 'null', 'PYTHONPATH': str(ROOT / 'src'), 'SDL_VIDEODRIVER': 'dummy', 'SDL_RENDER_DRIVER': 'software',
           'SHIRAZ_FIXTURE_ASYNC_TEARDOWN': '0', 'SHIRAZ_FIXTURE_ASYNC_FAIL': kind, 'SHIRAZ_FIXTURE_INPUT': '0', 'SHIRAZ_FIXTURE_LOCALE': 'en'}
    for key in ('SHIRAZ_FIXTURE_CALLBACK_FAIL', 'SHIRAZ_FIXTURE_POINTER_FAIL', 'SHIRAZ_FIXTURE_TOAST_MODE'):
        env.pop(key, None)
    result = subprocess.run([sys.executable, '-m', 'shiraz', 'run', str(apk), '--width', '96', '--height', '160',
                             '--frames', '4', '--timeout', '30'], cwd=ROOT, env=env, capture_output=True, text=True, timeout=35)
    work = ROOT / 'build/runs' / original
    evidence = json.loads((work / 'result.json').read_text())
    stderr = (work / 'stderr.log').read_text()
    if result.returncode != 1 or evidence['exit_status'] != 10 or not evidence['apk_unchanged'] or 'SHIRAZ_FIXTURE_EXPECTED_ASYNC_FAILURE' not in stderr:
        raise SystemExit('AsyncTask worker failure was not propagated to activity failure: ' + kind)
    print('ASYNC_FAILURE_PROPAGATION_OK ' + kind)


def keyboard_failure(kind):
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    original = hashlib.sha256(apk.read_bytes()).hexdigest()
    env = {**os.environ, 'SHIRAZ_AUDIO_DEVICE': 'null', 'PYTHONPATH': str(ROOT / 'src'), 'SDL_VIDEODRIVER': 'dummy', 'SDL_RENDER_DRIVER': 'software', 'SHIRAZ_FIXTURE_INPUT': '1', 'SHIRAZ_FIXTURE_LOCALE': 'en'}
    for name in ('ASYNC_FAIL', 'CALLBACK_FAIL', 'POINTER_FAIL', 'KEY_FAIL', 'TEXT_FAIL', 'BAD_TEXT', 'TOAST_MODE'):
        env.pop('SHIRAZ_FIXTURE_' + name, None)
    env['SHIRAZ_FIXTURE_' + {'key': 'KEY_FAIL', 'text': 'TEXT_FAIL', 'malformed': 'BAD_TEXT'}[kind]] = '1'
    result = subprocess.run([sys.executable, '-m', 'shiraz', 'run', str(apk), '--width', '96', '--height', '160', '--frames', '4', '--timeout', '30'], cwd=ROOT, env=env, capture_output=True, text=True, timeout=35)
    work = ROOT / 'build/runs' / original
    evidence = json.loads((work / 'result.json').read_text()); stderr = (work / 'stderr.log').read_text()
    marker = 'Invalid UTF-8 code point in SDL text' if kind == 'malformed' else 'SHIRAZ_FIXTURE_EXPECTED_' + kind.upper() + '_FAILURE'
    if result.returncode != 1 or evidence['exit_status'] != 10 or not evidence['apk_unchanged'] or marker not in stderr:
        raise SystemExit('Keyboard input failure was not propagated: ' + kind)
    print('KEYBOARD_FAILURE_PROPAGATION_OK ' + kind)


def fragment_failure():
    apk = ROOT / 'build/apk-fixture/fixture.apk'
    original = hashlib.sha256(apk.read_bytes()).hexdigest()
    env = {key: value for key, value in os.environ.items() if not key.startswith('SHIRAZ_FIXTURE_')}
    env.update(SHIRAZ_AUDIO_DEVICE='null', PYTHONPATH=str(ROOT / 'src'), SDL_VIDEODRIVER='dummy', SDL_RENDER_DRIVER='software', SHIRAZ_FIXTURE_FRAGMENT_FAIL='1')
    result = subprocess.run([sys.executable, '-m', 'shiraz', 'run', str(apk), '--frames', '2', '--timeout', '30'], cwd=ROOT, env=env, capture_output=True, text=True, timeout=35)
    work = ROOT / 'build/runs' / original
    stderr = (work / 'stderr.log').read_text()
    evidence = json.loads((work / 'result.json').read_text())
    if result.returncode != 1 or evidence['exit_status'] != 10 or not evidence['apk_unchanged'] or 'java.lang.IllegalStateException: SHIRAZ_FRAGMENT_PRIMARY_FAILURE' not in stderr or 'Suppressed: java.lang.IllegalStateException: SHIRAZ_FRAGMENT_CLEANUP_FAILURE' not in stderr:
        raise SystemExit('Fragment cleanup replaced the primary startup error')
    print('FRAGMENT_PRIMARY_FAILURE_PRESERVED_OK')


if __name__ == '__main__':
    fragment_failure()
    async_failure('task')
    async_failure('runnable')
    callback_failure()
    callback_failure(pointer=True)
    keyboard_failure('key')
    keyboard_failure('text')
    keyboard_failure('malformed')
    run('en', preference_mode='write')
    run('fr', preference_mode='read')
    run('en', 'cancel', teardown=True)
    run('en', 'expire')
    expected = run('en', 'end')
    updated = run('en', 'update')
    if updated != expected:
        raise SystemExit('Live toast text update did not render End')
    queued = run('en', 'queue')
    if queued != expected:
        raise SystemExit('Queued toast did not advance past a canceled pending entry to End')
    dialog_run()
