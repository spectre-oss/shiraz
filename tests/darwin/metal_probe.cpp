// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/metal_probe.hpp"

#include <SDL.h>

#include <cassert>
#include <cstring>
#include <iostream>
#include <string>

int main() {
    // 1. Verify SDL_TouchDeviceType enum values match our filter contract.
    static_assert(SDL_TOUCH_DEVICE_INVALID == -1, "SDL_TOUCH_DEVICE_INVALID mismatch");
    static_assert(SDL_TOUCH_DEVICE_DIRECT == 0, "SDL_TOUCH_DEVICE_DIRECT mismatch");
    static_assert(SDL_TOUCH_DEVICE_INDIRECT_ABSOLUTE == 1,
                  "SDL_TOUCH_DEVICE_INDIRECT_ABSOLUTE mismatch");
    static_assert(SDL_TOUCH_DEVICE_INDIRECT_RELATIVE == 2,
                  "SDL_TOUCH_DEVICE_INDIRECT_RELATIVE mismatch");

    assert(shiraz::darwin_allow_touch_device_type(SDL_TOUCH_DEVICE_DIRECT));
    assert(shiraz::darwin_allow_touch_device_type(SDL_TOUCH_DEVICE_INVALID));
    assert(!shiraz::darwin_allow_touch_device_type(SDL_TOUCH_DEVICE_INDIRECT_ABSOLUTE));
    assert(!shiraz::darwin_allow_touch_device_type(SDL_TOUCH_DEVICE_INDIRECT_RELATIVE));

    // 1b. Verify trackpad pinch-to-scroll conversion.
    assert(shiraz::darwin_pinch_scroll_delta(2, 0.05f) > 0.5f);
    assert(shiraz::darwin_pinch_scroll_delta(2, -0.05f) < -0.5f);
    assert(shiraz::darwin_pinch_scroll_delta(2, 0.0f) == 0.0f);
    assert(shiraz::darwin_pinch_scroll_delta(3, 0.1f) == 0.0f);
    assert(shiraz::darwin_pinch_scroll_delta(2, 0.0f / 0.0f) == 0.0f);

    // 2. Verify Metal GPU device probe returns the real Apple Silicon GPU.
    char device[256] = {};
    char vendor[128] = {};
    const bool found =
        shiraz::darwin_metal_device_info(device, sizeof(device), vendor, sizeof(vendor));
    assert(found);
    assert(std::strlen(device) > 0);
    assert(std::strlen(vendor) > 0);
    assert(std::string(device) != "unknown");
    assert(std::string(vendor) != "unknown");

    // 3. Verify tiny buffer truncation and null buffer handling do not overflow or crash.
    char tinyDev[4] = {};
    char tinyVendor[3] = {};
    assert(
        shiraz::darwin_metal_device_info(tinyDev, sizeof(tinyDev), tinyVendor, sizeof(tinyVendor)));
    assert(tinyDev[3] == '\0');
    assert(tinyVendor[2] == '\0');
    assert(shiraz::darwin_metal_device_info(nullptr, 0, nullptr, 0));

    // 4. Verify App Nap activity guard acquires and releases cleanly.
    {
        shiraz::DarwinAppNapGuard outer("Shiraz Metal probe test outer");
        assert(outer.active());
        shiraz::DarwinAppNapGuard inner("Shiraz Metal probe test inner");
        assert(inner.active());
    }
    shiraz::darwin_end_presentation_activity(nullptr);

    std::cout << "MACOS_METAL_PROBE device=" << device << " vendor=" << vendor << "\n";
    std::cout << "MACOS_METAL_PROBE_PASS\n";
    return 0;
}
