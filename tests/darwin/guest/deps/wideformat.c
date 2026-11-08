// SPDX-License-Identifier: GPL-3.0-only
#include <wchar.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <locale.h>
#include <errno.h>
_Static_assert(sizeof(wchar_t) == 4 && (wchar_t)-1 > 0, "Android unsigned32 wchar ABI");
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
static int same(const wchar_t *a, const wchar_t *b) {
  while (*a && *a == *b) { ++a; ++b; }
  return *a == *b;
}
static int list(wchar_t *output, size_t size, const wchar_t *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int count = vswprintf(output, size, format, arguments);
  va_end(arguments);
  return count;
}
int run_wideformat(void) {
  wchar_t output[512];
  CHECK(swprintf(output, 512, L"%d/%.2f/%ls/%s", -7, 2.5, L"wide", "ascii") == 18);
  CHECK(same(output, L"-7/2.50/wide/ascii"));
  CHECK(list(output, 512, L"%s/%ls/%lc/%c", "\xce\xb1\xf0\x9f\x98\x80", L"\u0645", (wint_t)0x03a9, 'Z') == 8);
  CHECK(same(output, L"\u03b1\U0001f600/\u0645/\u03a9/Z"));
  CHECK(list(output, 512, L"%6.1s", "\xce\xb1\xf0\x9f\x98\x80") == 6 && same(output, L"     \u03b1"));
  CHECK(list(output, 512, L"%-4.1ls", L"\u03b1\u03b2") == 4 && same(output, L"\u03b1   "));
  const char bytes[3] = {'a', 'b', 'c'};
  const wchar_t wide[2] = {L'x', 0x03b1};
  CHECK(list(output, 512, L"%.3s/%.2ls", bytes, wide) == 6 && same(output, L"abc/x\u03b1"));
  CHECK(list(output, 512, L"%S/%C", L"wide", (wint_t)0x03b1) == 6 && same(output, L"wide/\u03b1"));
  CHECK(list(output, 512, L"%#08x/%+08d/%#.0o/%.0d/%#b", 42u, -42, 0u, 0, 5u) == 26);
  CHECK(same(output, L"0x00002a/-0000042/0//0b101"));
  CHECK(list(output, 512, L"%hhd/%hhu/%hd/%hu", 255, 255, 65535, 65535) == 15);
  CHECK(same(output, L"-1/255/-1/65535"));
  CHECK(list(output, 512, L"%zu/%td/%jd", (size_t)4294967300ULL, (ptrdiff_t)-4294967301LL, (intmax_t)-73) > 0);
  CHECK(same(output, L"4294967300/-4294967301/-73"));
  CHECK(list(output, 512, L"%p/%a", (void *)(uintptr_t)0x1234, 1.5) == 15 && same(output, L"0x1234/0x1.8p+0"));
  CHECK(list(output, 512, L"%d%d%d%d%d%d%d%d%d%d:%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
             1,2,3,4,5,6,7,8,9,10, 1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0) > 0);
  CHECK(same(output, L"12345678910:1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0"));
  wchar_t truncated[5];
  errno = 0;
  CHECK(list(truncated, 5, L"%ls", L"abcdef") == -1 && errno == EOVERFLOW && same(truncated, L"abcd"));
  errno = 0;
  CHECK(list(0, 0, L"x") == -1 && errno == EINVAL);
  CHECK(list(truncated, 1, L"") == 0 && !truncated[0]);
  errno = 0;
  CHECK(list(truncated, 1, L"x") == -1 && errno == EOVERFLOW && !truncated[0]);
  for (size_t i = 0; i < 512; ++i) output[i] = '@';
  CHECK(list(output, 512, L"a%lcb", (wint_t)0) == 3 && output[0] == 'a' && !output[1] && output[2] == '@');
  int marker = 123;
  errno = 0;
  CHECK(list(output, 512, L"%n", &marker) == -1 && errno == ENOTSUP && marker == 123);
  errno = 0;
  CHECK(list(output, 512, L"%2$d", 1, 2) == -1 && errno == ENOTSUP);
  errno = 0;
  CHECK(list(output, 512, L"%Lf", (long double)1) == -1 && errno == ENOTSUP);
  errno = 0;
  CHECK(list(output, 512, L"%s", "\xc0\xaf") == -1 && errno == EILSEQ);
  errno = 0;
  CHECK(list(output, 512, L"%lc", (wint_t)0xd800) == -1 && errno == EILSEQ);
  errno = 0;
  CHECK(list(output, 512, L"%c", 0x80) == -1 && errno == EILSEQ);
  errno = 0;
  CHECK(list(output, 512, L"%262145d", 1) == -1 && errno == EOVERFLOW);
  locale_t c = newlocale(LC_ALL_MASK, "C", 0);
  CHECK(c);
  locale_t previous = uselocale(c);
  CHECK(previous);
  CHECK(list(output, 512, L"%s", "\xce\xb1") == 1 && output[0] == 0x03b1 && !output[1]);
  errno = 0;
  CHECK(list(output, 512, L"%.1s", "\xce\xb1") == -1 && errno == EILSEQ);
  CHECK(uselocale(previous) == c);
  freelocale(c);
  return 0;
}
