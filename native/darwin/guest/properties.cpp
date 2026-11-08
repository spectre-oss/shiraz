// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/properties.hpp"
namespace shiraz::guest {
PropertyMap api27_properties() {
  // Matches java/android/os/Build.java's nominal API-27 profile. This is
  // compatibility metadata, not a claim that all API-27 features exist.
  return {{"ro.build.version.sdk", "27"}, {"ro.build.version.preview_sdk", "0"},
          {"ro.build.version.release", "8.1"}, {"ro.build.version.codename", "REL"},
          {"ro.product.cpu.abi", "arm64-v8a"}, {"ro.product.cpu.abilist", "arm64-v8a"},
          {"ro.product.cpu.abilist64", "arm64-v8a"}, {"ro.product.cpu.abilist32", ""}};
}
std::vector<ProviderExport> property_exports(PropertyMap properties) {
  if (properties.size() > 4096) throw ElfError("guest property count limit");
  for (const auto &entry : properties)
    if (entry.first.empty() || entry.first.size() >= 4096 ||
        entry.first.find('\0') != std::string::npos || entry.second.size() >= 92 ||
        entry.second.find('\0') != std::string::npos)
      throw ElfError("invalid guest property name/value");
  auto values = std::make_shared<const PropertyMap>(std::move(properties));
  void *entry = closure([values](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const char *name = cursor.read<const char *>();
    char *output = cursor.read<char *>();
    HostErrno preserve;
    // Bionic requires a PROP_VALUE_MAX-byte output. Missing properties return
    // an empty string and zero; no host environment is consulted.
    if (!name || !output || strnlen(name, 4096) == 4096) {
      if (output) output[0] = 0;
      return_value(capture, -1); return;
    }
    auto found = values->find(name);
    if (found == values->end()) { output[0] = 0; return_value(capture, 0); return; }
    std::memcpy(output, found->second.c_str(), found->second.size() + 1);
    return_value(capture, int(found->second.size()));
  });
  return {{"__system_property_get", "LIBC", reinterpret_cast<uintptr_t>(entry),
            SymbolKind::Function, "properties/explicit-api-profile", false}};
}
}
