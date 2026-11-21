// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <float.h>
#include <math.h>
#include <stdint.h>
_Static_assert(sizeof(long double) == 16 && LDBL_MANT_DIG == 113, "AArch64 binary128 ABI");
_Static_assert(math_errhandling == MATH_ERREXCEPT, "pinned bionic math error policy");
union QuadPower {
    long double value;
    struct {
        uint64_t low, high;
    } bits;
};
static long double input(uint64_t low, uint64_t high) {
    union QuadPower value;
    value.bits.low = low;
    value.bits.high = high;
    return value.value;
}
static int same(long double value, uint64_t low, uint64_t high) {
    volatile union QuadPower result;
    result.value = value;
    return result.bits.low == low && result.bits.high == high;
}
#define CHECK(X)                                                                                   \
    do {                                                                                           \
        if (!(X))                                                                                  \
            return __LINE__;                                                                       \
    } while (0)
static long double (*volatile power)(long double, long double) = powl;
int run_quad_power(int mode, int group) {
    long double two = input(0, UINT64_C(0x4000000000000000));
    long double three = input(0, UINT64_C(0x4000800000000000));
    long double half = input(0, UINT64_C(0x3ffe000000000000));
    errno = 73;
    if (group == 0) {
        CHECK(same(power(two, three), 0, UINT64_C(0x4002000000000000)));
        CHECK(same(power(input(0, UINT64_C(0xc000000000000000)), three), 0,
                   UINT64_C(0xc002000000000000)));
        CHECK(same(power(two, input(0, UINT64_C(0xc00d01b800000000))), 1, 0));
        CHECK(errno == 73);
    } else if (group == 1) {
        uint64_t low = mode == 2 ? UINT64_C(0xc908b2fb1366ea96) : UINT64_C(0xc908b2fb1366ea95);
        CHECK(same(power(two, half), low, UINT64_C(0x3fff6a09e667f3bc)) && errno == 73);
    } else if (group == 2) {
        CHECK(same(power(input(0, UINT64_C(0xc000000000000000)), half), 0,
                   UINT64_C(0x7fff800000000000)) &&
              errno == 73);
    } else if (group == 3) {
        CHECK(same(power(input(0, UINT64_C(0x8000000000000000)),
                         input(0, UINT64_C(0xc000800000000000))),
                   0, UINT64_C(0xffff000000000000)) &&
              errno == 73);
    } else if (group == 4) {
        CHECK(same(power(input(1, UINT64_C(0x7fff000000000000)), input(0, 0)), 0,
                   UINT64_C(0x3fff000000000000)) &&
              errno == 73);
    } else if (group == 5) {
        CHECK(same(power(input(1, UINT64_C(0x7fff000000000000)), two), 1,
                   UINT64_C(0x7fff800000000000)) &&
              errno == 73);
    } else if (group == 6) {
        long double huge = input(0, UINT64_C(0x400d000000000000));
        uint64_t high =
            (mode == 0 || mode == 2) ? UINT64_C(0x7fff000000000000) : UINT64_C(0x7ffeffffffffffff);
        uint64_t low = (mode == 0 || mode == 2) ? 0 : UINT64_MAX;
        CHECK(same(power(two, huge), low, high) && errno == 73);
    } else if (group == 7) {
        long double tiny = input(0, UINT64_C(0xc00d01bc00000000));
        CHECK(same(power(two, tiny), mode == 2 ? 1 : 0, 0) && errno == 73);
    } else if (group == 8) {
        long double above_one = input(1, UINT64_C(0x3fff000000000000));
        CHECK(same(power(above_one, two), mode == 2 ? 3 : 2, UINT64_C(0x3fff000000000000)) &&
              errno == 73);
    } else
        return -1;
    return 0;
}
