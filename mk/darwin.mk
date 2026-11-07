# SPDX-License-Identifier: GPL-3.0-only
# macOS (Apple Silicon) host build. Included by the Makefile when uname is Darwin.
# The Linux targets (build, test, check) still parse here but are not supported on
# macOS; use the macos-* targets below. Written for the GNU make 3.81 that ships
# with macOS: no grouped targets, no $(file), no !=.
# Outputs go to build/macos/. Shared libraries keep Android's .so names (Mach-O
# inside) so APK-facing names stay stable; they are linked by explicit path.
#
# Cold order on a new Mac: make macos-deps (SDL2), make macos-build and
# make macos-check (no ART needed), then make runtime-art (alias macos-art:
# clones the pinned sources, applies the darwin patch chain and builds the
# darwin-x86 ART host tree), then make macos-runtime-libs,
# macos-framework-resources, macos-apk-fixture and macos-runtime-smoke.

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
# libshiraz-host with native/darwin/gles_version.cpp in place of the EGL probe:
# no GLES until ANGLE-Metal (M4), so reqGlEsVersion reports 0.
MACOS_HOST_SOURCES := native/host_process.cpp native/fds.cpp native/ipc_channel.cpp native/ipc_jni.cpp native/ipc_spawn.cpp native/darwin/gles_version.cpp
# No -z defs counterpart is passed: ld64 already rejects unresolved symbols in
# a dylib by default, and the explicit -undefined error flag is deprecated.
# The darwin ART host tree (built by make runtime-art) that the framework DEX
# and the ART smokes use; scripts/build_framework.py reads this fixed path.
MACOS_ART_HOST := $(CURDIR)/context/art_standalone/out/host/darwin-x86
MACOS_ART_CORE_CLASSES := $(CURDIR)/context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar
MACOS_ART_MISSING = echo 'Missing $@: build the darwin ART host tree first with make runtime-art (python3 scripts/bootstrap_runtime.py art).'; exit 1
# libandroidfw headers and library both come from one art_standalone checkout,
# so the resources bridge cannot compile against one tree and link another.
# Override the checkout as a whole with an absolute path, e.g.
# make build/macos/libshiraz-resources.so MACOS_ANDROIDFW_TREE=/abs/art_standalone
# (the library directory is baked into LC_RPATH, and ART runs from data/apps/<pkg>).
MACOS_ANDROIDFW_TREE := $(CURDIR)/context/art_standalone
ifneq ($(filter command environment,$(origin MACOS_ANDROIDFW_LIB_DIR) $(origin MACOS_ANDROIDFW_INCLUDE)),)
$(error Override MACOS_ANDROIDFW_TREE, not MACOS_ANDROIDFW_LIB_DIR or MACOS_ANDROIDFW_INCLUDE: headers and library must come from one tree)
endif
ifeq ($(filter /%,$(MACOS_ANDROIDFW_TREE)),)
$(error MACOS_ANDROIDFW_TREE must be an absolute path (it is baked into LC_RPATH): $(MACOS_ANDROIDFW_TREE))
endif
MACOS_ANDROIDFW_LIB_DIR := $(MACOS_ANDROIDFW_TREE)/out/host/darwin-x86/lib64
MACOS_ANDROIDFW_INCLUDE := $(MACOS_ANDROIDFW_TREE)/libandroidfw/include
MACOS_ANDROIDFW_HEADER := $(MACOS_ANDROIDFW_INCLUDE)/androidfw/androidfw_c_api.h
MACOS_LINK_ANDROID = $(MACOS_OUT)/libandroid.so $(MACOS_RPATH)
MACOS_PRESENTATION_HEADERS := include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/evdev_key_codes.h include/shiraz/live_input.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp include/shiraz/display_geometry.hpp
MACOS_TESTS := test-compositor test-pointer test-keyboard test-archive-path test-native-window test-shared-frame test-choreographer test-sensors test-shared-memory test-image-reader test-hardware-buffer test-system-fonts test-media-crypto test-media-ndk test-ipc-channel test-sync-fence
MACOS_SDL_TESTS := test-presentation test-live-input

.PHONY: macos-deps macos-art macos-runtime-libs macos-framework-missing macos-build macos-test macos-check macos-preview macos-smoke macos-framework macos-runtime-jni-smoke macos-runtime-jni-window-smoke macos-androidfw-lib-dir-check macos-framework-resources macos-apk-fixture macos-runtime-smoke macos-runtime-window-smoke
macos-deps:
	python3 scripts/bootstrap_sdl.py
# Same stage as make runtime-art; named here so the macOS cold path is discoverable.
macos-art: runtime-art

$(MACOS_OUT)/.dir:
	@mkdir -p $(MACOS_OUT)
	@touch $@

$(MACOS_SDL_CONFIG):
	@echo 'SDL2 missing. Run make macos-deps (local source build).'; exit 1

