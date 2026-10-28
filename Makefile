SHELL := /bin/sh
.DEFAULT_GOAL := all
CXX ?= c++
JAVA ?= java
JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(command -v javac))))
JNI_CFLAGS = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CPPFLAGS += -Iinclude -Ibuild
CXXFLAGS ?= -O2 -g
CXXFLAGS += -std=c++17 -Wall -Wextra -Wpedantic -Werror
export MSG
export PYTHONPATH := $(CURDIR)/src
export PKG_CONFIG_PATH := $(CURDIR)/.local/lib/pkgconfig:$(CURDIR)/.local/lib64/pkgconfig:$(PKG_CONFIG_PATH)
SDL_LOCAL_LIBRARY := $(wildcard .local/lib/libSDL2.a) build/libshiraz-gpu.so
SDL_CFLAGS = $(shell .local/bin/sdl2-config --cflags 2>/dev/null || pkg-config --cflags sdl2 2>/dev/null)
SDL_LIBS = $(shell .local/bin/sdl2-config --libs 2>/dev/null || pkg-config --static --libs sdl2 2>/dev/null) -Lbuild -lshiraz-gpu -Wl,-rpath,'$$ORIGIN'

.PHONY: framework apk-fixture apk-smoke runtime-aot-smoke runtime-jni-smoke jni-smoke runtime-smoke runtime-sources runtime-bridges runtime-art commit all build test check hooks version inspect plan run preview smoke clean deps context
commit:
	python3 scripts/commit.py

all: build
build: build/libshiraz-host.so build/shiraz-preview build/libandroid.so build/libandroid.so.0 build/libnativewindow.so build/libEGL.so.1 build/libshiraz-jni.so build/shiraz-framework.jar
build/libandroid.so.0: build/libandroid.so
	ln -sfn libandroid.so $@
build/libnativewindow.so: build/libandroid.so
	ln -sfn libandroid.so $@
build/libEGL.so.1: build/libEGL_shim.so
	ln -sfn libEGL_shim.so $@
build/version.hpp: VERSION
	@mkdir -p build
	@python3 -c 'from pathlib import Path; import json; v=Path("VERSION").read_text().strip(); Path("build/version.hpp").write_text("#define SHIRAZ_VERSION " + json.dumps(v) + "\n")'
build/shiraz-preview: native/main.cpp native/presentation.cpp include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/live_input.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp include/shiraz/native_window.h build/version.hpp build/libandroid.so $(SDL_LOCAL_LIBRARY)
	@pkg-config --exists sdl2 || { echo 'SDL2 missing. Run make deps (local source build), or install SDL2 development files.'; exit 1; }
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) native/main.cpp native/presentation.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN' $(SDL_LIBS)
build/test-compositor: tests/compositor.cpp include/shiraz/compositor.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/compositor.cpp -o $@
build/libEGL_shim.so: native/egl_shim.cpp include/shiraz/native_window.h include/shiraz/hardware_buffer.h build/libandroid.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/egl_shim.cpp -o $@ -Lbuild -landroid -lGLESv2 -ldl -Wl,-rpath,'$$ORIGIN'
build/test-egl-window: tests/egl_window.cpp build/libandroid.so build/libEGL_shim.so include/shiraz/native_window.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/egl_window.cpp -o $@ -Lbuild -landroid -lEGL_shim -lGLESv2 -Wl,-rpath,'$$ORIGIN' -pthread
build/libandroid.so: native/native_window.cpp native/shared_frame.cpp native/from_surface.cpp native/surface_texture.cpp native/shared_memory.cpp native/hardware_buffer.cpp native/sync_fence.cpp native/image_reader.cpp native/media_crypto.cpp native/media_ndk.cpp native/system_fonts.cpp native/looper.cpp native/choreographer.cpp native/sensors.cpp include/shiraz/shared_frame.hpp include/shiraz/native_window.h include/shiraz/hardware_buffer.h include/shiraz/window_frames.hpp include/shiraz/image_reader.h include/shiraz/media_crypto.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/native_window.cpp native/shared_frame.cpp native/from_surface.cpp native/surface_texture.cpp native/shared_memory.cpp native/hardware_buffer.cpp native/sync_fence.cpp native/image_reader.cpp native/media_crypto.cpp native/media_ndk.cpp native/system_fonts.cpp native/looper.cpp native/choreographer.cpp native/sensors.cpp -o $@ -pthread -ldl -lgbm -lGLESv2 $(shell pkg-config --cflags --libs fontconfig)

build/test-surface-lifetime: tests/native/surface_lifetime.cpp native/from_surface.cpp native/native_window.cpp native/shared_frame.cpp include/shiraz/native_window.h include/shiraz/shared_frame.hpp include/shiraz/window_frames.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) tests/native/surface_lifetime.cpp native/from_surface.cpp native/native_window.cpp native/shared_frame.cpp -o $@ -pthread -ldl -Wl,--wrap=dlopen,--wrap=dlsym,--wrap=dlclose

build/test-native-window: tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp include/shiraz/shared_frame.hpp include/shiraz/window_frames.hpp include/shiraz/native_window.h include/shiraz/window_snapshot.hpp include/shiraz/compositor.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/native_window.cpp native/native_window.cpp native/shared_frame.cpp -o $@ -pthread

build/test-archive-path: tests/native/archive_path.cpp include/shiraz/archive_path.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/native/archive_path.cpp -o $@

test: build/test-surface-lifetime build/test-archive-path build/test-shared-memory build/test-compositor build/test-native-window build/test-pointer build/test-audio build/test-egl-window build/shiraz-app build/libshiraz-android-process.so build/libshiraz-android-fonts.so gles-smoke
	./build/test-surface-lifetime
	./build/test-archive-path
	./build/test-shared-memory
	./build/test-compositor
	./build/test-native-window
	./build/test-pointer
	./build/test-keyboard
	./build/test-audio
	./build/test-egl-window
	python3 -m unittest discover -s tests -v
check: test
	python3 scripts/version.py --check
	python3 scripts/commit_guard.py
hooks:
	git config --local core.hooksPath .githooks
version:
	python3 -m shiraz --version
inspect:
	python3 -m shiraz inspect "$(APK)"
plan:
	python3 -m shiraz plan "$(APK)" --output build/app-plan.json
run:
	python3 -m shiraz run "$(APK)"
preview: build
	./build/shiraz-preview
smoke: build
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software ./build/shiraz-preview --frames 2 --snapshot build/preview.ppm
	./build/shiraz-preview --version
deps:
	python3 scripts/bootstrap_sdl.py
context:
	@test -d context/osmand/.git || git clone --depth 1 --filter=blob:none https://github.com/osmandapp/OsmAnd.git context/osmand
clean:
	rm -rf build

runtime-sources:
	python3 scripts/bootstrap_runtime.py sources
runtime-bridges:
	python3 scripts/bootstrap_runtime.py bridges
runtime-art:
	python3 scripts/bootstrap_runtime.py art

runtime-smoke:
	python3 scripts/art_smoke.py

build/libshiraz-jni.so: include/shiraz/window_snapshot.hpp native/jni_surface.cpp native/presentation.cpp include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/live_input.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp include/shiraz/native_window.h build/version.hpp build/libandroid.so $(SDL_LOCAL_LIBRARY)
	@pkg-config --exists sdl2
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/jni_surface.cpp native/presentation.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN' $(SDL_LIBS)
jni-smoke: framework build/libshiraz-jni.so build/libshiraz-test-input.so
	mkdir -p build/jni-smoke/classes
	javac --release 8 -cp build/framework/classes -d build/jni-smoke/classes java/shiraz/runtime/NativeSurface.java tests/java/shiraz/NativeSurfaceSmoke.java
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software java -Xcheck:jni -Djava.library.path=build -cp build/jni-smoke/classes:build/framework/classes shiraz.NativeSurfaceSmoke

runtime-jni-smoke: framework build/libshiraz-jni.so build/libshiraz-test-input.so
	python3 scripts/art_smoke.py --program surface

.PHONY: runtime-relocation-smoke
runtime-relocation-smoke: build/runtime-network.stamp
	python3 scripts/art_relocation_smoke.py

runtime-aot-smoke: framework build/libshiraz-jni.so build/libshiraz-test-input.so
	python3 scripts/art_smoke.py --aot
	python3 scripts/art_smoke.py --program surface --aot

framework: build/shiraz-framework.jar
build/shiraz-framework.jar: $(shell rg --files java -g '*.java') scripts/build_framework.py VERSION
	python3 scripts/build_framework.py
apk-fixture: build/apk-fixture/fixture.apk
build/apk-fixture/fixture.apk: $(shell rg --files tests/apk) scripts/build_apk_fixture.py scripts/fixture_images.py
	python3 scripts/build_apk_fixture.py
apk-smoke: build framework apk-fixture
	python3 scripts/apk_smoke.py

