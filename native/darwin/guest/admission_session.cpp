// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_session.hpp"
#include <algorithm>
#include <cstdlib>
#include <utility>
namespace shiraz::guest {
namespace {
thread_local bool in_admission = false;
struct Guard {
    Guard() {
        if (in_admission)
            throw ElfError("reentrant guest admission");
        in_admission = true;
    }
    ~Guard() {
        in_admission = false;
    }
};
} // namespace
AdmissionSession::AdmissionSession(Elf elf, std::vector<uint8_t> original, uintptr_t base,
                                   uintptr_t islands, size_t capacity, uintptr_t syscall_dispatcher,
                                   uintptr_t validator)
    : elf_(std::move(elf)), original_(std::move(original)), base_(base), islands_(islands),
      syscall_(syscall_dispatcher), validator_(validator), capacity_(capacity),
      publisher_(base, elf_.span, islands, capacity) {
    if (!validator_)
        throw ElfError("guest admission requires transfer validator");
}
bool AdmissionSession::contains_decoded(uint64_t address) const {
    if (address < elf_.first_page || (address & 3) || address - elf_.first_page >= elf_.span)
        return false;
    uint64_t index = (address - elf_.first_page) / 4;
    return index / 8 < decoded_.size() && (decoded_[size_t(index / 8)] & (1u << (index % 8)));
}
void AdmissionSession::publish(const std::vector<uint64_t>& roots) {
    auto analysis = inspect_thread_pointers(elf_, original_, roots);
    // Existing entries remain executable after a new entry is admitted. A
    // reanalysis that forgets any previously decoded instruction cannot be
    // used as their new admission state, even if its patch list is consistent.
    if (!decoded_.empty()) {
        if (analysis.decoded_bitmap.size() != decoded_.size())
            throw ElfError("guest admission decoded bitmap size changed");
        for (size_t i = 0; i < decoded_.size(); ++i)
            if (decoded_[i] & ~analysis.decoded_bitmap[i])
                throw ElfError("guest admission lost previously decoded code");
    }
    auto next_installed = installed_;
    auto retain_new = [&](auto& sites) {
        sites.erase(
            std::remove_if(
                sites.begin(), sites.end(),
                [&](const auto& site) {
                    auto found = next_installed.find(site.address);
                    if (found != next_installed.end()) {
                        if (found->second.original != site.instruction)
                            throw ElfError("guest admission site changed");
                        return true;
                    }
                    // A newly patched instruction must never belong to a previously
                    // admitted region: another thread may already be executing there.
                    // This is a necessary publication invariant, not proof that every
                    // ingress, signal or direct edge is correctly gated.
                    if (contains_decoded(site.address))
                        throw ElfError("guest admission would patch previously admitted code");
                    next_installed.emplace(site.address, InstalledSite{site.instruction, 0});
                    return false;
                }),
            sites.end());
    };
    retain_new(analysis.sites);
    retain_new(analysis.syscalls);
    retain_new(analysis.indirect_transfers);
    auto patches = plan_guest_admission(elf_, original_, analysis, base_, islands_ + used_,
                                        capacity_ - used_, syscall_, validator_);
    if (patches.size() > (capacity_ - used_) / 80)
        throw ElfError("guest admission island capacity");
    for (const auto& patch : patches) {
        auto found = next_installed.find(patch.guest_address);
        if (found == next_installed.end() || found->second.original != patch.original ||
            found->second.replacement)
            throw ElfError("guest admission patch state mismatch");
        found->second.replacement = patch.replacement;
    }
    // Allocate every new state object before writing code. State swaps after
    // publication are nonthrowing; caller must keep new code unreachable.
    auto next_roots = roots;
    auto next_decoded = std::move(analysis.decoded_bitmap);
    publisher_.publish(patches);
    used_ += patches.size() * 80;
    installed_.swap(next_installed);
    roots_.swap(next_roots);
    decoded_.swap(next_decoded);
}
void AdmissionSession::initialize() {
    Guard guard;
    std::lock_guard lock(mutex_);
    if (initialized_)
        throw ElfError("guest admission already initialized");
    publish({});
    initialized_ = true;
}
bool AdmissionSession::admit(uintptr_t target) {
    Guard guard;
    std::lock_guard lock(mutex_);
    if (!initialized_)
        throw ElfError("guest admission is not initialized");
    if (target < base_ || target - base_ >= elf_.span || (target & 3))
        throw ElfError("guest admission target outside image");
    uint64_t address = elf_.first_page + uint64_t(target - base_);
    if (contains_decoded(address))
        return false;
    auto next_roots = roots_;
    next_roots.push_back(address);
    publish(next_roots);
    if (!contains_decoded(address))
        std::abort(); // internal invariant after publication
    return true;
}
bool AdmissionSession::ready() {
    Guard guard;
    std::lock_guard lock(mutex_);
    return initialized_;
}
IndirectTransfer AdmissionSession::transfer_site(uintptr_t site) {
    Guard guard;
    std::lock_guard lock(mutex_);
    if (!initialized_ || site < base_ || site - base_ >= elf_.span || (site & 3))
        throw ElfError("guest transfer site outside initialized image");
    uint64_t address = elf_.first_page + uint64_t(site - base_);
    auto found = installed_.find(address);
    if (found == installed_.end() || !found->second.replacement ||
        __atomic_load_n(reinterpret_cast<const uint32_t*>(site), __ATOMIC_ACQUIRE) !=
            found->second.replacement)
        throw ElfError("guest transfer site is uninstalled or changed");
    auto transfer = classify_indirect_transfer(address, found->second.original);
    if (transfer.kind == IndirectTransferKind::Unsupported)
        throw ElfError("guest transfer site is not an indirect transfer");
    return transfer;
}
} // namespace shiraz::guest
