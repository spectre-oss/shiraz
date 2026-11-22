// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/win32/d3d11_probe.hpp"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <dxgi.h>

#include <cstdio>
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

bool win32_begin_presentation_activity() {
    return SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED) != 0;
}

void win32_end_presentation_activity() {
    SetThreadExecutionState(ES_CONTINUOUS);
}

} // namespace shiraz
#endif
