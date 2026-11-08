// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/wideformat.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include <atomic>
#include <filesystem>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: wideformat-probe fixture-directory");
    auto base = core_personality();
    Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") {
        auto locale = locale_exports(), wide = wideformat_exports();
        provider.exports.insert(provider.exports.end(), locale.begin(), locale.end());
        provider.exports.insert(provider.exports.end(), wide.begin(), wide.end());
      }
      providers.add(std::move(provider));
    }
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("libwideformat.so");
    errno = ELOOP;
    auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_wideformat").address), {}).result_integers[0];
    if (result) throw std::runtime_error("guest wide formatting failed at line " + std::to_string(result));
    require(errno == ELOOP, "wide formatting preserves host errno");
    auto *libc = loader.link("libc.so");
    auto entry = reinterpret_cast<void *>(loader.symbol(libc, "swprintf").address);
    auto pointer = [](const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); };
    const uint32_t format[] = {'%', 'd', '/', '%', '.', '1', 'f', '/', '%', 'l', 's', 0};
    const uint32_t text[] = {0x03b1, 0x1f600, 0};
    std::atomic<bool> passed{true};
    std::vector<std::thread> workers;
    for (int i = 0; i < 4; ++i) workers.emplace_back([&, i] {
      for (int j = 0; j < 32; ++j) {
        uint32_t output[64];
        auto count = invoke(entry, {pointer(output), Argument::integer(64, ArgumentType::Int64), pointer(format),
            Argument::integer(i), Argument::real(2.5), pointer(text)}).result_integers[0];
        if (count != 8 || output[0] != uint32_t('0' + i) || output[1] != '/' || output[2] != '2' || output[3] != '.' ||
            output[4] != '5' || output[5] != '/' || output[6] != text[0] || output[7] != text[1] || output[8]) passed = false;
      }
    });
    for (auto &worker : workers) worker.join();
    require(passed, "concurrent unsigned32 variadic formatting");
    std::cout << "M5_WIDEFORMAT_PASS swprintf=1 vswprintf=1 wchar_unsigned32=1 utf8_strings=1 va_list_overflow=1 truncation_errno=1 android_nul_quirk=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "wideformat-probe: " << error.what() << '\n'; return 1;
  }
}
