// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/hardware_buffer.h"
#if defined(__APPLE__)
#include <CoreFoundation/CoreFoundation.h>
#include <IOSurface/IOSurface.h>
#include <atomic>
#include <cerrno>
#include <cstdio>
#include <mutex>
#include <new>
#include <poll.h>
#else
#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <gbm.h>
#include <mutex>
#include <new>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

namespace {
constexpr uint64_t cpuRead = 0xf, cpuWrite = 0xf0, gpu = 0x300;
#if defined(__APPLE__)
// IOSurface pixel formats: 'RGBA' (R,G,B,A bytes, matching Android RGBA_8888 and
// RGBX_8888 memory order) and 'L565' (kCVPixelFormatType_16LE565). BLOB and every
// other format stay rejected, as on Linux.
uint32_t format(uint32_t android) {
    switch (android) { case 1: case 2: return 0x52474241; case 4: return 0x4C353635; default: return 0; }
}
void unsupported() { std::fputs("SHIRAZ_UNSUPPORTED hardware-buffer-cross-process\n", stderr); errno = ENOTSUP; }
#else
constexpr uint32_t magic = 0x53484231;
struct Device {
    int fd = -1;
    gbm_device* value = nullptr;
    Device() {
        for (unsigned n = 128; n < 192; ++n) {
            char path[64]; std::snprintf(path, sizeof(path), "/dev/dri/renderD%u", n);
            int candidate = open(path, O_RDWR | O_CLOEXEC);
            if (candidate < 0) continue;
            value = gbm_create_device(candidate);
            if (value) { fd = candidate; break; }
            close(candidate);
        }
    }
    ~Device() { if (value) gbm_device_destroy(value); if (fd >= 0) close(fd); }
};
gbm_device* device() { static Device d; return d.value; }
uint32_t format(uint32_t android) {
    switch (android) { case 1: return GBM_FORMAT_ABGR8888; case 2: return GBM_FORMAT_XBGR8888;
        case 4: return GBM_FORMAT_RGB565; default: return 0; }
}
#endif
unsigned bytes(uint32_t android) { return android == 4 ? 2 : 4; }
bool cpuUsage(uint64_t u) {
    unsigned r = u & cpuRead, w = (u & cpuWrite) >> 4;
    return (r == 0 || r == 2 || r == 3) && (w == 0 || w == 2 || w == 3);
}
bool valid(const AHardwareBuffer_Desc* d) {
    return d && d->width && d->height && d->width <= 8192 && d->height <= 8192 &&
        uint64_t(d->width) * d->height <= 16 * 1024 * 1024 && d->layers == 1 &&
        format(d->format) && !d->rfu0 && !d->rfu1 && !(d->usage & ~(cpuRead | cpuWrite | gpu)) && cpuUsage(d->usage);
}
#if defined(__APPLE__)
#else
struct Wire { uint32_t version; uint32_t pitch; AHardwareBuffer_Desc desc; };
#endif
}
#if defined(__APPLE__)
struct AHardwareBuffer {
    std::atomic<unsigned> refs{1};
    AHardwareBuffer_Desc desc{};
    IOSurfaceRef surface = nullptr;
    std::mutex mutex;
    void* mapping = nullptr;
    IOSurfaceLockOptions lockOptions = 0; // IOSurfaceUnlock must repeat the IOSurfaceLock options.
    unsigned readers = 0;
    bool writer = false;
    ~AHardwareBuffer() { if (mapping) IOSurfaceUnlock(surface, lockOptions, nullptr); if (surface) CFRelease(surface); }
};
#else
struct AHardwareBuffer {
    std::atomic<unsigned> refs{1};
    AHardwareBuffer_Desc desc{};
    gbm_bo* bo = nullptr;
    std::mutex mutex;
    void* mapping = nullptr;
    void* mapData = nullptr;
    unsigned readers = 0;
    bool writer = false;
    ~AHardwareBuffer() { if (mapData) gbm_bo_unmap(bo, mapData); if (bo) gbm_bo_destroy(bo); }
};
#endif
extern "C" int AHardwareBuffer_allocate(const AHardwareBuffer_Desc* d, AHardwareBuffer** out) {
    if (!out) return -EINVAL;
    *out = nullptr;
    if (!valid(d)) return -EINVAL;
#if defined(__APPLE__)
    // A linear IOSurface is shared directly by Metal and CPU mappings; its pitch is
    // fixed at creation, so the Linux map-time stride check happens here once.
    int width = int(d->width), height = int(d->height), bpe = int(bytes(d->format)), pixel = int(format(d->format));
    int pitch = int(IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, size_t(width) * bpe));
    const void* keys[] = {kIOSurfaceWidth, kIOSurfaceHeight, kIOSurfaceBytesPerElement, kIOSurfaceBytesPerRow, kIOSurfacePixelFormat};
    CFNumberRef values[] = {CFNumberCreate(nullptr, kCFNumberIntType, &width), CFNumberCreate(nullptr, kCFNumberIntType, &height),
        CFNumberCreate(nullptr, kCFNumberIntType, &bpe), CFNumberCreate(nullptr, kCFNumberIntType, &pitch),
        CFNumberCreate(nullptr, kCFNumberSInt32Type, &pixel)};
    bool numbers = true; for (auto* v : values) numbers = numbers && v;
    CFDictionaryRef properties = numbers ? CFDictionaryCreate(nullptr, keys, reinterpret_cast<const void**>(values), 5,
        &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks) : nullptr;
    for (auto* v : values) if (v) CFRelease(v);
    IOSurfaceRef surface = properties ? IOSurfaceCreate(properties) : nullptr;
    if (properties) CFRelease(properties);
    if (!surface) return -ENOMEM;
    size_t rowBytes = IOSurfaceGetBytesPerRow(surface);
    if (IOSurfaceGetWidth(surface) != size_t(width) || IOSurfaceGetHeight(surface) != size_t(height) ||
        rowBytes < size_t(width) * bpe || rowBytes % bpe) { CFRelease(surface); return -ENOTSUP; }
    auto* b = new(std::nothrow) AHardwareBuffer;
    if (!b) { CFRelease(surface); return -ENOMEM; }
    b->surface = surface; b->desc = *d; b->desc.stride = uint32_t(rowBytes / bpe);
    *out = b; return 0;
