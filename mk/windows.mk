# SPDX-License-Identifier: GPL-3.0-only
# Native Windows (MinGW-w64 / Clang-UCRT64) and cross-platform host build rules.

WINDOWS_OUT ?= build/windows
WINDOWS_CXX ?= g++
WINDOWS_PYTHON ?= $(shell command -v python3 >/dev/null 2>&1 && echo python3 || echo python)
WINDOWS_CXXFLAGS ?= -O2 -g -std=c++17 -Wall -Wextra -Wpedantic -Werror -DNOMINMAX -Iinclude -Ibuild

WINDOWS_HOST_SOURCES := \
	native/win32/d3d11_probe.cpp \
	native/win32/native_window.cpp \
	native/win32/shared_memory.cpp \
	native/win32/looper.cpp \
	native/win32/host_process.cpp \
	native/win32/ipc_channel.cpp \
	native/win32/notifications.cpp \
	native/win32/audio_output.cpp \
	native/win32/system_fonts.cpp \
	native/win32/video_codec.cpp \
	native/win32/location.cpp \
	native/win32/hardware_buffer.cpp \
	native/win32/sync_fence.cpp \
	native/win32/choreographer.cpp \
	native/win32/process_spawn.cpp \
	native/win32/platform_services.cpp \
	native/win32/sandbox.cpp

WINDOWS_HOST_HEADERS := \
	include/shiraz/platform.hpp \
	include/shiraz/win32/d3d11_probe.hpp

WINDOWS_LIBS := -lole32 -ld3d11 -ldxgi -ldwrite -lmf -lmfplat -lmfuuid -lshell32 -luser32 -lkernel32 -ladvapi32 -liphlpapi -lcrypt32 -luuid

.PHONY: windows-build windows-check windows-presentation-check windows-app-bundle-check cross-platform-native-check

$(WINDOWS_OUT)/.dir:
	@mkdir -p $(WINDOWS_OUT)
	@touch $@

$(WINDOWS_OUT)/shiraz-win32-host.dll: $(WINDOWS_HOST_SOURCES) $(WINDOWS_HOST_HEADERS) $(WINDOWS_OUT)/.dir
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) -shared -Wl,--out-implib,$(WINDOWS_OUT)/libshiraz-win32-host.dll.a $(WINDOWS_HOST_SOURCES) -o $@ $(WINDOWS_LIBS)

$(WINDOWS_OUT)/test-win32-platform.exe: tests/win32/platform_test.cpp $(WINDOWS_HOST_SOURCES) $(WINDOWS_HOST_HEADERS) $(WINDOWS_OUT)/.dir
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) tests/win32/platform_test.cpp $(WINDOWS_HOST_SOURCES) -o $@ $(WINDOWS_LIBS)

$(WINDOWS_OUT)/libaaudio.dll: native/ndk_aaudio.cpp include/shiraz/ndk/aaudio.h $(WINDOWS_OUT)/shiraz-win32-host.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) -shared -Wl,--export-all-symbols -Wl,--out-implib,$(WINDOWS_OUT)/libaaudio.dll.a native/ndk_aaudio.cpp -o $@ -L$(WINDOWS_OUT) -lshiraz-win32-host -pthread

$(WINDOWS_OUT)/test-ndk-aaudio.exe: tests/ndk_aaudio.cpp $(WINDOWS_OUT)/libaaudio.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) tests/ndk_aaudio.cpp -o $@ -L$(WINDOWS_OUT) -laaudio -pthread

WINDOWS_JNI_INCLUDES := $(if $(JAVA_HOME),-isystem "$(JAVA_HOME)/include" -isystem "$(JAVA_HOME)/include/win32",)

$(WINDOWS_OUT)/libOpenSLES.dll: native/opensles.cpp third_party/opensles/SLES/OpenSLES.h third_party/opensles/SLES/OpenSLES_Android.h $(WINDOWS_OUT)/shiraz-win32-host.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) $(WINDOWS_JNI_INCLUDES) -isystem third_party/opensles -shared -Wl,--export-all-symbols -Wl,--out-implib,$(WINDOWS_OUT)/libOpenSLES.dll.a native/opensles.cpp -o $@ -L$(WINDOWS_OUT) -lshiraz-win32-host -pthread

$(WINDOWS_OUT)/test-opensles.exe: tests/opensles.cpp $(WINDOWS_OUT)/libOpenSLES.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) $(WINDOWS_JNI_INCLUDES) -isystem third_party/opensles tests/opensles.cpp -o $@ -L$(WINDOWS_OUT) -lOpenSLES -pthread

$(WINDOWS_OUT)/libvulkan.dll: native/ndk_vulkan.cpp include/shiraz/ndk/vulkan.h include/shiraz/native_window.h include/shiraz/hardware_buffer.h $(WINDOWS_OUT)/shiraz-win32-host.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) -shared -Wl,--export-all-symbols -Wl,--out-implib,$(WINDOWS_OUT)/libvulkan.dll.a native/ndk_vulkan.cpp -o $@ -L$(WINDOWS_OUT) -lshiraz-win32-host -pthread

