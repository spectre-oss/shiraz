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
if __name__ == '__main__':
    main()
