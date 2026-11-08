# SPDX-License-Identifier: GPL-3.0-only
# The probe needs a working text UI before it can diagnose optional APIs.
.PHONY: api-probe-run api-probe-ready api-probe-smoke
ifeq ($(shell uname -s),Darwin)
api-probe-ready: api-probe-apk macos-runtime-libs macos-framework macos-framework-resources macos-graphics-libs
else
api-probe-ready: api-probe-apk build framework framework-resources
endif

api-probe-run: api-probe-ready
	uv run --no-project python -m shiraz run build/android-api-probe/api-probe-unsigned.apk

api-probe-smoke: api-probe-ready
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software uv run --no-project python -m shiraz run build/android-api-probe/api-probe-unsigned.apk --frames 3 --width 360 --height 640 --snapshot build/android-api-probe/smoke-portrait.ppm --timeout 60
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software uv run --no-project python -m shiraz run build/android-api-probe/api-probe-unsigned.apk --frames 3 --width 640 --height 360 --snapshot build/android-api-probe/smoke-landscape.ppm --timeout 60