.PHONY: presentation-smoke
build/test-presentation: tests/presentation.cpp native/presentation.cpp include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/live_input.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp $(SDL_LOCAL_LIBRARY)
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/presentation.cpp native/presentation.cpp -o $@ $(SDL_LIBS)
presentation-smoke: build/test-presentation
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software timeout 10 ./build/test-presentation
	env -u SDL_VIDEODRIVER -u SDL_RENDER_DRIVER SHIRAZ_HOST_SDL_VIDEODRIVER=dummy SHIRAZ_HOST_SDL_RENDER_DRIVER=software timeout 10 ./build/test-presentation

build/libshiraz-host.so: native/host_process.cpp native/fds.cpp native/gles_version.cpp native/ipc_channel.cpp native/ipc_jni.cpp native/ipc_spawn.cpp include/shiraz/ipc_channel.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/host_process.cpp native/fds.cpp native/gles_version.cpp native/ipc_channel.cpp native/ipc_jni.cpp native/ipc_spawn.cpp -o $@ -lEGL -ldl

.PHONY: runtime-resources
runtime-resources:
	python3 scripts/bootstrap_runtime.py resources

ART_HOST := context/art_standalone/out/host/linux-x86
build/libshiraz-resources.so: native/resources.cpp native/resource_colors.hpp include/shiraz/archive_path.hpp $(ART_HOST)/lib64/libandroidfw.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -isystem context/art_standalone/libandroidfw/include -fPIC -shared -Wl,-z,defs native/resources.cpp -o $@ -L$(ART_HOST)/lib64 -landroidfw -pthread
build/runtime-resources.stamp: scripts/bootstrap_runtime.py patches/art-resource-lifetime.patch patches/art-resource-size-configurations.patch patches/art-resource-identifier-status.patch patches/art-resource-private-attribute-io.patch
	@mkdir -p build
	$(MAKE) runtime-resources
	@test -f $(ART_HOST)/lib64/libandroidfw.so
	@touch $@
$(ART_HOST)/lib64/libandroidfw.so: | build/runtime-resources.stamp
	@test -f $@ || $(MAKE) runtime-resources
build: build/libshiraz-resources.so

.PHONY: resource-smoke
resource-smoke: build framework apk-fixture
	python3 scripts/resource_smoke.py

build/test-pointer: tests/pointer.cpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/pointer.cpp -o $@

build/libshiraz-test-input.so: tests/native/input_injector.cpp build/libshiraz-jni.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(SDL_CFLAGS) -fPIC -shared -Wl,-z,defs $< -o $@ -Lbuild -lshiraz-jni -Wl,-rpath,'$$ORIGIN' $(SDL_LIBS)
apk-smoke: build/libshiraz-test-input.so

TEXT_CFLAGS = $(shell pkg-config --cflags freetype2 harfbuzz fontconfig icu-uc 2>/dev/null)
TEXT_LIBS = $(shell pkg-config --libs freetype2 harfbuzz fontconfig icu-uc 2>/dev/null)
.PHONY: fonts
fonts: scripts/bootstrap_fonts.py
	python3 scripts/bootstrap_fonts.py
	@mkdir -p build

build/libshiraz-text.so: native/text.cpp include/shiraz/compositor.hpp | fonts
	@pkg-config --exists freetype2 harfbuzz fontconfig icu-uc || { echo 'Text rendering requires FreeType, HarfBuzz and Fontconfig development files.'; exit 1; }
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(TEXT_CFLAGS) -DSHIRAZ_FONT_DIR='"$(CURDIR)/.local/share/shiraz/fonts"' -fPIC -shared -Wl,-z,defs native/text.cpp -o $@ $(TEXT_LIBS) -pthread
build: build/libshiraz-text.so

.PHONY: text-smoke
text-smoke: build framework
	python3 scripts/text_smoke.py

.PHONY: preferences-smoke
preferences-smoke: build framework
	mkdir -p build/preferences-smoke
	javac --release 8 -cp build/framework/classes -d build/preferences-smoke tests/java/PreferencesSmoke.java
	java -Xcheck:jni -Djava.library.path=build -cp build/preferences-smoke:build/framework/classes:context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar PreferencesSmoke

.PHONY: storage-smoke
gles-smoke: framework build/libshiraz-host.so
	mkdir -p build/gles-smoke
	javac --release 8 -cp build/framework/classes -d build/gles-smoke tests/java/GlesSmoke.java
	java -Xcheck:jni -Djava.library.path=build -cp build/gles-smoke:build/framework/classes GlesSmoke
storage-smoke: framework build/libshiraz-host.so
	mkdir -p build/storage-smoke
	javac --release 8 -cp build/framework/classes -d build/storage-smoke tests/java/StorageSmoke.java
	java -Xcheck:jni -Djava.library.path=build -cp build/storage-smoke:build/framework/classes StorageSmoke
check: storage-smoke

.PHONY: alert-dialog-builder-smoke
alert-dialog-builder-smoke: framework build/libshiraz-host.so build/libshiraz-image.so
	mkdir -p build/alert-dialog-builder-smoke
	javac --release 8 -cp build/framework/classes -d build/alert-dialog-builder-smoke tests/java/AlertDialogBuilderSmoke.java
	$(JAVA) -Djava.library.path=build -Dshiraz.frameworkResources=build/framework-res/framework-res.apk -cp build/alert-dialog-builder-smoke:build/framework/classes:context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar AlertDialogBuilderSmoke
check: alert-dialog-builder-smoke

.PHONY: guest-pthread-attributes-smoke
.PHONY: guest-thread-names-smoke
build/test-guest-thread-names: tests/native/guest_thread_names.cpp native/digitalis_guest_tls.cpp native/guest_stack_environment.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -Inative tests/native/guest_thread_names.cpp native/digitalis_guest_tls.cpp -o $@ -pthread
guest-thread-names-smoke: build/test-guest-thread-names
	./build/test-guest-thread-names
check: guest-thread-names-smoke

build/test-guest-pthread-attributes: tests/native/guest_pthread_attributes.cpp native/guest_pthread_attributes.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -Inative $< -o $@ -pthread
guest-pthread-attributes-smoke: build/test-guest-pthread-attributes
	./build/test-guest-pthread-attributes
check: guest-pthread-attributes-smoke

.PHONY: digitalis-thread-smoke
digitalis-thread-smoke:
	python3 scripts/digitalis/thread_smoke.py $(DIGITALIS_THREAD_OPTIONS)

.PHONY: framework-resources
framework-resources: build/framework-res/framework-res.apk
build/framework-res/framework-res.apk: scripts/bootstrap_framework_resources.py src/shiraz/framework_resources.py scripts/build_apk_fixture.py
	python3 scripts/bootstrap_framework_resources.py
build: build/framework-res/framework-res.apk

AUDIO_CFLAGS = $(shell pkg-config --cflags sndfile 2>/dev/null)
AUDIO_LIBS = $(shell pkg-config --libs sndfile 2>/dev/null)
build/test-audio: tests/audio.cpp native/audio.cpp include/shiraz/audio.hpp
	@pkg-config --exists sndfile || { echo 'Sound decoding requires libsndfile development files.'; exit 1; }
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(AUDIO_CFLAGS) tests/audio.cpp native/audio.cpp -o $@ $(AUDIO_LIBS)

AUDIO_OUTPUT_CFLAGS = $(shell pkg-config --cflags alsa sndfile 2>/dev/null)
AUDIO_OUTPUT_LIBS = $(shell pkg-config --libs alsa sndfile 2>/dev/null)
build/libshiraz-audio.so: native/audio.cpp native/audio_output.cpp native/audio_jni.cpp include/shiraz/audio.hpp include/shiraz/audio_output.hpp
	@pkg-config --exists alsa sndfile || { echo 'Audio requires ALSA and libsndfile development files.'; exit 1; }
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(AUDIO_OUTPUT_CFLAGS) -fPIC -shared -Wl,-z,defs native/audio.cpp native/audio_output.cpp native/audio_jni.cpp -o $@ $(AUDIO_OUTPUT_LIBS) -pthread
build: build/libshiraz-audio.so

.PHONY: audio-output-smoke
build/test-audio-output: tests/audio_output.cpp native/audio.cpp native/audio_output.cpp include/shiraz/audio.hpp include/shiraz/audio_output.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(AUDIO_OUTPUT_CFLAGS) tests/audio_output.cpp native/audio.cpp native/audio_output.cpp -o $@ $(AUDIO_OUTPUT_LIBS) -pthread
audio-output-smoke: build/test-audio-output
	python3 scripts/audio_output_smoke.py

.PHONY: sensor-smoke
sensor-smoke: framework
	python3 scripts/sensor_smoke.py

.PHONY: runtime-xml
runtime-xml:
	python3 scripts/bootstrap_runtime.py xml
