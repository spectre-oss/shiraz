// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <new>
#include <pthread.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
// android-14.0.0_r1 pthread.h __pthread_cleanup_t. Never reinterpret a Darwin
// pthread cleanup record or install a raw guest routine in native pthread TLS.
struct GuestRecord { uintptr_t previous, routine, argument; };
static_assert(sizeof(GuestRecord) == 24 && alignof(GuestRecord) == 8 &&
              offsetof(GuestRecord, previous) == 0 && offsetof(GuestRecord, routine) == 8 &&
              offsetof(GuestRecord, argument) == 16);
[[noreturn]] void fatal(const char *reason) noexcept {
  constexpr char prefix[] = "SHIRAZ_GUEST_FATAL pthread cleanup ";
  size_t length = 0; while (reason[length]) ++length;
  (void)::write(STDERR_FILENO, prefix, sizeof(prefix) - 1);
  (void)::write(STDERR_FILENO, reason, length);
  (void)::write(STDERR_FILENO, "\n", 1);
  std::abort();
}
struct Entry {
  void *record;
  GuestRecord fields;
  std::shared_ptr<void> owner;
};
struct ThreadStack {
  std::vector<Entry> entries;
  unsigned execution_depth = 0;
  ~ThreadStack() {
    if (!entries.empty() || execution_depth) fatal("unbalanced thread exit; pthread_exit/cancellation unsupported");
  }
};
pthread_once_t stack_once = PTHREAD_ONCE_INIT;
pthread_key_t stack_key;
std::atomic<bool> stack_key_ready{false};
int stack_key_error = 0;
void destroy_stack(void *opaque) { delete static_cast<ThreadStack *>(opaque); }
void create_stack_key() {
  stack_key_error = ::pthread_key_create(&stack_key, destroy_stack);
  if (!stack_key_error) stack_key_ready.store(true, std::memory_order_release);
}
ThreadStack *existing_stack() noexcept {
  return stack_key_ready.load(std::memory_order_acquire)
             ? static_cast<ThreadStack *>(::pthread_getspecific(stack_key)) : nullptr;
}
ThreadStack &current_stack() {
  if (auto *value = existing_stack()) return *value;
  // A native key, rather than a C++ TLV destructor, lets late guest TLS/key
  // callbacks create balanced cleanup regions after an earlier empty cleanup
  // key destructor. No stale pointer to a destroyed C++ thread_local is used.
  auto value = std::make_unique<ThreadStack>();
  if (::pthread_setspecific(stack_key, value.get())) fatal("cannot set per-thread stack");
  return *value.release();
}
void check_record(void *record) {
  uintptr_t address = reinterpret_cast<uintptr_t>(record);
  if (!address || (address & 7) || address > UINTPTR_MAX - sizeof(GuestRecord))
    fatal("invalid record address/alignment");
}
} // namespace
struct CleanupRuntime::State : std::enable_shared_from_this<State> {
  std::mutex mutex;
  CodeValidator validate;
  size_t active = 0, executing = 0;
  bool attached = false, closed = false;
  explicit State(CodeValidator value) : validate(std::move(value)) {
    if (!validate) throw ElfError("pthread cleanup requires guest code validation");
    int error = ::pthread_once(&stack_once, create_stack_key);
    if (error || stack_key_error) throw ElfError("cannot allocate guest pthread cleanup key");
  }
  void push(void *record, void *routine, void *argument) {
    if (in_guest_signal_handler()) fatal("push in signal handler unsupported");
    check_record(record);
    std::lock_guard<std::mutex> lock(mutex);
    if (!attached || closed) fatal("runtime unattached or closed");
    auto &stack = current_stack();
    if (routine && !validate(routine)) fatal("routine is not resident guest code");
    if (stack.entries.size() >= 4096 || active >= 65536) fatal("active record limit");
    if (std::any_of(stack.entries.begin(), stack.entries.end(),
                    [record](const auto &entry) { return entry.record == record; }))
      fatal("duplicate active record");
    GuestRecord fields{stack.entries.empty() ? 0 : reinterpret_cast<uintptr_t>(stack.entries.back().record),
                       reinterpret_cast<uintptr_t>(routine), reinterpret_cast<uintptr_t>(argument)};
    try { stack.entries.push_back({record, fields, shared_from_this()}); }
    catch (const std::bad_alloc &) { fatal("record allocation failed"); }
    std::memcpy(record, &fields, sizeof(fields));
    ++active;
  }
  void pop(void *record, int execute) {
    if (in_guest_signal_handler()) fatal("pop in signal handler unsupported");
    check_record(record);
    auto &stack = current_stack();
    GuestRecord fields;
    {
      std::lock_guard<std::mutex> lock(mutex);
      if (!attached || closed) fatal("runtime unattached or closed");
      if (stack.entries.empty() || stack.entries.back().record != record || stack.entries.back().owner.get() != this)
        fatal("pop is not the current thread's LIFO record");
      auto entry = stack.entries.back();
      std::memcpy(&fields, record, sizeof(fields));
      if (std::memcmp(&fields, &entry.fields, sizeof(fields))) fatal("record metadata changed");
      if (!active) fatal("active record accounting underflow");
      // Remove before invoking: callbacks may open balanced nested regions.
      stack.entries.pop_back(); --active;
      if (execute) {
        if (!fields.routine || !validate(reinterpret_cast<const void *>(fields.routine)))
          fatal("executed routine is not resident guest code");
        if (stack.execution_depth >= 64) fatal("callback recursion limit");
        ++stack.execution_depth; ++executing;
      }
    }
    if (execute) {
      try {
        invoke(reinterpret_cast<void *>(fields.routine),
               {Argument::integer(int64_t(fields.argument), ArgumentType::Int64)});
      } catch (...) { fatal("callback failed"); }
      std::lock_guard<std::mutex> lock(mutex);
      --stack.execution_depth; --executing;
    }
  }
  void shutdown() {
    std::lock_guard<std::mutex> lock(mutex);
    if (closed) return;
    if (active || executing) throw ElfError("pthread cleanup records/callbacks still active before guest unmap");
    closed = true;
  }
};
CleanupRuntime::CleanupRuntime(CodeValidator validate) : state_(std::make_shared<State>(std::move(validate))) {}
CleanupRuntime::~CleanupRuntime() {
  try { shutdown(); }
  catch (...) { fatal("runtime destroyed with live records"); }
}
void CleanupRuntime::attach(Loader &loader) {
  auto state = state_;
  std::lock_guard<std::mutex> lock(state->mutex);
  if (state->attached || state->closed) throw ElfError("pthread cleanup already attached or closed");
  loader.add_postfinalizer_hook([state] { state->shutdown(); });
  state->attached = true;
}
void CleanupRuntime::shutdown() const { state_->shutdown(); }
size_t CleanupRuntime::pending() const { std::lock_guard<std::mutex> lock(state_->mutex); return state_->active; }
void cleanup_require_empty() noexcept {
  auto *stack = existing_stack();
  if (stack && (!stack->entries.empty() || stack->execution_depth)) fatal("unbalanced guest start-routine return");
}
std::vector<ProviderExport> CleanupRuntime::exports() const {
  auto state = state_;
  void *push = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *record = cursor.read<void *>(), *routine = cursor.read<void *>(), *argument = cursor.read<void *>();
    HostErrno preserve;
    state->push(record, routine, argument);
  });
  void *pop = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *record = cursor.read<void *>();
    int execute = cursor.read<int>();
    HostErrno preserve;
    state->pop(record, execute);
  });
  return {{"__pthread_cleanup_push", "LIBC", reinterpret_cast<uintptr_t>(push), SymbolKind::Function,
            "pthread/cleanup-validated-thread-stack", false},
          {"__pthread_cleanup_pop", "LIBC", reinterpret_cast<uintptr_t>(pop), SymbolKind::Function,
            "pthread/cleanup-balanced-lifo-callback", false}};
}
} // namespace shiraz::guest
