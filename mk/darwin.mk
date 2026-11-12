# SPDX-License-Identifier: GPL-3.0-only
# macOS (Apple Silicon) host build. Included by the Makefile when uname is Darwin.
# The Linux targets (build, test, check) still parse here but are not supported on
# macOS; use the macos-* targets below. Written for the GNU make 3.81 that ships
# with macOS: no grouped targets, no $(file), no !=.
# Outputs go to build/macos/. Shared libraries keep Android's .so names (Mach-O
# inside) so APK-facing names stay stable; they are linked by explicit path.
#
# Cold order on a new Mac: make macos-deps (SDL2), make macos-graphics-deps
# (text/path/image libraries; needs a python3 with tarfile extraction filters,
# 3.12+, not the OS python3 3.9), make macos-build and
# make macos-check (no ART needed), then make runtime-art (alias macos-art:
# builds the ICU/libpng deps stage, clones the pinned sources, applies the
# darwin patch chain and builds the darwin-x86 ART host tree), then
# make macos-runtime-libs (which also builds libshiraz-text/-path/-image, so it
# needs make macos-graphics-deps, fonts and the ICU/libpng deps stage; make
# macos-art-deps builds that stage alone), macos-framework-resources,
# macos-apk-fixture and macos-runtime-smoke. make macos-graphics-deps-check
# also needs fonts and that deps stage (its libpng test).
# OpenGL ES and the GPU canvas: make macos-gpu-deps (ANGLE's Metal backend into
# .local/lib/angle, then the darwin Skia archives; needs git access to
# *.googlesource.com, Xcode's clang and Metal toolchain, python3 3.11+, and ninja
# from make macos-graphics-deps or PATH), then make macos-gpu-deps-check. It
# comes before make macos-build: libshiraz-gpu links Skia, and libshiraz-host
# (reqGlEsVersion probe), libEGL_shim and libshiraz-gles1/-gles2 build against
# ANGLE.

MACOS_OUT := build/macos
# Written by python3 scripts/bootstrap_darwin.py deps (make macos-art-deps,
# also run by make runtime-art): ICU 78.3, lz4, liblzma and libpng16 in .local.
MACOS_ART_DEPS_STAMP := $(MACOS_OUT)/deps/deps.stamp
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
# Same source list as the Linux build/libshiraz-host.so, including the
# native/gles_version.cpp reqGlEsVersion probe (see the OpenGL ES rules below).
MACOS_HOST_SOURCES := native/host_process.cpp native/fds.cpp native/gles_version.cpp native/ipc_channel.cpp native/ipc_jni.cpp native/ipc_spawn.cpp
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
MACOS_TESTS := test-compositor test-window-aspect test-pointer test-keyboard test-archive-path test-native-window test-shared-frame test-choreographer test-sensors test-shared-memory test-image-reader test-hardware-buffer test-system-fonts test-media-crypto test-media-ndk test-ipc-channel test-sync-fence
MACOS_SDL_TESTS := test-presentation test-live-input

.PHONY: macos-deps macos-graphics-deps macos-graphics-deps-check macos-gpu-deps macos-gpu-deps-check macos-art macos-art-depsmacos-runtime-libs macos-framework-missing macos-build macos-test macos-check macos-preview macos-smoke macos-framework macos-runtime-jni-smoke macos-runtime-jni-window-smoke macos-androidfw-lib-dir-check macos-framework-resources macos-apk-fixture macos-runtime-smoke macos-runtime-window-smoke
macos-deps:
	python3 scripts/bootstrap_sdl.py
# Static FreeType, HarfBuzz, fontconfig, pixman, cairo, libwebp and libjpeg-turbo
# in .local for libshiraz-text/-path/-image (a stamp makes reruns no-ops). Its
# own stage, so it never invalidates the ICU/ART dependency stamp.
macos-graphics-deps:
	python3 scripts/bootstrap_darwin_graphics.py
# Links tests/darwin/graphics statically against .local and runs them.
macos-graphics-deps-check: macos-graphics-deps fonts $(MACOS_ART_DEPS_STAMP)
	python3 scripts/bootstrap_darwin_graphics.py check
# ANGLE chromium/8127 (Metal only) built from plain git checkouts with an in-tree
# gn: libEGL, libGLESv2 and libGLESv1_CM in .local/lib/angle (@rpath install
# names), headers in .local/include/angle. Then Skia's Ganesh archives in
# .deps/skia/out/shiraz-darwin, generated with that same gn. Stamps make reruns
# no-ops, and existing builds with matching pins and args are not rebuilt.
macos-gpu-deps:
	python3 scripts/bootstrap_angle.py
	python3 scripts/bootstrap_skia.py
# Compiles tests/darwin/gles/angle_probe.c against the installed headers and runs
# it on the installed dylibs (Metal renderer, ES 3.0/2.0/1.1 pbuffer readback).
macos-gpu-deps-check: macos-gpu-deps
	python3 scripts/bootstrap_angle.py check
MACOS_ANGLE_STAMP := $(MACOS_OUT)/deps/angle.stamp
MACOS_SKIA_STAMP := $(MACOS_OUT)/deps/skia.stamp
MACOS_GPU_DEPS := $(MACOS_ANGLE_STAMP) $(MACOS_SKIA_STAMP)
$(MACOS_ANGLE_STAMP):
	@echo 'ANGLE missing ($@). Run make macos-gpu-deps (pinned in-tree ANGLE Metal libEGL, libGLESv2, libGLESv1_CM).'; exit 1
$(MACOS_SKIA_STAMP):
	@echo 'Skia missing ($@). Run make macos-gpu-deps (pinned in-tree Skia Ganesh archives for macOS).'; exit 1
# Same stage as make runtime-art; named here so the macOS cold path is discoverable.
macos-art: runtime-art
# Only the ICU 78.3, lz4, liblzma and libpng16 stage of make runtime-art (its
# stamp makes reruns no-ops); libshiraz-text and -image build against it.
macos-art-deps:
	python3 scripts/bootstrap_darwin.py deps

$(MACOS_OUT)/.dir:
	@mkdir -p $(MACOS_OUT)
	@touch $@

$(MACOS_SDL_CONFIG):
	@echo 'SDL2 missing. Run make macos-deps (local source build).'; exit 1

$(MACOS_OUT)/libandroid.so: $(MACOS_ANDROID_SOURCES) $(MACOS_ANDROID_HEADERS) mk/darwin.mk $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) $(MACOS_ANDROID_SOURCES) -o $@ -pthread $(MACOS_ANDROID_FRAMEWORKS)

# The Linux library links -lEGL (libglvnd). Here the probe's EGL calls bind to
# libEGL_shim, which reaches ANGLE only through SHIRAZ_EGL_LIBRARY at run time:
# with ANGLE absent the probe finds no display and reports 0, and this
# frame-critical library still loads.
$(MACOS_OUT)/libshiraz-host.so: $(MACOS_HOST_SOURCES) include/shiraz/ipc_channel.hpp mk/darwin.mk $(MACOS_OUT)/libEGL_shim.so $(MACOS_ANGLE_STAMP) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_ANGLE_CFLAGS) -fPIC $(MACOS_DYLIB) $(MACOS_HOST_SOURCES) -o $@ $(MACOS_OUT)/libEGL_shim.so $(MACOS_RPATH)

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

# libshiraz-text (native/text.cpp), -path (native/path.cpp, native/ripple.cpp)
# and -image (native/image.cpp), compiled from the Linux sources unchanged and
# linked against the static FreeType, HarfBuzz, fontconfig, pixman, cairo,
# libwebp, libjpeg-turbo and libpng archives in .local (make macos-graphics-deps)
# plus the SDK's libz and libexpat. Flags come only from the in-tree .pc files:
# PKG_CONFIG_LIBDIR replaces the default search path, so no Homebrew .pc file is
# read (pkg-config itself, Homebrew pkgconf on this host, is only a build tool).
# Each dylib exports exactly the JNI entry points its Java classes declare
# (native/darwin/*.exports, generated by scripts/darwin_jni_exports.py), so its
# private libpng/zlib/FreeType copies never interpose on libandroidfw's hidden
# libpng or anything else in the process. The recipe checks the list against
# the Java sources before linking and nm -gU against the list after linking,
# and deletes the dylib on a mismatch.
MACOS_GRAPHICS_STAMP := $(MACOS_OUT)/deps/graphics.stamp
MACOS_PKG_CONFIG = env -u PKG_CONFIG_SYSROOT_DIR PKG_CONFIG_LIBDIR=$(CURDIR)/.local/lib/pkgconfig PKG_CONFIG_PATH= pkg-config
MACOS_TEXT_PACKAGES := freetype2 harfbuzz fontconfig
MACOS_PATH_PACKAGES := cairo
MACOS_IMAGE_PACKAGES := libpng zlib libwebp libjpeg
# libpng16 comes from the bootstrap_darwin.py deps stage, everything else in
# these lists from make macos-graphics-deps (zlib.pc describes the SDK libz).
MACOS_PKG_STAGE = $(if $(filter libpng,$(1)),make macos-art-deps (libpng16) and make macos-graphics-deps,make macos-graphics-deps)
MACOS_PKG_MISSING = command -v pkg-config >/dev/null || { echo 'pkg-config is required as a build tool and is not on PATH.'; exit 1; }; for package in $(1); do $(MACOS_PKG_CONFIG) --exists $$package || { echo "Missing in-tree $$package (.local/lib/pkgconfig). Run $(MACOS_PKG_STAGE)."; exit 1; }; done
# ICU 78.3 from bootstrap_darwin.py has no .pc file; .local/include holds its
# full (symbol-renamed) headers, ahead of the SDK's partial libicucore set.
# make runtime-art stages byte-identical copies next to libjavacore in the ART
# host lib64; the rpath names that directory first and .local/lib (the link
# input) second, for use outside ART (ctypes, tests). dyld reuses an already
# loaded image with the same install name for @rpath/libicuuc.78.dylib (checked
# with ctypes on macOS 27.2), so an ART process keeps the one ICU that
# libjavacore loaded. The @loader_path paths assume the dylib sits in build/macos.
MACOS_ICU_CFLAGS := -I$(CURDIR)/.local/include
MACOS_ICU_LIBS := -L$(CURDIR)/.local/lib -licuuc
ifeq ($(MACOS_ANDROIDFW_TREE),$(CURDIR)/context/art_standalone)
MACOS_ICU_RPATH := -Wl,-rpath,@loader_path/../../context/art_standalone/out/host/darwin-x86/lib64
else
MACOS_ICU_RPATH := -Wl,-rpath,$(MACOS_ANDROIDFW_LIB_DIR)
endif
MACOS_ICU_RPATH += -Wl,-rpath,@loader_path/../../.local/lib
MACOS_TEXT_CFLAGS = $(shell $(MACOS_PKG_CONFIG) --static --cflags $(MACOS_TEXT_PACKAGES) 2>/dev/null) $(MACOS_ICU_CFLAGS)
MACOS_TEXT_LIBS = $(shell $(MACOS_PKG_CONFIG) --static --libs $(MACOS_TEXT_PACKAGES) 2>/dev/null) $(MACOS_ICU_LIBS)
MACOS_PATH_CFLAGS = $(shell $(MACOS_PKG_CONFIG) --static --cflags $(MACOS_PATH_PACKAGES) 2>/dev/null)
MACOS_PATH_LIBS = $(shell $(MACOS_PKG_CONFIG) --static --libs $(MACOS_PATH_PACKAGES) 2>/dev/null)
MACOS_IMAGE_CFLAGS = $(shell $(MACOS_PKG_CONFIG) --static --cflags $(MACOS_IMAGE_PACKAGES) 2>/dev/null)
MACOS_IMAGE_LIBS = $(shell $(MACOS_PKG_CONFIG) --static --libs $(MACOS_IMAGE_PACKAGES) 2>/dev/null)
MACOS_TEXT_JAVA := java/shiraz/runtime/NativeText.java java/android/graphics/FontFamily.java
MACOS_PATH_JAVA := java/shiraz/runtime/NativePath.java
MACOS_IMAGE_JAVA := java/shiraz/runtime/NativeImage.java
MACOS_GRAPHICS_LIB_DEPS := scripts/darwin_jni_exports.py mk/darwin.mk $(MACOS_GRAPHICS_STAMP) $(MACOS_OUT)/.dir
# libshiraz-image embeds libpng16.a and libshiraz-text compiles against the ICU
# headers, both from the bootstrap_darwin.py deps stage: relink when it reruns.
MACOS_TEXT_ART_DEPS := $(MACOS_ART_DEPS_STAMP) .local/include/unicode/ubidi.h
MACOS_IMAGE_ART_DEPS := $(MACOS_ART_DEPS_STAMP) .local/lib/libpng16.a
# The checkout path is baked into libshiraz-text (-DSHIRAZ_FONT_DIR) and into
# .local/etc/fonts/fonts.conf. The file changes only when the checkout moves,
# which relinks; the recipe then rejects a fonts.conf from another checkout.
MACOS_GRAPHICS_ROOT := $(MACOS_OUT)/graphics-root.txt
MACOS_FONTS_CONF_ROOT_CHECK = grep -Fq '<cachedir>$(CURDIR)/.local/var/cache/fontconfig</cachedir>' .local/etc/fonts/fonts.conf || { echo '.local/etc/fonts/fonts.conf is missing or belongs to another checkout path. Run make macos-graphics-deps.'; exit 1; }
# $(1) exports list, $(2) Java sources. -dead_strip drops the archive code no
# exported entry point reaches.
MACOS_EXPORTS_FLAGS = -Wl,-exported_symbols_list,$(1) -Wl,-dead_strip
MACOS_EXPORTS_PRECHECK = python3 scripts/darwin_jni_exports.py --check $(1) $(2)
MACOS_EXPORTS_CHECK = nm -gU $@ > $@.nm && python3 scripts/darwin_jni_exports.py --check $(1) --symbols $@.nm $(2) || { rm -f $@ $@.nm; exit 1; }; rm -f $@.nm
$(MACOS_GRAPHICS_STAMP):
	@echo 'Graphics dependencies missing ($@). Run make macos-graphics-deps (pinned in-tree FreeType, HarfBuzz, fontconfig, pixman, cairo, libwebp, libjpeg-turbo).'; exit 1
$(MACOS_ART_DEPS_STAMP) .local/lib/libpng16.a .local/include/unicode/ubidi.h:
	@echo 'ICU/libpng dependencies missing ($@). Run make macos-art-deps (python3 scripts/bootstrap_darwin.py deps; make runtime-art also runs it).'; exit 1
$(MACOS_GRAPHICS_ROOT): macos-androidfw-lib-dir-check $(MACOS_OUT)/.dir
	@echo '$(CURDIR)' | cmp -s - $@ || echo '$(CURDIR)' > $@

$(MACOS_OUT)/libshiraz-text.so: native/text.cpp include/shiraz/compositor.hpp native/darwin/text.exports $(MACOS_TEXT_JAVA) $(MACOS_GRAPHICS_LIB_DEPS) $(MACOS_TEXT_ART_DEPS) $(MACOS_GRAPHICS_ROOT) | fonts
	@$(call MACOS_PKG_MISSING,$(MACOS_TEXT_PACKAGES))
	@$(MACOS_FONTS_CONF_ROOT_CHECK)
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/text.exports,$(MACOS_TEXT_JAVA))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_TEXT_CFLAGS) -DSHIRAZ_FONT_DIR='"$(CURDIR)/.local/share/shiraz/fonts"' -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/text.exports) native/text.cpp -o $@ $(MACOS_TEXT_LIBS) $(MACOS_ICU_RPATH) -pthread
	@$(call MACOS_EXPORTS_CHECK,native/darwin/text.exports,$(MACOS_TEXT_JAVA))

$(MACOS_OUT)/libshiraz-path.so: native/path.cpp native/ripple.cpp include/shiraz/compositor.hpp include/shiraz/color_filter.hpp native/darwin/path.exports $(MACOS_PATH_JAVA) $(MACOS_GRAPHICS_LIB_DEPS)
	@$(call MACOS_PKG_MISSING,$(MACOS_PATH_PACKAGES))
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/path.exports,$(MACOS_PATH_JAVA))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_PATH_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/path.exports) native/path.cpp native/ripple.cpp -o $@ $(MACOS_PATH_LIBS)
	@$(call MACOS_EXPORTS_CHECK,native/darwin/path.exports,$(MACOS_PATH_JAVA))

$(MACOS_OUT)/libshiraz-image.so: native/image.cpp native/darwin/image.exports $(MACOS_IMAGE_JAVA) $(MACOS_GRAPHICS_LIB_DEPS) $(MACOS_IMAGE_ART_DEPS)
	@$(call MACOS_PKG_MISSING,$(MACOS_IMAGE_PACKAGES))
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/image.exports,$(MACOS_IMAGE_JAVA))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_IMAGE_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/image.exports) native/image.cpp -o $@ $(MACOS_IMAGE_LIBS)
	@$(call MACOS_EXPORTS_CHECK,native/darwin/image.exports,$(MACOS_IMAGE_JAVA))

# Links libshiraz-gpu like the Linux shiraz-preview (SDL_LIBS -lshiraz-gpu).
$(MACOS_OUT)/shiraz-preview: native/main.cpp native/presentation.cpp $(MACOS_PRESENTATION_HEADERS) build/version.hpp $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_SDL_CONFIG)
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) native/main.cpp native/presentation.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)

# Java surface bridge loaded by dalvikvm through java.library.path.
MACOS_JNI_HEADERS := include/shiraz/window_snapshot.hpp include/shiraz/native_window.h include/shiraz/gpu.hpp build/version.hpp $(MACOS_PRESENTATION_HEADERS)
MACOS_SDL_ARCHIVE := $(wildcard .local/lib/libSDL2.a)

# OpenGL ES and the Skia GPU canvas (M4a). ANGLE's Metal libEGL, libGLESv2 and
# libGLESv1_CM (make macos-gpu-deps; @rpath install names, ad-hoc signed) are
# copied into build/macos, the runtime's native library directory: a temporary
# copy is renamed over the old file, never rewritten in place, and its
# signature is verified. native/egl_shim.cpp, gles1_jni.cpp, gles2_jni.cpp and
# gpu.cpp compile unchanged from the Linux sources, against ANGLE's Khronos
# headers in .local/include/angle and Skia in .deps/skia/out/shiraz-darwin.
# Darwin library names: the Linux libEGL_shim.so keeps its name. Linux also
# links it as libEGL.so.1 / libEGL_shiraz.so.1 for bionic-loaded app libraries
# and dlopen; macOS runs no APK native code yet (M5), and from_surface.cpp
# dlopens @loader_path/libEGL_shim.so, so no alias is made.
# Exports: each list is checked against its sources by darwin_jni_exports.py
# before linking and against nm -gU after (MACOS_EXPORTS_*), as for text/path/image.
MACOS_ANGLE_DIR := .local/lib/angle
MACOS_ANGLE_CFLAGS := -isystem .local/include/angle
MACOS_ANGLE_LIBRARIES := libEGL.dylib libGLESv2.dylib libGLESv1_CM.dylib
MACOS_GLES_LIBRARIES := $(MACOS_ANGLE_LIBRARIES) libEGL_shim.so libshiraz-gles1.so libshiraz-gles2.so
MACOS_SKIA_OUT := .deps/skia/out/shiraz-darwin
MACOS_SKIA_ARCHIVES := $(MACOS_SKIA_OUT)/libpathkit.a $(MACOS_SKIA_OUT)/libskia.a
# The Linux build/libshiraz-gpu.so flags; fonts are off in the darwin Skia, so
# no FreeType/fontconfig, and Skia gets GL only through gpu_initialize's getProc
# (SDL_GL_GetProcAddress), so no GL library is linked either.
MACOS_SKIA_CFLAGS := -isystem .deps/skia -DSK_GANESH -DSK_GL -DSK_R32_SHIFT=16
MACOS_GLES_LIB_DEPS := scripts/darwin_jni_exports.py mk/darwin.mk $(MACOS_ANGLE_STAMP) $(MACOS_OUT)/.dir
$(addprefix $(MACOS_ANGLE_DIR)/,$(MACOS_ANGLE_LIBRARIES)):
	@echo 'ANGLE library missing ($@). Run make macos-gpu-deps.'; exit 1
$(addprefix $(MACOS_OUT)/,$(MACOS_ANGLE_LIBRARIES)): $(MACOS_OUT)/%: $(MACOS_ANGLE_DIR)/% $(MACOS_ANGLE_STAMP) mk/darwin.mk $(MACOS_OUT)/.dir
	cp -f $(MACOS_ANGLE_DIR)/$* $@.tmp
	codesign --verify --strict $@.tmp || { rm -f $@.tmp; exit 1; }
	mv -f $@.tmp $@
$(MACOS_SKIA_ARCHIVES):
	@echo 'Skia archive missing ($@). Run make macos-gpu-deps.'; exit 1

# native/darwin/egl_shim_gles1.inc (included under __APPLE__) gives the shim's
# window framebuffer the OES entry points in ES 1 contexts, which ANGLE requires;
# native/darwin/egl_shim_contexts.inc drops a destroyed context's window
# framebuffer entries when eglDestroyContext is called. Under __APPLE__ the swap
# readback also calls glFinish first (ANGLE Metal returns zeros from a
# concurrent second context otherwise; build/macos/m4/race/).
# GL calls go to libGLESv2, weakly linked so that a missing ANGLE leaves the shim
# loadable (libshiraz-host links it); the shim makes none of them before ANGLE's
# libEGL (SHIRAZ_EGL_LIBRARY) has given it a current context. The real
# glBindFramebuffer comes from dlsym(RTLD_NEXT), which dyld resolves in this
# image's dependents (tests/darwin/gles/egl_shim_window.cpp checks it with
# libGLESv2 loaded first, as SDL does in a window).
$(MACOS_OUT)/libEGL_shim.so: native/egl_shim.cpp native/darwin/egl_shim_gles1.inc native/darwin/egl_shim_contexts.inc include/shiraz/native_window.h include/shiraz/hardware_buffer.h native/darwin/egl_shim.exports $(MACOS_GLES_LIB_DEPS) $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libGLESv2.dylib
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/egl_shim.exports,--c-source native/egl_shim.cpp)
	$(MACOS_CXX) $(MACOS_ANGLE_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/egl_shim.exports) native/egl_shim.cpp -o $@ $(MACOS_OUT)/libandroid.so -Wl,-weak_library,$(MACOS_OUT)/libGLESv2.dylib $(MACOS_RPATH)
	@$(call MACOS_EXPORTS_CHECK,native/darwin/egl_shim.exports,--c-source native/egl_shim.cpp)

# native/darwin/gles_readback.cpp gives the app's GLES20/GL10 glReadPixels a
# glFinish first: ANGLE Metal returns zeros from glReadPixels while another
# context renders on another thread (build/macos/m4/race/). It is linked into
# both JNI libraries, so their glReadPixels binds in-image (two-level namespace)
# and the check requires that none is imported from ANGLE; the export list
# keeps the definition private.
MACOS_GLES_READBACK = native/darwin/gles_readback.cpp -DSHIRAZ_GLES_READBACK_LIBRARY='"@rpath/$(1)"'
MACOS_GLES_READBACK_CHECK = nm -m $@ > $@.nm-m && grep -Eq '^[0-9a-f]+ \(__TEXT,__text\) non-external .*_glReadPixels$$' $@.nm-m && ! grep -Fq ' _glReadPixels (from ' $@.nm-m || { echo '$@: glReadPixels must be the in-image native/darwin/gles_readback.cpp definition'; rm -f $@ $@.nm-m; exit 1; }; rm -f $@.nm-m

