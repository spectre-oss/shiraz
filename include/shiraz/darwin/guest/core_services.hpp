// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
std::vector<ProviderExport> core_service_exports(CodeValidator validate = {});
}
