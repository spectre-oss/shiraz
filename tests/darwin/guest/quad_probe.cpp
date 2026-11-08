// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include <atomic>
#include <cfenv>
#include <filesystem>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
struct Environment {
  fenv_t value;
  Environment() { if (std::fegetenv(&value)) throw std::runtime_error("read host floating environment"); }
  ~Environment() { std::fesetenv(&value); }
};
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: quad-probe fixture-directory");
    Environment restore;
    auto base = core_personality();
    Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") {
        auto locales = locale_exports(), quads = quad_exports();
        provider.exports.insert(provider.exports.end(), locales.begin(), locales.end());
        provider.exports.insert(provider.exports.end(), quads.begin(), quads.end());
      }
      providers.add(std::move(provider));
    }
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("libquad.so");
    auto entry = reinterpret_cast<void *>(loader.symbol(module, "run_quad").address);
    const int modes[] = {FE_TONEAREST, FE_TOWARDZERO, FE_UPWARD, FE_DOWNWARD};
    for (int i = 0; i < 4; ++i) {
      require(!std::fesetround(modes[i]), "select rounding mode");
      std::feclearexcept(FE_ALL_EXCEPT);
      std::feraiseexcept(FE_DIVBYZERO);
      int flags = std::fetestexcept(FE_ALL_EXCEPT);
      errno = ELOOP;
      auto result = invoke(entry, {Argument::integer(i)}).result_integers[0];
      if (result) throw std::runtime_error("guest quad mode " + std::to_string(i) + " failed at line " + std::to_string(result));
      require(errno == ELOOP && std::fegetround() == modes[i] && std::fetestexcept(FE_ALL_EXCEPT) == flags,
              "quad preserves host errno/rounding/exception flags");
    }
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      Environment thread_restore;
      if (std::fesetround(modes[i]) || invoke(entry, {Argument::integer(i)}).result_integers[0]) passed = false;
    });
    for (auto &thread : threads) thread.join();
    require(passed, "independent concurrent binary128 rounding");
    std::cout << "M5_QUAD_PASS precision=113 q0=1 exact_rational=1 rounding_modes=4 subnormal=1 range=1 wide_endptr=1 no_host_float=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "quad-probe: " << error.what() << '\n'; return 1;
  }
}
