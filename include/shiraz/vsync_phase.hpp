// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>

namespace shiraz {

// Shared lock-free vsync phase-alignment tracker used by AChoreographer across
// macOS (fed by CoreVideo CVDisplayLink), Linux (fed by presenter/DRM/Wayland
// vsync timestamps), and Windows. All methods are safe to call from any thread.
struct VsyncPhaseTracker {
    static constexpr int64_t kDefaultPeriodNs = 16666667LL; // 60 Hz
    static constexpr int64_t kMinPeriodNs = 2000000LL;      // 500 Hz
    static constexpr int64_t kMaxPeriodNs = 50000000LL;     // 20 Hz
    static constexpr int64_t kFreshAnchorWindowNs = 250000000LL;

    std::atomic<int64_t> lastTickNs{0};
    std::atomic<int64_t> activePeriodNs{kDefaultPeriodNs};
    std::atomic<uint64_t> tickCount{0};

    explicit VsyncPhaseTracker(int64_t initialPeriodNs = kDefaultPeriodNs) {
        if (initialPeriodNs >= kMinPeriodNs && initialPeriodNs <= kMaxPeriodNs)
            activePeriodNs.store(initialPeriodNs, std::memory_order_relaxed);
    }

    void setRefreshRate(double hz) {
        if (hz >= 24.0 && hz <= 480.0) {
            const int64_t p = static_cast<int64_t>(std::llround(1000000000.0 / hz));
            if (p >= kMinPeriodNs && p <= kMaxPeriodNs)
                activePeriodNs.store(p, std::memory_order_relaxed);
        }
    }

    void setPeriodNs(int64_t periodNs) {
        if (periodNs >= kMinPeriodNs && periodNs <= kMaxPeriodNs)
            activePeriodNs.store(periodNs, std::memory_order_relaxed);
    }

    void noteVsync(int64_t monotonicNs, int64_t periodNs = 0) {
        if (periodNs >= kMinPeriodNs && periodNs <= kMaxPeriodNs)
            activePeriodNs.store(periodNs, std::memory_order_relaxed);
        if (monotonicNs > 0) {
            lastTickNs.store(monotonicNs, std::memory_order_release);
            tickCount.fetch_add(1, std::memory_order_relaxed);
        }
    }

    int64_t periodNs() const {
        return activePeriodNs.load(std::memory_order_relaxed);
    }

    int64_t nextDue(int64_t requested, int64_t current) const {
        const int64_t p = activePeriodNs.load(std::memory_order_relaxed);
        const int64_t anchor = lastTickNs.load(std::memory_order_acquire);
        if (anchor > 0 && current >= anchor && (current - anchor) < kFreshAnchorWindowNs) {
            const int64_t delta = requested - anchor;
            const int64_t steps = delta >= 0 ? (delta / p + 1) : 1;
            return anchor + steps * p;
        }
        return (requested / p + 1) * p;
    }
};

} // namespace shiraz
