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

struct ShirazWin32HwBuf;
int shiraz_win32_hwbuf_allocate(std::uint32_t width, std::uint32_t height, std::uint32_t format,
                                std::uint64_t usage, ShirazWin32HwBuf** out);
void shiraz_win32_hwbuf_acquire(ShirazWin32HwBuf* buf);
void shiraz_win32_hwbuf_release(ShirazWin32HwBuf* buf);
int shiraz_win32_hwbuf_describe(const ShirazWin32HwBuf* buf, std::uint32_t* outWidth,
                                std::uint32_t* outHeight, std::uint32_t* outStride,
                                std::uint32_t* outFormat, int* outGpuBacked);
void* shiraz_win32_hwbuf_shared_handle(const ShirazWin32HwBuf* buf);
int shiraz_win32_hwbuf_lock(ShirazWin32HwBuf* buf, void** outBits);
int shiraz_win32_hwbuf_unlock(ShirazWin32HwBuf* buf);

int shiraz_win32_fence_create(int signaled);
int shiraz_win32_fence_signal(int fd);
int shiraz_win32_fence_wait(int fd, int timeoutMs);
int shiraz_win32_fence_merge(const char* name, int fd1, int fd2);
int shiraz_win32_fence_dup(int fd);
int shiraz_win32_fence_close(int fd);

double shiraz_win32_choreographer_refresh_hz();
std::int64_t shiraz_win32_choreographer_period_ns();
int shiraz_win32_choreographer_wait_vsync(std::int64_t* outTimestampNs);

int shiraz_win32_spawn_process(const char* const* argv, int argc, std::uint32_t* outPid,
                               void** outProcessHandle, void** outJobHandle);
int shiraz_win32_wait_process(void* processHandle, int timeoutMs, int* outExitCode);
void shiraz_win32_close_process(void* processHandle, void* jobHandle);

int shiraz_win32_connectivity_summary(int* outConnected, int* outType);
int shiraz_win32_dpapi_protect(const std::uint8_t* plain, std::size_t plainLen,
                               std::uint8_t* outBuf, std::size_t outCap, std::size_t* outLen);
int shiraz_win32_dpapi_unprotect(const std::uint8_t* cipher, std::size_t cipherLen,
                                 std::uint8_t* outBuf, std::size_t outCap, std::size_t* outLen);
int shiraz_win32_tts_probe();
int shiraz_win32_audio_capture_probe(std::uint32_t* outSampleRate, std::uint16_t* outChannels);
int shiraz_win32_clipboard_set_text(const char* utf8);
int shiraz_win32_clipboard_get_text(char* outBuf, std::size_t outCap, std::size_t* outLen);
int shiraz_win32_camera_probe(std::uint32_t* outDeviceCount);

void* shiraz_win32_sandbox_create_job(std::uint64_t memoryLimitBytes,
                                      std::uint32_t activeProcessLimit, int uiRestrictions);
