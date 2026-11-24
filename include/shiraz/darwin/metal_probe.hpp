// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "shiraz/presentation_input.hpp"
#include <cstddef>

namespace shiraz {

// Queries the system default Metal GPU via MTLCreateSystemDefaultDevice().
// Writes null-terminated UTF-8 strings into deviceName and vendorName and
// returns true if a Metal device is present.
bool darwin_metal_device_info(char* deviceName, std::size_t deviceCap, char* vendorName,
                              std::size_t vendorCap);

// Prevents macOS App Nap timer coalescing while interactive presentation is
// running. Returns an opaque retained NSObject token (or nullptr) that must be
// released with darwin_end_presentation_activity().
void* darwin_begin_presentation_activity(const char* reason);
void darwin_end_presentation_activity(void* token);

// Queries the active macOS display refresh rate via CoreGraphics and
// NSScreen.maximumFramesPerSecond (reporting 120 Hz on ProMotion panels where
// CoreGraphics reports 0). Returns a bounded rate in [24.0f, 480.0f].
float darwin_presentation_refresh_hz();

// Delegates to shared cross-platform touch/pinch helpers in presentation_input.hpp.
inline bool darwin_allow_touch_device_type(int touchDeviceType) {
    return allow_touch_device_type(touchDeviceType);
}

inline float darwin_pinch_scroll_delta(int numFingers, float dDist) {
    return pinch_scroll_delta(numFingers, dDist);
}

class DarwinAppNapGuard {
  public:
    explicit DarwinAppNapGuard(const char* reason = "Shiraz interactive presentation")
        : token_(darwin_begin_presentation_activity(reason)) {}
    ~DarwinAppNapGuard() {
        darwin_end_presentation_activity(token_);
    }
    DarwinAppNapGuard(const DarwinAppNapGuard&) = delete;
    DarwinAppNapGuard& operator=(const DarwinAppNapGuard&) = delete;

    bool active() const {
        return token_ != nullptr;
    }

  private:
    void* token_ = nullptr;
};

} // namespace shiraz
