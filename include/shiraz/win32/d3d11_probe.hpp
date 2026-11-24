// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "shiraz/presentation_input.hpp"
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace shiraz {

// Resolves a PCI vendor ID from DXGI_ADAPTER_DESC1::VendorId into a canonical
// GPU vendor string for SHIRAZ_GPU telemetry.
inline const char* win32_vendor_name(std::uint32_t pciVendorId) {
    switch (pciVendorId) {
    case 0x10DE:
        return "NVIDIA";
    case 0x1002:
    case 0x1022:
        return "AMD";
    case 0x8086:
        return "Intel";
    case 0x1414:
        return "Microsoft";
    case 0x5143:
    case 0x4D4F4351:
        return "Qualcomm";
    case 0x15AD:
        return "VMware";
    case 0x1AF4:
        return "VirtIO";
    default:
        return "Unknown";
    }
}

// Converts a Win32 Per-Monitor V2 DPI value (base 96 DPI = 1.0x) into an
// Android DisplayMetrics densityDpi value (base 160 dpi = 1.0x).
inline int win32_dpi_to_android_density(unsigned int win32Dpi) {
    if (win32Dpi < 48 || win32Dpi > 1920)
        return 160;
    const double scaled = (static_cast<double>(win32Dpi) * 160.0) / 96.0;
    return static_cast<int>(std::lround(scaled));
}

// Delegates to shared cross-platform touch/pinch helpers in presentation_input.hpp.
inline bool win32_allow_touch_device_type(int touchDeviceType) {
    return allow_touch_device_type(touchDeviceType);
}

inline float win32_pinch_scroll_delta(int numFingers, float dDist) {
    return pinch_scroll_delta(numFingers, dDist);
}

#if defined(_WIN32)
// Enables Per-Monitor V2 DPI awareness via user32!SetProcessDpiAwarenessContext
// (falling back to shcore!SetProcessDpiAwareness or SetProcessDPIAware).
bool win32_enable_per_monitor_v2_dpi();

// Queries the active DPI for a Win32 HWND via GetDpiForWindow (default 96) and
// converts it into an Android DisplayMetrics densityDpi value.
int win32_window_dpi_density(void* hwnd);

// Checks whether an ANGLE-D3D11 EGL/GLESv2 runtime is configured via
// SHIRAZ_EGL_LIBRARY or present beside the executable/PATH.
bool win32_angle_d3d11_available();

// Queries the primary hardware (or WARP fallback) DXGI/D3D11 adapter via
// IDXGIFactory1::EnumAdapters1(). Writes UTF-8 deviceName and vendorName.
bool win32_d3d11_device_info(char* deviceName, std::size_t deviceCap, char* vendorName,
                             std::size_t vendorCap);

// Queries the active primary display refresh rate via EnumDisplaySettingsW
// (falls back to 60.0 Hz on headless/remote sessions).
float win32_presentation_refresh_hz();

// Prevents Windows display/system sleep during active interactive presentation
// via SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED).
bool win32_begin_presentation_activity();
void win32_end_presentation_activity();

class Win32PowerGuard {
  public:
    Win32PowerGuard() : active_(win32_begin_presentation_activity()) {}
    ~Win32PowerGuard() {
        if (active_)
            win32_end_presentation_activity();
    }
    Win32PowerGuard(const Win32PowerGuard&) = delete;
    Win32PowerGuard& operator=(const Win32PowerGuard&) = delete;

    bool active() const {
        return active_;
    }

  private:
    bool active_ = false;
};
#endif

} // namespace shiraz
