// SPDX-License-Identifier: GPL-3.0-only
#pragma once
// Shared zero-copy ANativeWindow queue state machine and telemetry helpers used
// by both Linux (native/linux/window_queue.inc, GBM/DMA-BUF + sync_file fences)
// and macOS (native/darwin/window_queue.inc, IOSurface + MTLSharedEvent).

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

struct ANativeWindow;

namespace shiraz {
namespace zc_common {

enum SlotState { SLOT_FREE, SLOT_DEQUEUED, SLOT_QUEUED, SLOT_ACQUIRED };

struct Counters {
    std::mutex mutex;
    std::map<std::string, uint64_t> reads, observer, counts;
    std::map<std::string, std::vector<double>> samples;
    uint64_t readTotal = 0, observerTotal = 0;
};

inline Counters& counters() {
    static Counters* value = new Counters;
    return *value;
}

inline bool traceZeroCopy() {
    static const bool enabled = [] {
        const char* v = std::getenv("SHIRAZ_TRACE_ZERO_COPY");
        return v && std::strcmp(v, "1") == 0;
    }();
    return enabled;
}

inline double percentile(std::vector<double> values, double p) {
    if (values.empty())
        return 0;
    std::sort(values.begin(), values.end());
    const size_t index = size_t(p * double(values.size() - 1) + 0.5);
    return values[std::min(index, values.size() - 1)];
}

inline void formatCounters(char* out, size_t size) {
    if (!out || !size)
        return;
    auto& c = counters();
    std::string line = "SHIRAZ_ZC_COUNTERS";
    {
        std::lock_guard<std::mutex> guard(c.mutex);
        line += " cpu_pixel_reads=" + std::to_string(c.readTotal);
        for (auto& e : c.reads)
            line += " read." + e.first + "=" + std::to_string(e.second);
        line += " observer_reads=" + std::to_string(c.observerTotal);
        for (auto& e : c.observer)
            line += " observer." + e.first + "=" + std::to_string(e.second);
        for (auto& e : c.counts)
            line += " " + e.first + "=" + std::to_string(e.second);
        char number[64];
        for (auto& e : c.samples) {
            std::snprintf(number, sizeof number, " %s_n=%zu", e.first.c_str(), e.second.size());
            line += number;
            std::snprintf(number, sizeof number, " %s_p50=%.3f", e.first.c_str(),
                          percentile(e.second, .5));
            line += number;
            std::snprintf(number, sizeof number, " %s_p90=%.3f", e.first.c_str(),
                          percentile(e.second, .9));
            line += number;
            std::snprintf(number, sizeof number, " %s_p99=%.3f", e.first.c_str(),
                          percentile(e.second, .99));
            line += number;
        }
    }
    std::snprintf(out, size, "%s", line.c_str());
}

inline void printCounters() {
    char line[4096];
    formatCounters(line, sizeof line);
    std::fprintf(stderr, "%s\n", line);
}

inline void startReporter() {
    if (!traceZeroCopy())
        return;
    static std::once_flag once;
    std::call_once(once, [] {
        std::atexit(printCounters);
        std::thread([] {
            for (;;) {
                std::this_thread::sleep_for(std::chrono::seconds(1));
                printCounters();
            }
        }).detach();
    });
}

inline int32_t zeroCopyMode() {
    static const int32_t mode = [] {
        const char* v = std::getenv("SHIRAZ_EGL_ZERO_COPY");
        if (!v || !*v || std::strcmp(v, "1") == 0)
            return 1;
        if (std::strcmp(v, "0") == 0)
            return 0;
        if (std::strcmp(v, "require") == 0)
            return 2;
        std::fprintf(stderr,
                     "SHIRAZ_RUNTIME_FATAL zero-copy reason=bad-env SHIRAZ_EGL_ZERO_COPY=%s "
                     "(0, 1 or require)\n",
                     v);
        std::abort();
    }();
    return mode;
}

inline void zeroCopyFatal(const char* reason) {
    if (zeroCopyMode() != 2)
        return;
    std::fprintf(stderr, "SHIRAZ_RUNTIME_FATAL zero-copy reason=%s\n", reason ? reason : "unknown");
    printCounters();
    std::fflush(stderr);
    std::abort();
}

inline void addCpuPixelReads(const char* site, uint64_t bytes) {
    auto& c = counters();
    {
        std::lock_guard<std::mutex> guard(c.mutex);
        c.reads[site ? site : "unknown"] += 1;
        ++c.readTotal;
        (void)bytes;
    }
    startReporter();
}

inline void addObserverReads(const char* site, uint64_t bytes) {
    auto& c = counters();
    {
        std::lock_guard<std::mutex> guard(c.mutex);
        c.observer[site ? site : "unknown"] += 1;
        ++c.observerTotal;
        (void)bytes;
    }
    startReporter();
}

inline void addZeroCopyCount(const char* name, uint64_t amount) {
    auto& c = counters();
    {
        std::lock_guard<std::mutex> guard(c.mutex);
        c.counts[name ? name : "unknown"] += amount;
    }
    startReporter();
}

inline void emitZeroCopyEvent(const char* name, ANativeWindow* window) {
    if (!traceZeroCopy())
        return;
    std::fprintf(
        stderr, "SHIRAZ_ZC_EVENT %s window=%p t_ns=%llu\n", name ? name : "unknown",
        static_cast<void*>(window),
        static_cast<unsigned long long>(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                            std::chrono::steady_clock::now().time_since_epoch())
                                            .count()));
}

inline void recordZeroCopySample(const char* name, double ms) {
    auto& c = counters();
    {
        std::lock_guard<std::mutex> guard(c.mutex);
        auto& v = c.samples[name ? name : "unknown"];
        if (v.size() < 200000)
            v.push_back(ms);
    }
    startReporter();
}

inline uint64_t cpuPixelReadsTotal() {
    auto& c = counters();
    std::lock_guard<std::mutex> guard(c.mutex);
    return c.readTotal;
}

// Selects an unpinned FREE slot (or older QUEUED slot when dropQueued is true),
// or signals allocation of a new slot up to maxSlots; returns -1 if all slots are busy.
template <typename QueueT>
inline int pickSlot(const QueueT* q, uint32_t maxSlots, int exclude, bool dropQueued,
                    bool* allocate) {
    *allocate = false;
    int best = -1;
    for (uint32_t i = 0; i < q->count; ++i) {
        if (int(i) == exclude)
            continue;
        const auto& s = q->slots[i];
        if (s.pins)
            continue;
        if (s.state == SLOT_FREE || (dropQueued && s.state == SLOT_QUEUED)) {
            best = int(i);
            if (s.state == SLOT_FREE)
                break;
        }
    }
    if (best >= 0)
        return best;
    if (q->count < maxSlots) {
        *allocate = true;
        return int(q->count);
    }
    return -1;
}

template <typename QueueT> inline int findNewestReadableSlot(const QueueT* q) {
    int newest = -1;
    for (uint32_t i = 0; i < q->count; ++i) {
        const auto& s = q->slots[i];
        if ((s.state == SLOT_QUEUED || s.state == SLOT_ACQUIRED) &&
            (newest < 0 || s.revision > q->slots[newest].revision)) {
            newest = int(i);
        }
    }
    return newest;
}

template <typename WindowT> inline int32_t checkProducer(WindowT* w, uint64_t producer) {
    auto* q = w->queue.get();
    if (!q || q->abandoned || !producer || q->producer != producer)
        return -EPIPE;
    return 0;
}

template <typename WindowT>
inline int32_t checkHeld(WindowT* w, uint32_t index, uint64_t generation) {
    auto* q = w->queue.get();
    if (index >= q->count || q->slots[index].generation != generation ||
        q->slots[index].state != SLOT_DEQUEUED)
        return -EINVAL;
    return 0;
}

template <typename WindowT, typename EnsureFn>
inline int32_t connectProducer(WindowT* w, uint64_t producer, EnsureFn&& ensureQueue) {
    if (!w || !producer)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    auto* q = ensureQueue(w);
    if (q->abandoned)
        return -EPIPE;
    if (q->producer && q->producer != producer)
        return -EBUSY;
    q->producer = producer;
    return 0;
}

template <typename WindowT> inline void disconnectProducer(WindowT* w, uint64_t producer) {
    if (!w)
        return;
    std::lock_guard<std::mutex> guard(w->mutex);
    auto* q = w->queue.get();
    if (!q || q->producer != producer)
        return;
    for (uint32_t i = 0; i < q->count; ++i) {
        if (q->slots[i].state == SLOT_DEQUEUED)
            q->slots[i].state = SLOT_FREE;
    }
    q->producer = 0;
    q->cv.notify_all();
}

template <typename WindowT>
inline int32_t cancelSlot(WindowT* w, uint64_t producer, uint32_t index) {
    if (!w)
        return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    auto* q = w->queue.get();
    if (!q || q->producer != producer || index >= q->count ||
        q->slots[index].state != SLOT_DEQUEUED)
        return -EINVAL;
    q->slots[index].state = SLOT_FREE;
    q->cv.notify_all();
    return 0;
}

template <typename WindowT> inline int32_t cpuFramesRequired(WindowT* w, const char** reason) {
    if (!w)
        return 0;
    std::lock_guard<std::mutex> guard(w->mutex);
    const char* why = nullptr;
    if (w->shared)
        why = "export";
    else if (w->frameSink)
        why = "frame-sink";
    else if (w->queue && w->queue->offscreen)
        why = "offscreen";
    if (reason)
        *reason = why;
    return why ? 1 : 0;
}

template <typename WindowT, typename EnsureFn>
inline void setOffscreen(WindowT* w, EnsureFn&& ensureQueue) {
    if (!w)
        return;
    std::lock_guard<std::mutex> guard(w->mutex);
    ensureQueue(w)->offscreen = true;
}

template <typename WindowT> inline const char* nodataReason(WindowT* w) {
    if (!w)
        return "nodata-nothing-queued";
    std::lock_guard<std::mutex> guard(w->mutex);
    return w->queue && w->queue->ever_queued ? "nodata-cpu-newer" : "nodata-nothing-queued";
}

template <typename WindowT, typename EnsureFn>
inline void abandon(WindowT* w, EnsureFn&& ensureQueue) {
    if (!w)
        return;
    std::lock_guard<std::mutex> guard(w->mutex);
    auto* q = ensureQueue(w);
    q->abandoned = true;
    q->cv.notify_all();
}

template <typename WindowT> inline int32_t abandoned(WindowT* w) {
    if (!w)
        return 1;
    std::lock_guard<std::mutex> guard(w->mutex);
    return w->queue && w->queue->abandoned ? 1 : 0;
}

inline void copyRgbaToArgb(const uint8_t* rgba, uint32_t* argb, uint64_t pixelCount) {
    for (uint64_t i = 0; i < pixelCount; ++i) {
        const auto* p = &rgba[i * 4];
        argb[i] = (uint32_t(p[3]) << 24) | (uint32_t(p[0]) << 16) | (uint32_t(p[1]) << 8) | p[2];
    }
}

} // namespace zc_common
} // namespace shiraz
