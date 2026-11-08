// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Direct assembly entry points: a closure wrapper would save its own native
// context instead of the guest context. Internal signal masks are real native
// pthread snapshots inside the opaque 256-byte guest jump buffer.
std::vector<ProviderExport> jump_exports();
} // namespace shiraz::guest
