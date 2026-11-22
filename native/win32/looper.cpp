// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <atomic>
#include <cstdint>
#include <mutex>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

extern "C" {

enum {
    SHIRAZ_WIN32_LOOPER_POLL_WAKE = -1,
    SHIRAZ_WIN32_LOOPER_POLL_CALLBACK = -2,
    SHIRAZ_WIN32_LOOPER_POLL_TIMEOUT = -3,
    SHIRAZ_WIN32_LOOPER_POLL_ERROR = -4,
};

enum {
    SHIRAZ_WIN32_LOOPER_EVENT_INPUT = 1 << 0,
    SHIRAZ_WIN32_LOOPER_EVENT_OUTPUT = 1 << 1,
    SHIRAZ_WIN32_LOOPER_EVENT_ERROR = 1 << 2,
    SHIRAZ_WIN32_LOOPER_EVENT_HANGUP = 1 << 3,
};

typedef int (*ShirazWin32LooperCallback)(void* handle, int events, void* data);

struct ShirazWin32Looper {
    struct Entry {
        HANDLE handle = nullptr;
        int ident = 0;
        int events = 0;
        ShirazWin32LooperCallback callback = nullptr;
        void* data = nullptr;
    };

    HANDLE wakeEvent = nullptr;
    std::atomic<int> refs{1};
    std::mutex mutex;
    std::vector<Entry> entries;

    ShirazWin32Looper() {
        wakeEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    }

    ~ShirazWin32Looper() {
        if (wakeEvent)
            CloseHandle(wakeEvent);
    }
};

namespace {
thread_local ShirazWin32Looper* tl_looper = nullptr;
} // namespace

SHIRAZ_EXPORT ShirazWin32Looper* shiraz_win32_looper_for_thread() {
    return tl_looper;
}

SHIRAZ_EXPORT ShirazWin32Looper* shiraz_win32_looper_prepare() {
    if (!tl_looper)
        tl_looper = new ShirazWin32Looper();
    return tl_looper;
}

SHIRAZ_EXPORT void shiraz_win32_looper_acquire(ShirazWin32Looper* looper) {
    if (looper)
        looper->refs.fetch_add(1, std::memory_order_relaxed);
}

SHIRAZ_EXPORT void shiraz_win32_looper_release(ShirazWin32Looper* looper) {
    if (!looper)
        return;
    if (looper->refs.fetch_sub(1, std::memory_order_acq_rel) == 1) {
        if (tl_looper == looper)
            tl_looper = nullptr;
        delete looper;
    }
}

SHIRAZ_EXPORT void shiraz_win32_looper_wake(ShirazWin32Looper* looper) {
    if (looper && looper->wakeEvent)
        SetEvent(looper->wakeEvent);
}

SHIRAZ_EXPORT int shiraz_win32_looper_add_handle(ShirazWin32Looper* looper, void* handle, int ident,
                                                 int events, ShirazWin32LooperCallback callback,
                                                 void* data) {
    if (!looper || !handle)
        return -1;
    std::lock_guard<std::mutex> lock(looper->mutex);
    if (looper->entries.size() >= MAXIMUM_WAIT_OBJECTS - 1)
        return -1;
    for (auto& e : looper->entries) {
        if (e.handle == handle) {
            e.ident = ident;
            e.events = events;
            e.callback = callback;
            e.data = data;
            return 1;
        }
    }
    looper->entries.push_back({static_cast<HANDLE>(handle), ident, events, callback, data});
    return 1;
}

SHIRAZ_EXPORT int shiraz_win32_looper_remove_handle(ShirazWin32Looper* looper, void* handle) {
    if (!looper || !handle)
        return -1;
    std::lock_guard<std::mutex> lock(looper->mutex);
    for (auto it = looper->entries.begin(); it != looper->entries.end(); ++it) {
        if (it->handle == handle) {
            looper->entries.erase(it);
            return 1;
        }
    }
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_looper_poll_once(int timeoutMillis, void** outHandle, int* outEvents,
                                                void** outData) {
    ShirazWin32Looper* looper = tl_looper;
    if (!looper || !looper->wakeEvent)
        return SHIRAZ_WIN32_LOOPER_POLL_ERROR;

    std::vector<HANDLE> handles;
    std::vector<ShirazWin32Looper::Entry> snapshot;
    {
        std::lock_guard<std::mutex> lock(looper->mutex);
        handles.reserve(looper->entries.size() + 1);
        handles.push_back(looper->wakeEvent);
        snapshot = looper->entries;
        for (const auto& e : snapshot)
            handles.push_back(e.handle);
    }

    const DWORD waitMs = timeoutMillis < 0 ? INFINITE : static_cast<DWORD>(timeoutMillis);
    const DWORD res =
        WaitForMultipleObjects(static_cast<DWORD>(handles.size()), handles.data(), FALSE, waitMs);
    if (res == WAIT_TIMEOUT)
        return SHIRAZ_WIN32_LOOPER_POLL_TIMEOUT;
    if (res == WAIT_OBJECT_0) {
        ResetEvent(looper->wakeEvent);
        return SHIRAZ_WIN32_LOOPER_POLL_WAKE;
    }
    if (res > WAIT_OBJECT_0 && res < WAIT_OBJECT_0 + handles.size()) {
        const std::size_t idx = static_cast<std::size_t>(res - WAIT_OBJECT_0 - 1);
        const auto entry = snapshot[idx];
        if (entry.callback) {
            const int keep =
                entry.callback(entry.handle, SHIRAZ_WIN32_LOOPER_EVENT_INPUT, entry.data);
            if (keep == 0)
                shiraz_win32_looper_remove_handle(looper, entry.handle);
            return SHIRAZ_WIN32_LOOPER_POLL_CALLBACK;
        }
        if (outHandle)
            *outHandle = entry.handle;
        if (outEvents)
            *outEvents = SHIRAZ_WIN32_LOOPER_EVENT_INPUT;
        if (outData)
            *outData = entry.data;
        return entry.ident;
    }
    return SHIRAZ_WIN32_LOOPER_POLL_ERROR;
}

} // extern "C"
#endif
