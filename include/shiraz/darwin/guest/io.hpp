// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Per-runtime handles and adapters; no process-global DIR/FILE layout aliases.
std::vector<ProviderExport> file_exports();
Providers file_personality();
} // namespace shiraz::guest
