// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
[[noreturn]] void fortify_failure(const char *operation);
std::vector<ProviderExport> fortify_exports();
}