build/runtime-xml.stamp: scripts/bootstrap_runtime.py scripts/bootstrap_boot.py patches/art-xml-entity-limits.patch build/runtime-resources.stamp
	@mkdir -p build
	$(MAKE) runtime-xml
	@touch $@
build: build/runtime-xml.stamp
build/shiraz-framework.jar: build/runtime-xml.stamp

.PHONY: xml-smoke
xml-smoke: framework
	python3 scripts/xml_smoke.py

SQLITE_CFLAGS = $(shell pkg-config --cflags sqlite3 2>/dev/null)
SQLITE_LIBS = $(shell pkg-config --libs sqlite3 2>/dev/null)
build/libshiraz-sqlite.so: native/sqlite.cpp
	@pkg-config --exists sqlite3 || { echo 'Database support requires SQLite development files.'; exit 1; }
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(SQLITE_CFLAGS) -fPIC -shared -Wl,-z,defs $< -o $@ $(SQLITE_LIBS) -pthread
build: build/libshiraz-sqlite.so

.PHONY: sqlite-smoke
sqlite-smoke: build framework
	python3 scripts/sqlite_smoke.py

.PHONY: sqlite-lifetime-smoke
sqlite-lifetime-smoke: build framework
	python3 scripts/sqlite_lifetime_smoke.py

.PHONY: boot-smoke
boot-smoke: framework
	python3 scripts/boot_smoke.py

NOTIFICATION_CFLAGS = $(shell pkg-config --cflags dbus-1 2>/dev/null)
NOTIFICATION_LIBS = $(shell pkg-config --libs dbus-1 2>/dev/null)
build/libshiraz-notifications.so: native/notifications.cpp
	@pkg-config --exists dbus-1 || { echo 'Desktop notifications require libdbus development files.'; exit 1; }
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(NOTIFICATION_CFLAGS) -fPIC -shared -Wl,-z,defs native/notifications.cpp -o $@ $(NOTIFICATION_LIBS) -pthread
build: build/libshiraz-notifications.so

.PHONY: notification-smoke
notification-smoke: build framework
	python3 scripts/notification_smoke.py

.PHONY: notification-policy-smoke
notification-policy-smoke: build framework
	python3 scripts/notification_policy_smoke.py

.PHONY: broadcast-smoke
broadcast-smoke: build framework
	python3 scripts/broadcast_smoke.py

build/libshiraz-bitmap.so: native/bitmap.cpp include/shiraz/color_filter.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/bitmap.cpp -o $@
build: build/libshiraz-bitmap.so

.PHONY: bitmap-smoke
bitmap-smoke: build framework
	python3 scripts/bitmap_smoke.py

build/test-keyboard: tests/keyboard.cpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/keyboard.cpp -o $@
test: build/test-keyboard

.PHONY: keyboard-smoke
keyboard-smoke: build framework framework-resources
	python3 scripts/keyboard_smoke.py

.PHONY: lru-smoke
lru-smoke: build framework
	python3 scripts/lru_smoke.py

.PHONY: drawable-smoke
drawable-smoke: build framework
	python3 scripts/drawable_smoke.py

.PHONY: imageview-smoke
imageview-smoke: build framework
	python3 scripts/imageview_smoke.py

.PHONY: framelayout-smoke
framelayout-smoke: build framework
	python3 scripts/framelayout_smoke.py

.PHONY: color-filter-smoke
color-filter-smoke: build framework
	python3 scripts/color_filter_smoke.py

.PHONY: typed-resource-smoke
typed-resource-smoke: build framework apk-fixture framework-resources
	python3 scripts/typed_resource_smoke.py

IMAGE_CFLAGS = $(shell pkg-config --cflags libpng zlib libwebp libjpeg 2>/dev/null)
IMAGE_LIBS = $(shell pkg-config --libs libpng zlib libwebp libjpeg 2>/dev/null)
build/libshiraz-image.so: native/image.cpp
	@mkdir -p build
	@pkg-config --exists libpng zlib libwebp libjpeg || { echo 'libpng/zlib/libwebp/libjpeg development files are required'; exit 1; }
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(IMAGE_CFLAGS) -fPIC -shared -Wl,-z,defs $< -o $@ $(IMAGE_LIBS)
build: build/libshiraz-image.so

.PHONY: png-smoke
png-smoke: build framework apk-fixture framework-resources
	python3 scripts/png_smoke.py

PATH_CFLAGS = $(shell pkg-config --cflags cairo 2>/dev/null)
PATH_LIBS = $(shell pkg-config --libs cairo 2>/dev/null)
build/libshiraz-path.so: native/path.cpp native/ripple.cpp include/shiraz/compositor.hpp
	@mkdir -p build
	@pkg-config --exists cairo || { echo 'Cairo development files are required'; exit 1; }
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) $(PATH_CFLAGS) -fPIC -shared -Wl,-z,defs native/path.cpp native/ripple.cpp -o $@ $(PATH_LIBS)
build: build/libshiraz-path.so

.PHONY: path-smoke
path-smoke: build framework
	python3 scripts/path_smoke.py

.PHONY: vector-smoke
vector-smoke: build framework apk-fixture framework-resources
	python3 scripts/vector_smoke.py

.PHONY: layer-smoke
layer-smoke: build framework apk-fixture framework-resources
	python3 scripts/layer_smoke.py

.PHONY: dash-smoke
dash-smoke: build framework
	python3 scripts/dash_smoke.py

.PHONY: rounded-smoke
rounded-smoke: build framework
	python3 scripts/rounded_smoke.py

.PHONY: pathmeasure-smoke
pathmeasure-smoke: build framework
	python3 scripts/pathmeasure_smoke.py

.PHONY: colormatrix-smoke
colormatrix-smoke: build framework
	python3 scripts/colormatrix_smoke.py

.PHONY: layout-listener-smoke
layout-listener-smoke: build framework
	python3 scripts/layout_listener_smoke.py

.PHONY: system-resources-smoke
system-resources-smoke: build framework apk-fixture framework-resources
	python3 scripts/system_resources_smoke.py

.PHONY: ble-values-smoke
ble-values-smoke: build framework
	python3 scripts/ble_values_smoke.py

.PHONY: tree-observer-smoke
tree-observer-smoke: build framework
	python3 scripts/tree_observer_smoke.py

.PHONY: compiled-xml-smoke
compiled-xml-smoke: build framework apk-fixture framework-resources
	python3 scripts/compiled_xml_smoke.py

.PHONY: inflater-smoke
inflater-smoke: build framework apk-fixture framework-resources
	python3 scripts/inflater_smoke.py

.PHONY: component-smoke
component-smoke: build framework framework-resources
	python3 scripts/component_smoke.py

.PHONY: menu-smoke
menu-smoke: build framework framework-resources
	python3 scripts/menu_smoke.py

.PHONY: build-identity-smoke
build-identity-smoke: framework
	python3 scripts/build_identity_smoke.py

.PHONY: dialog-smoke
dialog-smoke: build framework framework-resources build/libshiraz-test-input.so
	python3 scripts/dialog_smoke.py

.PHONY: service-smoke
service-smoke: build framework
	python3 scripts/service_smoke.py

.PHONY: service-metadata-smoke
service-metadata-smoke: build framework apk-fixture framework-resources
	python3 scripts/service_metadata_smoke.py

.PHONY: configuration-context-smoke
configuration-context-smoke: build framework apk-fixture framework-resources
	python3 scripts/configuration_context_smoke.py

.PHONY: resource-resize-smoke
resource-resize-smoke: build framework apk-fixture framework-resources
	python3 scripts/resource_resize_smoke.py

.PHONY: activity-policy-smoke
activity-policy-smoke: framework
	python3 scripts/activity_policy_smoke.py

.PHONY: saved-state-smoke
saved-state-smoke: build framework apk-fixture framework-resources
	python3 scripts/saved_state_smoke.py

.PHONY: activity-lifecycle-smoke
activity-lifecycle-smoke: framework apk-fixture
	python3 scripts/activity_lifecycle_smoke.py

.PHONY: activity-resize-smoke
activity-resize-smoke: build framework apk-fixture framework-resources build/libshiraz-test-input.so
	python3 scripts/activity_resize_smoke.py

.PHONY: orientation-smoke
orientation-smoke: framework
	python3 scripts/orientation_smoke.py

.PHONY: window-smoke
window-smoke: build framework apk-fixture framework-resources
	python3 scripts/window_smoke.py

.PHONY: system-ui-smoke
system-ui-smoke: build framework apk-fixture framework-resources
	python3 scripts/system_ui_smoke.py

.PHONY: options-menu-smoke
options-menu-smoke: framework apk-fixture
	python3 scripts/options_menu_smoke.py

.PHONY: styled-attributes-smoke
styled-attributes-smoke: service-metadata-smoke

