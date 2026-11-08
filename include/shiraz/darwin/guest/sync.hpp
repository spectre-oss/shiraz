// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
std::vector<ProviderExport> sync_exports(CodeValidator validate);
std::vector<ProviderExport> semaphore_exports();
} // namespace shiraz::guest
