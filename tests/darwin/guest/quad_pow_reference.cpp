// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad_pow.hpp"
#include <mpfr.h>
#include <atomic>
#include <cfenv>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <thread>
#include <vector>
using namespace shiraz::guest;
static QuadPowerBits b(uint64_t low, uint64_t high) {
    return {low, high};
}
static void test(const char* name, QuadPowerBits x, QuadPowerBits y, int mode,
                 QuadPowerBits expected, int error, int flags) {
    std::fesetround(mode);
    std::feclearexcept(FE_ALL_EXCEPT);
    std::feraiseexcept(FE_DIVBYZERO);
    errno = EBUSY;
    auto v = quad_power(x, y, mode);
    int observed = std::fetestexcept(FE_ALL_EXCEPT);
    if (v.bits.low != expected.low || v.bits.high != expected.high || v.error != error ||
        v.exceptions != flags || errno != EBUSY || observed != (flags | FE_DIVBYZERO)) {
        std::fprintf(stderr, "FAIL %s got=%016llx:%016llx error=%d flags=%x fenv=%x\n", name,
                     (unsigned long long)v.bits.high, (unsigned long long)v.bits.low, v.error,
                     v.exceptions, observed);
        std::exit(1);
    }
}
int main() {
    auto two = b(0, 0x4000000000000000ULL), three = b(0, 0x4000800000000000ULL),
         half = b(0, 0x3ffe000000000000ULL);
    auto plusone = b(0, 0x3fff000000000000ULL), zero = b(0, 0),
         negativezero = b(0, 0x8000000000000000ULL), inf = b(0, 0x7fff000000000000ULL),
         ninf = b(0, 0xffff000000000000ULL);
    test("exact", two, three, FE_TONEAREST, b(0, 0x4002000000000000ULL), 0, 0);
    test("sqrt nearest", two, half, FE_TONEAREST, b(0xc908b2fb1366ea95ULL, 0x3fff6a09e667f3bcULL),
         0, FE_INEXACT);
    test("sqrt up", two, half, FE_UPWARD, b(0xc908b2fb1366ea96ULL, 0x3fff6a09e667f3bcULL), 0,
         FE_INEXACT);
    test("sqrt down", two, half, FE_DOWNWARD, b(0xc908b2fb1366ea95ULL, 0x3fff6a09e667f3bcULL), 0,
         FE_INEXACT);
    test("subnormal", two, b(0, 0xc00d01b800000000ULL), FE_TONEAREST, b(1, 0), 0, 0);
    test("tiny", two, b(0, 0xc00d01bc00000000ULL), FE_TONEAREST, zero, ERANGE,
         FE_UNDERFLOW | FE_INEXACT);
    test("overflow", two, b(0, 0x400d000000000000ULL), FE_TONEAREST, inf, ERANGE,
         FE_OVERFLOW | FE_INEXACT);
    test("negative zero", negativezero, b(0, 0xc000800000000000ULL), FE_TONEAREST, ninf, ERANGE,
         FE_DIVBYZERO);
    test("negative power exact", b(0, 0xc000000000000000ULL), three, FE_TONEAREST,
         b(0, 0xc002000000000000ULL), 0, 0);
    test("large odd integer exponent", b(0, 0xbfff000000000000ULL), b(1, 0x406f000000000000ULL),
         FE_TONEAREST, b(0, 0xbfff000000000000ULL), 0, 0);
    test("large even integer exponent", b(0, 0xbfff000000000000ULL), b(0, 0x4070000000000000ULL),
         FE_TONEAREST, plusone, 0, 0);
    test("negative power overflow down", b(0, 0xc000000000000000ULL), b(0, 0x400d000400000000ULL),
         FE_DOWNWARD, ninf, ERANGE, FE_OVERFLOW | FE_INEXACT);
    test("negative power overflow up", b(0, 0xc000000000000000ULL), b(0, 0x400d000400000000ULL),
         FE_UPWARD, b(UINT64_MAX, 0xfffeffffffffffffULL), ERANGE, FE_OVERFLOW | FE_INEXACT);
    test("negative half-subnormal down", b(0, 0xc000000000000000ULL), b(0, 0xc00d01bc00000000ULL),
         FE_DOWNWARD, b(1, 0x8000000000000000ULL), ERANGE, FE_UNDERFLOW | FE_INEXACT);
    test("negative half-subnormal nearest", b(0, 0xc000000000000000ULL),
         b(0, 0xc00d01bc00000000ULL), FE_TONEAREST, negativezero, ERANGE,
         FE_UNDERFLOW | FE_INEXACT);
    test("minimum subnormal squared", b(1, 0), two, FE_UPWARD, b(1, 0), ERANGE,
         FE_UNDERFLOW | FE_INEXACT);
    test("minimum subnormal inverse", b(1, 0), b(0, 0xbfff000000000000ULL), FE_TOWARDZERO,
         b(UINT64_MAX, 0x7ffeffffffffffffULL), ERANGE, FE_OVERFLOW | FE_INEXACT);
    test("domain", b(0, 0xc000000000000000ULL), half, FE_TONEAREST, b(0, 0x7fff800000000000ULL),
         EDOM, FE_INVALID);
    test("snan power zero", b(1, 0x7fff000000000000ULL), zero, FE_TONEAREST, plusone, 0, 0);
    test("one power snan", plusone, b(1, 0x7fff000000000000ULL), FE_TONEAREST, plusone, 0, 0);
    test("snan", b(1, 0x7fff000000000000ULL), two, FE_TONEAREST, b(1, 0x7fff800000000000ULL), 0,
         FE_INVALID);
    auto original_min = mpfr_get_emin(), original_max = mpfr_get_emax();
    if (mpfr_set_emin(-1000) || mpfr_set_emax(1000))
        return 2;
    mpfr_clear_flags();
    mpfr_set_divby0();
    auto initial_flags = mpfr_flags_save();
    test("range restoration", two, three, FE_TONEAREST, b(0, 0x4002000000000000ULL), 0, 0);
    if (mpfr_get_emin() != -1000 || mpfr_get_emax() != 1000 || mpfr_flags_save() != initial_flags)
        return 3;
    if (mpfr_set_emin(original_min) || mpfr_set_emax(original_max))
        return 4;
    std::atomic<bool> valid{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i)
        threads.emplace_back([&] {
            if (mpfr_set_emin(-1000) || mpfr_set_emax(1000)) {
                valid = false;
                return;
            }
            for (int n = 0; n < 50; ++n) {
                auto v = quad_power(two, three, FE_TONEAREST);
                if (v.bits.high != 0x4002000000000000ULL || v.bits.low || v.error || v.exceptions ||
                    mpfr_get_emin() != -1000 || mpfr_get_emax() != 1000)
                    valid = false;
            }
        });
    for (auto& thread : threads)
        thread.join();
    if (!valid)
        return 5;
    std::puts(
        "QUAD_POWER_RESEARCH_PASS exact=1 rounding=1 subnormal=1 overflow=1 domain=1 signals=1");
}
