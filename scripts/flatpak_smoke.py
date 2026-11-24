#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Verify a Shiraz single-file .flatpak bundle (structure, GVariant validity, and optional flatpak install/run)."""

import argparse
import ctypes
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

OSTREE_SUPERBLOCK_SIG = b'(a{sv}tayay(a{sv}aya(say)sstayay)aya(uayttay)a(yaytt))'


def verify_gvariant_superblock(bundle_path: Path) -> None:
    """Validate that `bundle_path` is a normal-form OSTree static-delta superblock using libglib-2.0."""
    try:
        glib = ctypes.CDLL('libglib-2.0.so.0')
    except OSError:
        return
    glib.g_variant_type_new.argtypes = [ctypes.c_char_p]
    glib.g_variant_type_new.restype = ctypes.c_void_p
    glib.g_variant_type_free.argtypes = [ctypes.c_void_p]
    glib.g_variant_new_from_data.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_size_t,
        ctypes.c_int,
        ctypes.c_void_p,
        ctypes.c_void_p,
    ]
    glib.g_variant_new_from_data.restype = ctypes.c_void_p
    glib.g_variant_ref_sink.argtypes = [ctypes.c_void_p]
    glib.g_variant_ref_sink.restype = ctypes.c_void_p
    glib.g_variant_is_normal_form.argtypes = [ctypes.c_void_p]
    glib.g_variant_is_normal_form.restype = ctypes.c_int
    glib.g_variant_unref.argtypes = [ctypes.c_void_p]

    raw = bundle_path.read_bytes()
    buf = ctypes.create_string_buffer(raw, len(raw))
    vtype = glib.g_variant_type_new(OSTREE_SUPERBLOCK_SIG)
    variant = glib.g_variant_new_from_data(vtype, ctypes.addressof(buf), len(raw), 0, None, None)
    glib.g_variant_type_free(vtype)
    if not variant:
        raise RuntimeError('GLib rejected Flatpak superblock GVariant')
    glib.g_variant_ref_sink(variant)
    normal = glib.g_variant_is_normal_form(variant)
    glib.g_variant_unref(variant)
    if not normal:
        raise RuntimeError('Flatpak superblock GVariant is not in canonical normal form')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('--app-id', help='Expected Flatpak App ID')
    parser.add_argument(
        '--require-install', action='store_true', help='Require flatpak CLI install + diagnose run'
    )
    parser.add_argument(
        '--require-run', action='store_true', help='Require headless SDL dummy run inside flatpak'
    )
    parser.add_argument(
        '--expect-offline',
        action='store_true',
        help='Verify network sharing is omitted from metadata',
    )
    args = parser.parse_args()

    bundle = args.bundle.resolve()
    if not bundle.is_file():
        raise SystemExit('Missing bundle: ' + str(bundle))
    verify_gvariant_superblock(bundle)
    print(f'GVARIANT_SUPERBLOCK_OK {bundle.name} ({bundle.stat().st_size} bytes)')

    ostree_bin = shutil.which('ostree')
    if ostree_bin:
        with tempfile.TemporaryDirectory(prefix='shiraz-ostree-check-') as tmp:
            repo = Path(tmp) / 'repo'
            subprocess.run([ostree_bin, 'init', '--mode=bare-user', f'--repo={repo}'], check=True)
            subprocess.run(
                [ostree_bin, 'static-delta', 'apply-offline', f'--repo={repo}', str(bundle)],
                check=True,
            )
            print('OSTREE_APPLY_OFFLINE_OK')

    flatpak_bin = shutil.which('flatpak')
    if not flatpak_bin:
        if args.require_install or args.require_run:
            raise SystemExit('flatpak binary is required for --require-install / --require-run')
        print('FLATPAK_CLI_SKIPPED (flatpak not installed on host)')
        return

    with tempfile.TemporaryDirectory(prefix='shiraz-flatpak-smoke-') as tmp:
        env = dict(os.environ, FLATPAK_USER_DIR=str(Path(tmp) / 'flatpak-user'))
        install = subprocess.run(
            [flatpak_bin, 'install', '--user', '--noninteractive', '--no-deps', str(bundle)],
            env=env,
            capture_output=True,
            text=True,
        )
        if install.returncode != 0:
            raise SystemExit(f'flatpak install failed:\n{install.stdout}\n{install.stderr}')

        installed = subprocess.check_output(
            [flatpak_bin, 'list', '--user', '--app', '--columns=application'],
            env=env,
            text=True,
        ).splitlines()
        if not installed:
            raise SystemExit('flatpak install produced no installed app')
        app_id = args.app_id or installed[0].strip()
        if app_id not in [line.strip() for line in installed]:
            raise SystemExit(f'Expected {app_id} in installed apps: {installed}')

        meta = subprocess.check_output(
            [flatpak_bin, 'info', '--user', '--show-metadata', app_id],
            env=env,
            text=True,
        )
        if args.expect_offline and 'network;' in meta:
            raise SystemExit('Expected offline Flatpak metadata without network permission')
        print(f'FLATPAK_INSTALL_OK {app_id}')

        if args.require_run:
            diag = subprocess.run(
                [flatpak_bin, 'run', '--user', app_id, '--diagnose'],
                env=env,
                capture_output=True,
                text=True,
                timeout=60,
            )
            if diag.returncode != 0 or 'Container: flatpak' not in diag.stdout:
                raise SystemExit(f'flatpak --diagnose failed:\n{diag.stdout}\n{diag.stderr}')
            run = subprocess.run(
                [
                    flatpak_bin,
                    'run',
                    '--user',
                    '--env=SDL_VIDEODRIVER=dummy',
                    '--env=SDL_RENDER_DRIVER=software',
                    '--env=SHIRAZ_AUDIO_DEVICE=null',
                    app_id,
                    '--width',
                    '320',
                    '--height',
                    '480',
                    '--frames',
                    '2',
                ],
                env=env,
                capture_output=True,
                text=True,
                timeout=120,
            )
            if run.returncode != 0:
                raise SystemExit(f'flatpak headless run failed:\n{run.stdout}\n{run.stderr}')
            print(f'FLATPAK_RUN_OK {app_id}')


if __name__ == '__main__':
    main()
