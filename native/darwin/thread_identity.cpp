// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/thread_identity.hpp"
#include <atomic>
#include <cerrno>
#include <pthread.h>
#include <unistd.h>

namespace {
// Android's kernel limits its PID namespace to 22 bits. The table reserves
// virtual address space, commits pages on demand, and never reuses a slot:
// a stale Android TID can therefore never address a different host thread.
// Darwin's system-wide thread IDs are never reused. An exhausted table fails
// explicitly instead of aliasing another thread.
#ifdef SHIRAZ_TID_FIXTURE_BITS
constexpr uint32_t kSlots = 1u << SHIRAZ_TID_FIXTURE_BITS;
#else
constexpr uint32_t kSlots = 1u << 22;
#endif
static_assert(std::atomic<uint64_t>::is_always_lock_free,
              "thread identity requires lock-free atomics");
std::atomic<uint64_t> slots[kSlots]{};
std::atomic<int> owner_pid{0};

int check_process(int* pid_out) noexcept {
    int pid = int(getpid());
#ifdef SHIRAZ_TID_FIXTURE_PID
    pid = SHIRAZ_TID_FIXTURE_PID;
#endif
    if (pid <= 0 || uint32_t(pid) >= kSlots)
        return EOVERFLOW;
    int expected = 0;
    owner_pid.compare_exchange_strong(expected, pid, std::memory_order_relaxed);
    if (owner_pid.load(std::memory_order_relaxed) != pid)
        return ECHILD;
    *pid_out = pid;
    return 0;
}

uint32_t first_slot(uint64_t id) noexcept {
    id ^= id >> 33;
    id *= UINT64_C(0xff51afd7ed558ccd);
    id ^= id >> 33;
    id *= UINT64_C(0xc4ceb9fe1a85ec53);
    id ^= id >> 33;
    return uint32_t(id) & (kSlots - 1);
}

// When loaded on the process leader, reserve its Android identity before any
// caller can publish a different mapping. A library loaded on a worker cannot
// safely discover the leader's pthread ID; it retains the existing mapping.
__attribute__((constructor)) void reserve_main_thread() noexcept {
#ifndef SHIRAZ_TID_FIXTURE_BITS
    if (!pthread_main_np())
        return;
    int pid;
    uint64_t native = 0;
    if (check_process(&pid) || pthread_threadid_np(nullptr, &native) || !native)
        return;
    slots[uint32_t(pid)].store(native, std::memory_order_relaxed);
#endif
}
} // namespace

extern "C" int shiraz_android_tid_for_native(uint64_t native, int* result) noexcept {
    if (!native || !result)
        return EINVAL;
    int pid;
    if (int error = check_process(&pid))
        return error;
    if (slots[uint32_t(pid)].load(std::memory_order_relaxed) == native) {
        *result = pid;
        return 0;
    }
    const uint32_t first = first_slot(native);
    for (uint32_t probe = 0; probe < kSlots; ++probe) {
        uint32_t index = (first + probe) & (kSlots - 1);
        // Zero is invalid and the process ID is reserved for the leader.
        if (!index || index == uint32_t(pid))
            continue;
        uint64_t found = slots[index].load(std::memory_order_relaxed);
        if (found == native) {
            *result = int(index);
            return 0;
        }
        if (!found &&
            slots[index].compare_exchange_strong(found, native, std::memory_order_relaxed)) {
            *result = int(index);
            return 0;
        }
        if (found == native) {
            *result = int(index);
            return 0;
        }
    }
    return EAGAIN;
}

extern "C" int shiraz_android_tid_current(int* result) noexcept {
    if (!result)
        return EINVAL;
    uint64_t native = 0;
    int error = pthread_threadid_np(nullptr, &native);
    if (error)
        return error;
    if (!native)
        return EOVERFLOW;
    return shiraz_android_tid_for_native(native, result);
}

extern "C" int shiraz_android_tid_to_native(int tid, uint64_t* result) noexcept {
    if (!result || tid <= 0 || uint32_t(tid) >= kSlots)
        return EINVAL;
    int pid;
    if (int error = check_process(&pid))
        return error;
    uint64_t found = slots[uint32_t(tid)].load(std::memory_order_relaxed);
    if (!found)
        return ESRCH;
    *result = found;
    return 0;
}
