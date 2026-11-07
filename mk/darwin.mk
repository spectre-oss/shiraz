# SPDX-License-Identifier: GPL-3.0-only
# macOS (Apple Silicon) host build. Included by the Makefile when uname is Darwin.
# The Linux targets (build, test, check) still parse here but are not supported on
# macOS; use the macos-* targets below. Written for the GNU make 3.81 that ships
# with macOS: no grouped targets, no $(file), no !=.
# Outputs go to build/macos/. Shared libraries keep Android's .so names (Mach-O
# inside) so APK-facing names stay stable; they are linked by explicit path.

MACOS_OUT := build/macos
MACOS_JAVA_HOME := $(shell /usr/libexec/java_home 2>/dev/null)
MACOS_JNI_CFLAGS := -I$(MACOS_JAVA_HOME)/include -I$(MACOS_JAVA_HOME)/include/darwin
MACOS_SDL_CONFIG := .local/bin/sdl2-config
MACOS_SDL_CFLAGS = $(shell $(MACOS_SDL_CONFIG) --cflags 2>/dev/null)
MACOS_SDL_LIBS = $(shell $(MACOS_SDL_CONFIG) --static-libs 2>/dev/null)
MACOS_DYLIB = -dynamiclib -install_name @rpath/$(notdir $@)
MACOS_RPATH := -Wl,-rpath,@loader_path
MACOS_CXX = $(CXX) $(CPPFLAGS) $(CXXFLAGS)

# Same source list as the Linux build/libandroid.so. EGL-backed entry points fail
# explicitly on macOS until ANGLE-Metal lands (docs/macos_final_goal.md, M4).
MACOS_ANDROID_SOURCES := native/native_window.cpp native/shared_frame.cpp native/from_surface.cpp native/surface_texture.cpp native/shared_memory.cpp native/hardware_buffer.cpp native/sync_fence.cpp native/image_reader.cpp native/media_crypto.cpp native/media_ndk.cpp native/system_fonts.cpp native/looper.cpp native/choreographer.cpp native/sensors.cpp native/trace.cpp
MACOS_ANDROID_HEADERS := include/shiraz/shared_frame.hpp include/shiraz/native_window.h include/shiraz/hardware_buffer.h include/shiraz/window_frames.hpp include/shiraz/image_reader.h include/shiraz/media_crypto.h
MACOS_ANDROID_FRAMEWORKS := -framework CoreFoundation -framework CoreText -framework IOSurface
# libshiraz-host minus native/gles_version.cpp, which needs EGL headers (M4).
# Compiled to objects (not linked or run) so the ports stay warning-free until
# the library can link.
MACOS_HOST_SOURCES := native/host_process.cpp native/fds.cpp native/ipc_channel.cpp native/ipc_jni.cpp native/ipc_spawn.cpp
MACOS_LINK_ANDROID = $(MACOS_OUT)/libandroid.so $(MACOS_RPATH)
MACOS_PRESENTATION_HEADERS := include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/evdev_key_codes.h include/shiraz/live_input.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp include/shiraz/display_geometry.hpp
MACOS_TESTS := test-compositor test-pointer test-keyboard test-archive-path test-native-window test-shared-frame test-choreographer test-sensors test-shared-memory test-image-reader test-hardware-buffer test-system-fonts test-media-crypto test-media-ndk test-ipc-channel test-sync-fence
MACOS_SDL_TESTS := test-presentation test-live-input

.PHONY: macos-deps macos-build macos-test macos-check macos-preview macos-smoke
macos-deps:
	python3 scripts/bootstrap_sdl.py

$(MACOS_OUT)/.dir:
	@mkdir -p $(MACOS_OUT)
	@touch $@

$(MACOS_SDL_CONFIG):
	@echo 'SDL2 missing. Run make macos-deps (local source build).'; exit 1

$(MACOS_OUT)/libandroid.so: $(MACOS_ANDROID_SOURCES) $(MACOS_ANDROID_HEADERS) mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) $(MACOS_ANDROID_SOURCES) -o $@ -pthread $(MACOS_ANDROID_FRAMEWORKS)

$(MACOS_OUT)/host-sources.stamp: $(MACOS_HOST_SOURCES) include/shiraz/ipc_channel.hpp $(MACOS_OUT)/.dir
	@set -e; for f in $(MACOS_HOST_SOURCES); do echo "compile $$f"; $(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC -c $$f -o /dev/null; done
	@touch $@

$(MACOS_OUT)/shiraz-preview: native/main.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp $(MACOS_PRESENTATION_HEADERS) build/version.hpp $(MACOS_OUT)/libandroid.so $(MACOS_SDL_CONFIG)
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) native/main.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)

