// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

namespace {

constexpr DWORD kPipeBufferSize = 65536;
constexpr std::uint32_t kMaxMessageBytes = 4 * 1024 * 1024;

std::wstring pipe_path_from_name(const char* name) {
    if (!name || !name[0])
        return L"";
    std::wstring wide;
    const int needed = MultiByteToWideChar(CP_UTF8, 0, name, -1, nullptr, 0);
    if (needed <= 1)
        return L"";
    wide.resize(static_cast<std::size_t>(needed - 1));
    MultiByteToWideChar(CP_UTF8, 0, name, -1, &wide[0], needed);
    return L"\\\\.\\pipe\\shiraz-" + wide;
}

bool read_exact(HANDLE handle, void* buffer, std::size_t length) {
    auto* ptr = static_cast<std::uint8_t*>(buffer);
    std::size_t remaining = length;
    while (remaining > 0) {
        DWORD got = 0;
        if (!ReadFile(handle, ptr, static_cast<DWORD>(remaining), &got, nullptr) || got == 0)
            return false;
        ptr += got;
        remaining -= got;
    }
    return true;
}

bool write_exact(HANDLE handle, const void* buffer, std::size_t length) {
    const auto* ptr = static_cast<const std::uint8_t*>(buffer);
    std::size_t remaining = length;
    while (remaining > 0) {
        DWORD wrote = 0;
        if (!WriteFile(handle, ptr, static_cast<DWORD>(remaining), &wrote, nullptr) || wrote == 0)
            return false;
        ptr += wrote;
        remaining -= wrote;
    }
    return true;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT void* shiraz_win32_ipc_listen(const char* name) {
    const std::wstring path = pipe_path_from_name(name);
    if (path.empty())
        return nullptr;
    HANDLE pipe = CreateNamedPipeW(path.c_str(), PIPE_ACCESS_DUPLEX | FILE_FLAG_FIRST_PIPE_INSTANCE,
                                   PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT |
                                       PIPE_REJECT_REMOTE_CLIENTS,
                                   1, kPipeBufferSize, kPipeBufferSize, 0, nullptr);
    return pipe == INVALID_HANDLE_VALUE ? nullptr : static_cast<void*>(pipe);
}

SHIRAZ_EXPORT int shiraz_win32_ipc_accept(void* serverPipe) {
    if (!serverPipe)
        return -EINVAL;
    HANDLE pipe = static_cast<HANDLE>(serverPipe);
    if (ConnectNamedPipe(pipe, nullptr))
        return 0;
    return GetLastError() == ERROR_PIPE_CONNECTED ? 0 : -EIO;
}

SHIRAZ_EXPORT void* shiraz_win32_ipc_connect(const char* name) {
    const std::wstring path = pipe_path_from_name(name);
    if (path.empty())
        return nullptr;
    HANDLE pipe = CreateFileW(path.c_str(), GENERIC_READ | GENERIC_WRITE, 0, nullptr, OPEN_EXISTING,
                              SECURITY_SQOS_PRESENT | SECURITY_IDENTIFICATION, nullptr);
    return pipe == INVALID_HANDLE_VALUE ? nullptr : static_cast<void*>(pipe);
}

SHIRAZ_EXPORT int shiraz_win32_ipc_peer_pid(void* serverPipe, std::uint32_t* outPid) {
    if (!serverPipe || !outPid)
        return -EINVAL;
    ULONG pid = 0;
    if (!GetNamedPipeClientProcessId(static_cast<HANDLE>(serverPipe), &pid))
        return -EPERM;
    *outPid = static_cast<std::uint32_t>(pid);
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_ipc_send(void* handle, const void* payload, std::size_t length) {
    if (!handle || (!payload && length > 0) || length > kMaxMessageBytes)
        return -EINVAL;
    const std::uint32_t frameLen = static_cast<std::uint32_t>(length);
    if (!write_exact(static_cast<HANDLE>(handle), &frameLen, sizeof(frameLen)))
        return -EPIPE;
    if (length > 0 && !write_exact(static_cast<HANDLE>(handle), payload, length))
        return -EPIPE;
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_ipc_recv(void* handle, void* buffer, std::size_t capacity,
                                        std::size_t* outLength) {
    if (!handle || !outLength)
        return -EINVAL;
    std::uint32_t frameLen = 0;
    if (!read_exact(static_cast<HANDLE>(handle), &frameLen, sizeof(frameLen)))
        return -EPIPE;
    if (frameLen > kMaxMessageBytes || frameLen > capacity)
        return -EMSGSIZE;
    if (frameLen > 0 && (!buffer || !read_exact(static_cast<HANDLE>(handle), buffer, frameLen)))
        return -EPIPE;
    *outLength = frameLen;
    return 0;
}

SHIRAZ_EXPORT void shiraz_win32_ipc_close(void* handle) {
    if (handle)
        CloseHandle(static_cast<HANDLE>(handle));
}

} // extern "C"
#endif
