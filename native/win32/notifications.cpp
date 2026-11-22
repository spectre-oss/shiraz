// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <shellapi.h>

namespace {

std::mutex g_notifyMutex;
std::unordered_map<std::uint32_t, std::string> g_active;
std::uint32_t g_nextId = 1;

bool is_interactive_session() {
    HWINSTA station = GetProcessWindowStation();
    if (!station)
        return false;
    USEROBJECTFLAGS flags{};
    DWORD needed = 0;
    if (!GetUserObjectInformationW(station, UOI_FLAGS, &flags, sizeof(flags), &needed))
        return false;
    return (flags.dwFlags & WSF_VISIBLE) != 0;
}

void utf8_to_wide_fixed(wchar_t* dst, std::size_t cap, const char* src) {
    if (!dst || cap == 0)
        return;
    dst[0] = L'\0';
    if (!src || !src[0])
        return;
    const int n = MultiByteToWideChar(CP_UTF8, 0, src, -1, dst, static_cast<int>(cap));
    if (n <= 0)
        dst[0] = L'\0';
    dst[cap - 1] = L'\0';
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_notifications_available() {
    return is_interactive_session() ? 1 : 0;
}

SHIRAZ_EXPORT std::uint32_t shiraz_win32_notifications_post(std::uint32_t replaceId,
                                                            const char* title, const char* body,
                                                            int urgency, int silent) {
    std::lock_guard<std::mutex> lock(g_notifyMutex);
    const std::uint32_t id = replaceId ? replaceId : g_nextId++;
    std::string summary = title ? title : "";
    g_active[id] = summary;

    if (is_interactive_session()) {
        NOTIFYICONDATAW nid{};
        nid.cbSize = sizeof(nid);
        nid.hWnd = GetConsoleWindow();
        nid.uID = id;
        nid.uFlags = NIF_INFO;
        nid.dwInfoFlags = (urgency >= 2 ? NIIF_WARNING : NIIF_INFO) | (silent ? NIIF_NOSOUND : 0);
        utf8_to_wide_fixed(nid.szInfoTitle, sizeof(nid.szInfoTitle) / sizeof(nid.szInfoTitle[0]),
                           title ? title : "Shiraz");
        utf8_to_wide_fixed(nid.szInfo, sizeof(nid.szInfo) / sizeof(nid.szInfo[0]),
                           body ? body : "");
        if (nid.hWnd) {
            Shell_NotifyIconW(NIM_ADD, &nid);
            Shell_NotifyIconW(NIM_MODIFY, &nid);
            Shell_NotifyIconW(NIM_DELETE, &nid);
        }
    }
    return id;
}

SHIRAZ_EXPORT int shiraz_win32_notifications_cancel(std::uint32_t id) {
    std::lock_guard<std::mutex> lock(g_notifyMutex);
    return g_active.erase(id) > 0 ? 1 : 0;
}

SHIRAZ_EXPORT std::size_t shiraz_win32_notifications_active_count() {
    std::lock_guard<std::mutex> lock(g_notifyMutex);
    return g_active.size();
}

} // extern "C"
#endif
