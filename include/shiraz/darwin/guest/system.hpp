// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Linux selector numbers are translated explicitly. No Linux selector or
// syscall number is forwarded into Darwin, and no synthetic ELF/vDSO is exposed.
std::vector<ProviderExport> system_exports();
} // namespace shiraz::guest
