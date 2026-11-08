// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/lifecycle.hpp"
namespace shiraz::guest {
// Partial audited AArch64 Linux syscall ABI, private native logging state,
// owned abort message and explicit process termination. Futex is unsupported.
// Replace core_personality's abort export with this returned abort adapter.
std::vector<ProviderExport> process_exports(std::shared_ptr<Lifecycle> lifecycle = {});
} // namespace shiraz::guest
