// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/loader.hpp"
namespace shiraz::guest {
// Opt-in bounded diagnostic of actual bound pointer slots. Addresses are ELF
// relative, never ASLR pointers. SHA256 is added by the owning Python harness.
// Caller owns namespace serialization and keeps every image resident.
size_t write_binding_evidence(const Loader &, const std::filesystem::path &output,
                             const std::string &scope, const std::vector<std::string> &load_order,
                             const std::vector<int> &load_flags = {});
}
