# SPDX-License-Identifier: GPL-3.0-only
# Android NDK API 35 native library targets for Linux (build/) and macOS (build/macos/).
# Written for GNU Make 3.81 compatibility on macOS.

NDK_ICU_CFLAGS ?= $(shell pkg-config --cflags icu-uc icu-i18n 2>/dev/null)
NDK_ICU_LIBS ?= $(shell pkg-config --libs icu-uc icu-i18n 2>/dev/null || echo "-licuuc -licui18n")
NDK_MACOS_ICU_CFLAGS ?= -I$(CURDIR)/.local/include
NDK_MACOS_ICU_LIBS ?= -L$(CURDIR)/.local/lib -licuuc -licui18n -Wl,-rpath,$(CURDIR)/.local/lib

NDK_LINUX_LIBS := \
	build/libandroid.so \
	build/libnativewindow.so \
	build/libjnigraphics.so \
	build/libmediandk.so \
	build/libsync.so \
	build/libOpenSLES.so \
	build/libaaudio.so \
	build/liblog.so \
	build/libicu.so \
	build/libamidi.so \
	build/libcamera2ndk.so \
	build/libOpenMAXAL.so \
	build/libbinder_ndk.so \
	build/libneuralnetworks.so

NDK_MACOS_LIBS := \
	$(MACOS_OUT)/libandroid.so \
	$(MACOS_OUT)/libnativewindow.so \
	$(MACOS_OUT)/libjnigraphics.so \
	$(MACOS_OUT)/libmediandk.so \
	$(MACOS_OUT)/libsync.so \
	$(MACOS_OUT)/libOpenSLES.so \
	$(MACOS_OUT)/libaaudio.so \
	$(MACOS_OUT)/liblog.so \
	$(MACOS_OUT)/libicu.so \
	$(MACOS_OUT)/libamidi.so \
	$(MACOS_OUT)/libcamera2ndk.so \
	$(MACOS_OUT)/libOpenMAXAL.so \
	$(MACOS_OUT)/libbinder_ndk.so \
	$(MACOS_OUT)/libneuralnetworks.so

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

build/liblog.so: native/ndk_log.cpp include/shiraz/ndk/log.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_log.cpp -o $@ -pthread