.PHONY: fragment-smoke
fragment-smoke: activity-policy-smoke activity-resize-smoke

.PHONY: linearlayout-smoke
linearlayout-smoke: build framework
	python3 scripts/linearlayout_smoke.py

.PHONY: attach-smoke
attach-smoke: build framework
	python3 scripts/attach_smoke.py

.PHONY: insets-smoke
insets-smoke: build framework
	python3 scripts/insets_smoke.py

.PHONY: textview-smoke
textview-smoke: build framework
	python3 scripts/textview_smoke.py

.PHONY: title-smoke
title-smoke: build framework build/libshiraz-test-input.so
	python3 scripts/title_smoke.py

.PHONY: animation-smoke
animation-smoke: build framework
	python3 scripts/animation_smoke.py

.PHONY: accessibility-smoke
accessibility-smoke: build framework
	python3 scripts/accessibility_smoke.py

.PHONY: interpolator-smoke
interpolator-smoke: build framework apk-fixture framework-resources
	python3 scripts/interpolator_smoke.py

.PHONY: view-configuration-smoke
view-configuration-smoke: build framework apk-fixture framework-resources
	python3 scripts/view_configuration_smoke.py

.PHONY: scroller-smoke
scroller-smoke: build framework apk-fixture framework-resources
	python3 scripts/scroller_smoke.py

.PHONY: multitouch-smoke
multitouch-smoke: build framework apk-fixture framework-resources
	python3 scripts/multitouch_smoke.py

.PHONY: gesture-smoke
gesture-smoke: build framework apk-fixture framework-resources
	python3 scripts/gesture_smoke.py

.PHONY: surface-smoke
surface-smoke: build framework apk-fixture framework-resources build/libtest-surface-wrapper.so
	python3 scripts/surface_smoke.py

.PHONY: transformation-smoke
transformation-smoke: build framework apk-fixture framework-resources
	python3 scripts/transformation_smoke.py

.PHONY: editable-smoke
editable-smoke: build framework apk-fixture framework-resources
	python3 scripts/editable_smoke.py

.PHONY: edittext-smoke
edittext-smoke: build framework apk-fixture framework-resources
	python3 scripts/edittext_smoke.py

.PHONY: ripple-smoke
ripple-smoke: build framework apk-fixture framework-resources
	python3 scripts/ripple_smoke.py

.PHONY: ellipsis-smoke
ellipsis-smoke: build framework apk-fixture framework-resources
	python3 scripts/ellipsis_smoke.py

.PHONY: break-strategy-smoke
break-strategy-smoke: build framework apk-fixture framework-resources
	python3 scripts/break_strategy_smoke.py

.PHONY: compound-button-smoke
compound-button-smoke: build framework apk-fixture framework-resources
	python3 scripts/compound_button_smoke.py

.PHONY: property-smoke
property-smoke: build framework apk-fixture framework-resources
	python3 scripts/property_smoke.py

.PHONY: selector-smoke
selector-smoke: build framework apk-fixture framework-resources
	python3 scripts/selector_smoke.py

.PHONY: nine-patch-smoke
nine-patch-smoke: build framework apk-fixture framework-resources
	python3 scripts/nine_patch_smoke.py

.PHONY: alpha-smoke
alpha-smoke: build framework apk-fixture framework-resources
	python3 scripts/alpha_smoke.py

.PHONY: value-animator-smoke
value-animator-smoke: build framework
	python3 scripts/value_animator_smoke.py

.PHONY: view-transition-smoke
view-transition-smoke: build framework apk-fixture framework-resources
	python3 scripts/view_transition_smoke.py

.PHONY: object-animator-smoke
object-animator-smoke: build framework
	python3 scripts/object_animator_smoke.py

.PHONY: layout-transition-smoke
layout-transition-smoke: build framework
	python3 scripts/layout_transition_smoke.py

.PHONY: outline-smoke
outline-smoke: build framework
	python3 scripts/outline_smoke.py

.PHONY: elevation-smoke
elevation-smoke: build framework
	python3 scripts/elevation_smoke.py

.PHONY: corner-smoke
corner-smoke: build framework
	python3 scripts/corner_smoke.py

.PHONY: scroll-smoke
scroll-smoke: build framework
	python3 scripts/scroll_smoke.py

.PHONY: autofill-metadata-smoke
autofill-metadata-smoke: build framework
	python3 scripts/autofill_metadata_smoke.py

.PHONY: accessibility-manager-smoke
accessibility-manager-smoke: build framework
	python3 scripts/accessibility_manager_smoke.py

.PHONY: parent-attachment-smoke
parent-attachment-smoke: build framework
	python3 scripts/parent_attachment_smoke.py

.PHONY: gradient-smoke
gradient-smoke: build framework apk-fixture
	python3 scripts/gradient_smoke.py

.PHONY: translation-smoke
translation-smoke: build framework
	python3 scripts/translation_smoke.py

.PHONY: matrix-smoke
matrix-smoke: build framework
	python3 scripts/matrix_smoke.py

.PHONY: xfermode-smoke
xfermode-smoke: build framework
	python3 scripts/xfermode_smoke.py

.PHONY: wrapper-smoke
wrapper-smoke: build framework
	python3 scripts/wrapper_smoke.py

.PHONY: region-smoke
region-smoke: build framework
	python3 scripts/region_smoke.py

.PHONY: variation-smoke
variation-smoke: build framework
	python3 scripts/variation_smoke.py

.PHONY: spacing-smoke
spacing-smoke: build framework
	python3 scripts/spacing_smoke.py

.PHONY: shadow-smoke
shadow-smoke: build framework
	python3 scripts/shadow_smoke.py

.PHONY: progress-smoke
progress-smoke: build framework
	python3 scripts/progress_smoke.py

.PHONY: adapter-smoke
adapter-smoke: build framework
	python3 scripts/adapter_smoke.py

.PHONY: list-smoke
list-smoke: build framework
	python3 scripts/list_smoke.py

.PHONY: sparse-int-smoke
sparse-int-smoke: build framework
	python3 scripts/sparse_int_smoke.py

.PHONY: relative-smoke
relative-smoke: build framework
	python3 scripts/relative_smoke.py

.PHONY: long-click-smoke
long-click-smoke: build framework
	python3 scripts/long_click_smoke.py

.PHONY: trace-smoke
trace-smoke: build framework
	python3 scripts/trace_smoke.py

.PHONY: frame-animation-smoke
frame-animation-smoke: build framework apk-fixture framework-resources
	python3 scripts/frame_animation_smoke.py

.PHONY: table-smoke
table-smoke: build framework
	python3 scripts/table_smoke.py

.PHONY: break-text-smoke
break-text-smoke: build framework
	python3 scripts/break_text_smoke.py

.PHONY: overlay-smoke
overlay-smoke: build framework
	python3 scripts/overlay_smoke.py

.PHONY: shape-drawable-smoke
shape-drawable-smoke: build framework
	python3 scripts/shape_drawable_smoke.py

.PHONY: focus-highlight-smoke
focus-highlight-smoke: build framework apk-fixture framework-resources
	python3 scripts/focus_highlight_smoke.py

.PHONY: array-adapter-smoke
array-adapter-smoke: build framework apk-fixture framework-resources
	python3 scripts/array_adapter_smoke.py

.PHONY: ui-thread-smoke
ui-thread-smoke: build framework
	python3 scripts/ui_thread_smoke.py

.PHONY: icon-smoke
icon-smoke: build framework framework-resources
	python3 scripts/icon_smoke.py

.PHONY: shortcut-smoke
shortcut-smoke: build framework framework-resources
	python3 scripts/shortcut_smoke.py

.PHONY: webp-smoke
webp-smoke: build framework framework-resources
	python3 scripts/webp_smoke.py

.PHONY: spannable-smoke
spannable-smoke: build framework framework-resources
	python3 scripts/spannable_smoke.py

.PHONY: color-span-smoke
color-span-smoke: build framework framework-resources
	python3 scripts/color_span_smoke.py

.PHONY: location-smoke
location-smoke: build framework framework-resources
	python3 scripts/location_smoke.py

.PHONY: atomic-file-smoke
atomic-file-smoke: build framework
	python3 scripts/atomic_file_smoke.py

.PHONY: runtime-network network-smoke
runtime-network:
	python3 scripts/bootstrap_runtime.py network
build/runtime-network.stamp: scripts/bootstrap_runtime.py scripts/bootstrap_boot.py patches/art-host-trust-store.patch patches/art-tls-stream-lifetime.patch patches/art-tls-cache-lifetime.patch build/runtime-xml.stamp
	$(MAKE) runtime-network
	touch $@
build runtime-smoke runtime-aot-smoke runtime-jni-smoke: build/runtime-network.stamp
network-smoke: build framework
	python3 scripts/network_smoke.py

.PHONY: fill-benchmark
fill-benchmark: build framework
	python3 scripts/fill_benchmark.py

