// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Typed guest output pointers are marshalled independently of native varargs.
// Wide, allocating, positional and binary128 conversions fail explicitly.
std::vector<ProviderExport> scan_exports();
} // namespace shiraz::guest