# The shim comes first on the link line, as on Linux. Two-level namespace binds
# each import to the dylib that supplied it at link time, so the recipe also
# requires GLES20.glBindFramebuffer's shiraz_egl_bind_framebuffer to bind to
# libEGL_shim and no plain glBindFramebuffer to be imported at all (nm -m).
MACOS_GLES2_JAVA := java/android/opengl/GLES20.java java/android/opengl/GLES30.java
MACOS_GLES2_BIND_CHECK = nm -m $@ > $@.nm-m && grep -Fq '(undefined) external _shiraz_egl_bind_framebuffer (from @rpath/libEGL_shim.so)' $@.nm-m && ! grep -Fq ' _glBindFramebuffer (from ' $@.nm-m || { echo '$@: shiraz_egl_bind_framebuffer must bind to libEGL_shim and no glBindFramebuffer may be imported'; rm -f $@ $@.nm-m; exit 1; }; rm -f $@.nm-m
$(MACOS_OUT)/libshiraz-gles2.so: native/gles2_jni.cpp native/darwin/gles_readback.cpp native/darwin/gles2.exports $(MACOS_GLES2_JAVA) $(MACOS_GLES_LIB_DEPS) $(MACOS_OUT)/libEGL_shim.so $(MACOS_OUT)/libGLESv2.dylib
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/gles2.exports,$(MACOS_GLES2_JAVA))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_ANGLE_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/gles2.exports) native/gles2_jni.cpp $(call MACOS_GLES_READBACK,libGLESv2.dylib) -o $@ $(MACOS_OUT)/libEGL_shim.so $(MACOS_OUT)/libGLESv2.dylib $(MACOS_RPATH)
	@$(call MACOS_EXPORTS_CHECK,native/darwin/gles2.exports,$(MACOS_GLES2_JAVA))
	@$(MACOS_GLES2_BIND_CHECK)
	@$(MACOS_GLES_READBACK_CHECK)

# ES 1 entry points (glOrthof, glMatrixMode, ...) exist only in libGLESv1_CM,
# which resolves @rpath/libGLESv2.dylib beside itself. ANGLE's GLES/gl.h
# declares prototypes only under GL_GLES_PROTOTYPES (its GLES2/GLES3 headers
# default it to 1; Mesa's GLES/gl.h has no such guard).
MACOS_GLES1_JAVA := java/shiraz/runtime/HostGL10.java java/android/opengl/GLUtils.java
MACOS_GLES1_CFLAGS := -DGL_GLES_PROTOTYPES=1
$(MACOS_OUT)/libshiraz-gles1.so: native/gles1_jni.cpp native/darwin/gles_readback.cpp native/darwin/gles1.exports $(MACOS_GLES1_JAVA) $(MACOS_GLES_LIB_DEPS) $(MACOS_OUT)/libGLESv1_CM.dylib $(MACOS_OUT)/libGLESv2.dylib
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/gles1.exports,$(MACOS_GLES1_JAVA))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_ANGLE_CFLAGS) $(MACOS_GLES1_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/gles1.exports) native/gles1_jni.cpp $(call MACOS_GLES_READBACK,libGLESv1_CM.dylib) -o $@ $(MACOS_OUT)/libGLESv1_CM.dylib $(MACOS_RPATH)
	@$(call MACOS_EXPORTS_CHECK,native/darwin/gles1.exports,$(MACOS_GLES1_JAVA))
	@$(MACOS_GLES_READBACK_CHECK)

# native/gpu.cpp with Skia Ganesh GL. It never links ANGLE: with ANGLE absent
# SDL picks the Metal renderer, presentation.cpp sees no GL context and never
# calls gpu_initialize, NativeGpu.available() stays false and Canvas uses the
# Java software raster; Path.op (Skia path ops) works either way. Exports: the
# NativeGpu JNI methods and the include/shiraz/gpu.hpp functions.
MACOS_GPU_JAVA := java/shiraz/runtime/NativeGpu.java
MACOS_GPU_EXPORT_SOURCES := --extra native/darwin/gpu.cxx-symbols $(MACOS_GPU_JAVA)
$(MACOS_OUT)/libshiraz-gpu.so: native/gpu.cpp native/ripple_gpu.inc include/shiraz/gpu.hpp include/shiraz/compositor.hpp native/darwin/gpu.exports native/darwin/gpu.cxx-symbols $(MACOS_GPU_JAVA) scripts/darwin_jni_exports.py mk/darwin.mk $(MACOS_SKIA_STAMP) $(MACOS_SKIA_ARCHIVES) $(MACOS_OUT)/.dir
	@$(call MACOS_EXPORTS_PRECHECK,native/darwin/gpu.exports,$(MACOS_GPU_EXPORT_SOURCES))
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_SKIA_CFLAGS) -fPIC $(MACOS_DYLIB) $(call MACOS_EXPORTS_FLAGS,native/darwin/gpu.exports) native/gpu.cpp $(MACOS_SKIA_ARCHIVES) -o $@ -pthread
	@$(call MACOS_EXPORTS_CHECK,native/darwin/gpu.exports,$(MACOS_GPU_EXPORT_SOURCES))

# The shim on ANGLE over a Shiraz ANativeWindow: window framebuffer, swap
# readback, resize and every glBindFramebuffer route, on ES 3 and ES 1 contexts.
.PHONY: macos-egl-shim-test
$(MACOS_OUT)/test-egl-shim-window: tests/darwin/gles/egl_shim_window.cpp include/shiraz/native_window.h $(MACOS_OUT)/libandroid.so $(MACOS_ANGLE_STAMP)
	$(MACOS_CXX) $(MACOS_ANGLE_CFLAGS) tests/darwin/gles/egl_shim_window.cpp -o $@ $(MACOS_LINK_ANDROID)
macos-egl-shim-test: $(MACOS_OUT)/test-egl-shim-window $(addprefix $(MACOS_OUT)/,$(MACOS_GLES_LIBRARIES))
	SHIRAZ_EGL_LIBRARY=$(CURDIR)/$(MACOS_OUT)/libEGL.dylib ./$(MACOS_OUT)/test-egl-shim-window $(CURDIR)/$(MACOS_OUT)
	@$(MAKE) --no-print-directory macos-egl-shim-no-angle-test

# The shim without ANGLE, from a cwd holding a planted libEGL_mesa_host.so.1
# (tests/darwin/gles/egl_shim_no_angle.cpp): no EGL library may load (the
# planted constructor's marker must not appear) and swap/bind must return.
MACOS_SHIM_NO_ANGLE := $(MACOS_OUT)/egl-shim-no-angle
.PHONY: macos-egl-shim-no-angle-test
$(MACOS_OUT)/test-egl-shim-no-angle: tests/darwin/gles/egl_shim_no_angle.cpp include/shiraz/native_window.h $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) tests/darwin/gles/egl_shim_no_angle.cpp -o $@ $(MACOS_OUT)/libandroid.so -Wl,-rpath,@loader_path/egl-shim-no-angle/lib
macos-egl-shim-no-angle-test: $(MACOS_OUT)/test-egl-shim-no-angle $(MACOS_OUT)/libEGL_shim.so $(MACOS_OUT)/libandroid.so
	rm -rf $(MACOS_SHIM_NO_ANGLE) && mkdir -p $(MACOS_SHIM_NO_ANGLE)/lib $(MACOS_SHIM_NO_ANGLE)/cwd
	cp $(MACOS_OUT)/libEGL_shim.so $(MACOS_OUT)/libandroid.so $(MACOS_SHIM_NO_ANGLE)/lib/
	printf '#include <stdio.h>\n__attribute__((constructor)) static void planted(void){fprintf(stderr,"PLANTED_LIBRARY_LOADED\\n");}\n' > $(MACOS_SHIM_NO_ANGLE)/planted.c
	$(CC) -dynamiclib $(MACOS_SHIM_NO_ANGLE)/planted.c -o $(MACOS_SHIM_NO_ANGLE)/cwd/libEGL_mesa_host.so.1
	set -e; for value in - libEGL.dylib libEGL_mesa_host.so.1 /nonexistent/libEGL.dylib; do \
	  echo "== SHIRAZ_EGL_LIBRARY=$$value"; \
	  (cd $(MACOS_SHIM_NO_ANGLE)/cwd && ../../test-egl-shim-no-angle $(CURDIR)/$(MACOS_SHIM_NO_ANGLE)/lib $$value) > $(MACOS_SHIM_NO_ANGLE)/run.log 2>&1 || { cat $(MACOS_SHIM_NO_ANGLE)/run.log; exit 1; }; \
	  cat $(MACOS_SHIM_NO_ANGLE)/run.log; ! grep -q PLANTED_LIBRARY_LOADED $(MACOS_SHIM_NO_ANGLE)/run.log; done

# SDL is linked statically here and its public API stays exported, so test
# helpers bind to this SDL instance instead of linking a second copy.
$(MACOS_OUT)/libshiraz-jni.so: native/jni_surface.cpp native/presentation.cpp include/shiraz/window_aspect.hpp $(MACOS_JNI_HEADERS) mk/darwin.mk $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_SDL_CONFIG) $(MACOS_SDL_ARCHIVE)
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
# With ANGLE built, `shiraz run` defaults a Cocoa window to SDL's opengles2
# renderer (src/shiraz/runtime.py DARWIN_GLES); the window smokes pin metal
# (SDL_RENDER_DRIVER) so the no-ANGLE fallback path keeps a test, and the
# headless smokes keep SDL's dummy driver and software renderer either way.
# The frame checks of both window smokes read ActivityRunner's snapshot, the
# framework's CPU-composited frame: they prove what Shiraz drew, while Metal is
# asserted only through SHIRAZ_PRESENTATION renderer=metal. What Metal put on
# screen (texture upload, channel order, stride) is not read back here.
macos-runtime-window-smoke: $(MACOS_RUNTIME_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --windowed --snapshot $(MACOS_OUT)/runtime-window-smoke/frame.ppm
# The default Cocoa renderer (opengles2 on ANGLE Metal): additionally requires
# an accelerated renderer, an ANGLE Metal device, the Skia GPU canvas, the
# centre pixel in SDL's presented readback, and SHIRAZ_EGL_SURFACE reporting
# an EGL window surface the size of SDL's output (a larger surface puts the
# frame in one corner of the window, which the readback cannot see).
macos-runtime-gles2-window-smoke: $(MACOS_RUNTIME_SMOKE_DEPS) $(addprefix $(MACOS_OUT)/,$(MACOS_GLES_LIBRARIES))
	python3 scripts/macos_runtime_smoke.py --windowed --expect-renderer opengles2 --snapshot $(MACOS_OUT)/runtime-gles2-window-smoke/frame.ppm

$(MACOS_OUT)/test-compositor: tests/compositor.cpp include/shiraz/compositor.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/compositor.cpp -o $@
$(MACOS_OUT)/test-pointer: tests/pointer.cpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/pointer.cpp -o $@
$(MACOS_OUT)/test-keyboard: tests/keyboard.cpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/evdev_key_codes.h $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/keyboard.cpp -o $@
$(MACOS_OUT)/test-window-aspect: tests/native/window_aspect.cpp include/shiraz/window_aspect.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native/window_aspect.cpp -o $@
$(MACOS_OUT)/test-archive-path: tests/native/archive_path.cpp include/shiraz/archive_path.hpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native/archive_path.cpp -o $@
$(MACOS_OUT)/test-native-window: tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp include/shiraz/shared_frame.hpp include/shiraz/window_frames.hpp include/shiraz/native_window.h include/shiraz/window_snapshot.hpp include/shiraz/compositor.hpp native/darwin/shared_event.mm native/darwin/window_queue.inc $(MACOS_OUT)/.dir
	$(MACOS_CXX) tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp native/darwin/shared_event.mm -o $@ -pthread -framework IOSurface -framework Metal -framework Foundation -framework CoreFoundation
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
$(MACOS_OUT)/test-presentation: tests/presentation.cpp native/presentation.cpp $(MACOS_PRESENTATION_HEADERS) $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/presentation.cpp native/presentation.cpp -o $@ $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)
$(MACOS_OUT)/test-live-input: tests/live_input.cpp native/presentation.cpp $(MACOS_PRESENTATION_HEADERS) $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_SDL_CONFIG) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_SDL_CFLAGS) tests/live_input.cpp native/presentation.cpp -o $@ $(MACOS_OUT)/libshiraz-gpu.so $(MACOS_RPATH) $(MACOS_SDL_LIBS)

# macos-build (and so macos-check) needs SDL only. libshiraz-resources links
# libandroidfw from the ART host tree, so it is built by macos-runtime-libs.
# The text/path/image libraries need make macos-graphics-deps; they are part of
# macos-runtime-libs because libshiraz-text is frame-critical (as on Linux,
# where build/libshiraz-text.so and the bundled fonts are required inputs).
MACOS_LIBRARIES := libandroid.so libshiraz-host.so libshiraz-bitmap.so libshiraz-sqlite.so libshiraz-gpu.so libshiraz-jni.so libshiraz-test-input.so $(MACOS_GLES_LIBRARIES)
MACOS_GRAPHICS_LIBRARIES := libshiraz-text.so libshiraz-path.so libshiraz-image.so
MACOS_RUNTIME_LIBRARIES := libshiraz-resources.so
macos-build: $(MACOS_OUT)/shiraz-preview $(addprefix $(MACOS_OUT)/,$(MACOS_LIBRARIES))
macos-graphics-libs: $(addprefix $(MACOS_OUT)/,$(MACOS_GRAPHICS_LIBRARIES))
macos-runtime-libs: macos-build macos-graphics-libs $(addprefix $(MACOS_OUT)/,$(MACOS_RUNTIME_LIBRARIES))

# SDL tests run headless like their Linux smokes (dummy video, software renderer).
macos-test: $(addprefix $(MACOS_OUT)/,$(MACOS_TESTS) $(MACOS_SDL_TESTS)) $(MACOS_OUT)/libshiraz-host.so macos-egl-shim-test
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
# The preview on SDL's opengles2 renderer (ANGLE Metal, as `shiraz run` picks
# for a Cocoa window): SDL's output must equal ANGLE's EGL window surface. On a
# 1x display a stale CALayer contentsScale made the surface 2x the output and
# the frame filled the bottom-left quarter (patches/sdl-cocoa-gles-layer-scale.patch).
macos-smoke-gles2: macos-build
	env -u SDL_VIDEODRIVER SDL_RENDER_DRIVER=opengles2 SDL_VIDEO_EGL_DRIVER=$(CURDIR)/$(MACOS_OUT)/libEGL.dylib SDL_VIDEO_GL_DRIVER=$(CURDIR)/$(MACOS_OUT)/libGLESv2.dylib ./$(MACOS_OUT)/shiraz-preview --frames 30 --snapshot $(MACOS_OUT)/preview-gles2.ppm 2> $(MACOS_OUT)/preview-gles2.err; status=$$?; cat $(MACOS_OUT)/preview-gles2.err; test $$status -eq 0
	grep -q 'SHIRAZ_PRESENTATION renderer=opengles2 accelerated=1' $(MACOS_OUT)/preview-gles2.err
	grep -q 'SHIRAZ_EGL_SURFACE .* match=1' $(MACOS_OUT)/preview-gles2.err
	! grep -q 'SHIRAZ_EGL_SURFACE .* match=0' $(MACOS_OUT)/preview-gles2.err

macos-check: macos-test macos-smoke macos-smoke-gles2
	python3 scripts/darwin_linux_guard.py
	python3 scripts/version.py --check
	python3 scripts/commit_guard.py

# Graphics fixture APK (tests/macos-apk-graphics): text, vector paths and
# PNG/WebP/JPEG assets that scripts/macos_graphics_images.py generates at build
# time, plus Bitmap.compress round trips. The graphics smokes need
# libshiraz-text, -path and -image (macos-graphics-libs); a missing one fails
# the run with UnsatisfiedLinkError, which the smoke reports.
.PHONY: macos-apk-graphics-fixture macos-graphics-libs macos-runtime-graphics-smoke macos-runtime-graphics-window-smoke macos-runtime-graphics-gles2-window-smoke macos-runtime-gles2-window-smoke macos-smoke-gles2
MACOS_APK_GRAPHICS_SOURCES := tests/macos-apk-graphics/AndroidManifest.xml $(shell find tests/macos-apk-graphics -name '*.java')
build/macos-apk/graphics.apk: $(MACOS_APK_GRAPHICS_SOURCES) scripts/build_macos_fixture.py scripts/macos_graphics_images.py scripts/build_apk_fixture.py scripts/fixture_images.py src/shiraz/apk.py src/shiraz/host_layout.py $(MACOS_ART_HOST)/bin/dx
	python3 scripts/build_macos_fixture.py --fixture graphics
