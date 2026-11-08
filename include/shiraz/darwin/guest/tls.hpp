// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
// Initializes native TLS storage before entering guest code. No TPIDR register
// is written; instruction redirection is a separate verification gate.
// Late teardown callbacks require this scope; fresh state is drained at the
// callback boundary without depending on another native destructor round.
class GuestTlsCleanupScope {
  void *state_ = nullptr;
public:
  GuestTlsCleanupScope() noexcept;
  ~GuestTlsCleanupScope();
  GuestTlsCleanupScope(const GuestTlsCleanupScope &) = delete;
  GuestTlsCleanupScope &operator=(const GuestTlsCleanupScope &) = delete;
};
void prepare_guest_tls();
void *guest_tls_base();
// Async-signal-safe readiness query only; never initializes native TLS.
bool guest_tls_prepared() noexcept;
// Cached pthread-reported extent, published with TLS readiness. This query
// does not allocate or ask pthread for stack metadata on a signal path.
// Membership is not proof of mapped pages or continuous guest SP residence.
bool prepared_guest_stack_contains(uintptr_t pointer) noexcept;
// Handles have namespace-independent identity and stay alive while any thread
// retains a value. Deletion suppresses future selections, never invokes a
// destructor. A callback already selected during concurrent deletion may finish.
struct GuestThreadKey;
using GuestThreadKeyHandle = std::shared_ptr<GuestThreadKey>;
GuestThreadKeyHandle make_guest_thread_key(void *destructor);
void delete_guest_thread_key(const GuestThreadKeyHandle &key) noexcept;
int set_guest_thread_value(const GuestThreadKeyHandle &key, const void *value);
void *guest_thread_value(const GuestThreadKeyHandle &key);
std::vector<ProviderExport> tls_exports(CodeValidator validate);
} // namespace shiraz::guest
