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

// Android LP64 attribute API values are not glibc enum values (notably
// explicit/inherited scheduling). Operate only on guest storage here.
// Stack alignment/guard defaults match Shiraz's current 4 KiB guest profile.
namespace shiraz_guest_attr {
using Attr = ShirazGuestPthreadAttributes;
inline int init(Attr* a) { *a={}; a->stack_size=1024*1024-32*1024; a->guard_size=4096; return 0; }
inline int destroy(Attr* a) { *a={}; return 0; }
inline int getdetachstate(const Attr* a,int* v) { *v=a->flags&1u; return 0; }
inline int setdetachstate(Attr* a,int v) { if(v!=0&&v!=1)return EINVAL; a->flags=(a->flags&~1u)|v; return 0; }
inline int getguardsize(const Attr* a,size_t* v) { *v=a->guard_size; return 0; }
inline int setguardsize(Attr* a,size_t v) { a->guard_size=v; return 0; }
inline int getinheritsched(const Attr* a,int* v) { *v=(a->flags&4u)?1:((a->flags&8u)?0:(a->sched_policy==0?1:0)); return 0; }
inline int setinheritsched(Attr* a,int v) { if(v!=0&&v!=1)return EINVAL; a->flags=(a->flags&~12u)|(v?4u:8u); return 0; }
inline int getschedpolicy(const Attr* a,int* v) { *v=a->sched_policy; return 0; }
inline int setschedpolicy(Attr* a,int v) { a->sched_policy=v; return 0; }
inline int getschedparam(const Attr* a,int* priority) { *priority=a->sched_priority; return 0; }
inline int setschedparam(Attr* a,const int* priority) { a->sched_priority=*priority; return 0; }
inline int getscope(const Attr*,int* v) { *v=0; return 0; }
inline int setscope(Attr*,int v) { return v==0?0:(v==1?ENOTSUP:EINVAL); }
inline int getstack(const Attr* a,void** base,size_t* size) { *base=a->stack_base; *size=a->stack_size; return 0; }
inline int getstacksize(const Attr* a,size_t* v) { *v=a->stack_size; return 0; }
inline int setstacksize(Attr* a,size_t v) { if(v<16384)return EINVAL; a->stack_size=v; return 0; }
inline int setstack(Attr* a,void* base,size_t size) {
  if(size<16384 || size%4096 || reinterpret_cast<uintptr_t>(base)%4096)return EINVAL;
  a->stack_base=base; a->stack_size=size; return 0;
}
}  // namespace shiraz_guest_attr

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
