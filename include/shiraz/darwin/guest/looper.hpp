// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
class Lifecycle;
// Without a lifecycle, the caller must keep callback code resident.
std::vector<ProviderExport> looper_exports(std::function<bool(const void*)> validate,
                                           std::shared_ptr<Lifecycle> lifecycle = {},
                                           std::function<void()>* shutdown = nullptr);
} // namespace shiraz::guest
