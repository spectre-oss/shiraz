// SPDX-License-Identifier: GPL-3.0-only
// Native Windows sync fence implementation over Win32 manual-reset HANDLE events
// supporting wait, signal, dup, and multi-fence merge.
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

namespace {

constexpr int kFenceFdBase = 0x6000;

struct Win32Fence {
    std::vector<HANDLE> events; // All events must be signaled for the fence to complete.
};

std::mutex g_fenceMutex;
std::unordered_map<int, Win32Fence> g_fences;
int g_nextFenceFd = kFenceFdBase;

bool duplicate_events(const std::vector<HANDLE>& src, std::vector<HANDLE>& dst) {
    for (HANDLE h : src) {
        HANDLE dup = nullptr;
        if (!DuplicateHandle(GetCurrentProcess(), h, GetCurrentProcess(), &dup, 0, FALSE,
                             DUPLICATE_SAME_ACCESS)) {
            for (HANDLE created : dst)
                CloseHandle(created);
            dst.clear();
            return false;
        }
        dst.push_back(dup);
    }
    return true;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_fence_create(int signaled) {
    HANDLE ev = CreateEventW(nullptr, TRUE, signaled ? TRUE : FALSE, nullptr);
    if (!ev)
        return -ENOMEM;
    std::lock_guard<std::mutex> lock(g_fenceMutex);
    const int fd = g_nextFenceFd++;
    g_fences.emplace(fd, Win32Fence{{ev}});
    return fd;
}

SHIRAZ_EXPORT int shiraz_win32_fence_signal(int fd) {
    std::vector<HANDLE> handles;
    {
        std::lock_guard<std::mutex> lock(g_fenceMutex);
        auto it = g_fences.find(fd);
        if (it == g_fences.end())
            return -EBADF;
        handles = it->second.events;
    }
    for (HANDLE h : handles) {
        if (!SetEvent(h))
            return -EIO;
    }
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_fence_wait(int fd, int timeoutMs) {
    std::vector<HANDLE> handles;
    {
        std::lock_guard<std::mutex> lock(g_fenceMutex);
        auto it = g_fences.find(fd);
        if (it == g_fences.end())
            return -EBADF;
        if (!duplicate_events(it->second.events, handles))
            return -ENOMEM;
    }

    int result = 0;
    const DWORD waitMs = timeoutMs < 0 ? INFINITE : static_cast<DWORD>(timeoutMs);
    if (handles.size() == 1) {
        const DWORD rc = WaitForSingleObject(handles[0], waitMs);
        if (rc == WAIT_OBJECT_0)
            result = 0;
        else if (rc == WAIT_TIMEOUT)
            result = -ETIMEDOUT;
        else
            result = -EIO;
    } else if (!handles.empty()) {
        const DWORD count = static_cast<DWORD>(
            handles.size() > MAXIMUM_WAIT_OBJECTS ? MAXIMUM_WAIT_OBJECTS : handles.size());
        const DWORD rc = WaitForMultipleObjects(count, handles.data(), TRUE, waitMs);
        if (rc >= WAIT_OBJECT_0 && rc < WAIT_OBJECT_0 + count)
            result = 0;
        else if (rc == WAIT_TIMEOUT)
            result = -ETIMEDOUT;
        else
            result = -EIO;
    }

    for (HANDLE h : handles)
        CloseHandle(h);
    return result;
}

SHIRAZ_EXPORT int shiraz_win32_fence_merge(const char* /*name*/, int fd1, int fd2) {
    std::lock_guard<std::mutex> lock(g_fenceMutex);
    auto it1 = g_fences.find(fd1);
    auto it2 = g_fences.find(fd2);
    if (it1 == g_fences.end() || it2 == g_fences.end())
        return -EBADF;
    if (it1->second.events.size() + it2->second.events.size() > MAXIMUM_WAIT_OBJECTS)
        return -EINVAL;

    std::vector<HANDLE> merged;
    merged.reserve(it1->second.events.size() + it2->second.events.size());
    if (!duplicate_events(it1->second.events, merged))
        return -ENOMEM;
    if (!duplicate_events(it2->second.events, merged))
        return -ENOMEM;

    const int fd = g_nextFenceFd++;
    g_fences.emplace(fd, Win32Fence{std::move(merged)});
    return fd;
}

SHIRAZ_EXPORT int shiraz_win32_fence_dup(int fd) {
    std::lock_guard<std::mutex> lock(g_fenceMutex);
    auto it = g_fences.find(fd);
    if (it == g_fences.end())
        return -EBADF;
    std::vector<HANDLE> copied;
    if (!duplicate_events(it->second.events, copied))
        return -ENOMEM;
    const int next = g_nextFenceFd++;
    g_fences.emplace(next, Win32Fence{std::move(copied)});
    return next;
}

SHIRAZ_EXPORT int shiraz_win32_fence_close(int fd) {
    std::vector<HANDLE> toClose;
    {
        std::lock_guard<std::mutex> lock(g_fenceMutex);
        auto it = g_fences.find(fd);
        if (it == g_fences.end())
            return -EBADF;
        toClose = std::move(it->second.events);
        g_fences.erase(it);
    }
    for (HANDLE h : toClose)
        CloseHandle(h);
    return 0;
}

} // extern "C"
#endif
