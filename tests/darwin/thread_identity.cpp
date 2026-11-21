// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/thread_identity.hpp"
#include <algorithm>
#include <atomic>
#include <cassert>
#include <cerrno>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <pthread.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {
volatile sig_atomic_t observed_tid = 0;
void handler(int) {
    int tid = 0;
    if (shiraz_android_tid_current(&tid) == 0)
        observed_tid = tid;
}
} // namespace

int main() {
    // Loading the identity image establishes its owner even before the first call.
    pid_t early_child = fork();
    assert(early_child >= 0);
    if (!early_child) {
        int value = 91;
        _exit(shiraz_android_tid_current(&value) == ECHILD && value == 91 ? 0 : 1);
    }
    int early_status = 0;
    assert(waitpid(early_child, &early_status, 0) == early_child && WIFEXITED(early_status) &&
           !WEXITSTATUS(early_status));
    int self = 0;
    assert(shiraz_android_tid_current(&self) == 0 && self > 0 && self < (1 << 22));
    assert(self == getpid());
    uint64_t native = 0, original = 0;
    assert(!pthread_threadid_np(nullptr, &original));
    assert(!shiraz_android_tid_to_native(self, &native) && native == original);
    int again = 0;
    assert(!shiraz_android_tid_for_native(original, &again) && again == self);
    struct sigaction action{};
    action.sa_handler = handler;
    sigemptyset(&action.sa_mask);
    assert(!sigaction(SIGUSR1, &action, nullptr));
    assert(!raise(SIGUSR1) && observed_tid == self);
    observed_tid = 0;
    std::atomic<bool> first_signal_ok{false};
    std::thread first_signal([&] {
        if (raise(SIGUSR1))
            return;
        int first = 0;
        first_signal_ok = !shiraz_android_tid_current(&first) && first == observed_tid;
    });
    first_signal.join();
    assert(first_signal_ok);

    constexpr int n = 96;
    std::atomic<int> ready{0};
    std::atomic<bool> release{false};
    std::vector<int> tids(n);
    std::vector<uint64_t> natives(n);
    std::vector<std::thread> threads;
    for (int i = 0; i < n; ++i)
        threads.emplace_back([&, i] {
            assert(!shiraz_android_tid_current(&tids[i]));
            assert(!pthread_threadid_np(nullptr, &natives[i]));
            ++ready;
            while (!release.load(std::memory_order_acquire))
                std::this_thread::yield();
            int repeat = 0;
            assert(!shiraz_android_tid_current(&repeat) && repeat == tids[i]);
        });
    while (ready.load() != n)
        std::this_thread::yield();
    for (int i = 0; i < n; ++i) {
        assert(tids[i] > 0 && tids[i] < (1 << 22) && tids[i] != self && tids[i] != getpid());
        assert(!shiraz_android_tid_to_native(tids[i], &native) && native == natives[i]);
        for (int j = 0; j < i; ++j)
            assert(tids[j] != tids[i]);
    }
    release.store(true, std::memory_order_release);
    for (auto& thread : threads)
        thread.join();
    // IDs are never recycled, including after a thread has exited.
    for (int i = 0; i < n; ++i) {
        assert(!shiraz_android_tid_to_native(tids[i], &native) && native == natives[i]);
    }
    native = 123;
    assert(shiraz_android_tid_to_native(0, &native) == EINVAL && native == 123);
    assert(!shiraz_android_tid_to_native(getpid(), &native) && native == original);
    assert(shiraz_android_tid_for_native(0, &again) == EINVAL);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        int value = 91;
        _exit(shiraz_android_tid_current(&value) == ECHILD && value == 91 ? 0 : 1);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && !WEXITSTATUS(status));
    std::puts("DARWIN_THREAD_IDENTITY_PASS threads=96 unique=1 reverse=1 signal=1 fork=closed");
}