int shiraz_win32_sandbox_create_restricted_token(void** outToken);
void shiraz_win32_sandbox_close(void* handle);
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
    const float displayHz = shiraz::win32_presentation_refresh_hz();
    assert(displayHz >= 24.0f && displayHz <= 480.0f);
    std::printf("WIN32_DISPLAY refresh_hz=%.1f\n", displayHz);
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

    // D3D11/DXGI HardwareBuffer allocate, lock/unlock, shared handle, refcount
    ShirazWin32HwBuf* hwbuf = nullptr;
    assert(shiraz_win32_hwbuf_allocate(64, 32, 1, 0x333ULL, &hwbuf) == 0);
    assert(hwbuf != nullptr);
    std::uint32_t bw = 0, bh = 0, bs = 0, bf = 0;
    int gpuBacked = 0;
    assert(shiraz_win32_hwbuf_describe(hwbuf, &bw, &bh, &bs, &bf, &gpuBacked) == 0);
    assert(bw == 64 && bh == 32 && bs >= 64 && bf == 1);
    void* bits = nullptr;
    assert(shiraz_win32_hwbuf_lock(hwbuf, &bits) == 0 && bits != nullptr);
    static_cast<std::uint8_t*>(bits)[0] = 0xDE;
    static_cast<std::uint8_t*>(bits)[1] = 0xAD;
    assert(shiraz_win32_hwbuf_unlock(hwbuf) == 0);
    shiraz_win32_hwbuf_acquire(hwbuf);
    shiraz_win32_hwbuf_release(hwbuf);
    std::printf("WIN32_HWBUF gpu=%d shared_handle=%p\n", gpuBacked,
                shiraz_win32_hwbuf_shared_handle(hwbuf));
    shiraz_win32_hwbuf_release(hwbuf);

    // Sync fences: create, timeout, signal, merge, dup, close
    const int f1 = shiraz_win32_fence_create(0);
    const int f2 = shiraz_win32_fence_create(0);
    assert(f1 >= 0 && f2 >= 0);
    assert(shiraz_win32_fence_wait(f1, 0) < 0);
    const int merged = shiraz_win32_fence_merge("test-merge", f1, f2);
    assert(merged >= 0);
    assert(shiraz_win32_fence_signal(f1) == 0);
    assert(shiraz_win32_fence_wait(f1, 0) == 0);
    assert(shiraz_win32_fence_wait(merged, 0) < 0);
    assert(shiraz_win32_fence_signal(f2) == 0);
    assert(shiraz_win32_fence_wait(merged, 100) == 0);
    const int fdup = shiraz_win32_fence_dup(merged);
    assert(fdup >= 0 && shiraz_win32_fence_wait(fdup, 0) == 0);
    assert(shiraz_win32_fence_close(fdup) == 0);
    assert(shiraz_win32_fence_close(merged) == 0);
    assert(shiraz_win32_fence_close(f2) == 0);
    assert(shiraz_win32_fence_close(f1) == 0);

    // Choreographer DwmFlush / high-res waitable timer pacing
    const double hz = shiraz_win32_choreographer_refresh_hz();
    const std::int64_t periodNs = shiraz_win32_choreographer_period_ns();
    assert(hz >= 24.0 && hz <= 480.0);
    assert(periodNs > 0);
    std::int64_t vsyncTs = 0;
    assert(shiraz_win32_choreographer_wait_vsync(&vsyncTs) == 0);
    assert(vsyncTs > 0);
    std::printf("WIN32_VSYNC hz=%.1f period_ns=%lld\n", hz, static_cast<long long>(periodNs));

    // Process spawn with Job Object kill-on-close
    const char* spawnArgv[] = {"cmd.exe", "/c", "exit", "0"};
    std::uint32_t childPid = 0;
    void* childProc = nullptr;
    void* childJob = nullptr;
    assert(shiraz_win32_spawn_process(spawnArgv, 4, &childPid, &childProc, &childJob) == 0);
    assert(childPid > 0 && childProc != nullptr && childJob != nullptr);
    int childExit = -1;
    assert(shiraz_win32_wait_process(childProc, 5000, &childExit) == 0);
    assert(childExit == 0);
    shiraz_win32_close_process(childProc, childJob);

    // Platform services: Connectivity, DPAPI protect/unprotect, TTS & capture probes
    int connUp = 0, connType = -1;
    assert(shiraz_win32_connectivity_summary(&connUp, &connType) == 0);
    const char secret[] = "shiraz-dpapi-secret-key-2026";
    std::uint8_t protectedBuf[1024] = {};
    std::size_t protectedLen = 0;
    assert(shiraz_win32_dpapi_protect(reinterpret_cast<const std::uint8_t*>(secret),
                                      sizeof(secret) - 1, protectedBuf, sizeof(protectedBuf),
                                      &protectedLen) == 0);
    assert(protectedLen > 0);
    std::uint8_t recoveredBuf[256] = {};
    std::size_t recoveredLen = 0;
    assert(shiraz_win32_dpapi_unprotect(protectedBuf, protectedLen, recoveredBuf,
                                        sizeof(recoveredBuf), &recoveredLen) == 0);
    assert(recoveredLen == sizeof(secret) - 1);
    assert(std::memcmp(recoveredBuf, secret, recoveredLen) == 0);
    const int ttsAvail = shiraz_win32_tts_probe();
    std::uint32_t capRate = 0;
    std::uint16_t capCh = 0;
    const int capProbe = shiraz_win32_audio_capture_probe(&capRate, &capCh);
    std::printf("WIN32_SERVICES conn=%d type=%d dpapi=ok tts=%d capture=%d\n", connUp, connType,
                ttsAvail, capProbe);

    // Sandbox: Job Object resource/UI limits + Restricted Token
    void* sbJob = shiraz_win32_sandbox_create_job(512ULL * 1024ULL * 1024ULL, 4, 1);
    assert(sbJob != nullptr);
    shiraz_win32_sandbox_close(sbJob);
    void* restrictedToken = nullptr;
    assert(shiraz_win32_sandbox_create_restricted_token(&restrictedToken) == 0);
    assert(restrictedToken != nullptr);
    shiraz_win32_sandbox_close(restrictedToken);

    // Native Win32 Clipboard UTF-8 <-> UTF-16 round-trip
    const char clipPayload[] = "Shiraz Win32 Clipboard — café 日本語";
    const int clipSetRc = shiraz_win32_clipboard_set_text(clipPayload);
    if (clipSetRc == 0) {
        char clipOut[256] = {};
        std::size_t clipLen = 0;
        assert(shiraz_win32_clipboard_get_text(clipOut, sizeof(clipOut), &clipLen) == 0);
        assert(clipLen == std::strlen(clipPayload));
        assert(std::strcmp(clipOut, clipPayload) == 0);
    }
    std::printf("WIN32_CLIPBOARD rc=%d\n", clipSetRc);

    // Media Foundation video-capture camera device enumeration (headless-safe: 0 devices is valid)
    std::uint32_t cameraDevices = 0;
    const int camRc = shiraz_win32_camera_probe(&cameraDevices);
    assert(camRc == 0);
    std::printf("WIN32_CAMERA rc=%d devices=%u\n", camRc, cameraDevices);
#endif

    std::printf("CROSS_PLATFORM_NATIVE_PASS os=%s arch=%s\n", shiraz::host_os_name(),
                shiraz::host_arch_name());
    return 0;
}