macos-apk-graphics-fixture: build/macos-apk/graphics.apk
MACOS_RUNTIME_GRAPHICS_SMOKE_DEPS := macos-runtime-libs macos-graphics-libs macos-framework macos-framework-resources macos-apk-graphics-fixture
macos-runtime-graphics-smoke: $(MACOS_RUNTIME_GRAPHICS_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile graphics
# Windowed: frame checks on the framework snapshot, as for macos-runtime-window-smoke.
macos-runtime-graphics-window-smoke: $(MACOS_RUNTIME_GRAPHICS_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile graphics --windowed --snapshot $(MACOS_OUT)/runtime-graphics-window-smoke/frame.ppm
# Text, paths and images through Skia Ganesh GL on ANGLE Metal (the default
# Cocoa renderer), with the GPU and EGL surface checks of
# macos-runtime-gles2-window-smoke and the graphics frame checks applied to
# both ActivityRunner's snapshot and SDL's presented readback.
macos-runtime-graphics-gles2-window-smoke: $(MACOS_RUNTIME_GRAPHICS_SMOKE_DEPS) $(addprefix $(MACOS_OUT)/,$(MACOS_GLES_LIBRARIES))
	python3 scripts/macos_runtime_smoke.py --profile graphics --windowed --expect-renderer opengles2 --snapshot $(MACOS_OUT)/runtime-graphics-gles2-window-smoke/frame.ppm

# Frame-rate fixture APK (tests/macos-apk-framerate): a Canvas View that redraws
# every frame. macos-framerate-bench runs it in a window for 20 s and prints the
# presenter's SHIRAZ_FRAME_STATS summary; it fails below 55 presents per second.
.PHONY: macos-apk-framerate-fixture macos-framerate-bench
MACOS_APK_FRAMERATE_SOURCES := tests/macos-apk-framerate/AndroidManifest.xml $(shell find tests/macos-apk-framerate -name '*.java')
build/macos-apk/framerate.apk: $(MACOS_APK_FRAMERATE_SOURCES) scripts/build_macos_fixture.py scripts/build_apk_fixture.py src/shiraz/apk.py src/shiraz/host_layout.py $(MACOS_ART_HOST)/bin/dx
	python3 scripts/build_macos_fixture.py --fixture framerate
macos-apk-framerate-fixture: build/macos-apk/framerate.apk
macos-framerate-bench: macos-runtime-libs macos-graphics-libs macos-framework macos-framework-resources macos-apk-framerate-fixture
	python3 scripts/macos_framerate_bench.py --min-fps 55

# GLES fixture APK (tests/macos-apk-gles): an ES 2 GLSurfaceView (shader
# triangle, GLES30 vertex array object) and an ES 1 GLSurfaceView (GL10 and a
# GLUtils texture) on their own GL threads, beside a coloured View and a
# TextView. The app checks glReadPixels exactly; the smoke checks the
# presented frame. Headless keeps the software SDL renderer (the app's EGL
# contexts do not need SDL's); the window smoke requires renderer=opengles2,
# an ANGLE Metal SHIRAZ_GPU device and the Skia GPU canvas, and also checks
# the frame SDL presented (SHIRAZ_PRESENT_SNAPSHOT). Both fail while
# libshiraz-gles1/-gles2, ANGLE or the darwin EGL backend are missing.
.PHONY: macos-apk-gles-fixture macos-runtime-gles-smoke macos-runtime-gles-window-smoke macos-runtime-gles-es2-pair-smoke macos-runtime-gles-es2-pair-window-smoke
MACOS_APK_GLES_SOURCES := tests/macos-apk-gles/AndroidManifest.xml $(shell find tests/macos-apk-gles -name '*.java')
build/macos-apk/gles.apk: $(MACOS_APK_GLES_SOURCES) scripts/build_macos_fixture.py scripts/build_apk_fixture.py scripts/fixture_images.py src/shiraz/apk.py src/shiraz/host_layout.py $(MACOS_ART_HOST)/bin/dx
	python3 scripts/build_macos_fixture.py --fixture gles
macos-apk-gles-fixture: build/macos-apk/gles.apk
MACOS_RUNTIME_GLES_SMOKE_DEPS := macos-runtime-libs $(addprefix $(MACOS_OUT)/,$(MACOS_GLES_LIBRARIES)) macos-framework macos-framework-resources macos-apk-gles-fixture
macos-runtime-gles-smoke: $(MACOS_RUNTIME_GLES_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile gles
macos-runtime-gles-window-smoke: $(MACOS_RUNTIME_GLES_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile gles --windowed --snapshot $(MACOS_OUT)/runtime-gles-window-smoke/frame.ppm
# Two ES 2 GLSurfaceViews (SHIRAZ_TEST_GLES_LOWER=es2) render on two GL
# threads at once; both must report MACOS_GLES2_FRAME_OK and both rectangles
# must hold the triangle. Two ANGLE Metal contexts of one share group
# deadlocked here (build/macos/m4/race), and the daemon GL threads let the run
# exit 0; a GL thread that fails or does not stop now fails the run
# (android/opengl/GLSurfaceView.java), which the smoke reports.
macos-runtime-gles-es2-pair-smoke: $(MACOS_RUNTIME_GLES_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile gles --gles-lower es2 --timeout 60 --snapshot $(MACOS_OUT)/runtime-gles-es2-pair-smoke/frame.ppm
macos-runtime-gles-es2-pair-window-smoke: $(MACOS_RUNTIME_GLES_SMOKE_DEPS)
	python3 scripts/macos_runtime_smoke.py --profile gles --gles-lower es2 --timeout 60 --windowed --snapshot $(MACOS_OUT)/runtime-gles-es2-pair-window-smoke/frame.ppm

# Host-JVM ports of the Linux OpenGL ES smokes (Makefile gles20-smoke,
# gles30-smoke, gles-window-smoke, egl-surface-resize-smoke, pixel-copy-smoke,
# gles-probe-smoke, gpu-canvas-smoke), run on the OpenJDK that MACOS_JNI_CFLAGS
# compiles against. The Java tests and tests/native/egl_surface_resize.cpp are
# the Linux files unchanged; gles-probe and gpu-canvas have macOS ports
# (tests/darwin/gles/gles_probe.cpp, scripts/macos_gpu_canvas_smoke.py) that
# say which setup steps differ and assert those differences.
# OpenJDK maps System.loadLibrary("x") to libx.dylib, so MACOS_JVM_NATIVE holds
# lib*.dylib symlinks to the build/macos lib*.so files (dyld resolves the
# libraries' @loader_path and from_surface.cpp's @loader_path/libEGL_shim.so
# through the symlink to build/macos). EGL reaches ANGLE through
# SHIRAZ_EGL_LIBRARY, as in `shiraz run`. No DYLD_* or LD_* variables are set.
MACOS_JAVA := $(MACOS_JAVA_HOME)/bin/java
MACOS_JAVAC := $(MACOS_JAVA_HOME)/bin/javac
MACOS_JVM_NATIVE := $(MACOS_OUT)/jvm-native
MACOS_JVM_LIBRARIES := $(addprefix $(MACOS_OUT)/,$(MACOS_LIBRARIES) $(MACOS_GRAPHICS_LIBRARIES))
MACOS_JVM_LINKS = mkdir -p $(MACOS_JVM_NATIVE) && for library in $(MACOS_OUT)/lib*.so; do name=$$(basename $$library .so); ln -sfn ../$$name.so $(MACOS_JVM_NATIVE)/$$name.dylib; done
MACOS_JVM_ENV := SHIRAZ_EGL_LIBRARY=$(CURDIR)/$(MACOS_OUT)/libEGL.dylib
MACOS_JVM = $(MACOS_JVM_ENV) $(MACOS_JAVA) --enable-native-access=ALL-UNNAMED -Xcheck:jni -Djava.library.path=$(CURDIR)/$(MACOS_JVM_NATIVE)
# $(1) class directory, $(2) Java sources.
MACOS_JVM_COMPILE = mkdir -p $(1) && $(MACOS_JAVAC) --release 8 -Xlint:-options -cp build/framework/classes -d $(1) $(2)
.PHONY: macos-jvm-native macos-gles20-smoke macos-gles30-smoke macos-gles-window-smoke macos-egl-surface-resize-smoke macos-pixel-copy-smoke macos-gles-probe-smoke macos-gpu-canvas-smoke macos-gles-jvm-smokes
macos-jvm-native: $(MACOS_JVM_LIBRARIES)
	@$(MACOS_JVM_LINKS)
MACOS_GLES_JVM_DEPS := macos-framework macos-jvm-native
macos-gles20-smoke: $(MACOS_GLES_JVM_DEPS)
	$(call MACOS_JVM_COMPILE,$(MACOS_OUT)/gles20-smoke,tests/java/GLES20Smoke.java)
	$(MACOS_JVM) -cp $(MACOS_OUT)/gles20-smoke:build/framework/classes GLES20Smoke
macos-gles30-smoke: $(MACOS_GLES_JVM_DEPS)
	$(call MACOS_JVM_COMPILE,$(MACOS_OUT)/gles30-smoke,tests/java/GLES30Smoke.java)
	$(MACOS_JVM) -cp $(MACOS_OUT)/gles30-smoke:build/framework/classes GLES30Smoke
# Linux preloads libGLESv2 so that its glBindFramebuffer is in the global scope
# before the shim's, the worst case for the shim's window-framebuffer routing.
# Here tests/darwin/gles/PreloadMain.java System.loads ANGLE's libGLESv2.dylib
# first (dlopen, global) in the same JVM. Two-level namespace already binds
# libshiraz-gles2's shiraz_egl_bind_framebuffer to libEGL_shim at link time
# (MACOS_GLES2_BIND_CHECK); the preload covers the shim's own
# dlsym(RTLD_NEXT, "glBindFramebuffer") with libGLESv2 loaded first.
macos-gles-window-smoke: $(MACOS_GLES_JVM_DEPS)
	$(call MACOS_JVM_COMPILE,$(MACOS_OUT)/gles-window-smoke,tests/java/GlesWindowFramebufferSmoke.java tests/darwin/gles/PreloadMain.java)
	$(MACOS_JVM) -cp $(MACOS_OUT)/gles-window-smoke:build/framework/classes PreloadMain $(CURDIR)/$(MACOS_OUT)/libGLESv2.dylib GlesWindowFramebufferSmoke > $(MACOS_OUT)/gles-window-smoke/stdout.log; status=$$?; cat $(MACOS_OUT)/gles-window-smoke/stdout.log; test $$status -eq 0
	grep -Fxq 'SHIRAZ_PRELOADED $(CURDIR)/$(MACOS_OUT)/libGLESv2.dylib' $(MACOS_OUT)/gles-window-smoke/stdout.log
# The test library dlopens "libEGL_shiraz.so.1" by leaf name to reach the shim
# the framework already loaded. dyld searches a leaf name relative to the
# current directory (this OpenJDK process did not honour LD_LIBRARY_PATH), so
# the JVM runs in a directory holding that alias; dlopen then returns the
# already loaded build/macos/libEGL_shim.so image.
MACOS_EGL_RESIZE := $(MACOS_OUT)/egl-surface-resize-smoke
$(MACOS_OUT)/libshiraz-egl-resize-test.so: tests/native/egl_surface_resize.cpp $(MACOS_ANGLE_STAMP) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_ANGLE_CFLAGS) -fPIC $(MACOS_DYLIB) tests/native/egl_surface_resize.cpp -o $@
macos-egl-surface-resize-smoke: $(MACOS_GLES_JVM_DEPS) $(MACOS_OUT)/libshiraz-egl-resize-test.so
	@$(MACOS_JVM_LINKS)
	$(call MACOS_JVM_COMPILE,$(MACOS_EGL_RESIZE),tests/java/EglSurfaceResizeSmoke.java)
	ln -sfn ../libEGL_shim.so $(MACOS_EGL_RESIZE)/libEGL_shiraz.so.1
	cd $(MACOS_EGL_RESIZE) && $(MACOS_JVM) -cp $(CURDIR)/$(MACOS_EGL_RESIZE):$(CURDIR)/build/framework/classes EglSurfaceResizeSmoke
macos-pixel-copy-smoke: $(MACOS_GLES_JVM_DEPS)
	$(call MACOS_JVM_COMPILE,$(MACOS_OUT)/pixel-copy-smoke,tests/java/PixelCopySmoke.java)
	$(MACOS_JVM) -cp $(MACOS_OUT)/pixel-copy-smoke:build/framework/classes PixelCopySmoke
# Linked like libshiraz-host (native/gles_version.cpp against libEGL_shim).
$(MACOS_OUT)/test-gles-probe: tests/darwin/gles/gles_probe.cpp native/gles_version.cpp $(MACOS_OUT)/libEGL_shim.so $(MACOS_ANGLE_STAMP)
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) $(MACOS_ANGLE_CFLAGS) tests/darwin/gles/gles_probe.cpp native/gles_version.cpp -o $@ $(MACOS_OUT)/libEGL_shim.so $(MACOS_RPATH)
macos-gles-probe-smoke: $(MACOS_OUT)/test-gles-probe $(addprefix $(MACOS_OUT)/,$(MACOS_ANGLE_LIBRARIES))
	$(MACOS_JVM_ENV) ./$(MACOS_OUT)/test-gles-probe
# Opens Cocoa windows (gpu, forced and metal cases) on both OpenJDK and ART.
macos-gpu-canvas-smoke: macos-runtime-libs macos-framework
	python3 scripts/macos_gpu_canvas_smoke.py
macos-gles-jvm-smokes: macos-gles20-smoke macos-gles30-smoke macos-gles-window-smoke macos-egl-surface-resize-smoke macos-pixel-copy-smoke macos-gles-probe-smoke

# M5 phase 0: synthetic host memory probe, independent of ART and APKs.
.PHONY: macos-guest-memory-check
$(MACOS_OUT)/m5/guest-memory: tests/darwin/guest/memory.c
	mkdir -p $(dir $@)
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror $< -o $@

macos-guest-memory-check: $(MACOS_OUT)/m5/guest-memory tests/darwin/guest/jit.entitlements
	$(MACOS_OUT)/m5/guest-memory
	cp $(MACOS_OUT)/m5/guest-memory $(MACOS_OUT)/m5/guest-memory-hardened.pending
	mv $(MACOS_OUT)/m5/guest-memory-hardened.pending $(MACOS_OUT)/m5/guest-memory-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/guest-memory-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/guest-memory-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/guest-memory-hardened
	$(MACOS_OUT)/m5/guest-memory-hardened

.PHONY: macos-guest-toolchain
macos-guest-toolchain:
	uv run --no-project python scripts/bootstrap_guest_toolchain.py

.PHONY: macos-guest-fixture
$(MACOS_OUT)/m5/fixture.o: tests/darwin/guest/fixture.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
macos-guest-fixture: macos-guest-toolchain $(MACOS_OUT)/m5/fixture.o
	.deps/guest-toolchain/bin/ld.lld -shared --hash-style=both --no-undefined -z now -z max-page-size=16384 -soname libshiraz_fixture.so $(MACOS_OUT)/m5/fixture.o -o $(MACOS_OUT)/m5/fixture.so

MACOS_GUEST_CPPFLAGS := -Iinclude -I$(MACOS_OUT)/m5 -I.deps/capstone/src/include
MACOS_GUEST_SOURCES := native/darwin/guest/providers.cpp native/darwin/guest/lifecycle.cpp native/darwin/guest/jump.cpp native/darwin/guest/jump.S native/darwin/guest/dynamic.cpp native/darwin/guest/tls.cpp native/darwin/guest/instruction.cpp native/darwin/guest/decode.cpp native/darwin/guest/unwind.cpp native/darwin/guest/unwind_runtime.cpp native/darwin/guest/tpidr.S native/darwin/guest/loader.cpp native/darwin/guest/jit.cpp native/darwin/guest/elf.cpp native/darwin/guest/image.cpp native/darwin/guest/call.cpp native/darwin/guest/va_list.cpp native/darwin/guest/closure.cpp native/darwin/guest/invoke.S .deps/capstone/build/libcapstone.a
MACOS_GUEST_HEADERS := $(MACOS_OUT)/m5/guest_tls_layout.hpp include/shiraz/darwin/guest/unwind.hpp include/shiraz/darwin/guest/unwind_runtime.hpp include/shiraz/darwin/guest/decode.hpp include/shiraz/darwin/guest/instruction.hpp include/shiraz/darwin/guest/tls.hpp include/shiraz/darwin/guest/providers.hpp include/shiraz/darwin/guest/lifecycle.hpp include/shiraz/darwin/guest/jump.hpp $(MACOS_OUT)/m5/guest_errno.hpp include/shiraz/darwin/guest/loader.hpp include/shiraz/darwin/guest/dynamic.hpp include/shiraz/darwin/guest/jit.hpp include/shiraz/darwin/guest/elf.hpp include/shiraz/darwin/guest/image.hpp include/shiraz/darwin/guest/call.hpp include/shiraz/darwin/guest/va_list.hpp include/shiraz/darwin/guest/closure.hpp include/shiraz/darwin/guest/boundary_token.hpp $(MACOS_OUT)/m5/guest_io.hpp
$(MACOS_OUT)/m5/elf-probe: tests/darwin/guest/elf_probe.cpp tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/elf_probe.cpp tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-elf-check
macos-guest-elf-check: macos-guest-fixture $(MACOS_OUT)/m5/elf-probe
	xcrun clang --version
	shasum -a 256 $(MACOS_OUT)/m5/fixture.so
	$(MACOS_OUT)/m5/elf-probe $(MACOS_OUT)/m5/fixture.so
	cp $(MACOS_OUT)/m5/elf-probe $(MACOS_OUT)/m5/elf-probe-hardened.pending
	mv $(MACOS_OUT)/m5/elf-probe-hardened.pending $(MACOS_OUT)/m5/elf-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/elf-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/elf-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/elf-probe-hardened
	$(MACOS_OUT)/m5/elf-probe-hardened $(MACOS_OUT)/m5/fixture.so

$(MACOS_OUT)/m5/elf-negative: tests/darwin/guest/elf_negative.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/elf_negative.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-parser-check
macos-guest-parser-check: macos-guest-fixture $(MACOS_OUT)/m5/elf-negative
	$(MACOS_OUT)/m5/elf-negative $(MACOS_OUT)/m5/fixture.so

.PHONY: macos-guest-check
macos-guest-check: macos-guest-memory-check macos-guest-elf-check macos-guest-parser-check macos-guest-dependency-check macos-guest-dependency-sanitize-check macos-guest-personality-check

# M5 JNI registration metadata is generated from this ART tree, never guessed.
MACOS_GUEST_ART := context/art_standalone
MACOS_GUEST_JNI_FLAGS := -I$(MACOS_GUEST_ART)/libnativehelper/include -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -I$(MACOS_GUEST_ART)/system/core/libnativebridge/include
$(MACOS_OUT)/m5/guest_jni_slots.inc: scripts/generate_guest_jni.py $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h
	uv run --no-project python scripts/generate_guest_jni.py $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h $@
MACOS_GUEST_BRIDGE_LINK = $(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) $(MACOS_GUEST_JNI_FLAGS) $(MACOS_ANGLE_CFLAGS) -isystem third_party/opensles -std=c++17 -O2 -g -Wall -Wextra -Werror -fvisibility=hidden -dynamiclib -Wl,-install_name,@rpath/libshiraz-guest.so $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_IO_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) native/darwin/guest/binding_evidence.cpp native/darwin/guest/jni.cpp native/darwin/guest/bridge.cpp native/darwin/guest/bitmap.cpp native/android_bitmap.cpp native/darwin/guest/guestegl.cpp native/darwin/guest/path.cpp native/darwin/guest/opensles.cpp -framework AudioToolbox
$(MACOS_OUT)/libshiraz-guest.so: native/darwin/guest/opensles.cpp native/opensles.cpp include/shiraz/darwin/guest/opensles.hpp native/darwin/guest/binding_evidence.cpp include/shiraz/darwin/guest/binding_evidence.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS) native/darwin/guest/jni.cpp native/darwin/guest/bridge.cpp include/shiraz/darwin/guest/jni.hpp $(MACOS_OUT)/m5/guest_jni_slots.inc native/darwin/guest/guestegl.cpp include/shiraz/darwin/guest/guestegl.hpp include/shiraz/darwin/guest/guestegl_symbols.inc native/darwin/guest/path.cpp include/shiraz/darwin/guest/path.hpp
	$(MACOS_GUEST_BRIDGE_LINK) -o $@

$(MACOS_OUT)/m5/jni-fixture.o: tests/darwin/guest/jni_fixture.c $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -funwind-tables -fstack-protector-all -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-jni-fixture macos-guest-jni-check
macos-guest-jni-fixture: macos-guest-toolchain macos-guest-emutls-source $(MACOS_OUT)/m5/deps/emutls.o $(MACOS_OUT)/m5/jni-fixture.o $(MACOS_OUT)/m5/deps/libc-link.so $(MACOS_OUT)/m5/deps/libjnigraphics-link.so
	.deps/guest-toolchain/bin/ld.lld -shared --hash-style=both --eh-frame-hdr --no-undefined -z now -z max-page-size=16384 -soname libshiraz_jni_fixture.so $(MACOS_OUT)/m5/jni-fixture.o $(MACOS_GUEST_DEP_DIR)/emutls.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -l:libjnigraphics-link.so -o $(MACOS_OUT)/m5/jni-fixture.so
$(MACOS_OUT)/m5/native-closure-inventory.o: tests/darwin/guest/native_closure_inventory.c $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h
	mkdir -p $(dir $@)
	xcrun clang -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -std=c11 -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_OUT)/m5/host-fixture.so: tests/darwin/guest/host_fixture.c $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h $(MACOS_OUT)/m5/native-closure-inventory.o
	mkdir -p $(dir $@)
	xcrun clang -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -dynamiclib -O2 -Wall -Wextra -Werror $< $(MACOS_OUT)/m5/native-closure-inventory.o -o $@
macos-guest-jni-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py

.PHONY: macos-guest-jni-signal-check
macos-guest-jni-signal-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so macos-guest-io-abi-check
	uv run --no-project python scripts/macos_guest_jni_smoke.py --signal-chain-check

$(MACOS_OUT)/m5/signal-lifetime-probe: tests/darwin/guest/signal_lifetime_probe.cpp
	mkdir -p $(dir $@)
	$(CXX) -I$(MACOS_GUEST_ART)/system/core/libnativebridge/include -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -I$(MACOS_GUEST_ART)/art/sigchainlib -std=c++17 -O2 -g -Wall -Wextra -Werror $< -Wl,-headerpad_max_install_names -o $@
.PHONY: macos-guest-signal-lifetime-check
macos-guest-signal-lifetime-check: $(MACOS_OUT)/m5/signal-lifetime-probe $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_signal_lifetime.py

# Dedicated known-failing lifecycle regression; ordinary JNI success does not
# satisfy this final-native-TSD-round cleanup gate.
.PHONY: macos-guest-jni-late-tls-check
macos-guest-jni-late-tls-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py --late-tls

.PHONY: macos-guest-jni-late-tls-refusal-check
macos-guest-jni-late-tls-refusal-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py --expect-late-tls-refusal

.PHONY: macos-guest-jni-adhoc-check
macos-guest-jni-adhoc-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py --mode adhoc

# Separate closure route check; this is not a hardened ART acceptance gate.
.PHONY: macos-guest-jni-staged-adhoc-check
macos-guest-jni-staged-adhoc-check: macos-guest-jni-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py --mode staged-adhoc

MACOS_GUEST_DEP_DIR := $(MACOS_OUT)/m5/deps
MACOS_GUEST_LD := .deps/guest-toolchain/bin/ld.lld
MACOS_GUEST_LD_FLAGS := -shared --hash-style=both -z now -z max-page-size=16384
$(MACOS_GUEST_DEP_DIR)/%.o: tests/darwin/guest/deps/%.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-dependency-fixtures
macos-guest-dependency-fixtures: macos-guest-toolchain $(addprefix $(MACOS_GUEST_DEP_DIR)/,leaf.o left.o right.o root.o global.o missing.o cycle-a.o cycle-b.o cycle-stub.o host-escape.o)
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/leaf.map -soname libleaf.so $(MACOS_GUEST_DEP_DIR)/leaf.o -o $(MACOS_GUEST_DEP_DIR)/libleaf.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libleft.so $(MACOS_GUEST_DEP_DIR)/left.o -L$(MACOS_GUEST_DEP_DIR) -lleaf -o $(MACOS_GUEST_DEP_DIR)/libleft.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libright.so $(MACOS_GUEST_DEP_DIR)/right.o -o $(MACOS_GUEST_DEP_DIR)/libright.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libroot.so $(MACOS_GUEST_DEP_DIR)/root.o -L$(MACOS_GUEST_DEP_DIR) -lleft -lright -o $(MACOS_GUEST_DEP_DIR)/libroot.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -z global -soname libglobal.so $(MACOS_GUEST_DEP_DIR)/global.o -o $(MACOS_GUEST_DEP_DIR)/libglobal.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) -soname libmissing.so $(MACOS_GUEST_DEP_DIR)/missing.o -L$(MACOS_GUEST_DEP_DIR) -lleft -o $(MACOS_GUEST_DEP_DIR)/libmissing.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) -soname libcycle-a.so $(MACOS_GUEST_DEP_DIR)/cycle-stub.o -o $(MACOS_GUEST_DEP_DIR)/libcycle-a.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libcycle-b.so $(MACOS_GUEST_DEP_DIR)/cycle-b.o -L$(MACOS_GUEST_DEP_DIR) -lcycle-a -o $(MACOS_GUEST_DEP_DIR)/libcycle-b.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libcycle-a.so $(MACOS_GUEST_DEP_DIR)/cycle-a.o -L$(MACOS_GUEST_DEP_DIR) -lcycle-b -o $(MACOS_GUEST_DEP_DIR)/libcycle-a.so
	xcrun clang --target=aarch64-linux-android24 -DNO_OLD_VERSION -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c tests/darwin/guest/deps/leaf.c -o $(MACOS_GUEST_DEP_DIR)/leaf-bad.o
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/leaf-bad.map -soname libleaf.so $(MACOS_GUEST_DEP_DIR)/leaf-bad.o -o $(MACOS_GUEST_DEP_DIR)/leaf-bad.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) -soname libhost-escape.so $(MACOS_GUEST_DEP_DIR)/host-escape.o -o $(MACOS_GUEST_DEP_DIR)/libhost-escape.so

$(MACOS_OUT)/m5/dependency-probe: tests/darwin/guest/dependency_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/dependency_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-dependency-check
macos-guest-dependency-check: macos-guest-dependency-fixtures $(MACOS_OUT)/m5/dependency-probe
	$(MACOS_OUT)/m5/dependency-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/dependency-probe $(MACOS_OUT)/m5/dependency-probe-hardened.pending
	mv $(MACOS_OUT)/m5/dependency-probe-hardened.pending $(MACOS_OUT)/m5/dependency-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/dependency-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/dependency-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/dependency-probe-hardened
	$(MACOS_OUT)/m5/dependency-probe-hardened $(MACOS_GUEST_DEP_DIR)

$(MACOS_OUT)/m5/guest_errno.hpp: scripts/generate_guest_errno.py scripts/bootstrap_bionic_headers.py
	uv run --no-project python scripts/generate_guest_errno.py

$(MACOS_GUEST_DEP_DIR)/libc-link.so: $(MACOS_GUEST_DEP_DIR)/link-libc.o tests/darwin/guest/deps/libc.map | macos-guest-toolchain
$(MACOS_GUEST_DEP_DIR)/libfixture_host.so: $(MACOS_GUEST_DEP_DIR)/link-host.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libfixture_host.so $< -o $@
$(MACOS_GUEST_DEP_DIR)/personality.o: tests/darwin/guest/deps/personality.c $(MACOS_OUT)/m5/guest_errno.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-personality-fixture macos-guest-personality-check
macos-guest-personality-fixture: $(MACOS_GUEST_DEP_DIR)/personality.o $(MACOS_GUEST_DEP_DIR)/libc-link.so $(MACOS_GUEST_DEP_DIR)/libfixture_host.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libpersonality.so $(MACOS_GUEST_DEP_DIR)/personality.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -lfixture_host -o $(MACOS_GUEST_DEP_DIR)/libpersonality.so
$(MACOS_OUT)/m5/personality-probe: tests/darwin/guest/personality_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/personality_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-personality-check: macos-guest-personality-fixture $(MACOS_OUT)/m5/personality-probe
	$(MACOS_OUT)/m5/personality-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/personality-probe $(MACOS_OUT)/m5/personality-probe-hardened.pending
	mv $(MACOS_OUT)/m5/personality-probe-hardened.pending $(MACOS_OUT)/m5/personality-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/personality-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/personality-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/personality-probe-hardened
	$(MACOS_OUT)/m5/personality-probe-hardened $(MACOS_GUEST_DEP_DIR)

$(MACOS_OUT)/m5/personality-sanitized: tests/darwin/guest/personality_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/personality_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-personality-sanitize-check
macos-guest-personality-sanitize-check: macos-guest-personality-fixture $(MACOS_OUT)/m5/personality-sanitized
	$(MACOS_OUT)/m5/personality-sanitized $(MACOS_GUEST_DEP_DIR)

