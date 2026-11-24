// SPDX-License-Identifier: GPL-3.0-only
// Included by native/native_window.cpp on macOS only, right after struct
// ANativeWindow. The M4b window buffer queue (include/shiraz/darwin/window_queue.h,
// build/macos/m4b/design/DESIGN-v2.md section 3): IOSurface slots, the window's
// frame and release events, CPU materialise and the process-wide zero-copy
// counters. All slot state is guarded by ANativeWindow::mutex. Metal objects are
// made in native/darwin/shared_event.mm (MRC, +1 references).
#include "shiraz/darwin/window_queue.h"
#include "shiraz/window_queue_common.hpp"
#include <IOSurface/IOSurfaceRef.h>
#include <condition_variable>

extern "C" {
void* shiraz_mtl_slot_create(void* device, int32_t width, int32_t height, void** surfaceOut, uint32_t* bytesPerRow);
void shiraz_mtl_release(void* object);
void* shiraz_mtl_retain(void* object);
}

namespace shiraz { namespace darwin {
using shiraz::zc_common::SlotState;
using shiraz::zc_common::SLOT_FREE;
using shiraz::zc_common::SLOT_DEQUEUED;
using shiraz::zc_common::SLOT_QUEUED;
using shiraz::zc_common::SLOT_ACQUIRED;
struct QueueSlot {
    void* surface = nullptr;   // IOSurfaceRef +1
    void* texture = nullptr;   // id<MTLTexture> +1
    uint64_t generation = 0;
    int32_t width = 0, height = 0; uint32_t bytes_per_row = 0;
    SlotState state = SLOT_FREE;
    int pins = 0;
    uint64_t frame_value = 0, release_value = 0, sampled_value = 0, revision = 0;
    uint64_t acquired_serial = 0;
    bool pending_mark = false;
    void retire() { shiraz_mtl_release(texture); shiraz_mtl_release(surface); texture = surface = nullptr; }
};
struct WindowQueue {
    QueueSlot slots[SHIRAZ_WINDOW_MAX_SLOTS];
    uint32_t count = 0;
    void* events[2] = {nullptr, nullptr};
    uint64_t serials[2] = {0, 0}, values[2] = {0, 0};
    uint64_t producer = 0, consumer = 0;
    bool abandoned = false, offscreen = false, latest_iosurface = false, ever_queued = false;
    bool logged_extra = false, logged_pin = false;
    uint64_t materialized_revision = 0;
    std::condition_variable cv;
    ~WindowQueue() {
        for (auto& slot : slots) slot.retire();
        for (void* event : events) shiraz_mtl_release(event);
    }
};
}}

