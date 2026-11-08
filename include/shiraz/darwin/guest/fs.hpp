// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Mount counters/layouts are translated explicitly. Filesystems without an
// unambiguous Linux type code (including APFS) return EOPNOTSUPP, not fake magic.
std::vector<ProviderExport> filesystem_exports();
}
