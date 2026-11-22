// SPDX-License-Identifier: GPL-3.0-only
// Native macOS behavioral tests for display refresh pacing (ProMotion/CoreGraphics),
// zero-copy IOSurface -> Metal MTLTexture import, Security.framework trust anchors,
// and AVFoundation speech voice catalog.
#include "shiraz/darwin/display_pacing.hpp"
#include "shiraz/native_window.h"

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <vector>

int main() {
    // 1. Display refresh rate & period (ProMotion / CoreGraphics / 60 Hz headless fallback).
    const double hz = shiraz::darwin_display_refresh_hz();
    const std::int64_t periodNs = shiraz::darwin_display_period_ns();
    assert(hz >= 24.0 && hz <= 480.0);
    assert(periodNs >= 2000000LL && periodNs <= 50000000LL);

    // 2. Zero-copy IOSurface -> Metal MTLTexture import from a real ANativeWindow frame.
    ANativeWindow* win = shiraz_window_create(64, 48);
    assert(win != nullptr);
    std::vector<std::uint32_t> frame(64U * 48U, 0xFF123456U);
    assert(shiraz_window_post_argb(win, frame.data(), frame.size()) == 0);

    std::uint32_t texW = 0, texH = 0, firstArgb = 0;
    const int metalRc = shiraz::darwin_iosurface_metal_verify(win, &texW, &texH, &firstArgb);
    assert(metalRc == 0);
    assert(texW == 64 && texH == 48);
    assert(firstArgb == 0xFF123456U);
    ANativeWindow_release(win);

    // 3. Security.framework system TLS trust anchors.
    const std::size_t anchorCount = shiraz::darwin_trust_anchor_count();
    assert(anchorCount >= 20);

    // 4. AVFoundation speech synthesis voice catalog.
    const std::size_t voiceCount = shiraz::darwin_speech_voice_count();
    assert(voiceCount >= 1);

    std::printf(
        "MACOS_PLATFORM_EXTRAS hz=%.1f period_ns=%lld metal_tex=%ux%u anchors=%zu voices=%zu\n", hz,
        static_cast<long long>(periodNs), texW, texH, anchorCount, voiceCount);
    std::printf("MACOS_PLATFORM_EXTRAS_PASS\n");
    return 0;
}
