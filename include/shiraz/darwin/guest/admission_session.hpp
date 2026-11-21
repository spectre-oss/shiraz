// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/admission_publish.hpp"
#include <map>
#include <mutex>
namespace shiraz::guest {
// Experimental single-image admission coordinator for a closed fixture. The
// owner must intercept every external entry and every indirect transfer,
// gate initial execution until initialize returns, exclude other code writers,
// keep the mapping alive, and drain callers before destruction. It provides no
// host/cross-image policy, signal safety, unwind proof or APK authorization.
class AdmissionSession {
  public:
    AdmissionSession(Elf elf, std::vector<uint8_t> original, uintptr_t image_base,
                     uintptr_t islands, size_t island_capacity, uintptr_t syscall_dispatcher,
                     uintptr_t transfer_validator);
    void initialize();
    // Returns true only if this call admitted a new target. Host targets and
    // addresses outside this image are rejected; the owner handles those.
    bool admit(uintptr_t actual_target);
    // Verify that site is an installed, unchanged indirect-transfer patch and
    // return its original transfer kind/register. No target is authorized here.
    IndirectTransfer transfer_site(uintptr_t site);
    uintptr_t image_base() const noexcept {
        return base_;
    }
    uint64_t image_span() const noexcept {
        return elf_.span;
    }
    bool ready();

  private:
    struct InstalledSite {
        uint32_t original, replacement;
    };
    void publish(const std::vector<uint64_t>& roots);
    bool contains_decoded(uint64_t address) const;
    Elf elf_;
    std::vector<uint8_t> original_;
    uintptr_t base_, islands_, syscall_, validator_;
    size_t capacity_, used_ = 0;
    AdmissionPublisher publisher_;
    std::mutex mutex_;
    bool initialized_ = false;
    std::vector<uint64_t> roots_;
    std::vector<uint8_t> decoded_;
    std::map<uint64_t, InstalledSite> installed_;
};
} // namespace shiraz::guest