.PHONY: runtime-speech speech-smoke
runtime-speech:
	python3 scripts/bootstrap_runtime.py speech
build/runtime-speech.stamp: scripts/bootstrap_runtime.py
	$(MAKE) runtime-speech
	touch $@
build/libshiraz-speech.so: native/speech.cpp native/speech_output.cpp build/runtime-speech.stamp
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -I$(CURDIR)/.local/include -fPIC -shared -Wl,-z,defs native/speech.cpp native/speech_output.cpp -o $@ -L$(CURDIR)/.local/lib -lespeak-ng -lasound -Wl,-rpath,'$$ORIGIN/../.local/lib' -pthread
speech-smoke: build/libshiraz-speech.so framework
	python3 scripts/speech_smoke.py

.PHONY: speech-playback-smoke
speech-playback-smoke: build/libshiraz-speech.so framework
	python3 scripts/speech_playback_smoke.py

.PHONY: tts-smoke
build: build/libshiraz-speech.so
tts-smoke: build/libshiraz-speech.so framework
	python3 scripts/tts_smoke.py

.PHONY: drag-smoke
drag-smoke: build framework
	python3 scripts/drag_smoke.py

.PHONY: sparse-long-smoke
sparse-long-smoke: build framework
	python3 scripts/sparse_long_smoke.py

.PHONY: sparse-boolean-smoke
sparse-boolean-smoke: build framework
	python3 scripts/sparse_boolean_smoke.py

.PHONY: size-smoke
size-smoke: build framework
	python3 scripts/size_smoke.py

.PHONY: binder-smoke
binder-smoke: build framework
	python3 scripts/binder_smoke.py

.PHONY: cursor-anchor-smoke
cursor-anchor-smoke: build framework
	python3 scripts/cursor_anchor_smoke.py

.PHONY: live-input-smoke
build/test-live-input: tests/live_input.cpp native/presentation.cpp include/shiraz/live_input.hpp include/shiraz/presentation.hpp include/shiraz/keyboard.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/pointer.hpp include/shiraz/compositor.hpp $(SDL_LOCAL_LIBRARY)
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/live_input.cpp native/presentation.cpp -o $@ $(SDL_LIBS)
live-input-smoke: build/test-live-input
	SDL_VIDEODRIVER=dummy ./build/test-live-input

.PHONY: layout-smoke
layout-smoke: build framework framework-resources
	python3 scripts/layout_smoke.py

.PHONY: grid-smoke
grid-smoke: build framework framework-resources
	python3 scripts/grid_smoke.py

.PHONY: view-property-animator-smoke
view-property-animator-smoke: build framework
	python3 scripts/view_property_animator_smoke.py

.PHONY: android-tls-smoke
android-tls-smoke: build framework
	python3 scripts/android_tls_smoke.py

.PHONY: apk-tls-smoke
apk-tls-smoke: build framework
	python3 scripts/android_tls_smoke.py --apk context/apks/net.osmand.plus_541002.apk

.PHONY: patterns-smoke
patterns-smoke: build framework
	python3 scripts/patterns_smoke.py

.PHONY: bidi-text-smoke
bidi-text-smoke: build framework
	python3 scripts/bidi_text_smoke.py

.PHONY: text-path-smoke
text-path-smoke: build framework
	python3 scripts/text_path_smoke.py

.PHONY: statfs-smoke
statfs-smoke: build framework
	python3 scripts/statfs_smoke.py

.PHONY: content-resolver-smoke
content-resolver-smoke: build framework apk-fixture framework-resources
	python3 scripts/content_resolver_smoke.py

.PHONY: uri-matcher-smoke
uri-matcher-smoke: build framework
	python3 scripts/uri_matcher_smoke.py

.PHONY: line-smoke
line-smoke: build framework
	python3 scripts/line_smoke.py

.PHONY: appimage osmand-appimage packaging-tools
packaging-tools:
	python3 scripts/bootstrap_patchelf.py
appimage osmand-appimage: packaging-tools
HOST_ARCH := $(shell uname -m)
OSMAND_APK := context/apks/net.osmand.plus_$(if $(filter aarch64,$(HOST_ARCH)),541003,541002).apk
build/shiraz-app: packaging/app_run.cpp packaging/desktop_install.h
	@mkdir -p build
	$(CXX) $(CXXFLAGS) $< -o $@
appimage: build framework build/shiraz-app build/libEGL_shim.so
	@test -n "$(APK)" || { echo 'Usage: make appimage APK=/absolute/path/app.apk [NAME=Title] [AOT=0]'; exit 1; }
	python3 scripts/build_appimage.py --apk "$(APK)" $(if $(NAME),--name "$(NAME)",) $(if $(filter 0,$(AOT)),--no-aot,)
osmand-appimage: build framework build/shiraz-app build/libEGL_shim.so
	python3 scripts/build_appimage.py --apk "$(if $(APK),$(APK),$(OSMAND_APK))" $(if $(filter 0,$(AOT)),--no-aot,)

test: build/shiraz-app
.PHONY: appimage-smoke
appimage-smoke:
	python3 scripts/appimage_smoke.py "$(if $(IMAGE),$(IMAGE),dist/OsmAnd-Shiraz-$(shell cat VERSION)-$(HOST_ARCH).AppImage)" $(if $(filter 1,$(AOT)),--require-aot,)

.PHONY: asset-list-smoke
asset-list-smoke: build framework apk-fixture
	python3 scripts/asset_list_smoke.py

.PHONY: legacy-animation-smoke
legacy-animation-smoke: build framework apk-fixture framework-resources
	python3 scripts/legacy_animation_smoke.py

.PHONY: connectivity-smoke
connectivity-smoke: build framework
	python3 scripts/connectivity_smoke.py

.PHONY: permission-smoke
permission-smoke: build framework
	python3 scripts/permission_smoke.py

.PHONY: circular-reveal-smoke
circular-reveal-smoke: build framework framework-resources
	python3 scripts/circular_reveal_smoke.py

.PHONY: large-bitmap-smoke
large-bitmap-smoke: build framework
	python3 scripts/large_bitmap_smoke.py

.PHONY: runtime-memory art-low4g-smoke
runtime-memory:
	python3 scripts/bootstrap_runtime.py memory
build/runtime-memory.stamp: scripts/bootstrap_runtime.py patches/art-low4g-fallback.patch
	$(MAKE) runtime-memory
	@touch $@
build appimage: build/runtime-memory.stamp
art-low4g-smoke: build/runtime-memory.stamp
	python3 scripts/art_low4g_smoke.py

.PHONY: osmand-transition-smoke
osmand-transition-smoke: build framework framework-resources
	python3 scripts/androidx_transition_smoke.py --apk "$(if $(APK),$(APK),$(OSMAND_APK))"

.PHONY: bucket-resize-smoke
bucket-resize-smoke: build framework apk-fixture framework-resources build/libshiraz-test-input.so
	python3 scripts/bucket_resize_smoke.py

.PHONY: theme-smoke
theme-smoke: build framework apk-fixture framework-resources
	python3 scripts/theme_smoke.py

.PHONY: activity-navigation-smoke
activity-navigation-smoke: build framework apk-fixture framework-resources build/libshiraz-test-input.so
	python3 scripts/activity_navigation_smoke.py

.PHONY: audio-focus-smoke
audio-focus-smoke: build framework framework-resources
	python3 scripts/audio_focus_smoke.py

.PHONY: subpixel-text-smoke
subpixel-text-smoke: build framework
	python3 scripts/subpixel_text_smoke.py

.PHONY: child-drawable-state-smoke
child-drawable-state-smoke: build framework apk-fixture framework-resources
	python3 scripts/child_drawable_state_smoke.py

.PHONY: progress-dialog-smoke
progress-dialog-smoke: build framework framework-resources
	python3 scripts/progress_dialog_smoke.py

.PHONY: autocomplete-text-smoke
autocomplete-text-smoke: build framework apk-fixture framework-resources
	python3 scripts/autocomplete_text_smoke.py

.PHONY: popup-placement-smoke
popup-placement-smoke: build framework
	python3 scripts/popup_placement_smoke.py

.PHONY: popup-window-smoke
popup-window-smoke: build framework framework-resources
	python3 scripts/popup_window_smoke.py

.PHONY: animated-selector-smoke
animated-selector-smoke: build framework framework-resources apk-fixture
	python3 scripts/animated_selector_smoke.py

.PHONY: dedicated-process-smoke
dedicated-process-smoke: build framework
	python3 scripts/dedicated_process_smoke.py

.PHONY: process-apk-smoke
process-apk-smoke: build framework framework-resources
	python3 scripts/process_apk_smoke.py

.PHONY: runtime-bionic-smoke
runtime-bionic-smoke:
	python3 scripts/bionic_smoke.py

