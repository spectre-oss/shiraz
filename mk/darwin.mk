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
MACOS_TESTS := test-compositor test-pointer test-keyboard test-archive-path test-native-window test-shared-frame test-choreographer test-sensors test-shared-memory test-image-reader test-hardware-buffer test-system-fonts test-media-crypto test-media-ndk test-ipc-channel test-sync-fence
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
