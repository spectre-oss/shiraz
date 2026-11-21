// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/admission_plan.hpp"
#include <mutex>
#include <utility>
#include <vector>
namespace shiraz::guest {
// Serializes writers to caller-owned, valid MAP_JIT regions. Before calling,
// the owner must prevent execution of every newly patched site until this
// function returns, maintain mapping lifetime, and exclude all other writers.
// This is not a runtime admission gate, is not signal/reentrancy safe, and
// does not itself authorize any guest target or external entry.
class AdmissionPublisher {
  public:
    AdmissionPublisher(uintptr_t code, size_t code_size, uintptr_t islands, size_t island_size);
    void publish(const std::vector<AdmissionPatch>& patches);

  private:
    uintptr_t code_, islands_;
    size_t code_size_, island_size_;
    std::mutex mutex_;
    std::vector<std::pair<uintptr_t, uintptr_t>> used_islands_;
};
} // namespace shiraz::guest
