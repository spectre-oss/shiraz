// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad.hpp"
#include <atomic>
#include <cfenv>
#include <filesystem>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
struct Environment {
    fenv_t saved;
    Environment() {
        if (std::fegetenv(&saved))
            throw std::runtime_error("fenv");
    }
    ~Environment() {
        std::fesetenv(&saved);
    }
};
int main(int argc, char** argv) {
    try {
        require(argc == 2, "fixture directory");
        Environment restore;
        auto providers = core_personality();
        providers.add({"libm.so", {"LIBC"}, {quad_power_export()}, false});
        Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
        auto* module = loader.open("libquad-power.so");
        auto* entry = reinterpret_cast<void*>(loader.symbol(module, "run_quad_power").address);
        const int modes[] = {FE_TONEAREST, FE_TOWARDZERO, FE_UPWARD, FE_DOWNWARD};
        const int raised[] = {0,
                              FE_INEXACT,
                              FE_INVALID,
                              FE_DIVBYZERO,
                              0,
                              FE_INVALID,
                              FE_OVERFLOW | FE_INEXACT,
                              FE_UNDERFLOW | FE_INEXACT,
                              FE_INEXACT};
        for (int mode = 0; mode < 4; ++mode)
            for (int group = 0; group < 9; ++group) {
                require(!std::fesetround(modes[mode]), "set rounding");
                std::feclearexcept(FE_ALL_EXCEPT);
                std::feraiseexcept(group == 3 ? FE_INVALID : FE_DIVBYZERO);
                int prior = std::fetestexcept(FE_ALL_EXCEPT);
                errno = EBUSY;
                auto result = invoke(entry, {Argument::integer(mode), Argument::integer(group)})
                                  .result_integers[0];
                if (result)
                    throw std::runtime_error("guest pow fixture line " + std::to_string(result));
                require(std::fegetround() == modes[mode] &&
                            std::fetestexcept(FE_ALL_EXCEPT) == (prior | raised[group]),
                        "flags and rounding");
                require(errno == EBUSY, "host errno preservation");
            }
        std::atomic<bool> passed{true};
        std::vector<std::thread> threads;
        for (int i = 0; i < 4; ++i)
            threads.emplace_back([&, i] {
                Environment local;
                if (std::fesetround(modes[i]) ||
                    invoke(entry, {Argument::integer(i), Argument::integer(1)}).result_integers[0])
                    passed = false;
            });
        for (auto& thread : threads)
            thread.join();
        require(passed, "concurrent guest calls");
        std::cout << "M5_QUAD_POWER_PASS aapcs_q0_q1=1 round_modes=4 special=1 subnormal=1 flags=1 "
                     "threads=4\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
