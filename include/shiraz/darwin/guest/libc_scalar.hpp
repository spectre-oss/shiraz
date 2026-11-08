// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
// Merge these audited fixed-signature services into the libc.so provider.
// No guest locale handles, FILE layouts, varargs, or long double aliases.
std::vector<ProviderExport> libc_scalar_exports(CodeValidator validate = {});
} // namespace shiraz::guest
