// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/maps_stream.hpp"
namespace shiraz::guest {
// Each export set owns opaque guest FILE identities. Host FILE pointers never
// cross the ABI. Formatting marshals AAPCS64 variadics/va_list separately from
// Darwin, with bounded output; direct private FILE access remains unsupported.
std::vector<ProviderExport> stdio_exports();
Providers stdio_personality(MapsStream::Factory maps = {});
} // namespace shiraz::guest
