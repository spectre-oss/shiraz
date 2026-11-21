// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/metal_probe.hpp"

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>

#include <cstring>

namespace shiraz {
namespace {

void copy_string(char* dst, std::size_t cap, const char* src) {
    if (!dst || cap == 0)
        return;
    if (!src) {
        dst[0] = '\0';
        return;
    }
    const std::size_t len = std::strlen(src);
    const std::size_t n = len < cap - 1 ? len : cap - 1;
    std::memcpy(dst, src, n);
    dst[n] = '\0';
}

const char* infer_vendor(NSString* name) {
    if (!name)
        return "Metal";
    if ([name rangeOfString:@"Apple" options:NSCaseInsensitiveSearch].location != NSNotFound)
        return "Apple";
    if ([name rangeOfString:@"AMD" options:NSCaseInsensitiveSearch].location != NSNotFound ||
        [name rangeOfString:@"Radeon" options:NSCaseInsensitiveSearch].location != NSNotFound)
        return "AMD";
    if ([name rangeOfString:@"Intel" options:NSCaseInsensitiveSearch].location != NSNotFound)
        return "Intel";
    return "Metal";
}

} // namespace

bool darwin_metal_device_info(char* deviceName, std::size_t deviceCap, char* vendorName,
                              std::size_t vendorCap) {
    if (deviceName && deviceCap > 0)
        deviceName[0] = '\0';
    if (vendorName && vendorCap > 0)
        vendorName[0] = '\0';
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device)
            return false;
        NSString* name = [device name];
        const char* utf8 = name ? [name UTF8String] : nullptr;
        if (!utf8 || !*utf8)
            utf8 = "Apple Metal GPU";
        copy_string(deviceName, deviceCap, utf8);
        copy_string(vendorName, vendorCap, infer_vendor(name));
        return true;
    }
}

void* darwin_begin_presentation_activity(const char* reason) {
    @autoreleasepool {
        NSString* text = (reason && *reason) ? [NSString stringWithUTF8String:reason] : nil;
        if (!text)
            text = @"Shiraz interactive presentation";
        const NSActivityOptions options =
            NSActivityUserInitiatedAllowingIdleSystemSleep | NSActivityLatencyCritical;
        id<NSObject> token = [[NSProcessInfo processInfo] beginActivityWithOptions:options
                                                                            reason:text];
        return token ? (__bridge_retained void*)token : nullptr;
    }
}

void darwin_end_presentation_activity(void* token) {
    if (!token)
        return;
    @autoreleasepool {
        id<NSObject> activity = (__bridge_transfer id<NSObject>)token;
        [[NSProcessInfo processInfo] endActivity:activity];
    }
}

} // namespace shiraz
