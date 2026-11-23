// SPDX-License-Identifier: GPL-3.0-only
// Native Windows ANativeWindow implementation backing VK_KHR_android_surface,
// swapchain presentation, and SDL2 presentation on Windows.
#include "shiraz/native_window.h"
#include "shiraz/platform.hpp"
#include "shiraz/window_frames.hpp"

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <new>
#include <unordered_set>
#include <vector>

#if defined(_WIN32)

struct ANativeWindow {
    std::atomic<unsigned> references{1};
    std::mutex mutex;
    int32_t width, height, initial_width, initial_height;
    bool locked = false;
    ShirazFrameSink frameSink;
    uint64_t revision = 1;
    bool front_argb = false;
    std::vector<uint8_t> front, back;

    ANativeWindow(int32_t w, int32_t h)
        : width(w), height(h), initial_width(w), initial_height(h),
          front(static_cast<size_t>(w) * static_cast<size_t>(h) * 4), back(front.size()) {}
};

namespace {

std::mutex knownMutex;
std::unordered_set<ANativeWindow*> knownWindows;

bool valid_size(int32_t w, int32_t h) {
    return w > 0 && h > 0 && w <= 8192 && h <= 8192 &&
           static_cast<uint64_t>(w) * static_cast<uint64_t>(h) <= 16ULL * 1024ULL * 1024ULL;
}

int32_t set_geometry(ANativeWindow* w, int32_t width, int32_t height, int32_t format,
                     bool hostResize) {
    if (!w || (format != 0 && format != 1))
        return -EINVAL;
    if (hostResize && !valid_size(width, height))
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (w->locked)
        return -EBUSY;
    if (width == 0 && height == 0) {
        width = w->initial_width;
        height = w->initial_height;
    }
    if (!valid_size(width, height))
        return -EINVAL;
    if (width == w->width && height == w->height) {
        if (hostResize) {
            w->initial_width = width;
            w->initial_height = height;
        }
        return 0;
    }
    try {
        std::vector<uint8_t> front(static_cast<size_t>(width) * static_cast<size_t>(height) * 4);
        std::vector<uint8_t> back(front.size());
        w->front.swap(front);
        w->back.swap(back);
        w->width = width;
        w->height = height;
        w->front_argb = false;
        ++w->revision;
    } catch (const std::bad_alloc&) {
        return -ENOMEM;
    }
    if (hostResize) {
        w->initial_width = width;
        w->initial_height = height;
    }
    return 0;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT ANativeWindow* shiraz_window_create(int32_t w, int32_t h) {
    if (!valid_size(w, h))
        return nullptr;
    try {
        auto window = std::make_unique<ANativeWindow>(w, h);
        std::lock_guard<std::mutex> guard(knownMutex);
        knownWindows.insert(window.get());
        return window.release();
    } catch (const std::bad_alloc&) {
        return nullptr;
    }
}

SHIRAZ_EXPORT void ANativeWindow_acquire(ANativeWindow* w) {
    if (w)
        ++w->references;
}

SHIRAZ_EXPORT void ANativeWindow_release(ANativeWindow* w) {
    if (!w || --w->references != 0)
        return;
    {
        std::lock_guard<std::mutex> guard(knownMutex);
        knownWindows.erase(w);
    }
    delete w;
}

SHIRAZ_EXPORT int32_t shiraz_window_known(ANativeWindow* w) {
    std::lock_guard<std::mutex> guard(knownMutex);
    return knownWindows.count(w) ? 1 : 0;
}

SHIRAZ_EXPORT int32_t ANativeWindow_getWidth(ANativeWindow* w) {
    if (!w)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    return w->width;
}

SHIRAZ_EXPORT int32_t ANativeWindow_getHeight(ANativeWindow* w) {
    if (!w)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    return w->height;
}

SHIRAZ_EXPORT int32_t ANativeWindow_getFormat(ANativeWindow* w) {
    return w ? 1 : -EINVAL;
}

SHIRAZ_EXPORT int32_t ANativeWindow_setBuffersGeometry(ANativeWindow* w, int32_t width,
                                                       int32_t height, int32_t format) {
    return set_geometry(w, width, height, format, false);
}

SHIRAZ_EXPORT int32_t shiraz_window_resize(ANativeWindow* w, int32_t width, int32_t height) {
    return set_geometry(w, width, height, 1, true);
}

SHIRAZ_EXPORT int shiraz_window_export(ANativeWindow*) {
    return -ENOTSUP;
}

SHIRAZ_EXPORT ANativeWindow* shiraz_window_import(int) {
    return nullptr;
}

SHIRAZ_EXPORT int32_t ANativeWindow_lock(ANativeWindow* w, ANativeWindow_Buffer* buffer,
                                         ARect* dirty) {
    if (!w || !buffer)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (w->locked)
        return -EBUSY;
    *buffer = {};
    buffer->width = w->width;
    buffer->height = w->height;
    buffer->stride = w->width;
    buffer->format = 1;
    buffer->bits = w->back.data();
    if (dirty)
        *dirty = {0, 0, w->width, w->height};
    w->locked = true;
    return 0;
}

SHIRAZ_EXPORT int32_t ANativeWindow_unlockAndPost(ANativeWindow* w) {
    if (!w)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (!w->locked)
        return -EINVAL;
    w->front.swap(w->back);
    w->locked = false;
    w->front_argb = false;
    ++w->revision;
    if (w->frameSink)
        w->frameSink(reinterpret_cast<const uint32_t*>(w->front.data()), w->width, w->height,
                     false);
    return 0;
}

SHIRAZ_EXPORT int32_t shiraz_window_copy_argb(ANativeWindow* w, uint32_t* output,
                                              uint64_t capacity) {
    if (!w || !output)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    const uint64_t count = static_cast<uint64_t>(w->width) * static_cast<uint64_t>(w->height);
    if (capacity < count)
        return -EINVAL;
    if (w->front_argb) {
        std::memcpy(output, w->front.data(), static_cast<size_t>(count) * 4);
    } else {
        for (uint64_t i = 0; i < count; ++i) {
            const uint8_t* p = &w->front[static_cast<size_t>(i) * 4];
            output[i] = (static_cast<uint32_t>(p[3]) << 24) | (static_cast<uint32_t>(p[0]) << 16) |
                        (static_cast<uint32_t>(p[1]) << 8) | static_cast<uint32_t>(p[2]);
        }
    }
    return 0;
}

SHIRAZ_EXPORT int32_t shiraz_window_post_argb(ANativeWindow* w, const uint32_t* pixels,
                                              uint64_t count) {
    if (!w || !pixels)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (w->locked)
        return -EBUSY;
    const uint64_t expected = static_cast<uint64_t>(w->width) * static_cast<uint64_t>(w->height);
    if (count != expected)
        return -EINVAL;
    std::memcpy(w->front.data(), pixels, static_cast<size_t>(count) * 4);
    w->front_argb = true;
    ++w->revision;
    if (w->frameSink)
        w->frameSink(reinterpret_cast<const uint32_t*>(w->front.data()), w->width, w->height, true);
    return 0;
}

SHIRAZ_EXPORT int32_t shiraz_window_post_argb_sized(ANativeWindow* w, const uint32_t* pixels,
                                                    uint64_t count, int32_t width, int32_t height) {
    if (!w || !pixels)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (w->locked)
        return -EBUSY;
    if (width != w->width || height != w->height)
        return -EAGAIN;
    const uint64_t expected = static_cast<uint64_t>(w->width) * static_cast<uint64_t>(w->height);
    if (count != expected)
        return -EINVAL;
    std::memcpy(w->front.data(), pixels, static_cast<size_t>(count) * 4);
    w->front_argb = true;
    ++w->revision;
    if (w->frameSink)
        w->frameSink(reinterpret_cast<const uint32_t*>(w->front.data()), w->width, w->height, true);
    return 0;
}

SHIRAZ_EXPORT uint64_t shiraz_window_revision(ANativeWindow* w) {
    if (!w)
        return 0;
    std::lock_guard<std::mutex> guard(w->mutex);
    return w->revision;
}

} // extern "C"

SHIRAZ_EXPORT void shiraz_window_set_frame_sink(ANativeWindow* w, ShirazFrameSink sink) {
    if (!w)
        return;
    std::lock_guard<std::mutex> guard(w->mutex);
    w->frameSink = std::move(sink);
}

#endif
