// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Ordinary OS account lookup only. Native passwd/group layouts and storage
// never escape; native password fields are neither inspected nor copied.
std::vector<ProviderExport> accounts_exports();
} // namespace shiraz::guest