namespace {
using shiraz::darwin::WindowQueue;
using shiraz::darwin::QueueSlot;
using namespace shiraz::darwin;
std::mutex deviceMutex;
void* recordedDevice = nullptr;
std::atomic<uint64_t> eventSerials{0}, slotGenerations{0};

WindowQueue* ensureQueue(ANativeWindow* w) {
    if (!w->queue) w->queue.reset(new WindowQueue);
    return w->queue.get();
}
void cpuPosted(ANativeWindow* w) {
    if (w->queue) w->queue->latest_iosurface = false;
}
void fillSlot(const ANativeWindow* w, uint32_t index, ShirazWindowSlot* out, int kind) {
    const WindowQueue* q = w->queue.get();
    const QueueSlot& s = q->slots[index];
    *out = ShirazWindowSlot{};
    out->index = index; out->generation = s.generation;
    out->surface = s.surface; out->texture = s.texture;
    out->width = s.width; out->height = s.height; out->bytes_per_row = s.bytes_per_row; out->fourcc = 'RGBA';
    out->event = q->events[kind]; out->event_serial = q->serials[kind];
    out->value = kind == SHIRAZ_EVENT_FRAME ? s.frame_value : s.release_value;
    out->revision = s.revision; out->flags = SHIRAZ_SLOT_BOTTOM_UP;
}
// Allocates (or reallocates) slot INDEX at the window size. Mutex held.
bool allocateSlot(ANativeWindow* w, uint32_t index) {
    void* device = nullptr;
    { std::lock_guard<std::mutex> guard(deviceMutex); device = recordedDevice; }
    if (!device) { std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE no-device window=%p\n", static_cast<void*>(w)); return false; }
    QueueSlot& s = w->queue->slots[index];
    void* surface = nullptr; uint32_t rowBytes = 0;
    void* texture = shiraz_mtl_slot_create(device, w->width, w->height, &surface, &rowBytes);
    if (!texture) return false;
    s.retire();
    s.surface = surface; s.texture = texture; s.bytes_per_row = rowBytes;
    s.width = w->width; s.height = w->height; s.generation = ++slotGenerations;
    // A new surface has no outstanding GPU readers of this generation.
    s.release_value = s.sampled_value = 0; s.frame_value = 0; s.revision = 0; s.pending_mark = false;
    return true;
}
// A slot the producer may take now (FREE and unpinned, or the older QUEUED one the
// queue step will drop when DROP_QUEUED), or a new slot index; -1 when none.
// EXCLUDE is the producer's current slot. Mutex held; does not mutate.
int pickSlot(const WindowQueue* q, int exclude, bool dropQueued, bool* allocate) {
    return shiraz::zc_common::pickSlot(q, SHIRAZ_WINDOW_MAX_SLOTS, exclude, dropQueued, allocate);
}
// Takes slot INDEX for the producer. Mutex held.
int32_t takeSlot(ANativeWindow* w, int index, bool allocate, ShirazWindowSlot* out) {
    WindowQueue* q = w->queue.get();
    if (allocate) {
        if (index >= 3 && !q->logged_extra) {
            q->logged_extra = true;
            std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE extra-slot reason=pin window=%p\n", static_cast<void*>(w));
            shiraz_zero_copy_count("extra_slots", 1);
        }
        if (!allocateSlot(w, uint32_t(index))) return -ENOMEM;
        q->count = uint32_t(index) + 1;
    }
    QueueSlot& s = q->slots[index];
    if (s.width != w->width || s.height != w->height) { if (!allocateSlot(w, uint32_t(index))) return -ENOMEM; }
    s.state = SLOT_DEQUEUED;
    fillSlot(w, uint32_t(index), out, SHIRAZ_EVENT_RELEASE);
    return 0;
}
// The queue step. Returns 0 or -EAGAIN (resize drop). Mutex held.
int32_t queueStep(ANativeWindow* w, uint32_t index, int32_t width, int32_t height, uint64_t frameValue) {
    WindowQueue* q = w->queue.get();
    QueueSlot& s = q->slots[index];
    if (width != w->width || height != w->height || s.width != width || s.height != height) {
        s.state = SLOT_FREE;  // keeps release_value: it was never sampled at this content
        shiraz_zero_copy_count("resize_drops", 1);
        return -EAGAIN;
    }
    for (uint32_t i = 0; i < q->count; ++i)
        if (i != index && q->slots[i].state == SLOT_QUEUED) { q->slots[i].state = SLOT_FREE; shiraz_zero_copy_count("mailbox_drops", 1); }
    s.frame_value = frameValue;
    s.revision = ++w->revision;
    s.state = SLOT_QUEUED;
    q->latest_iosurface = true; q->ever_queued = true;
    shiraz_zero_copy_count("queued", 1);
    return 0;
}
int32_t checkProducer(ANativeWindow* w, uint64_t producer) {
    return shiraz::zc_common::checkProducer(w, producer);
}
int32_t checkHeld(ANativeWindow* w, uint32_t index, uint64_t generation) {
    return shiraz::zc_common::checkHeld(w, index, generation);
}

// CPU materialise (C2, C7). LOCK holds w->mutex on entry and on return.
int32_t materialize(ANativeWindow* w, std::unique_lock<std::mutex>& lock, uint64_t* outRevision) {
    for (int attempt = 0; attempt < 3; ++attempt) {
        WindowQueue* q = w->queue.get();
        if (!q || !q->latest_iosurface || q->materialized_revision == w->revision) { *outRevision = w->revision; return 0; }
        int newest = shiraz::zc_common::findNewestReadableSlot(q);
        if (newest < 0) { *outRevision = w->revision; return 0; }
        QueueSlot& s = q->slots[newest];
        const uint64_t generation = s.generation, revision = s.revision, frameValue = s.frame_value;
        const int32_t width = s.width, height = s.height; const uint32_t rowBytes = s.bytes_per_row;
        IOSurfaceRef surface = static_cast<IOSurfaceRef>(s.surface);
        void* event = q->events[SHIRAZ_EVENT_FRAME];
        ++s.pins;
        lock.unlock();
        auto started = std::chrono::steady_clock::now();
        int32_t waited = shiraz_event_cpu_wait(event, frameValue, 1000);
        std::vector<uint8_t> staging;
        bool copied = false;
        if (waited == 0) {
            try { staging.resize(size_t(width) * height * 4); } catch (...) { waited = -ENOMEM; }
        }
        if (waited == 0 && IOSurfaceLock(surface, kIOSurfaceLockReadOnly, nullptr) == 0) {
            const uint8_t* base = static_cast<const uint8_t*>(IOSurfaceGetBaseAddress(surface));
            // GL rendered the slot bottom-up; front is top-down.
            for (int32_t y = 0; y < height; ++y)
                std::memcpy(&staging[size_t(y) * width * 4], base + size_t(height - 1 - y) * rowBytes, size_t(width) * 4);
            IOSurfaceUnlock(surface, kIOSurfaceLockReadOnly, nullptr);
            copied = true;
            shiraz_cpu_pixel_reads_add("materialize", uint64_t(width) * height * 4);
            shiraz_zero_copy_sample("materialize_ms", std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count());
        }
        lock.lock();
        q = w->queue.get();
        --q->slots[newest].pins;
        q->cv.notify_all();
        if (waited) return waited;
        if (!copied) return -EIO;
        if (w->width == width && w->height == height && q->slots[newest].generation == generation && !w->locked) {
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
    int error = sync_shared(w); if (error) return error;
    uint64_t copiedRevision = w->revision;
    error = materialize(w, lock, &copiedRevision); if (error) return error;
    auto count = uint64_t(w->width) * w->height;
    if (capacity < count) return -EINVAL;
    if (w->front_argb) std::memcpy(output, w->front.data(), size_t(count) * 4);
    else shiraz::zc_common::copyRgbaToArgb(w->front.data(), output, count);
    // Never report a revision newer than the pixels returned.
    if (revision) *revision = (w->queue && w->queue->latest_iosurface) ? copiedRevision : w->revision;
    return 0;
}
} // namespace

extern "C" {
int32_t shiraz_zero_copy_mode(void) { return shiraz::zc_common::zeroCopyMode(); }
void shiraz_zero_copy_fatal(const char* reason) { shiraz::zc_common::zeroCopyFatal(reason); }
void shiraz_cpu_pixel_reads_add(const char* site, uint64_t bytes) { shiraz::zc_common::addCpuPixelReads(site, bytes); }
void shiraz_observer_reads_add(const char* site, uint64_t bytes) { shiraz::zc_common::addObserverReads(site, bytes); }
void shiraz_zero_copy_count(const char* name, uint64_t amount) { shiraz::zc_common::addZeroCopyCount(name, amount); }
void shiraz_zero_copy_event(const char* name, ANativeWindow* window) { shiraz::zc_common::emitZeroCopyEvent(name, window); }
void shiraz_zero_copy_sample(const char* name, double ms) { shiraz::zc_common::recordZeroCopySample(name, ms); }
uint64_t shiraz_cpu_pixel_reads_total(void) { return shiraz::zc_common::cpuPixelReadsTotal(); }
void shiraz_zero_copy_counters(char* out, size_t size) { shiraz::zc_common::formatCounters(out, size); }

int32_t shiraz_window_set_device(void* device) {
    if (!device) return -EINVAL;
    std::lock_guard<std::mutex> guard(deviceMutex);
    if (!recordedDevice) { recordedDevice = shiraz_mtl_retain(device); return 0; }
    return recordedDevice == device ? 0 : -EXDEV;
}
void* shiraz_window_adopt_event(ANativeWindow* w, int kind, void* event, uint64_t* serial) {
    if (!w || (kind != SHIRAZ_EVENT_FRAME && kind != SHIRAZ_EVENT_RELEASE)) { shiraz_mtl_release(event); return nullptr; }
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = ensureQueue(w);
    if (!q->events[kind]) { q->events[kind] = event; q->serials[kind] = ++eventSerials; }
    else if (event != q->events[kind]) shiraz_mtl_release(event);
    else shiraz_mtl_release(event);  // same object adopted twice: drop the extra +1
    if (serial) *serial = q->serials[kind];
    return q->events[kind];
}
void* shiraz_window_event(ANativeWindow* w, int kind, uint64_t* serial) {
    if (!w || (kind != SHIRAZ_EVENT_FRAME && kind != SHIRAZ_EVENT_RELEASE)) return nullptr;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = ensureQueue(w);
    if (serial) *serial = q->serials[kind];
    return q->events[kind];
}
uint64_t shiraz_window_next_value(ANativeWindow* w, int kind) {
    if (!w || (kind != SHIRAZ_EVENT_FRAME && kind != SHIRAZ_EVENT_RELEASE)) return 0;
    std::lock_guard<std::mutex> guard(w->mutex);
    return ++ensureQueue(w)->values[kind];
}
int32_t shiraz_window_connect_producer(ANativeWindow* w, uint64_t producer) {
    return shiraz::zc_common::connectProducer(w, producer, ensureQueue);
}
void shiraz_window_disconnect_producer(ANativeWindow* w, uint64_t producer) {
    shiraz::zc_common::disconnectProducer(w, producer);
}
int32_t shiraz_window_dequeue_slot(ANativeWindow* w, uint64_t producer, ShirazWindowSlot* out) {
    if (!w || !out) return -EINVAL;
    std::unique_lock<std::mutex> lock(w->mutex);
    if (int32_t error = checkProducer(w, producer)) return error;
    for (int round = 0; round < 2; ++round) {
        bool allocate = false;
        int index = pickSlot(w->queue.get(), -1, false, &allocate);
        if (index >= 0) return takeSlot(w, index, allocate, out);
        if (round == 0) {
            w->queue->cv.wait_for(lock, std::chrono::milliseconds(16));
            if (int32_t error = checkProducer(w, producer)) return error;
        }
    }
    shiraz_zero_copy_count("pin_drops", 1);
    if (!w->queue->logged_pin) { w->queue->logged_pin = true; std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE pin-timeout window=%p\n", static_cast<void*>(w)); }
    return -EWOULDBLOCK;
}
int32_t shiraz_window_queue_slot(ANativeWindow* w, uint64_t producer, uint32_t index, uint64_t generation,
                                 int32_t width, int32_t height, uint64_t frameValue) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (int32_t error = checkProducer(w, producer)) return error;
    if (int32_t error = checkHeld(w, index, generation)) return error;
    int32_t result = queueStep(w, index, width, height, frameValue);
    w->queue->cv.notify_all();
    return result;
}
int32_t shiraz_window_queue_and_dequeue(ANativeWindow* w, uint64_t producer, uint32_t index, uint64_t generation,
                                        int32_t width, int32_t height, uint64_t frameValue, ShirazWindowSlot* next) {
    if (!w || !next) return -EINVAL;
    std::unique_lock<std::mutex> lock(w->mutex);
    for (int round = 0; round < 2; ++round) {
        if (int32_t error = checkProducer(w, producer)) return error;
        if (int32_t error = checkHeld(w, index, generation)) return error;
        WindowQueue* q = w->queue.get();
        // Will the queue step free CUR itself (resize) or the older QUEUED slot (mailbox)?
        const QueueSlot& cur = q->slots[index];
        const bool resized = width != w->width || height != w->height || cur.width != width || cur.height != height;
        bool allocate = false;
        int pick = pickSlot(q, resized ? -1 : int(index), !resized, &allocate);
        if (pick >= 0) {
            queueStep(w, index, width, height, frameValue);
            int32_t taken = takeSlot(w, pick, allocate, next);
            q->cv.notify_all();
            return taken;
        }
        // Nothing can be had without waiting: CUR stays DEQUEUED and unqueued.
        if (round == 0) q->cv.wait_for(lock, std::chrono::milliseconds(16));
    }
    shiraz_zero_copy_count("pin_drops", 1);
    if (!w->queue->logged_pin) { w->queue->logged_pin = true; std::fprintf(stderr, "SHIRAZ_WINDOW_QUEUE pin-timeout window=%p\n", static_cast<void*>(w)); }
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
int32_t shiraz_window_acquire_slot(ANativeWindow* w, uint64_t consumer, uint64_t frameSerial, ShirazWindowSlot* out) {
    if (!w || !out || !consumer) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q) return -ENODATA;
    if (q->abandoned) return -EPIPE;
    if (q->consumer && q->consumer != consumer) return -EBUSY;
    if (!q->latest_iosurface) return -ENODATA;
    q->consumer = consumer;
    int acquired = -1, queued = -1;
    for (uint32_t i = 0; i < q->count; ++i) {
        if (q->slots[i].state == SLOT_ACQUIRED) acquired = int(i);
        if (q->slots[i].state == SLOT_QUEUED) queued = int(i);
    }
    if (acquired >= 0 && q->slots[acquired].acquired_serial == frameSerial) {
        fillSlot(w, uint32_t(acquired), out, SHIRAZ_EVENT_FRAME);
        return 0;
    }
    if (queued >= 0) {
        if (acquired >= 0) {
            QueueSlot& old = q->slots[acquired];
            if (old.pending_mark) return -EINPROGRESS;
            // Release on supersede (C1): every frame that sampled OLD has a
            // smaller serial and committed its release value before now.
            old.state = SLOT_FREE; old.release_value = old.sampled_value;
        }
        QueueSlot& next = q->slots[queued];
        next.state = SLOT_ACQUIRED; next.acquired_serial = frameSerial; next.pending_mark = true;
        q->cv.notify_all();
        fillSlot(w, uint32_t(queued), out, SHIRAZ_EVENT_FRAME);
        return 0;
    }
    if (acquired >= 0) {
        QueueSlot& same = q->slots[acquired];
        same.acquired_serial = frameSerial; same.pending_mark = true;
        fillSlot(w, uint32_t(acquired), out, SHIRAZ_EVENT_FRAME);
        return 0;
    }
    return -ENODATA;
}
const char* shiraz_window_nodata_reason(ANativeWindow* w) {
    return shiraz::zc_common::nodataReason(w);
}
int32_t shiraz_window_mark_sampled(ANativeWindow* w, uint64_t consumer, uint32_t index, uint64_t generation, uint64_t releaseValue) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q || q->consumer != consumer || index >= q->count) return -EINVAL;
    QueueSlot& s = q->slots[index];
    if (s.state != SLOT_ACQUIRED || s.generation != generation) return -ESTALE;
    s.sampled_value = releaseValue; s.pending_mark = false;
    return 0;
}
int32_t shiraz_window_consumer_detach(ANativeWindow* w, uint64_t consumer) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    WindowQueue* q = w->queue.get();
    if (!q || q->consumer != consumer) return -EINVAL;
    for (uint32_t i = 0; i < q->count; ++i) {
        QueueSlot& s = q->slots[i];
        if (s.state == SLOT_ACQUIRED) { s.state = SLOT_FREE; s.release_value = 0; s.sampled_value = 0; s.pending_mark = false; }
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
int32_t shiraz_window_copy_argb_revision(ANativeWindow* w, uint32_t* pixels, size_t count, uint64_t* revision) {
    if (!w || !pixels) return -EINVAL;
    return copyArgbLocked(w, pixels, count, revision);
}
}
