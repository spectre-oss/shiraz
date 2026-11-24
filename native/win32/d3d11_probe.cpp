// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/win32/d3d11_probe.hpp"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <dxgi.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace shiraz {

namespace {

void copy_utf8(char* dst, std::size_t cap, const char* src) {
    if (!dst || cap == 0)
        return;
    if (!src) {
        dst[0] = '\0';
        return;
    }
    std::snprintf(dst, cap, "%s", src);
}

void wide_to_utf8(char* dst, std::size_t cap, const wchar_t* src) {
    if (!dst || cap == 0)
        return;
    dst[0] = '\0';
    if (!src || !src[0])
        return;
    const int needed =
        WideCharToMultiByte(CP_UTF8, 0, src, -1, dst, static_cast<int>(cap), nullptr, nullptr);
    if (needed <= 0)
        dst[0] = '\0';
}

} // namespace

bool win32_enable_per_monitor_v2_dpi() {
    static const bool enabled = []() -> bool {
        if (HMODULE user32 = GetModuleHandleW(L"user32.dll")) {
            using SetDpiCtxFn = BOOL(WINAPI*)(void*);
            FARPROC raw = GetProcAddress(user32, "SetProcessDpiAwarenessContext");
            if (raw) {
                SetDpiCtxFn fn = nullptr;
                std::memcpy(&fn, &raw, sizeof(fn));
                // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = (DPI_AWARENESS_CONTEXT)-4
                if (fn(reinterpret_cast<void*>(static_cast<std::intptr_t>(-4))))
                    return true;
                if (GetLastError() == ERROR_ACCESS_DENIED)
                    return true; // Already configured by manifest or earlier call.
            }
        }
        if (HMODULE shcore = LoadLibraryW(L"shcore.dll")) {
            using SetAwarenessFn = HRESULT(WINAPI*)(int);
            FARPROC raw = GetProcAddress(shcore, "SetProcessDpiAwareness");
            bool ok = false;
            if (raw) {
                SetAwarenessFn fn = nullptr;
                std::memcpy(&fn, &raw, sizeof(fn));
                const HRESULT hr = fn(2); // PROCESS_PER_MONITOR_DPI_AWARE
                ok = SUCCEEDED(hr) || hr == E_ACCESSDENIED;
            }
            FreeLibrary(shcore);
            if (ok)
                return true;
        }
        return SetProcessDPIAware() != 0;
    }();
    return enabled;
}

int win32_window_dpi_density(void* hwnd) {
    unsigned int dpi = 96;
    if (HMODULE user32 = GetModuleHandleW(L"user32.dll")) {
        using GetDpiForWindowFn = UINT(WINAPI*)(HWND);
        using GetDpiForSystemFn = UINT(WINAPI*)();
        if (hwnd) {
            if (FARPROC raw = GetProcAddress(user32, "GetDpiForWindow")) {
                GetDpiForWindowFn fn = nullptr;
                std::memcpy(&fn, &raw, sizeof(fn));
                const UINT queried = fn(static_cast<HWND>(hwnd));
                if (queried >= 48 && queried <= 1920)
                    dpi = queried;
            }
        } else if (FARPROC raw = GetProcAddress(user32, "GetDpiForSystem")) {
            GetDpiForSystemFn fn = nullptr;
            std::memcpy(&fn, &raw, sizeof(fn));
            const UINT queried = fn();
            if (queried >= 48 && queried <= 1920)
                dpi = queried;
        }
    }
    return win32_dpi_to_android_density(dpi);
}

bool win32_angle_d3d11_available() {
    if (const char* eglEnv = std::getenv("SHIRAZ_EGL_LIBRARY"); eglEnv && *eglEnv) {
        if (HMODULE h = LoadLibraryA(eglEnv)) {
            const bool hasProc = GetProcAddress(h, "eglGetProcAddress") != nullptr;
            FreeLibrary(h);
            return hasProc;
        }
        return false;
    }
    if (HMODULE h = LoadLibraryW(L"libEGL.dll")) {
        const bool hasProc = GetProcAddress(h, "eglGetProcAddress") != nullptr;
        FreeLibrary(h);
        return hasProc;
    }
    return false;
}

bool win32_d3d11_device_info(char* deviceName, std::size_t deviceCap, char* vendorName,
                             std::size_t vendorCap) {
    copy_utf8(deviceName, deviceCap, "");
    copy_utf8(vendorName, vendorCap, "");

    IDXGIFactory1* factory = nullptr;
    if (FAILED(CreateDXGIFactory1(__uuidof(IDXGIFactory1), reinterpret_cast<void**>(&factory))) ||
        !factory) {
        return false;
    }

    DXGI_ADAPTER_DESC1 chosen{};
    bool foundHardware = false;
    bool foundAny = false;

    for (UINT index = 0;; ++index) {
        IDXGIAdapter1* adapter = nullptr;
        const HRESULT hr = factory->EnumAdapters1(index, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND)
            break;
        if (FAILED(hr) || !adapter)
            continue;
        DXGI_ADAPTER_DESC1 desc{};
        if (SUCCEEDED(adapter->GetDesc1(&desc))) {
            const bool isSoftware = (desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0;
            if (!foundAny) {
                chosen = desc;
                foundAny = true;
            }
            if (!isSoftware && !foundHardware) {
                chosen = desc;
                foundHardware = true;
            }
        }
        adapter->Release();
        if (foundHardware)
            break;
    }

    factory->Release();
    if (!foundAny)
        return false;

    wide_to_utf8(deviceName, deviceCap, chosen.Description);
    copy_utf8(vendorName, vendorCap, win32_vendor_name(chosen.VendorId));
    return deviceName && deviceName[0] != '\0';
}

float win32_presentation_refresh_hz() {
    DEVMODEW dm{};
    dm.dmSize = sizeof(dm);
    if (EnumDisplaySettingsW(nullptr, ENUM_CURRENT_SETTINGS, &dm) && dm.dmDisplayFrequency >= 24 &&
        dm.dmDisplayFrequency <= 480) {
        return static_cast<float>(dm.dmDisplayFrequency);
    }
    return 60.0f;
}

bool win32_begin_presentation_activity() {
    return SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED) != 0;
}

void win32_end_presentation_activity() {
    SetThreadExecutionState(ES_CONTINUOUS);
}

} // namespace shiraz
#endif
