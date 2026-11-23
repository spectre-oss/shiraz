# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

from shiraz import aot_cache, spectre_daemon


class SpectreDaemonUnitTest(unittest.TestCase):
    def test_desktop_entry_mime_and_autostart_respect_xdg(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = {
                'HOME': str(root / 'home'),
                'XDG_CONFIG_HOME': str(root / 'config'),
                'XDG_DATA_HOME': str(root / 'data'),
                'XDG_CACHE_HOME': str(root / 'cache'),
                'XDG_RUNTIME_DIR': str(root / 'run'),
            }
            desktop = spectre_daemon.write_desktop_entry(
                'net.osmand.plus',
                'OsmAnd~',
                version_name='5.4.1',
                aot_compiled=True,
                env=env,
            )
            self.assertEqual(desktop, root / 'data/applications/net.osmand.plus.Shiraz.desktop')
            text = desktop.read_text(encoding='utf-8')
            self.assertIn('Version=1.5', text)
            self.assertIn('Name=OsmAnd~', text)
            self.assertIn('Icon=net.osmand.plus.Shiraz', text)
            self.assertIn('StartupWMClass=net.osmand.plus.Shiraz', text)
            self.assertIn('X-Shiraz-Package=net.osmand.plus', text)
            self.assertIn('X-Shiraz-AOT=true', text)
            self.assertIn('[Desktop Action force-stop]', text)

            mime = spectre_daemon.ensure_mime_apk_handler(env)
            self.assertTrue(Path(mime['installer_desktop']).is_file())
            mime_text = Path(mime['mimeapps']).read_text(encoding='utf-8')
            self.assertIn(
                'application/vnd.android.package-archive=org.spectreos.ShirazInstaller.desktop;',
                mime_text,
            )

            auto = spectre_daemon.sync_autostart_entry(True, env)
            self.assertEqual(auto, root / 'config/autostart/org.spectreos.Shiraz.desktop')
            self.assertTrue(auto.is_file())
            spectre_daemon.sync_autostart_entry(False, env)
            self.assertFalse(auto.exists())

    def test_memory_warmer_maps_and_releases_cleanly(self):
        with tempfile.TemporaryDirectory() as tmp:
            sample = Path(tmp) / 'blob.bin'
            sample.write_bytes(b'S' * (2 * 1024 * 1024))
            warmer = spectre_daemon.MemoryWarmer()
            files, total = warmer.warm([sample])
            self.assertEqual(files, 1)
            self.assertEqual(total, 2 * 1024 * 1024)
            self.assertEqual(warmer.warm_files, 1)
            self.assertEqual(warmer.warm_ram_mb, 2)
            warmer.release()
            self.assertEqual(warmer.warm_files, 0)
            self.assertEqual(warmer.warm_ram_mb, 0)

    def test_fallback_png_is_valid_png(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'icon.png'
            spectre_daemon._write_fallback_png(out, 'Delta Chat', size=64)
            data = out.read_bytes()
            self.assertTrue(data.startswith(b'\x89PNG\r\n\x1a\n'))
            self.assertGreater(len(data), 100)

    def test_daemon_ipc_status_configure_and_shutdown(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = dict(
                os.environ,
                HOME=str(root / 'home'),
                XDG_CONFIG_HOME=str(root / 'config'),
                XDG_DATA_HOME=str(root / 'data'),
                XDG_CACHE_HOME=str(root / 'cache'),
                XDG_RUNTIME_DIR=str(root / 'run'),
            )
            with mock.patch.dict(os.environ, env, clear=True):
                daemon = spectre_daemon.ShirazDaemon(
                    prewarm_memory=False,
                    aot_enabled=False,
                )
                thread = threading.Thread(target=daemon.serve_forever, daemon=True)
                thread.start()
                try:
                    deadline = time.monotonic() + 5.0
                    while time.monotonic() < deadline and not daemon.sock_path.exists():
                        time.sleep(0.05)
                    self.assertTrue(daemon.sock_path.exists())

                    st = spectre_daemon.ctl_request({'cmd': 'status'})
                    self.assertTrue(st['ok'])
                    self.assertIn(st['status']['state'], ('starting', 'warming', 'ready'))

                    cfg = spectre_daemon.ctl_request(
                        {
                            'cmd': 'configure',
                            'width': 400,
                            'height': 832,
                            'wayland_display': 'wayland-9',
                        }
                    )
                    self.assertTrue(cfg['ok'])
                    self.assertEqual(daemon.width, 400)
                    self.assertEqual(daemon.height, 832)
                    self.assertEqual(daemon.wayland_display, 'wayland-9')

                    sh = spectre_daemon.ctl_request({'cmd': 'shutdown'})
                    self.assertTrue(sh['ok'])
                finally:
                    thread.join(timeout=5.0)
                self.assertFalse(thread.is_alive())
                self.assertFalse(daemon.sock_path.exists())
                final_status = json.loads(daemon.status_path.read_text(encoding='utf-8'))
                self.assertEqual(final_status['state'], 'disabled')

    def test_collect_warm_files_returns_existing_runtime_files(self):
        files = aot_cache.collect_warm_files()
        self.assertTrue(all(p.is_file() for p in files))


if __name__ == '__main__':
    unittest.main()
