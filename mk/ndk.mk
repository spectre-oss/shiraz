# SPDX-License-Identifier: GPL-3.0-only
# Android NDK API 35 native library targets for Linux (build/) and macOS (build/macos/).
# Written for GNU Make 3.81 compatibility on macOS.

NDK_LINUX_LIBS := \
	build/libandroid.so \
	build/libnativewindow.so \
	build/libjnigraphics.so \
	build/libmediandk.so \
	build/libsync.so \
	build/libOpenSLES.so \
	build/libaaudio.so

NDK_MACOS_LIBS := \
	$(MACOS_OUT)/libandroid.so \
	$(MACOS_OUT)/libnativewindow.so \
	$(MACOS_OUT)/libjnigraphics.so \
	$(MACOS_OUT)/libmediandk.so \
	$(MACOS_OUT)/libsync.so \
	$(MACOS_OUT)/libOpenSLES.so \
	$(MACOS_OUT)/libaaudio.so

# --- Milestone N0: Standalone libmediandk.so, libsync.so, libnativewindow.so, libOpenSLES.so ---

build/libmediandk.so: native/media_ndk.cpp native/ndk_media_container.cpp native/media_crypto.cpp native/image_reader.cpp include/shiraz/ndk/media_container.h include/shiraz/media_crypto.h include/shiraz/image_reader.h include/shiraz/hardware_buffer.h include/shiraz/native_window.h build/libandroid.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) -fPIC -shared -Wl,-z,defs native/media_ndk.cpp native/ndk_media_container.cpp native/media_crypto.cpp native/image_reader.cpp -o $@ -Lbuild -landroid -pthread -Wl,-rpath,'$$ORIGIN'

build/libsync.so: native/sync_fence.cpp build/libandroid.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/sync_fence.cpp -o $@ -pthread

build/libOpenSLES.so: build/libOpenSLES.so.1
	ln -sfn libOpenSLES.so.1 $@

build/libaaudio.so: native/ndk_aaudio.cpp include/shiraz/ndk/aaudio.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_aaudio.cpp -o $@ -pthread

$(MACOS_OUT)/libnativewindow.so: $(MACOS_OUT)/libandroid.so
	ln -sfn libandroid.so $@

$(MACOS_OUT)/libmediandk.so: native/media_ndk.cpp native/ndk_media_container.cpp native/media_crypto.cpp native/image_reader.cpp include/shiraz/ndk/media_container.h include/shiraz/media_crypto.h include/shiraz/image_reader.h include/shiraz/hardware_buffer.h include/shiraz/native_window.h $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -fPIC $(MACOS_DYLIB) native/media_ndk.cpp native/ndk_media_container.cpp native/media_crypto.cpp native/image_reader.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_RPATH) -pthread $(MACOS_ANDROID_FRAMEWORKS)

$(MACOS_OUT)/libsync.so: native/sync_fence.cpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/sync_fence.cpp -o $@ -pthread

$(MACOS_OUT)/libOpenSLES.so: native/opensles.cpp third_party/opensles/SLES/OpenSLES.h third_party/opensles/SLES/OpenSLES_Android.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -isystem third_party/opensles -fPIC $(MACOS_DYLIB) native/opensles.cpp -o $@ -pthread -framework AudioToolbox -framework CoreFoundation

$(MACOS_OUT)/libaaudio.so: native/ndk_aaudio.cpp include/shiraz/ndk/aaudio.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_aaudio.cpp -o $@ -pthread -framework AudioToolbox -framework CoreFoundation

$(MACOS_OUT)/test-ndk-standalone: tests/ndk_standalone.cpp $(MACOS_OUT)/libmediandk.so $(MACOS_OUT)/libsync.so $(MACOS_OUT)/libOpenSLES.so $(MACOS_OUT)/libnativewindow.so
	$(MACOS_CXX) tests/ndk_standalone.cpp -o $@ $(MACOS_OUT)/libmediandk.so $(MACOS_OUT)/libsync.so $(MACOS_OUT)/libOpenSLES.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-core-app: tests/ndk_core_app.cpp $(MACOS_OUT)/libandroid.so
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) tests/ndk_core_app.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-graphics-system: tests/ndk_graphics_system.cpp $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libjnigraphics.so
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) tests/ndk_graphics_system.cpp -o $@ $(MACOS_OUT)/libandroid.so $(MACOS_OUT)/libjnigraphics.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-mediandk: tests/ndk_mediandk.cpp $(MACOS_OUT)/libmediandk.so
	$(MACOS_CXX) tests/ndk_mediandk.cpp -o $@ $(MACOS_OUT)/libmediandk.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-aaudio: tests/ndk_aaudio.cpp $(MACOS_OUT)/libaaudio.so
	$(MACOS_CXX) tests/ndk_aaudio.cpp -o $@ $(MACOS_OUT)/libaaudio.so $(MACOS_RPATH) -pthread

build/test-ndk-standalone: tests/ndk_standalone.cpp build/libmediandk.so build/libsync.so build/libOpenSLES.so build/libnativewindow.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ndk_standalone.cpp -o $@ -Lbuild -lmediandk -lsync -lOpenSLES -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-core-app: tests/ndk_core_app.cpp build/libandroid.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) tests/ndk_core_app.cpp -o $@ -Lbuild -landroid -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-graphics-system: tests/ndk_graphics_system.cpp build/libandroid.so build/libjnigraphics.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(JNI_CFLAGS) tests/ndk_graphics_system.cpp -o $@ -Lbuild -landroid -ljnigraphics -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-mediandk: tests/ndk_mediandk.cpp build/libmediandk.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ndk_mediandk.cpp -o $@ -Lbuild -lmediandk -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-aaudio: tests/ndk_aaudio.cpp build/libaaudio.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ndk_aaudio.cpp -o $@ -Lbuild -laaudio -Wl,-rpath,'$$ORIGIN' -pthread

.PHONY: ndk-build ndk-test macos-ndk-build macos-ndk-test ndk-audit

ndk-build: $(NDK_LINUX_LIBS)

ndk-test: build/test-ndk-standalone build/test-ndk-core-app build/test-ndk-graphics-system build/test-ndk-mediandk build/test-ndk-aaudio
	./build/test-ndk-standalone
	./build/test-ndk-core-app
	./build/test-ndk-graphics-system
	./build/test-ndk-mediandk
	./build/test-ndk-aaudio

macos-ndk-build: $(NDK_MACOS_LIBS)

macos-ndk-test: $(MACOS_OUT)/test-ndk-standalone $(MACOS_OUT)/test-ndk-core-app $(MACOS_OUT)/test-ndk-graphics-system $(MACOS_OUT)/test-ndk-mediandk $(MACOS_OUT)/test-ndk-aaudio
	$(MACOS_OUT)/test-ndk-standalone
	$(MACOS_OUT)/test-ndk-core-app
	$(MACOS_OUT)/test-ndk-graphics-system
	$(MACOS_OUT)/test-ndk-mediandk
	$(MACOS_OUT)/test-ndk-aaudio

ndk-audit:
	python3 scripts/audit_ndk_apis.py