$(WINDOWS_OUT)/test-ndk-vulkan.exe: tests/ndk_vulkan.cpp $(WINDOWS_OUT)/libvulkan.dll $(WINDOWS_OUT)/shiraz-win32-host.dll
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) tests/ndk_vulkan.cpp -o $@ -L$(WINDOWS_OUT) -lvulkan -lshiraz-win32-host -pthread

$(WINDOWS_OUT)/test-win32-guest-elf.exe: tests/win32/guest_elf_test.cpp native/win32/guest/elf_loader.cpp include/shiraz/win32/guest/elf_loader.hpp $(WINDOWS_OUT)/.dir
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) tests/win32/guest_elf_test.cpp native/win32/guest/elf_loader.cpp -o $@ -lkernel32

WINDOWS_SDL_CFLAGS = $(shell sdl2-config --cflags 2>/dev/null | sed 's/-Dmain=SDL_main//g')
WINDOWS_SDL_LIBS = $(shell sdl2-config --libs 2>/dev/null | sed 's/-lmingw32//g; s/-lSDL2main//g; s/-mwindows//g')

$(WINDOWS_OUT)/test-presentation.exe: tests/presentation.cpp native/presentation.cpp native/win32/d3d11_probe.cpp native/win32/gpu_stub.cpp include/shiraz/presentation.hpp include/shiraz/sdl_keyboard.hpp include/shiraz/live_input.hpp include/shiraz/win32/d3d11_probe.hpp $(WINDOWS_OUT)/.dir
	$(WINDOWS_CXX) $(WINDOWS_CXXFLAGS) $(WINDOWS_SDL_CFLAGS) tests/presentation.cpp native/presentation.cpp native/win32/d3d11_probe.cpp native/win32/gpu_stub.cpp -o $@ $(WINDOWS_SDL_LIBS) -ld3d11 -ldxgi -luser32 -lkernel32 -pthread

windows-build: $(WINDOWS_OUT)/shiraz-win32-host.dll $(WINDOWS_OUT)/test-win32-platform.exe $(WINDOWS_OUT)/libaaudio.dll $(WINDOWS_OUT)/test-ndk-aaudio.exe $(WINDOWS_OUT)/libOpenSLES.dll $(WINDOWS_OUT)/test-opensles.exe $(WINDOWS_OUT)/libvulkan.dll $(WINDOWS_OUT)/test-ndk-vulkan.exe $(WINDOWS_OUT)/test-win32-guest-elf.exe

windows-check: windows-build
	$(WINDOWS_OUT)/test-win32-platform.exe
	$(WINDOWS_OUT)/test-ndk-aaudio.exe
	$(WINDOWS_OUT)/test-opensles.exe
	$(WINDOWS_OUT)/test-ndk-vulkan.exe
	$(WINDOWS_OUT)/test-win32-guest-elf.exe

windows-presentation-check: $(WINDOWS_OUT)/test-presentation.exe
	@mkdir -p $(WINDOWS_OUT)/tmp
	TMPDIR=$(WINDOWS_OUT)/tmp SDL_VIDEODRIVER=dummy SDL_RENDER_DRIVER=software $(WINDOWS_OUT)/test-presentation.exe
	TMPDIR=$(WINDOWS_OUT)/tmp env -u SDL_VIDEODRIVER -u SDL_RENDER_DRIVER SHIRAZ_HOST_SDL_VIDEODRIVER=dummy SHIRAZ_HOST_SDL_RENDER_DRIVER=software $(WINDOWS_OUT)/test-presentation.exe

windows-app-bundle-check:
	$(WINDOWS_PYTHON) -c "import sys, unittest; sys.path[:0]=['src','tests']; res=unittest.main(module='test_build_windows_app', argv=['unittest','-v'], exit=False); sys.exit(0 if res.result.wasSuccessful() else 1)"

build/test-cross-platform-native: tests/win32/platform_test.cpp $(WINDOWS_HOST_HEADERS)
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/win32/platform_test.cpp -o $@

build/test-win32-guest-elf: tests/win32/guest_elf_test.cpp native/win32/guest/elf_loader.cpp include/shiraz/win32/guest/elf_loader.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/win32/guest_elf_test.cpp native/win32/guest/elf_loader.cpp -o $@

cross-platform-native-check: build/test-cross-platform-native build/test-win32-guest-elf
	./build/test-cross-platform-native
	./build/test-win32-guest-elf

build/test-sandbox-linux: tests/sandbox_linux.cpp native/sandbox_linux.cpp include/shiraz/sandbox.hpp
	@mkdir -p build
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) tests/sandbox_linux.cpp native/sandbox_linux.cpp -o $@

.PHONY: sandbox-linux-check
sandbox-linux-check: build/test-sandbox-linux
	./build/test-sandbox-linux
