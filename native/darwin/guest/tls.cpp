// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "guest_io.hpp"
#include <array>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <vector>
#include <pthread.h>

namespace shiraz::guest {
struct GuestThreadKey {
  std::atomic<bool> active{true};
  void *destructor;
  explicit GuestThreadKey(void *function) : destructor(function) {}
};
namespace {
struct Destructor { void *function, *object; };
// The async readiness query must not touch a Mach-O TLV on an unprepared
// thread: doing so could allocate TLS from inside the signal handler.
std::array<std::atomic<pthread_t>, 4096> prepared_threads{};
std::array<std::atomic<uintptr_t>, 4096> prepared_stack_low{}, prepared_stack_high{};
static_assert(std::atomic<pthread_t>::is_always_lock_free);
static_assert(std::atomic<uintptr_t>::is_always_lock_free);
uintptr_t guard() {
  // Android's stack guard is a process value copied into each thread's TLS.
  static const uintptr_t value = [] {
    uintptr_t random;
    do { arc4random_buf(&random, sizeof(random)); random &= ~uintptr_t(0xff); }
    while (!random);
    return random;
  }();
  return value;
}
struct ThreadState {
  alignas(16) std::array<uintptr_t, 64> slots{};
  std::vector<Destructor> destructors;
  std::map<GuestThreadKeyHandle, const void *, std::owner_less<GuestThreadKeyHandle>> key_values;
  size_t registrations = 0;
  unsigned cleanup_depth = 0;
  bool draining = false;
  size_t prepared_index = prepared_threads.size();
  ThreadState() { slots[5] = guard(); }
  void publish() {
    if (prepared_index != prepared_threads.size()) return;
    const auto thread = pthread_self();
    const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(thread));
    const auto size = pthread_get_stacksize_np(thread);
    if (!size || high <= size) {
      std::fprintf(stderr, "SHIRAZ_GUEST_FATAL native stack extent unavailable\n");
      std::abort();
    }
    for (size_t i = 0; i < prepared_threads.size(); ++i) {
      pthread_t empty = nullptr;
      if (prepared_threads[i].compare_exchange_strong(empty, reinterpret_cast<pthread_t>(uintptr_t(1)),
                                                     std::memory_order_acq_rel)) {
        prepared_index = i;
        prepared_stack_low[i].store(high - size, std::memory_order_relaxed);
        prepared_stack_high[i].store(high, std::memory_order_relaxed);
        prepared_threads[i].store(thread, std::memory_order_release);
        return;
      }
    }
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL prepared TLS thread limit\n");
    std::abort();
  }
  void drain_destructors() {
    // Registrations made by a destructor join the same LIFO list. Keep TLS
    // storage alive until all callbacks finish; guest images stay resident.
    while (!destructors.empty()) {
      auto entry = destructors.back();
      destructors.pop_back();
      try {
        invoke(entry.function, {Argument::integer(reinterpret_cast<uintptr_t>(entry.object),
                                                   ArgumentType::Int64)});
      } catch (...) {
        std::fprintf(stderr, "SHIRAZ_GUEST_FATAL thread-local destructor failure\n");
        std::abort();
      }
    }
  }
  ~ThreadState() {
    drain_destructors();
    // Guest passes are independent of Darwin's native TSD round. In particular
    // a first emutls allocation in a final native callback still gets the
    // upstream runtime's delayed key cleanup. New/reset values join later
    // passes; callbacks run without a registry lock and with this state live.
    for (int pass = 0; pass < bionic::kPTHREAD_DESTRUCTOR_ITERATIONS; ++pass) {
      std::vector<GuestThreadKeyHandle> pending;
      for (const auto &entry : key_values)
        if (entry.second && entry.first->active.load(std::memory_order_acquire) &&
            entry.first->destructor)
          pending.push_back(entry.first);
      if (pending.empty()) break;
      for (const auto &key : pending) {
        auto found = key_values.find(key);
        if (found == key_values.end() || !found->second) continue;
        const void *value = found->second;
        key_values.erase(found); // POSIX clears the slot before the callback.
        if (!key->active.load(std::memory_order_acquire)) continue;
        try {
          invoke(key->destructor, {Argument::integer(reinterpret_cast<uintptr_t>(value),
                                                     ArgumentType::Int64)});
        } catch (...) {
          std::fprintf(stderr, "SHIRAZ_GUEST_FATAL pthread key destructor failure\n");
          std::abort();
        }
        // A key destructor may construct C++ TLS and register more cleanup.
        // Run that LIFO cleanup while its emutls storage is still resident.
        drain_destructors();
      }
    }
    // POSIX does not promise to destroy values endlessly reset by destructors.
    // Discard residual slots after the fixed guest pass budget; do not schedule
    // additional native callbacks or dereference application-owned payloads.
    key_values.clear();
    if (prepared_index != prepared_threads.size())
      prepared_threads[prepared_index].store(nullptr, std::memory_order_release);
  }
};
pthread_key_t state_key;
pthread_once_t state_key_once = PTHREAD_ONCE_INIT;
void destroy_state(void *opaque) {
  auto *current = static_cast<ThreadState *>(opaque);
  // Native TSD clears its slot before calling us. Reattach while destructors
  // execute so nested TLS access and atexit registration see this live state.
  if (pthread_setspecific(state_key, current)) std::abort();
  current->draining = true;
  delete current;
  if (pthread_setspecific(state_key, nullptr)) std::abort();
}
void initialize_state_key() {
  if (pthread_key_create(&state_key, &destroy_state)) std::abort();
}
ThreadState &live_state() {
  if (pthread_once(&state_key_once, &initialize_state_key)) std::abort();
  auto *current = static_cast<ThreadState *>(pthread_getspecific(state_key));
  if (current) return *current;
  auto *created = new ThreadState;
  if (pthread_setspecific(state_key, created)) { delete created; std::abort(); }
  return *created;
}
[[noreturn]] void stack_fail() {
  std::fprintf(stderr, "SHIRAZ_GUEST_FATAL stack guard check failed\n");
  std::abort();
}
} // namespace
extern "C" [[noreturn]] void shiraz_guest_stack_check_fail() { stack_fail(); }
GuestTlsCleanupScope::GuestTlsCleanupScope() noexcept {
  try {
    auto &current = live_state();
    if (current.cleanup_depth == 4096) std::abort();
    ++current.cleanup_depth;
    state_ = &current;
  } catch (...) { std::abort(); }
}
GuestTlsCleanupScope::~GuestTlsCleanupScope() {
  auto *current = static_cast<ThreadState *>(state_);
  if (!current || pthread_getspecific(state_key) != current || !current->cleanup_depth)
    std::abort();
  --current->cleanup_depth;
  // Always drain at the outer callback boundary. Native TLV bookkeeping can
  // itself be torn down before late callbacks, and there may be no later TSD
  // round. Keep depth/draining in this live owned object, never another TLV.
  if (!current->cleanup_depth && !current->draining) destroy_state(current);
}
void prepare_guest_tls() {
  HostErrno preserve;
  guest_errno_location();
  (void)guest_tls_base();
}
void *guest_tls_base() {
  auto &current = live_state();
  auto *result = current.slots.data(); // Complete TLV initialization first.
  current.publish();
  return result;
}
bool guest_tls_prepared() noexcept {
  pthread_t current = pthread_self();
  for (auto &thread : prepared_threads)
    if (thread.load(std::memory_order_acquire) == current) return true;
  return false;
}
bool prepared_guest_stack_contains(uintptr_t pointer) noexcept {
  const auto current = pthread_self();
  for (size_t i = 0; i < prepared_threads.size(); ++i)
    if (prepared_threads[i].load(std::memory_order_acquire) == current)
      return pointer >= prepared_stack_low[i].load(std::memory_order_relaxed) &&
             pointer < prepared_stack_high[i].load(std::memory_order_relaxed);
  return false;
}
GuestThreadKeyHandle make_guest_thread_key(void *destructor) {
  return std::make_shared<GuestThreadKey>(destructor);
}
void delete_guest_thread_key(const GuestThreadKeyHandle &key) noexcept {
  key->active.store(false, std::memory_order_release);
}
int set_guest_thread_value(const GuestThreadKeyHandle &key, const void *value) {
  if (!key->active.load(std::memory_order_acquire)) return EINVAL;
  auto &values = live_state().key_values;
  // Deleted keys from any namespace need no destructor and must not accumulate
  // across create/delete churn on a long-lived thread.
  for (auto it = values.begin(); it != values.end();)
    if (!it->first->active.load(std::memory_order_acquire)) it = values.erase(it);
    else ++it;
  if (!value) { values.erase(key); return 0; }
  if (!values.count(key) && values.size() >= 4096) return EAGAIN;
  values[key] = value;
  return 0;
}
void *guest_thread_value(const GuestThreadKeyHandle &key) {
  if (!key->active.load(std::memory_order_acquire)) return nullptr;
  auto &values = live_state().key_values;
  auto found = values.find(key);
  return found == values.end() ? nullptr : const_cast<void *>(found->second);
}
std::vector<ProviderExport> tls_exports(CodeValidator validate) {
  if (!validate) throw ElfError("guest TLS requires resident-code validation");
  auto *address = closure([validate = std::move(validate)](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *function = cursor.read<void *>();
    void *object = cursor.read<void *>();
    (void)cursor.read<void *>(); // DSO handle: unloading is pinned by the loader.
    HostErrno preserve;
    auto &current = live_state();
    int result = -1;
    if (!function || !validate(function)) {
      *guest_errno_location() = errno_to_guest(EINVAL);
    } else if (current.registrations >= 4096) {
      *guest_errno_location() = errno_to_guest(ENOMEM);
    } else {
      try {
        current.destructors.push_back({function, object});
        ++current.registrations;
        result = 0;
      } catch (const std::bad_alloc &) {
        *guest_errno_location() = errno_to_guest(ENOMEM);
      }
    }
    return_value(capture, result);
  });
  return {{"__cxa_thread_atexit_impl", "LIBC", reinterpret_cast<uintptr_t>(address),
           SymbolKind::Function, "tls/destructors-resident-images", false},
          explicit_noreturn_export("__stack_chk_fail", "LIBC", &shiraz_guest_stack_check_fail)};
}
} // namespace shiraz::guest
