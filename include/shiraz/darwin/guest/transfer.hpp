// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Bounded Linux LP64 sendfile/sendfile64 with an explicit 64-bit input
// offset. Null-offset nonempty transfers are explicitly ENOSYS until all
// read/lseek/dup paths share an open-file-description cursor registry.
std::vector<ProviderExport> transfer_exports();
} // namespace shiraz::guest