# Instrument the exact production assembly entry with a raw body shim. Only
# this private providers object renames its C++ body; runtime objects do not.
$(MACOS_OUT)/m5/malloc-quarantine-providers.o: native/darwin/guest/providers.cpp $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -Dshiraz_guest_managed_malloc_body=shiraz_test_actual_managed_malloc_body -c $< -o $@
$(MACOS_OUT)/m5/malloc-quarantine-probe: tests/darwin/guest/malloc_quarantine_probe.cpp tests/darwin/guest/malloc_quarantine_entry.S $(MACOS_OUT)/m5/malloc-quarantine-providers.o $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/malloc_quarantine_probe.cpp tests/darwin/guest/malloc_quarantine_entry.S $(MACOS_OUT)/m5/malloc-quarantine-providers.o $(filter-out native/darwin/guest/providers.cpp,$(MACOS_GUEST_SOURCES)) -o $@
.PHONY: macos-guest-malloc-quarantine-check
macos-guest-malloc-quarantine-check: $(MACOS_OUT)/m5/malloc-quarantine-probe
	$<
	cp $< $(MACOS_OUT)/m5/malloc-quarantine-hardened.pending
	mv $(MACOS_OUT)/m5/malloc-quarantine-hardened.pending $(MACOS_OUT)/m5/malloc-quarantine-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/malloc-quarantine-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/malloc-quarantine-hardened
	$(MACOS_OUT)/m5/malloc-quarantine-hardened
$(MACOS_OUT)/m5/malloc-quarantine-providers-sanitized.o: native/darwin/guest/providers.cpp $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -Dshiraz_guest_managed_malloc_body=shiraz_test_actual_managed_malloc_body -c $< -o $@
$(MACOS_OUT)/m5/malloc-quarantine-sanitized: tests/darwin/guest/malloc_quarantine_probe.cpp tests/darwin/guest/malloc_quarantine_entry.S $(MACOS_OUT)/m5/malloc-quarantine-providers-sanitized.o $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/malloc_quarantine_probe.cpp tests/darwin/guest/malloc_quarantine_entry.S $(MACOS_OUT)/m5/malloc-quarantine-providers-sanitized.o $(filter-out native/darwin/guest/providers.cpp,$(MACOS_GUEST_SOURCES)) -o $@
.PHONY: macos-guest-malloc-quarantine-sanitize-check
macos-guest-malloc-quarantine-sanitize-check: $(MACOS_OUT)/m5/malloc-quarantine-sanitized
	$<
macos-guest-check: macos-guest-malloc-quarantine-check macos-guest-malloc-quarantine-sanitize-check

$(MACOS_OUT)/m5/dependency-sanitized: tests/darwin/guest/dependency_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/dependency_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-dependency-sanitize-check
macos-guest-dependency-sanitize-check: macos-guest-dependency-fixtures $(MACOS_OUT)/m5/dependency-sanitized
	$(MACOS_OUT)/m5/dependency-sanitized $(MACOS_GUEST_DEP_DIR)

.PHONY: macos-guest-errno-check
macos-guest-errno-check:
	uv run --no-project python scripts/generate_guest_errno.py
macos-guest-check macos-guest-jni-check macos-guest-jni-adhoc-check: macos-guest-errno-check

$(MACOS_OUT)/m5/guest_io.hpp: scripts/generate_guest_io.py scripts/generate_guest_errno.py scripts/bootstrap_bionic_headers.py
	uv run --no-project python scripts/generate_guest_io.py

MACOS_GUEST_ICU_PREFIX := $(CURDIR)/.deps/guest-icu-static/install
MACOS_GUEST_ICU_ARCHIVES := $(MACOS_GUEST_ICU_PREFIX)/lib/libicuuc.a $(MACOS_GUEST_ICU_PREFIX)/lib/libicudata.a
MACOS_GUEST_ICU_CFLAGS := -I$(MACOS_GUEST_ICU_PREFIX)/include -DU_STATIC_IMPLEMENTATION
MACOS_GUEST_ICU_LIBS := $(MACOS_GUEST_ICU_ARCHIVES)

MACOS_GUEST_IO_SOURCES := native/darwin/guest/io.cpp native/darwin/guest/memory.cpp native/darwin/guest/math.cpp native/darwin/guest/stdio.cpp native/darwin/guest/format.cpp native/darwin/guest/log.cpp native/darwin/guest/properties.cpp native/darwin/guest/libc_scalar.cpp native/darwin/guest/quad.cpp native/darwin/guest/transfer.cpp native/darwin/guest/wideformat.cpp native/darwin/guest/core_services.cpp native/darwin/guest/accounts.cpp native/darwin/guest/cleanup.cpp native/darwin/guest/fs.cpp native/darwin/guest/fonts.cpp native/darwin/guest/events.cpp native/darwin/guest/networking.cpp native/darwin/guest/signals.cpp native/darwin/guest/signals.S native/darwin/guest/fortify.cpp native/darwin/guest/cxx.cpp native/darwin/guest/locale.cpp native/darwin/guest/multibyte.cpp native/darwin/guest/time.cpp native/darwin/guest/system.cpp native/darwin/guest/scan.cpp native/darwin/guest/zlib.cpp native/darwin/guest/thread.cpp native/darwin/guest/sync.cpp native/darwin/guest/semaphore.cpp native/darwin/guest/process.cpp
MACOS_GUEST_IO_HEADERS := $(MACOS_GUEST_ICU_ARCHIVES) include/shiraz/darwin/guest/io.hpp include/shiraz/darwin/guest/memory.hpp include/shiraz/darwin/guest/math.hpp include/shiraz/darwin/guest/stdio.hpp include/shiraz/darwin/guest/format.hpp include/shiraz/darwin/guest/log.hpp include/shiraz/darwin/guest/properties.hpp include/shiraz/darwin/guest/libc_scalar.hpp include/shiraz/darwin/guest/quad.hpp include/shiraz/darwin/guest/transfer.hpp include/shiraz/darwin/guest/wideformat.hpp include/shiraz/darwin/guest/core_services.hpp include/shiraz/darwin/guest/accounts.hpp include/shiraz/darwin/guest/cleanup.hpp include/shiraz/darwin/guest/fs.hpp include/shiraz/darwin/guest/fonts.hpp include/shiraz/darwin/guest/events.hpp include/shiraz/darwin/guest/networking.hpp include/shiraz/darwin/guest/signals.hpp include/shiraz/darwin/guest/fortify.hpp include/shiraz/darwin/guest/cxx.hpp include/shiraz/darwin/guest/locale.hpp include/shiraz/darwin/guest/multibyte.hpp include/shiraz/darwin/guest/time.hpp include/shiraz/darwin/guest/system.hpp include/shiraz/darwin/guest/scan.hpp include/shiraz/darwin/guest/zlib.hpp include/shiraz/darwin/guest/thread.hpp include/shiraz/darwin/guest/sync.hpp include/shiraz/darwin/guest/tls.hpp $(MACOS_OUT)/m5/guest_io.hpp include/shiraz/darwin/guest/process.hpp
$(MACOS_OUT)/m5/io.o: $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -c native/darwin/guest/io.cpp -o $@

$(MACOS_GUEST_DEP_DIR)/link-io.o: tests/darwin/guest/deps/link-io.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libc-link.so: $(MACOS_GUEST_DEP_DIR)/link-io.o $(MACOS_GUEST_DEP_DIR)/link-stdio.o $(MACOS_GUEST_DEP_DIR)/link-scalar.o $(MACOS_GUEST_DEP_DIR)/link-locale.o $(MACOS_GUEST_DEP_DIR)/link-process.o
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/libc.map -soname libc.so $(MACOS_GUEST_DEP_DIR)/link-libc.o $(MACOS_GUEST_DEP_DIR)/link-io.o $(MACOS_GUEST_DEP_DIR)/link-stdio.o $(MACOS_GUEST_DEP_DIR)/link-scalar.o $(MACOS_GUEST_DEP_DIR)/link-locale.o $(MACOS_GUEST_DEP_DIR)/link-process.o -o $@
$(MACOS_GUEST_DEP_DIR)/io.o: tests/darwin/guest/deps/io.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-io-fixture macos-guest-io-check
macos-guest-io-fixture: $(MACOS_GUEST_DEP_DIR)/io.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libio.so $(MACOS_GUEST_DEP_DIR)/io.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libio.so
$(MACOS_OUT)/m5/io-probe: tests/darwin/guest/io_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/io_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-io-check: macos-guest-io-fixture $(MACOS_OUT)/m5/io-probe
	$(MACOS_OUT)/m5/io-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/io-probe $(MACOS_OUT)/m5/io-probe-hardened.pending
	mv $(MACOS_OUT)/m5/io-probe-hardened.pending $(MACOS_OUT)/m5/io-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/io-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/io-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/io-probe-hardened
	$(MACOS_OUT)/m5/io-probe-hardened $(MACOS_GUEST_DEP_DIR)

$(MACOS_OUT)/libshiraz-guest.so: $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS)

$(MACOS_OUT)/m5/io-sanitized: tests/darwin/guest/io_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/io_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-io-sanitize-check macos-guest-io-abi-check
macos-guest-io-sanitize-check: macos-guest-io-fixture $(MACOS_OUT)/m5/io-sanitized
	$(MACOS_OUT)/m5/io-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-io-abi-check:
	uv run --no-project python scripts/generate_guest_io.py
macos-guest-check: macos-guest-io-check macos-guest-io-sanitize-check macos-guest-io-abi-check
macos-guest-jni-check macos-guest-jni-adhoc-check: macos-guest-io-abi-check

$(MACOS_GUEST_DEP_DIR)/memory.o: tests/darwin/guest/deps/memory.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-memory-api-fixture macos-guest-memory-api-check macos-guest-memory-api-sanitize-check
macos-guest-memory-api-fixture: $(MACOS_GUEST_DEP_DIR)/memory.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libmemory.so $(MACOS_GUEST_DEP_DIR)/memory.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libmemory.so
$(MACOS_OUT)/m5/memory-api-probe: tests/darwin/guest/memory_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/memory_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-memory-api-check: macos-guest-memory-api-fixture $(MACOS_OUT)/m5/memory-api-probe
	$(MACOS_OUT)/m5/memory-api-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/memory-api-probe $(MACOS_OUT)/m5/memory-api-probe-hardened.pending
	mv $(MACOS_OUT)/m5/memory-api-probe-hardened.pending $(MACOS_OUT)/m5/memory-api-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/memory-api-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/memory-api-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/memory-api-probe-hardened
	$(MACOS_OUT)/m5/memory-api-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/memory-api-sanitized: tests/darwin/guest/memory_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/memory_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-memory-api-sanitize-check: macos-guest-memory-api-fixture $(MACOS_OUT)/m5/memory-api-sanitized
	$(MACOS_OUT)/m5/memory-api-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-memory-api-check macos-guest-memory-api-sanitize-check

$(MACOS_GUEST_DEP_DIR)/thread.o: tests/darwin/guest/deps/thread.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-thread-fixture macos-guest-thread-check macos-guest-thread-sanitize-check
macos-guest-thread-fixture: $(MACOS_GUEST_DEP_DIR)/thread.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libthread.so $(MACOS_GUEST_DEP_DIR)/thread.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libthread.so
$(MACOS_OUT)/m5/thread-probe: tests/darwin/guest/thread_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/thread_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-thread-check: macos-guest-thread-fixture $(MACOS_OUT)/m5/thread-probe
	$(MACOS_OUT)/m5/thread-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/thread-probe $(MACOS_OUT)/m5/thread-probe-hardened.pending
	mv $(MACOS_OUT)/m5/thread-probe-hardened.pending $(MACOS_OUT)/m5/thread-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/thread-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/thread-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/thread-probe-hardened
	$(MACOS_OUT)/m5/thread-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/thread-sanitized: tests/darwin/guest/thread_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/thread_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-thread-sanitize-check: macos-guest-thread-fixture $(MACOS_OUT)/m5/thread-sanitized
	$(MACOS_OUT)/m5/thread-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-thread-check macos-guest-thread-sanitize-check

$(MACOS_GUEST_DEP_DIR)/sync.o: tests/darwin/guest/deps/sync.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-sync-fixture macos-guest-sync-check macos-guest-sync-sanitize-check
macos-guest-sync-fixture: $(MACOS_GUEST_DEP_DIR)/sync.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libsync.so $(MACOS_GUEST_DEP_DIR)/sync.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libsync.so
$(MACOS_OUT)/m5/sync-probe: tests/darwin/guest/sync_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/sync_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-sync-check: macos-guest-sync-fixture $(MACOS_OUT)/m5/sync-probe
	$(MACOS_OUT)/m5/sync-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/sync-probe $(MACOS_OUT)/m5/sync-probe-hardened.pending
	mv $(MACOS_OUT)/m5/sync-probe-hardened.pending $(MACOS_OUT)/m5/sync-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/sync-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/sync-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/sync-probe-hardened
	$(MACOS_OUT)/m5/sync-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/sync-sanitized: tests/darwin/guest/sync_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/sync_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-sync-sanitize-check: macos-guest-sync-fixture $(MACOS_OUT)/m5/sync-sanitized
	$(MACOS_OUT)/m5/sync-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-sync-check macos-guest-sync-sanitize-check

$(MACOS_GUEST_DEP_DIR)/rwsem.o: tests/darwin/guest/deps/rwsem.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-rwsem-fixture macos-guest-rwsem-check macos-guest-rwsem-sanitize-check
macos-guest-rwsem-fixture: $(MACOS_GUEST_DEP_DIR)/rwsem.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname librwsem.so $(MACOS_GUEST_DEP_DIR)/rwsem.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/librwsem.so
$(MACOS_OUT)/m5/rwsem-probe: tests/darwin/guest/rwsem_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/rwsem_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-rwsem-check: macos-guest-rwsem-fixture $(MACOS_OUT)/m5/rwsem-probe
	$(MACOS_OUT)/m5/rwsem-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/rwsem-probe $(MACOS_OUT)/m5/rwsem-probe-hardened.pending
	mv $(MACOS_OUT)/m5/rwsem-probe-hardened.pending $(MACOS_OUT)/m5/rwsem-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/rwsem-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/rwsem-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/rwsem-probe-hardened
	$(MACOS_OUT)/m5/rwsem-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/rwsem-sanitized: tests/darwin/guest/rwsem_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/rwsem_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-rwsem-sanitize-check: macos-guest-rwsem-fixture $(MACOS_OUT)/m5/rwsem-sanitized
	$(MACOS_OUT)/m5/rwsem-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-rwsem-check macos-guest-rwsem-sanitize-check

.PHONY: macos-guest-emutls-source macos-guest-tls-fixture macos-guest-tls-check macos-guest-tls-sanitize-check
macos-guest-emutls-source:
	uv run --no-project python scripts/bootstrap_guest_emutls.py
.deps/compiler-rt-emutls/emutls.c: scripts/bootstrap_guest_emutls.py
	uv run --no-project python scripts/bootstrap_guest_emutls.py
$(MACOS_GUEST_DEP_DIR)/emutls.o: .deps/compiler-rt-emutls/emutls.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/tls.o: tests/darwin/guest/deps/tls.cpp $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -mno-outline-atomics -femulated-tls -nostdinc++ -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-exceptions -fno-rtti -std=c++17 -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/tls-peer.o: tests/darwin/guest/deps/tls_peer.cpp $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -femulated-tls -ffreestanding -fPIC -fno-stack-protector -fno-exceptions -fno-rtti -std=c++17 -O2 -Wall -Wextra -Werror -c $< -o $@
macos-guest-tls-fixture: macos-guest-emutls-source $(MACOS_GUEST_DEP_DIR)/emutls.o $(MACOS_GUEST_DEP_DIR)/tls.o $(MACOS_GUEST_DEP_DIR)/tls-peer.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libemutls-fixture.so $(MACOS_GUEST_DEP_DIR)/emutls.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libemutls-fixture.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libtls-peer.so $(MACOS_GUEST_DEP_DIR)/tls-peer.o -L$(MACOS_GUEST_DEP_DIR) -lemutls-fixture -o $(MACOS_GUEST_DEP_DIR)/libtls-peer.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libtls-fixture.so $(MACOS_GUEST_DEP_DIR)/tls.o -L$(MACOS_GUEST_DEP_DIR) -ltls-peer -lemutls-fixture -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libtls-fixture.so
$(MACOS_OUT)/m5/tls-probe: tests/darwin/guest/tls_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/tls_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-tls-check: macos-guest-tls-fixture $(MACOS_OUT)/m5/tls-probe
	$(MACOS_OUT)/m5/tls-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/tls-probe $(MACOS_OUT)/m5/tls-probe-hardened.pending
	mv $(MACOS_OUT)/m5/tls-probe-hardened.pending $(MACOS_OUT)/m5/tls-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/tls-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/tls-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/tls-probe-hardened
	$(MACOS_OUT)/m5/tls-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/tls-sanitized: tests/darwin/guest/tls_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/tls_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-tls-sanitize-check: macos-guest-tls-fixture $(MACOS_OUT)/m5/tls-sanitized
	$(MACOS_OUT)/m5/tls-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-tls-check macos-guest-tls-sanitize-check

.PHONY: macos-guest-tpidr-thunk-check macos-guest-tpidr-thunk-sanitize-check
$(MACOS_OUT)/m5/tpidr-thunk-probe: tests/darwin/guest/tpidr_probe.cpp tests/darwin/guest/tpidr_probe.S tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/tpidr_probe.cpp tests/darwin/guest/tpidr_probe.S tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) -o $@
macos-guest-tpidr-thunk-check: $(MACOS_OUT)/m5/tpidr-thunk-probe
	$(MACOS_OUT)/m5/tpidr-thunk-probe
	cp $(MACOS_OUT)/m5/tpidr-thunk-probe $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened.pending
	mv $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened.pending $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/tpidr-thunk-probe-hardened
	$(MACOS_OUT)/m5/tpidr-thunk-probe-hardened
$(MACOS_OUT)/m5/tpidr-thunk-sanitized: tests/darwin/guest/tpidr_probe.cpp tests/darwin/guest/tpidr_probe.S tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/tpidr_probe.cpp tests/darwin/guest/tpidr_probe.S tests/darwin/guest/stack_entry.S $(MACOS_GUEST_SOURCES) -o $@
macos-guest-tpidr-thunk-sanitize-check: $(MACOS_OUT)/m5/tpidr-thunk-sanitized
	$(MACOS_OUT)/m5/tpidr-thunk-sanitized
macos-guest-check: macos-guest-tpidr-thunk-check macos-guest-tpidr-thunk-sanitize-check

.PHONY: macos-guest-decoder-check
.deps/capstone/build/libcapstone.a: scripts/bootstrap_guest_decoder.py
	uv run --no-project python scripts/bootstrap_guest_decoder.py
macos-guest-decoder-check:
	uv run --no-project python scripts/bootstrap_guest_decoder.py
macos-guest-check macos-guest-jni-check macos-guest-jni-adhoc-check: macos-guest-decoder-check

.PHONY: macos-guest-protected-fixture
$(MACOS_GUEST_DEP_DIR)/protected.o: tests/darwin/guest/deps/protected.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-inline -funwind-tables -fstack-protector-all -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/code-data.o: tests/darwin/guest/deps/code_data.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
macos-guest-protected-fixture: $(MACOS_GUEST_DEP_DIR)/protected.o $(MACOS_GUEST_DEP_DIR)/code-data.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --eh-frame-hdr -soname libprotected.so $(MACOS_GUEST_DEP_DIR)/protected.o $(MACOS_GUEST_DEP_DIR)/code-data.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libprotected.so
$(MACOS_OUT)/m5/protected-probe: tests/darwin/guest/protected_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/protected_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-protected-check
macos-guest-protected-check: macos-guest-protected-fixture $(MACOS_OUT)/m5/protected-probe
	$(MACOS_OUT)/m5/protected-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/protected-probe $(MACOS_OUT)/m5/protected-probe-hardened.pending
	mv $(MACOS_OUT)/m5/protected-probe-hardened.pending $(MACOS_OUT)/m5/protected-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/protected-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/protected-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/protected-probe-hardened
	$(MACOS_OUT)/m5/protected-probe-hardened $(MACOS_GUEST_DEP_DIR)
include mk/darwin-zero-copy.mk

$(MACOS_OUT)/m5/protected-sanitized: tests/darwin/guest/protected_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/protected_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-protected-sanitize-check
macos-guest-protected-sanitize-check: macos-guest-protected-fixture $(MACOS_OUT)/m5/protected-sanitized
	$(MACOS_OUT)/m5/protected-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-protected-check macos-guest-protected-sanitize-check

$(MACOS_OUT)/m5/math-probe: tests/darwin/guest/math_probe.cpp native/darwin/guest/math.cpp include/shiraz/darwin/guest/math.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/math_probe.cpp native/darwin/guest/math.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-math-check
macos-guest-math-check: $(MACOS_OUT)/m5/math-probe
	$(MACOS_OUT)/m5/math-probe
	cp $(MACOS_OUT)/m5/math-probe $(MACOS_OUT)/m5/math-probe-hardened.pending
	mv $(MACOS_OUT)/m5/math-probe-hardened.pending $(MACOS_OUT)/m5/math-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/math-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/math-probe-hardened
	$(MACOS_OUT)/m5/math-probe-hardened
macos-guest-check: macos-guest-math-check

$(MACOS_GUEST_DEP_DIR)/link-stdio.o: tests/darwin/guest/deps/link-stdio.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/stdio.o: tests/darwin/guest/deps/stdio.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-stdio-fixture macos-guest-stdio-check
macos-guest-stdio-fixture: $(MACOS_GUEST_DEP_DIR)/stdio.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libstdio.so $(MACOS_GUEST_DEP_DIR)/stdio.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libstdio.so
$(MACOS_OUT)/m5/stdio-probe: tests/darwin/guest/stdio_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/stdio_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-stdio-check: macos-guest-stdio-fixture $(MACOS_OUT)/m5/stdio-probe
	$(MACOS_OUT)/m5/stdio-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/stdio-probe $(MACOS_OUT)/m5/stdio-probe-hardened.pending
	mv $(MACOS_OUT)/m5/stdio-probe-hardened.pending $(MACOS_OUT)/m5/stdio-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/stdio-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/stdio-probe-hardened
	$(MACOS_OUT)/m5/stdio-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-stdio-check

$(MACOS_OUT)/m5/osmand-link-probe: native/darwin/guest/binding_evidence.cpp include/shiraz/darwin/guest/binding_evidence.hpp tests/darwin/guest/osmand_link_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS) native/darwin/guest/bitmap.cpp include/shiraz/darwin/guest/bitmap.hpp native/android_bitmap.cpp native/darwin/guest/guestegl.cpp include/shiraz/darwin/guest/guestegl.hpp include/shiraz/darwin/guest/guestegl_symbols.inc
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) $(MACOS_GUEST_JNI_FLAGS) $(MACOS_ANGLE_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/osmand_link_probe.cpp native/darwin/guest/binding_evidence.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) native/darwin/guest/bitmap.cpp native/android_bitmap.cpp native/darwin/guest/guestegl.cpp -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-osmand-link-check
macos-guest-osmand-link-check: $(MACOS_OUT)/m5/osmand-link-probe
	SHIRAZ_EGL_LIBRARY="$(CURDIR)/$(MACOS_OUT)/libEGL.dylib" SHIRAZ_GUEST_EGL_SHIM="$(CURDIR)/$(MACOS_OUT)/libEGL_shim.so" $(MACOS_OUT)/m5/osmand-link-probe $(MACOS_OUT)/m5/design/abi-table/libs/lib/arm64-v8a

