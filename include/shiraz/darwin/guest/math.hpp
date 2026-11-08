// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Explicit scalar libm ABI. Binary128 long double exports remain unsupported.
Provider math_provider();
}
