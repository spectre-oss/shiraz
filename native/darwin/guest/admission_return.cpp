// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_return.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cstdlib>
namespace shiraz::guest {
namespace {
thread_local AdmissionReturnScope* current = nullptr;
}
AdmissionReturnScope::AdmissionReturnScope(const void* owner) noexcept
    : previous_(current), owner_(owner) {
    current = this;
}
AdmissionReturnScope::~AdmissionReturnScope() {
    if (current != this)
        std::abort();
    current = previous_;
}
void AdmissionReturnScope::arm(uintptr_t return_pc, uintptr_t guest_sp) {
    if (current != this || !owner_ || return_pc_ || !return_pc || (return_pc & 3) || !guest_sp ||
        (guest_sp & 15) || !prepared_guest_stack_contains(guest_sp))
        throw ElfError("invalid guest return boundary");
    return_pc_ = return_pc;
    guest_sp_ = guest_sp;
}
bool AdmissionReturnScope::allows(const void* owner, const IndirectTransfer& transfer,
                                  uintptr_t target, uintptr_t sp) noexcept {
    if (!owner || !current || current->owner_ != owner || !current->return_pc_ ||
        target != current->return_pc_ || sp != current->guest_sp_)
        return false;
    return transfer.kind == IndirectTransferKind::Return ||
           (transfer.kind == IndirectTransferKind::Branch && transfer.target_register == 30);
}
} // namespace shiraz::guest