build/libjnigraphics.so: native/android_bitmap.cpp include/shiraz/android_bitmap.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/android_bitmap.cpp -o $@ -pthread
build: build/libjnigraphics.so

build/libshiraz-test-android-bitmap.so: tests/android_bitmap.cpp include/shiraz/android_bitmap.h build/libjnigraphics.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs tests/android_bitmap.cpp -o $@ -Lbuild -ljnigraphics -Wl,-rpath,'$$ORIGIN'
.PHONY: android-bitmap-smoke
android-bitmap-smoke: framework build/libshiraz-test-android-bitmap.so
	python3 scripts/android_bitmap_smoke.py

.PHONY: png-encoder-smoke
png-encoder-smoke: framework build/libshiraz-image.so
	python3 scripts/png_encoder_smoke.py

build/libshiraz-android-fonts.so: native/android_font_files.c
	@mkdir -p build
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -fPIC -shared -Wl,-z,defs $< -o $@ -ldl -pthread
build: build/libshiraz-android-fonts.so

build/libshiraz-android-process.so: native/android_process_compat.c native/android_profile_properties.h native/virtual_file_metadata.h
	@mkdir -p build
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -fPIC -shared -Wl,-z,defs $< -o $@ -ldl
build: build/libshiraz-android-process.so

build/test-android-font-files: tests/android_font_files.c
	@mkdir -p build
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror $< -o $@
.PHONY: android-fonts-smoke
android-fonts-smoke: build/libshiraz-android-fonts.so build/test-android-font-files
	python3 scripts/android_fonts_smoke.py

build/test-presentation-damage: tests/presentation_damage.cpp native/presentation.cpp include/shiraz/presentation.hpp $(SDL_LOCAL_LIBRARY)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/presentation_damage.cpp native/presentation.cpp -o $@ -Wl,--wrap=SDL_RenderPresent $(SDL_LIBS)
.PHONY: presentation-damage-smoke
presentation-damage-smoke: build/test-presentation-damage
	python3 scripts/presentation_damage_smoke.py

.PHONY: media-session-smoke
media-session-smoke: build framework
	python3 scripts/media_session_smoke.py

.PHONY: popup-anchor-smoke
popup-anchor-smoke: build framework framework-resources
	python3 scripts/popup_anchor_smoke.py

.PHONY: capture-codec
capture-codec:
	python3 scripts/bootstrap_capture_codec.py

build/libshiraz-capture.so: native/capture.cpp | capture-codec
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -I$(CURDIR)/.local/include -fPIC -shared -Wl,-z,defs native/capture.cpp -o $@ -L$(CURDIR)/.local/lib -Wl,-rpath,'$$ORIGIN:$(CURDIR)/.local/lib' -lvo-aacenc -lasound -ljpeg -pthread
build: build/libshiraz-capture.so

.PHONY: capture-smoke
capture-smoke: build framework framework-resources
	python3 scripts/capture_smoke.py

.PHONY: activity-transition-smoke
activity-transition-smoke: build framework framework-resources
	python3 scripts/activity_transition_smoke.py

.PHONY: motion-reference-apk
motion-reference-apk:
	python3 scripts/build_motion_reference.py

.PHONY: child-clipping-smoke
child-clipping-smoke: build framework apk-fixture
	python3 scripts/child_clipping_smoke.py

.PHONY: playback-codec
playback-codec:
	python3 scripts/bootstrap_playback_codec.py
build/libshiraz-playback.so: native/playback.cpp | playback-codec
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -I$(CURDIR)/.local/include -fPIC -shared -Wl,-z,defs native/playback.cpp -o $@ -L$(CURDIR)/.local/lib -Wl,-rpath,'$$ORIGIN:$(CURDIR)/.local/lib' -lfaad -lsndfile -lasound -pthread
build: build/libshiraz-playback.so

.PHONY: audio-playback-smoke
audio-playback-smoke: build framework framework-resources
	python3 scripts/audio_playback_smoke.py

.PHONY: media-player-smoke
media-player-smoke: build framework framework-resources
	python3 scripts/media_player_smoke.py

.PHONY: installer-smoke
installer-smoke: build framework
	python3 scripts/installer_smoke.py

.PHONY: gpu-presentation-smoke
build/test-presentation-gpu: tests/presentation_gpu.cpp native/presentation.cpp include/shiraz/presentation.hpp $(SDL_LOCAL_LIBRARY)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/presentation_gpu.cpp native/presentation.cpp -o $@ $(SDL_LIBS)
gpu-presentation-smoke: build/test-presentation-gpu
	SDL_VIDEODRIVER=offscreen SHIRAZ_RENDERER=gpu timeout 20 ./build/test-presentation-gpu
	SDL_VIDEODRIVER=dummy SHIRAZ_RENDERER=auto timeout 20 ./build/test-presentation-gpu
	SDL_VIDEODRIVER=dummy SHIRAZ_RENDERER=software timeout 20 ./build/test-presentation-gpu
	LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEODRIVER=offscreen SHIRAZ_RENDERER=auto timeout 20 ./build/test-presentation-gpu

# Display scale changes affect every presentation entry point.
build/shiraz-preview build/libshiraz-jni.so build/test-presentation build/test-presentation-gpu: include/shiraz/display_geometry.hpp

# Skia Ganesh is shared by presentation and Java Canvas JNI; one context per UI thread.
.deps/skia/out/shiraz/libskia.a .deps/skia/out/shiraz/libpathkit.a &: scripts/bootstrap_skia.py
	python3 scripts/bootstrap_skia.py
build/libshiraz-gpu.so: native/ripple_gpu.inc native/gpu.exports native/gpu.cpp include/shiraz/gpu.hpp .deps/skia/out/shiraz/libskia.a .deps/skia/out/shiraz/libpathkit.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -isystem .deps/skia -DSK_GANESH -DSK_GL -DSK_R32_SHIFT=16 -fPIC -shared -Wl,-z,defs -Wl,--version-script=native/gpu.exports native/gpu.cpp .deps/skia/out/shiraz/libpathkit.a .deps/skia/out/shiraz/libskia.a -o $@ -lfreetype -lfontconfig -lGL -ldl -pthread
build: build/libshiraz-gpu.so

.PHONY: gpu-canvas-smoke
gpu-canvas-smoke: build framework
	python3 scripts/gpu_canvas_smoke.py
.PHONY: gpu-animation-benchmark
gpu-animation-benchmark: build framework
	python3 scripts/gpu_animation_benchmark.py

.PHONY: bionic-fortify-smoke
bionic-fortify-smoke:
	$(CC) -Wall -Wextra -Werror tests/bionic_fortify.c -o build/test-bionic-fortify -L.local/lib -lc_bio -Wl,-rpath-link,.local/lib -Wl,-rpath,'$$ORIGIN/../.local/lib'
	LD_LIBRARY_PATH="$(CURDIR)/.local/lib" build/test-bionic-fortify

build/test-shared-memory: tests/shared_memory.cpp build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/shared_memory.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN'

build/test-hardware-buffer: tests/hardware_buffer.cpp include/shiraz/hardware_buffer.h build/libandroid.so build/libEGL_shim.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/hardware_buffer.cpp -o $@ -Lbuild -landroid -lEGL_shim -lGLESv2 -Wl,-rpath,'$$ORIGIN'
.PHONY: hardware-buffer-smoke
hardware-buffer-smoke: build/test-hardware-buffer
	./build/test-hardware-buffer

build/test-image-reader: tests/image_reader.cpp include/shiraz/image_reader.h build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/image_reader.cpp -o $@ -Lbuild -landroid -pthread -Wl,-rpath,'$$ORIGIN'
.PHONY: image-reader-smoke
image-reader-smoke: build/test-image-reader
	./build/test-image-reader

build/libtest-surface-wrapper.so: tests/surface_wrapper.cpp build/libandroid.so build/libEGL_shim.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -shared -fPIC tests/surface_wrapper.cpp -o $@ -Lbuild -landroid -lEGL_shim -lGLESv2 -Wl,-rpath,'$$ORIGIN'

build/test-media-crypto: tests/media_crypto.cpp include/shiraz/media_crypto.h build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/media_crypto.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN'
.PHONY: media-crypto-smoke
media-crypto-smoke: build/test-media-crypto
	./build/test-media-crypto

.PHONY: bionic-environment-smoke
bionic-environment-smoke:
	$(CC) -Wall -Wextra -Werror tests/bionic_environment.c -o build/test-bionic-environment -L.local/lib -lc_bio -Wl,-rpath-link,.local/lib -Wl,-rpath,'$$ORIGIN/../.local/lib'
	LD_LIBRARY_PATH="$(CURDIR)/.local/lib" build/test-bionic-environment

