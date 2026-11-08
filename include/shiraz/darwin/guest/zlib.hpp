// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
// Android LP64 stream fields are marshalled explicitly. Custom allocators run
// through AAPCS64 invocation and must belong to the loaded guest namespace.
// Callers must End every live stream before Loader teardown. Abandoned streams
// retain allocations/budget rather than invoking guest frees after image unmap.
// Aggregate allocation is capped at 1 GiB per provider (256 MiB per stream).
Provider zlib_provider(CodeValidator validate);
} // namespace shiraz::guest