$(MACOS_GUEST_DEP_DIR)/unwind.o: tests/darwin/guest/deps/unwind.cpp
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -ffreestanding -fPIC -funwind-tables -fno-stack-protector -fno-exceptions -fno-rtti -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-unwind-fixture macos-guest-unwind-check
$(MACOS_GUEST_DEP_DIR)/unwind-islands.o: tests/darwin/guest/deps/unwind_islands.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
macos-guest-unwind-fixture: macos-guest-toolchain $(MACOS_GUEST_DEP_DIR)/unwind.o $(MACOS_GUEST_DEP_DIR)/unwind-islands.o
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libunwind-fixture.so $(MACOS_GUEST_DEP_DIR)/unwind.o $(MACOS_GUEST_DEP_DIR)/unwind-islands.o -o $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
$(MACOS_OUT)/m5/unwind-probe: tests/darwin/guest/unwind_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/unwind_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-unwind-check: macos-guest-unwind-fixture $(MACOS_OUT)/m5/unwind-probe
	$(MACOS_OUT)/m5/unwind-probe $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
	cp $(MACOS_OUT)/m5/unwind-probe $(MACOS_OUT)/m5/unwind-probe-hardened.pending
	mv $(MACOS_OUT)/m5/unwind-probe-hardened.pending $(MACOS_OUT)/m5/unwind-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/unwind-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/unwind-probe-hardened
	$(MACOS_OUT)/m5/unwind-probe-hardened $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
macos-guest-check: macos-guest-unwind-check

$(MACOS_OUT)/m5/unwind-sanitized: tests/darwin/guest/unwind_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/unwind_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-unwind-sanitize-check
macos-guest-unwind-sanitize-check: macos-guest-unwind-fixture $(MACOS_OUT)/m5/unwind-sanitized
	$(MACOS_OUT)/m5/unwind-sanitized $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
macos-guest-check: macos-guest-unwind-sanitize-check

$(MACOS_GUEST_DEP_DIR)/link-log.o: tests/darwin/guest/deps/link-log.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/liblog-link.so: $(MACOS_GUEST_DEP_DIR)/link-log.o tests/darwin/guest/deps/log.map | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --version-script=tests/darwin/guest/deps/log.map -soname liblog.so $< -o $@
$(MACOS_GUEST_DEP_DIR)/log.o: tests/darwin/guest/deps/log.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-log-fixture macos-guest-log-check
macos-guest-log-fixture: $(MACOS_GUEST_DEP_DIR)/log.o $(MACOS_GUEST_DEP_DIR)/liblog-link.so $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname liblog-fixture.so $(MACOS_GUEST_DEP_DIR)/log.o -L$(MACOS_GUEST_DEP_DIR) -l:liblog-link.so -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/liblog-fixture.so
$(MACOS_OUT)/m5/log-probe: tests/darwin/guest/log_probe.cpp native/darwin/guest/log.cpp native/darwin/guest/format.cpp include/shiraz/darwin/guest/log.hpp include/shiraz/darwin/guest/format.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/log_probe.cpp native/darwin/guest/log.cpp native/darwin/guest/format.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-log-check: macos-guest-log-fixture $(MACOS_OUT)/m5/log-probe
	$(MACOS_OUT)/m5/log-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/log-probe $(MACOS_OUT)/m5/log-probe-hardened.pending
	mv $(MACOS_OUT)/m5/log-probe-hardened.pending $(MACOS_OUT)/m5/log-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/log-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/log-probe-hardened
	$(MACOS_OUT)/m5/log-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-log-check

$(MACOS_GUEST_DEP_DIR)/zlib-link.o: tests/darwin/guest/deps/zlib.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -DZLIB_LINK_ONLY -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libz-link.so: $(MACOS_GUEST_DEP_DIR)/zlib-link.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libz.so $< -o $@
$(MACOS_GUEST_DEP_DIR)/zlib.o: tests/darwin/guest/deps/zlib.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-zlib-fixture macos-guest-zlib-check
macos-guest-zlib-fixture: $(MACOS_GUEST_DEP_DIR)/zlib.o $(MACOS_GUEST_DEP_DIR)/libz-link.so $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libzlib-fixture.so $(MACOS_GUEST_DEP_DIR)/zlib.o -L$(MACOS_GUEST_DEP_DIR) -l:libz-link.so -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libzlib-fixture.so
$(MACOS_OUT)/m5/zlib-probe: tests/darwin/guest/zlib_probe.cpp native/darwin/guest/zlib.cpp include/shiraz/darwin/guest/zlib.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/zlib_probe.cpp native/darwin/guest/zlib.cpp $(MACOS_GUEST_SOURCES) -lz -o $@
macos-guest-zlib-check: macos-guest-zlib-fixture $(MACOS_OUT)/m5/zlib-probe
	$(MACOS_OUT)/m5/zlib-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/zlib-probe $(MACOS_OUT)/m5/zlib-probe-hardened.pending
	mv $(MACOS_OUT)/m5/zlib-probe-hardened.pending $(MACOS_OUT)/m5/zlib-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/zlib-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/zlib-probe-hardened
	$(MACOS_OUT)/m5/zlib-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-zlib-check

$(MACOS_GUEST_DEP_DIR)/link-dynamic.o: tests/darwin/guest/deps/link-dynamic.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libdl-link.so: $(MACOS_GUEST_DEP_DIR)/link-dynamic.o tests/darwin/guest/deps/dynamic.map | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/dynamic.map -soname libdl.so $< -o $@
$(MACOS_GUEST_DEP_DIR)/dynamic.o: tests/darwin/guest/deps/dynamic.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-dynamic-fixture macos-guest-dynamic-check
macos-guest-dynamic-fixture: $(MACOS_GUEST_DEP_DIR)/dynamic.o $(MACOS_GUEST_DEP_DIR)/libdl-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libdynamic.so $(MACOS_GUEST_DEP_DIR)/dynamic.o -L$(MACOS_GUEST_DEP_DIR) -l:libdl-link.so -o $(MACOS_GUEST_DEP_DIR)/libdynamic.so
$(MACOS_OUT)/m5/dynamic-probe: tests/darwin/guest/dynamic_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/dynamic_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-dynamic-check: macos-guest-dynamic-fixture $(MACOS_OUT)/m5/dynamic-probe
	$(MACOS_OUT)/m5/dynamic-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/dynamic-probe $(MACOS_OUT)/m5/dynamic-probe-hardened.pending
	mv $(MACOS_OUT)/m5/dynamic-probe-hardened.pending $(MACOS_OUT)/m5/dynamic-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/dynamic-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/dynamic-probe-hardened
	$(MACOS_OUT)/m5/dynamic-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-dynamic-check

$(MACOS_OUT)/m5/dynamic-sanitized: tests/darwin/guest/dynamic_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/dynamic_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/zlib-sanitized: tests/darwin/guest/zlib_probe.cpp native/darwin/guest/zlib.cpp include/shiraz/darwin/guest/zlib.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/zlib_probe.cpp native/darwin/guest/zlib.cpp $(MACOS_GUEST_SOURCES) -lz -o $@
$(MACOS_OUT)/m5/log-sanitized: tests/darwin/guest/log_probe.cpp native/darwin/guest/log.cpp native/darwin/guest/format.cpp include/shiraz/darwin/guest/log.hpp include/shiraz/darwin/guest/format.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/log_probe.cpp native/darwin/guest/log.cpp native/darwin/guest/format.cpp $(MACOS_GUEST_SOURCES) -o $@
$(MACOS_OUT)/m5/stdio-sanitized: tests/darwin/guest/stdio_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/stdio_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-services-sanitize-check
macos-guest-services-sanitize-check: macos-guest-dynamic-fixture macos-guest-zlib-fixture macos-guest-log-fixture macos-guest-stdio-fixture $(MACOS_OUT)/m5/dynamic-sanitized $(MACOS_OUT)/m5/zlib-sanitized $(MACOS_OUT)/m5/log-sanitized $(MACOS_OUT)/m5/stdio-sanitized
	$(MACOS_OUT)/m5/dynamic-sanitized $(MACOS_GUEST_DEP_DIR)
	$(MACOS_OUT)/m5/zlib-sanitized $(MACOS_GUEST_DEP_DIR)
	$(MACOS_OUT)/m5/log-sanitized $(MACOS_GUEST_DEP_DIR)
	$(MACOS_OUT)/m5/stdio-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-services-sanitize-check

# Service variables are declared below the NativeBridge rule; add their
# prerequisites after expansion so service edits rebuild the ART bridge too.
$(MACOS_OUT)/libshiraz-guest.so: $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS)

$(MACOS_OUT)/libshiraz-guest.so: native/darwin/guest/bitmap.cpp include/shiraz/darwin/guest/bitmap.hpp native/android_bitmap.cpp include/shiraz/android_bitmap.h
$(MACOS_OUT)/m5/properties-probe: tests/darwin/guest/properties_probe.cpp native/darwin/guest/properties.cpp include/shiraz/darwin/guest/properties.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/properties_probe.cpp native/darwin/guest/properties.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-properties-check
macos-guest-properties-check: $(MACOS_OUT)/m5/properties-probe
	$(MACOS_OUT)/m5/properties-probe
	cp $(MACOS_OUT)/m5/properties-probe $(MACOS_OUT)/m5/properties-probe-hardened.pending
	mv $(MACOS_OUT)/m5/properties-probe-hardened.pending $(MACOS_OUT)/m5/properties-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/properties-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/properties-probe-hardened
	$(MACOS_OUT)/m5/properties-probe-hardened
macos-guest-check: macos-guest-properties-check

$(MACOS_GUEST_DEP_DIR)/link-bitmap.o: tests/darwin/guest/deps/link-bitmap.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libjnigraphics-link.so: $(MACOS_GUEST_DEP_DIR)/link-bitmap.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libjnigraphics.so $< -o $@

$(MACOS_GUEST_DEP_DIR)/lifecycle.o: tests/darwin/guest/deps/lifecycle.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-lifecycle-fixture macos-guest-lifecycle-check
macos-guest-lifecycle-fixture: $(MACOS_GUEST_DEP_DIR)/lifecycle.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname liblifecycle.so $(MACOS_GUEST_DEP_DIR)/lifecycle.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/liblifecycle.so
$(MACOS_OUT)/m5/lifecycle-probe: tests/darwin/guest/lifecycle_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/lifecycle_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-lifecycle-check: macos-guest-lifecycle-fixture $(MACOS_OUT)/m5/lifecycle-probe
	$(MACOS_OUT)/m5/lifecycle-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/lifecycle-probe $(MACOS_OUT)/m5/lifecycle-probe-hardened.pending
	mv $(MACOS_OUT)/m5/lifecycle-probe-hardened.pending $(MACOS_OUT)/m5/lifecycle-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/lifecycle-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/lifecycle-probe-hardened
	$(MACOS_OUT)/m5/lifecycle-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-lifecycle-check

$(MACOS_GUEST_DEP_DIR)/format.o: tests/darwin/guest/deps/format.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-format-fixture macos-guest-format-check
macos-guest-format-fixture: $(MACOS_GUEST_DEP_DIR)/format.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libformat.so $(MACOS_GUEST_DEP_DIR)/format.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libformat.so
$(MACOS_OUT)/m5/format-probe: tests/darwin/guest/format_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/format_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-format-check: macos-guest-format-fixture $(MACOS_OUT)/m5/format-probe
	$(MACOS_OUT)/m5/format-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/format-probe $(MACOS_OUT)/m5/format-probe-hardened.pending
	mv $(MACOS_OUT)/m5/format-probe-hardened.pending $(MACOS_OUT)/m5/format-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/format-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/format-probe-hardened
	$(MACOS_OUT)/m5/format-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-format-check

$(MACOS_GUEST_DEP_DIR)/link-scalar.o: tests/darwin/guest/deps/link-scalar.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libc-scalar.o: tests/darwin/guest/deps/libc_scalar.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-libc-scalar-fixture macos-guest-libc-scalar-check
macos-guest-libc-scalar-fixture: $(MACOS_GUEST_DEP_DIR)/libc-scalar.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname liblibc-scalar.so $(MACOS_GUEST_DEP_DIR)/libc-scalar.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/liblibc-scalar.so
$(MACOS_OUT)/m5/libc-scalar-probe: tests/darwin/guest/libc_scalar_probe.cpp native/darwin/guest/libc_scalar.cpp include/shiraz/darwin/guest/libc_scalar.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/libc_scalar_probe.cpp native/darwin/guest/libc_scalar.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-libc-scalar-check: macos-guest-libc-scalar-fixture $(MACOS_OUT)/m5/libc-scalar-probe
	$(MACOS_OUT)/m5/libc-scalar-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/libc-scalar-probe $(MACOS_OUT)/m5/libc-scalar-probe-hardened.pending
	mv $(MACOS_OUT)/m5/libc-scalar-probe-hardened.pending $(MACOS_OUT)/m5/libc-scalar-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/libc-scalar-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/libc-scalar-probe-hardened
	$(MACOS_OUT)/m5/libc-scalar-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-libc-scalar-check

# NativeSound on macOS: real Ogg/CoreAudio decoding and AudioQueue output.
MACOS_AUDIO_SOURCES := native/audio.cpp native/darwin/audio_decode.cpp native/darwin/audio_output.cpp
MACOS_AUDIO_HEADERS := include/shiraz/audio.hpp include/shiraz/audio_output.hpp native/darwin/audio_decode.hpp
MACOS_AUDIO_CODEC := .deps/stb-audio/libstb_vorbis.a
MACOS_AUDIO_FRAMEWORKS := -framework AudioToolbox -framework CoreFoundation
.PHONY: macos-audio-deps macos-audio-check
macos-audio-deps:
	uv run --no-project python scripts/bootstrap_macos_audio.py
$(MACOS_AUDIO_CODEC): scripts/bootstrap_macos_audio.py
	uv run --no-project python scripts/bootstrap_macos_audio.py
$(MACOS_OUT)/libshiraz-audio.so: $(MACOS_AUDIO_SOURCES) $(MACOS_AUDIO_HEADERS) native/audio_jni.cpp $(MACOS_AUDIO_CODEC) $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -I.deps/stb-audio -fPIC $(MACOS_DYLIB) $(MACOS_AUDIO_SOURCES) native/audio_jni.cpp $(MACOS_AUDIO_CODEC) -o $@ $(MACOS_AUDIO_FRAMEWORKS) -pthread
$(MACOS_OUT)/test-audio: tests/audio.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_HEADERS) $(MACOS_AUDIO_CODEC) $(MACOS_OUT)/.dir
	$(MACOS_CXX) -I.deps/stb-audio tests/audio.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_CODEC) -o $@ $(MACOS_AUDIO_FRAMEWORKS) -pthread
$(MACOS_OUT)/test-audio-decode: tests/darwin/audio_decode.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_HEADERS) $(MACOS_AUDIO_CODEC) $(MACOS_OUT)/.dir
	$(MACOS_CXX) -I.deps/stb-audio tests/darwin/audio_decode.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_CODEC) -o $@ $(MACOS_AUDIO_FRAMEWORKS) -pthread
$(MACOS_OUT)/test-audio-output: tests/darwin/audio_output.cpp $(MACOS_AUDIO_SOURCES) $(MACOS_AUDIO_HEADERS) $(MACOS_AUDIO_CODEC) $(MACOS_OUT)/.dir
	$(MACOS_CXX) -I.deps/stb-audio tests/darwin/audio_output.cpp $(MACOS_AUDIO_SOURCES) $(MACOS_AUDIO_CODEC) -o $@ $(MACOS_AUDIO_FRAMEWORKS) -pthread
macos-audio-check: macos-audio-deps $(MACOS_OUT)/test-audio $(MACOS_OUT)/test-audio-decode $(MACOS_OUT)/test-audio-output
	$(MACOS_OUT)/test-audio
macos-runtime-libs: $(MACOS_OUT)/libshiraz-audio.so

.PHONY: macos-sensor-check
macos-sensor-check: macos-framework
	mkdir -p $(MACOS_OUT)/sensor-smoke/classes
	javac --release 8 -cp build/framework/classes -d $(MACOS_OUT)/sensor-smoke/classes tests/java/SensorDiscoverySmoke.java
	java -cp $(MACOS_OUT)/sensor-smoke/classes:build/framework/classes SensorDiscoverySmoke $(MACOS_OUT)/sensor-smoke

$(MACOS_OUT)/m5/lifecycle-sanitized: tests/darwin/guest/lifecycle_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/lifecycle_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/format-sanitized: tests/darwin/guest/format_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/format_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/libc-scalar-sanitized: tests/darwin/guest/libc_scalar_probe.cpp native/darwin/guest/libc_scalar.cpp include/shiraz/darwin/guest/libc_scalar.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/libc_scalar_probe.cpp native/darwin/guest/libc_scalar.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-extra-services-sanitize-check
macos-guest-extra-services-sanitize-check: macos-guest-lifecycle-fixture macos-guest-format-fixture macos-guest-libc-scalar-fixture $(MACOS_OUT)/m5/lifecycle-sanitized $(MACOS_OUT)/m5/format-sanitized $(MACOS_OUT)/m5/libc-scalar-sanitized
	$(MACOS_OUT)/m5/lifecycle-sanitized $(MACOS_GUEST_DEP_DIR)
	$(MACOS_OUT)/m5/format-sanitized $(MACOS_GUEST_DEP_DIR)
	$(MACOS_OUT)/m5/libc-scalar-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-extra-services-sanitize-check

$(MACOS_OUT)/test-audio-decode-sanitized: tests/darwin/audio_decode.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_HEADERS) $(MACOS_AUDIO_CODEC) $(MACOS_OUT)/.dir
	$(MACOS_CXX) -O1 -fsanitize=address,undefined -I.deps/stb-audio tests/darwin/audio_decode.cpp native/audio.cpp native/darwin/audio_decode.cpp $(MACOS_AUDIO_CODEC) -o $@ $(MACOS_AUDIO_FRAMEWORKS) -pthread

$(MACOS_GUEST_DEP_DIR)/fortify.o: tests/darwin/guest/deps/fortify.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-fortify-fixture macos-guest-fortify-check
macos-guest-fortify-fixture: $(MACOS_GUEST_DEP_DIR)/fortify.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libfortify.so $(MACOS_GUEST_DEP_DIR)/fortify.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libfortify.so
$(MACOS_OUT)/m5/fortify-probe: tests/darwin/guest/fortify_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/fortify_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-fortify-check: macos-guest-fortify-fixture $(MACOS_OUT)/m5/fortify-probe
	$(MACOS_OUT)/m5/fortify-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/fortify-probe $(MACOS_OUT)/m5/fortify-probe-hardened.pending
	mv $(MACOS_OUT)/m5/fortify-probe-hardened.pending $(MACOS_OUT)/m5/fortify-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/fortify-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/fortify-probe-hardened
	$(MACOS_OUT)/m5/fortify-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-fortify-check

$(MACOS_GUEST_DEP_DIR)/link-cxx.o: tests/darwin/guest/deps/link-cxx.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libstdc++-link.so: $(MACOS_GUEST_DEP_DIR)/link-cxx.o tests/darwin/guest/deps/cxx.map | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/cxx.map -soname libstdc++.so $< -o $@
$(MACOS_GUEST_DEP_DIR)/cxx.o: tests/darwin/guest/deps/cxx.cpp mk/darwin.mk
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -fno-exceptions -fno-rtti -mno-outline-atomics -nostdinc++ -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-cxx-fixture macos-guest-cxx-check
macos-guest-cxx-fixture: $(MACOS_GUEST_DEP_DIR)/cxx.o $(MACOS_GUEST_DEP_DIR)/libstdc++-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libcxx-fixture.so $(MACOS_GUEST_DEP_DIR)/cxx.o -L$(MACOS_GUEST_DEP_DIR) -l:libstdc++-link.so -o $(MACOS_GUEST_DEP_DIR)/libcxx-fixture.so
$(MACOS_OUT)/m5/cxx-probe: tests/darwin/guest/cxx_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/cxx_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-cxx-check: macos-guest-cxx-fixture $(MACOS_OUT)/m5/cxx-probe
	$(MACOS_OUT)/m5/cxx-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/cxx-probe $(MACOS_OUT)/m5/cxx-probe-hardened.pending
	mv $(MACOS_OUT)/m5/cxx-probe-hardened.pending $(MACOS_OUT)/m5/cxx-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/cxx-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/cxx-probe-hardened
	$(MACOS_OUT)/m5/cxx-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-cxx-check

$(MACOS_GUEST_DEP_DIR)/link-locale.o: tests/darwin/guest/deps/link-locale.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/locale.o: tests/darwin/guest/deps/locale.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/multibyte.o: tests/darwin/guest/deps/multibyte.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/time.o: tests/darwin/guest/deps/time.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-locale-fixture macos-guest-multibyte-fixture macos-guest-time-fixture
macos-guest-locale-fixture: $(MACOS_GUEST_DEP_DIR)/locale.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname liblocale.so $(MACOS_GUEST_DEP_DIR)/locale.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/liblocale.so
macos-guest-multibyte-fixture: $(MACOS_GUEST_DEP_DIR)/multibyte.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libmultibyte.so $(MACOS_GUEST_DEP_DIR)/multibyte.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libmultibyte.so
macos-guest-time-fixture: $(MACOS_GUEST_DEP_DIR)/time.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libtime-fixture.so $(MACOS_GUEST_DEP_DIR)/time.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libtime-fixture.so
$(MACOS_OUT)/m5/locale-probe: tests/darwin/guest/locale_probe.cpp native/darwin/guest/locale.cpp include/shiraz/darwin/guest/locale.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/locale_probe.cpp native/darwin/guest/locale.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/multibyte-probe: tests/darwin/guest/multibyte_probe.cpp native/darwin/guest/locale.cpp native/darwin/guest/multibyte.cpp include/shiraz/darwin/guest/locale.hpp include/shiraz/darwin/guest/multibyte.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/multibyte_probe.cpp native/darwin/guest/locale.cpp native/darwin/guest/multibyte.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/time-probe: tests/darwin/guest/time_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/time_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-locale-check macos-guest-multibyte-check macos-guest-time-check
macos-guest-locale-check: macos-guest-locale-fixture $(MACOS_OUT)/m5/locale-probe
	$(MACOS_OUT)/m5/locale-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/locale-probe $(MACOS_OUT)/m5/locale-probe-hardened.pending
	mv $(MACOS_OUT)/m5/locale-probe-hardened.pending $(MACOS_OUT)/m5/locale-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/locale-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/locale-probe-hardened
	$(MACOS_OUT)/m5/locale-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-multibyte-check: macos-guest-multibyte-fixture $(MACOS_OUT)/m5/multibyte-probe
	$(MACOS_OUT)/m5/multibyte-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/multibyte-probe $(MACOS_OUT)/m5/multibyte-probe-hardened.pending
	mv $(MACOS_OUT)/m5/multibyte-probe-hardened.pending $(MACOS_OUT)/m5/multibyte-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/multibyte-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/multibyte-probe-hardened
	$(MACOS_OUT)/m5/multibyte-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-time-check: macos-guest-time-fixture $(MACOS_OUT)/m5/time-probe
	$(MACOS_OUT)/m5/time-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/time-probe $(MACOS_OUT)/m5/time-probe-hardened.pending
	mv $(MACOS_OUT)/m5/time-probe-hardened.pending $(MACOS_OUT)/m5/time-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/time-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/time-probe-hardened
	$(MACOS_OUT)/m5/time-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-locale-check macos-guest-multibyte-check macos-guest-time-check

