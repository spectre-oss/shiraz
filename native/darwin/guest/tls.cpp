// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include "guest_io.hpp"
#include "guest_tls_layout.hpp"
#include <array>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <iterator>
#include <mutex>
#include <vector>
#include <utility>
#include <pthread.h>
#include <stdexcept>
#include <unistd.h>

// Apple libpthread private/pthread/private.h: int result, 0 or 1; macOS 11+.
// Weak linkage gives unsupported hosts an explicit failure, never an assumed
// normal lifetime. No private pthread/TSD layout or global hook is used.
extern "C" int pthread_self_is_exiting_np(void) __attribute__((weak_import));

namespace shiraz::guest {
struct GuestThreadKey {
  std::atomic<bool> active{true};
  std::mutex mutex;
  void *const destructor;
  const std::shared_ptr<Lifecycle> lifecycle;
  size_t queued_values = 0;
  // One token epoch covers all queued values. Selected callbacks copy it
  // before clearing their slot, so concurrent deletion cannot unpin them.
  std::shared_ptr<void> queued_retention;
  explicit GuestThreadKey(void *function, std::shared_ptr<Lifecycle> owner)
      : destructor(function), lifecycle(std::move(owner)) {}
};
namespace {
struct Destructor { void *function, *object; std::shared_ptr<void> retention; };
// Call only for an existing nonnull per-thread value. Deletion has already
// reset the logical queued count; stale deleted slots must not decrement it.
void discard_key_value(const GuestThreadKeyHandle &key) {
  std::shared_ptr<void> retired;
  {
    std::lock_guard<std::mutex> lock(key->mutex);
    if (key->active.load(std::memory_order_relaxed) && key->destructor) {
      if (!key->queued_values) std::terminate();
      if (!--key->queued_values) retired = std::move(key->queued_retention);
    }
  }
  // Retention destruction takes the lifecycle mutex. Never do it under the
  // key mutex or while invoking guest code.
}
struct SelectedKeyCallback {
  void *function = nullptr;
  std::shared_ptr<void> retention;
};
SelectedKeyCallback select_key_callback(const GuestThreadKeyHandle &key) {
  SelectedKeyCallback selected;
  std::shared_ptr<void> retired;
  {
    std::lock_guard<std::mutex> lock(key->mutex);
    if (!key->active.load(std::memory_order_relaxed) || !key->destructor) return selected;
    if (!key->queued_values || (key->lifecycle && !key->queued_retention)) std::terminate();
    selected.function = key->destructor;
    selected.retention = key->queued_retention;
    if (!--key->queued_values) retired = std::move(key->queued_retention);
  }
  return selected;
}
// The async readiness query must not touch a Mach-O TLV on an unprepared
// thread: doing so could allocate TLS from inside the signal handler.
std::array<std::atomic<pthread_t>, 4096> prepared_threads{};
std::array<std::atomic<uintptr_t>, 4096> prepared_stack_low{}, prepared_stack_high{};
std::array<std::atomic<uintptr_t>, 4096> prepared_guest_bases{};
std::array<std::atomic<uint64_t>, 4096> prepared_native_ids{};
std::array<std::atomic<uintptr_t>, 4096> prepared_native_tls_keys{};
inline uintptr_t native_tls_key() noexcept {
  uintptr_t key;
  asm volatile("mrs %0, tpidrro_el0" : "=r"(key));
  return key;
}
static_assert(std::atomic<pthread_t>::is_always_lock_free);
static_assert(std::atomic<uintptr_t>::is_always_lock_free);
static_assert(std::atomic<uint64_t>::is_always_lock_free);
struct MemoryDomains {
  std::mutex mutex;
  std::map<uintptr_t, uintptr_t> images;
  std::map<uintptr_t, uintptr_t> heaps, stacks;
};
MemoryDomains &memory_domains() {
  // Native teardown and Image destruction may run after ordinary static
  // destructors. Keep only the registry shell resident; owners remove entries.
  static auto *value = new MemoryDomains;
  return *value;
}
bool overlaps_range(const std::map<uintptr_t, uintptr_t> &ranges,
                    uintptr_t low, uintptr_t high, uintptr_t ignored = 0) {
  auto next = ranges.lower_bound(low);
  if (next != ranges.end() && next->first == ignored) ++next;
  if (next != ranges.end() && next->first < high) return true;
  auto before = ranges.lower_bound(low);
  while (before != ranges.begin()) {
    --before;
    if (before->first != ignored) return before->second > low;
  }
  return false;
}
bool overlaps_image(const MemoryDomains &domains, uintptr_t low, uintptr_t high) {
  return overlaps_range(domains.images, low, high);
}
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
struct TlsSlotStorage {
  static_assert(bionic::kTlsMinSlot >= -16 && bionic::kTlsMinSlot <= 0 &&
                bionic::kTlsMaxSlot >= 0 && bionic::kTlsMaxSlot <= 63);
  static_assert(bionic::kTlsSlotCount == bionic::kTlsMaxSlot - bionic::kTlsMinSlot + 1);
  static_assert(bionic::kTlsPointerBytes == sizeof(uintptr_t) && sizeof(uintptr_t) == 8);
  static_assert(bionic::kTlsStackGuardSlot >= bionic::kTlsMinSlot &&
                bionic::kTlsStackGuardSlot <= bionic::kTlsMaxSlot);
  // The returned TP addresses logical slot0, not the start of the object.
  // Round the prefix to whole16-byte units to retain TP alignment even when
  // the pinned arm64 ABI reserves a negative slot. Extra prefix is padding,
  // not an additional advertised TLS slot.
  static constexpr size_t prefix = (size_t(-bionic::kTlsMinSlot) + 1) & ~size_t(1);
  static constexpr size_t count = prefix + size_t(bionic::kTlsMaxSlot) + 1;
  alignas(16) std::array<uintptr_t, count> words{};
  uintptr_t *base() noexcept { return words.data() + prefix; }
};
struct ThreadState {
  // Backing covers the pinned slot range; private slot contents remain
  // unimplemented. This is still a canary-only personality, not a bionic TCB.
  TlsSlotStorage slots;
  std::vector<Destructor> destructors;
  std::map<GuestThreadKeyHandle, const void *, std::owner_less<GuestThreadKeyHandle>> key_values;
  size_t registrations = 0;
  unsigned cleanup_depth = 0;
  bool draining = false;
  size_t prepared_index = prepared_threads.size();
  ThreadState() {
    slots.base()[bionic::kTlsStackGuardSlot] = guard();
  }
  void publish() {
    if (prepared_index != prepared_threads.size()) return;
    const auto thread = pthread_self();
    const auto native_key = native_tls_key();
    if (!native_key) throw ElfError("native read-only TLS identity unavailable");
    uint64_t native_id = 0;
    if (pthread_threadid_np(nullptr, &native_id) || !native_id)
      throw ElfError("native thread identity unavailable");
    const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(thread));
    const auto size = pthread_get_stacksize_np(thread);
    if (!size || high <= size) {
      std::fprintf(stderr, "SHIRAZ_GUEST_FATAL native stack extent unavailable\n");
      std::abort();
    }
    auto &domains = memory_domains();
    std::lock_guard<std::mutex> lock(domains.mutex);
    if (!cached_self_tables_disjoint_stack(high - size, high))
      throw ElfError("native stack overlaps cached pthread identity table");
    for (size_t i = 0; i < prepared_threads.size(); ++i)
      if (reinterpret_cast<uintptr_t>(prepared_threads[i].load(std::memory_order_acquire)) > 1 &&
          prepared_native_tls_keys[i].load(std::memory_order_relaxed) == native_key)
        throw ElfError("duplicate prepared native read-only TLS identity");
    if (overlaps_image(domains, high - size, high))
      throw ElfError("native stack overlaps guest image reservation");
    if (overlaps_range(domains.heaps, high - size, high))
      throw ElfError("native stack overlaps managed guest allocation");
    if (overlaps_range(domains.stacks, high - size, high))
      throw ElfError("overlapping prepared native stack extents");
    for (size_t i = 0; i < prepared_threads.size(); ++i) {
      pthread_t empty = nullptr;
      if (prepared_threads[i].compare_exchange_strong(empty, reinterpret_cast<pthread_t>(uintptr_t(1)),
                                                     std::memory_order_acq_rel)) {
        try {
          if (!domains.stacks.emplace(high - size, high).second) std::abort();
        } catch (...) {
          prepared_threads[i].store(nullptr, std::memory_order_release);
          throw;
        }
        prepared_index = i;
        prepared_stack_low[i].store(high - size, std::memory_order_relaxed);
        prepared_stack_high[i].store(high, std::memory_order_relaxed);
        prepared_native_ids[i].store(native_id, std::memory_order_relaxed);
        prepared_native_tls_keys[i].store(native_key, std::memory_order_relaxed);
        prepared_guest_bases[i].store(reinterpret_cast<uintptr_t>(slots.base()), std::memory_order_relaxed);
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
        auto selected = select_key_callback(key);
        key_values.erase(found); // POSIX clears the slot before the callback.
        if (!selected.function) continue;
        try {
          // selected.retention survives the complete invoke return, even if
          // another thread deletes this key after selection.
          invoke(selected.function, {Argument::integer(reinterpret_cast<uintptr_t>(value),
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
    while (!key_values.empty()) {
      auto found = key_values.begin();
      auto key = found->first;
      key_values.erase(found);
      discard_key_value(key);
    }
    if (prepared_index != prepared_threads.size()) {
      auto &domains = memory_domains();
      std::lock_guard<std::mutex> lock(domains.mutex);
      const auto low = prepared_stack_low[prepared_index].load(std::memory_order_relaxed);
      const auto high = prepared_stack_high[prepared_index].load(std::memory_order_relaxed);
      auto stack = domains.stacks.find(low);
      if (stack == domains.stacks.end() || stack->second != high) std::abort();
      // Keep this cache live through all guest cleanup above, then invalidate
      // it while we still own the row and domains lock, before any row reuse.
      prepared_guest_bases[prepared_index].store(0, std::memory_order_relaxed);
      prepared_native_ids[prepared_index].store(0, std::memory_order_relaxed);
      prepared_native_tls_keys[prepared_index].store(0, std::memory_order_relaxed);
      prepared_threads[prepared_index].store(nullptr, std::memory_order_release);
      domains.stacks.erase(stack);
    }
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
template <size_t N> [[noreturn]] void mrs_failure(const char (&message)[N]) noexcept {
  (void)::write(STDERR_FILENO, message, N - 1);
  std::abort();
}
} // namespace
CachedSelfTables cached_pthread_self_tables() noexcept {
  static_assert(sizeof(prepared_threads) == 32768 &&
                sizeof(prepared_native_tls_keys) == 32768 &&
                sizeof(prepared_stack_low) == 32768 && sizeof(prepared_stack_high) == 32768);
  return {{{"owners", reinterpret_cast<uintptr_t>(prepared_threads.data()), sizeof(prepared_threads)},
           {"keys", reinterpret_cast<uintptr_t>(prepared_native_tls_keys.data()), sizeof(prepared_native_tls_keys)},
           {"low", reinterpret_cast<uintptr_t>(prepared_stack_low.data()), sizeof(prepared_stack_low)},
           {"high", reinterpret_cast<uintptr_t>(prepared_stack_high.data()), sizeof(prepared_stack_high)}},
          prepared_threads.size()};
}
bool cached_self_tables_disjoint_stack(uintptr_t low, uintptr_t high) noexcept {
  if (!low || high <= low) return false;
  const auto cache = cached_pthread_self_tables();
  if (cache.rows != 4096) return false;
  for (const auto &table : cache.tables) {
    if (!table.bytes || table.bytes > UINTPTR_MAX - table.address) return false;
    if (low < table.address + table.bytes && table.address < high) return false;
  }
  return true;
}
GuestImageReservation::GuestImageReservation(uintptr_t base, uint64_t bytes) : base_(base) {
  if (!base || !bytes || bytes > UINTPTR_MAX - base)
    throw ElfError("invalid guest image reservation extent");
  const uintptr_t high = base + bytes;
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  if (domains.images.size() >= 4096)
    throw ElfError("guest image reservation limit");
  if (overlaps_image(domains, base, high))
    throw ElfError("overlapping guest image reservations");
  if (overlaps_range(domains.stacks, base, high))
    throw ElfError("guest image overlaps prepared native stack extent");
  if (overlaps_range(domains.heaps, base, high))
    throw ElfError("guest image overlaps managed guest allocation");
  // map::emplace has the strong exception guarantee. Nothing is published
  // when allocation fails; the constructor's owner can unmap and roll back.
  domains.images.emplace(base, high);
}
GuestImageReservation::~GuestImageReservation() {
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  if (domains.images.erase(base_) != 1) std::abort();
}
GuestHeapReservation::GuestHeapReservation(uintptr_t base, uint64_t bytes) : base_(base) {
  if (!base || !bytes || bytes > UINTPTR_MAX - base)
    throw ElfError("invalid managed guest allocation extent");
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  if (domains.heaps.size() >= (1u << 20)) throw ElfError("managed guest allocation limit");
  const uintptr_t high = base + bytes;
  if (overlaps_range(domains.heaps, base, high))
    throw ElfError("overlapping managed guest allocation extents");
  if (overlaps_image(domains, base, high))
    throw ElfError("managed guest allocation overlaps guest image reservation");
  if (overlaps_range(domains.stacks, base, high))
    throw ElfError("managed guest allocation overlaps prepared native stack extent");
  domains.heaps.emplace(base, high);
}
GuestHeapReservation::~GuestHeapReservation() {
  if (!base_) return;
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  if (domains.heaps.erase(base_) != 1) std::abort();
}
GuestHeapReservation::GuestHeapReservation(GuestHeapReservation &&other) noexcept
    : base_(std::exchange(other.base_, 0)) {}
void GuestHeapReservation::relocate(uintptr_t base, uint64_t bytes) {
  if (!base_ || !base || !bytes || bytes > UINTPTR_MAX - base)
    throw ElfError("invalid managed guest allocation relocation");
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  const uintptr_t high = base + bytes;
  auto previous = domains.heaps.find(base_);
  if (previous == domains.heaps.end()) std::abort();
  if (overlaps_range(domains.heaps, base, high, base_))
    throw ElfError("relocated managed guest allocation overlaps another allocation");
  if (overlaps_image(domains, base, high))
    throw ElfError("relocated managed guest allocation overlaps guest image reservation");
  if (overlaps_range(domains.stacks, base, high))
    throw ElfError("relocated managed guest allocation overlaps prepared native stack extent");
  auto node = domains.heaps.extract(previous);
  node.key() = base;
  node.mapped() = high;
  if (!domains.heaps.insert(std::move(node)).inserted) std::abort();
  base_ = base;
}
bool registered_guest_heap_contains(uintptr_t pointer, uint64_t bytes) {
  if (!pointer || !bytes) return false;
  auto &domains = memory_domains();
  std::lock_guard<std::mutex> lock(domains.mutex);
  auto next = domains.heaps.upper_bound(pointer);
  if (next == domains.heaps.begin()) return false;
  const auto &range = *std::prev(next);
  return pointer < range.second && bytes <= range.second - pointer;
}
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
void require_guest_tls_lifetime() {
  if (!pthread_self_is_exiting_np)
    throw std::runtime_error("Darwin native thread exit-state query unavailable");
  const int exiting = pthread_self_is_exiting_np();
  if (!exiting) return;
  if (exiting != 1)
    throw std::runtime_error("invalid Darwin native thread exit state");
  if (pthread_once(&state_key_once, &initialize_state_key)) std::abort();
  auto *current = static_cast<ThreadState *>(pthread_getspecific(state_key));
  // An arbitrary external TSD callback has no owned end boundary. Draining on
  // each JNI return would prematurely destroy state used by its next JNI call;
  // another native destructor registration may already have missed round four.
  if (!current || (!current->draining && !current->cleanup_depth))
    throw std::runtime_error("unsupported guest entry during unowned native thread teardown");
}
void prepare_guest_tls() {
  HostErrno preserve;
  guest_errno_location();
  (void)guest_tls_base();
}
void *guest_tls_base() {
  auto &current = live_state();
  auto *result = current.slots.base(); // Complete state initialization first.
  current.publish();
  return result;
}
// The shared MRS entry forwards original SP in x7. This path reads only the
// already-published cache: it never initializes TLS, publishes a row, or takes
// a registry lock. It is a snapshot under the existing guest-entry lifetime
// preflight, not a lifetime lease or an asynchronous/native-spill proof.
extern "C" void *shiraz_guest_tls_access(uint64_t, uint64_t, uint64_t, uint64_t,
                                        uint64_t, uint64_t, uint64_t, uintptr_t original_sp) noexcept {
  int *const host_error = &errno;
  const int saved_error = *host_error;
  const auto current = pthread_self();
  uint64_t native_id = 0;
  const int identity_error = pthread_threadid_np(nullptr, &native_id);
  // Restore errno before loading the guest TP, without a destructor or a
  // further authored native call on the successful return path after it.
  *host_error = saved_error;
  if (identity_error || !native_id)
    mrs_failure("SHIRAZ_GUEST_FATAL MRS native thread identity unavailable\n");
  for (size_t i = 0; i < prepared_threads.size(); ++i) {
    if (prepared_threads[i].load(std::memory_order_acquire) != current ||
        prepared_native_ids[i].load(std::memory_order_relaxed) != native_id) continue;
    const auto low = prepared_stack_low[i].load(std::memory_order_relaxed);
    const auto high = prepared_stack_high[i].load(std::memory_order_relaxed);
    // Recheck both ownership observations before the final pointer load.
    // Native IDs also distinguish ordinary native pthread-handle reuse.
    if (prepared_threads[i].load(std::memory_order_acquire) != current ||
        prepared_native_ids[i].load(std::memory_order_acquire) != native_id) continue;
    if ((original_sp & 15) || original_sp < low || original_sp >= high)
      mrs_failure("SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n");
    if (kMrsStackBytesBefore > original_sp - low || kMrsStackBytesAfter > high - original_sp)
      mrs_failure("SHIRAZ_GUEST_FATAL MRS spill window outside prepared native stack extent\n");
    const auto base = prepared_guest_bases[i].load(std::memory_order_acquire);
    if (!base) mrs_failure("SHIRAZ_GUEST_FATAL MRS prepared TLS cache unavailable\n");
    return reinterpret_cast<void *>(base);
  }
  mrs_failure("SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n");
}
bool guest_tls_prepared() noexcept {
  pthread_t current = pthread_self();
  for (auto &thread : prepared_threads)
    if (thread.load(std::memory_order_acquire) == current) return true;
  return false;
}
extern "C" uintptr_t shiraz_guest_cached_pthread_self() noexcept {
  uintptr_t sp;
  asm volatile("mov %0, sp" : "=r"(sp));
  const auto key = native_tls_key();
  for (size_t i = 0; i < prepared_threads.size(); ++i) {
    const auto owner = prepared_threads[i].load(std::memory_order_acquire);
    if (reinterpret_cast<uintptr_t>(owner) <= 1) continue;
    if (!key || prepared_native_tls_keys[i].load(std::memory_order_relaxed) != key) continue;
    const auto low = prepared_stack_low[i].load(std::memory_order_relaxed);
    const auto high = prepared_stack_high[i].load(std::memory_order_relaxed);
    if (sp < low || sp >= high) continue;
    if (prepared_threads[i].load(std::memory_order_acquire) != owner ||
        prepared_native_tls_keys[i].load(std::memory_order_acquire) != key) continue;
    return reinterpret_cast<uintptr_t>(owner);
  }
  // Returning null would invent a successful pthread_self result. Guest entry
  // must prepare its native stack before reaching this no-argument adapter.
  __builtin_trap();
}
bool prepared_guest_stack_disjoint(uintptr_t pointer, uint64_t bytes) noexcept {
  if (!pointer || !bytes || bytes > UINTPTR_MAX - pointer) return false;
  const auto current = pthread_self();
  for (size_t i = 0; i < prepared_threads.size(); ++i) {
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    const auto low = prepared_stack_low[i].load(std::memory_order_relaxed);
    const auto high = prepared_stack_high[i].load(std::memory_order_relaxed);
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    if (!low || low >= high) return false;
    return pointer >= high || pointer + bytes <= low;
  }
  return false;
}
bool prepared_guest_stack_contains(uintptr_t pointer) noexcept {
  return prepared_guest_stack_remaining(pointer) != 0;
}
uintptr_t prepared_guest_stack_remaining(uintptr_t pointer) noexcept {
  const auto current = pthread_self();
  for (size_t i = 0; i < prepared_threads.size(); ++i) {
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    const auto low = prepared_stack_low[i].load(std::memory_order_relaxed);
    const auto high = prepared_stack_high[i].load(std::memory_order_relaxed);
    // A nested callback may release/reuse a readiness slot. Do not pair
    // another owner's metadata with the first matching pthread observation.
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    return pointer >= low && pointer < high ? high - pointer : 0;
  }
  return 0;
}
bool prepared_guest_stack_window(uintptr_t anchor, uint64_t before, uint64_t after) noexcept {
  const auto current = pthread_self();
  for (size_t i = 0; i < prepared_threads.size(); ++i) {
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    const auto low = prepared_stack_low[i].load(std::memory_order_relaxed);
    const auto high = prepared_stack_high[i].load(std::memory_order_relaxed);
    if (prepared_threads[i].load(std::memory_order_acquire) != current) continue;
    return anchor >= low && anchor < high && before <= anchor - low && after <= high - anchor;
  }
  return false;
}
GuestThreadKeyHandle make_guest_thread_key(void *destructor, std::shared_ptr<Lifecycle> lifecycle) {
  return std::make_shared<GuestThreadKey>(destructor, std::move(lifecycle));
}
void delete_guest_thread_key(const GuestThreadKeyHandle &key) noexcept {
  std::shared_ptr<void> retired;
  {
    std::lock_guard<std::mutex> lock(key->mutex);
    key->active.store(false, std::memory_order_release);
    key->queued_values = 0;
    retired = std::move(key->queued_retention);
  }
  // Raw values retained in foreign thread maps cannot select a callback now.
  // Previously selected callbacks own independent copies of this token epoch.
}
int set_guest_thread_value(const GuestThreadKeyHandle &key, const void *value) {
  if (!key->active.load(std::memory_order_acquire)) return EINVAL;
  // Acquire admission before publication, outside the key mutex. A temporary
  // token also protects the zero-to-nonzero handoff from namespace shutdown.
  std::shared_ptr<void> candidate;
  if (value && key->destructor && key->lifecycle) {
    candidate = key->lifecycle->retain_thread_callback();
    if (!candidate) return EINVAL;
  }
  auto &values = live_state().key_values;
  // Deletion reset these keys' queued counts/tokens; stale values have no
  // ownership to release and must not accumulate during create/delete churn.
  for (auto it = values.begin(); it != values.end();)
    if (!it->first->active.load(std::memory_order_acquire)) it = values.erase(it);
    else ++it;
  std::shared_ptr<void> retired;
  {
    std::lock_guard<std::mutex> lock(key->mutex);
    if (!key->active.load(std::memory_order_relaxed)) return EINVAL;
    auto found = values.find(key);
    if (!value) {
      if (found != values.end()) {
        values.erase(found);
        if (key->destructor) {
          if (!key->queued_values) std::terminate();
          if (!--key->queued_values) retired = std::move(key->queued_retention);
        }
      }
    } else if (found != values.end()) {
      found->second = value; // Replacement neither allocates nor adds a value.
    } else {
      if (values.size() >= 4096 || key->queued_values == SIZE_MAX) return EAGAIN;
      // Allocate the map node before changing count/token state. On failure,
      // the old values and queued ownership remain unchanged; candidate drops
      // outside this lock during unwinding.
      values.emplace(key, value);
      if (key->destructor && !key->queued_values++)
        key->queued_retention = std::move(candidate);
    }
  }
  return 0;
}
void *guest_thread_value(const GuestThreadKeyHandle &key) {
  if (!key->active.load(std::memory_order_acquire)) return nullptr;
  auto &values = live_state().key_values;
  auto found = values.find(key);
  return found == values.end() ? nullptr : const_cast<void *>(found->second);
}
std::vector<ProviderExport> tls_exports(CodeValidator validate, std::shared_ptr<Lifecycle> lifecycle) {
  if (!validate) throw ElfError("guest TLS requires resident-code validation");
  auto *address = closure([validate = std::move(validate), lifecycle = std::move(lifecycle)](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *function = cursor.read<void *>();
    void *object = cursor.read<void *>();
    (void)cursor.read<void *>(); // DSO handle: unloading is pinned by the loader.
    HostErrno preserve;
    auto &current = live_state();
    int result = -1;
    std::shared_ptr<void> retention;
    try {
      // Retention precedes any validator access to the Loader/Image. Closing
      // admission and inspecting the count use the same lifecycle lock.
      if (lifecycle) retention = lifecycle->retain_thread_callback();
    } catch (const std::bad_alloc &) {
      *guest_errno_location() = errno_to_guest(ENOMEM);
      return_value(capture, result);
      return;
    }
    if ((lifecycle && !retention) || !function || !validate(function)) {
      *guest_errno_location() = errno_to_guest(EINVAL);
    } else if (current.registrations >= 4096) {
      *guest_errno_location() = errno_to_guest(ENOMEM);
    } else {
      try {
        current.destructors.push_back({function, object, std::move(retention)});
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
