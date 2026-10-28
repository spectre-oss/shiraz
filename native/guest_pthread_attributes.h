// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <pthread.h>
#include <unistd.h>
#include <algorithm>
#include <cerrno>
#include <cstddef>
#include <cstdint>

// LP64 Bionic layout from the pinned libc/include/bits/pthread_types.h.
// Never pass these bytes to a glibc pthread_attr_* function.
struct ShirazGuestPthreadAttributes {
  uint32_t flags;
  void* stack_base;
  size_t stack_size;
  size_t guard_size;
  int32_t sched_policy;
  int32_t sched_priority;
  char reserved[16];
};
static_assert(sizeof(ShirazGuestPthreadAttributes) == 56);
static_assert(offsetof(ShirazGuestPthreadAttributes, stack_base) == 8);
static_assert(offsetof(ShirazGuestPthreadAttributes, sched_policy) == 32);

class ShirazHostPthreadAttributes {
 public:
  explicit ShirazHostPthreadAttributes(const ShirazGuestPthreadAttributes* guest) {
    error = pthread_attr_init(&host);
    initialized = error == 0;
    if (error) return;
    const long page = sysconf(_SC_PAGESIZE);
    guard_size = page > 0 ? static_cast<size_t>(page) : 4096;
    if (guest) {
      // Joined is internal state, not an application creation attribute.
      if ((guest->flags & ~13u) || (guest->flags & 12u) == 12u) { error = EINVAL; return; }
      stack = guest->stack_base; stack_size = guest->stack_size; guard_size = guest->guard_size;
      if (stack_size < static_cast<size_t>(PTHREAD_STACK_MIN) ||
          (stack && (reinterpret_cast<uintptr_t>(stack) % guard_page() || stack_size % guard_page()))) {
        error = EINVAL; return;
      }
      error = pthread_attr_setdetachstate(&host, (guest->flags & 1u) ? PTHREAD_CREATE_DETACHED : PTHREAD_CREATE_JOINABLE);
      const bool explicit_schedule = (guest->flags & 8u) ||
          (!(guest->flags & 4u) && guest->sched_policy != SCHED_OTHER);
      if (!error && explicit_schedule) {
        error = pthread_attr_setinheritsched(&host, PTHREAD_EXPLICIT_SCHED);
        if (!error) error = pthread_attr_setschedpolicy(&host, guest->sched_policy);
        sched_param parameter{}; parameter.sched_priority = guest->sched_priority;
        if (!error) error = pthread_attr_setschedparam(&host, &parameter);
      }
    }
    size_t host_size = 0;
    if (!error) error = pthread_attr_getstacksize(&host, &host_size);
    if (!error) error = pthread_attr_setstacksize(&host, std::max(host_size, stack_size));
    if (!error) error = pthread_attr_setguardsize(&host, guard_size);
  }
  ~ShirazHostPthreadAttributes() { if (initialized) pthread_attr_destroy(&host); }
  ShirazHostPthreadAttributes(const ShirazHostPthreadAttributes&) = delete;
  ShirazHostPthreadAttributes& operator=(const ShirazHostPthreadAttributes&) = delete;
  pthread_attr_t host{};
  void* stack = nullptr;
  size_t stack_size = 1024*1024;
  size_t guard_size = 0;
  int error = 0;
 private:
  static size_t guard_page() { long page=sysconf(_SC_PAGESIZE); return page>0?static_cast<size_t>(page):4096; }
  bool initialized = false;
};
