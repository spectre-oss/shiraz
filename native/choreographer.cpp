// SPDX-License-Identifier: GPL-3.0-only
// Frame pacing for the host display. Callbacks run on the owning ALooper;
// macOS tracks CoreVideo CVDisplayLink vsync, and Linux tracks display refresh
// rate plus presenter vsync phase timestamps fed from presentation.cpp.
// Both platforms share shiraz::VsyncPhaseTracker (include/shiraz/vsync_phase.hpp).
#include "shiraz/vsync_phase.hpp"
#include <atomic>
#if defined(__APPLE__)
#include <dlfcn.h>
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>
#include <fcntl.h>
#include <time.h>
#else
#include <cstdlib>
#include <sys/timerfd.h>
#include <time.h>
#endif
#include <unistd.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <vector>

struct ALooper;
extern "C" {
ALooper* ALooper_forThread();
void ALooper_acquire(ALooper*);
void ALooper_release(ALooper*);
int ALooper_addFd(ALooper*, int, int, int, int (*)(int, int, void*), void*);
int ALooper_removeFd(ALooper*, int);
}
namespace {
#if defined(__APPLE__)
int64_t darwin_frame_period_ns() {
    void* cg = dlopen("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics",
                      RTLD_LAZY | RTLD_LOCAL);
    if (cg) {
        using MainDisplayFn = uint32_t (*)();
        using CopyModeFn = void* (*)(uint32_t);
        using GetRateFn = double (*)(void*);
        using ReleaseModeFn = void (*)(void*);
        auto* mainDisplay = reinterpret_cast<MainDisplayFn>(dlsym(cg, "CGMainDisplayID"));
        auto* copyMode = reinterpret_cast<CopyModeFn>(dlsym(cg, "CGDisplayCopyDisplayMode"));
        auto* getRate = reinterpret_cast<GetRateFn>(dlsym(cg, "CGDisplayModeGetRefreshRate"));
        auto* releaseMode = reinterpret_cast<ReleaseModeFn>(dlsym(cg, "CGDisplayModeRelease"));
        if (mainDisplay && copyMode && getRate && releaseMode) {
            void* mode = copyMode(mainDisplay());
            if (mode) {
                const double hz = getRate(mode);
                releaseMode(mode);
                if (hz >= 24.0 && hz <= 480.0)
                    return static_cast<int64_t>(1000000000.0 / hz);
            }
        }
    }
    return 16666667;
}
const int64_t period = darwin_frame_period_ns();

// Hardware vsync phase tracker via CoreVideo CVDisplayLink (loaded lazily via
// dlopen so headless/CI binaries require no CoreVideo link dependency).
struct DarwinVsyncTracker {
    shiraz::VsyncPhaseTracker phase{period};

    struct CVTimeLite {
        int64_t timeValue;
        int32_t timeScale;
        int32_t flags;
    };
    struct CVTimeStampLite {
        uint32_t version;
        int32_t videoTimeScale;
        int64_t videoTime;
        uint64_t hostTime;
        double rateScalar;
        int64_t videoRefreshPeriod;
        uint8_t smpteTime[24];
        uint64_t flags;
        uint64_t reserved;
    };
    using CallbackFn = int32_t (*)(void*, const CVTimeStampLite*, const CVTimeStampLite*, uint64_t,
                                   uint64_t*, void*);
    static int32_t onVsync(void*, const CVTimeStampLite*, const CVTimeStampLite* outputTime,
                           uint64_t, uint64_t*, void* userdata) {
        auto* self = static_cast<DarwinVsyncTracker*>(userdata);
        timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t tickNs = int64_t(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
        int64_t periodNs = 0;
        if (outputTime && outputTime->videoTimeScale > 0 && outputTime->videoRefreshPeriod > 0) {
            periodNs = (outputTime->videoRefreshPeriod * 1000000000LL) /
                       int64_t(outputTime->videoTimeScale);
        }
        self->phase.noteVsync(tickNs, periodNs);
        return 0;
    }

    DarwinVsyncTracker() {
        void* cv = dlopen("/System/Library/Frameworks/CoreVideo.framework/CoreVideo",
                          RTLD_LAZY | RTLD_LOCAL);
        if (!cv)
            return;
        using CreateFn = int32_t (*)(void**);
        using SetCbFn = int32_t (*)(void*, CallbackFn, void*);
        using StartFn = int32_t (*)(void*);
        using GetNominalFn = CVTimeLite (*)(void*);
        auto* createLink =
            reinterpret_cast<CreateFn>(dlsym(cv, "CVDisplayLinkCreateWithActiveCGDisplays"));
        auto* setCb = reinterpret_cast<SetCbFn>(dlsym(cv, "CVDisplayLinkSetOutputCallback"));
        auto* startLink = reinterpret_cast<StartFn>(dlsym(cv, "CVDisplayLinkStart"));
        auto* getNominal = reinterpret_cast<GetNominalFn>(
            dlsym(cv, "CVDisplayLinkGetNominalOutputVideoRefreshPeriod"));
        if (!createLink || !setCb || !startLink)
            return;
        void* link = nullptr;
        if (createLink(&link) != 0 || !link)
            return;
        if (getNominal) {
            CVTimeLite nom = getNominal(link);
            if (!(nom.flags & 1) && nom.timeScale > 0 && nom.timeValue > 0) {
                const int64_t p = (nom.timeValue * 1000000000LL) / int64_t(nom.timeScale);
                phase.setPeriodNs(p);
            }
        }
        if (setCb(link, &DarwinVsyncTracker::onVsync, this) == 0)
            startLink(link);
    }

    int64_t periodNs() const {
        return phase.periodNs();
    }

    int64_t nextDue(int64_t requested, int64_t current) const {
        return phase.nextDue(requested, current);
    }
};

DarwinVsyncTracker& darwin_vsync() {
    static DarwinVsyncTracker* tracker = new DarwinVsyncTracker;
    return *tracker;
}
#else
struct LinuxVsyncTracker {
    shiraz::VsyncPhaseTracker phase;

