// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/maps_stream.hpp"
namespace shiraz::guest {
// Per-runtime handles and adapters; no process-global DIR/FILE layout aliases.
std::vector<ProviderExport> file_exports(MapsStream::Factory maps = {});
Providers file_personality(MapsStream::Factory maps = {});
} // namespace shiraz::guest