$(MACOS_OUT)/test-compositor: tests/compositor.cpp include/shiraz/compositor.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/compositor.cpp -o $@
$(MACOS_OUT)/test-pointer: tests/pointer.cpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/pointer.cpp -o $@
$(MACOS_OUT)/test-keyboard: tests/keyboard.cpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/evdev_key_codes.h $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/keyboard.cpp -o $@
$(MACOS_OUT)/test-archive-path: tests/native/archive_path.cpp include/shiraz/archive_path.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native/archive_path.cpp -o $@
$(MACOS_OUT)/test-native-window: tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp include/shiraz/shared_frame.hpp include/shiraz/window_frames.hpp include/shiraz/native_window.h include/shiraz/window_snapshot.hpp include/shiraz/compositor.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp -o $@ -pthread
$(MACOS_OUT)/test-shared-frame: tests/shared_frame.cpp native/shared_frame.cpp include/shiraz/shared_frame.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/shared_frame.cpp native/shared_frame.cpp -o $@ -pthread
$(MACOS_OUT)/test-choreographer: tests/native/choreographer.cpp native/choreographer.cpp native/looper.cpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native/choreographer.cpp native/choreographer.cpp native/looper.cpp -o $@ -pthread
$(MACOS_OUT)/test-sensors: tests/native/sensors.cpp native/sensors.cpp native/looper.cpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native/sensors.cpp native/sensors.cpp native/looper.cpp -o $@ -pthread
$(MACOS_OUT)/test-ipc-channel: tests/ipc_channel.cpp native/ipc_channel.cpp include/shiraz/ipc_channel.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/ipc_channel.cpp native/ipc_channel.cpp -o $@
$(MACOS_OUT)/test-shared-memory: tests/shared_memory.cpp $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/shared_memory.cpp -o $@ $(MACOS_LINK_ANDROID)
$(MACOS_OUT)/test-image-reader: tests/image_reader.cpp include/shiraz/image_reader.h $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/image_reader.cpp -o $@ $(MACOS_LINK_ANDROID) -pthread
$(MACOS_OUT)/test-hardware-buffer: tests/hardware_buffer.cpp include/shiraz/hardware_buffer.h $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/hardware_buffer.cpp -o $@ $(MACOS_LINK_ANDROID)
$(MACOS_OUT)/test-system-fonts: tests/system_fonts.cpp $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/system_fonts.cpp -o $@ $(MACOS_LINK_ANDROID)
$(MACOS_OUT)/test-media-crypto: tests/media_crypto.cpp include/shiraz/media_crypto.h $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/media_crypto.cpp -o $@ $(MACOS_LINK_ANDROID)
$(MACOS_OUT)/test-media-ndk: tests/media_ndk.cpp $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/media_ndk.cpp -o $@ $(MACOS_LINK_ANDROID)
$(MACOS_OUT)/test-sync-fence: tests/darwin/sync_fence.cpp $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/darwin/sync_fence.cpp -o $@ $(MACOS_LINK_ANDROID) -pthread
$(MACOS_OUT)/test-presentation: tests/presentation.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp $(MACOS_PRESENTATION_HEADERS) $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/presentation.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp -o $@ $(MACOS_SDL_LIBS)
$(MACOS_OUT)/test-live-input: tests/live_input.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp $(MACOS_PRESENTATION_HEADERS) $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/live_input.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp -o $@ $(MACOS_SDL_LIBS)

macos-build: $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/shiraz-preview $(MACOS_OUT)/host-sources.stamp

# SDL tests run headless like their Linux smokes (dummy video, software renderer).
macos-test: $(addprefix $(MACOS_OUT)/,$(MACOS_TESTS) $(MACOS_SDL_TESTS)) $(MACOS_OUT)/host-sources.stamp
	@set -e; for t in $(MACOS_TESTS); do echo "== $$t"; ./$(MACOS_OUT)/$$t; done
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software ./$(MACOS_OUT)/test-presentation
	env -u SDL_VIDEODRIVER -u SDL_RENDER_DRIVER SHIRAZ_HOST_SDL_VIDEODRIVER=dummy SHIRAZ_HOST_SDL_RENDER_DRIVER=software ./$(MACOS_OUT)/test-presentation
	SDL_VIDEODRIVER=dummy ./$(MACOS_OUT)/test-live-input

macos-preview: macos-build
	./$(MACOS_OUT)/shiraz-preview

# Opens a real Cocoa window for a few frames and requires the Metal renderer.
macos-smoke: macos-build
	./$(MACOS_OUT)/shiraz-preview --version
	./$(MACOS_OUT)/shiraz-preview --frames 30 --snapshot $(MACOS_OUT)/preview.ppm 2> $(MACOS_OUT)/preview.err; status=$$?; cat $(MACOS_OUT)/preview.err; test $$status -eq 0
	grep -q 'SHIRAZ_PRESENTATION renderer=metal' $(MACOS_OUT)/preview.err

macos-check: macos-test macos-smoke
	python3 scripts/darwin_linux_guard.py
	python3 scripts/version.py --check
	python3 scripts/commit_guard.py
