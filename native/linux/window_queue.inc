// SPDX-License-Identifier: GPL-3.0-only
// Included by native/native_window.cpp on Linux only, right after struct ANativeWindow.
// Implements the GBM/DMA-BUF ANativeWindow zero-copy buffer queue
// (include/shiraz/linux/window_queue.h) with kernel sync_file fences, on-demand CPU materialization
// via AHardwareBuffer_lock, and SHIRAZ_EGL_ZERO_COPY telemetry matching the macOS zero-copy queue.
#include "shiraz/linux/window_queue.h"
#include "shiraz/window_queue_common.hpp"
#include <condition_variable>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>

extern "C" {
// Weak declarations so standalone unit binaries (test-native-window, test-surface-lifetime)
// that compile native_window.cpp without hardware_buffer.cpp link cleanly; inside libandroid.so
// these resolve directly to hardware_buffer.cpp's strong definitions.
__attribute__((weak)) int AHardwareBuffer_allocate(const AHardwareBuffer_Desc*, AHardwareBuffer**);
__attribute__((weak)) void AHardwareBuffer_acquire(AHardwareBuffer*);
__attribute__((weak)) void AHardwareBuffer_release(AHardwareBuffer*);
__attribute__((weak)) void AHardwareBuffer_describe(const AHardwareBuffer*, AHardwareBuffer_Desc*);
__attribute__((weak)) int AHardwareBuffer_lock(AHardwareBuffer*, uint64_t, int32_t, const ARect*,
                                               void**);
__attribute__((weak)) int AHardwareBuffer_unlock(AHardwareBuffer*, int32_t*);
__attribute__((weak)) int shiraz_hardware_buffer_export(AHardwareBuffer*, uint32_t*, uint32_t*);
}

namespace shiraz {
namespace linux_zc {

using shiraz::zc_common::SLOT_ACQUIRED;
using shiraz::zc_common::SLOT_DEQUEUED;
using shiraz::zc_common::SLOT_FREE;
using shiraz::zc_common::SLOT_QUEUED;
using shiraz::zc_common::SlotState;

inline void closeFd(int32_t& fd) {
    if (fd >= 0) {
        close(fd);
        fd = -1;
    }
}

inline int32_t dupUnsignaledFence(int32_t fd) {
    if (fd < 0)
        return -1;
    // Fast-path C10 optimization: if the sync_file is already signaled, skip dup + GPU wait.
    pollfd p{fd, POLLIN, 0};
    if (poll(&p, 1, 0) > 0 && (p.revents & POLLIN))
        return -1;
    int duped = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    return duped >= 0 ? duped : -1;
}

struct QueueSlot {
    AHardwareBuffer* buffer = nullptr; // +1 owned
    uint64_t generation = 0;
    int32_t width = 0, height = 0;
    uint32_t stride_bytes = 0;
    uint32_t drm_fourcc = 0;
    SlotState state = SLOT_FREE;
    int pins = 0;
    int32_t frame_fence_fd = -1;
    int32_t release_fence_fd = -1;
    int32_t sampled_fence_fd = -1;
    uint64_t revision = 0;
    uint64_t acquired_serial = 0;
    bool pending_mark = false;

    void retire() {
        closeFd(frame_fence_fd);
        closeFd(release_fence_fd);
        closeFd(sampled_fence_fd);
        if (buffer && AHardwareBuffer_release)
            AHardwareBuffer_release(buffer);
        buffer = nullptr;
    }
};

struct WindowQueue {
    QueueSlot slots[SHIRAZ_WINDOW_MAX_SLOTS];
    uint32_t count = 0;
    uint64_t producer = 0, consumer = 0;
    bool abandoned = false, offscreen = false, latest_dmabuf = false, ever_queued = false;
    bool logged_extra = false, logged_pin = false;
    uint64_t materialized_revision = 0;
    std::condition_variable cv;

