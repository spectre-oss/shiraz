// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>

struct ANativeWindow;

namespace shiraz {

// Queries the active macOS display refresh rate (supporting ProMotion up to
// 120+ Hz via NSScreen.maximumFramesPerSecond with CoreGraphics fallback, and
// 60.0 Hz default in headless sessions).
double darwin_display_refresh_hz();
std::int64_t darwin_display_period_ns();

// Zero-copy IOSurface -> Metal MTLTexture bridge for ANativeWindow frames.
// Creates an IOSurface populated from the posted ANativeWindow frame, imports
// it directly into an Apple Silicon Metal MTLTexture via
// newTextureWithDescriptor:iosurface:plane:0, and verifies texture dimensions
// and top-left pixel ARGB readback. Returns 0 on success or negative errno.
int darwin_iosurface_metal_verify(ANativeWindow* window, std::uint32_t* outWidth,
                                  std::uint32_t* outHeight, std::uint32_t* outFirstPixelArgb);

// Queries macOS Security.framework system root certificate anchor count via
// SecTrustCopyAnchorCertificates (public anchors only).
std::size_t darwin_trust_anchor_count();

// Queries macOS AVFoundation AVSpeechSynthesisVoice voice catalog count.
std::size_t darwin_speech_voice_count();

} // namespace shiraz
