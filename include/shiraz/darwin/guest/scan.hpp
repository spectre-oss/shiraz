// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <cstddef>
namespace shiraz::guest {
struct GuestScanError {
    int error;
};
// Validate the complete narrow scanf format before touching a stream or
// consuming guest arguments; returns the number of output pointers.
size_t scan_stream_pointer_count(const char* format);
// Typed guest output pointers are marshalled independently of native varargs.
// Wide, allocating, positional and binary128 conversions fail explicitly.
std::vector<ProviderExport> scan_exports();
} // namespace shiraz::guest
