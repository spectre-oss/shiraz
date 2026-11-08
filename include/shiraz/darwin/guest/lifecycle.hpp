// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
// Per-Loader guest callback registry. No guest function is registered with
// host atexit; attach() installs a mandatory drain before ELF images unmap.
// Attach after constructing Loader and before executing any guest initializer.
class Lifecycle {
public:
  explicit Lifecycle(CodeValidator validate);
  std::vector<ProviderExport> exports() const;
  void attach(Loader &loader);
  void finalize(void *dso = nullptr) const;
  // Idempotent; callbacks registered by callbacks join the same LIFO drain.
  // Shutdown from within this registry's own callback is rejected, because
  // unmapping the executing callback can never be safe.
  void shutdown() const;
  bool closed() const;
  size_t pending() const;
private:
  struct State;
  std::shared_ptr<State> state_;
};
} // namespace shiraz::guest
