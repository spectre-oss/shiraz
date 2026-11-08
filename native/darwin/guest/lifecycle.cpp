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
  std::map<std::thread::id, size_t> executing;
  CodeValidator validate;
  size_t registrations = 0, active = 0;
  bool attached = false, shutting_down = false, closed = false;
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
    for (;;) {
      Entry entry{};
      {
        std::lock_guard<std::mutex> lock(mutex);
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
      if (executing.count(current))
        throw ElfError("cannot unmap a guest lifecycle callback while it executes");
      if (shutting_down) {
        if (shutdown_thread == current) throw ElfError("recursive guest lifecycle shutdown");
        changed.wait(lock, [&] { return closed; });
        return;
      }
      shutting_down = true;
      shutdown_thread = current;
    }
    for (;;) {
      finalize(nullptr);
      std::unique_lock<std::mutex> lock(mutex);
      if (entries.empty() && !active) {
        closed = true;
        // Process-resident provider closures must never retain a validator
        // that could inspect an Image after its Loader has been destroyed.
        validate = {};
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
  return {{"__cxa_atexit", "LIBC", reinterpret_cast<uintptr_t>(cxa), SymbolKind::Function,
           "guest-lifecycle/validated-resident-callback", false},
          {"atexit", "LIBC", reinterpret_cast<uintptr_t>(plain), SymbolKind::Function,
           "guest-lifecycle/no-argument-callback", false},
          {"__cxa_finalize", "LIBC", reinterpret_cast<uintptr_t>(finalize), SymbolKind::Function,
           "guest-lifecycle/lifo-per-dso-exactly-once", false}};
}
void Lifecycle::attach(Loader &loader) {
  auto state = state_;
  std::lock_guard<std::mutex> lock(state->mutex);
  if (state->attached || state->closed) throw ElfError("guest lifecycle already attached or closed");
  loader.set_finalizer([state] { state->shutdown(); });
  state->attached = true;
}
void Lifecycle::finalize(void *dso) const { state_->finalize(dso); }
void Lifecycle::shutdown() const { state_->shutdown(); }
bool Lifecycle::closed() const { std::lock_guard<std::mutex> lock(state_->mutex); return state_->closed; }
size_t Lifecycle::pending() const { std::lock_guard<std::mutex> lock(state_->mutex); return state_->entries.size(); }
} // namespace shiraz::guest
