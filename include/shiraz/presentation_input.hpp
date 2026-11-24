// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstring>

namespace shiraz {

// Returns true if an SDL_TouchDeviceType (cast to int to avoid requiring SDL
// headers here) should be forwarded to Android touchscreen input.
// Indirect touchpad devices (SDL_TOUCH_DEVICE_INDIRECT_ABSOLUTE = 1 and
// SDL_TOUCH_DEVICE_INDIRECT_RELATIVE = 2) are rejected across Linux, macOS,
// and Windows so laptop trackpads never inject phantom Android touch events.
// Direct touchscreens (0) and synthetic test events (-1) pass.
inline bool allow_touch_device_type(int touchDeviceType) {
    return touchDeviceType != 1 && touchDeviceType != 2;
}

// Translates a two-finger trackpad pinch gesture (SDL_MULTIGESTURE dDist) into
// a vertical scroll delta suitable for Android MotionEvent.ACTION_SCROLL zoom
// handlers (e.g. map zoom in OsmAnd+). Returns 0.0f for non-two-finger, tiny,
// or non-finite deltas.
inline float pinch_scroll_delta(int numFingers, float dDist) {
    if (numFingers != 2 || !(dDist > -10.0f && dDist < 10.0f))
        return 0.0f;
    if (dDist > -1e-4f && dDist < 1e-4f)
        return 0.0f;
    return dDist * 18.0f;
}

// Live-resize SDL event watches repaint synchronously while interactive window
// drag loops run on live compositors (Wayland, X11, Cocoa, Windows). Headless
// dummy/offscreen video drivers stay driven solely by the main presentation loop
// so frame-damage accounting remains deterministic.
inline bool should_enable_live_resize_watch(const char* videoDriver) {
    if (!videoDriver || !*videoDriver)
        return false;
    return std::strcmp(videoDriver, "dummy") != 0 && std::strcmp(videoDriver, "offscreen") != 0;
}

} // namespace shiraz
