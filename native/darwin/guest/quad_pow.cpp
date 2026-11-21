// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad_pow.hpp"
#include <mpfr.h>
#include <cfenv>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <optional>

namespace shiraz::guest {
namespace {
constexpr uint64_t sign_mask = UINT64_C(0x8000000000000000);
constexpr uint64_t fraction_mask = UINT64_C(0x0000ffffffffffff);
constexpr uint64_t exponent_mask = UINT64_C(0x7fff000000000000);
constexpr uint64_t quiet_mask = UINT64_C(0x0000800000000000);
constexpr QuadPowerBits one{0, UINT64_C(0x3fff000000000000)};
constexpr QuadPowerBits nan{0, UINT64_C(0x7fff800000000000)};
bool zero(QuadPowerBits value) {
    return !(value.high & ~sign_mask) && !value.low;
}
bool infinity(QuadPowerBits value) {
    return (value.high & ~sign_mask) == exponent_mask && !value.low;
}
bool is_nan(QuadPowerBits value) {
    return (value.high & exponent_mask) == exponent_mask &&
           ((value.high & fraction_mask) || value.low);
}
bool signaling(QuadPowerBits value) {
    return is_nan(value) && !(value.high & quiet_mask);
}
bool equals(QuadPowerBits a, QuadPowerBits b) {
    return a.low == b.low && a.high == b.high;
}
using U128 = unsigned __int128;
// Power-of-two bases and integral exponents have an exact integer exponent.
// This path is fixed-cost in the binary128 input size; it does not enter
// MPFR's adaptive power refinement. Other inputs use the existing engine.
std::optional<QuadPowerResult> power_of_two(QuadPowerBits x, QuadPowerBits y, int mode) {
    unsigned xf = unsigned((x.high >> 48) & 0x7fff);
    if (xf == 0x7fff)
        return {};
    U128 fraction = (U128(x.high & fraction_mask) << 64) | x.low;
    int base_exponent;
    if (xf) {
        if (fraction)
            return {};
        base_exponent = int(xf) - 16383;
    } else {
        if (!fraction || (fraction & (fraction - 1)))
            return {};
        int bit = 0;
        while ((fraction >>= 1))
            ++bit;
        base_exponent = bit - 16494;
    }
    unsigned yf = unsigned((y.high >> 48) & 0x7fff);
    if (!yf || yf == 0x7fff)
        return {};
    int exponent = int(yf) - 16383;
    if (exponent < 0)
        return {};
    U128 significand = (U128(1) << 112) | (U128(y.high & fraction_mask) << 64) | y.low;
    bool odd = false;
    unsigned magnitude = 32768; // Already beyond every binary128 result boundary.
    if (exponent < 112) {
        unsigned shift = unsigned(112 - exponent);
        if (significand & ((U128(1) << shift) - 1))
            return {};
        U128 integer = significand >> shift;
        odd = (integer & 1) != 0;
        if (integer < magnitude)
            magnitude = unsigned(integer);
    } else {
        odd = exponent == 112 && (significand & 1);
    }
    bool negative = (x.high & sign_mask) && odd;
    int result_exponent = base_exponent * int(magnitude);
    if (y.high & sign_mask)
        result_exponent = -result_exponent;
    uint64_t sign = negative ? sign_mask : 0;
    bool outward = (mode == FE_UPWARD && !negative) || (mode == FE_DOWNWARD && negative);
    if (result_exponent > 16383) {
        QuadPowerBits result = (mode == FE_TONEAREST || outward)
                                   ? QuadPowerBits{0, sign | exponent_mask}
                                   : QuadPowerBits{UINT64_MAX, sign | UINT64_C(0x7ffeffffffffffff)};
        return QuadPowerResult{result, ERANGE, FE_OVERFLOW | FE_INEXACT};
    }
    if (result_exponent < -16494) {
        // At exactly half the minimum subnormal, ties-to-even produces zero.
        return QuadPowerResult{{outward ? 1u : 0u, sign}, ERANGE, FE_UNDERFLOW | FE_INEXACT};
    }
    if (result_exponent >= -16382)
        return QuadPowerResult{{0, sign | (uint64_t(result_exponent + 16383) << 48)}, 0, 0};
    U128 bits = U128(1) << unsigned(result_exponent + 16494);
    return QuadPowerResult{{uint64_t(bits), sign | uint64_t(bits >> 64)}, 0, 0};
}
struct LocalCache {
    ~LocalCache() {
        int saved_errno = errno;
        fenv_t saved{};
        bool valid = std::fegetenv(&saved) == 0;
        mpfr_free_cache2(MPFR_FREE_LOCAL_CACHE);
        if (valid)
            std::fesetenv(&saved);
        errno = saved_errno;
    }
};
thread_local LocalCache local_cache;
struct Environment {
    int saved_errno = errno;
    mpfr_exp_t minimum = mpfr_get_emin(), maximum = mpfr_get_emax();
    mpfr_flags_t flags = mpfr_flags_save();
    fenv_t floating{};
    bool valid = std::fegetenv(&floating) == 0 && mpfr_buildopt_tls_p();
    int exceptions = 0;
    ~Environment() {
        if (mpfr_set_emin(mpfr_get_emin_min()) || mpfr_set_emax(mpfr_get_emax_max()) ||
            mpfr_set_emin(minimum) || mpfr_set_emax(maximum))
            std::abort();
        mpfr_flags_restore(flags, MPFR_FLAGS_ALL);
        if (valid) {
            std::fesetenv(&floating);
            if (exceptions)
                std::feraiseexcept(exceptions);
        }
        errno = saved_errno;
    }
    bool set_range() {
        return !mpfr_set_emin(mpfr_get_emin_min()) && !mpfr_set_emax(mpfr_get_emax_max()) &&
               !mpfr_set_emin(-16493) && !mpfr_set_emax(16384);
    }
};
struct Number {
    mpfr_t value;
    Number() {
        mpfr_init2(value, 113);
    }
    ~Number() {
        mpfr_clear(value);
    }
    Number(const Number&) = delete;
    Number& operator=(const Number&) = delete;
};
struct Integer {
    mpz_t value;
    Integer() {
        mpz_init(value);
    }
    ~Integer() {
        mpz_clear(value);
    }
    Integer(const Integer&) = delete;
    Integer& operator=(const Integer&) = delete;
};
void unpack(mpfr_ptr output, QuadPowerBits input) {
    bool negative = input.high & sign_mask;
    unsigned field = unsigned((input.high >> 48) & 0x7fff);
    if (field == 0x7fff) {
        mpfr_set_inf(output, negative ? -1 : 1);
        return;
    }
    if (zero(input)) {
        mpfr_set_zero(output, negative ? -1 : 1);
        return;
    }
    uint64_t words[2] = {input.low, input.high & fraction_mask};
    Integer magnitude;
    mpz_import(magnitude.value, 2, -1, sizeof(uint64_t), 0, 0, words);
    if (field)
        mpz_setbit(magnitude.value, 112);
    if (negative)
        mpz_neg(magnitude.value, magnitude.value);
    int exponent = field ? int(field) - 16383 - 112 : -16494;
    if (mpfr_set_z_2exp(output, magnitude.value, exponent, MPFR_RNDN) != 0)
        std::abort();
}
QuadPowerBits pack(mpfr_srcptr value) {
    const uint64_t sign = mpfr_signbit(value) ? sign_mask : 0;
    if (mpfr_nan_p(value))
        return nan;
    if (mpfr_inf_p(value))
        return {0, sign | exponent_mask};
    if (mpfr_zero_p(value))
        return {0, sign};
    const auto exponent = mpfr_get_exp(value) - 1;
    Integer magnitude;
    auto shift = mpfr_get_z_2exp(magnitude.value, value);
    mpz_abs(magnitude.value, magnitude.value);
    uint64_t field = 0;
    if (exponent >= -16382) {
        if (exponent > 16383)
            std::abort();
        field = uint64_t(exponent + 16383) << 48;
        mpz_clrbit(magnitude.value, 112);
    } else {
        auto amount = shift + 16494;
        if (amount >= 0)
            mpz_mul_2exp(magnitude.value, magnitude.value, mp_bitcnt_t(amount));
        else
            mpz_tdiv_q_2exp(magnitude.value, magnitude.value, mp_bitcnt_t(-amount));
    }
    if (mpz_sizeinbase(magnitude.value, 2) > 112)
        std::abort();
    uint64_t words[2] = {0, 0};
    size_t count = 0;
    mpz_export(words, &count, -1, sizeof(uint64_t), 0, 0, magnitude.value);
    if (count > 2)
        std::abort();
    return {words[0], sign | field | words[1]};
}
} // namespace
QuadPowerResult quad_power(QuadPowerBits x, QuadPowerBits y, int rounding) {
    (void)local_cache;
    Environment environment;
    QuadPowerResult out{nan, EINVAL, FE_INVALID};
    auto finish = [&](QuadPowerResult value) {
        environment.exceptions = value.exceptions;
        return value;
    };
    mpfr_rnd_t mode;
    if (rounding == FE_TONEAREST)
        mode = MPFR_RNDN;
    else if (rounding == FE_TOWARDZERO)
        mode = MPFR_RNDZ;
    else if (rounding == FE_UPWARD)
        mode = MPFR_RNDU;
    else if (rounding == FE_DOWNWARD)
        mode = MPFR_RNDD;
    else
        return finish(out);
    if (!environment.valid || !environment.set_range())
        return finish(out);
    if (zero(y) || equals(x, one) || (equals(x, {0, one.high | sign_mask}) && infinity(y)))
        return finish({one, 0, 0});
    if (is_nan(x) || is_nan(y)) {
        auto selected = signaling(x) ? x : signaling(y) ? y : is_nan(x) ? x : y;
        selected.high |= quiet_mask;
        return finish({selected, 0, (signaling(x) || signaling(y)) ? FE_INVALID : 0});
    }
    if (auto exact = power_of_two(x, y, rounding))
        return finish(*exact);
    Number a, b, result;
    unpack(a.value, x);
    unpack(b.value, y);
    mpfr_clear_flags();
    int ternary = mpfr_pow(result.value, a.value, b.value, mode);
    (void)mpfr_subnormalize(result.value, ternary, mode);
    bool invalid = mpfr_nanflag_p(), divide = mpfr_divby0_p(), overflow = mpfr_overflow_p(),
         underflow = mpfr_underflow_p(), inexact = mpfr_inexflag_p();
    int errors = 0, exceptions = 0;
    if (invalid) {
        errors = EDOM;
        exceptions |= FE_INVALID;
    }
    if (divide) {
        errors = ERANGE;
        exceptions |= FE_DIVBYZERO;
    }
    if (overflow) {
        errors = ERANGE;
        exceptions |= FE_OVERFLOW;
    }
    if (underflow && inexact) {
        errors = ERANGE;
        exceptions |= FE_UNDERFLOW;
    }
    if (inexact)
        exceptions |= FE_INEXACT;
    return finish({pack(result.value), errors, exceptions});
}
} // namespace shiraz::guest
