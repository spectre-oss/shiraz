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
// Synchronous invoke during teardown requires an owned scope or active drain.
// Ordinary native entries preserve state for the entire pthread lifetime.
// Uses an explicitly admitted Darwin exit-state SPI; missing capability fails.
void require_guest_tls_lifetime();
// Versioned admission contract: live Image reservations and prepared native
// stack extents cannot overlap. This does not describe arbitrary host memory.
inline constexpr uint32_t kImageStackExclusionProfile = 1;
// Register an entire owned Image reservation, including islands and gaps.
// Registration and native-stack publication share one exclusion lock. The
// owner must retain this token until after unmapping the reservation.
class GuestImageReservation {
  uintptr_t base_;
public:
  GuestImageReservation(uintptr_t base, uint64_t bytes);
  ~GuestImageReservation();
  GuestImageReservation(const GuestImageReservation &) = delete;
  GuestImageReservation &operator=(const GuestImageReservation &) = delete;
};
// A bounded managed allocator range, not a complete allocation-lifetime or
// readable-object certificate. Opaque native allocation APIs remain outside
// this registry. Admission, relocation and native-stack publication serialize
// with Image reservations. Retain the token until the native free completes.
class GuestHeapReservation {
  uintptr_t base_;
public:
  GuestHeapReservation(uintptr_t base, uint64_t bytes);
  ~GuestHeapReservation();
  GuestHeapReservation(GuestHeapReservation &&other) noexcept;
  GuestHeapReservation(const GuestHeapReservation &) = delete;
  GuestHeapReservation &operator=(const GuestHeapReservation &) = delete;
  // Strong precheck; reuses its existing registry node without allocating.
  void relocate(uintptr_t base, uint64_t bytes);
};
// Locked diagnostic snapshot only. Not async-safe, a lifetime lease, proof of
// actual mapped/readable user bytes, or an analyzer alias exemption.
bool registered_guest_heap_contains(uintptr_t pointer, uint64_t bytes);
void prepare_guest_tls();
void *guest_tls_base();
// Async-signal-safe readiness query only; never initializes native TLS.
bool guest_tls_prepared() noexcept;
// Cached pthread-reported extent, published with TLS readiness. This query
// does not allocate or ask pthread for stack metadata on a signal path.
// Membership is not proof of mapped pages or continuous guest SP residence.
bool prepared_guest_stack_contains(uintptr_t pointer) noexcept;
// Snapshot bytes remaining above pointer in this thread's prepared extent;
// zero means unavailable/outside. The physical extent outlives TLS epochs.
uintptr_t prepared_guest_stack_remaining(uintptr_t pointer) noexcept;
// Validates anchor membership and [anchor-before, anchor+after) from one
// cached bound pair, using subtraction so caller sizes cannot wrap addresses.
// Async query only; no allocation, registry lock or pthread metadata lookup.
bool prepared_guest_stack_window(uintptr_t anchor, uint64_t before, uint64_t after) noexcept;
// Lock-free current-thread extent exclusion. False for unprepared, empty or
// overflowing spans. This does not assert mapped bytes or object lifetime.
bool prepared_guest_stack_disjoint(uintptr_t pointer, uint64_t bytes) noexcept;
// Handles have namespace-independent identity and stay alive while any thread
// retains a value. Deletion suppresses future selections, never invokes a
// destructor. A callback already selected during concurrent deletion may finish.
class Lifecycle;
struct GuestThreadKey;
using GuestThreadKeyHandle = std::shared_ptr<GuestThreadKey>;
// An optional owner retains callback code while active nonnull values or
// selected callbacks exist. Application payload objects remain caller-owned.
GuestThreadKeyHandle make_guest_thread_key(void *destructor, std::shared_ptr<Lifecycle> lifecycle = {});
void delete_guest_thread_key(const GuestThreadKeyHandle &key) noexcept;
int set_guest_thread_value(const GuestThreadKeyHandle &key, const void *value);
void *guest_thread_value(const GuestThreadKeyHandle &key);
// With a lifecycle, C++ thread destructor registration retains the namespace
// through callback return and rejects shutdown before guest images finalize.
// The standalone form requires the caller to retain all validated code until
// native thread cleanup completes. Pthread keys use their own value retention.
std::vector<ProviderExport> tls_exports(CodeValidator validate, std::shared_ptr<Lifecycle> lifecycle = {});
} // namespace shiraz::guest
