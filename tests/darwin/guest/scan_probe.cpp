// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/scan.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <cstring>
#include <iostream>

using namespace shiraz::guest;
int main(int argc, char **argv) {
  try {
    if (argc != 2) throw std::runtime_error("usage: scan-probe fixture-directory");
    Provider libc{"libc.so", {"LIBC"}, scan_exports(), false};
    libc.exports.push_back(fixed_export("__errno", "LIBC", &guest_errno_location));
    libc.exports.push_back(fixed_export("strlen", "LIBC", &std::strlen));
    libc.exports.push_back(fixed_export("strcmp", "LIBC", &std::strcmp));
    libc.exports.push_back(fixed_export("memcmp", "LIBC", &std::memcmp));
    libc.exports.push_back(fixed_export("memcpy", "LIBC", &std::memcpy));
    libc.exports.push_back(fixed_export("memset", "LIBC", &std::memset));
    Providers providers; providers.add(std::move(libc));
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libscan.so");
    for (const char *test : {"scan_scalars", "scan_text", "scan_list", "scan_failures"}) {
      errno = ELOOP;
      int result = int(invoke(reinterpret_cast<void *>(loader.symbol(module, test).address), {}).result_integers[0]);
      if (result != 73) throw std::runtime_error(std::string(test) + " case=" + std::to_string(result));
      if (errno != ELOOP) throw std::runtime_error("scanf changed host errno");
    }
    std::cout << "M5_SCAN_PASS scalar_types=1 gpr_stack_outputs=1 guest_va_list=1 text_scanset=1 suppression=1 count=1 eof_mismatch=1 unsupported_explicit=1 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "scan-probe: " << error.what() << '\n';
    return 1;
  }
}