$(MACOS_GUEST_DEP_DIR)/system.o: tests/darwin/guest/deps/system.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/scan.o: tests/darwin/guest/deps/scan.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-system-fixture macos-guest-scan-fixture
macos-guest-system-fixture: $(MACOS_GUEST_DEP_DIR)/system.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libsystem-fixture.so $(MACOS_GUEST_DEP_DIR)/system.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libsystem-fixture.so
macos-guest-scan-fixture: $(MACOS_GUEST_DEP_DIR)/scan.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libscan.so $(MACOS_GUEST_DEP_DIR)/scan.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libscan.so
$(MACOS_OUT)/m5/system-probe: tests/darwin/guest/system_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/system_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/scan-probe: tests/darwin/guest/scan_probe.cpp native/darwin/guest/scan.cpp include/shiraz/darwin/guest/scan.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/scan_probe.cpp native/darwin/guest/scan.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-system-check macos-guest-scan-check
macos-guest-system-check: macos-guest-system-fixture $(MACOS_OUT)/m5/system-probe
	$(MACOS_OUT)/m5/system-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/system-probe $(MACOS_OUT)/m5/system-probe-hardened.pending
	mv $(MACOS_OUT)/m5/system-probe-hardened.pending $(MACOS_OUT)/m5/system-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/system-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/system-probe-hardened
	$(MACOS_OUT)/m5/system-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-scan-check: macos-guest-scan-fixture $(MACOS_OUT)/m5/scan-probe
	$(MACOS_OUT)/m5/scan-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/scan-probe $(MACOS_OUT)/m5/scan-probe-hardened.pending
	mv $(MACOS_OUT)/m5/scan-probe-hardened.pending $(MACOS_OUT)/m5/scan-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/scan-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/scan-probe-hardened
	$(MACOS_OUT)/m5/scan-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-system-check macos-guest-scan-check

$(MACOS_GUEST_ICU_ARCHIVES): scripts/bootstrap_guest_icu.py scripts/bootstrap_darwin.py
	uv run --no-project python scripts/bootstrap_guest_icu.py
.PHONY: macos-guest-icu-check
macos-guest-icu-check:
	uv run --no-project python scripts/bootstrap_guest_icu.py
$(MACOS_OUT)/m5/locale-probe $(MACOS_OUT)/m5/multibyte-probe: $(MACOS_GUEST_ICU_ARCHIVES)
macos-guest-check macos-guest-jni-check macos-guest-jni-adhoc-check: macos-guest-icu-check

$(MACOS_GUEST_DEP_DIR)/jump.o: tests/darwin/guest/deps/jump.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/scalar-extra.o: tests/darwin/guest/deps/scalar_extra.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-jump-fixture macos-guest-scalar-extra-fixture
macos-guest-jump-fixture: $(MACOS_GUEST_DEP_DIR)/jump.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libjump-fixture.so $(MACOS_GUEST_DEP_DIR)/jump.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libjump-fixture.so
macos-guest-scalar-extra-fixture: $(MACOS_GUEST_DEP_DIR)/scalar-extra.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libscalar-extra.so $(MACOS_GUEST_DEP_DIR)/scalar-extra.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libscalar-extra.so
$(MACOS_OUT)/m5/jump-probe: tests/darwin/guest/jump_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/jump_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/scalar-extra-probe: tests/darwin/guest/scalar_extra_probe.cpp native/darwin/guest/libc_scalar.cpp include/shiraz/darwin/guest/libc_scalar.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/scalar_extra_probe.cpp native/darwin/guest/libc_scalar.cpp $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-jump-check macos-guest-scalar-extra-check
macos-guest-jump-check: macos-guest-jump-fixture $(MACOS_OUT)/m5/jump-probe
	$(MACOS_OUT)/m5/jump-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/jump-probe $(MACOS_OUT)/m5/jump-probe-hardened.pending
	mv $(MACOS_OUT)/m5/jump-probe-hardened.pending $(MACOS_OUT)/m5/jump-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/jump-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/jump-probe-hardened
	$(MACOS_OUT)/m5/jump-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-scalar-extra-check: macos-guest-scalar-extra-fixture $(MACOS_OUT)/m5/scalar-extra-probe
	$(MACOS_OUT)/m5/scalar-extra-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/scalar-extra-probe $(MACOS_OUT)/m5/scalar-extra-probe-hardened.pending
	mv $(MACOS_OUT)/m5/scalar-extra-probe-hardened.pending $(MACOS_OUT)/m5/scalar-extra-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/scalar-extra-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/scalar-extra-probe-hardened
	$(MACOS_OUT)/m5/scalar-extra-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-jump-check macos-guest-scalar-extra-check

# Binary128 conversion gates: genuine AAPCS64 q0 returns, never Darwin long double.
$(MACOS_GUEST_DEP_DIR)/quad.o: tests/darwin/guest/deps/quad.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-quad-fixture macos-guest-quad-check macos-guest-quad-sanitize-check
macos-guest-quad-fixture: $(MACOS_GUEST_DEP_DIR)/quad.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libquad.so $(MACOS_GUEST_DEP_DIR)/quad.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libquad.so
$(MACOS_OUT)/m5/quad-probe: tests/darwin/guest/quad_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp include/shiraz/darwin/guest/quad.hpp include/shiraz/darwin/guest/locale.hpp $(MACOS_GUEST_ICU_ARCHIVES) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/quad_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-quad-check: macos-guest-quad-fixture $(MACOS_OUT)/m5/quad-probe
	$(MACOS_OUT)/m5/quad-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/quad-probe $(MACOS_OUT)/m5/quad-probe-hardened.pending
	mv $(MACOS_OUT)/m5/quad-probe-hardened.pending $(MACOS_OUT)/m5/quad-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/quad-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/quad-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/quad-probe-hardened
	$(MACOS_OUT)/m5/quad-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/quad-sanitized: tests/darwin/guest/quad_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp include/shiraz/darwin/guest/quad.hpp include/shiraz/darwin/guest/locale.hpp $(MACOS_GUEST_ICU_ARCHIVES) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/quad_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-quad-sanitize-check: macos-guest-quad-fixture $(MACOS_OUT)/m5/quad-sanitized
	$(MACOS_OUT)/m5/quad-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-quad-check macos-guest-quad-sanitize-check

# Guest process services: no raw Linux syscalls or host-global syslog state.
$(MACOS_GUEST_DEP_DIR)/link-process.o: tests/darwin/guest/deps/link-process.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/process.o: tests/darwin/guest/deps/process.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-process-fixture macos-guest-process-check macos-guest-process-sanitize-check
macos-guest-process-fixture: $(MACOS_GUEST_DEP_DIR)/process.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libprocess-fixture.so $(MACOS_GUEST_DEP_DIR)/process.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libprocess-fixture.so
$(MACOS_OUT)/m5/process-probe: tests/darwin/guest/process_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/process_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-process-check: macos-guest-process-fixture $(MACOS_OUT)/m5/process-probe
	$(MACOS_OUT)/m5/process-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/process-probe $(MACOS_OUT)/m5/process-probe-hardened.pending
	mv $(MACOS_OUT)/m5/process-probe-hardened.pending $(MACOS_OUT)/m5/process-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/process-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/process-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/process-probe-hardened
	$(MACOS_OUT)/m5/process-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/process-sanitized: tests/darwin/guest/process_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/process_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-process-sanitize-check: macos-guest-process-fixture $(MACOS_OUT)/m5/process-sanitized
	$(MACOS_OUT)/m5/process-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-process-check
macos-guest-extra-services-sanitize-check: macos-guest-process-sanitize-check
# This target is declared before IO variables above; retain explicit new
# process integration dependencies so an existing JNI library rebuilds too.
$(MACOS_OUT)/libshiraz-guest.so: native/darwin/guest/process.cpp include/shiraz/darwin/guest/process.hpp native/darwin/guest/memory.cpp

# IO lists are defined after the JNI target, so add binary128 rebuild inputs explicitly.
$(MACOS_OUT)/libshiraz-guest.so: native/darwin/guest/quad.cpp include/shiraz/darwin/guest/quad.hpp native/darwin/guest/memory.cpp

$(MACOS_GUEST_DEP_DIR)/link-quad-math.o: tests/darwin/guest/deps/link-quad-math.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libquad-math-link.so: $(MACOS_GUEST_DEP_DIR)/link-quad-math.o
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined --version-script=tests/darwin/guest/deps/libc.map -soname libm.so $< -o $@

$(MACOS_GUEST_DEP_DIR)/quad-math.o: tests/darwin/guest/deps/quad_math.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-quad-math-fixture macos-guest-quad-math-check macos-guest-quad-math-sanitize-check
macos-guest-quad-math-fixture: $(MACOS_GUEST_DEP_DIR)/quad-math.o $(MACOS_GUEST_DEP_DIR)/libc-link.so $(MACOS_GUEST_DEP_DIR)/libquad-math-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libquad-math.so $(MACOS_GUEST_DEP_DIR)/quad-math.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -l:libquad-math-link.so -o $(MACOS_GUEST_DEP_DIR)/libquad-math.so
$(MACOS_OUT)/m5/quad-math-probe: tests/darwin/guest/quad_math_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp include/shiraz/darwin/guest/quad.hpp include/shiraz/darwin/guest/locale.hpp $(MACOS_GUEST_ICU_ARCHIVES) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/quad_math_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-quad-math-check: macos-guest-quad-math-fixture $(MACOS_OUT)/m5/quad-math-probe
	$(MACOS_OUT)/m5/quad-math-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/quad-math-probe $(MACOS_OUT)/m5/quad-math-probe-hardened.pending
	mv $(MACOS_OUT)/m5/quad-math-probe-hardened.pending $(MACOS_OUT)/m5/quad-math-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/quad-math-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/quad-math-probe-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/quad-math-probe-hardened
	$(MACOS_OUT)/m5/quad-math-probe-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/quad-math-sanitized: tests/darwin/guest/quad_math_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp include/shiraz/darwin/guest/quad.hpp include/shiraz/darwin/guest/locale.hpp $(MACOS_GUEST_ICU_ARCHIVES) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/quad_math_probe.cpp native/darwin/guest/quad.cpp native/darwin/guest/locale.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-quad-math-sanitize-check: macos-guest-quad-math-fixture $(MACOS_OUT)/m5/quad-math-sanitized
	$(MACOS_OUT)/m5/quad-math-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-quad-math-check macos-guest-quad-math-sanitize-check

$(MACOS_GUEST_DEP_DIR)/transfer.o: tests/darwin/guest/deps/transfer.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@

.PHONY: macos-guest-transfer-fixture macos-guest-transfer-check
macos-guest-transfer-fixture: $(MACOS_GUEST_DEP_DIR)/transfer.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libtransfer.so $(MACOS_GUEST_DEP_DIR)/transfer.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libtransfer.so
$(MACOS_OUT)/m5/transfer-probe: tests/darwin/guest/transfer_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/transfer_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-transfer-check: macos-guest-transfer-fixture $(MACOS_OUT)/m5/transfer-probe
	$(MACOS_OUT)/m5/transfer-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/transfer-probe $(MACOS_OUT)/m5/transfer-probe-hardened.pending
	mv $(MACOS_OUT)/m5/transfer-probe-hardened.pending $(MACOS_OUT)/m5/transfer-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/transfer-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/transfer-probe-hardened
	$(MACOS_OUT)/m5/transfer-probe-hardened $(MACOS_GUEST_DEP_DIR)

macos-guest-check: macos-guest-transfer-check

$(MACOS_GUEST_DEP_DIR)/wideformat.o: tests/darwin/guest/deps/wideformat.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@

.PHONY: macos-guest-wideformat-fixture macos-guest-wideformat-check
macos-guest-wideformat-fixture: $(MACOS_GUEST_DEP_DIR)/wideformat.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libwideformat.so $(MACOS_GUEST_DEP_DIR)/wideformat.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libwideformat.so
$(MACOS_OUT)/m5/wideformat-probe: tests/darwin/guest/wideformat_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/wideformat_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-wideformat-check: macos-guest-wideformat-fixture $(MACOS_OUT)/m5/wideformat-probe
	$(MACOS_OUT)/m5/wideformat-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/wideformat-probe $(MACOS_OUT)/m5/wideformat-probe-hardened.pending
	mv $(MACOS_OUT)/m5/wideformat-probe-hardened.pending $(MACOS_OUT)/m5/wideformat-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/wideformat-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/wideformat-probe-hardened
	$(MACOS_OUT)/m5/wideformat-probe-hardened $(MACOS_GUEST_DEP_DIR)

macos-guest-check: macos-guest-wideformat-check

$(MACOS_GUEST_DEP_DIR)/guestegl.o: tests/darwin/guest/deps/guestegl.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 $(MACOS_ANGLE_CFLAGS) -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/guestegl-bool.o: tests/darwin/guest/deps/guestegl_bool.S
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/link-guestegl.o: tests/darwin/guest/deps/link-guestegl.S
	xcrun clang --target=aarch64-linux-android24 -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libguestegl-link.so: $(MACOS_GUEST_DEP_DIR)/link-guestegl.o
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libEGL.so $< -o $@
.PHONY: macos-guest-egl-fixture macos-guest-egl-check
macos-guest-egl-fixture: $(MACOS_GUEST_DEP_DIR)/guestegl.o $(MACOS_GUEST_DEP_DIR)/guestegl-bool.o $(MACOS_GUEST_DEP_DIR)/libguestegl-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libguestegl.so $(MACOS_GUEST_DEP_DIR)/guestegl.o $(MACOS_GUEST_DEP_DIR)/guestegl-bool.o -L$(MACOS_GUEST_DEP_DIR) -l:libguestegl-link.so -o $(MACOS_GUEST_DEP_DIR)/libguestegl.so
$(MACOS_OUT)/m5/guestegl-probe: tests/darwin/guest/guestegl_probe.cpp native/darwin/guest/guestegl.cpp include/shiraz/darwin/guest/guestegl.hpp include/shiraz/darwin/guest/guestegl_symbols.inc include/shiraz/darwin/guest/guestegl_required.inc $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_ANGLE_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/guestegl_probe.cpp native/darwin/guest/guestegl.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-egl-check: macos-guest-egl-fixture $(MACOS_OUT)/m5/guestegl-probe $(MACOS_OUT)/libEGL_shim.so
	SHIRAZ_EGL_LIBRARY="$(CURDIR)/$(MACOS_OUT)/libEGL.dylib" $(MACOS_OUT)/m5/guestegl-probe $(MACOS_GUEST_DEP_DIR) "$(CURDIR)/$(MACOS_OUT)/libEGL_shim.so"
macos-guest-osmand-link-check: $(MACOS_OUT)/libEGL_shim.so

$(MACOS_OUT)/m5/wideformat-sanitized: tests/darwin/guest/wideformat_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/wideformat_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-wideformat-sanitize-check
macos-guest-wideformat-sanitize-check: macos-guest-wideformat-fixture $(MACOS_OUT)/m5/wideformat-sanitized
	$(MACOS_OUT)/m5/wideformat-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-wideformat-sanitize-check

$(MACOS_OUT)/m5/transfer-sanitized: tests/darwin/guest/transfer_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/transfer_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-transfer-sanitize-check
macos-guest-transfer-sanitize-check: macos-guest-transfer-fixture $(MACOS_OUT)/m5/transfer-sanitized
	$(MACOS_OUT)/m5/transfer-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-transfer-sanitize-check

macos-guest-check: macos-guest-egl-check

$(MACOS_GUEST_DEP_DIR)/core-services.o: tests/darwin/guest/deps/core_services.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-core-services-fixture macos-guest-core-services-check macos-guest-core-services-sanitize-check
macos-guest-core-services-fixture: $(MACOS_GUEST_DEP_DIR)/core-services.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libcore-services.so $(MACOS_GUEST_DEP_DIR)/core-services.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libcore-services.so
$(MACOS_OUT)/m5/core-services-probe: tests/darwin/guest/core_services_probe.cpp native/darwin/guest/core_services.cpp native/darwin/guest/accounts.cpp native/darwin/guest/cleanup.cpp native/darwin/guest/fs.cpp native/darwin/guest/fonts.cpp native/darwin/guest/events.cpp native/darwin/guest/networking.cpp native/darwin/guest/signals.cpp native/darwin/guest/signals.S native/darwin/guest/fortify.cpp include/shiraz/darwin/guest/core_services.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS) $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/core_services_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
$(MACOS_OUT)/m5/core-services-sanitized: tests/darwin/guest/core_services_probe.cpp native/darwin/guest/core_services.cpp native/darwin/guest/accounts.cpp native/darwin/guest/cleanup.cpp native/darwin/guest/fs.cpp native/darwin/guest/fonts.cpp native/darwin/guest/events.cpp native/darwin/guest/networking.cpp native/darwin/guest/signals.cpp native/darwin/guest/signals.S native/darwin/guest/fortify.cpp include/shiraz/darwin/guest/core_services.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS) $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -fsanitize=address,undefined -g -Wall -Wextra -Werror tests/darwin/guest/core_services_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-core-services-check: macos-guest-core-services-fixture $(MACOS_OUT)/m5/core-services-probe
	$(MACOS_OUT)/m5/core-services-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/core-services-probe $(MACOS_OUT)/m5/core-services-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/core-services-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/core-services-probe-hardened
	$(MACOS_OUT)/m5/core-services-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-core-services-sanitize-check: macos-guest-core-services-fixture $(MACOS_OUT)/m5/core-services-sanitized
	$(MACOS_OUT)/m5/core-services-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-core-services-check macos-guest-core-services-sanitize-check

$(MACOS_OUT)/m5/osmand-host-observer.so: tests/darwin/guest/osmand_host_observer.cpp $(MACOS_OUT)/m5/native-closure-inventory.o
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_JNI_FLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -fPIC -dynamiclib $< $(MACOS_OUT)/m5/native-closure-inventory.o -o $@
.PHONY: macos-guest-osmand-jni-check
macos-guest-osmand-jni-check: $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/osmand-host-observer.so
	uv run --no-project python scripts/macos_guest_osmand_smoke.py --apk "$(APK)"

$(MACOS_GUEST_DEP_DIR)/events.o: tests/darwin/guest/deps/events.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-events-fixture macos-guest-events-check
macos-guest-events-fixture: $(MACOS_GUEST_DEP_DIR)/events.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libevents.so $(MACOS_GUEST_DEP_DIR)/events.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libevents.so
$(MACOS_OUT)/m5/events-probe: tests/darwin/guest/events_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/events_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-events-check: macos-guest-events-fixture $(MACOS_OUT)/m5/events-probe
	$(MACOS_OUT)/m5/events-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/events-probe $(MACOS_OUT)/m5/events-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/events-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/events-probe-hardened
	$(MACOS_OUT)/m5/events-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-events-check

$(MACOS_GUEST_DEP_DIR)/signals.o: tests/darwin/guest/deps/signals.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -mno-outline-atomics -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-signals-fixture macos-guest-signals-check
macos-guest-signals-fixture: $(MACOS_GUEST_DEP_DIR)/signals.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libsignals.so $(MACOS_GUEST_DEP_DIR)/signals.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libsignals.so
$(MACOS_OUT)/m5/signals-probe: tests/darwin/guest/signals_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/signals_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-signals-check: macos-guest-signals-fixture $(MACOS_OUT)/m5/signals-probe
	$(MACOS_OUT)/m5/signals-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/signals-probe $(MACOS_OUT)/m5/signals-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/signals-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/signals-probe-hardened
	$(MACOS_OUT)/m5/signals-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-signals-check

$(MACOS_GUEST_DEP_DIR)/networking.o: tests/darwin/guest/deps/networking.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-networking-fixture macos-guest-networking-check
macos-guest-networking-fixture: $(MACOS_GUEST_DEP_DIR)/networking.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libnetworking.so $(MACOS_GUEST_DEP_DIR)/networking.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libnetworking.so
$(MACOS_OUT)/m5/networking-probe: tests/darwin/guest/networking_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/networking_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-networking-check: macos-guest-networking-fixture $(MACOS_OUT)/m5/networking-probe
	$(MACOS_OUT)/m5/networking-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/networking-probe $(MACOS_OUT)/m5/networking-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/networking-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/networking-probe-hardened
	$(MACOS_OUT)/m5/networking-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-networking-check


$(MACOS_GUEST_DEP_DIR)/accounts.o: tests/darwin/guest/deps/accounts.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-accounts-fixture macos-guest-accounts-check
macos-guest-accounts-fixture: $(MACOS_GUEST_DEP_DIR)/accounts.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libaccounts.so $(MACOS_GUEST_DEP_DIR)/accounts.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libaccounts.so
$(MACOS_OUT)/m5/accounts-probe: tests/darwin/guest/accounts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/accounts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-accounts-check: macos-guest-accounts-fixture $(MACOS_OUT)/m5/accounts-probe
	$(MACOS_OUT)/m5/accounts-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/accounts-probe $(MACOS_OUT)/m5/accounts-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/accounts-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/accounts-probe-hardened
	$(MACOS_OUT)/m5/accounts-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-accounts-check


$(MACOS_GUEST_DEP_DIR)/remap.o: tests/darwin/guest/deps/remap.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-remap-fixture macos-guest-remap-check
macos-guest-remap-fixture: $(MACOS_GUEST_DEP_DIR)/remap.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libremap.so $(MACOS_GUEST_DEP_DIR)/remap.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libremap.so
$(MACOS_OUT)/m5/remap-probe: tests/darwin/guest/remap_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/remap_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-remap-check: macos-guest-remap-fixture $(MACOS_OUT)/m5/remap-probe
	$(MACOS_OUT)/m5/remap-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/remap-probe $(MACOS_OUT)/m5/remap-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/remap-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/remap-probe-hardened
	$(MACOS_OUT)/m5/remap-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-remap-check


$(MACOS_GUEST_DEP_DIR)/fs.o: tests/darwin/guest/deps/fs.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-fs-fixture macos-guest-fs-check
macos-guest-fs-fixture: $(MACOS_GUEST_DEP_DIR)/fs.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libfs.so $(MACOS_GUEST_DEP_DIR)/fs.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libfs.so
$(MACOS_OUT)/m5/fs-probe: tests/darwin/guest/fs_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/fs_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-fs-check: macos-guest-fs-fixture $(MACOS_OUT)/m5/fs-probe
	$(MACOS_OUT)/m5/fs-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/fs-probe $(MACOS_OUT)/m5/fs-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/fs-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/fs-probe-hardened
	$(MACOS_OUT)/m5/fs-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-fs-check


