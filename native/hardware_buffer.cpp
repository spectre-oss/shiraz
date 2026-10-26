// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/hardware_buffer.h"
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

namespace {
constexpr uint64_t cpuRead = 0xf, cpuWrite = 0xf0, gpu = 0x300;
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
struct Wire { uint32_t version; uint32_t pitch; AHardwareBuffer_Desc desc; };
}
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
extern "C" int AHardwareBuffer_allocate(const AHardwareBuffer_Desc* d, AHardwareBuffer** out) {
    if (!out) return -EINVAL;
    *out = nullptr;
    if (!valid(d)) return -EINVAL;
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
        uint32_t stride = 0, flags = (usage & cpuRead ? GBM_BO_TRANSFER_READ : 0) | (writing ? GBM_BO_TRANSFER_WRITE : 0);
        b->mapping = gbm_bo_map(b->bo, 0, 0, b->desc.width, b->desc.height, flags, &stride, &b->mapData);
        if (!b->mapping) { b->mapData = nullptr; return -EIO; }
        if (stride != b->desc.stride * bytes(b->desc.format)) {
            gbm_bo_unmap(b->bo, b->mapData); b->mapping = b->mapData = nullptr; return -ENOTSUP;
        }
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
    if (!b->readers) { gbm_bo_unmap(b->bo, b->mapData); b->mapping = b->mapData = nullptr; }
    return 0;
}
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