    LinuxVsyncTracker() {
        const char* envHz = std::getenv("SHIRAZ_REFRESH_HZ");
        if (envHz && *envHz) {
            char* end = nullptr;
            const double hz = std::strtod(envHz, &end);
            if (end != envHz)
                phase.setRefreshRate(hz);
        }
    }

    void setRefreshRate(float hz) {
        phase.setRefreshRate(static_cast<double>(hz));
    }

    void noteVsync(int64_t monotonicNs, int64_t periodNs) {
        phase.noteVsync(monotonicNs, periodNs);
    }

    int64_t periodNs() const {
        return phase.periodNs();
    }

    int64_t nextDue(int64_t requested, int64_t current) const {
        return phase.nextDue(requested, current);
    }
};

LinuxVsyncTracker& linux_vsync() {
    static LinuxVsyncTracker* tracker = new LinuxVsyncTracker;
    return *tracker;
}
#endif
int64_t now() {
    timespec value{};
    clock_gettime(CLOCK_MONOTONIC, &value);
    return int64_t(value.tv_sec) * 1000000000 + value.tv_nsec;
}
#if defined(__APPLE__)
// timerfd replacement: a private kqueue holding one EVFILT_TIMER knote. A kqueue
// fd is readable while it has pending events, so it is watched through the
// public ALooper_addFd API exactly like the Linux timerfd.
int timer_create_fd() {
    const int fd = kqueue();
    if (fd >= 0 && fcntl(fd, F_SETFD, FD_CLOEXEC)) {
        close(fd);
        return -1;
    }
    return fd;
}
#endif
} // namespace
struct AChoreographer {
    struct Pending {
        int64_t due;
        void (*wide)(int64_t, void*);
        void (*legacy)(long, void*);
        void* data;
    };
    ALooper* looper;
    int timer;
    std::mutex mutex;
    std::vector<Pending> pending;
#if defined(__APPLE__)
    explicit AChoreographer(ALooper* owner) : looper(owner), timer(timer_create_fd()) {
        ALooper_acquire(looper);
    }
#else
    explicit AChoreographer(ALooper* owner)
        : looper(owner), timer(timerfd_create(CLOCK_MONOTONIC, TFD_CLOEXEC | TFD_NONBLOCK)) {
        ALooper_acquire(looper);
    }
#endif
    ~AChoreographer() {
        if (timer >= 0) {
            ALooper_removeFd(looper, timer);
            close(timer);
        }
        ALooper_release(looper);
    }
#if defined(__APPLE__)
    // One-shot relative timer to the earliest due time; a past due fires at once.
    // Relative nanoseconds keep CLOCK_MONOTONIC as the only clock (NOTE_ABSOLUTE
    // would use the wall clock or mach time instead).
    void arm() {
        struct kevent change;
        if (pending.empty())
            EV_SET(&change, 0, EVFILT_TIMER, EV_DELETE, 0, 0, nullptr);
        else {
            int64_t due =
                std::min_element(pending.begin(), pending.end(),
                                 [](const Pending& a, const Pending& b) { return a.due < b.due; })
                    ->due;
            const int64_t delay = std::max<int64_t>(0, due - now());
            EV_SET(&change, 0, EVFILT_TIMER, EV_ADD | EV_ONESHOT, NOTE_NSECONDS | NOTE_CRITICAL,
                   delay, nullptr);
        }
        kevent(timer, &change, 1, nullptr, 0, nullptr);
    }
#else
    void arm() {
        itimerspec value{};
        if (!pending.empty()) {
            int64_t due =
                std::min_element(pending.begin(), pending.end(),
                                 [](const Pending& a, const Pending& b) { return a.due < b.due; })
                    ->due;
            value.it_value.tv_sec = due / 1000000000;
            value.it_value.tv_nsec = due % 1000000000;
        }
        timerfd_settime(timer, TFD_TIMER_ABSTIME, &value, nullptr);
    }
#endif
    static int dispatch(int fd, int, void* data) {
        auto& self = *static_cast<AChoreographer*>(data);
#if defined(__APPLE__)
        struct kevent fired;
        const timespec immediate{};
        if (kevent(fd, nullptr, 0, &fired, 1, &immediate) != 1 || fired.filter != EVFILT_TIMER)
            return 1;
#else
        uint64_t expirations;
        if (read(fd, &expirations, sizeof(expirations)) != sizeof(expirations))
            return 1;
#endif
        const int64_t frame = now();
        std::vector<Pending> ready;
        {
            std::lock_guard<std::mutex> lock(self.mutex);
            auto it = self.pending.begin();
            while (it != self.pending.end()) {
                if (it->due <= frame) {
                    ready.push_back(*it);
                    it = self.pending.erase(it);
                } else
                    ++it;
            }
            self.arm();
        }
        // Callbacks may post again; never hold the queue mutex while invoking app code.
        for (const auto& item : ready) {
            if (item.wide)
                item.wide(frame, item.data);
            else
                item.legacy(static_cast<long>(frame), item.data);
        }
        return 1;
    }
    void post(void (*wide)(int64_t, void*), void (*legacy)(long, void*), void* data,
              uint64_t delay) {
        if (!wide && !legacy)
            return;
        const int64_t current = now();
#if defined(__APPLE__)
        const int64_t framePeriod = darwin_vsync().periodNs();
#else
        const int64_t framePeriod = linux_vsync().periodNs();
#endif
        const uint64_t maximum =
            (uint64_t(std::numeric_limits<int64_t>::max()) - current - framePeriod) / 1000000;
        const int64_t requested = current + int64_t(std::min(delay, maximum)) * 1000000;
#if defined(__APPLE__)
        const int64_t due = darwin_vsync().nextDue(requested, current);
#else
        const int64_t due = linux_vsync().nextDue(requested, current);
#endif
        std::lock_guard<std::mutex> lock(mutex);
        pending.push_back({due, wide, legacy, data});
        arm();
    }
};
namespace {
thread_local std::unique_ptr<AChoreographer> current;
}
extern "C" AChoreographer* AChoreographer_getInstance() {
    if (current)
        return current.get();
    ALooper* looper = ALooper_forThread();
    if (!looper)
        return nullptr;
    auto value = std::make_unique<AChoreographer>(looper);
    if (value->timer < 0 ||
        ALooper_addFd(looper, value->timer, 0, 1, AChoreographer::dispatch, value.get()) != 1)
        return nullptr;
    current = std::move(value);
    return current.get();
}
extern "C" void AChoreographer_postFrameCallback(AChoreographer* c, void (*callback)(long, void*),
                                                 void* data) {
    if (c)
        c->post(nullptr, callback, data, 0);
}
extern "C" void AChoreographer_postFrameCallback64(AChoreographer* c,
                                                   void (*callback)(int64_t, void*), void* data) {
    if (c)
        c->post(callback, nullptr, data, 0);
}
extern "C" void AChoreographer_postFrameCallbackDelayed(AChoreographer* c,
                                                        void (*callback)(long, void*), void* data,
                                                        long delay) {
    if (c)
        c->post(nullptr, callback, data, delay > 0 ? uint64_t(delay) : 0);
}
extern "C" void AChoreographer_postFrameCallbackDelayed64(AChoreographer* c,
                                                          void (*callback)(int64_t, void*),
                                                          void* data, uint32_t delay) {
    if (c)
        c->post(callback, nullptr, data, delay);
}
#if !defined(__APPLE__)
extern "C" void shiraz_choreographer_set_refresh_rate(float hz) {
    linux_vsync().setRefreshRate(hz);
}
extern "C" void shiraz_choreographer_note_vsync(int64_t monotonic_ns, int64_t period_ns) {
    linux_vsync().noteVsync(monotonic_ns, period_ns);
}
extern "C" int64_t shiraz_choreographer_period_ns(void) {
    return linux_vsync().periodNs();
}
#endif
