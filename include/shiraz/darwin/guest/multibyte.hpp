// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Android arm64 unsigned UTF-32 / eight-byte mbstate adapters. No native
// mbstate_t or wchar_t aliases. Merge into libc.so's explicit provider.
std::vector<ProviderExport> multibyte_exports();
} // namespace shiraz::guest
