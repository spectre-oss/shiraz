// SPDX-License-Identifier: GPL-3.0-only
// Original implementation, behavior/ABI checked against android-14.0.0_r1:
// libc/bionic/{__cxa_guard.cpp,__cxa_pure_virtual.cpp,new.cpp} and
// libc/libstdc++.map.txt. No upstream implementation is vendored here.
#include "shiraz/darwin/guest/cxx.hpp"
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <mutex>
#include <thread>

namespace shiraz::guest {
namespace {
[[noreturn]] void fatal(const char *reason) {
  std::fprintf(stderr, "SHIRAZ_GUEST_FATAL bionic libstdc++: %s\n", reason);
  std::abort();
}
// AArch64 uses an eight-byte guard. Its first 32 bits contain bionic's
// state; the other 32 bits are left untouched. Inline guest checks test the
// initialized bit in the first byte, so native mutex storage never goes here.
constexpr uint32_t kNew = 0, kDone = 1, kBusy = 0x100, kWaiters = 0x200;
uint32_t *word(void *guard) {
  if (!guard || reinterpret_cast<uintptr_t>(guard) % 8) fatal("invalid guard address/alignment");
  return static_cast<uint32_t *>(guard);
}
uint32_t load(uint32_t *guard) { return __atomic_load_n(guard, __ATOMIC_ACQUIRE); }
class Guards {
  struct Pending {
    std::thread::id owner;
    std::condition_variable ready;
    bool finished = false;
  };
  std::mutex mutex_;
  std::map<void *, std::shared_ptr<Pending>> pending_;
public:
  int acquire(void *address) {
    auto *guard = word(address);
    for (;;) {
      if (load(guard) == kDone) return 0;
      std::unique_lock<std::mutex> lock(mutex_);
      uint32_t value = load(guard);
      if (value == kDone) return 0;
      if (value == kNew) {
        if (pending_.count(address)) fatal("inconsistent guard ownership");
        if (pending_.size() >= 16384) fatal("concurrent guard limit");
        auto pending = std::make_shared<Pending>();
        pending->owner = std::this_thread::get_id();
        pending_.emplace(address, std::move(pending));
        __atomic_store_n(guard, kBusy, __ATOMIC_RELEASE);
        return 1;
      }
      if (value != kBusy && value != kWaiters) fatal("invalid guard state");
      auto found = pending_.find(address);
      if (found == pending_.end()) fatal("guard initialization belongs to another runtime");
      auto pending = found->second;
      if (pending->owner == std::this_thread::get_id()) fatal("recursive local-static initialization");
      if (value == kBusy) __atomic_store_n(guard, kWaiters, __ATOMIC_RELEASE);
      // Wait on this initialization attempt, not the guest word: after abort
      // another owner can already begin a fresh attempt before we wake.
      pending->ready.wait(lock, [&] { return pending->finished; });
    }
  }
  void finish(void *address, bool complete) {
    auto *guard = word(address);
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = pending_.find(address);
    if (found == pending_.end() || found->second->owner != std::this_thread::get_id())
      fatal("guard release/abort without matching owner");
    uint32_t value = load(guard);
    if (value != kBusy && value != kWaiters) fatal("corrupted pending guard state");
    auto pending = found->second;
    __atomic_store_n(guard, complete ? kDone : kNew, __ATOMIC_RELEASE);
    pending->finished = true;
    pending_.erase(found);
    pending->ready.notify_all();
  }
};
void *allocate(size_t size) {
  void *result = std::malloc(size);
  if (!result) fatal("ordinary new allocation failed");
  return result;
}
void *allocate_nothrow(size_t size, const void *) { return std::malloc(size); }
void deallocate(void *pointer) { release_guest_allocation(pointer); }
void deallocate_nothrow(void *pointer, const void *) { release_guest_allocation(pointer); }
[[noreturn]] void pure_virtual() { fatal("pure virtual function called"); }
const unsigned char nothrow_object = 0;
} // namespace
Provider cxx_provider() {
  auto guards = std::make_shared<Guards>();
  Provider provider{"libstdc++.so", {"LIBC_O"}, {}, false};
  auto guard = [&](const char *name, int operation) {
    void *entry = closure([guards, operation](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      auto *address = cursor.read<void *>();
      HostErrno preserve;
      if (!operation) return_value(capture, guards->acquire(address));
      else guards->finish(address, operation == 1);
    });
    provider.exports.push_back({name, "LIBC_O", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
                                "bionic-guard/low32-state-native-wait", false});
  };
  guard("__cxa_guard_acquire", 0);
  guard("__cxa_guard_release", 1);
  guard("__cxa_guard_abort", 2);
  provider.exports.push_back(fixed_export("__cxa_pure_virtual", "LIBC_O", &pure_virtual));
  for (const char *name : {"_Znwm", "_Znam"})
    provider.exports.push_back(fixed_export(name, "LIBC_O", &allocate));
  for (const char *name : {"_ZnwmRKSt9nothrow_t", "_ZnamRKSt9nothrow_t"})
    provider.exports.push_back(fixed_export(name, "LIBC_O", &allocate_nothrow));
  for (const char *name : {"_ZdlPv", "_ZdaPv"})
    provider.exports.push_back(fixed_export(name, "LIBC_O", &deallocate));
  for (const char *name : {"_ZdlPvRKSt9nothrow_t", "_ZdaPvRKSt9nothrow_t"})
    provider.exports.push_back(fixed_export(name, "LIBC_O", &deallocate_nothrow));
  provider.exports.push_back({"_ZSt7nothrow", "LIBC_O", reinterpret_cast<uintptr_t>(&nothrow_object),
                              SymbolKind::Object, "bionic-empty-nothrow-tag", false});
  return provider;
}
} // namespace shiraz::guest
