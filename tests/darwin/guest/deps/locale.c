// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <locale.h>
#include <limits.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <wctype.h>
#include <errno.h>
_Static_assert(LC_CTYPE == 0 && LC_ALL == 6 && LC_IDENTIFICATION == 12 && LC_ALL_MASK == 0x1fbf,
               "Android locale category/mask ABI");
_Static_assert(sizeof(struct lconv) == 96 && offsetof(struct lconv, int_frac_digits) == 80,
               "Android LP64 lconv layout");
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
int run_locale(void) {
  CHECK(!strcmp(setlocale(LC_ALL, 0), "C.UTF-8"));
  locale_t c = newlocale(LC_ALL_MASK, "C", 0);
  locale_t utf8 = newlocale(LC_CTYPE_MASK, "C.UTF-8", 0);
  CHECK(c && utf8 && c != utf8);
  CHECK(uselocale(0) == LC_GLOBAL_LOCALE && uselocale(c) == LC_GLOBAL_LOCALE && uselocale(0) == c);
  CHECK(!strcmp(setlocale(LC_ALL, "C.UTF-8"), "C.UTF-8") && uselocale(0) == c);
  // Android14's wide _l routines ignore locale and use Unicode ICU properties.
  CHECK(iswalpha_l(0x03b1, c) && iswalpha_l(0x03b1, utf8));
  CHECK(iswalpha_l(0x0645, c) && iswdigit_l(0x0661, c));
  CHECK(iswspace_l(0x00a0, c) && iswblank_l(0x00a0, utf8));
  CHECK(iswprint_l(0x1f600, c) && iswgraph_l(0x1f600, c) && !iswpunct_l(0x1f600, c));
  CHECK(iswcntrl_l(0x1f, c) && iswpunct_l('!', c) && iswxdigit_l('F', c));
  CHECK(towupper_l(0x03b1, c) == 0x0391 && towlower_l(0x0391, utf8) == 0x03b1);
  CHECK(towlower_l(0xff21, c) == 0xff41);
  const wint_t invalid[] = {WEOF, 0x80000000u, 0x110000u};
  for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i) {
    CHECK(!iswalpha_l(invalid[i], c) && !iswdigit_l(invalid[i], c) && !iswprint_l(invalid[i], c));
    CHECK(towupper_l(invalid[i], c) == invalid[i] && towlower_l(invalid[i], utf8) == invalid[i]);
  }
  CHECK(strcoll_l("z", "ä", utf8) < 0 && strcoll("z", "ä") < 0);
  char transformed[4];
  CHECK(strxfrm_l(transformed, "abcdef", 4, utf8) == 6 && !strcmp(transformed, "abc"));
  CHECK(strxfrm_l(0, "abcdef", 0, c) == 6);
  const wchar_t high[] = {(wchar_t)0x80000000u, 0}, low[] = {L'z', 0};
  CHECK(wcscoll_l(high, low, c) > 0 && wcscoll(high, low) > 0);
  wchar_t wide[3];
  CHECK(wcsxfrm_l(wide, L"abcd", 3, utf8) == 4 && wide[0] == L'a' && wide[1] == L'b' && !wide[2]);
  CHECK(wcsxfrm(wide, L"abcd", 3) == 4);
  char *end = 0;
  CHECK(strtoll_l("-4294967301x", &end, 10, c) == -4294967301LL && *end == 'x');
  CHECK(strtoull_l("ffffffffffffffff", &end, 16, utf8) == 18446744073709551615ULL && !*end);
  CHECK(strtod_l("1.25x", &end, utf8) == 1.25 && *end == 'x');
  CHECK(strtod_l("1,25", &end, c) == 1 && *end == ',');
  errno = 0;
  CHECK(strtoll_l("999999999999999999999999999", &end, 10, c) == LLONG_MAX && errno == ERANGE);
  struct lconv *conventions = localeconv();
  CHECK(conventions && !strcmp(conventions->decimal_point, ".") && !*conventions->thousands_sep);
  CHECK((unsigned char)conventions->frac_digits == (unsigned char)CHAR_MAX &&
        (unsigned char)conventions->int_n_sign_posn == (unsigned char)CHAR_MAX);
  locale_t duplicate = duplocale(c);
  CHECK(duplicate && duplicate != c);
  CHECK(uselocale(LC_GLOBAL_LOCALE) == c);
  freelocale(c); freelocale(utf8); freelocale(duplicate);
  errno = 0;
  CHECK(!newlocale(1 << LC_ALL, "C", 0) && errno == EINVAL);
  errno = 0;
  CHECK(!newlocale(LC_ALL_MASK, "fa_IR.UTF-8", 0) && errno == ENOENT);
  errno = 0;
  CHECK(!uselocale(c) && errno == EINVAL);
  errno = 0;
  CHECK(!setlocale(13, "C") && errno == EINVAL);
  CHECK(!strcmp(setlocale(LC_ALL, ""), "C.UTF-8"));
  return 0;
}
uintptr_t select_locale(int utf8) {
  locale_t locale = newlocale(LC_ALL_MASK, utf8 ? "C.UTF-8" : "C", 0);
  if (!locale || !uselocale(locale)) return 0;
  return (uintptr_t)locale;
}
void clear_locale(uintptr_t locale) {
  uselocale(LC_GLOBAL_LOCALE);
  freelocale((locale_t)locale);
}
