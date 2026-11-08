// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
using UnwrapEnvironment = std::function<void *(void *)>;
Provider bitmap_provider(UnwrapEnvironment unwrap);
Provider android_provider(const std::filesystem::path &directory, UnwrapEnvironment unwrap);
}