    ~WindowQueue() {
        for (auto& slot : slots)
            slot.retire();
    }
};

} // namespace linux_zc
} // namespace shiraz

namespace {
using shiraz::linux_zc::closeFd;
using shiraz::linux_zc::dupUnsignaledFence;
using shiraz::linux_zc::QueueSlot;
using shiraz::linux_zc::SLOT_ACQUIRED;
using shiraz::linux_zc::SLOT_DEQUEUED;
using shiraz::linux_zc::SLOT_FREE;
using shiraz::linux_zc::SLOT_QUEUED;
using shiraz::linux_zc::WindowQueue;

std::atomic<uint64_t> slotGenerations{0};

WindowQueue* ensureQueue(ANativeWindow* w) {
    if (!w->queue)
        w->queue.reset(new WindowQueue);
    return w->queue.get();
}

void cpuPosted(ANativeWindow* w) {
    if (w->queue)
        w->queue->latest_dmabuf = false;
}

void fillSlot(const ANativeWindow* w, uint32_t index, ShirazWindowSlot* out, bool forConsumer) {
    const WindowQueue* q = w->queue.get();
    const QueueSlot& s = q->slots[index];
    *out = ShirazWindowSlot{};
    out->index = index;
    out->generation = s.generation;
    out->buffer = s.buffer;
    out->width = s.width;
    out->height = s.height;
    out->stride_bytes = s.stride_bytes;
    out->drm_fourcc = s.drm_fourcc;
    out->fence_fd = dupUnsignaledFence(forConsumer ? s.frame_fence_fd : s.release_fence_fd);
    out->revision = s.revision;
    out->flags = SHIRAZ_SLOT_BOTTOM_UP;
}

bool allocateSlot(ANativeWindow* w, uint32_t index) {
    if (!AHardwareBuffer_allocate || !shiraz_hardware_buffer_export || !AHardwareBuffer_release)
        return false;
    // Usage: CPU_READ_OFTEN (3) | GPU_SAMPLED_IMAGE (0x100) | GPU_FRAMEBUFFER (0x200).
    AHardwareBuffer_Desc desc{};
    desc.width = static_cast<uint32_t>(w->width);
    desc.height = static_cast<uint32_t>(w->height);
    desc.layers = 1;
    desc.format = 1; // RGBA_8888 -> GBM_FORMAT_ABGR8888
    desc.usage = 0x303;
    AHardwareBuffer* buf = nullptr;
    if (AHardwareBuffer_allocate(&desc, &buf) != 0 || !buf)
        return false;
    uint32_t fourcc = 0, pitch = 0;
    int testFd = shiraz_hardware_buffer_export(buf, &fourcc, &pitch);
    if (testFd < 0 || pitch < static_cast<uint32_t>(w->width) * 4) {
        if (testFd >= 0)
            close(testFd);
        AHardwareBuffer_release(buf);
        return false;
    }
    close(testFd);
    QueueSlot& s = w->queue->slots[index];
    s.retire();
    s.buffer = buf;
    s.width = w->width;
    s.height = w->height;
    s.stride_bytes = pitch;
    s.drm_fourcc = fourcc;
    s.generation = ++slotGenerations;
    s.revision = 0;
    s.pending_mark = false;
    return true;
}

int pickSlot(const WindowQueue* q, int exclude, bool dropQueued, bool* allocate) {
    return shiraz::zc_common::pickSlot(q, SHIRAZ_WINDOW_MAX_SLOTS, exclude, dropQueued, allocate);
}

int32_t takeSlot(ANativeWindow* w, int index, bool allocate, ShirazWindowSlot* out) {
    WindowQueue* q = w->queue.get();
    if (allocate) {
        if (index >= 3 && !q->logged_extra) {
            q->logged_extra = true;
            std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE extra-slot reason=pin window=%p\n",
                         static_cast<void*>(w));
            shiraz_zero_copy_count("extra_slots", 1);
        }
        if (!allocateSlot(w, uint32_t(index)))
            return -ENOMEM;
        q->count = uint32_t(index) + 1;
    }
    QueueSlot& s = q->slots[index];
    if (s.width != w->width || s.height != w->height) {
        if (!allocateSlot(w, uint32_t(index)))
            return -ENOMEM;
    }
    s.state = SLOT_DEQUEUED;
    fillSlot(w, uint32_t(index), out, false);
    return 0;
}

