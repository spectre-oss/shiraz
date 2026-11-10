// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
using CodeValidator = std::function<bool(const void *)>;
class Lifecycle;
// Ownerless exports require the caller to keep all callback code resident.
std::vector<ProviderExport> thread_exports(CodeValidator validate, std::shared_ptr<Lifecycle> lifecycle = {});
} // namespace shiraz::guest
