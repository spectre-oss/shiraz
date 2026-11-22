// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstdint>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

extern "C" {

SHIRAZ_EXPORT std::uint32_t shiraz_win32_pid() {
    return static_cast<std::uint32_t>(GetCurrentProcessId());
}

SHIRAZ_EXPORT std::uint32_t shiraz_win32_tid() {
    return static_cast<std::uint32_t>(GetCurrentThreadId());
}

SHIRAZ_EXPORT std::int64_t shiraz_win32_monotonic_ns() {
    LARGE_INTEGER freq{};
    LARGE_INTEGER counter{};
    if (!QueryPerformanceFrequency(&freq) || freq.QuadPart <= 0 ||
        !QueryPerformanceCounter(&counter)) {
        return static_cast<std::int64_t>(GetTickCount64()) * 1000000LL;
    }
    const std::int64_t sec = counter.QuadPart / freq.QuadPart;
    const std::int64_t rem = counter.QuadPart % freq.QuadPart;
    return sec * 1000000000LL + (rem * 1000000000LL) / freq.QuadPart;
}

SHIRAZ_EXPORT std::int64_t shiraz_win32_boottime_ns() {
    return static_cast<std::int64_t>(GetTickCount64()) * 1000000LL;
}

SHIRAZ_EXPORT int shiraz_win32_memory_info(std::uint64_t* totalBytes, std::uint64_t* availBytes) {
    MEMORYSTATUSEX status{};
    status.dwLength = sizeof(status);
    if (!GlobalMemoryStatusEx(&status))
        return -1;
    if (totalBytes)
        *totalBytes = static_cast<std::uint64_t>(status.ullTotalPhys);
    if (availBytes)
        *availBytes = static_cast<std::uint64_t>(status.ullAvailPhys);
    return 0;
}

SHIRAZ_EXPORT std::uint32_t shiraz_win32_page_size() {
    SYSTEM_INFO info{};
    GetSystemInfo(&info);
    return static_cast<std::uint32_t>(info.dwPageSize ? info.dwPageSize : 4096);
}

} // extern "C"
#endif