int32_t queueStep(ANativeWindow* w, uint32_t index, int32_t width, int32_t height,
                  int32_t& frameFenceFd) {
    WindowQueue* q = w->queue.get();
    QueueSlot& s = q->slots[index];
    if (width != w->width || height != w->height || s.width != width || s.height != height) {
        closeFd(frameFenceFd);
        s.state = SLOT_FREE;
        shiraz_zero_copy_count("resize_drops", 1);
        return -EAGAIN;
    }
    for (uint32_t i = 0; i < q->count; ++i) {
        if (i != index && q->slots[i].state == SLOT_QUEUED) {
            closeFd(q->slots[i].frame_fence_fd);
            q->slots[i].state = SLOT_FREE;
            shiraz_zero_copy_count("mailbox_drops", 1);
        }
    }
    closeFd(s.frame_fence_fd);
    s.frame_fence_fd = frameFenceFd;
    frameFenceFd = -1;
    s.revision = ++w->revision;
    s.state = SLOT_QUEUED;
    q->latest_dmabuf = true;
    q->ever_queued = true;
    shiraz_zero_copy_count("queued", 1);
    return 0;
}

int32_t checkProducer(ANativeWindow* w, uint64_t producer) {
    return shiraz::zc_common::checkProducer(w, producer);
}

int32_t checkHeld(ANativeWindow* w, uint32_t index, uint64_t generation) {
    return shiraz::zc_common::checkHeld(w, index, generation);
}

int32_t waitFenceCpu(int32_t fd, int timeoutMs) {
    if (fd < 0)
        return 0;
    pollfd p{fd, POLLIN, 0};
    int res = 0;
    do {
        res = poll(&p, 1, timeoutMs);
    } while (res < 0 && errno == EINTR);
    if (res == 0)
        return -ETIMEDOUT;
    if (res < 0 || (p.revents & (POLLERR | POLLNVAL)))
        return -EIO;
    return 0;
}

// On-demand CPU materialization for PixelCopy / screenshots / software Canvas fallback.
int32_t materialize(ANativeWindow* w, std::unique_lock<std::mutex>& lock, uint64_t* outRevision) {
    if (!AHardwareBuffer_acquire || !AHardwareBuffer_release || !AHardwareBuffer_lock ||
        !AHardwareBuffer_unlock) {
        *outRevision = w->revision;
        return 0;
    }
    for (int attempt = 0; attempt < 3; ++attempt) {
        WindowQueue* q = w->queue.get();
        if (!q || !q->latest_dmabuf || q->materialized_revision == w->revision) {
            *outRevision = w->revision;
            return 0;
        }
        int newest = shiraz::zc_common::findNewestReadableSlot(q);
        if (newest < 0) {
            *outRevision = w->revision;
            return 0;
        }
        QueueSlot& s = q->slots[newest];
        const uint64_t generation = s.generation, revision = s.revision;
        const int32_t width = s.width, height = s.height;
        const uint32_t rowBytes = s.stride_bytes;
        AHardwareBuffer* buf = s.buffer;
        AHardwareBuffer_acquire(buf);
        int32_t fenceFd = dupUnsignaledFence(s.frame_fence_fd);
        ++s.pins;
        lock.unlock();

        auto started = std::chrono::steady_clock::now();
        int32_t waited = waitFenceCpu(fenceFd, 1000);
        closeFd(fenceFd);
        std::vector<uint8_t> staging;
        bool copied = false;
        if (waited == 0) {
            try {
                staging.resize(size_t(width) * height * 4);
            } catch (...) {
                waited = -ENOMEM;
            }
        }
        if (waited == 0) {
            void* mapped = nullptr;
            if (AHardwareBuffer_lock(buf, 3 /* CPU_READ_OFTEN */, -1, nullptr, &mapped) == 0 &&
                mapped) {
                const auto* base = static_cast<const uint8_t*>(mapped);
                // GL renders into the DMA-BUF slot bottom-up; ANativeWindow::front is top-down.
                for (int32_t y = 0; y < height; ++y) {
                    std::memcpy(&staging[size_t(y) * width * 4],
                                base + size_t(height - 1 - y) * rowBytes, size_t(width) * 4);
                }
                AHardwareBuffer_unlock(buf, nullptr);
                copied = true;
                shiraz_cpu_pixel_reads_add("materialize", uint64_t(width) * height * 4);
                shiraz_zero_copy_sample("materialize_ms",
                                        std::chrono::duration<double, std::milli>(
                                            std::chrono::steady_clock::now() - started)
                                            .count());
            }
        }
        AHardwareBuffer_release(buf);

        lock.lock();
        q = w->queue.get();
        --q->slots[newest].pins;
        q->cv.notify_all();
        if (waited)
            return waited;
        if (!copied)
            return -EIO;
        if (w->width == width && w->height == height && q->slots[newest].generation == generation &&
            !w->locked) {
            w->front.swap(staging);
            w->front_argb = false;
            q->materialized_revision = revision;
            *outRevision = revision;
            return 0;
        }
    }
    return -EAGAIN;
}

