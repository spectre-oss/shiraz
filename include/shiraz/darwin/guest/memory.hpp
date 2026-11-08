// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
class Lifecycle;
class SignalRuntime;
class CleanupRuntime;
std::vector<ProviderExport> memory_exports();
Providers native_personality(CodeValidator validate = {}, std::shared_ptr<Lifecycle> lifecycle = {}, std::shared_ptr<SignalRuntime> signals = {}, std::shared_ptr<CleanupRuntime> cleanup = {});
} // namespace shiraz::guest
