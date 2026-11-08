// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/properties.hpp"
#include <cstdio>
#include <stdexcept>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main() {
  try {
    auto exports = property_exports(api27_properties());
    void *entry = reinterpret_cast<void *>(exports.at(0).address);
    char output[96];
    auto get = [&](const char *name) {
      std::memset(output, 0x7a, sizeof(output));
      return int(int32_t(invoke(entry, {Argument::integer(reinterpret_cast<uintptr_t>(name), ArgumentType::Int64),
        Argument::integer(reinterpret_cast<uintptr_t>(output), ArgumentType::Int64)}).result_integers[0]));
    };
    errno = EBUSY; *guest_errno_location() = 73;
    require(get("ro.build.version.sdk") == 2 && !std::strcmp(output, "27"), "nominal SDK profile");
    require(get("ro.product.cpu.abi") == 9 && !std::strcmp(output, "arm64-v8a"), "actual guest ABI");
    require(get("no.such.property") == 0 && output[0] == 0 && output[1] == 0x7a, "missing property semantics");
    require(errno == EBUSY && *guest_errno_location() == 73, "property lookup preserves errno");
    auto custom = property_exports({{"bounded", std::string(91, 'a')}});
    auto result = invoke(reinterpret_cast<void *>(custom.at(0).address),
      {Argument::integer(reinterpret_cast<uintptr_t>("bounded"), ArgumentType::Int64),
       Argument::integer(reinterpret_cast<uintptr_t>(output), ArgumentType::Int64)});
    require(result.result_integers[0] == 91 && output[91] == 0 && output[92] == 0x7a, "PROP_VALUE_MAX boundary");
    unsigned rejected = 0;
    for (auto values : {PropertyMap{{"", "bad"}}, PropertyMap{{"x", std::string(92, 'x')}},
                        PropertyMap{{"x", std::string("a\0b", 3)}}}) {
      try { property_exports(std::move(values)); } catch (const ElfError &) { ++rejected; }
    }
    require(rejected == 3, "invalid property namespaces rejected");
    std::puts("M5_PROPERTIES_PASS profile=27 guest_abi=arm64-v8a missing=empty length=91 rejected=3 errno_preserved=1");
  } catch (const std::exception &error) { std::fprintf(stderr, "properties-probe: %s\n", error.what()); return 1; }
}
