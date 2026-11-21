// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
namespace shiraz::guest {
struct QuadPowerBits {
    uint64_t low, high;
};
struct QuadPowerResult {
    QuadPowerBits bits;
    int error, exceptions;
};
// Experimental pure binary128 operation. Not yet registered with the guest
// personality: the private math dependency and lifecycle require validation.
QuadPowerResult quad_power(QuadPowerBits base, QuadPowerBits exponent, int rounding);
} // namespace shiraz::guest
