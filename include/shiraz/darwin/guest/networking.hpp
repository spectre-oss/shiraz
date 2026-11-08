// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Explicit address/resolver/message/option adapters; guest structs never alias
// Darwin sockaddr/addrinfo/msghdr/cmsghdr. Resolver results are owned/bounded.
std::vector<ProviderExport> networking_exports();
}