$(MACOS_OUT)/libandroid.so: $(MACOS_ANDROID_SOURCES) $(MACOS_ANDROID_HEADERS) mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) $(MACOS_ANDROID_SOURCES) -o $@ -pthread $(MACOS_ANDROID_FRAMEWORKS)

$(MACOS_OUT)/libshiraz-host.so: $(MACOS_HOST_SOURCES) include/shiraz/ipc_channel.hpp mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) $(MACOS_HOST_SOURCES) -o $@

$(MACOS_OUT)/libshiraz-bitmap.so: native/bitmap.cpp include/shiraz/color_filter.hpp mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) native/bitmap.cpp -o $@

# Linked against the SDK's libsqlite3 (/usr/lib/libsqlite3.dylib).
$(MACOS_OUT)/libshiraz-sqlite.so: native/sqlite.cpp mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) native/sqlite.cpp -o $@ -lsqlite3 -pthread

# libandroidfw's install name is @rpath/libandroidfw.so; the absolute rpath
# resolves it (and its @rpath libbase/libutils/... siblings) in-tree, and
# @loader_path covers a copy placed next to this library.
$(MACOS_ANDROIDFW_LIB_DIR)/libandroidfw.so:
	@$(MACOS_ART_MISSING)
$(MACOS_ANDROIDFW_HEADER):
	@$(MACOS_ART_MISSING)
# Rewritten only when the tree changes, so overriding MACOS_ANDROIDFW_TREE
# recompiles and relinks instead of keeping headers or an rpath from the old tree.
$(MACOS_OUT)/androidfw-lib-dir.txt: macos-androidfw-lib-dir-check $(MACOS_OUT)/.dir
	@echo '$(MACOS_ANDROIDFW_LIB_DIR) $(MACOS_ANDROIDFW_INCLUDE)' | cmp -s - $@ || echo '$(MACOS_ANDROIDFW_LIB_DIR) $(MACOS_ANDROIDFW_INCLUDE)' > $@
macos-androidfw-lib-dir-check:
$(MACOS_OUT)/libshiraz-resources.so: native/resources.cpp native/resource_colors.hpp include/shiraz/archive_path.hpp mk/darwin.mk $(MACOS_OUT)/androidfw-lib-dir.txt $(MACOS_ANDROIDFW_HEADER) $(MACOS_ANDROIDFW_LIB_DIR)/libandroidfw.so $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -isystem $(MACOS_ANDROIDFW_INCLUDE) -fPIC $(MACOS_DYLIB) native/resources.cpp -o $@ -L$(MACOS_ANDROIDFW_LIB_DIR) -landroidfw -Wl,-rpath,$(MACOS_ANDROIDFW_LIB_DIR) $(MACOS_RPATH) -pthread

$(MACOS_OUT)/shiraz-preview: native/main.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp $(MACOS_PRESENTATION_HEADERS) build/version.hpp $(MACOS_OUT)/libandroid.so $(MACOS_SDL_CONFIG)
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) native/main.cpp native/presentation.cpp native/darwin/gpu_unavailable.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)

# Java surface bridge loaded by dalvikvm through java.library.path. The JNI
# stub keeps NativeGpu.available() false (Java software canvas); every other
# NativeGpu entry throws UnsupportedOperationException until Skia lands (M4).
# libshiraz-gpu also carries the C++ gpu_* fallback, so it exists once.
MACOS_JNI_HEADERS := include/shiraz/window_snapshot.hpp include/shiraz/native_window.h include/shiraz/gpu.hpp build/version.hpp $(MACOS_PRESENTATION_HEADERS)
MACOS_SDL_ARCHIVE := $(wildcard .local/lib/libSDL2.a)
$(MACOS_OUT)/libshiraz-gpu.so: native/darwin/gpu_stub_jni.cpp native/darwin/gpu_unavailable.cpp native/darwin/gpu.exports include/shiraz/gpu.hpp mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) -Wl,-exported_symbols_list,native/darwin/gpu.exports native/darwin/gpu_stub_jni.cpp native/darwin/gpu_unavailable.cpp -o $@

# SDL is linked statically here and its public API stays exported, so test
# helpers bind to this SDL instance instead of linking a second copy.
$(MACOS_OUT)/libshiraz-jni.so: native/jni_surface.cpp native/presentation.cpp $(MACOS_JNI_HEADERS) mk/darwin.mk $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_SDL_CONFIG) $(MACOS_SDL_ARCHIVE)
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) native/jni_surface.cpp native/presentation.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)

$(MACOS_OUT)/libshiraz-test-input.so: tests/native/input_injector.cpp $(MACOS_OUT)/libshiraz-jni.so $(MACOS_SDL_CONFIG)
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) tests/native/input_injector.cpp -o $@ $(MACOS_OUT)/libshiraz-jni.so $(MACOS_RPATH)

