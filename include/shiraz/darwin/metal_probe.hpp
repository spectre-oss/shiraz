// SPDX-License-Identifier: GPL-3.0-only
#pragma once

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

// Returns true if an SDL_TouchDeviceType (cast to int to avoid requiring SDL
// headers here) should be forwarded to Android touchscreen input on macOS.
// Indirect trackpad devices (SDL_TOUCH_DEVICE_INDIRECT_ABSOLUTE = 1 and
// SDL_TOUCH_DEVICE_INDIRECT_RELATIVE = 2) are rejected so MacBook / Magic
// Trackpad touches never inject phantom Android touchscreen events. Direct
// touchscreens (0) and synthetic test events (-1) pass.
inline bool darwin_allow_touch_device_type(int touchDeviceType) {
    return touchDeviceType != 1 && touchDeviceType != 2;
}

// Translates a macOS trackpad two-finger pinch gesture (SDL_MULTIGESTURE dDist)
// into a vertical scroll delta suitable for Android MotionEvent.ACTION_SCROLL
// zoom handlers. Returns 0.0f for non-two-finger, tiny, or non-finite deltas.
inline float darwin_pinch_scroll_delta(int numFingers, float dDist) {
    if (numFingers != 2 || !(dDist > -10.0f && dDist < 10.0f))
        return 0.0f;
    if (dDist > -1e-4f && dDist < 1e-4f)
        return 0.0f;
    return dDist * 18.0f;
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
