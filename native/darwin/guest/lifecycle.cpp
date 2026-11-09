// SPDX-License-Identifier: GPL-3.0-only
// Original implementation of the Itanium DSO destructor ABI. Behavioral
// reference: android-14.0.0_r1 libc/bionic/atexit.cpp (extract before invoking,
// restart at the newest registration after reentrant callbacks).
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <algorithm>
#include <condition_variable>
#include <map>
#include <mutex>
#include <thread>

namespace shiraz::guest {
struct Lifecycle::State {
  struct Entry { void *function, *object, *dso; bool plain; };
  std::mutex mutex;
  std::condition_variable changed;
  std::vector<Entry> entries;
  struct ForkEntry { void *prepare,*parent,*child,*dso; };
  std::vector<ForkEntry> fork_entries;
  std::map<std::thread::id, size_t> executing;
  std::map<std::thread::id, size_t> draining;
  CodeValidator validate;
  std::function<void()> before_shutdown;
  std::function<void()> before_quiesce;
  size_t registrations = 0, active = 0, active_drains = 0;
  size_t thread_callbacks = 0;
  bool attached = false, shutting_down = false, closed = false;
  bool callback_admission_closed = false;
  std::thread::id shutdown_thread;
  explicit State(CodeValidator check) : validate(std::move(check)) {
    if (!validate) throw ElfError("guest lifecycle requires resident-code validation");
  }
  int add(void *function, void *object, void *dso, bool plain) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!attached || closed || !function || !validate(function)) {
      *guest_errno_location() = errno_to_guest(EINVAL);
      return -1;
    }
    if (registrations >= 4096) {
      *guest_errno_location() = errno_to_guest(ENOMEM);
      return -1;
    }
    try { entries.push_back({function, object, dso, plain}); }
    catch (const std::bad_alloc &) {
      *guest_errno_location() = errno_to_guest(ENOMEM);
      return -1;
    }
    ++registrations;
    changed.notify_all();
    return 0;
  }
  int add_fork(void *prepare,void *parent,void *child,void *dso) {
    std::lock_guard<std::mutex> lock(mutex);
    if(!attached||closed||shutting_down)return errno_to_guest(EINVAL);
    for(void *function:{prepare,parent,child})if(function&&!validate(function))return errno_to_guest(EINVAL);
    if(fork_entries.size()>=4096)return errno_to_guest(ENOMEM);
    try{fork_entries.push_back({prepare,parent,child,dso});}catch(const std::bad_alloc &){return errno_to_guest(ENOMEM);}
    // Owned by this guest namespace, never registered with native pthread_atfork.
    // Guest fork/clone remains explicitly unsupported; native host subprocesses
    // must not accidentally execute guest callbacks in an unrelated child.
    return 0;
  }
  void completed() {
    std::lock_guard<std::mutex> lock(mutex);
    auto found = executing.find(std::this_thread::get_id());
    if (found == executing.end() || !found->second || !active)
      std::terminate();
    if (!--found->second) executing.erase(found);
    --active;
    changed.notify_all();
  }
  void finalize(void *dso) {
    const auto current = std::this_thread::get_id();
    {
      std::lock_guard<std::mutex> lock(mutex);
      if (closed) return;
      if (callback_admission_closed && current != shutdown_thread &&
          !executing.count(current) && !draining.count(current))
        throw ElfError("foreign guest lifecycle drain after shutdown admission closed");
      ++draining[current];
      ++active_drains;
    }
    struct Completion {
      State *state;
      std::thread::id thread;
      ~Completion() {
        std::lock_guard<std::mutex> lock(state->mutex);
        auto found = state->draining.find(thread);
        if (found == state->draining.end() || !found->second || !state->active_drains) std::terminate();
        if (!--found->second) state->draining.erase(found);
        --state->active_drains;
        state->changed.notify_all();
      }
    } completion{this, current};
    if(dso){std::lock_guard<std::mutex> lock(mutex);fork_entries.erase(std::remove_if(fork_entries.begin(),fork_entries.end(),[&](const ForkEntry &entry){return entry.dso==dso;}),fork_entries.end());}
    for (;;) {
      Entry entry{};
      {
        std::unique_lock<std::mutex> lock(mutex);
        if (closed) return;
        auto found = std::find_if(entries.rbegin(), entries.rend(), [&](const Entry &candidate) {
          return !dso || candidate.dso == dso;
        });
        if (found == entries.rend()) return;
        entry = *found;
        // Allocate bookkeeping before removing the registration. Failure
        // leaves the callback pending, and Loader shutdown will abort safely.
        ++executing[std::this_thread::get_id()];
        ++active;
        entries.erase(std::next(found).base());
      }
      try {
        prepare_guest_tls();
        if (entry.plain) invoke(entry.function, {});
        else invoke(entry.function, {Argument::integer(reinterpret_cast<uintptr_t>(entry.object), ArgumentType::Int64)});
      } catch (...) {
        completed();
        throw;
      }
      completed();
    }
  }
  void shutdown() {
    {
      std::unique_lock<std::mutex> lock(mutex);
      if (closed) return;
      auto current = std::this_thread::get_id();
      if (executing.count(current) || draining.count(current))
        throw ElfError("cannot unmap a guest lifecycle callback while it executes");
      if (shutting_down) {
        if (shutdown_thread == current) throw ElfError("recursive guest lifecycle shutdown");
        // Native stop hooks may join this caller. Waiting for the owner to
        // close here would make that join cyclic; refuse the competing full
        // shutdown while ordinary per-DSO drains remain admitted.
        if (!callback_admission_closed)
          throw ElfError("concurrent guest lifecycle shutdown during native callback stop");
        throw ElfError("concurrent guest lifecycle shutdown after callback admission closed");
      }
      shutting_down = true;
      shutdown_thread = current;
    }
    // Stop native resource delivery before blocking external drains: a stop
    // hook may need to join a worker currently calling __cxa_finalize.
    if (before_quiesce) before_quiesce();
    {
      std::unique_lock<std::mutex> lock(mutex);
      callback_admission_closed = true;
      changed.wait(lock, [&] { return !active && !active_drains; });
      if (thread_callbacks)
        throw ElfError("guest thread-local callbacks still retain namespace at shutdown");
    }
    // Run ELF finalizers before closing registration/validation. In particular
    // CRT FINI_ARRAY entries may invoke __cxa_finalize or register callbacks.
    if (before_shutdown) before_shutdown();
    for (;;) {
      finalize(nullptr);
      std::unique_lock<std::mutex> lock(mutex);
      if (entries.empty() && !active) {
        closed = true;
        fork_entries.clear();
        // Process-resident provider closures must never retain a validator
        // that could inspect an Image after its Loader has been destroyed.
        validate = {};
        before_shutdown = {};
        before_quiesce = {};
        changed.notify_all();
        return;
      }
      changed.wait(lock, [&] { return !entries.empty() || !active; });
    }
  }
};
Lifecycle::Lifecycle(CodeValidator validate) : state_(std::make_shared<State>(std::move(validate))) {}
std::vector<ProviderExport> Lifecycle::exports() const {
  auto state = state_;
  auto cxa = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    auto *function = cursor.read<void *>();
    auto *object = cursor.read<void *>();
    auto *dso = cursor.read<void *>();
    HostErrno preserve;
    return_value(capture, state->add(function, object, dso, false));
  });
  auto plain = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    auto *function = cursor.read<void *>();
    HostErrno preserve;
    return_value(capture, state->add(function, nullptr, nullptr, true));
  });
  auto finalize = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    auto *dso = cursor.read<void *>();
    HostErrno preserve;
    state->finalize(dso);
  });
  auto atfork=closure([state](Capture &capture){CallCursor cursor(capture,Convention::Aapcs64);
    void *prepare=cursor.read<void *>(),*parent=cursor.read<void *>(),*child=cursor.read<void *>(),*dso=cursor.read<void *>();
    HostErrno preserve;return_value(capture,state->add_fork(prepare,parent,child,dso));
  });
  return {{"__cxa_atexit", "LIBC", reinterpret_cast<uintptr_t>(cxa), SymbolKind::Function,
           "guest-lifecycle/validated-resident-callback", false},
          {"atexit", "LIBC", reinterpret_cast<uintptr_t>(plain), SymbolKind::Function,
           "guest-lifecycle/no-argument-callback", false},
          {"__register_atfork","LIBC",uintptr_t(atfork),SymbolKind::Function,"guest-lifecycle/owned-atfork-registration-no-guest-fork",false},
          {"__cxa_finalize", "LIBC", reinterpret_cast<uintptr_t>(finalize), SymbolKind::Function,
           "guest-lifecycle/lifo-per-dso-exactly-once", false}};
}
void Lifecycle::attach(Loader &loader) {
  auto state = state_;
  std::lock_guard<std::mutex> lock(state->mutex);
  if (state->attached || state->closed) throw ElfError("guest lifecycle already attached or closed");
  loader.set_finalizer([state] { state->shutdown(); }, true);
  state->before_shutdown = [&loader] { loader.finalize_images(); };
  state->before_quiesce = [&loader] { loader.stop_native_callbacks(); };
  state->attached = true;
}
void Lifecycle::finalize(void *dso) const { state_->finalize(dso); }
void Lifecycle::shutdown() const { state_->shutdown(); }
bool Lifecycle::closed() const { std::lock_guard<std::mutex> lock(state_->mutex); return state_->closed; }
size_t Lifecycle::pending() const { std::lock_guard<std::mutex> lock(state_->mutex); return state_->entries.size(); }
std::shared_ptr<void> Lifecycle::retain_thread_callback() const {
  struct Retention {
    std::shared_ptr<State> state;
    bool admitted = false;
    explicit Retention(std::shared_ptr<State> value) : state(std::move(value)) {
      std::lock_guard<std::mutex> lock(state->mutex);
      if (!state->attached || state->closed || state->callback_admission_closed) return;
      if (state->thread_callbacks == SIZE_MAX) throw ElfError("guest thread callback retention overflow");
      ++state->thread_callbacks;
      admitted = true;
    }
    ~Retention() {
      if (!admitted) return;
      std::lock_guard<std::mutex> lock(state->mutex);
      if (!state->thread_callbacks) std::terminate();
      --state->thread_callbacks;
      state->changed.notify_all();
    }
  };
  auto token = std::make_shared<Retention>(state_);
  return token->admitted ? std::shared_ptr<void>(std::move(token)) : nullptr;
}
size_t Lifecycle::retained_thread_callbacks() const {
  std::lock_guard<std::mutex> lock(state_->mutex);
  return state_->thread_callbacks;
}
} // namespace shiraz::guest
