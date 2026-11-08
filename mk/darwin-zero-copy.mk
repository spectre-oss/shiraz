# SPDX-License-Identifier: GPL-3.0-only
# M4b: app GLES frames reach the screen with no CPU readback (macOS only).
# Included at the end of mk/darwin.mk, so every variable it extends is defined.
# Design: build/macos/m4b/design/DESIGN-v2.md (sections 3-7, 9, 12).
# SHIRAZ_EGL_ZERO_COPY: unset/1 = auto, 0 = readback (today's path), require =
# strict (any unexpected readback or consumer fallback is SHIRAZ_RUNTIME_FATAL).

# Sources/frameworks: the libandroid recipe expands these at run time, so += reaches it.
MACOS_ANDROID_SOURCES    += native/darwin/shared_event.mm
MACOS_ANDROID_FRAMEWORKS += -framework Metal -framework Foundation
# Prerequisites need explicit lines (parse-time expansion).
MACOS_ZC_QUEUE_FILES := native/darwin/shared_event.mm native/darwin/window_queue.inc include/shiraz/darwin/window_queue.h mk/darwin-zero-copy.mk
$(MACOS_OUT)/libandroid.so: $(MACOS_ZC_QUEUE_FILES)
$(MACOS_OUT)/libEGL_shim.so: native/darwin/egl_shim_iosurface.inc include/shiraz/darwin/window_queue.h mk/darwin-zero-copy.mk
$(MACOS_OUT)/libshiraz-gpu.so: native/darwin/gpu_iosurface.inc include/shiraz/darwin/window_queue.h mk/darwin-zero-copy.mk
$(MACOS_OUT)/libshiraz-gles2.so: mk/darwin-zero-copy.mk
$(MACOS_OUT)/libshiraz-jni.so: mk/darwin-zero-copy.mk

MACOS_ZC_GATES := $(MACOS_OUT)/m4b/gates
MACOS_ZC_TEST_CFLAGS := -Iinclude -std=c++17 -O1 -g -Wall -Wextra -Werror -fobjc-arc
MACOS_ZC_TEST_FRAMEWORKS := -framework Metal -framework Foundation -framework IOSurface -framework CoreFoundation
# Log header: tree identity, protected-file hashes, env, display scale.
MACOS_ZC_PROTECTED := java/android/graphics/Bitmap.java java/android/graphics/Canvas.java java/android/widget/TextView.java \
  java/shiraz/runtime/ActivityRunner.java java/shiraz/runtime/NativeBitmap.java java/shiraz/runtime/TextGeometry.java \
  native/bitmap.cpp native/text.cpp Makefile docs/DEVELOPMENT.md scripts/bootstrap_runtime.py mk/darwin.mk
MACOS_ZC_HEADER = mkdir -p $(1) && { echo "gate=$(1)"; date; git rev-parse HEAD; git diff --stat; shasum -a 256 $(MACOS_ZC_PROTECTED); \
  env | grep -E '^(SHIRAZ_|SDL_)' | sort; system_profiler SPDisplaysDataType 2>/dev/null | grep -E 'Resolution|Retina|UI Looks like' ; } > $(1)/header.log 2>&1

.PHONY: macos-zero-copy-queue-test macos-zero-copy-ring-test macos-zero-copy-shim-test macos-apk-gles-anim-fixture \
  macos-zero-copy-window-smoke macos-zero-copy-pixels macos-zero-copy-sequence macos-zero-copy-timing \
  macos-zero-copy-negative macos-zero-copy-java-parity macos-zero-copy-check

# T9: the queue in libandroid alone.
$(MACOS_OUT)/test-zero-copy-queue: tests/darwin/zero_copy/window_queue_test.mm $(MACOS_ZC_QUEUE_FILES) $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) $(MACOS_ZC_TEST_CFLAGS) tests/darwin/zero_copy/window_queue_test.mm -o $@.tmp $(MACOS_OUT)/libandroid.so $(MACOS_RPATH) $(MACOS_ZC_TEST_FRAMEWORKS) && mv $@.tmp $@
macos-zero-copy-queue-test: $(MACOS_OUT)/test-zero-copy-queue
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/queue-test)
	$(MACOS_OUT)/test-zero-copy-queue > $(MACOS_ZC_GATES)/queue-test/run.log 2>&1; rc=$$?; cat $(MACOS_ZC_GATES)/queue-test/run.log; test $$rc -eq 0

