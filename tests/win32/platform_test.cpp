// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"
#include "shiraz/win32/d3d11_probe.hpp"

#include <cassert>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <thread>

extern "C" {
int shiraz_win32_shm_create(const char* name, std::size_t size);
std::size_t shiraz_win32_shm_get_size(int fd);
int shiraz_win32_shm_set_prot(int fd, int prot);
void* shiraz_win32_shm_map(int fd, int prot);
int shiraz_win32_shm_unmap(void* addr);
int shiraz_win32_shm_dup(int fd);
int shiraz_win32_shm_close(int fd);

struct ShirazWin32Looper;
ShirazWin32Looper* shiraz_win32_looper_prepare();
ShirazWin32Looper* shiraz_win32_looper_for_thread();
void shiraz_win32_looper_wake(ShirazWin32Looper* looper);
int shiraz_win32_looper_poll_once(int timeoutMillis, void** outHandle, int* outEvents,
                                  void** outData);
void shiraz_win32_looper_release(ShirazWin32Looper* looper);

std::uint32_t shiraz_win32_pid();
std::uint32_t shiraz_win32_tid();
std::int64_t shiraz_win32_monotonic_ns();
std::int64_t shiraz_win32_boottime_ns();
int shiraz_win32_memory_info(std::uint64_t* totalBytes, std::uint64_t* availBytes);
std::uint32_t shiraz_win32_page_size();

void* shiraz_win32_ipc_listen(const char* name);
int shiraz_win32_ipc_accept(void* serverPipe);
void* shiraz_win32_ipc_connect(const char* name);
int shiraz_win32_ipc_peer_pid(void* serverPipe, std::uint32_t* outPid);
int shiraz_win32_ipc_send(void* handle, const void* payload, std::size_t length);
int shiraz_win32_ipc_recv(void* handle, void* buffer, std::size_t capacity, std::size_t* outLength);
void shiraz_win32_ipc_close(void* handle);

std::uint32_t shiraz_win32_notifications_post(std::uint32_t replaceId, const char* title,
                                              const char* body, int urgency, int silent);
int shiraz_win32_notifications_cancel(std::uint32_t id);
std::size_t shiraz_win32_notifications_active_count();

int shiraz_win32_audio_probe_default(std::uint32_t* outSampleRate, std::uint16_t* outChannels);

typedef void (*ShirazWin32FontCallback)(const char* pathUtf8, std::uint32_t faceIndex, void* user);
std::size_t shiraz_win32_enumerate_system_fonts(ShirazWin32FontCallback cb, void* user);

int shiraz_win32_video_has_decoder(const char* mime);
int shiraz_win32_location_status();
}
#endif

