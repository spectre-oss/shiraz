// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <thread>
#include <time.h>
struct ALooper;
struct AChoreographer;
extern "C" {
ALooper* ALooper_prepare(int);
int ALooper_pollOnce(int, int*, int*, void**);
AChoreographer* AChoreographer_getInstance();
void AChoreographer_postFrameCallback64(AChoreographer*, void (*)(int64_t, void*), void*);
void AChoreographer_postFrameCallbackDelayed64(AChoreographer*, void (*)(int64_t, void*), void*,
                                               uint32_t);
void AChoreographer_postFrameCallback(AChoreographer*, void (*)(long, void*), void*);
#if !defined(__APPLE__)
void shiraz_choreographer_set_refresh_rate(float hz);
void shiraz_choreographer_note_vsync(int64_t monotonic_ns, int64_t period_ns);
int64_t shiraz_choreographer_period_ns(void);
#endif
}
struct State {
    int calls = 0;
    int64_t stamp = 0;
    std::thread::id owner = std::this_thread::get_id();
    AChoreographer* choreographer = nullptr;
};
void callback(int64_t stamp, void* data) {
    auto& state = *static_cast<State*>(data);
    assert(std::this_thread::get_id() == state.owner);
    assert(stamp > state.stamp);
    state.stamp = stamp;
    if (++state.calls == 1 && state.choreographer)
        AChoreographer_postFrameCallback64(state.choreographer, callback, data);
}
void legacy(long stamp, void* data) {
    callback(stamp, data);
}
void wait(State& state, int count) {
    auto end = std::chrono::steady_clock::now() + std::chrono::seconds(2);
    while (state.calls < count && std::chrono::steady_clock::now() < end)
        ALooper_pollOnce(100, nullptr, nullptr, nullptr);
    assert(state.calls == count);
}
int main() {
    assert(!AChoreographer_getInstance());
    assert(ALooper_prepare(0));
    auto* choreographer = AChoreographer_getInstance();
    assert(choreographer && choreographer == AChoreographer_getInstance());
    State repeat;
    repeat.choreographer = choreographer;
    AChoreographer_postFrameCallback64(choreographer, callback, &repeat);
    assert(repeat.calls == 0);
    wait(repeat, 2);
    State delayed;
    auto start = std::chrono::steady_clock::now();
    std::thread producer(
        [&] { AChoreographer_postFrameCallbackDelayed64(choreographer, callback, &delayed, 60); });
    producer.join();
    wait(delayed, 1);
    assert(std::chrono::steady_clock::now() - start >= std::chrono::milliseconds(60));
    State old;
    AChoreographer_postFrameCallback(choreographer, legacy, &old);
    wait(old, 1);
    ALooper_pollOnce(30, nullptr, nullptr, nullptr);
    assert(old.calls == 1 && repeat.calls == 2 && delayed.calls == 1);
    // Thread teardown cancels pending work and releases its timer and looper.
    for (int i = 0; i < 32; i++)
        std::thread([] {
            ALooper_prepare(0);
            AChoreographer_postFrameCallbackDelayed64(AChoreographer_getInstance(), callback,
                                                      nullptr, 10000);
        }).join();
#if !defined(__APPLE__)
    // Dynamic display refresh rate + presenter vsync phase alignment on Linux.
    shiraz_choreographer_set_refresh_rate(120.0f);
    assert(shiraz_choreographer_period_ns() == 8333333LL);
    // Out-of-range refresh rates must be ignored.
    shiraz_choreographer_set_refresh_rate(0.0f);
    shiraz_choreographer_set_refresh_rate(1000.0f);
    assert(shiraz_choreographer_period_ns() == 8333333LL);
    {
        timespec ts{};
        assert(clock_gettime(CLOCK_MONOTONIC, &ts) == 0);
        const int64_t anchor = int64_t(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
        shiraz_choreographer_note_vsync(anchor, 8333333LL);
        State paced;
        AChoreographer_postFrameCallback64(choreographer, callback, &paced);
        wait(paced, 1);
        assert(paced.stamp >= anchor + 8333333LL);
    }
    shiraz_choreographer_set_refresh_rate(60.0f);
    assert(shiraz_choreographer_period_ns() == 16666667LL);
#endif
    std::puts("NDK_CHOREOGRAPHER_OK");
}
