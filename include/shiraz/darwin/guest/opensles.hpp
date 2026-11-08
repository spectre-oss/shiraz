// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
namespace shiraz::guest {
Provider opensles_provider(CodeValidator validate,std::function<void()> &shutdown);
}