# T10: the shim on ANGLE over a Shiraz window in all three modes (the existing
# macos-egl-shim-test binary, extended), then the no-ANGLE fallback test.
macos-zero-copy-shim-test: $(MACOS_OUT)/test-egl-shim-window $(addprefix $(MACOS_OUT)/,$(MACOS_GLES_LIBRARIES))
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/shim-test)
	for mode in 1 require 0; do \
	  echo "== SHIRAZ_EGL_ZERO_COPY=$$mode"; \
	  SHIRAZ_EGL_ZERO_COPY=$$mode SHIRAZ_EGL_LIBRARY=$(CURDIR)/$(MACOS_OUT)/libEGL.dylib ./$(MACOS_OUT)/test-egl-shim-window $(CURDIR)/$(MACOS_OUT) || exit 1; \
	done > $(MACOS_ZC_GATES)/shim-test/run.log 2>&1; rc=$$?; cat $(MACOS_ZC_GATES)/shim-test/run.log; test $$rc -eq 0
	grep -q 'mode=iosurface' $(MACOS_ZC_GATES)/shim-test/run.log && grep -q 'mode=readback reason=env' $(MACOS_ZC_GATES)/shim-test/run.log
	@$(MAKE) --no-print-directory macos-egl-shim-no-angle-test

# The continuous animated fixture (DESIGN-v2 section 7.2).
build/macos-apk/gles-anim.apk: tests/macos-apk-gles-anim/AndroidManifest.xml tests/macos-apk-gles-anim/GlesAnimActivity.java scripts/build_macos_fixture.py scripts/build_apk_fixture.py src/shiraz/apk.py src/shiraz/host_layout.py $(MACOS_ART_HOST)/bin/dx
	python3 scripts/build_macos_fixture.py --fixture gles-anim
macos-apk-gles-anim-fixture: build/macos-apk/gles-anim.apk

# App runs use the same runtime prerequisites as the windowed GLES smokes.
MACOS_ZC_RUN_DEPS := $(MACOS_RUNTIME_GLES_SMOKE_DEPS) build/macos-apk/gles-anim.apk
MACOS_ZC_GATE = PYTHONPATH=src python3 scripts/zero_copy_gates.py

# T1 + T2 (R): the existing gles fixture, counter run (no presenter reads), require arm.
macos-zero-copy-window-smoke: $(MACOS_ZC_RUN_DEPS)
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/t1t2-require)
	$(MACOS_ZC_GATE) --fixture gles --arm require --gates T1,T2 --out $(MACOS_ZC_GATES)/t1t2-require
# T3: the existing gles profile pixel checks (snapshot run), require arm.
macos-zero-copy-pixels: $(MACOS_ZC_RUN_DEPS)
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/t3-require)
	SHIRAZ_EGL_ZERO_COPY=require python3 scripts/macos_runtime_smoke.py --profile gles --windowed --snapshot $(MACOS_ZC_GATES)/t3-require/frame.ppm
# T3s (paced) and T3f (free): presenter sample sequences in both arms (frame-id gates pass in both).
macos-zero-copy-sequence: $(MACOS_ZC_RUN_DEPS)
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/sequence)
	for arm in require 0; do \
	  $(MACOS_ZC_GATE) --fixture gles-anim --anim-mode paced --arm $$arm --gates T3s --samples --out $(MACOS_ZC_GATES)/t3s-$$arm || exit 1; \
	  $(MACOS_ZC_GATE) --fixture gles-anim --anim-mode free --arm $$arm --gates T3f --samples --out $(MACOS_ZC_GATES)/t3f-$$arm || exit 1; \
	done
# T4 (R): paced timing at a 1080x2340 request (the window is clamped to the display; the log records the size).
macos-zero-copy-timing: $(MACOS_ZC_RUN_DEPS)
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/t4-require)
	$(MACOS_ZC_GATE) --fixture gles-anim --anim-mode paced --arm require --gates T4 --width 1080 --height 2340 --out $(MACOS_ZC_GATES)/t4-require
# Negative control: SHIRAZ_EGL_ZERO_COPY=0 must FAIL T1, T2 and T4 (and T4 with swap p50 >= 5 ms).
macos-zero-copy-negative: $(MACOS_ZC_RUN_DEPS)
	@$(call MACOS_ZC_HEADER,$(MACOS_ZC_GATES)/negative)
	$(MACOS_ZC_GATE) --fixture gles --arm 0 --gates T1,T2 --expect-fail=T1,T2 --out $(MACOS_ZC_GATES)/t1t2-negative
	$(MACOS_ZC_GATE) --fixture gles-anim --anim-mode paced --arm 0 --gates T4 --expect-fail=T4 --width 1080 --height 2340 --out $(MACOS_ZC_GATES)/t4-negative
# H8: host JVM, -Dos.name=Linux, no Shiraz natives.
macos-zero-copy-java-parity: macos-framework
	mkdir -p $(MACOS_OUT)/m4b/java-parity
	javac --release 8 -cp build/framework/classes -d $(MACOS_OUT)/m4b/java-parity tests/java/ZeroCopyParitySmoke.java
	java -Dos.name=Linux -cp $(MACOS_OUT)/m4b/java-parity:build/framework/classes ZeroCopyParitySmoke

macos-zero-copy-check: macos-zero-copy-queue-test macos-zero-copy-shim-test macos-zero-copy-java-parity macos-zero-copy-window-smoke \
  macos-zero-copy-pixels macos-zero-copy-sequence macos-zero-copy-timing macos-zero-copy-negative
