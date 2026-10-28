// SPDX-License-Identifier: GPL-3.0-only
// Experimental AOSP16 guest TLS storage for the pinned Digitalis guest bundle.
#include <cstdint>
#include <cstdlib>
#include <cstdio>
#include "guest_stack_environment.h"
#include <cstring>
#include <unistd.h>
#include <sys/syscall.h>
namespace berberis {
struct GuestBionicThreadStorage {
 alignas(16) unsigned char pthread_record[4096]{};
 alignas(16) unsigned char tls_record[32768]{};
 GuestBionicThreadStorage() {
  const int tid=static_cast<int>(syscall(SYS_gettid));
  const int pid=getpid();
  std::memcpy(pthread_record+16,&tid,sizeof(tid));
  std::memcpy(pthread_record+20,&pid,sizeof(pid));
  void* ptr=tls_record;
  // bionic_tls member offset verified from the pinned linker's init routine.
  std::memcpy(pthread_record+760,&ptr,sizeof(ptr));
 }
};
thread_local GuestBionicThreadStorage storage;
void ShirazRefreshGuestThreadIdentity() {
 const int tid=static_cast<int>(syscall(SYS_gettid));
 const int pid=getpid();
 std::memcpy(storage.pthread_record+16,&tid,sizeof(tid));
 std::memcpy(storage.pthread_record+20,&pid,sizeof(pid));
}
void* ShirazGuestPthread(){return storage.pthread_record;}
void* ShirazGuestBionicTls(){return storage.tls_record;}
}

namespace berberis {
unsigned long ShirazAttachedGuestStack(unsigned long top, size_t stack_size) {
  const char* enabled = std::getenv("SHIRAZ_GUEST_STACK_ENVIRONMENT");
  if (!enabled || std::strcmp(enabled, "1")) return top;
  size_t capacity = stack_size / 4;
  if (capacity > 65536) capacity = 65536;
  capacity &= ~size_t(15);
  auto result = shiraz_stack_environment(reinterpret_cast<void*>(top - capacity),
      capacity, environ);
  if (!result) {
    std::fprintf(stderr, "Shiraz: environment exceeds guest stack reservation\n");
    std::abort();
  }
  return result;
}
}
