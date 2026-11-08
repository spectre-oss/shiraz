// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/locale.hpp"
#include <atomic>
#include <filesystem>
#include <iostream>
#include <thread>
#include <unicode/uchar.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: locale-probe fixture-directory");
    auto base = core_personality();
    Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") {
        provider.exports.push_back(fixed_export("strtod", "LIBC", &std::strtod, ErrnoPolicy::Observe));
        auto exports = locale_exports();
        provider.exports.insert(provider.exports.end(), exports.begin(), exports.end());
      }
      providers.add(std::move(provider));
    }
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("liblocale.so");
    errno = ELOOP;
    auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_locale").address), {}).result_integers[0];
    if (result) throw std::runtime_error("guest locale failed at line " + std::to_string(result));
    require(errno == ELOOP, "locale preserves host errno");
    auto select = reinterpret_cast<void *>(loader.symbol(module, "select_locale").address);
    auto clear = reinterpret_cast<void *>(loader.symbol(module, "clear_locale").address);
    std::atomic<unsigned> arrived{0};
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (unsigned i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      auto identity = invoke(select, {Argument::integer(i & 1)}).result_integers[0];
      if (!identity) { passed = false; ++arrived; return; }
      ++arrived;
      while (arrived.load() != 4) std::this_thread::yield();
      for (int j = 0; j < 128; ++j) if (current_locale_utf8() != bool(i & 1)) passed = false;
      invoke(clear, {Argument::integer(identity, ArgumentType::Int64)});
      if (!current_locale_utf8()) passed = false;
    });
    for (auto &thread : threads) thread.join();
    require(passed, "per-thread locale overrides and restored global");
    bool value = false;
    require(!locale_is_utf8(reinterpret_cast<void *>(0x12345), value), "forged locale rejected");
    UVersionInfo version{}; u_getUnicodeVersion(version);
    std::cout << "M5_LOCALE_PASS names=5 masks=1 opaque_handles=1 copied_lconv=1 unicode="
              << unsigned(version[0]) << '.' << unsigned(version[1])
              << " android_wide_locale_ignored=1 high_bit=1 collation_binary=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "locale-probe: " << error.what() << '\n'; return 1;
  }
}
