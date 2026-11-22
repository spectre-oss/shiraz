// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/display_pacing.hpp"

#include "shiraz/native_window.h"

#import <AVFoundation/AVFoundation.h>
#import <AppKit/AppKit.h>
#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>
#import <IOSurface/IOSurface.h>
#import <Metal/Metal.h>
#import <Security/Security.h>

#include <cerrno>
#include <cstring>
#include <vector>

namespace shiraz {

double darwin_display_refresh_hz() {
    @autoreleasepool {
        // 1. CoreGraphics display mode refresh rate (works headless-safe without AppKit main thread
        // window server initialization).
        CGDirectDisplayID mainDisplay = CGMainDisplayID();
        CGDisplayModeRef mode = CGDisplayCopyDisplayMode(mainDisplay);
        if (mode) {
            const double rate = CGDisplayModeGetRefreshRate(mode);
            CGDisplayModeRelease(mode);
            if (rate >= 24.0 && rate <= 480.0) {
                return rate;
            }
        }
        // 2. NSScreen.maximumFramesPerSecond (reports 120 Hz on ProMotion displays where
        // CGDisplayModeGetRefreshRate may report 0).
        if ([NSThread isMainThread]) {
            NSScreen* screen = [NSScreen mainScreen];
            if (screen && [screen respondsToSelector:@selector(maximumFramesPerSecond)]) {
                const NSInteger fps = [screen maximumFramesPerSecond];
                if (fps >= 24 && fps <= 480) {
                    return static_cast<double>(fps);
                }
            }
        }
        return 60.0;
    }
}

std::int64_t darwin_display_period_ns() {
    const double hz = darwin_display_refresh_hz();
    return static_cast<std::int64_t>(1000000000.0 / (hz >= 24.0 ? hz : 60.0));
}

int darwin_iosurface_metal_verify(ANativeWindow* window, std::uint32_t* outWidth,
                                  std::uint32_t* outHeight, std::uint32_t* outFirstPixelArgb) {
    if (!window)
        return -EINVAL;
    const int32_t w = ANativeWindow_getWidth(window);
    const int32_t h = ANativeWindow_getHeight(window);
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192)
        return -EINVAL;

    std::vector<std::uint32_t> argb(static_cast<std::size_t>(w) * static_cast<std::size_t>(h), 0);
    if (shiraz_window_copy_argb(window, argb.data(), argb.size()) != 0)
        return -EIO;

    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device)
            return -ENODEV;

        const int bytesPerElement = 4;
        const int bytesPerRow = static_cast<int>(IOSurfaceAlignProperty(
            kIOSurfaceBytesPerRow, static_cast<std::size_t>(w) * bytesPerElement));
        const int pixelFormat = static_cast<int>('BGRA');
        NSDictionary* props = @{
            (__bridge NSString*)kIOSurfaceWidth : @(w),
            (__bridge NSString*)kIOSurfaceHeight : @(h),
            (__bridge NSString*)kIOSurfaceBytesPerElement : @(bytesPerElement),
            (__bridge NSString*)kIOSurfaceBytesPerRow : @(bytesPerRow),
            (__bridge NSString*)kIOSurfacePixelFormat : @(pixelFormat),
        };
        IOSurfaceRef surface = IOSurfaceCreate((__bridge CFDictionaryRef)props);
        if (!surface)
            return -ENOMEM;

        if (IOSurfaceLock(surface, 0, nullptr) != kIOReturnSuccess) {
            CFRelease(surface);
            return -EIO;
        }
        auto* dst = static_cast<std::uint8_t*>(IOSurfaceGetBaseAddress(surface));
        const std::size_t stride = IOSurfaceGetBytesPerRow(surface);
        // Little-endian 0xAARRGGBB in uint32_t has bytes [B, G, R, A] in memory,
        // matching Metal MTLPixelFormatBGRA8Unorm and IOSurface 'BGRA' directly.
        for (int32_t y = 0; y < h; ++y) {
            std::memcpy(dst + static_cast<std::size_t>(y) * stride,
                        argb.data() + static_cast<std::size_t>(y) * static_cast<std::size_t>(w),
                        static_cast<std::size_t>(w) * 4U);
        }
        IOSurfaceUnlock(surface, 0, nullptr);

        MTLTextureDescriptor* desc =
            [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                               width:static_cast<NSUInteger>(w)
                                                              height:static_cast<NSUInteger>(h)
                                                           mipmapped:NO];
        desc.usage = MTLTextureUsageShaderRead | MTLTextureUsageRenderTarget;
        desc.storageMode = MTLStorageModeShared;

        id<MTLTexture> texture = [device newTextureWithDescriptor:desc iosurface:surface plane:0];
        if (!texture) {
            CFRelease(surface);
            return -EIO;
        }
        if (texture.iosurface != surface || texture.width != static_cast<NSUInteger>(w) ||
            texture.height != static_cast<NSUInteger>(h)) {
            CFRelease(surface);
            return -EIO;
        }

        std::uint32_t firstPixel = 0;
        [texture getBytes:&firstPixel
              bytesPerRow:4
               fromRegion:MTLRegionMake2D(0, 0, 1, 1)
              mipmapLevel:0];
        if (outWidth)
            *outWidth = static_cast<std::uint32_t>(texture.width);
        if (outHeight)
            *outHeight = static_cast<std::uint32_t>(texture.height);
        if (outFirstPixelArgb)
            *outFirstPixelArgb = firstPixel;

        CFRelease(surface);
        return 0;
    }
}

std::size_t darwin_trust_anchor_count() {
    CFArrayRef roots = nullptr;
    const OSStatus status = SecTrustCopyAnchorCertificates(&roots);
    if (status != errSecSuccess || !roots) {
        if (roots)
            CFRelease(roots);
        return 0;
    }
    const CFIndex count = CFArrayGetCount(roots);
    CFRelease(roots);
    return count > 0 ? static_cast<std::size_t>(count) : 0;
}

std::size_t darwin_speech_voice_count() {
    @autoreleasepool {
        NSArray<AVSpeechSynthesisVoice*>* voices = [AVSpeechSynthesisVoice speechVoices];
        return voices ? static_cast<std::size_t>([voices count]) : 0;
    }
}

} // namespace shiraz
