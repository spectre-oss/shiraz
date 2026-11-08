// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Android's small bionic libstdc++.so, not libc++abi. Ordinary allocation
// failure aborts as bionic does; guest std::bad_alloc/throwing-runtime semantics
// remain the responsibility of the real guest C++ runtime.
Provider cxx_provider();
} // namespace shiraz::guest
