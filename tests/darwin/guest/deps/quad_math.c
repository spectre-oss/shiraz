// SPDX-License-Identifier: GPL-3.0-only
#include <math.h>
#include <float.h>
#include <stdint.h>
#include <errno.h>
_Static_assert(sizeof(long double) == 16 && LDBL_MANT_DIG == 113, "binary128 math ABI");
union Quad { long double value; struct { uint64_t low, high; } bits; };
static long double input(uint64_t low, uint64_t high) {
  union Quad value; value.bits.low = low; value.bits.high = high; return value.value;
}
static int result(long double value, uint64_t low, uint64_t high) {
  volatile union Quad bits; bits.value = value; return bits.bits.low == low && bits.bits.high == high;
}
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
// A volatile function pointer prevents compile-time folding of log2l literals.
static long double (*volatile logarithm)(long double) = log2l;
int run_quad_math(int mode, int group) {
  errno = 73;
  if (group == 0) {
    CHECK(result(logarithm(input(0, UINT64_C(0x3fff000000000000))), 0, 0));
    CHECK(result(logarithm(input(0, UINT64_C(0x4000000000000000))), 0, UINT64_C(0x3fff000000000000)));
    CHECK(result(logarithm(input(0, UINT64_C(0x3ffe000000000000))), 0, UINT64_C(0xbfff000000000000)));
    CHECK(result(logarithm(input(0, UINT64_C(0x4002000000000000))), 0, UINT64_C(0x4000800000000000)));
    CHECK(result(logarithm(input(1, 0)), 0, UINT64_C(0xc00d01b800000000))); // log2(2^-16494)=-16494
    CHECK(errno == 73);
  } else if (group == 1) {
    // Independent220-digit Decimal ln-ratio golden generation; main rechecks.
    // Modes: nearest-even, towardzero, upward, downward. Last-bit distinctions
    // below are beyond double/extended80 precision and prove full q0 results.
    uint64_t three = mode == 0 || mode == 2 ? UINT64_C(0x879fa00b120a068c) : UINT64_C(0x879fa00b120a068b);
    CHECK(result(logarithm(input(0, UINT64_C(0x4000800000000000))), three, UINT64_C(0x3fff95c01a39fbd6)));
    uint64_t above = mode == 0 || mode == 2 ? UINT64_C(0xe1777d0ffda0d23a) : UINT64_C(0xe1777d0ffda0d239);
    CHECK(result(logarithm(input(1, UINT64_C(0x3fff000000000000))), above, UINT64_C(0x3f8f71547652b82f)));
    uint64_t below = mode == 0 || mode == 3 ? UINT64_C(0xe1777d0ffda0d23b) : UINT64_C(0xe1777d0ffda0d23a);
    CHECK(result(logarithm(input(UINT64_MAX, UINT64_C(0x3ffeffffffffffff))), below, UINT64_C(0xbf8e71547652b82f)));
    CHECK(result(logarithm(input(UINT64_MAX, UINT64_C(0x0000ffffffffffff))), mode == 3 ? 1 : 0, UINT64_C(0xc00cfff000000000)));
    int upper = mode == 0 || mode == 2;
    CHECK(result(logarithm(input(UINT64_MAX, UINT64_C(0x7ffeffffffffffff))), upper ? 0 : UINT64_MAX,
                 upper ? UINT64_C(0x400d000000000000) : UINT64_C(0x400cffffffffffff)));
    CHECK(errno == 73);
  } else if (group == 2) {
    CHECK(result(logarithm(input(0, 0)), 0, UINT64_C(0xffff000000000000)) && errno == ERANGE);
    CHECK(result(logarithm(input(0, UINT64_C(0x8000000000000000))), 0, UINT64_C(0xffff000000000000)) && errno == ERANGE);
  } else if (group == 3) {
    CHECK(result(logarithm(input(0, UINT64_C(0xbfff000000000000))), 0, UINT64_C(0x7fff800000000000)) && errno == EDOM);
    CHECK(result(logarithm(input(0, UINT64_C(0xffff000000000000))), 0, UINT64_C(0x7fff800000000000)) && errno == EDOM);
  } else if (group == 4) {
    CHECK(result(logarithm(input(0x123, UINT64_C(0xffff800000000000))), 0x123, UINT64_C(0xffff800000000000)) && errno == 73);
  } else if (group == 5) {
    CHECK(result(logarithm(input(1, UINT64_C(0x7fff000000000000))), 1, UINT64_C(0x7fff800000000000)) && errno == 73);
  } else if (group == 6) {
    CHECK(result(logarithm(input(0, UINT64_C(0x7fff000000000000))), 0, UINT64_C(0x7fff000000000000)) && errno == 73);
  } else return -1;
  return 0;
}
