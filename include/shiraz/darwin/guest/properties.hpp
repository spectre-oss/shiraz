// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
using PropertyMap = std::map<std::string, std::string>;
// An explicit guest property namespace, independent of host environment/secrets.
std::vector<ProviderExport> property_exports(PropertyMap properties);
PropertyMap api27_properties();
}
