# SPDX-License-Identifier: GPL-3.0-only
"""Host-specific ART launch commands built by run_activity (Linux and macOS)."""
import contextlib
import io
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz import runtime
from shiraz.apk import FormatError
from shiraz.architecture import native_abi
from shiraz.host_layout import darwin_socket_directory, host_out_dir

RUN = 'build/runs/' + '0' * 64

# Captured from the pre-macOS run_activity (HEAD c665d4c) on a fake Linux host;
# the Linux launch must keep producing exactly this argv and environment.
LINUX_COMMAND = [
    '<HOST>/bin/dalvikvm64',
    '-Xbootclasspath:<HOST>/framework/core-oj-hostdex.jar:<HOST>/framework/core-libart-hostdex.jar:'
    '<HOST>/framework/okhttp-hostdex.jar:<HOST>/framework/bouncycastle-hostdex.jar:<HOST>/framework/wolfssljni-hostdex.jar',
    '-Xnoimage-dex2oat', '-Xint', '-Xusejit:false', '-Xcheck:jni',
    '-Xcompiler:<HOST>/bin/dex2oat', '-Dlog.tag=INFO',
    f'-Djava.library.path=<HOST>/lib64:<ROOT>/build:<ROOT>/{RUN}/app_lib',
    '-Dshiraz.frameworkResources=<ROOT>/build/framework-res/framework-res.apk',
    '-Dshiraz.speechData=<ROOT>/.local/share',
    f'-Djava.io.tmpdir=<ROOT>/{RUN}/tmp', f'-Duser.home=<ROOT>/{RUN}/home',
    '-cp', '<ROOT>/build/shiraz-framework.jar:<ROOT>/app.apk:<ROOT>/build/shiraz-legacy-http.jar',
    'shiraz.runtime.ActivityRunner', 'sample.app', 'android.app.Application', 'sample.app.Main',
    '360', '640', '-1', '<ROOT>/data/apps/sample.app', '-', f'<ROOT>/{RUN}/application.properties']
LINUX_ENV = [
    ('PATH', '/usr/bin:/bin'), ('HOME', '<ROOT>/home'), ('XDG_CONFIG_HOME', '<ROOT>/xdg-config'),
    ('ANDROID_ROOT', '<HOST>'), ('ANDROID_DATA', f'<ROOT>/{RUN}/android-data'),
    ('XDG_CACHE_HOME', f'<ROOT>/{RUN}/cache'),
    ('BOOTCLASSPATH', '<HOST>/framework/core-oj-hostdex.jar:<HOST>/framework/core-libart-hostdex.jar:'
     '<HOST>/framework/okhttp-hostdex.jar:<HOST>/framework/bouncycastle-hostdex.jar:<HOST>/framework/wolfssljni-hostdex.jar'),
    ('LD_LIBRARY_PATH', f'<HOST>/lib64:<ROOT>/.local/lib:<ROOT>/build:<ROOT>/{RUN}/app_lib'),
    ('BIONIC_LD_LIBRARY_PATH', f'<ROOT>/{RUN}/app_lib'),
    ('SHIRAZ_FONT_DIR', '<ROOT>/.local/share/shiraz/fonts'), ('XDG_DATA_DIRS', '<ROOT>/.local/share:/usr/share'),
    ('SHIRAZ_ANDROID_DISPLAY_ID', 'Shiraz 9.9.9'), ('SHIRAZ_ANDROID_RELEASE', '8.1'),
    ('SHIRAZ_ANDROID_FONT_DIR', '<ROOT>/.local/share/shiraz/fonts'),
    ('LD_PRELOAD', '<ROOT>/build/libshiraz-android-process.so:<ROOT>/build/libandroid.so:'
     '<ROOT>/build/libshiraz-resources.so:<ROOT>/build/libshiraz-android-fonts.so')]

