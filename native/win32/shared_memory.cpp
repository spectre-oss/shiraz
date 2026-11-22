// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <unordered_map>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

namespace {

constexpr int kProtRead = 0x1;
constexpr int kProtWrite = 0x2;
constexpr int kProtMask = kProtRead | kProtWrite;
constexpr int kHandleBase = 0x4000;

struct SharedRegion {
    HANDLE mapping = nullptr;
    std::size_t size = 0;
    int prot = kProtRead | kProtWrite;
};

std::mutex g_mutex;
std::unordered_map<int, SharedRegion> g_regions;
int g_nextHandle = kHandleBase;

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_shm_create(const char* /*name*/, std::size_t size) {
    if (size == 0)
        return -EINVAL;
    const std::uint64_t sz = static_cast<std::uint64_t>(size);
    const DWORD high = static_cast<DWORD>((sz >> 32) & 0xFFFFFFFFu);
    const DWORD low = static_cast<DWORD>(sz & 0xFFFFFFFFu);
    HANDLE h =
        CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, high, low, nullptr);
    if (!h)
        return -ENOMEM;

    std::lock_guard<std::mutex> lock(g_mutex);
    const int fd = g_nextHandle++;
    g_regions.emplace(fd, SharedRegion{h, size, kProtRead | kProtWrite});
    return fd;
}

SHIRAZ_EXPORT std::size_t shiraz_win32_shm_get_size(int fd) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_regions.find(fd);
    if (it == g_regions.end())
        return 0;
    return it->second.size;
}

SHIRAZ_EXPORT int shiraz_win32_shm_set_prot(int fd, int prot) {
    if ((prot & ~kProtMask) != 0)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_regions.find(fd);
    if (it == g_regions.end())
        return -EBADF;
    // Permissions may only be narrowed, never re-widened.
    if ((prot & ~it->second.prot) != 0)
        return -EPERM;
    it->second.prot = prot;
    return 0;
}

SHIRAZ_EXPORT void* shiraz_win32_shm_map(int fd, int prot) {
    HANDLE mapping = nullptr;
    std::size_t size = 0;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_regions.find(fd);
        if (it == g_regions.end())
            return nullptr;
        if ((prot & ~it->second.prot) != 0)
            return nullptr;
        mapping = it->second.mapping;
        size = it->second.size;
    }
    DWORD access = 0;
    if (prot & kProtWrite)
        access = FILE_MAP_ALL_ACCESS;
    else if (prot & kProtRead)
        access = FILE_MAP_READ;
    else
        return nullptr;
    return MapViewOfFile(mapping, access, 0, 0, size);
}

SHIRAZ_EXPORT int shiraz_win32_shm_unmap(void* addr) {
    if (!addr)
        return -EINVAL;
    return UnmapViewOfFile(addr) ? 0 : -EINVAL;
}

SHIRAZ_EXPORT int shiraz_win32_shm_dup(int fd) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_regions.find(fd);
    if (it == g_regions.end())
        return -EBADF;
    HANDLE dupHandle = nullptr;
    if (!DuplicateHandle(GetCurrentProcess(), it->second.mapping, GetCurrentProcess(), &dupHandle,
                         0, FALSE, DUPLICATE_SAME_ACCESS)) {
        return -ENOMEM;
    }
    const int next = g_nextHandle++;
    g_regions.emplace(next, SharedRegion{dupHandle, it->second.size, it->second.prot});
    return next;
}

SHIRAZ_EXPORT int shiraz_win32_shm_close(int fd) {
    HANDLE mapping = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_regions.find(fd);
        if (it == g_regions.end())
            return -EBADF;
        mapping = it->second.mapping;
        g_regions.erase(it);
    }
    CloseHandle(mapping);
    return 0;
}

} // extern "C"
#endif
