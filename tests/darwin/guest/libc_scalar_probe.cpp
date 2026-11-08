// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/libc_scalar.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <cstring>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <unistd.h>

using namespace shiraz::guest;
namespace fs = std::filesystem;
namespace {
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
struct Environment {
  std::optional<std::string> value, absent;
  Environment() {
    if (auto *previous = std::getenv("SHIRAZ_M5_SCALAR_PROBE")) value = previous;
    if (auto *previous = std::getenv("SHIRAZ_M5_SCALAR_ABSENT")) absent = previous;
  }
  ~Environment() {
    if (value) setenv("SHIRAZ_M5_SCALAR_PROBE", value->c_str(), 1);
    else unsetenv("SHIRAZ_M5_SCALAR_PROBE");
    if (absent) setenv("SHIRAZ_M5_SCALAR_ABSENT", absent->c_str(), 1);
    else unsetenv("SHIRAZ_M5_SCALAR_ABSENT");
  }
};
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: libc-scalar-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]);
    Provider libc{"libc.so", {"LIBC"}, libc_scalar_exports(), false};
    libc.exports.push_back(fixed_export("__errno", "LIBC", &guest_errno_location));
    libc.exports.push_back(fixed_export("strcmp", "LIBC", &std::strcmp));
    libc.exports.push_back(fixed_export("memcpy", "LIBC", &std::memcpy));
    libc.exports.push_back(fixed_export("memset", "LIBC", &std::memset));
    libc.exports.push_back(fixed_export("free", "LIBC", &std::free));
    Providers providers;
    providers.add(std::move(libc));
    Loader loader({fixtures}, std::move(providers));
    auto *module = loader.open("liblibc-scalar.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments);
      require(int(result.result_integers[0]) == 73, name);
      require(errno == ELOOP, "host errno preserved");
    };
    errno = ELOOP;
    call("scalar_numeric");
    call("scalar_wide");
    call("scalar_alignment");
    // Set only dedicated known fixture variables; never inspect host secrets.
    Environment cleanup;
    require(!setenv("SHIRAZ_M5_SCALAR_PROBE", "scalar-fixture", 1) &&
                !unsetenv("SHIRAZ_M5_SCALAR_ABSENT"), "fixture environment");
    auto owned = fixtures / ("scalar-owned-" + std::to_string(getpid()));
    require(!fs::exists(owned), "exclusive owned fixture path");
    { std::ofstream file(owned); file << "owned scalar fixture\n"; require(bool(file), "create scalar file"); }
    auto cwd = fs::canonical(fs::current_path()).string();
    auto path = owned.string();
    errno = ELOOP;
    call("scalar_paths", {Argument::integer(int64_t(reinterpret_cast<uintptr_t>(path.c_str())), ArgumentType::Int64),
                           Argument::integer(int64_t(reinterpret_cast<uintptr_t>(cwd.c_str())), ArgumentType::Int64)});
    require(!fs::exists(owned), "guest remove deleted owned fixture file");
    std::cout << "M5_LIBC_SCALAR_PASS numeric=1 wide_utf32=1 wide_unsigned=1 alignment=1 paths=1 environment=1 errno_translation=1 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "libc-scalar-probe: " << error.what() << '\n';
    return 1;
  }
}