DARWIN_BOOT = ':'.join('<HOST>/framework/' + name for name in runtime.JARS)
DARWIN_COMMAND = [
    f'<ROOT>/{RUN}/host/Shiraz.app/Contents/MacOS/ShirazHost', '-Xbootclasspath:' + DARWIN_BOOT, '-Xnoimage-dex2oat', '-Xint', '-Xusejit:false',
    '-Xcheck:jni', '-Dlog.tag=INFO', '-Djava.library.path=<ROOT>/build/macos:<HOST>/lib64',
    '-Dshiraz.frameworkResources=<ROOT>/build/framework-res/framework-res.apk',
    '-Dshiraz.speechData=<ROOT>/.local/share',
    f'-Djava.io.tmpdir=<ROOT>/{RUN}/tmp', f'-Duser.home=<ROOT>/{RUN}/home',
    '-cp', '<ROOT>/build/shiraz-framework.jar:<ROOT>/app.apk',
    'shiraz.runtime.ActivityRunner', 'sample.app', 'android.app.Application', 'sample.app.Main',
    '96', '160', '30', '<ROOT>/data/apps/sample.app', '-', f'<ROOT>/{RUN}/application.properties']


class Launched(Exception):
    pass


class LaunchCommandTests(unittest.TestCase):
    def setUp(self):
        self.report = {'manifest': {'package': 'sample.app', 'application_class': None, 'min_sdk': '27',
                                    'launcher_activities': [{'target': 'sample.app.Main'}]},
                       'native_abis': [], 'host_native_candidate': True, 'apk_sha256': '0' * 64}
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name).resolve()
        (self.root / 'VERSION').write_text('9.9.9\n')

    def tree(self, system, extra=()):
        host = host_out_dir(self.root / 'context/art_standalone', system=system)
        _, _, _, required, _ = runtime.runtime_inputs(system, self.root, host, 'interpreter')
        for path in [*required, *extra]:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.touch()
        if system == 'Darwin':
            (host / 'bin/dalvikvm').write_bytes(b'\xcf\xfa\xed\xfe' + b'fixture')
            self.write_fonts_conf()
        return host

    def write_fonts_conf(self, root=None, cache=None, extra=''):
        """A fonts.conf shaped like the one scripts/bootstrap_darwin_graphics.py writes."""
        root = root or self.root
        cache = cache or root / '.local/var/cache/fontconfig'
        (self.root / '.local/var/cache/fontconfig').mkdir(parents=True, exist_ok=True)
        conf = self.root / '.local/etc/fonts/fonts.conf'
        conf.parent.mkdir(parents=True, exist_ok=True)
        conf.write_text('<?xml version="1.0"?>\n<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">\n'
                        f'<fontconfig>\n  <dir>{root}/.local/share/shiraz/fonts</dir>\n'
                        '  <dir>/System/Library/Fonts</dir>\n  <dir>/Library/Fonts</dir>\n'
                        f'  <cachedir>{cache}</cachedir>\n{extra}'
                        f'  <include ignore_missing="yes">{root}/.local/etc/fonts/conf.d</include>\n</fontconfig>\n')

    def launch(self, system, host, environ, **kwargs):
        captured = {}

        def fake_run(command, cwd, env, work, timeout):
            captured.update(command=command, env=env)
            raise Launched()

        stdout = io.StringIO()
        environ = {'PATH': '/usr/bin:/bin', 'HOME': str(self.root / 'home'),
                   'XDG_CONFIG_HOME': str(self.root / 'xdg-config'), **environ}
        with patch.object(runtime, 'ROOT', self.root), patch.object(runtime, 'HOST', host), \
                patch.object(runtime, 'host_system', return_value=system), \
                patch.object(runtime, 'run_with_live_logs', fake_run), \
                patch.dict(os.environ, environ, clear=True), contextlib.redirect_stdout(stdout):
            with self.assertRaises(Launched):
                runtime.run_activity(self.root / 'app.apk', self.report, **kwargs)
        return captured['command'], captured['env'], stdout.getvalue()

    def expand(self, value, host):
        return value.replace('<HOST>', str(host)).replace('<ROOT>', str(self.root))

    def test_linux_command_and_environment_are_unchanged(self):
        host = self.tree('Linux', (self.root / 'context/art_standalone/out/host/linux-x86/bin/dalvikvm64',
                                   self.root / 'build/shiraz-legacy-http.jar'))
        self.assertEqual(host, self.root / 'context/art_standalone/out/host/linux-x86')
        with patch('shiraz.architecture.platform.machine', return_value='x86_64'):
            command, env, _ = self.launch('Linux', host, {})
        self.assertEqual(command, [self.expand(item, host) for item in LINUX_COMMAND])
        expected = dict((key, self.expand(value, host)) for key, value in LINUX_ENV)
        with patch.object(runtime, 'ROOT', self.root):
            expected.update(runtime.native_build_environment({}))
        self.assertEqual(env, expected)

    def test_linux_runtime_loader_wraps_the_same_command(self):
        host = self.tree('Linux')
        loader = '/opt/loader/ld-linux-x86-64.so.2'
        with patch('shiraz.architecture.platform.machine', return_value='x86_64'):
            command, env, _ = self.launch('Linux', host, {'SHIRAZ_RUNTIME_LOADER': loader})
        self.assertEqual(command[:3], [loader, '--library-path',
                                       self.expand(f'<HOST>/lib64:<ROOT>/.local/lib:<ROOT>/build:<ROOT>/{RUN}/app_lib:/opt/loader', host)])
        self.assertEqual(command[3], '--preload')
        self.assertEqual(command[5], str(host / 'bin/dalvikvm'))
        self.assertNotIn('LD_PRELOAD', env)
        self.assertNotIn('LD_LIBRARY_PATH', env)

    def test_darwin_command_uses_dyld_paths_and_core_boot_jars(self):
        host = self.tree('Darwin')
        self.assertEqual(host, self.root / 'context/art_standalone/out/host/darwin-x86')
        inherited = {'LD_PRELOAD': '/x/pre.so', 'LD_LIBRARY_PATH': '/x', 'DYLD_INSERT_LIBRARIES': '/x/i.dylib',
                     'DYLD_LIBRARY_PATH': '/x', 'BIONIC_LD_LIBRARY_PATH': '/x', 'BERBERIS_GUEST_LD_LIBRARY_PATH': '/x',
                     'SDL_VIDEODRIVER': 'dummy', 'SHIRAZ_DIGITALIS_LIBRARY': '/x/bridge.so',
                     'FONTCONFIG_FILE': '/x/fonts.conf', 'FONTCONFIG_PATH': '/x', 'FONTCONFIG_SYSROOT': '/x'}
        command, env, output = self.launch('Darwin', host, inherited, width=96, height=160, frames=30)
        self.assertEqual(command, [self.expand(item, host) for item in DARWIN_COMMAND])
        self.assertFalse([key for key in env if key.startswith(('LD_', 'DYLD_', 'BIONIC_', 'BERBERIS_'))])
        # fontconfig reads only the in-tree configuration, never an inherited one.
        self.assertEqual({key: value for key, value in env.items() if key.startswith('FONTCONFIG_')},
                         {'FONTCONFIG_FILE': str(self.root / '.local/etc/fonts/fonts.conf'),
                          'FONTCONFIG_PATH': str(self.root / '.local/etc/fonts')})
        self.assertEqual(env['SHIRAZ_FONT_DIR'], str(self.root / '.local/share/shiraz/fonts'))
        self.assertEqual(env['BOOTCLASSPATH'], self.expand(DARWIN_BOOT, host))
        self.assertEqual(env['SHIRAZ_LOCAL_SOCKET_DIR'], str(darwin_socket_directory(self.root)))
        self.assertEqual(env['ANDROID_ROOT'], str(host))
        self.assertEqual(env['ANDROID_DATA'], str(self.root / RUN / 'android-data'))
        self.assertEqual(env['XDG_CACHE_HOME'], str(self.root / RUN / 'cache'))
        self.assertEqual(env['SHIRAZ_HOST_SDL_VIDEODRIVER'], 'dummy')
        self.assertNotIn('SDL_VIDEODRIVER', env)
        self.assertFalse(any(item.startswith(('-Xcompiler', '-XX:NativeBridge')) for item in command))
        # Command-line -D properties are unchangeable in libcore; apps must keep
        # being able to set java.protocol.handler.pkgs themselves.
        self.assertFalse(any(item.startswith('-Djava.protocol.handler.pkgs') for item in command))
        self.assertNotIn('SHIRAZ_HOST_UNAVAILABLE tls http', output)
        line = next(item for item in output.splitlines() if item.startswith('SHIRAZ_HOST_UNAVAILABLE libraries='))
        self.assertIn('libshiraz-sqlite.so', line)
        self.assertIn('libshiraz-path.so', line)
        self.assertIn('libshiraz-image.so', line)
        # Frame-critical, so never reported as an unavailable optional library.
        self.assertNotIn('libshiraz-text.so', line)
        self.assertTrue((self.root / RUN / 'android-data/dalvik-cache').is_dir())

    def test_darwin_reports_only_unbuilt_optional_libraries(self):
        built = self.root / 'build/macos/libshiraz-sqlite.so'
        host = self.tree('Darwin', (built,))
        _, _, output = self.launch('Darwin', host, {})
        line = next(item for item in output.splitlines() if item.startswith('SHIRAZ_HOST_UNAVAILABLE libraries='))
        self.assertNotIn('libshiraz-sqlite.so', line)
        self.assertIn('libshiraz-video.so', line)
        # GLES has its own line naming the fallback.
        self.assertNotIn('gles', line)

    def test_darwin_without_angle_keeps_metal_and_software_canvas(self):
        host = self.tree('Darwin', (self.root / 'build/macos/libGLESv2.dylib',))
        _, env, output = self.launch('Darwin', host, {'SHIRAZ_EGL_LIBRARY': '/x/libEGL.dylib'})
        line = next(item for item in output.splitlines() if item.startswith('SHIRAZ_HOST_UNAVAILABLE gles '))
        self.assertEqual(line, 'SHIRAZ_HOST_UNAVAILABLE gles libraries=libEGL.dylib,libGLESv1_CM.dylib,'
                         'libshiraz-gles1.so,libshiraz-gles2.so reason=angle-not-built-for-macos'
                         ' fallback=renderer-metal,canvas-software,reqGlEsVersion-0 first-use=UnsatisfiedLinkError')
        # An inherited EGL library must not reach the shim either.
        for key in ('SHIRAZ_EGL_LIBRARY', 'SHIRAZ_HOST_SDL_RENDER_DRIVER', 'SDL_VIDEO_EGL_DRIVER', 'SDL_VIDEO_GL_DRIVER'):
            self.assertNotIn(key, env)

    def angle_tree(self):
        return self.tree('Darwin', tuple(self.root / 'build/macos' / name for name in runtime.DARWIN_GLES))

    def test_darwin_with_angle_selects_the_gles2_renderer_for_a_window(self):
        host = self.angle_tree()
        _, env, output = self.launch('Darwin', host, {})
        self.assertNotIn('SHIRAZ_HOST_UNAVAILABLE gles', output)
        libraries = self.root / 'build/macos'
        self.assertEqual({key: env.get(key) for key in ('SHIRAZ_EGL_LIBRARY', 'SHIRAZ_HOST_SDL_RENDER_DRIVER',
                                                        'SDL_VIDEO_EGL_DRIVER', 'SDL_VIDEO_GL_DRIVER')},
                         {'SHIRAZ_EGL_LIBRARY': str(libraries / 'libEGL.dylib'),
                          'SHIRAZ_HOST_SDL_RENDER_DRIVER': 'opengles2',
                          'SDL_VIDEO_EGL_DRIVER': str(libraries / 'libEGL.dylib'),
                          'SDL_VIDEO_GL_DRIVER': str(libraries / 'libGLESv2.dylib')})
        self.assertNotIn('SDL_RENDER_DRIVER', env)
        # The shim on macOS loads SHIRAZ_EGL_LIBRARY only as an absolute path and has no leaf-name fallback
        # (dyld would search the cwd, the app's writable data directory); without ANGLE the variable is unset.
        self.assertTrue(Path(env['SHIRAZ_EGL_LIBRARY']).is_absolute())

    def test_darwin_with_angle_keeps_an_explicit_renderer_and_headless_drivers(self):
        host = self.angle_tree()
        libraries = self.root / 'build/macos'
        for environ, renderer in (({'SDL_RENDER_DRIVER': 'metal'}, 'metal'),
                                  ({'SDL_VIDEODRIVER': 'dummy', 'SDL_RENDER_DRIVER': 'software'}, 'software'),
                                  ({'SDL_VIDEODRIVER': 'dummy'}, None)):
            with self.subTest(environ=environ):
                _, env, _ = self.launch('Darwin', host, environ)
                self.assertEqual(env.get('SHIRAZ_HOST_SDL_RENDER_DRIVER'), renderer)
                # App EGL contexts (libEGL_shim) still use ANGLE; SDL is not pointed at it.
                self.assertEqual(env['SHIRAZ_EGL_LIBRARY'], str(libraries / 'libEGL.dylib'))
                self.assertNotIn('SDL_VIDEO_EGL_DRIVER', env)
                self.assertNotIn('SDL_VIDEO_GL_DRIVER', env)

    def test_darwin_missing_inputs_name_only_frame_critical_files(self):
        with patch.object(runtime, 'ROOT', self.root), patch.object(runtime, 'HOST', self.root / 'context/art_standalone/out/host/darwin-x86'), \
                patch.object(runtime, 'host_system', return_value='Darwin'), \
                patch.object(runtime, 'run_with_live_logs', side_effect=AssertionError('must not launch')):
            with self.assertRaisesRegex(FormatError, 'Runtime is not built for macOS') as caught:
                runtime.run_activity(self.root / 'app.apk', self.report)
        message = str(caught.exception)
        for name in ('bin/dalvikvm', 'core-oj-hostdex.jar', 'core-libart-hostdex.jar', 'build/shiraz-framework.jar',
                     'framework-res.apk', 'build/macos/libandroid.so', 'build/macos/libshiraz-jni.so',
                     'build/macos/libshiraz-gpu.so', 'build/macos/libshiraz-host.so', 'build/macos/libshiraz-bitmap.so',
                     'build/macos/libshiraz-resources.so', 'lib64/libandroidfw.so',
                     'build/macos/libshiraz-text.so', 'build/macos/libEGL_shim.so',
                     '.local/share/shiraz/fonts/NotoSans-Regular.ttf',
                     '.local/share/shiraz/fonts/NotoSansArabic-Regular.ttf',
                     '.local/share/shiraz/fonts/NotoNaskhArabic-Regular.ttf', '.local/etc/fonts/fonts.conf'):
            self.assertIn(name, message)
        for name in ('sqlite', 'espeak', 'gles', 'libEGL.dylib', 'libGLES', 'libshiraz-path',
                     'libshiraz-image'):
            self.assertNotIn(name, message)

    def test_darwin_rejects_unusable_fonts_conf_before_launch(self):
        """fontconfig's silent fallback and a fonts.conf from another checkout never reach ART."""
        host = self.tree('Darwin')
        other = self.root.parent / 'moved-checkout'
        cases = {
            'malformed': lambda: (self.root / '.local/etc/fonts/fonts.conf').write_text('<fontconfig><dir>'),
            'empty': lambda: (self.root / '.local/etc/fonts/fonts.conf').write_text(''),
            'moved checkout': lambda: self.write_fonts_conf(root=other),
            'cache outside checkout': lambda: self.write_fonts_conf(cache=other / 'cache'),
            'second cachedir': lambda: self.write_fonts_conf(extra='  <cachedir prefix="xdg">fontconfig</cachedir>\n'),
            'per-user dir': lambda: self.write_fonts_conf(extra='  <dir>~/.fonts</dir>\n'),
            'host dir': lambda: self.write_fonts_conf(extra='  <dir>/opt/homebrew/share/fonts</dir>\n'),
        }
        for name, damage in cases.items():
            with self.subTest(name):
                self.write_fonts_conf()
                damage()
                with patch.object(runtime, 'ROOT', self.root), patch.object(runtime, 'HOST', host), \
                        patch.object(runtime, 'host_system', return_value='Darwin'), \
                        patch.object(runtime, 'run_with_live_logs', side_effect=AssertionError('must not launch')), \
                        contextlib.redirect_stdout(io.StringIO()):
                    with self.assertRaisesRegex(FormatError, 'rerun make macos-graphics-deps'):
                        runtime.run_activity(self.root / 'app.apk', self.report)
        self.write_fonts_conf()
        (self.root / '.local/var/cache/fontconfig').rmdir()
        self.assertIn('not writable', runtime.darwin_fontconfig_failure(self.root))
        self.write_fonts_conf()
        self.assertIsNone(runtime.darwin_fontconfig_failure(self.root))

    def test_darwin_refuses_native_jni_libraries_when_asked(self):
        for abis in (['arm64-v8a'], ['x86_64'], ['arm64-v8a', 'armeabi-v7a']):
            report = {**self.report, 'native_abis': abis}
            with patch.object(runtime, 'host_system', return_value='Darwin'), \
                    patch.dict(os.environ, {'SHIRAZ_REFUSE_NATIVE_LIBS': '1'}), \
                    patch.object(runtime, 'runtime_inputs', side_effect=AssertionError('must reject first')), \
                    patch.object(runtime, 'run_with_live_logs', side_effect=AssertionError('must not launch')):
                with self.assertRaisesRegex(FormatError, 'native JNI libraries need the M5 guest loader on macOS'):
                    runtime.run_activity(Path('missing.apk'), report)

    def test_darwin_reports_native_jni_libraries_and_runs_java_side(self):
        report = {**self.report, 'native_abis': ['arm64-v8a', 'x86_64']}
        out = io.StringIO()
        with patch.object(runtime, 'host_system', return_value='Darwin'), \
                patch.dict(os.environ, {'SHIRAZ_REFUSE_NATIVE_LIBS': '', 'SHIRAZ_GUEST_LOADER': '0'}), \
                patch.object(runtime, 'runtime_inputs', side_effect=AssertionError('reached launch inputs')), \
                contextlib.redirect_stdout(out):
            with self.assertRaisesRegex(AssertionError, 'reached launch inputs'):
                runtime.run_activity(Path('missing.apk'), report)
        self.assertIn('SHIRAZ_UNSUPPORTED native-libraries abis=arm64-v8a,x86_64 reason=m5-guest-loader-disabled', out.getvalue())

    def test_darwin_rejects_linux_only_runtime_switches(self):
        for variable, value, message in (('SHIRAZ_ART_MODE', 'jit', 'jit is not supported on macOS'),
                                         ('SHIRAZ_RUNTIME_LOADER', '/lib/ld.so', 'Linux ld.so')):
            with patch.object(runtime, 'host_system', return_value='Darwin'), \
                    patch.dict(os.environ, {variable: value}), \
                    patch.object(runtime, 'run_with_live_logs', side_effect=AssertionError('must not launch')):
                with self.assertRaisesRegex(FormatError, message):
                    runtime.run_activity(Path('missing.apk'), self.report)

    def test_host_layout_paths(self):
        tree = Path('/tree')
        self.assertEqual(host_out_dir(tree, system='Linux'), Path('/tree/out/host/linux-x86'))
        self.assertEqual(host_out_dir(tree, system='Darwin'), Path('/tree/out/host/darwin-x86'))
        self.assertEqual(runtime.HOST, host_out_dir(runtime.ROOT / 'context/art_standalone'))

    def test_apple_silicon_machine_maps_to_arm64_v8a(self):
        self.assertEqual(native_abi('arm64'), 'arm64-v8a')
        self.assertEqual(native_abi('aarch64'), 'arm64-v8a')
        self.assertEqual(native_abi('x86_64'), 'x86_64')
        self.assertIsNone(native_abi('riscv64'))


if __name__ == '__main__':
    unittest.main()