#else
    gbm_device* dev = device();
    if (!dev) return -ENODEV;
    // A linear DMA buffer is shared directly by Mesa and CPU mappings. Never
    // report successful GPU allocation for an ordinary CPU-only bitmap.
    gbm_bo* bo = gbm_bo_create(dev, d->width, d->height, format(d->format), GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR);
    if (!bo) return errno ? -errno : -ENOMEM;
    if (gbm_bo_get_modifier(bo) != 0) { gbm_bo_destroy(bo); return -ENOTSUP; }
    auto* b = new(std::nothrow) AHardwareBuffer;
    if (!b) { gbm_bo_destroy(bo); return -ENOMEM; }
    b->bo = bo; b->desc = *d; b->desc.stride = gbm_bo_get_stride(bo) / bytes(d->format);
    *out = b; return 0;
#endif
}
extern "C" int AHardwareBuffer_isSupported(const AHardwareBuffer_Desc* d) {
    AHardwareBuffer* b = nullptr;
    if (AHardwareBuffer_allocate(d, &b)) return 0;
    AHardwareBuffer_release(b); return 1;
}
extern "C" void AHardwareBuffer_acquire(AHardwareBuffer* b) { if (b) ++b->refs; }
extern "C" void AHardwareBuffer_release(AHardwareBuffer* b) { if (b && --b->refs == 0) delete b; }
extern "C" void AHardwareBuffer_describe(const AHardwareBuffer* b, AHardwareBuffer_Desc* out) {
    if (out) *out = b ? b->desc : AHardwareBuffer_Desc{};
}
extern "C" int AHardwareBuffer_lock(AHardwareBuffer* b, uint64_t usage, int32_t fence, const ARect* rect, void** out) {
    if (!out) return -EINVAL;
    *out = nullptr;
    if (!b || !usage || usage & ~(cpuRead | cpuWrite) || !cpuUsage(usage)) return -EINVAL;
    if (((usage & cpuRead) && !(b->desc.usage & cpuRead)) ||
        ((usage & cpuWrite) && !(b->desc.usage & cpuWrite))) return -EINVAL;
    if (rect && (rect->left < 0 || rect->top < 0 || rect->right < rect->left || rect->bottom < rect->top ||
        uint32_t(rect->right) > b->desc.width || uint32_t(rect->bottom) > b->desc.height)) return -EINVAL;
    if (fence >= 0) {
        pollfd p{fence, POLLIN, 0}; int result;
        do { result = poll(&p, 1, -1); } while (result < 0 && errno == EINTR);
        if (result < 0 || p.revents & (POLLERR | POLLNVAL)) return -EINVAL;
    }
    std::lock_guard<std::mutex> guard(b->mutex);
    bool writing = usage & cpuWrite;
    if (b->writer || (writing && b->readers)) return -EBUSY;
    if (!b->mapping) {
#if defined(__APPLE__)
        IOSurfaceLockOptions options = writing ? 0 : kIOSurfaceLockReadOnly;
        if (IOSurfaceLock(b->surface, options, nullptr) != kIOReturnSuccess) return -EIO;
        b->mapping = IOSurfaceGetBaseAddress(b->surface); b->lockOptions = options;
        if (!b->mapping) { IOSurfaceUnlock(b->surface, options, nullptr); return -EIO; }
        if (IOSurfaceGetBytesPerRow(b->surface) != size_t(b->desc.stride) * bytes(b->desc.format)) {
            IOSurfaceUnlock(b->surface, options, nullptr); b->mapping = nullptr; return -ENOTSUP;
        }
#else
        uint32_t stride = 0, flags = (usage & cpuRead ? GBM_BO_TRANSFER_READ : 0) | (writing ? GBM_BO_TRANSFER_WRITE : 0);
        b->mapping = gbm_bo_map(b->bo, 0, 0, b->desc.width, b->desc.height, flags, &stride, &b->mapData);
        if (!b->mapping) { b->mapData = nullptr; return -EIO; }
        if (stride != b->desc.stride * bytes(b->desc.format)) {
            gbm_bo_unmap(b->bo, b->mapData); b->mapping = b->mapData = nullptr; return -ENOTSUP;
        }
#endif
    }
    if (writing) b->writer = true; else ++b->readers;
    *out = b->mapping; return 0;
}
extern "C" int AHardwareBuffer_unlock(AHardwareBuffer* b, int32_t* fence) {
    if (fence) *fence = -1;
    if (!b) return -EINVAL;
    std::lock_guard<std::mutex> guard(b->mutex);
    if (!b->writer && !b->readers) return -EINVAL;
    if (b->writer) b->writer = false; else --b->readers;
#if defined(__APPLE__)
    if (!b->readers) { IOSurfaceUnlock(b->surface, b->lockOptions, nullptr); b->mapping = nullptr; b->lockOptions = 0; }
#else
    if (!b->readers) { gbm_bo_unmap(b->bo, b->mapData); b->mapping = b->mapData = nullptr; }
#endif
    return 0;
}
#if defined(__APPLE__)
// IOSurfaces have no file descriptor. The dma-buf export used by the EGL bridge and
// the SCM_RIGHTS handle transfer fail explicitly until the Mach-port path lands.
extern "C" int shiraz_hardware_buffer_export(AHardwareBuffer* b, uint32_t* f, uint32_t* pitch) {
    if (!b || !f || !pitch) return -EINVAL;
    unsupported(); return -ENOTSUP;
}
extern "C" int AHardwareBuffer_sendHandleToUnixSocket(const AHardwareBuffer* b, int) {
    if (!b) return -EINVAL;
    unsupported(); return -ENOTSUP;
}
extern "C" int AHardwareBuffer_recvHandleFromUnixSocket(int, AHardwareBuffer** out) {
    if (!out) return -EINVAL;
    *out = nullptr;
    unsupported(); return -ENOTSUP;
}
#else
extern "C" int shiraz_hardware_buffer_export(AHardwareBuffer* b, uint32_t* f, uint32_t* pitch) {
    if (!b || !f || !pitch) return -EINVAL;
    *f = gbm_bo_get_format(b->bo); *pitch = gbm_bo_get_stride(b->bo);
    return gbm_bo_get_fd(b->bo);
}
extern "C" int AHardwareBuffer_sendHandleToUnixSocket(const AHardwareBuffer* b, int socket) {
    if (!b) return -EINVAL;
    int fd = gbm_bo_get_fd(b->bo); if (fd < 0) return -errno;
    Wire data{magic, gbm_bo_get_stride(b->bo), b->desc};
    iovec iov{&data, sizeof(data)};
    alignas(cmsghdr) char control[CMSG_SPACE(sizeof(int))]{};
    msghdr message{}; message.msg_iov = &iov; message.msg_iovlen = 1;
    message.msg_control = control; message.msg_controllen = sizeof(control);
    cmsghdr* c = CMSG_FIRSTHDR(&message); c->cmsg_level = SOL_SOCKET; c->cmsg_type = SCM_RIGHTS;
    c->cmsg_len = CMSG_LEN(sizeof(int)); std::memcpy(CMSG_DATA(c), &fd, sizeof(fd));
    ssize_t sent; do { sent = sendmsg(socket, &message, MSG_NOSIGNAL); } while (sent < 0 && errno == EINTR);
    int error = errno; close(fd);
    return sent == sizeof(data) ? 0 : sent < 0 ? -error : -EIO;
}
extern "C" int AHardwareBuffer_recvHandleFromUnixSocket(int socket, AHardwareBuffer** out) {
    if (!out) return -EINVAL;
    *out = nullptr;
    Wire data{}; iovec iov{&data, sizeof(data)};
    alignas(cmsghdr) char control[CMSG_SPACE(sizeof(int) * 8)]{};
    msghdr message{}; message.msg_iov = &iov; message.msg_iovlen = 1;
    message.msg_control = control; message.msg_controllen = sizeof(control);
    ssize_t got; do { got = recvmsg(socket, &message, MSG_WAITALL | MSG_CMSG_CLOEXEC); } while (got < 0 && errno == EINTR);
    if (got < 0) return -errno;
    int fd = -1; unsigned count = 0;
    for (auto* c = CMSG_FIRSTHDR(&message); c; c = CMSG_NXTHDR(&message, c)) {
        if (c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_RIGHTS || c->cmsg_len < CMSG_LEN(0)) continue;
        size_t n = (c->cmsg_len - CMSG_LEN(0)) / sizeof(int);
        for (size_t i = 0; i < n; ++i) { int received; std::memcpy(&received, CMSG_DATA(c) + i * sizeof(int), sizeof(int));
            if (++count == 1) fd = received; else close(received); }
    }
    bool ok = got == sizeof(data) && !(message.msg_flags & (MSG_TRUNC | MSG_CTRUNC)) && count == 1 &&
        data.version == magic && valid(&data.desc) && data.pitch >= data.desc.width * bytes(data.desc.format) &&
        data.pitch <= 65536 && data.pitch % bytes(data.desc.format) == 0 && data.desc.stride == data.pitch / bytes(data.desc.format);
    if (!ok) { if (fd >= 0) close(fd); return -EINVAL; }
    off_t capacity = lseek(fd, 0, SEEK_END);
    uint64_t needed = uint64_t(data.pitch) * (data.desc.height - 1) + uint64_t(data.desc.width) * bytes(data.desc.format);
    if (capacity < 0 || uint64_t(capacity) < needed) { close(fd); return -EINVAL; }
    gbm_device* dev = device();
    gbm_import_fd_data import{fd, data.desc.width, data.desc.height, data.pitch, format(data.desc.format)};
    gbm_bo* bo = dev ? gbm_bo_import(dev, GBM_BO_IMPORT_FD, &import, GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR) : nullptr;
    close(fd);
    if (!bo) return -EINVAL;
    if (gbm_bo_get_modifier(bo) != 0) { gbm_bo_destroy(bo); return -ENOTSUP; }
    auto* b = new(std::nothrow) AHardwareBuffer;
    if (!b) { gbm_bo_destroy(bo); return -ENOMEM; }
    b->bo = bo; b->desc = data.desc; *out = b; return 0;
}
#endif