$(MACOS_GUEST_DEP_DIR)/cleanup.o: tests/darwin/guest/deps/cleanup.c $(MACOS_OUT)/m5/guest_io.hpp mk/darwin.mk
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -mno-outline-atomics -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-cleanup-fixture macos-guest-cleanup-check
macos-guest-cleanup-fixture: $(MACOS_GUEST_DEP_DIR)/cleanup.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libcleanup.so $(MACOS_GUEST_DEP_DIR)/cleanup.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libcleanup.so
$(MACOS_OUT)/m5/cleanup-probe: tests/darwin/guest/cleanup_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/cleanup_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-cleanup-check: macos-guest-cleanup-fixture $(MACOS_OUT)/m5/cleanup-probe
	$(MACOS_OUT)/m5/cleanup-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/cleanup-probe $(MACOS_OUT)/m5/cleanup-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/cleanup-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/cleanup-probe-hardened
	$(MACOS_OUT)/m5/cleanup-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-cleanup-check


.PHONY: macos-guest-osmand-core-check
macos-guest-osmand-core-check: macos-framework $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/osmand-host-observer.so $(MACOS_OUT)/libEGL_shim.so
	uv run --no-project python scripts/macos_guest_osmand_smoke.py --core --apk "$(APK)"

macos-runtime-libs: $(MACOS_OUT)/libshiraz-guest.so

$(MACOS_OUT)/m5/events-sanitized: tests/darwin/guest/events_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/events_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-events-sanitize-check
macos-guest-events-sanitize-check: macos-guest-events-fixture $(MACOS_OUT)/m5/events-sanitized
	$(MACOS_OUT)/m5/events-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-events-sanitize-check

$(MACOS_OUT)/m5/signals-sanitized: tests/darwin/guest/signals_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/signals_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-signals-sanitize-check
macos-guest-signals-sanitize-check: macos-guest-signals-fixture $(MACOS_OUT)/m5/signals-sanitized
	$(MACOS_OUT)/m5/signals-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-signals-sanitize-check

$(MACOS_OUT)/m5/networking-sanitized: tests/darwin/guest/networking_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/networking_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-networking-sanitize-check
macos-guest-networking-sanitize-check: macos-guest-networking-fixture $(MACOS_OUT)/m5/networking-sanitized
	$(MACOS_OUT)/m5/networking-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-networking-sanitize-check

$(MACOS_OUT)/m5/accounts-sanitized: tests/darwin/guest/accounts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/accounts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-accounts-sanitize-check
macos-guest-accounts-sanitize-check: macos-guest-accounts-fixture $(MACOS_OUT)/m5/accounts-sanitized
	$(MACOS_OUT)/m5/accounts-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-accounts-sanitize-check

$(MACOS_OUT)/m5/cleanup-sanitized: tests/darwin/guest/cleanup_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/cleanup_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-cleanup-sanitize-check
macos-guest-cleanup-sanitize-check: macos-guest-cleanup-fixture $(MACOS_OUT)/m5/cleanup-sanitized
	$(MACOS_OUT)/m5/cleanup-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-cleanup-sanitize-check

$(MACOS_OUT)/m5/fs-sanitized: tests/darwin/guest/fs_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/fs_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-fs-sanitize-check
macos-guest-fs-sanitize-check: macos-guest-fs-fixture $(MACOS_OUT)/m5/fs-sanitized
	$(MACOS_OUT)/m5/fs-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-fs-sanitize-check

$(MACOS_OUT)/m5/remap-sanitized: tests/darwin/guest/remap_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/remap_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-remap-sanitize-check
macos-guest-remap-sanitize-check: macos-guest-remap-fixture $(MACOS_OUT)/m5/remap-sanitized
	$(MACOS_OUT)/m5/remap-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-remap-sanitize-check

$(MACOS_GUEST_DEP_DIR)/fonts.o: tests/darwin/guest/deps/fonts.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -fno-builtin -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-fonts-fixture macos-guest-fonts-check
macos-guest-fonts-fixture: $(MACOS_GUEST_DEP_DIR)/fonts.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libfonts.so $(MACOS_GUEST_DEP_DIR)/fonts.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libfonts.so
$(MACOS_OUT)/m5/fonts-probe: tests/darwin/guest/fonts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/fonts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-fonts-check: macos-guest-fonts-fixture $(MACOS_OUT)/m5/fonts-probe
	$(MACOS_OUT)/m5/fonts-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/fonts-probe $(MACOS_OUT)/m5/fonts-probe-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/fonts-probe-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/fonts-probe-hardened
	$(MACOS_OUT)/m5/fonts-probe-hardened $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-fonts-check


$(MACOS_OUT)/m5/fonts-sanitized: tests/darwin/guest/fonts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/fonts_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-fonts-sanitize-check
macos-guest-fonts-sanitize-check: macos-guest-fonts-fixture $(MACOS_OUT)/m5/fonts-sanitized
	$(MACOS_OUT)/m5/fonts-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-fonts-sanitize-check

# Exact-PC assertions complement ordinary FDE backtrace coverage.
.PHONY: macos-guest-island-unwind-check
macos-guest-island-unwind-check: macos-guest-unwind-check macos-guest-unwind-sanitize-check
	$(MACOS_OUT)/m5/unwind-probe $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so --islands
	$(MACOS_OUT)/m5/unwind-probe-hardened $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so --islands
	$(MACOS_OUT)/m5/unwind-sanitized $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so --islands
macos-guest-check: macos-guest-island-unwind-check

# Real C++ exception ABI/LSDA gate: first-party fixture, unchanged APK libc++.
# Intentionally APK-parametric and outside the APK-free aggregate fixture suite.
$(MACOS_GUEST_DEP_DIR)/cxx-exception.o: tests/darwin/guest/deps/cxx_exception.cpp
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -ffreestanding -fPIC -funwind-tables -fexceptions -frtti -fno-stack-protector -fno-omit-frame-pointer -mno-outline-atomics -O1 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_OUT)/m5/cxx-exception-probe: tests/darwin/guest/cxx_exception_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/cxx_exception_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
.PHONY: macos-guest-cxx-exception-check
macos-guest-cxx-exception-check: $(MACOS_GUEST_DEP_DIR)/libc-link.so $(MACOS_GUEST_DEP_DIR)/libdl-link.so macos-guest-libunwind-archive macos-guest-toolchain $(MACOS_GUEST_DEP_DIR)/cxx-exception.o $(MACOS_OUT)/m5/cxx-exception-probe
	uv run --no-project python scripts/macos_guest_cxx_exception_smoke.py --apk "$(APK)" --object $(MACOS_GUEST_DEP_DIR)/cxx-exception.o --probe $(MACOS_OUT)/m5/cxx-exception-probe --linker $(MACOS_GUEST_LD) --unwind-archive .deps/guest-libunwind/lib/libunwind.a --link-provider $(MACOS_GUEST_DEP_DIR)/libc-link.so --link-provider $(MACOS_GUEST_DEP_DIR)/libdl-link.so

# Deliberately partial exception search/catch gate. Full RAII/rethrow target above
# uses the real pinned guest unwinder for compiler-generated cleanup resumes.
$(MACOS_GUEST_DEP_DIR)/cxx-exception-basic.o: tests/darwin/guest/deps/cxx_exception_basic.cpp
	mkdir -p $(dir $@)
	xcrun clang++ --target=aarch64-linux-android24 -ffreestanding -fPIC -funwind-tables -fexceptions -frtti -fno-stack-protector -fno-omit-frame-pointer -fno-optimize-sibling-calls -mno-outline-atomics -O1 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-cxx-exception-basic-check
macos-guest-cxx-exception-basic-check: macos-guest-toolchain $(MACOS_GUEST_DEP_DIR)/cxx-exception-basic.o $(MACOS_OUT)/m5/cxx-exception-probe
	uv run --no-project python scripts/macos_guest_cxx_exception_smoke.py --coverage basic --apk "$(APK)" --object $(MACOS_GUEST_DEP_DIR)/cxx-exception-basic.o --probe $(MACOS_OUT)/m5/cxx-exception-probe --linker $(MACOS_GUEST_LD)

.PHONY: macos-guest-cxx-exception-libunwind-check
macos-guest-cxx-exception-libunwind-check: macos-guest-cxx-exception-check

# Actual open-source guest libunwind, used only by explicit exception fixtures.
# No NDK or native _Unwind fallback; archive existence is not execution proof.
.PHONY: macos-guest-libunwind-source macos-guest-libunwind-archive
macos-guest-libunwind-source:
	uv run --no-project python scripts/bootstrap_guest_libunwind.py --source-only
macos-guest-libunwind-archive: macos-guest-toolchain $(MACOS_OUT)/m5/guest_io.hpp
	uv run --no-project python scripts/bootstrap_guest_libunwind.py

.PHONY: macos-guest-jni-hardened-prepare
macos-guest-jni-hardened-prepare: $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_jni_smoke.py --mode hardened --prepare-only

# Identical ELF/DEX corpus for actual bionic and Darwin; no reference is invented.
GUEST_DIFFERENTIAL_MODE ?= adhoc
$(MACOS_GUEST_DEP_DIR)/differential.o: tests/darwin/guest/differential_fixture.c $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -femulated-tls -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -funwind-tables -fstack-protector-all -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/differential-guard.o: tests/darwin/guest/differential_guard.S
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -c $< -o $@
.PHONY: macos-guest-differential-fixture macos-guest-differential-corpus macos-guest-differential-darwin-check macos-guest-differential-check
macos-guest-differential-fixture: macos-guest-toolchain macos-guest-emutls-source $(MACOS_GUEST_DEP_DIR)/differential.o $(MACOS_GUEST_DEP_DIR)/differential-guard.o $(MACOS_GUEST_DEP_DIR)/emutls.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libm5-differential.so $(MACOS_GUEST_DEP_DIR)/differential.o $(MACOS_GUEST_DEP_DIR)/differential-guard.o $(MACOS_GUEST_DEP_DIR)/emutls.o -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libm5-differential.so
macos-guest-differential-corpus: macos-guest-differential-fixture
	uv run --no-project python scripts/macos_guest_differential_smoke.py --emit-reference-only
macos-guest-differential-darwin-check: macos-guest-differential-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	uv run --no-project python scripts/macos_guest_differential_smoke.py --mode $(GUEST_DIFFERENTIAL_MODE)
macos-guest-differential-check: macos-guest-differential-fixture $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so
	@test -n "$(BIONIC_REFERENCE_JSON)" || { echo 'Actual bionic reference JSON is required; use macos-guest-differential-corpus to prepare identical ELF/DEX inputs.'; exit 1; }
	uv run --no-project python scripts/macos_guest_differential_smoke.py --mode $(GUEST_DIFFERENTIAL_MODE) --reference-result "$(BIONIC_REFERENCE_JSON)"

# Component regression with the guest bridge disabled in OpenJDK and ART.
.PHONY: macos-guest-path-software-check
macos-guest-path-software-check: macos-framework
	uv run --no-project python scripts/pathmeasure_smoke.py

# Real seven-library Core gate through private native copies; route checks do
# not claim hardened runtime. Strict mode requires a supplied public identity.
MACOS_GUEST_OSMAND_STAGED_DEPS := macos-framework $(MACOS_OUT)/libshiraz-guest.so $(MACOS_OUT)/m5/host-fixture.so $(MACOS_OUT)/m5/osmand-host-observer.so $(MACOS_OUT)/libEGL_shim.so $(MACOS_OUT)/libEGL.dylib $(MACOS_OUT)/libGLESv2.dylib
.PHONY: macos-guest-osmand-core-staged-adhoc-check macos-guest-osmand-core-hardened-prepare macos-guest-osmand-core-hardened-check
macos-guest-osmand-core-staged-adhoc-check: $(MACOS_GUEST_OSMAND_STAGED_DEPS)
	uv run --no-project python scripts/macos_guest_osmand_smoke.py --core --mode staged-adhoc --apk "$(APK)"
macos-guest-osmand-core-hardened-prepare: $(MACOS_GUEST_OSMAND_STAGED_DEPS)
	uv run --no-project python scripts/macos_guest_osmand_smoke.py --core --mode hardened --prepare-only --apk "$(APK)"
macos-guest-osmand-core-hardened-check: $(MACOS_GUEST_OSMAND_STAGED_DEPS)
	uv run --no-project python scripts/macos_guest_osmand_smoke.py --core --mode hardened --apk "$(APK)"


# Real Android reference supervision. This helper is loaded only on Android;
# native host probes verify wait-status collection without claiming bionic proof.
$(MACOS_GUEST_DEP_DIR)/reference-child.o: tests/darwin/guest/bionic_reference_child.c tests/darwin/guest/bionic_reference_child.h $(MACOS_GUEST_ART)/libnativehelper/include/nativehelper/jni.h $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -isystem .deps/bionic-headers/sysroot/include -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libm5-reference-child.so: $(MACOS_GUEST_DEP_DIR)/reference-child.o $(MACOS_GUEST_DEP_DIR)/libc-link.so | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libm5-reference-child.so $< -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $@
macos-guest-differential-fixture: $(MACOS_GUEST_DEP_DIR)/libm5-reference-child.so
$(MACOS_OUT)/m5/reference-child-probe: tests/darwin/guest/reference_child_probe.c tests/darwin/guest/bionic_reference_child.c tests/darwin/guest/bionic_reference_child.h $(MACOS_OUT)/.dir
	mkdir -p $(dir $@)
	xcrun clang -std=c11 -O2 -Wall -Wextra -Werror -DSHIRAZ_REFERENCE_CHILD_CORE_ONLY tests/darwin/guest/reference_child_probe.c tests/darwin/guest/bionic_reference_child.c -o $@
$(MACOS_OUT)/m5/reference-child-sanitized: tests/darwin/guest/reference_child_probe.c tests/darwin/guest/bionic_reference_child.c tests/darwin/guest/bionic_reference_child.h $(MACOS_OUT)/.dir
	mkdir -p $(dir $@)
	xcrun clang -std=c11 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -DSHIRAZ_REFERENCE_CHILD_CORE_ONLY tests/darwin/guest/reference_child_probe.c tests/darwin/guest/bionic_reference_child.c -o $@
.PHONY: macos-guest-reference-child-check
macos-guest-reference-child-check: $(MACOS_OUT)/m5/reference-child-probe $(MACOS_OUT)/m5/reference-child-sanitized
	$(MACOS_OUT)/m5/reference-child-probe
	cp $(MACOS_OUT)/m5/reference-child-probe $(MACOS_OUT)/m5/reference-child-hardened.pending
	mv $(MACOS_OUT)/m5/reference-child-hardened.pending $(MACOS_OUT)/m5/reference-child-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/reference-child-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/reference-child-hardened
	uv run --no-project python scripts/macos_guest_differential_smoke.py --verify-native-collector $(MACOS_OUT)/m5/reference-child-hardened
	$(MACOS_OUT)/m5/reference-child-hardened
	ASAN_OPTIONS=detect_leaks=0 $(MACOS_OUT)/m5/reference-child-sanitized

.PHONY: macos-guest-reference-java-check
macos-guest-reference-java-check:
	mkdir -p $(MACOS_OUT)/m5/reference-java
	javac --release 8 -d $(MACOS_OUT)/m5/reference-java tests/darwin/guest/BionicReferenceRunner.java tests/darwin/guest/BionicReferenceSyntaxSmoke.java
	java -cp $(MACOS_OUT)/m5/reference-java BionicReferenceSyntaxSmoke

macos-guest-check: macos-guest-reference-child-check macos-guest-reference-java-check


# Genuine guest execution validates the exact atomic summary's two paths.
$(MACOS_GUEST_DEP_DIR)/atomic-effects.o: tests/darwin/guest/atomic_effects.S | macos-guest-toolchain
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libatomic-effects.so: $(MACOS_GUEST_DEP_DIR)/atomic-effects.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libatomic-effects.so $< -o $@
$(MACOS_OUT)/m5/atomic-effects-probe: tests/darwin/guest/atomic_effects_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror $< $(MACOS_GUEST_SOURCES) -o $@
$(MACOS_OUT)/m5/atomic-effects-sanitized: tests/darwin/guest/atomic_effects_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined $< $(MACOS_GUEST_SOURCES) -o $@
.PHONY: macos-guest-atomic-effects-check
macos-guest-atomic-effects-check: $(MACOS_GUEST_DEP_DIR)/libatomic-effects.so $(MACOS_OUT)/m5/atomic-effects-probe $(MACOS_OUT)/m5/atomic-effects-sanitized
	$(MACOS_OUT)/m5/atomic-effects-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/atomic-effects-probe $(MACOS_OUT)/m5/atomic-effects-hardened.pending
	mv $(MACOS_OUT)/m5/atomic-effects-hardened.pending $(MACOS_OUT)/m5/atomic-effects-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/atomic-effects-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/atomic-effects-hardened
	codesign --display --verbose=2 --entitlements - $(MACOS_OUT)/m5/atomic-effects-hardened
	$(MACOS_OUT)/m5/atomic-effects-hardened $(MACOS_GUEST_DEP_DIR)
	ASAN_OPTIONS=detect_leaks=0 $(MACOS_OUT)/m5/atomic-effects-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-atomic-effects-check

# Guest ELF compilation only; reuse existing host probes without building or
# signing them. This target deliberately is not part of the signing aggregate.
$(MACOS_OUT)/m5/return-auth/a/atomic-effects.o: tests/darwin/guest/atomic_effects.S | macos-guest-toolchain
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -DSHIRAZ_RETURN_AUTH_A -c $< -o $@
$(MACOS_OUT)/m5/return-auth/b/atomic-effects.o: tests/darwin/guest/atomic_effects.S | macos-guest-toolchain
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -DSHIRAZ_RETURN_AUTH_B -c $< -o $@
$(MACOS_OUT)/m5/return-auth/a/libatomic-effects.so: $(MACOS_OUT)/m5/return-auth/a/atomic-effects.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libatomic-effects.so $< -o $@
$(MACOS_OUT)/m5/return-auth/b/libatomic-effects.so: $(MACOS_OUT)/m5/return-auth/b/atomic-effects.o | macos-guest-toolchain
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --eh-frame-hdr --no-undefined -soname libatomic-effects.so $< -o $@
.PHONY: macos-guest-return-auth-local-check
macos-guest-return-auth-local-check: $(MACOS_OUT)/m5/return-auth/a/libatomic-effects.so $(MACOS_OUT)/m5/return-auth/b/libatomic-effects.so
	uv run --no-project python scripts/macos_guest_return_auth_local.py --directory $(MACOS_OUT)/m5/return-auth --output $(MACOS_OUT)/m5/return-auth/result.json

$(MACOS_OUT)/libshiraz-guest.so: include/shiraz/darwin/guest/descriptor.hpp
.PHONY: macos-guest-descriptor-local-check
macos-guest-check: macos-guest-descriptor-local-check
# Syntax/constexpr checks only: no Mach-O output, linker or codesign invocation.
macos-guest-descriptor-local-check:
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -Wall -Wextra -Werror -fsyntax-only tests/darwin/guest/descriptor_compile.cpp
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_JNI_FLAGS) -std=c++17 -Wall -Wextra -Werror -fsyntax-only native/darwin/guest/jni.cpp

.PHONY: macos-guest-return-auth-unwind-local-check
# Requires existing probes and guest dependencies; never builds/signs host code.
macos-guest-return-auth-unwind-local-check:
	uv run --no-project python scripts/macos_guest_return_auth_unwind_local.py --apk "$(APK)"

.PHONY: macos-guest-descriptor-runtime-local-check
# Guest-only compilation; all host probes must already exist unchanged.
macos-guest-descriptor-runtime-local-check:
	uv run --no-project python scripts/macos_guest_descriptor_runtime_local.py

.PHONY: macos-guest-opensles-lifetime-local-check
# LLVM text only: verifies no static-destructor registration before loader exit.
macos-guest-opensles-lifetime-local-check:
	mkdir -p $(MACOS_OUT)/m5
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_JNI_FLAGS) -isystem third_party/opensles -std=c++17 -Wall -Wextra -Werror -O0 -S -emit-llvm native/darwin/guest/opensles.cpp -o $(MACOS_OUT)/m5/opensles-lifetime.ll
	uv run --no-project python -c 'from pathlib import Path; s=Path("$(MACOS_OUT)/m5/opensles-lifetime.ll").read_text(); assert "define " in s and "audio_runtime" in s; assert all(x not in s for x in ("llvm.global_ctors", "__cxa_atexit", "AudioRuntimeD")); print("M5_OPENSL_LIFETIME_COMPILE_PASS static_destructors=0 runtime_execution=0 signing_performed=0")'
macos-guest-check: macos-guest-opensles-lifetime-local-check

# Typed guest OpenSL tables and native AudioQueue backend. This component gate
# exercises object/queue semantics without claiming hardware audio playback.
.PHONY: macos-guest-opensles-check
$(MACOS_OUT)/m5/opensles-probe: tests/darwin/guest/opensles_probe.cpp native/darwin/guest/opensles.cpp native/opensles.cpp include/shiraz/darwin/guest/opensles.hpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_JNI_CFLAGS) -isystem third_party/opensles -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/opensles_probe.cpp native/darwin/guest/opensles.cpp $(MACOS_GUEST_SOURCES) -framework AudioToolbox -o $@
macos-guest-opensles-check: $(MACOS_OUT)/m5/opensles-probe
	$(MACOS_OUT)/m5/opensles-probe
macos-guest-check: macos-guest-opensles-check

.PHONY: macos-guest-unsigned-local-build
# Existing generated inputs only. Suppress the linker's automatic signature;
# write a separate image and never mutate the normal bridge.
macos-guest-unsigned-local-build:
	mkdir -p $(MACOS_OUT)/m5/unsigned
	$(MACOS_GUEST_BRIDGE_LINK) -Wl,-no_adhoc_codesign -o $(MACOS_OUT)/m5/unsigned/libshiraz-guest.so

$(MACOS_OUT)/m5/guest_tls_layout.hpp: scripts/generate_guest_tls_layout.py
	uv run --no-project python scripts/generate_guest_tls_layout.py
$(MACOS_OUT)/libshiraz-guest.so: $(MACOS_OUT)/m5/guest_tls_layout.hpp

.PHONY: macos-guest-tls-layout-local-check
macos-guest-tls-layout-local-check:
	uv run --no-project python scripts/generate_guest_tls_layout.py
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -Wall -Wextra -Werror -fsyntax-only native/darwin/guest/tls.cpp
macos-guest-check: macos-guest-tls-layout-local-check

.PHONY: macos-guest-jni-descriptor-local-check
# Requires a current --mode adhoc JNI run; only guest ELF variants are built.
macos-guest-jni-descriptor-local-check:
	uv run --no-project python scripts/macos_guest_jni_descriptor_local.py

.PHONY: macos-guest-jni-descriptor-hardened-local-check
# Reuses an existing strict signed closure; verifies it without signing.
macos-guest-jni-descriptor-hardened-local-check:
	uv run --no-project python scripts/macos_guest_jni_descriptor_local.py --mode hardened

.PHONY: macos-guest-telegram-api-check
$(MACOS_OUT)/m5/telegram-api-probe: tests/darwin/guest/telegram_api_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS) $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/telegram_api_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_IO_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-telegram-api-check: $(MACOS_OUT)/m5/telegram-api-probe
	$(MACOS_OUT)/m5/telegram-api-probe $(MACOS_OUT)/m5/telegram-api-work
macos-guest-check: macos-guest-telegram-api-check