build/test-ipc-channel: tests/ipc_channel.cpp native/ipc_channel.cpp include/shiraz/ipc_channel.hpp
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ipc_channel.cpp native/ipc_channel.cpp -o $@
.PHONY: ipc-channel-smoke
ipc-channel-smoke: build/test-ipc-channel
	./build/test-ipc-channel

build/test-system-fonts: tests/system_fonts.cpp build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/system_fonts.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN'
.PHONY: system-fonts-smoke
system-fonts-smoke: build/test-system-fonts
	./build/test-system-fonts

.PHONY: frame-metrics-smoke
frame-metrics-smoke: build framework
	python3 scripts/frame_metrics_smoke.py

.PHONY: webp-encoder-smoke
webp-encoder-smoke: framework build/libshiraz-image.so
	python3 scripts/webp_encoder_smoke.py

.PHONY: core-abi-smoke
core-abi-smoke: build framework
	python3 scripts/core_abi_smoke.py

.PHONY: bionic-dirent-smoke
bionic-dirent-smoke:
	$(CC) -Wall -Wextra -Werror tests/bionic_dirent.c -o build/test-bionic-dirent -L.local/lib -lc_bio -Wl,-rpath-link,.local/lib -Wl,-rpath,'$$ORIGIN/../.local/lib'
	LD_LIBRARY_PATH="$(CURDIR)/.local/lib" build/test-bionic-dirent

.PHONY: bionic-sigchain-smoke
bionic-sigchain-smoke:
	$(CXX) -std=c++17 -Wall -Wextra -Werror tests/bionic_sigchain.cpp -o build/test-bionic-sigchain -L.local/lib -lc_bio -ldl -Wl,-rpath-link,.local/lib -Wl,-rpath,'$$ORIGIN/../.local/lib'
	LD_LIBRARY_PATH="$(CURDIR)/.local/lib:$(CURDIR)/context/art_standalone/out/host/linux-x86/lib64" build/test-bionic-sigchain "$(CURDIR)/context/art_standalone/out/host/linux-x86/lib64/libsigchain.so"

.PHONY: shared-frame-smoke
shared-frame-smoke:
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) native/shared_frame.cpp tests/shared_frame.cpp -pthread -o build/test-shared-frame
	build/test-shared-frame

.PHONY: host-downloads-smoke
host-downloads-smoke: framework
	python3 scripts/host_downloads_smoke.py

.PHONY: download-manager-smoke
download-manager-smoke: framework
	python3 scripts/download_manager_smoke.py

# Optional OS codec adapter. GStreamer plugins remain OS-managed at runtime.
.PHONY: system-video
system-video:
	@if pkg-config --exists gstreamer-app-1.0 gstreamer-video-1.0 opus; then $(MAKE) build/libshiraz-video.so; else rm -f build/libshiraz-video.so; echo 'OS video adapter unavailable: GStreamer/Opus development files missing'; fi
build/libshiraz-video.so: native/video_codec.cpp native/opus_codec.cpp build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -shared -fPIC native/video_codec.cpp native/opus_codec.cpp -o $@ -Lbuild -landroid $$(pkg-config --cflags --libs gstreamer-app-1.0 gstreamer-video-1.0) -Wl,-rpath,'$$ORIGIN'
appimage: system-video
.PHONY: system-video-smoke
system-video-smoke: build framework system-video
	@test -n "$(VIDEO_SAMPLE)" || { echo 'Set VIDEO_SAMPLE to a local Annex-B H.264 clip with AUDs'; exit 1; }
	mkdir -p build/system-video-smoke
	javac --release 8 -cp build/framework/classes -d build/system-video-smoke tests/java/SystemVideoSmoke.java
	java -Xcheck:jni -Djava.library.path=build -cp build/system-video-smoke:build/framework/classes SystemVideoSmoke "$(VIDEO_SAMPLE)"

build/libOpenSLES.so.1: native/opensles.cpp third_party/opensles/SLES/OpenSLES.h third_party/opensles/SLES/OpenSLES_Android.h
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -isystem third_party/opensles -shared -fPIC -Wl,-z,defs -Wl,-soname,libOpenSLES.so.1 native/opensles.cpp -o $@ -lasound -pthread

build/test-opensles: tests/opensles.cpp build/libOpenSLES.so.1
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -isystem third_party/opensles tests/opensles.cpp -o $@ -Lbuild -l:libOpenSLES.so.1 -Wl,-rpath,'$$ORIGIN' -pthread
.PHONY: opensles-smoke
opensles-smoke: build/test-opensles
	mkdir -p build/opensles-smoke
	printf 'pcm.null { type null }\npcm.shiraz_test { type file slave.pcm "null" file "$(CURDIR)/build/opensles-smoke/output.pcm" format "raw" }\n' > build/opensles-smoke/alsa.conf
	ALSA_CONFIG_PATH=$(CURDIR)/build/opensles-smoke/alsa.conf SHIRAZ_AUDIO_DEVICE=shiraz_test build/test-opensles
	python3 -c 'import array,pathlib; a=array.array("h",pathlib.Path("build/opensles-smoke/output.pcm").read_bytes()); assert len(a)==3840 and max(a)>11000 and min(a)<-11000; print("OPENSL_PCM_SIGNAL_OK samples="+str(len(a)))'
appimage: build/libOpenSLES.so.1
.PHONY: opus-codec-smoke
opus-codec-smoke: framework system-video
	mkdir -p build/opus-smoke
	javac --release 8 -cp build/framework/classes -d build/opus-smoke tests/java/OpusCodecSmoke.java
	java -Xcheck:jni -Djava.library.path=build -cp build/framework/classes:build/opus-smoke OpusCodecSmoke

.PHONY: bionic-dladdr-smoke
bionic-dladdr-smoke: runtime-bionic-smoke
	$(CC) -Wall -Wextra -Werror tests/bionic_dladdr.c -o build/test-bionic-dladdr -L.local/lib -ldl_bio -Wl,-rpath-link,.local/lib -Wl,-rpath,'$$ORIGIN/../.local/lib'
	LD_LIBRARY_PATH=$(CURDIR)/.local/lib BIONIC_LD_LIBRARY_PATH=$(CURDIR)/build/bionic-smoke/libs build/test-bionic-dladdr $(CURDIR)/build/bionic-smoke/libs/libshiraz-bionic-probe.so > build/bionic-dladdr.log 2>&1
	@tail -1 build/bionic-dladdr.log

.PHONY: media-ndk-smoke
build/test-media-ndk: tests/media_ndk.cpp build/libandroid.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $< -o $@ -Lbuild -landroid -ldl -Wl,-rpath,'$$ORIGIN'
media-ndk-smoke: build/test-media-ndk
	./build/test-media-ndk

# Optional experimental ARM64 translation. Requires pinned Digitalis sources,
# standalone ART libraries and compiler headers; never runs guest APKs locally.
.PHONY: runtime-digitalis
runtime-digitalis:
	python3 scripts/digitalis/build.py

.PHONY: test-software-keystore
test-software-keystore: framework
	mkdir -p build/keystore-smoke/classes
	javac --release 8 -cp build/framework/classes -d build/keystore-smoke/classes tests/java/KeyStoreSmoke.java
	java -cp build/keystore-smoke/classes:build/framework/classes KeyStoreSmoke build/keystore-smoke/data
	java -cp build/keystore-smoke/classes:build/framework/classes KeyStoreSmoke build/keystore-smoke/data reopen

.PHONY: digitalis-nativewindow-proxy
build/libshiraz-gles1.so: native/gles1_jni.cpp
	mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs $< -o $@ -lGLESv1_CM
build: build/libshiraz-gles1.so

digitalis-nativewindow-proxy:
	python3 scripts/digitalis/build_nativewindow_proxy.py $(DIGITALIS_PROXY_OPTIONS)

.PHONY: provider-metadata-smoke
provider-metadata-smoke: framework build/libshiraz-host.so
	mkdir -p build/provider-metadata-smoke
	javac --release 8 -cp build/framework/classes -d build/provider-metadata-smoke tests/java/ProviderMetadataSmoke.java
	java -Djava.library.path=build -cp build/provider-metadata-smoke:build/framework/classes shiraz.runtime.ProviderMetadataSmoke

.PHONY: telephony-listener-smoke
telephony-listener-smoke: framework build/libshiraz-host.so
	mkdir -p build/telephony-listener-smoke
	javac --release 8 -cp build/framework/classes -d build/telephony-listener-smoke tests/java/TelephonyListenerSmoke.java
	java -Djava.library.path=build -cp build/telephony-listener-smoke:build/framework/classes TelephonyListenerSmoke

.PHONY: generic-motion-listener-smoke
generic-motion-listener-smoke: framework build/libshiraz-host.so
	mkdir -p build/generic-motion-listener-smoke
	javac --release 8 -cp build/framework/classes -d build/generic-motion-listener-smoke tests/java/GenericMotionListenerSmoke.java
	java -Djava.library.path=build -cp build/generic-motion-listener-smoke:build/framework/classes GenericMotionListenerSmoke

