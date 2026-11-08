// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/sync.hpp"
#include "guest_io.hpp"
#include <array>
#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <mutex>
#include <semaphore.h>
#include <unistd.h>
#include <time.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr size_t slot_count = 512;
constexpr uint32_t index_mask = slot_count - 1;
static_assert(std::atomic<uint32_t>::is_always_lock_free &&
              std::atomic<bool>::is_always_lock_free);
static_assert(b::kSemaphoreSize == 16 && b::kSemaphoreAlign >= 4);
struct Slot {
  std::atomic<uint32_t> token{0}, count{0}, waiters{0};
  std::atomic<bool> notified{false};
  uint32_t generation = 0;
  void *address = nullptr;
  sem_t *wake = SEM_FAILED;
  ~Slot() { if (wake != SEM_FAILED) ::sem_close(wake); }
};
class Semaphores {
  std::array<Slot, slot_count> slots_;
  std::mutex registry_;
  Slot *get(void *address) {
    if (!address || reinterpret_cast<uintptr_t>(address) % 4) return nullptr;
    uint32_t token = __atomic_load_n(static_cast<uint32_t *>(address), __ATOMIC_ACQUIRE);
    if (!(token >> 9)) return nullptr;
    auto &slot = slots_[token & index_mask];
    return slot.token.load(std::memory_order_acquire) == token ? &slot : nullptr;
  }
  static bool take(Slot &slot) {
    uint32_t value = slot.count.load(std::memory_order_relaxed);
    while (value)
      if (slot.count.compare_exchange_weak(value, value - 1,
              std::memory_order_acquire, std::memory_order_relaxed)) return true;
    return false;
  }
  static void notify(Slot &slot) {
    if (slot.waiters.load(std::memory_order_acquire) &&
        !slot.notified.exchange(true, std::memory_order_acq_rel)) {
      // Only one kernel wake token can be outstanding. An unexpected kernel
      // failure must never be reported as a successful guest post.
      if (::sem_post(slot.wake)) std::abort();
    }
  }
public:
  int init(void *address, int shared, uint32_t value) {
    if (!address || reinterpret_cast<uintptr_t>(address) % 4) return EINVAL;
    if (shared) return EOPNOTSUPP;
    if (value > uint32_t(b::kSEM_VALUE_MAX)) return EINVAL;
    std::lock_guard<std::mutex> lock(registry_);
    Slot *available = nullptr;
    size_t index = 0;
    for (size_t i = 0; i < slot_count; ++i) {
      auto &slot = slots_[i];
      if (slot.address == address) return EBUSY;
      if (!available && !slot.address && slot.generation < (UINT32_MAX >> 9)) {
        available = &slot; index = i;
      }
    }
    if (!available) return ENOSPC;
    sem_t *wake = SEM_FAILED;
    for (int attempt = 0; attempt < 8; ++attempt) {
      uint64_t random;
      arc4random_buf(&random, sizeof(random));
      char name[32];
      std::snprintf(name, sizeof(name), "/sz%08x%016llx", unsigned(getpid()),
                    static_cast<unsigned long long>(random));
      wake = ::sem_open(name, O_CREAT | O_EXCL, 0600, 0);
      if (wake == SEM_FAILED) {
        if (errno == EEXIST) continue;
        return errno;
      }
      if (::sem_unlink(name)) {
        int error = errno;
        ::sem_close(wake);
        return error;
      }
      break;
    }
    if (wake == SEM_FAILED) return EAGAIN;
    auto &slot = *available;
    uint32_t token = (++slot.generation << 9) | uint32_t(index);
    slot.wake = wake;
    slot.address = address;
    slot.count.store(value, std::memory_order_relaxed);
    slot.waiters.store(0, std::memory_order_relaxed);
    slot.notified.store(false, std::memory_order_relaxed);
    std::memset(address, 0, b::kSemaphoreSize);
    slot.token.store(token, std::memory_order_release);
    __atomic_store_n(static_cast<uint32_t *>(address), token, __ATOMIC_RELEASE);
    return 0;
  }
  int destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    auto *slot = get(address);
    if (!slot || slot->address != address) return EINVAL;
    if (slot->waiters.load(std::memory_order_acquire)) return EBUSY;
    if (::sem_close(slot->wake)) return errno;
    slot->token.store(0, std::memory_order_release);
    __atomic_store_n(static_cast<uint32_t *>(address), 0, __ATOMIC_RELEASE);
    slot->wake = SEM_FAILED;
    slot->address = nullptr;
    return 0;
  }
  int post(void *address) {
    auto *slot = get(address);
    if (!slot) return EINVAL;
    uint32_t value = slot->count.load(std::memory_order_relaxed);
    for (;;) {
      if (value == uint32_t(b::kSEM_VALUE_MAX)) return EOVERFLOW;
      if (slot->count.compare_exchange_weak(value, value + 1,
              std::memory_order_release, std::memory_order_relaxed)) break;
    }
    notify(*slot);
    return 0;
  }
  int wait_internal(void *address,const int64_t *deadline) {
    auto *slot = get(address);
    if (!slot) return EINVAL;
    for (;;) {
      if (take(*slot)) return 0;
      uint32_t old = slot->waiters.fetch_add(1, std::memory_order_acq_rel);
      if (old >= 1024) {
        slot->waiters.fetch_sub(1, std::memory_order_release);
        return EAGAIN;
      }
      // Recheck after publishing the waiter to avoid losing a concurrent post.
      if (take(*slot)) {
        slot->waiters.fetch_sub(1, std::memory_order_release);
        return 0;
      }
      int result,error=0;
      if(!deadline){result=::sem_wait(slot->wake);error=errno;}
      else {
        int64_t fields[2];std::memcpy(fields,deadline,16);
        if(fields[1]<0||fields[1]>=1000000000){slot->waiters.fetch_sub(1,std::memory_order_release);return EINVAL;}
        if(fields[0]<0){slot->waiters.fetch_sub(1,std::memory_order_release);return ETIMEDOUT;}
        for(;;){
          result=::sem_trywait(slot->wake);error=errno;
          if(!result||error!=EAGAIN)break;
          timespec now{};if(::clock_gettime(CLOCK_REALTIME,&now)){error=errno;break;}
          int64_t seconds=fields[0]-now.tv_sec,nanos=fields[1]-now.tv_nsec;
          if(nanos<0){--seconds;nanos+=1000000000;}
          if(seconds<0||(!seconds&&!nanos)){error=ETIMEDOUT;break;}
          timespec pause{0,seconds>0?1000000:std::min<int64_t>(nanos,1000000)};
          if(::nanosleep(&pause,nullptr)){error=errno;break;}
        }
      }
      slot->waiters.fetch_sub(1, std::memory_order_acq_rel);
      if (!result) slot->notified.store(false, std::memory_order_release);
      if (slot->count.load(std::memory_order_acquire)) notify(*slot);
      if (result) return error ? error : EIO;
    }
  }
  int wait(void *address){return wait_internal(address,nullptr);}
  int timedwait(void *address,const int64_t *deadline){return deadline?wait_internal(address,deadline):EFAULT;}
  int trywait(void *address) {
    auto *slot = get(address);
    return !slot ? EINVAL : take(*slot) ? 0 : EAGAIN;
  }
  int value(void *address, int *output) {
    auto *slot = get(address);
    if (!slot || !output) return EINVAL;
    *output = int(slot->count.load(std::memory_order_acquire));
    return 0;
  }
};
template <typename... Args>
ProviderExport method(const char *name, std::shared_ptr<Semaphores> state,
                      int (Semaphores::*function)(Args...)) {
  void *address = closure([state, function](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    int error = std::apply([&](Args... values) { return ((*state).*function)(values...); }, args);
    if (error) *guest_errno_location() = errno_to_guest(error);
    return_value(capture, error ? -1 : 0);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(address), SymbolKind::Function,
          "semaphore/atomic-count-kernel-wake", false};
}
} // namespace
std::vector<ProviderExport> semaphore_exports() {
  auto state = std::make_shared<Semaphores>();
  return {method("sem_init", state, &Semaphores::init),
          method("sem_destroy", state, &Semaphores::destroy),
          method("sem_post", state, &Semaphores::post),
          method("sem_wait", state, &Semaphores::wait),
          method("sem_timedwait",state,&Semaphores::timedwait),
          method("sem_trywait", state, &Semaphores::trywait),
          method("sem_getvalue", state, &Semaphores::value)};
}
} // namespace shiraz::guest