.PHONY: macos-work-source-smoke
macos-work-source-smoke: macos-framework
	uv run --no-project python scripts/java_framework_smoke.py WorkSourceSmoke --marker WORK_SOURCE_OK

.PHONY: macos-telegram-framework-smoke
macos-telegram-framework-smoke: macos-runtime-libs macos-graphics-libs macos-framework-resources macos-framework
	uv run --no-project python scripts/java_framework_smoke.py WorkSourceSmoke --marker WORK_SOURCE_OK
	uv run --no-project python scripts/java_framework_smoke.py ColorValueSmoke --marker COLOR_VALUE_AND_DEFAULT_FEATURES_OK
	uv run --no-project python scripts/java_framework_smoke.py TypefaceBuilderSmoke --marker TYPEFACE_BUILDER_OK
	uv run --no-project python scripts/java_framework_smoke.py BitmapDrawableConstructorSmoke --marker BITMAP_DRAWABLE_CONSTRUCTOR_OK
	uv run --no-project python scripts/java_framework_smoke.py AppOpsWatchSmoke --marker APP_OPS_WATCH_OK
	uv run --no-project python scripts/java_framework_smoke.py BackupUnavailableSmoke --marker BACKUP_UNAVAILABLE_OK
	uv run --no-project python scripts/java_framework_smoke.py InputMethodMetadataSmoke --marker INPUT_METHOD_METADATA_OK
	uv run --no-project python scripts/java_framework_smoke.py WindowMetadataSmoke --marker WINDOW_METADATA_OK
	uv run --no-project python scripts/java_framework_smoke.py BitmapOpacitySmoke --marker BITMAP_OPACITY_OK
	uv run --no-project python scripts/java_framework_smoke.py ViewSwitcherSmoke --marker VIEW_SWITCHER_OK
	uv run --no-project python scripts/java_framework_smoke.py PathLastPointSmoke --marker PATH_LAST_POINT_OK
	uv run --no-project python scripts/java_framework_smoke.py ColorGlyphBoundsSmoke --marker COLOR_GLYPH_BOUNDS_OK

# AVFoundation capture keeps macOS TCC and Shiraz per-app consent independent.
$(MACOS_OUT)/capture.exports: scripts/darwin_jni_exports.py java/shiraz/runtime/NativeCapture.java
	uv run --no-project python scripts/darwin_jni_exports.py java/shiraz/runtime/NativeCapture.java > $@
$(MACOS_OUT)/libshiraz-capture.so: $(CURDIR)/.local/lib/libvo-aacenc.a native/darwin/capture.mm include/shiraz/darwin/audio_clock.hpp java/shiraz/runtime/NativeCapture.java $(MACOS_OUT)/capture.exports
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -fobjc-arc -fPIC $(MACOS_DYLIB) -Wl,-exported_symbols_list,$(MACOS_OUT)/capture.exports -Wl,-dead_strip -I$(CURDIR)/.local/include native/darwin/capture.mm $(CURDIR)/.local/lib/libvo-aacenc.a -o $@.fresh -framework AVFoundation -framework AudioToolbox -framework Foundation -framework CoreMedia -framework CoreVideo -pthread
	mv -f $@.fresh $@
macos-runtime-libs: $(MACOS_OUT)/libshiraz-capture.so
macos-jvm-native: $(MACOS_OUT)/libshiraz-capture.so

.PHONY: macos-capture-api-smoke media-probe-apk
macos-capture-api-smoke: macos-framework macos-jvm-native
	uv run --no-project python scripts/java_framework_smoke.py DarwinCaptureConsentSmoke --marker DARWIN_CAPTURE_CONSENT_OK
	uv run --no-project python scripts/java_framework_smoke.py CameraJpegSmoke --marker CAMERA_JPEG_ROTATION_OK
	PYTHONPATH=src uv run --no-project python -m unittest discover -s tests -p test_darwin_app.py
media-probe-apk:
	PYTHONPATH=src uv run --no-project python scripts/build_media_probe.py

$(MACOS_OUT)/playback.exports: scripts/darwin_jni_exports.py java/shiraz/runtime/NativePlayback.java
	uv run --no-project python scripts/darwin_jni_exports.py java/shiraz/runtime/NativePlayback.java > $@
$(MACOS_OUT)/libshiraz-playback.so: native/darwin/playback.cpp include/shiraz/darwin/audio_clock.hpp java/shiraz/runtime/NativePlayback.java $(MACOS_OUT)/playback.exports
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -fPIC $(MACOS_DYLIB) -Wl,-exported_symbols_list,$(MACOS_OUT)/playback.exports -Wl,-dead_strip native/darwin/playback.cpp -o $@.fresh -framework AudioToolbox -framework CoreFoundation -pthread
	mv -f $@.fresh $@
macos-runtime-libs macos-jvm-native: $(MACOS_OUT)/libshiraz-playback.so

.PHONY: macos-media-jvm-native macos-playback-api-smoke
macos-media-jvm-native: $(MACOS_OUT)/libshiraz-playback.so $(MACOS_OUT)/libshiraz-capture.so
	mkdir -p $(MACOS_JVM_NATIVE)
	ln -sfn ../libshiraz-playback.so $(MACOS_JVM_NATIVE)/libshiraz-playback.dylib
	ln -sfn ../libshiraz-capture.so $(MACOS_JVM_NATIVE)/libshiraz-capture.dylib
$(MACOS_OUT)/extract-aac-fixture: tests/darwin/audio/extract_aac.cpp
	$(MACOS_CXX) -std=c++17 -O1 -Wall -Wextra -Werror $< -framework AudioToolbox -framework CoreFoundation -o $@
macos-playback-api-smoke: macos-framework macos-media-jvm-native $(MACOS_OUT)/extract-aac-fixture
	uv run --no-project python scripts/prepare_darwin_audio_fixture.py
	uv run --no-project python scripts/java_framework_smoke.py DarwinPlaybackSmoke --marker DARWIN_PLAYBACK_PCM_AAC_OK

$(CURDIR)/.local/lib/libvo-aacenc.a: scripts/bootstrap_capture_codec.py
	uv run --no-project python scripts/bootstrap_capture_codec.py

.PHONY: macos-aac-encode-api-smoke
macos-aac-encode-api-smoke: macos-framework macos-media-jvm-native
	uv run --no-project python scripts/java_framework_smoke.py DarwinAacEncodeSmoke --marker DARWIN_AAC_ENCODER_ROUNDTRIP_OK

.PHONY: macos-recorder-api-smoke
macos-recorder-api-smoke: macos-framework macos-media-jvm-native
	uv run --no-project python scripts/java_framework_smoke.py MediaRecorderStateSmoke --marker MEDIA_RECORDER_STATE_CONSENT_OK

.PHONY: macos-view-alpha-overshoot-smoke
macos-view-alpha-overshoot-smoke: macos-framework macos-jvm-native
	uv run --no-project python scripts/java_framework_smoke.py ViewAlphaOvershootSmoke --marker VIEW_ALPHA_OVERSHOOT_OK

.PHONY: macos-media-router-smoke
macos-media-router-smoke: macos-framework
	uv run --no-project python scripts/java_framework_smoke.py MediaRouterSmoke --marker MEDIA_ROUTER_LOCAL_OUTPUT_OK

.PHONY: macos-preferences-process-smoke
macos-preferences-process-smoke: macos-framework
	uv run --no-project python scripts/preferences_process_smoke.py

# Observe default MRS inputs using production assembly and a private TLS body.
$(MACOS_OUT)/m5/mrs-quarantine-tls.o: native/darwin/guest/tls.cpp $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror -Dshiraz_guest_tls_access=shiraz_test_actual_tls_access -c $< -o $@
$(MACOS_OUT)/m5/mrs-quarantine-probe: tests/darwin/guest/mrs_quarantine_probe.cpp tests/darwin/guest/mrs_quarantine_entry.S tests/darwin/guest/tpidr_probe.S $(MACOS_OUT)/m5/mrs-quarantine-tls.o $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/mrs_quarantine_probe.cpp tests/darwin/guest/mrs_quarantine_entry.S tests/darwin/guest/tpidr_probe.S $(MACOS_OUT)/m5/mrs-quarantine-tls.o $(filter-out native/darwin/guest/tls.cpp,$(MACOS_GUEST_SOURCES)) -o $@
.PHONY: macos-guest-mrs-quarantine-check
macos-guest-mrs-quarantine-check: macos-guest-unwind-fixture $(MACOS_OUT)/m5/mrs-quarantine-probe
	$(MACOS_OUT)/m5/mrs-quarantine-probe $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
	cp $(MACOS_OUT)/m5/mrs-quarantine-probe $(MACOS_OUT)/m5/mrs-quarantine-hardened.pending
	mv $(MACOS_OUT)/m5/mrs-quarantine-hardened.pending $(MACOS_OUT)/m5/mrs-quarantine-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/mrs-quarantine-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/mrs-quarantine-hardened
	$(MACOS_OUT)/m5/mrs-quarantine-hardened $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
$(MACOS_OUT)/m5/mrs-quarantine-tls-sanitized.o: native/darwin/guest/tls.cpp $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -Dshiraz_guest_tls_access=shiraz_test_actual_tls_access -c $< -o $@
$(MACOS_OUT)/m5/mrs-quarantine-sanitized: tests/darwin/guest/mrs_quarantine_probe.cpp tests/darwin/guest/mrs_quarantine_entry.S tests/darwin/guest/tpidr_probe.S $(MACOS_OUT)/m5/mrs-quarantine-tls-sanitized.o $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/mrs_quarantine_probe.cpp tests/darwin/guest/mrs_quarantine_entry.S tests/darwin/guest/tpidr_probe.S $(MACOS_OUT)/m5/mrs-quarantine-tls-sanitized.o $(filter-out native/darwin/guest/tls.cpp,$(MACOS_GUEST_SOURCES)) -o $@
.PHONY: macos-guest-mrs-quarantine-sanitize-check
macos-guest-mrs-quarantine-sanitize-check: macos-guest-unwind-fixture $(MACOS_OUT)/m5/mrs-quarantine-sanitized
	$(MACOS_OUT)/m5/mrs-quarantine-sanitized $(MACOS_GUEST_DEP_DIR)/libunwind-fixture.so
macos-guest-check: macos-guest-mrs-quarantine-check macos-guest-mrs-quarantine-sanitize-check

# ELF destructor ordering and admission: first-party DSOs, no application code.
$(MACOS_GUEST_DEP_DIR)/finalize-host.o: tests/darwin/guest/deps/finalize.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -DFINALIZE_LINK_HOST -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/finalize-dep.o: tests/darwin/guest/deps/finalize.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/finalize-root.o: tests/darwin/guest/deps/finalize.c
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -DFINALIZE_ROOT -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
.PHONY: macos-guest-finalize-fixture macos-guest-finalize-check macos-guest-finalize-sanitize-check
macos-guest-finalize-fixture: macos-guest-toolchain $(MACOS_GUEST_DEP_DIR)/finalize-host.o $(MACOS_GUEST_DEP_DIR)/finalize-dep.o $(MACOS_GUEST_DEP_DIR)/finalize-root.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libfinalize-host.so $(MACOS_GUEST_DEP_DIR)/finalize-host.o -o $(MACOS_GUEST_DEP_DIR)/libfinalize-host.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -fini guest_fini -soname libfinalize-dep.so $(MACOS_GUEST_DEP_DIR)/finalize-dep.o -L$(MACOS_GUEST_DEP_DIR) -lfinalize-host -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libfinalize-dep.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -fini guest_fini -soname libfinalize-root.so $(MACOS_GUEST_DEP_DIR)/finalize-root.o -L$(MACOS_GUEST_DEP_DIR) -lfinalize-dep -lfinalize-host -l:libc-link.so -o $(MACOS_GUEST_DEP_DIR)/libfinalize-root.so
$(MACOS_OUT)/m5/finalize-probe: tests/darwin/guest/finalize_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror tests/darwin/guest/finalize_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-finalize-check: macos-guest-finalize-fixture $(MACOS_OUT)/m5/finalize-probe
	$(MACOS_OUT)/m5/finalize-probe $(MACOS_GUEST_DEP_DIR)
	cp $(MACOS_OUT)/m5/finalize-probe $(MACOS_OUT)/m5/finalize-hardened.pending
	mv $(MACOS_OUT)/m5/finalize-hardened.pending $(MACOS_OUT)/m5/finalize-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/finalize-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/finalize-hardened
	$(MACOS_OUT)/m5/finalize-hardened $(MACOS_GUEST_DEP_DIR)
$(MACOS_OUT)/m5/finalize-sanitized: tests/darwin/guest/finalize_probe.cpp $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) -std=c++17 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined tests/darwin/guest/finalize_probe.cpp $(MACOS_GUEST_SOURCES) -o $@
macos-guest-finalize-sanitize-check: macos-guest-finalize-fixture $(MACOS_OUT)/m5/finalize-sanitized
	$(MACOS_OUT)/m5/finalize-sanitized $(MACOS_GUEST_DEP_DIR)
macos-guest-check: macos-guest-finalize-check macos-guest-finalize-sanitize-check

# Complete relocation evidence, including a valid mutable ABS64 pointer object.
.PHONY: macos-guest-relocation-evidence-check
macos-guest-relocation-evidence-check: macos-guest-fixture $(MACOS_OUT)/m5/osmand-link-probe scripts/verify_guest_relocation_fixture.py scripts/guest_binding_evidence.py
	mkdir -p $(MACOS_OUT)/m5/relocation-evidence
	cp $(MACOS_OUT)/m5/fixture.so $(MACOS_OUT)/m5/relocation-evidence/libshiraz_fixture.so
	$(MACOS_OUT)/m5/osmand-link-probe $(MACOS_OUT)/m5/relocation-evidence --mutable-relocation-report $(MACOS_OUT)/m5/relocation-evidence/ordinary.json
	uv run --no-project python scripts/verify_guest_relocation_fixture.py $(MACOS_OUT)/m5/relocation-evidence/libshiraz_fixture.so $(MACOS_OUT)/m5/relocation-evidence/ordinary.json
	cp $(MACOS_OUT)/m5/osmand-link-probe $(MACOS_OUT)/m5/relocation-evidence/hardened.pending
	mv $(MACOS_OUT)/m5/relocation-evidence/hardened.pending $(MACOS_OUT)/m5/relocation-evidence/hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/relocation-evidence/hardened
	codesign --verify --strict $(MACOS_OUT)/m5/relocation-evidence/hardened
	$(MACOS_OUT)/m5/relocation-evidence/hardened $(MACOS_OUT)/m5/relocation-evidence --mutable-relocation-report $(MACOS_OUT)/m5/relocation-evidence/hardened.json
	uv run --no-project python scripts/verify_guest_relocation_fixture.py $(MACOS_OUT)/m5/relocation-evidence/libshiraz_fixture.so $(MACOS_OUT)/m5/relocation-evidence/hardened.json
macos-guest-check: macos-guest-relocation-evidence-check

# C++ thread-local callback namespace retention; each rejection uses a fresh process.
.PHONY: macos-guest-tls-retention-check
$(MACOS_GUEST_DEP_DIR)/tls-retention.o: tests/darwin/guest/deps/tls_retention.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libtls-retention.so: $(MACOS_GUEST_DEP_DIR)/tls-retention.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libtls-retention.so $< -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $@
$(MACOS_OUT)/m5/tls-retention-probe: tests/darwin/guest/tls_retention_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror $< $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-tls-retention-check: $(MACOS_GUEST_DEP_DIR)/libtls-retention.so $(MACOS_OUT)/m5/tls-retention-probe
	@for mode in joined pending active validation same-thread unmap; do $(MACOS_OUT)/m5/tls-retention-probe $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
	cp $(MACOS_OUT)/m5/tls-retention-probe $(MACOS_OUT)/m5/tls-retention-hardened.pending
	mv $(MACOS_OUT)/m5/tls-retention-hardened.pending $(MACOS_OUT)/m5/tls-retention-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/tls-retention-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/tls-retention-hardened
	@for mode in joined pending active validation same-thread unmap; do $(MACOS_OUT)/m5/tls-retention-hardened $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
macos-guest-check: macos-guest-tls-retention-check
.PHONY: macos-guest-key-retention-check
$(MACOS_GUEST_DEP_DIR)/key-retention.o: tests/darwin/guest/deps/key_retention.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libkey-retention.so: $(MACOS_GUEST_DEP_DIR)/key-retention.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libkey-retention.so $< -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $@
$(MACOS_OUT)/m5/key-retention-probe: tests/darwin/guest/key_retention_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror $< $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-key-retention-check: $(MACOS_GUEST_DEP_DIR)/libkey-retention.so $(MACOS_OUT)/m5/key-retention-probe
	@for mode in joined pending same-thread clear delete active-delete exhaustion multi active-delete-release; do $(MACOS_OUT)/m5/key-retention-probe $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
	cp $(MACOS_OUT)/m5/key-retention-probe $(MACOS_OUT)/m5/key-retention-hardened.pending
	mv $(MACOS_OUT)/m5/key-retention-hardened.pending $(MACOS_OUT)/m5/key-retention-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/key-retention-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/key-retention-hardened
	@for mode in joined pending same-thread clear delete active-delete exhaustion multi active-delete-release; do $(MACOS_OUT)/m5/key-retention-hardened $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
macos-guest-check: macos-guest-key-retention-check

$(MACOS_OUT)/m5/namespace-lifetime-probe: tests/darwin/guest/namespace_lifetime_probe.cpp
	mkdir -p $(dir $@)
	$(CXX) -I$(MACOS_GUEST_ART)/system/core/libnativebridge/include -I$(MACOS_GUEST_ART)/libnativehelper/include/nativehelper -std=c++17 -O2 -g -Wall -Wextra -Werror $< -o $@
.PHONY: macos-guest-namespace-lifetime-check
macos-guest-namespace-lifetime-check: $(MACOS_OUT)/m5/namespace-lifetime-probe $(MACOS_OUT)/libshiraz-guest.so $(MACOS_GUEST_DEP_DIR)/libkey-retention.so
	$(MACOS_OUT)/m5/namespace-lifetime-probe $(abspath $(MACOS_OUT)/libshiraz-guest.so) $(abspath $(MACOS_GUEST_DEP_DIR)/libkey-retention.so)
macos-guest-check: macos-guest-namespace-lifetime-check
.PHONY: macos-guest-thread-retention-check
$(MACOS_GUEST_DEP_DIR)/thread-retention.o: tests/darwin/guest/deps/thread_retention.c $(MACOS_OUT)/m5/guest_io.hpp
	mkdir -p $(dir $@)
	xcrun clang --target=aarch64-linux-android24 -mno-outline-atomics -ffreestanding -fPIC -fno-stack-protector -O2 -Wall -Wextra -Werror -c $< -o $@
$(MACOS_GUEST_DEP_DIR)/libthread-retention.so: $(MACOS_GUEST_DEP_DIR)/thread-retention.o $(MACOS_GUEST_DEP_DIR)/libc-link.so
	$(MACOS_GUEST_LD) $(MACOS_GUEST_LD_FLAGS) --no-undefined -soname libthread-retention.so $< -L$(MACOS_GUEST_DEP_DIR) -l:libc-link.so -o $@
$(MACOS_OUT)/m5/thread-retention-probe: tests/darwin/guest/thread_retention_probe.cpp $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_IO_HEADERS) $(MACOS_GUEST_SOURCES) $(MACOS_GUEST_HEADERS)
	mkdir -p $(dir $@)
	$(CXX) $(MACOS_GUEST_CPPFLAGS) $(MACOS_GUEST_ICU_CFLAGS) -std=c++17 -O2 -g -Wall -Wextra -Werror $< $(MACOS_GUEST_IO_SOURCES) $(MACOS_GUEST_SOURCES) -lz $(MACOS_GUEST_ICU_LIBS) -o $@
macos-guest-thread-retention-check: $(MACOS_GUEST_DEP_DIR)/libthread-retention.so $(MACOS_OUT)/m5/thread-retention-probe
	@for mode in joined active validation; do $(MACOS_OUT)/m5/thread-retention-probe $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
	cp $(MACOS_OUT)/m5/thread-retention-probe $(MACOS_OUT)/m5/thread-retention-hardened.pending
	mv $(MACOS_OUT)/m5/thread-retention-hardened.pending $(MACOS_OUT)/m5/thread-retention-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/thread-retention-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/thread-retention-hardened
	@for mode in joined active validation; do $(MACOS_OUT)/m5/thread-retention-hardened $(MACOS_GUEST_DEP_DIR) $$mode || exit $$?; done
macos-guest-check: macos-guest-thread-retention-check
.PHONY: macos-guest-native-mapping-check
$(MACOS_OUT)/m5/native-mapping-probe: tests/darwin/guest/native_mapping_probe.cpp native/darwin/guest/native_mapping.hpp
	mkdir -p $(dir $@)
	$(CXX) -iquote . -std=c++17 -O2 -g -Wall -Wextra -Werror $< -o $@
macos-guest-native-mapping-check: $(MACOS_OUT)/m5/native-mapping-probe
	$(MACOS_OUT)/m5/native-mapping-probe
	cp $(MACOS_OUT)/m5/native-mapping-probe $(MACOS_OUT)/m5/native-mapping-hardened.pending
	mv $(MACOS_OUT)/m5/native-mapping-hardened.pending $(MACOS_OUT)/m5/native-mapping-hardened
	codesign --force --sign - --options runtime --entitlements tests/darwin/guest/jit.entitlements $(MACOS_OUT)/m5/native-mapping-hardened
	codesign --verify --strict $(MACOS_OUT)/m5/native-mapping-hardened
	$(MACOS_OUT)/m5/native-mapping-hardened
macos-guest-check: macos-guest-native-mapping-check

$(MACOS_OUT)/libshiraz-guest.so: native/darwin/guest/native_mapping.hpp

.PHONY: macos-guest-mrs-accessor-code-check
macos-guest-mrs-accessor-code-check: $(MACOS_OUT)/m5/tpidr-thunk-probe $(MACOS_OUT)/libshiraz-guest.so
	uv run --no-project python scripts/check_guest_mrs_accessor.py $(MACOS_OUT)/m5/tpidr-thunk-probe
	uv run --no-project python scripts/check_guest_mrs_accessor.py $(MACOS_OUT)/libshiraz-guest.so
macos-guest-check: macos-guest-mrs-accessor-code-check

# NDK bitmap ABI smoke on darwin ART (Mach-O libraries keep Android .so names).
$(MACOS_OUT)/libjnigraphics.so: native/android_bitmap.cpp include/shiraz/android_bitmap.h
	@mkdir -p $(MACOS_OUT)
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) native/android_bitmap.cpp -o $@
$(MACOS_OUT)/libshiraz-test-android-bitmap.so: tests/android_bitmap.cpp include/shiraz/android_bitmap.h $(MACOS_OUT)/libjnigraphics.so
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) tests/android_bitmap.cpp $(MACOS_OUT)/libjnigraphics.so $(MACOS_RPATH) -o $@
.PHONY: macos-android-bitmap-smoke
macos-android-bitmap-smoke: macos-framework $(MACOS_OUT)/libshiraz-test-android-bitmap.so
	python3 scripts/android_bitmap_smoke.py
