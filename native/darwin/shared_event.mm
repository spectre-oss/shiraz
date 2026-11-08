// SPDX-License-Identifier: GPL-3.0-only
// macOS only, compiled into libandroid (mk/darwin-zero-copy.mk). The Metal and
// IOSurface objects behind the M4b window queue (include/shiraz/darwin/window_queue.h).
// Compiled with manual reference counting (the libandroid recipe passes no
// -fobjc-arc): every object returned by shiraz_mtl_slot_create is +1 and is
// released with shiraz_mtl_release when its slot generation retires. The EGL
// shim and libshiraz-gpu stay plain C++ and pass the texture to ANGLE as an
// opaque EGL_METAL_TEXTURE_ANGLE client buffer.
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <IOSurface/IOSurfaceRef.h>
#include <cerrno>
#include <cstdint>
#include <cstdio>

extern "C" {
// Returns the texture (+1) and stores the surface (+1). Null on failure (logged).
void* shiraz_mtl_slot_create(void* device, int32_t width, int32_t height, void** surfaceOut, uint32_t* bytesPerRow) {
    if (!device || !surfaceOut || width < 1 || height < 1) return nullptr;
    *surfaceOut = nullptr;
    @autoreleasepool {
        const size_t rowBytes = IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, size_t(width) * 4);
        NSDictionary* properties = @{(id)kIOSurfaceWidth: @(width), (id)kIOSurfaceHeight: @(height),
                                     (id)kIOSurfaceBytesPerElement: @4, (id)kIOSurfaceBytesPerRow: @(rowBytes),
                                     (id)kIOSurfacePixelFormat: @((uint32_t)'RGBA')};
        IOSurfaceRef surface = IOSurfaceCreate((CFDictionaryRef)properties);
        // ANGLE does not check the texture format against the surface format
        // (t0d side check), so the queue checks the surface itself.
        if (!surface || IOSurfaceGetPixelFormat(surface) != (OSType)'RGBA' || IOSurfaceGetBytesPerElement(surface) != 4) {
            std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE iosurface-create-failed size=%dx%d\n", width, height);
            if (surface) CFRelease(surface);
            return nullptr;
        }
        MTLTextureDescriptor* descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                                                                              width:NSUInteger(width)
                                                                                             height:NSUInteger(height)
                                                                                          mipmapped:NO];
        descriptor.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
        descriptor.storageMode = MTLStorageModeShared;
        id<MTLTexture> texture = [(id<MTLDevice>)device newTextureWithDescriptor:descriptor iosurface:surface plane:0];
        if (!texture) {
            std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE mtltexture-create-failed size=%dx%d\n", width, height);
            CFRelease(surface);
            return nullptr;
        }
        if (bytesPerRow) *bytesPerRow = uint32_t(IOSurfaceGetBytesPerRow(surface));
        *surfaceOut = surface;
        return texture;
    }
}
void shiraz_mtl_release(void* object) {
    if (object) CFRelease(object);
}
void* shiraz_mtl_retain(void* object) {
    return object ? const_cast<void*>(CFRetain(object)) : nullptr;
}
int32_t shiraz_event_signaled(void* event, uint64_t value) {
    if (!value) return 1;
    if (!event) return 0;
    return [(id<MTLSharedEvent>)event signaledValue] >= value ? 1 : 0;
}
int32_t shiraz_event_cpu_wait(void* event, uint64_t value, int32_t timeoutMs) {
    if (shiraz_event_signaled(event, value)) return 0;
    if (!event) return -EINVAL;
    // A blocking wait with no registered listener block: a timed-out wait leaves
    // nothing behind (a notifyListener block would stay registered, holding its
    // semaphore, until the event reached VALUE).
    BOOL reached = [(id<MTLSharedEvent>)event waitUntilSignaledValue:value timeoutMS:uint64_t(timeoutMs > 0 ? timeoutMs : 0)];
    return reached || shiraz_event_signaled(event, value) ? 0 : -ETIMEDOUT;
}
}
