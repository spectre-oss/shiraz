// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/tls.hpp"
namespace shiraz::guest {
// Only query from an ABI entry or a prepared guest signal callback; the
// signal dispatcher checks readiness before touching this native TLS state.
bool in_guest_signal_handler() noexcept;
// Owns native dispositions until shutdown. Attach before exposing guest
// installation APIs; Loader's pre-unmap hook stops callbacks before code dies.
// Basic one-argument handlers are supported. SIGINFO/ucontext, alternate
// stacks, realtime signals and unprepared native delivery threads are not.
// Mask changes affect only this runtime's owned dispositions. Native masks are
// preserved, handler mask APIs are unsupported, and live changed thread masks
// must be restored before shutdown. Exiting native threads release their masks.
class SignalRuntime {
public:
  explicit SignalRuntime(CodeValidator validate);
  ~SignalRuntime();
  std::vector<ProviderExport> exports() const;
  void attach(Loader &loader);
  void shutdown() const;

private:
  struct State;
  std::shared_ptr<State> state_;
};
} // namespace shiraz::guest