build/test-asset-stream: tests/native/asset_stream.cpp build/libshiraz-resources.so
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -isystem context/art_standalone/libandroidfw/include $< -o $@ -Lbuild -lshiraz-resources -L$(ART_HOST)/lib64 -landroidfw -Wl,--allow-shlib-undefined -Wl,-rpath,'$$ORIGIN'

build/asset-stream-fixture.apk: scripts/build_asset_stream_fixture.py
	python3 $<
.PHONY: asset-stream-smoke
ASSET_STREAM_RUNNER ?=
asset-stream-smoke: build/test-asset-stream build/asset-stream-fixture.apk
	LD_LIBRARY_PATH="$(ART_HOST)/lib64:$(CURDIR)/build:$${LD_LIBRARY_PATH}" $(ASSET_STREAM_RUNNER) ./build/test-asset-stream build/asset-stream-fixture.apk
check: asset-stream-smoke

build/test-choreographer: tests/native/choreographer.cpp native/choreographer.cpp native/looper.cpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -o $@ -pthread
.PHONY: choreographer-smoke
choreographer-smoke: build/test-choreographer
	./build/test-choreographer
check: choreographer-smoke provider-metadata-smoke telephony-listener-smoke generic-motion-listener-smoke

build/test-sensors: tests/native/sensors.cpp native/sensors.cpp native/looper.cpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -o $@ -pthread
.PHONY: sensors-smoke
sensors-smoke: build/test-sensors
	./build/test-sensors
check: sensors-smoke

.PHONY: uri-permission-smoke
uri-permission-smoke: framework
	mkdir -p build/uri-permission-smoke
	javac --release 8 -cp build/framework/classes -d build/uri-permission-smoke tests/java/UriPermissionSmoke.java
	java -Djava.library.path=build -cp build/uri-permission-smoke:build/framework/classes UriPermissionSmoke
check: uri-permission-smoke

.PHONY: configuration-diff-smoke
configuration-diff-smoke: framework
	mkdir -p build/configuration-diff-smoke
	javac --release 8 -cp build/framework/classes -d build/configuration-diff-smoke tests/java/ConfigurationDiffSmoke.java
	java -Djava.library.path=build -cp build/configuration-diff-smoke:build/framework/classes ConfigurationDiffSmoke
check: configuration-diff-smoke

.PHONY: window-focus-callback-smoke
window-focus-callback-smoke: framework
	mkdir -p build/window-focus-callback-smoke
	javac --release 8 -cp build/framework/classes -d build/window-focus-callback-smoke tests/java/WindowFocusCallbackSmoke.java
	java -Djava.library.path=build -cp build/window-focus-callback-smoke:build/framework/classes WindowFocusCallbackSmoke
check: window-focus-callback-smoke

.PHONY: icon-value-smoke
icon-value-smoke: framework
	mkdir -p build/icon-value-smoke
	javac --release 8 -cp build/framework/classes -d build/icon-value-smoke tests/java/IconValueSmoke.java
	java -Djava.library.path=build -cp build/icon-value-smoke:build/framework/classes IconValueSmoke
check: icon-value-smoke

build/libshiraz-gles2.so: native/gles2_jni.cpp build/libEGL_shim.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/gles2_jni.cpp -o $@ -Lbuild -lEGL_shim -lGLESv2 -Wl,-rpath,'$$ORIGIN'
build: build/libshiraz-gles2.so

.PHONY: descendant-rect-smoke
descendant-rect-smoke: framework
	mkdir -p build/descendant-rect-smoke
	javac --release 8 -cp build/framework/classes -d build/descendant-rect-smoke tests/java/DescendantRectSmoke.java
	java -Djava.library.path=build -cp build/descendant-rect-smoke:build/framework/classes DescendantRectSmoke
check: descendant-rect-smoke

.PHONY: gles20-smoke
gles20-smoke: framework build/libshiraz-gles2.so build/libshiraz-gles1.so build/libEGL.so.1
	mkdir -p build/gles20-smoke
	javac --release 8 -cp build/framework/classes -d build/gles20-smoke tests/java/GLES20Smoke.java
	LD_LIBRARY_PATH="$(CURDIR)/build:$${LD_LIBRARY_PATH}" java -Xcheck:jni -Djava.library.path=build -cp build/gles20-smoke:build/framework/classes GLES20Smoke
check: gles20-smoke

.PHONY: color-value-smoke
color-value-smoke: framework
	mkdir -p build/color-value-smoke
	javac --release 8 -cp build/framework/classes -d build/color-value-smoke tests/java/ColorValueSmoke.java
	java -Djava.library.path=build -cp build/color-value-smoke:build/framework/classes ColorValueSmoke
check: color-value-smoke

.PHONY: pixel-copy-smoke
pixel-copy-smoke: framework build/libshiraz-gles2.so build/libshiraz-gles1.so build/libEGL.so.1 build/libshiraz-bitmap.so build/libshiraz-path.so
	mkdir -p build/pixel-copy-smoke
	javac --release 8 -cp build/framework/classes -d build/pixel-copy-smoke tests/java/PixelCopySmoke.java
	LD_LIBRARY_PATH="$(CURDIR)/build:$${LD_LIBRARY_PATH}" java -Xcheck:jni -Djava.library.path=build -cp build/pixel-copy-smoke:build/framework/classes PixelCopySmoke
check: pixel-copy-smoke

build/libshiraz-test-alt-stack.so: tests/native/alt_signal_stack.cpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -pthread $< -o $@

.PHONY: view-rotation-smoke
view-rotation-smoke: framework build/libshiraz-bitmap.so build/libshiraz-path.so
	mkdir -p build/view-rotation-smoke
	javac --release 8 -cp build/framework/classes -d build/view-rotation-smoke tests/java/ViewRotationSmoke.java
	java -Djava.library.path=build -cp build/view-rotation-smoke:build/framework/classes ViewRotationSmoke
check: view-rotation-smoke

.PHONY: qwerty-key-smoke
qwerty-key-smoke: framework
	mkdir -p build/qwerty-key-smoke
	javac --release 8 -cp build/framework/classes -d build/qwerty-key-smoke tests/java/QwertyKeySmoke.java
	java -cp build/qwerty-key-smoke:build/framework/classes QwertyKeySmoke
check: qwerty-key-smoke

.PHONY: textview-lineheight-smoke
textview-lineheight-smoke: framework build/libshiraz-text.so build/libshiraz-bitmap.so build/libshiraz-path.so build/libshiraz-resources.so
	mkdir -p build/textview-lineheight-smoke
	javac --release 8 -cp build/framework/classes -d build/textview-lineheight-smoke tests/java/TextViewLineHeightSmoke.java
	LD_LIBRARY_PATH="$(CURDIR)/context/art_standalone/out/host/linux-x86/lib64:$(CURDIR)/.local/lib:$(CURDIR)/build:$${LD_LIBRARY_PATH}" $(JAVA) -Xcheck:jni -Dshiraz.frameworkResources=build/framework-res/framework-res.apk -Djava.library.path=build -cp build/textview-lineheight-smoke:build/framework/classes:context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar TextViewLineHeightSmoke
check: textview-lineheight-smoke

.PHONY: art-alt-stack-smoke
art-alt-stack-smoke: build/libshiraz-test-alt-stack.so
	python3 scripts/art_alt_stack_smoke.py


.PHONY: bitmap-colors-smoke
bitmap-colors-smoke: framework
	mkdir -p build/bitmap-colors-smoke
	javac --release 8 -cp build/framework/classes -d build/bitmap-colors-smoke tests/java/BitmapColorsSmoke.java
	java -cp build/bitmap-colors-smoke:build/framework/classes BitmapColorsSmoke
check: bitmap-colors-smoke


.PHONY: pointer-icon-smoke color-cursor-smoke
pointer-icon-smoke: framework
	mkdir -p build/pointer-icon-smoke
	javac --release 8 -cp build/framework/classes -d build/pointer-icon-smoke tests/java/PointerIconSmoke.java
	java -cp build/pointer-icon-smoke:build/framework/classes PointerIconSmoke
check: pointer-icon-smoke

build/test-color-cursor: tests/color_cursor.cpp native/presentation.cpp include/shiraz/presentation.hpp $(SDL_LOCAL_LIBRARY)
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(SDL_CFLAGS) tests/color_cursor.cpp native/presentation.cpp -o $@ $(SDL_LIBS)
color-cursor-smoke: build/test-color-cursor
	SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software timeout 10 ./build/test-color-cursor


.PHONY: guest-resolver-smoke
build/test-guest-resolver: tests/native/guest_resolver.cpp native/guest_resolver.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -Inative $< -o $@
guest-resolver-smoke: build/test-guest-resolver
	./build/test-guest-resolver
check: guest-resolver-smoke
