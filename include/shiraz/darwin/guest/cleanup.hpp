// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
// Balanced Android pthread cleanup records. Attach before guest constructors;
// shutdown rejects live records/callbacks before their code can be unmapped.
// Explicit pthread_exit ordering and native cancellation are not implemented.
// Thread return with an unbalanced lexical cleanup region is a fatal contract
// error, not permission to call handlers through dead guest stack records.
// The shutdown guard runs after DSO finalizers and before image destruction.
class CleanupRuntime {
public:
  explicit CleanupRuntime(CodeValidator validate);
  ~CleanupRuntime();
  CleanupRuntime(const CleanupRuntime &) = delete;
  CleanupRuntime &operator=(const CleanupRuntime &) = delete;
  std::vector<ProviderExport> exports() const;
  void attach(Loader &loader);
  void shutdown() const;
  size_t pending() const;
private:
  struct State;
  std::shared_ptr<State> state_;
};
// Optional thread-entry integration: call after the guest start routine
// returns, before native thread/TLS epilogues. Also enforced by TLS teardown.
void cleanup_require_empty() noexcept;
} // namespace shiraz::guest
