// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
class Lifecycle;
using UnwrapEnvironment = std::function<void*(void*)>;
Provider bitmap_provider(UnwrapEnvironment unwrap);
Provider android_provider(const std::filesystem::path& directory, UnwrapEnvironment unwrap,
                          std::function<bool(const void*)> validate,
                          std::shared_ptr<Lifecycle> lifecycle = {},
                          std::function<void()>* shutdown_looper = nullptr);
} // namespace shiraz::guest
