// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Android unsigned32 wchar destinations; neither host wchar_t nor va_list is
// aliased. Bounded real swprintf/vswprintf; unsupported formats return errors.
std::vector<ProviderExport> wideformat_exports();
}