int main() {
    // 1. Portable cross-platform checks (run on Linux, macOS, and Windows).
    assert(std::strcmp(shiraz::host_os_name(), "linux") == 0 ||
           std::strcmp(shiraz::host_os_name(), "darwin") == 0 ||
           std::strcmp(shiraz::host_os_name(), "windows") == 0);
    assert(shiraz::host_default_page_size() == 4096 || shiraz::host_default_page_size() == 16384);

    assert(std::strcmp(shiraz::win32_vendor_name(0x10DE), "NVIDIA") == 0);
    assert(std::strcmp(shiraz::win32_vendor_name(0x1002), "AMD") == 0);
    assert(std::strcmp(shiraz::win32_vendor_name(0x8086), "Intel") == 0);
    assert(std::strcmp(shiraz::win32_vendor_name(0x1414), "Microsoft") == 0);
    assert(std::strcmp(shiraz::win32_vendor_name(0x5143), "Qualcomm") == 0);

    assert(shiraz::win32_dpi_to_android_density(96) == 160);
    assert(shiraz::win32_dpi_to_android_density(144) == 240);
    assert(shiraz::win32_dpi_to_android_density(192) == 320);
    assert(shiraz::win32_dpi_to_android_density(0) == 160);

    assert(shiraz::win32_allow_touch_device_type(-1));
    assert(shiraz::win32_allow_touch_device_type(0));
    assert(!shiraz::win32_allow_touch_device_type(1));
    assert(!shiraz::win32_allow_touch_device_type(2));

    assert(shiraz::win32_pinch_scroll_delta(2, 0.1f) > 1.0f);
    assert(shiraz::win32_pinch_scroll_delta(2, -0.1f) < -1.0f);
    assert(shiraz::win32_pinch_scroll_delta(3, 0.1f) == 0.0f);
    assert(shiraz::win32_pinch_scroll_delta(2, 0.0f) == 0.0f);
    assert(shiraz::win32_pinch_scroll_delta(2, NAN) == 0.0f);

#if defined(_WIN32)
    // 2. Native Win32 host checks (run on Windows hosts and windows-latest CI).
    assert(std::strcmp(shiraz::host_os_name(), "windows") == 0);

    // DXGI GPU probe + power guard
    char device[256] = {};
    char vendor[128] = {};
    const bool hasGpu =
        shiraz::win32_d3d11_device_info(device, sizeof(device), vendor, sizeof(vendor));
    assert(hasGpu);
    assert(device[0] != '\0');
    assert(vendor[0] != '\0');
    std::printf("WIN32_GPU device=%s vendor=%s\n", device, vendor);
    {
        shiraz::Win32PowerGuard guard;
        assert(guard.active());
    }

    // Host process, clock, memory, page size
    assert(shiraz_win32_pid() > 0);
    assert(shiraz_win32_tid() > 0);
    const std::int64_t t0 = shiraz_win32_monotonic_ns();
    const std::int64_t t1 = shiraz_win32_monotonic_ns();
    assert(t0 > 0 && t1 >= t0);
    assert(shiraz_win32_boottime_ns() > 0);
    std::uint64_t totalMem = 0, availMem = 0;
    assert(shiraz_win32_memory_info(&totalMem, &availMem) == 0);
    assert(totalMem > 0 && availMem <= totalMem);
    assert(shiraz_win32_page_size() == 4096);

    // ASharedMemory over CreateFileMappingW
    const int shm = shiraz_win32_shm_create("test", 8192);
    assert(shm >= 0);
    assert(shiraz_win32_shm_get_size(shm) == 8192);
    auto* rw = static_cast<std::uint8_t*>(shiraz_win32_shm_map(shm, 0x3));
    assert(rw != nullptr);
    rw[0] = 0x5A;
    rw[8191] = 0xA5;
    assert(shiraz_win32_shm_unmap(rw) == 0);
    assert(shiraz_win32_shm_set_prot(shm, 0x1) == 0);
    assert(shiraz_win32_shm_set_prot(shm, 0x3) < 0); // cannot re-widen
    assert(shiraz_win32_shm_map(shm, 0x3) == nullptr);
    auto* ro = static_cast<const std::uint8_t*>(shiraz_win32_shm_map(shm, 0x1));
    assert(ro != nullptr && ro[0] == 0x5A && ro[8191] == 0xA5);
    assert(shiraz_win32_shm_unmap(const_cast<std::uint8_t*>(ro)) == 0);
    const int dup = shiraz_win32_shm_dup(shm);
    assert(dup >= 0 && dup != shm);
    assert(shiraz_win32_shm_close(shm) == 0);
    assert(shiraz_win32_shm_close(dup) == 0);

    // ALooper wake and timeout
    auto* looper = shiraz_win32_looper_prepare();
    assert(looper != nullptr && shiraz_win32_looper_for_thread() == looper);
    assert(shiraz_win32_looper_poll_once(1, nullptr, nullptr, nullptr) == -3); // timeout
    shiraz_win32_looper_wake(looper);
    assert(shiraz_win32_looper_poll_once(100, nullptr, nullptr, nullptr) == -1); // wake
    shiraz_win32_looper_release(looper);

    // Named Pipe IPC with peer PID check
    const std::string pipeName = "test-" + std::to_string(shiraz_win32_pid());
    void* server = shiraz_win32_ipc_listen(pipeName.c_str());
    assert(server != nullptr);
    std::thread clientThread([&]() {
        void* client = shiraz_win32_ipc_connect(pipeName.c_str());
        assert(client != nullptr);
        const char msg[] = "shiraz-win32-ipc";
        assert(shiraz_win32_ipc_send(client, msg, sizeof(msg) - 1) == 0);
        shiraz_win32_ipc_close(client);
    });
    assert(shiraz_win32_ipc_accept(server) == 0);
    std::uint32_t peerPid = 0;
    assert(shiraz_win32_ipc_peer_pid(server, &peerPid) == 0);
    assert(peerPid == shiraz_win32_pid());
    char buf[64] = {};
    std::size_t got = 0;
    assert(shiraz_win32_ipc_recv(server, buf, sizeof(buf), &got) == 0);
    assert(got == 16 && std::memcmp(buf, "shiraz-win32-ipc", 16) == 0);
    shiraz_win32_ipc_close(server);
    clientThread.join();

    // Notifications registry & headless safety
    const std::uint32_t nid = shiraz_win32_notifications_post(0, "Shiraz", "Hello Windows", 1, 1);
    assert(nid > 0);
    assert(shiraz_win32_notifications_active_count() == 1);
    assert(shiraz_win32_notifications_cancel(nid) == 1);
    assert(shiraz_win32_notifications_active_count() == 0);

    // DirectWrite system fonts
    const std::size_t fontCount = shiraz_win32_enumerate_system_fonts(nullptr, nullptr);
    assert(fontCount > 0);
    std::printf("WIN32_FONTS count=%zu\n", fontCount);

    // Media Foundation H.264 decoder probe + WASAPI + Location (headless-safe)
    const int hasH264 = shiraz_win32_video_has_decoder("video/avc");
    std::uint32_t rate = 0;
    std::uint16_t ch = 0;
    const int audioProbe = shiraz_win32_audio_probe_default(&rate, &ch);
    const int locStatus = shiraz_win32_location_status();
    std::printf("WIN32_MEDIA h264=%d audio_probe=%d rate=%u ch=%u location_status=%d\n", hasH264,
                audioProbe, rate, ch, locStatus);
    assert(locStatus >= 0 && locStatus <= 3);
#endif

    std::printf("CROSS_PLATFORM_NATIVE_PASS os=%s arch=%s\n", shiraz::host_os_name(),
                shiraz::host_arch_name());
    return 0;
}