int32_t copyArgbLocked(ANativeWindow* w, uint32_t* output, uint64_t capacity, uint64_t* revision) {
    std::unique_lock<std::mutex> lock(w->mutex);
    int error = sync_shared(w);
    if (error)
        return error;
    uint64_t copiedRevision = w->revision;
    error = materialize(w, lock, &copiedRevision);
    if (error)
        return error;
    auto count = uint64_t(w->width) * w->height;
    if (capacity < count)
        return -EINVAL;
    if (w->front_argb) {
        std::memcpy(output, w->front.data(), size_t(count) * 4);
    } else {
        shiraz::zc_common::copyRgbaToArgb(w->front.data(), output, count);
    }
    if (revision)
        *revision = (w->queue && w->queue->latest_dmabuf) ? copiedRevision : w->revision;
    return 0;
}

} // namespace

extern "C" {

int32_t shiraz_zero_copy_mode(void) {
    return shiraz::zc_common::zeroCopyMode();
}

void shiraz_zero_copy_fatal(const char* reason) {
    shiraz::zc_common::zeroCopyFatal(reason);
}

void shiraz_cpu_pixel_reads_add(const char* site, uint64_t bytes) {
    shiraz::zc_common::addCpuPixelReads(site, bytes);
}

void shiraz_observer_reads_add(const char* site, uint64_t bytes) {
    shiraz::zc_common::addObserverReads(site, bytes);
}

void shiraz_zero_copy_count(const char* name, uint64_t amount) {
    shiraz::zc_common::addZeroCopyCount(name, amount);
}

void shiraz_zero_copy_event(const char* name, ANativeWindow* window) {
    shiraz::zc_common::emitZeroCopyEvent(name, window);
}

void shiraz_zero_copy_sample(const char* name, double ms) {
    shiraz::zc_common::recordZeroCopySample(name, ms);
}

uint64_t shiraz_cpu_pixel_reads_total(void) {
    return shiraz::zc_common::cpuPixelReadsTotal();
}

void shiraz_zero_copy_counters(char* out, size_t size) {
    shiraz::zc_common::formatCounters(out, size);
}

int32_t shiraz_window_connect_producer(ANativeWindow* w, uint64_t producer) {
    return shiraz::zc_common::connectProducer(w, producer, ensureQueue);
}

void shiraz_window_disconnect_producer(ANativeWindow* w, uint64_t producer) {
    shiraz::zc_common::disconnectProducer(w, producer);
}

int32_t shiraz_window_dequeue_slot(ANativeWindow* w, uint64_t producer, ShirazWindowSlot* out) {
    if (!w || !out)
        return -EINVAL;
    std::unique_lock<std::mutex> lock(w->mutex);
    if (int32_t error = checkProducer(w, producer))
        return error;
    for (int round = 0; round < 2; ++round) {
        bool allocate = false;
        int index = pickSlot(w->queue.get(), -1, false, &allocate);
        if (index >= 0)
            return takeSlot(w, index, allocate, out);
        if (round == 0) {
            w->queue->cv.wait_for(lock, std::chrono::milliseconds(16));
            if (int32_t error = checkProducer(w, producer))
                return error;
        }
    }
    shiraz_zero_copy_count("pin_drops", 1);
    if (!w->queue->logged_pin) {
        w->queue->logged_pin = true;
        std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE pin-timeout window=%p\n", static_cast<void*>(w));
    }
    return -EWOULDBLOCK;
}

int32_t shiraz_window_queue_slot(ANativeWindow* w, uint64_t producer, uint32_t index,
                                 uint64_t generation, int32_t width, int32_t height,
                                 int32_t frameFenceFd) {
    if (!w) {
        closeFd(frameFenceFd);
        return -EINVAL;
    }
    std::lock_guard<std::mutex> guard(w->mutex);
    if (int32_t error = checkProducer(w, producer)) {
        closeFd(frameFenceFd);
        return error;
    }
    if (int32_t error = checkHeld(w, index, generation)) {
        closeFd(frameFenceFd);
        return error;
    }
    int32_t result = queueStep(w, index, width, height, frameFenceFd);
    closeFd(frameFenceFd);
    w->queue->cv.notify_all();
    return result;
}

int32_t shiraz_window_queue_and_dequeue(ANativeWindow* w, uint64_t producer, uint32_t index,
                                        uint64_t generation, int32_t width, int32_t height,
                                        int32_t frameFenceFd, ShirazWindowSlot* next) {
    if (!w || !next) {
        closeFd(frameFenceFd);
        return -EINVAL;
    }
    std::unique_lock<std::mutex> lock(w->mutex);
    for (int round = 0; round < 2; ++round) {
        if (int32_t error = checkProducer(w, producer)) {
            closeFd(frameFenceFd);
            return error;
        }
        if (int32_t error = checkHeld(w, index, generation)) {
            closeFd(frameFenceFd);
            return error;
        }
        WindowQueue* q = w->queue.get();
        const QueueSlot& cur = q->slots[index];
        const bool resized =
            width != w->width || height != w->height || cur.width != width || cur.height != height;
        bool allocate = false;
        int pick = pickSlot(q, resized ? -1 : int(index), !resized, &allocate);
        if (pick >= 0) {
            queueStep(w, index, width, height, frameFenceFd);
            closeFd(frameFenceFd);
            int32_t taken = takeSlot(w, pick, allocate, next);
            q->cv.notify_all();
            return taken;
        }
        if (round == 0)
            q->cv.wait_for(lock, std::chrono::milliseconds(16));
    }
    closeFd(frameFenceFd);
    shiraz_zero_copy_count("pin_drops", 1);
    if (!w->queue->logged_pin) {
        w->queue->logged_pin = true;
        std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE pin-timeout window=%p\n", static_cast<void*>(w));
    }
    return -EWOULDBLOCK;
}

int32_t shiraz_window_cancel_slot(ANativeWindow* w, uint64_t producer, uint32_t index) {
    return shiraz::zc_common::cancelSlot(w, producer, index);
}

int32_t shiraz_window_cpu_frames_required(ANativeWindow* w, const char** reason) {
    return shiraz::zc_common::cpuFramesRequired(w, reason);
}

void shiraz_window_set_offscreen(ANativeWindow* w) {
    shiraz::zc_common::setOffscreen(w, ensureQueue);
}

int32_t shiraz_window_acquire_slot(ANativeWindow* w, uint64_t consumer, uint64_t frameSerial,
                                   ShirazWindowSlot* out) {
    if (!w || !out || !consumer)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q)
        return -ENODATA;
    if (q->abandoned)
        return -EPIPE;
    if (q->consumer && q->consumer != consumer)
        return -EBUSY;
    if (!q->latest_dmabuf)
        return -ENODATA;
    q->consumer = consumer;
    int acquired = -1, queued = -1;
    for (uint32_t i = 0; i < q->count; ++i) {
        if (q->slots[i].state == SLOT_ACQUIRED)
            acquired = int(i);
        if (q->slots[i].state == SLOT_QUEUED)
            queued = int(i);
    }
    if (acquired >= 0 && q->slots[acquired].acquired_serial == frameSerial) {
        fillSlot(w, uint32_t(acquired), out, true);
        return 0;
    }
    if (queued >= 0) {
        if (acquired >= 0) {
            QueueSlot& old = q->slots[acquired];
            if (old.pending_mark)
                return -EINPROGRESS;
            // Release on supersede (C1 invariant): transfer consumer's release fence.
            old.state = SLOT_FREE;
            closeFd(old.release_fence_fd);
            old.release_fence_fd = old.sampled_fence_fd;
            old.sampled_fence_fd = -1;
        }
        QueueSlot& next = q->slots[queued];
        next.state = SLOT_ACQUIRED;
        next.acquired_serial = frameSerial;
        next.pending_mark = true;
        q->cv.notify_all();
        fillSlot(w, uint32_t(queued), out, true);
        return 0;
    }
    if (acquired >= 0) {
        QueueSlot& same = q->slots[acquired];
        same.acquired_serial = frameSerial;
        same.pending_mark = true;
        fillSlot(w, uint32_t(acquired), out, true);
        return 0;
    }
    return -ENODATA;
}