build/libicu.so: native/ndk_icu.cpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(NDK_ICU_CFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_icu.cpp -o $@ $(NDK_ICU_LIBS) -pthread

build/libamidi.so: native/ndk_amidi.cpp include/shiraz/ndk/amidi.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_amidi.cpp -o $@ -pthread

build/libcamera2ndk.so: native/ndk_camera2.cpp include/shiraz/ndk/camera2ndk.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_camera2.cpp -o $@ -pthread

build/libOpenMAXAL.so: native/ndk_openmaxal.cpp include/shiraz/ndk/openmaxal.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_openmaxal.cpp -o $@ -pthread

build/libbinder_ndk.so: native/ndk_binder.cpp include/shiraz/ndk/binder_ndk.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_binder.cpp -o $@ -pthread

build/libneuralnetworks.so: native/ndk_nnapi.cpp include/shiraz/ndk/neuralnetworks.h
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -fPIC -shared -Wl,-z,defs native/ndk_nnapi.cpp -o $@ -pthread

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

$(MACOS_OUT)/liblog.so: native/ndk_log.cpp include/shiraz/ndk/log.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_log.cpp -o $@ -pthread

$(MACOS_OUT)/libicu.so: native/ndk_icu.cpp $(MACOS_OUT)/.dir
	$(MACOS_CXX) $(NDK_MACOS_ICU_CFLAGS) -fPIC $(MACOS_DYLIB) native/ndk_icu.cpp -o $@ $(NDK_MACOS_ICU_LIBS) -pthread

$(MACOS_OUT)/libamidi.so: native/ndk_amidi.cpp include/shiraz/ndk/amidi.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_amidi.cpp -o $@ -pthread

$(MACOS_OUT)/libcamera2ndk.so: native/ndk_camera2.cpp include/shiraz/ndk/camera2ndk.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_camera2.cpp -o $@ -pthread

$(MACOS_OUT)/libOpenMAXAL.so: native/ndk_openmaxal.cpp include/shiraz/ndk/openmaxal.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_openmaxal.cpp -o $@ -pthread

$(MACOS_OUT)/libbinder_ndk.so: native/ndk_binder.cpp include/shiraz/ndk/binder_ndk.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_binder.cpp -o $@ -pthread

$(MACOS_OUT)/libneuralnetworks.so: native/ndk_nnapi.cpp include/shiraz/ndk/neuralnetworks.h $(MACOS_OUT)/.dir
	$(MACOS_CXX) -fPIC $(MACOS_DYLIB) native/ndk_nnapi.cpp -o $@ -pthread

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

$(MACOS_OUT)/test-ndk-log-icu: tests/ndk_log_icu.cpp $(MACOS_OUT)/liblog.so $(MACOS_OUT)/libicu.so
	$(MACOS_CXX) $(NDK_MACOS_ICU_CFLAGS) -DU_DISABLE_RENAMING=1 tests/ndk_log_icu.cpp -o $@ $(MACOS_OUT)/liblog.so $(MACOS_OUT)/libicu.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-midi-camera-xa: tests/ndk_midi_camera_xa.cpp $(MACOS_OUT)/libamidi.so $(MACOS_OUT)/libcamera2ndk.so $(MACOS_OUT)/libOpenMAXAL.so
	$(MACOS_CXX) tests/ndk_midi_camera_xa.cpp -o $@ $(MACOS_OUT)/libamidi.so $(MACOS_OUT)/libcamera2ndk.so $(MACOS_OUT)/libOpenMAXAL.so $(MACOS_RPATH) -pthread

$(MACOS_OUT)/test-ndk-binder-nnapi: tests/ndk_binder_nnapi.cpp $(MACOS_OUT)/libbinder_ndk.so $(MACOS_OUT)/libneuralnetworks.so
	$(MACOS_CXX) tests/ndk_binder_nnapi.cpp -o $@ $(MACOS_OUT)/libbinder_ndk.so $(MACOS_OUT)/libneuralnetworks.so $(MACOS_RPATH) -pthread

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

build/test-ndk-log-icu: tests/ndk_log_icu.cpp build/liblog.so build/libicu.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $(NDK_ICU_CFLAGS) -DU_DISABLE_RENAMING=1 tests/ndk_log_icu.cpp -o $@ -Lbuild -llog -licu -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-midi-camera-xa: tests/ndk_midi_camera_xa.cpp build/libamidi.so build/libcamera2ndk.so build/libOpenMAXAL.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ndk_midi_camera_xa.cpp -o $@ -Lbuild -lamidi -lcamera2ndk -lOpenMAXAL -Wl,-rpath,'$$ORIGIN' -pthread

build/test-ndk-binder-nnapi: tests/ndk_binder_nnapi.cpp build/libbinder_ndk.so build/libneuralnetworks.so
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/ndk_binder_nnapi.cpp -o $@ -Lbuild -lbinder_ndk -lneuralnetworks -Wl,-rpath,'$$ORIGIN' -pthread

.PHONY: ndk-build ndk-test macos-ndk-build macos-ndk-test ndk-audit

ndk-build: $(NDK_LINUX_LIBS)

ndk-test: build/test-ndk-standalone build/test-ndk-core-app build/test-ndk-graphics-system build/test-ndk-mediandk build/test-ndk-aaudio build/test-ndk-log-icu build/test-ndk-midi-camera-xa build/test-ndk-binder-nnapi
	./build/test-ndk-standalone
	./build/test-ndk-core-app
	./build/test-ndk-graphics-system
	./build/test-ndk-mediandk
	./build/test-ndk-aaudio
	./build/test-ndk-log-icu
	./build/test-ndk-midi-camera-xa
	./build/test-ndk-binder-nnapi

macos-ndk-build: $(NDK_MACOS_LIBS)

macos-ndk-test: $(MACOS_OUT)/test-ndk-standalone $(MACOS_OUT)/test-ndk-core-app $(MACOS_OUT)/test-ndk-graphics-system $(MACOS_OUT)/test-ndk-mediandk $(MACOS_OUT)/test-ndk-aaudio $(MACOS_OUT)/test-ndk-log-icu $(MACOS_OUT)/test-ndk-midi-camera-xa $(MACOS_OUT)/test-ndk-binder-nnapi
	$(MACOS_OUT)/test-ndk-standalone
	$(MACOS_OUT)/test-ndk-core-app
	$(MACOS_OUT)/test-ndk-graphics-system
	$(MACOS_OUT)/test-ndk-mediandk
	$(MACOS_OUT)/test-ndk-aaudio
	$(MACOS_OUT)/test-ndk-log-icu
	$(MACOS_OUT)/test-ndk-midi-camera-xa
	$(MACOS_OUT)/test-ndk-binder-nnapi

ndk-audit:
	python3 scripts/audit_ndk_apis.py
