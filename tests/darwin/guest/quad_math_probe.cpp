// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad.hpp"
#include <atomic>
#include <cfenv>
#include <filesystem>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
struct Environment {
  fenv_t value;
  Environment() { if (std::fegetenv(&value)) throw std::runtime_error("read host fenv"); }
  ~Environment() { std::fesetenv(&value); }
};
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: quad-math-probe fixture-directory");
    Environment restore;
    auto providers = core_personality();
    providers.add({"libm.so", {"LIBC"}, {quad_log2_export()}, false});
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("libquad-math.so");
    auto entry = reinterpret_cast<void *>(loader.symbol(module, "run_quad_math").address);
    const int modes[] = {FE_TONEAREST, FE_TOWARDZERO, FE_UPWARD, FE_DOWNWARD};
    const int raised[] = {0, FE_INEXACT, FE_DIVBYZERO, FE_INVALID, 0, FE_INVALID, 0};
    for (int mode = 0; mode < 4; ++mode) for (int group = 0; group < 7; ++group) {
      require(!std::fesetround(modes[mode]), "select actual math rounding mode");
      std::feclearexcept(FE_ALL_EXCEPT);
      std::feraiseexcept(group == 2 ? FE_INVALID : FE_DIVBYZERO);
      int baseline = std::fetestexcept(FE_ALL_EXCEPT);
      errno = ELOOP;
      auto result = invoke(entry, {Argument::integer(mode), Argument::integer(group)}).result_integers[0];
      if (result) throw std::runtime_error("guest quad math mode " + std::to_string(mode) + " group " + std::to_string(group) + " failed at line " + std::to_string(result));
      require(std::fegetround() == modes[mode] && std::fetestexcept(FE_ALL_EXCEPT) == (baseline | raised[group]),
              "math preserves rounding/existing flags and raises required exceptions");
      require(errno == ELOOP, "math preserves host errno");
    }
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      Environment local;
      if (std::fesetround(modes[i]) || invoke(entry, {Argument::integer(i), Argument::integer(1)}).result_integers[0]) passed = false;
    });
    for (auto &thread : threads) thread.join();
    require(passed, "concurrent interval refinement/cache and rounding");
    std::cout << "M5_QUAD_MATH_PASS log2l=1 q0_input_output=1 interval_proof=1 rounding_modes=4 special_values=1 flags=1 subnormal_input=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "quad-math-probe: " << error.what() << '\n'; return 1;
  }
}