# Framework DEX (javac --release 8 + darwin-x86 dx). Same outputs as the Linux
# framework target (build/shiraz-framework.jar, build/framework/classes): the
# DEX is host-independent. A stamp, not the jar, is the target so the Linux
# jar rule's runtime-xml prerequisite (unsupported on macOS) is not pulled in.
# The stamp is remade when either output is missing, and when dx or the core
# libart classes javac compiles against change.
MACOS_FRAMEWORK_SOURCES := $(shell find java -name '*.java')
MACOS_FRAMEWORK_OUTPUTS := build/shiraz-framework.jar build/framework/classes/android/view/Window.class
$(MACOS_ART_HOST)/bin/dx $(MACOS_ART_CORE_CLASSES):
	@$(MACOS_ART_MISSING)
macos-framework-missing:
$(MACOS_OUT)/framework.stamp: $(MACOS_FRAMEWORK_SOURCES) scripts/build_framework.py src/shiraz/host_layout.py VERSION $(MACOS_ART_HOST)/bin/dx $(MACOS_ART_CORE_CLASSES) $(MACOS_OUT)/.dir $(if $(filter-out $(wildcard $(MACOS_FRAMEWORK_OUTPUTS)),$(MACOS_FRAMEWORK_OUTPUTS)),macos-framework-missing)
	python3 scripts/build_framework.py
	@for output in $(MACOS_FRAMEWORK_OUTPUTS); do test -f $$output || { echo "build_framework.py did not produce $$output"; exit 1; }; done
	@touch $@
macos-framework: $(MACOS_OUT)/framework.stamp

# Framework resources reuse the host-independent Makefile rule for
# build/framework-res/framework-res.apk (pinned AOSP sources, host aapt2).
macos-framework-resources: build/framework-res/framework-res.apk

# Minimal pure-Java fixture APK (tests/macos-apk) built with host aapt2 and the
# darwin-x86 dx; the script fails explicitly when dx is missing.
MACOS_APK_FIXTURE_SOURCES := tests/macos-apk/AndroidManifest.xml $(shell find tests/macos-apk -name '*.java')
build/macos-apk/minimal.apk: $(MACOS_APK_FIXTURE_SOURCES) scripts/build_macos_fixture.py scripts/build_apk_fixture.py scripts/fixture_images.py src/shiraz/apk.py src/shiraz/host_layout.py $(MACOS_ART_HOST)/bin/dx
	python3 scripts/build_macos_fixture.py
macos-apk-fixture: build/macos-apk/minimal.apk

MACOS_JNI_SMOKE_DEPS :=macos-framework $(MACOS_OUT)/libshiraz-jni.so $(MACOS_OUT)/libshiraz-test-input.so
# Standalone dalvikvm loads the JNI bridge and presents through SDL (dummy video).
macos-runtime-jni-smoke: $(MACOS_JNI_SMOKE_DEPS)
	python3 scripts/art_smoke.py --program surface
# Same on the real Cocoa driver with the Metal renderer; opens a window briefly.
macos-runtime-jni-window-smoke: $(MACOS_JNI_SMOKE_DEPS)
	python3 scripts/art_smoke.py --program surface --windowed

# The fixture APK through `shiraz run` (ActivityRunner, View draw, SDL present):
# checks rc, the fixture and cleanup markers, http/https URL connections failing
# with IOException, the SDL window backend and renderer, the presented logical
# and pixel size against the snapshot size, and the centre pixel 0x123456.
# Headless (dummy video, software renderer). Needs the ART host tree.
MACOS_RUNTIME_SMOKE_DEPS := macos-runtime-libs macos-framework macos-framework-resources macos-apk-fixture
macos-runtime-smoke: $(MACOS_RUNTIME_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py
# Same on the Cocoa driver; requires the Metal renderer and opens a window briefly.
macos-runtime-window-smoke: $(MACOS_RUNTIME_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --windowed --snapshot $(MACOS_OUT)/runtime-window-smoke/frame.ppm

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

# macos-build (and so macos-check) needs SDL only. libshiraz-resources links
# libandroidfw from the ART host tree, so it is built by macos-runtime-libs.
MACOS_LIBRARIES := libandroid.so libshiraz-host.so libshiraz-bitmap.so libshiraz-sqlite.so libshiraz-gpu.so libshiraz-jni.so libshiraz-test-input.so
MACOS_RUNTIME_LIBRARIES := libshiraz-resources.so
macos-build: $(MACOS_OUT)/shiraz-preview $(addprefix $(MACOS_OUT)/,$(MACOS_LIBRARIES))
macos-runtime-libs: macos-build $(addprefix $(MACOS_OUT)/,$(MACOS_RUNTIME_LIBRARIES))

# SDL tests run headless like their Linux smokes (dummy video, software renderer).
macos-test: $(addprefix $(MACOS_OUT)/,$(MACOS_TESTS) $(MACOS_SDL_TESTS)) $(MACOS_OUT)/libshiraz-host.so
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
