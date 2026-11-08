// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <stdlib.h>
#include <wchar.h>
#include <locale.h>
#include <float.h>
#include <stdint.h>
#include <stddef.h>
#include <errno.h>
_Static_assert(sizeof(long double) == 16 && LDBL_MANT_DIG == 113 && LDBL_MAX_EXP == 16384,
               "guest IEEE754binary128 ABI");
union Quad { long double value; struct { uint64_t low, high; } bits; };
static int equal(long double value, uint64_t low, uint64_t high) {
  volatile union Quad converted; converted.value = value;
  return converted.bits.low == low && converted.bits.high == high;
}
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
#define ZERO27 "000000000000000000000000000"
#define ZERO28 "0000000000000000000000000000"
#define ONES28 "ffffffffffffffffffffffffffff"
// mode0 nearest-even,1 towardzero,2 upward,3 downward; main sets real fenv.
int run_quad(int mode) {
  char *end = 0;
  errno = 73;
  CHECK(equal(strtold("42", &end), 0, UINT64_C(0x4004500000000000)) && !*end && errno == 73);
  CHECK(equal(strtold("-0e999999", &end), 0, UINT64_C(0x8000000000000000)) && !*end && errno == 73);
  CHECK(equal(strtold("0x1." ZERO27 "1p0", &end), 1, UINT64_C(0x3fff000000000000)) && !*end);
  CHECK(equal(strtold("0.1", &end), mode == 1 || mode == 3 ? UINT64_C(0x9999999999999999) : UINT64_C(0x999999999999999a), UINT64_C(0x3ffb999999999999)));
  CHECK(equal(strtold("-0.1", &end), mode == 1 || mode == 2 ? UINT64_C(0x9999999999999999) : UINT64_C(0x999999999999999a), UINT64_C(0xbffb999999999999)));
  CHECK(equal(strtold("0x1." ZERO28 "8p0", &end), mode == 2 ? 1 : 0, UINT64_C(0x3fff000000000000)));
  CHECK(equal(strtold("-0x1." ZERO28 "8p0", &end), mode == 3 ? 1 : 0, UINT64_C(0xbfff000000000000)));
  CHECK(equal(strtold("0x1." ZERO27 "18p0", &end), mode == 0 || mode == 2 ? 2 : 1, UINT64_C(0x3fff000000000000)));
  CHECK(equal(strtold("0x1p-16382", &end), 0, UINT64_C(0x0001000000000000)));
  errno = 0;
  CHECK(equal(strtold("0x1p-16494", &end), 1, 0) && errno == 0);
  errno = 0;
  CHECK(equal(strtold("0x1p-16495", &end), mode == 2 ? 1 : 0, 0) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("-0x1p-16495", &end), mode == 3 ? 1 : 0, UINT64_C(0x8000000000000000)) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("0x3p-16496", &end), mode == 0 || mode == 2 ? 1 : 0, 0) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("0x1." ONES28 "p16383", &end), UINT64_MAX, UINT64_C(0x7ffeffffffffffff)) && errno == 0);
  int positive_infinity = mode == 0 || mode == 2;
  int negative_infinity = mode == 0 || mode == 3;
  errno = 0;
  CHECK(equal(strtold("0x1p16384", &end), positive_infinity ? 0 : UINT64_MAX,
              positive_infinity ? UINT64_C(0x7fff000000000000) : UINT64_C(0x7ffeffffffffffff)) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("-0x1p16384", &end), negative_infinity ? 0 : UINT64_MAX,
              negative_infinity ? UINT64_C(0xffff000000000000) : UINT64_C(0xfffeffffffffffff)) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("1e4933", &end), positive_infinity ? 0 : UINT64_MAX,
              positive_infinity ? UINT64_C(0x7fff000000000000) : UINT64_C(0x7ffeffffffffffff)) && errno == ERANGE);
  errno = 0;
  CHECK(equal(strtold("1e-5000", &end), mode == 2 ? 1 : 0, 0) && errno == ERANGE);
  const char no_conversion[] = "  -no-number";
  errno = 73;
  CHECK(equal(strtold(no_conversion, &end), 0, 0) && end == no_conversion && errno == 73);
  const char exponent[] = "0x1p+";
  CHECK(equal(strtold(exponent, &end), 0, UINT64_C(0x3fff000000000000)) && end == exponent + 3);
  const char suffix[] = " 12.5tail";
  CHECK(equal(strtold(suffix, &end), 0, UINT64_C(0x4002900000000000)) && end == suffix + 5);
  const char nan_payload[] = "nan(payload)!";
  CHECK(equal(strtold(nan_payload, &end), 0, UINT64_C(0x7fff800000000000)) && end == nan_payload + 12);
  const char inf[] = " -INFINITY!";
  CHECK(equal(strtold(inf, &end), 0, UINT64_C(0xffff000000000000)) && end == inf + 10);
  wchar_t *wide_end = 0;
  const wchar_t wide[] = L"\u2003 0x1bp0tail";
  CHECK(equal(wcstold(wide, &wide_end), 0, UINT64_C(0x3fff000000000000)) && wide_end == wide + 5);
  const wchar_t wide_nan[] = L"nan(payload)";
  CHECK(equal(wcstold(wide_nan, &wide_end), 0, UINT64_C(0x7fff800000000000)) && wide_end == wide_nan + 3);
  const wchar_t wide_bad[] = L"\u2003 \u0645";
  CHECK(equal(wcstold(wide_bad, &wide_end), 0, 0) && wide_end == wide_bad);
  locale_t c = newlocale(LC_ALL_MASK, "C", 0);
  CHECK(c && equal(strtold_l("0x1." ZERO27 "1p0", &end, c), 1, UINT64_C(0x3fff000000000000)));
  freelocale(c);
  // Explicit resource-bound failure, never a successful truncated conversion.
  char over_budget[16386];
  for (size_t i = 0; i < sizeof(over_budget) - 1; ++i) over_budget[i] = '1';
  over_budget[sizeof(over_budget) - 1] = 0;
  errno = 0;
  CHECK(equal(strtold(over_budget, &end), 0, 0) && end == over_budget && errno == EOVERFLOW);
  return 0;
}
