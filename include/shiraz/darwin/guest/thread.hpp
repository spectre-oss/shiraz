// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
using CodeValidator = std::function<bool(const void *)>;
std::vector<ProviderExport> thread_exports(CodeValidator validate);
} // namespace shiraz::guest