const char* shiraz_window_nodata_reason(ANativeWindow* w) {
    return shiraz::zc_common::nodataReason(w);
}

int32_t shiraz_window_mark_sampled(ANativeWindow* w, uint64_t consumer, uint32_t index,
                                   uint64_t generation, int32_t releaseFenceFd) {
    if (!w) {
        closeFd(releaseFenceFd);
        return -EINVAL;
    }
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q || q->consumer != consumer || index >= q->count) {
        closeFd(releaseFenceFd);
        return -EINVAL;
    }
    QueueSlot& s = q->slots[index];
    if (s.state != SLOT_ACQUIRED || s.generation != generation) {
        closeFd(releaseFenceFd);
        return -ESTALE;
    }
    closeFd(s.sampled_fence_fd);
    s.sampled_fence_fd = releaseFenceFd;
    s.pending_mark = false;
    return 0;
}

int32_t shiraz_window_consumer_detach(ANativeWindow* w, uint64_t consumer) {
    if (!w)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q || q->consumer != consumer)
        return -EINVAL;
    for (uint32_t i = 0; i < q->count; ++i) {
        QueueSlot& s = q->slots[i];
        if (s.state == SLOT_ACQUIRED) {
            s.state = SLOT_FREE;
            closeFd(s.release_fence_fd);
            closeFd(s.sampled_fence_fd);
            s.pending_mark = false;
        }
    }
    q->consumer = 0;
    q->cv.notify_all();
    return 0;
}

void shiraz_window_abandon(ANativeWindow* w) {
    shiraz::zc_common::abandon(w, ensureQueue);
}

int32_t shiraz_window_abandoned(ANativeWindow* w) {
    return shiraz::zc_common::abandoned(w);
}

int32_t shiraz_window_copy_argb_revision(ANativeWindow* w, uint32_t* pixels, size_t count,
                                         uint64_t* revision) {
    if (!w || !pixels)
        return -EINVAL;
    return copyArgbLocked(w, pixels, count, revision);
}

} // extern "C"
