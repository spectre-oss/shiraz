// SPDX-License-Identifier: GPL-3.0-only
// Native Windows display refresh / Choreographer pacing over DwmFlush()
// with CreateWaitableTimerExW high-resolution timer fallback.
#include "shiraz/platform.hpp"

#include <cstdint>
#include <cstring>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#ifndef CREATE_WAITABLE_TIMER_HIGH_RESOLUTION
#define CREATE_WAITABLE_TIMER_HIGH_RESOLUTION 0x00000002
#endif

namespace {

using PFN_DwmIsCompositionEnabled = HRESULT(WINAPI*)(BOOL*);
using PFN_DwmFlush = HRESULT(WINAPI*)();

std::int64_t qpc_now_ns() {
    LARGE_INTEGER freq{}, counter{};
    QueryPerformanceFrequency(&freq);
    QueryPerformanceCounter(&counter);
    if (freq.QuadPart <= 0)
        return 0;
    const std::int64_t whole = (counter.QuadPart / freq.QuadPart) * 1000000000LL;
    const std::int64_t rem = ((counter.QuadPart % freq.QuadPart) * 1000000000LL) / freq.QuadPart;
    return whole + rem;
}

struct DwmApi {
    HMODULE module = nullptr;
    PFN_DwmIsCompositionEnabled isEnabled = nullptr;
    PFN_DwmFlush flush = nullptr;

    DwmApi() {
        module = LoadLibraryW(L"dwmapi.dll");
        if (!module)
            return;
        FARPROC rawEnabled = GetProcAddress(module, "DwmIsCompositionEnabled");
        FARPROC rawFlush = GetProcAddress(module, "DwmFlush");
        if (rawEnabled)
            std::memcpy(&isEnabled, &rawEnabled, sizeof(isEnabled));
        if (rawFlush)
            std::memcpy(&flush, &rawFlush, sizeof(flush));
    }

    bool tryFlush() const {
        if (!isEnabled || !flush)
            return false;
        BOOL enabled = FALSE;
        if (FAILED(isEnabled(&enabled)) || !enabled)
            return false;
        return SUCCEEDED(flush());
    }
};

const DwmApi& dwm_api() {
    static DwmApi api;
    return api;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT double shiraz_win32_choreographer_refresh_hz() {
    DEVMODEW mode{};
    mode.dmSize = sizeof(mode);
    if (EnumDisplaySettingsW(nullptr, ENUM_CURRENT_SETTINGS, &mode) &&
        mode.dmDisplayFrequency >= 24 && mode.dmDisplayFrequency <= 480) {
        return static_cast<double>(mode.dmDisplayFrequency);
    }
    return 60.0;
}

SHIRAZ_EXPORT std::int64_t shiraz_win32_choreographer_period_ns() {
    const double hz = shiraz_win32_choreographer_refresh_hz();
    return static_cast<std::int64_t>(1000000000.0 / (hz > 1.0 ? hz : 60.0));
}

SHIRAZ_EXPORT int shiraz_win32_choreographer_wait_vsync(std::int64_t* outTimestampNs) {
    if (dwm_api().tryFlush()) {
        if (outTimestampNs)
            *outTimestampNs = qpc_now_ns();
        return 0;
    }

    // Fallback: wait until the next quantized frame boundary using a high-res waitable timer.
    const std::int64_t periodNs = shiraz_win32_choreographer_period_ns();
    const std::int64_t nowNs = qpc_now_ns();
    const std::int64_t remainingNs = periodNs - (nowNs % periodNs);
    const std::int64_t delay100ns = remainingNs > 100 ? (remainingNs / 100) : 1;

    HANDLE timer = CreateWaitableTimerExW(nullptr, nullptr, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,
                                          TIMER_ALL_ACCESS);
    if (!timer) {
        timer = CreateWaitableTimerW(nullptr, TRUE, nullptr);
    }
    if (timer) {
        LARGE_INTEGER due{};
        due.QuadPart = -delay100ns;
        if (SetWaitableTimer(timer, &due, 0, nullptr, nullptr, FALSE)) {
            WaitForSingleObject(timer, 100);
        }
        CloseHandle(timer);
    } else {
        Sleep(static_cast<DWORD>((remainingNs + 999999LL) / 1000000LL));
    }

    if (outTimestampNs)
        *outTimestampNs = qpc_now_ns();
    return 0;
}

} // extern "C"
#endif
