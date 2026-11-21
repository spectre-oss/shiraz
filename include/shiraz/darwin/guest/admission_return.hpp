// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/decode.hpp"
namespace shiraz::guest {
// Experimental synchronous host-to-guest return boundary. Scopes are
// thread-local, strictly nested, and must outlive the guest call. Arming must
// happen at the actual guest-entry SP. Not signal-safe and not a substitute for
// image lifetime or external-call authorization.
class AdmissionReturnScope {
  public:
    explicit AdmissionReturnScope(const void* owner) noexcept;
    ~AdmissionReturnScope();
    AdmissionReturnScope(const AdmissionReturnScope&) = delete;
    AdmissionReturnScope& operator=(const AdmissionReturnScope&) = delete;
    void arm(uintptr_t return_pc, uintptr_t guest_sp);
    // transfer must have come from AdmissionSession::transfer_site.
    static bool allows(const void* owner, const IndirectTransfer& transfer, uintptr_t target,
                       uintptr_t guest_sp) noexcept;

  private:
    AdmissionReturnScope* previous_;
    const void* owner_;
    uintptr_t return_pc_ = 0, guest_sp_ = 0;
};
} // namespace shiraz::guest
