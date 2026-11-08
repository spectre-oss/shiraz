// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <locale.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
_Static_assert(sizeof(mbstate_t) == 8 && _Alignof(mbstate_t) == 1 &&
               offsetof(mbstate_t, __seq) == 0 && offsetof(mbstate_t, __reserved) == 4,
               "pinned bionic LP64 mbstate ABI");
_Static_assert(sizeof(wchar_t) == 4 && WCHAR_MIN == 0 && WCHAR_MAX == UINT32_MAX,
               "pinned bionic unsigned UTF32 ABI");
int multibyte_decode(void) {
  mbstate_t state = {0};
  wchar_t output = 73;
  memset(state.__reserved, 0xaa, 4);
  if (!mbsinit(&state) || !mbsinit(0) || mbrtowc(&output, "A", 1, &state) != 1 || output != 'A') return 1;
  output = 73;
  if (mbrtowc(&output, "", 0, &state) != 0 || output != 73) return 2;
  if (mbrtowc(&output, "\xf0\x9f", 2, &state) != (size_t)-2 || mbsinit(&state)) return 3;
  if (mbrtowc(&output, "\x92\xa9", 2, &state) != 2 || output != 0x1f4a9 || !mbsinit(&state)) return 4;
  for (int i = 0; i < 4; ++i) if (state.__reserved[i] != 0xaa) return 5;
  const char *invalid[] = {"\xc0\x80", "\xed\xa0\x80", "\xf4\x90\x80\x80", "\xff"};
  const size_t lengths[] = {2, 3, 4, 1};
  for (int i = 0; i < 4; ++i) {
    errno = 0;
    if (mbrtowc(&output, invalid[i], lengths[i], &state) != (size_t)-1 ||
        errno != EILSEQ || !mbsinit(&state)) return 6;
  }
  if (mbrlen("\xd9", 1, &state) != (size_t)-2) return 7;
  errno = 0;
  if (mbrtowc(&output, "A", 1, &state) != (size_t)-1 || errno != EILSEQ || !mbsinit(&state)) return 8;
  state.__seq[3] = 1;
  errno = 0;
  if (mbrlen("A", 1, &state) != (size_t)-1 || errno != EINVAL || !mbsinit(&state)) return 9;
  if (mbtowc(0, 0, 0) != 0 || mbtowc(&output, "\xd9\x84", 2) != 2 || output != 0x644) return 10;
  if (btowc('A') != 'A' || btowc(0xc2) != WEOF || wctob(0x644) != -1 || wctob('A') != 'A') return 11;
  errno = 0;
  if (mbtowc(&output, "\xd9", 1) != -1 || errno != EILSEQ ||
      mbtowc(&output, "\x84", 1) != 1 || output != 0x644) return 12;
  if (mbrlen("\xd9", 1, &state) != (size_t)-2) return 13;
  errno = 0;
  if (mbrtowc(0, 0, 0, &state) != (size_t)-1 || errno != EILSEQ || !mbsinit(&state)) return 14;
  if (mbrtowc(0, 0, 0, &state) != 0) return 15;
  return 73;
}
int multibyte_strings(void) {
  static const char text[] = "A\xd9\x84\xf0\x9f\x92\xa9";
  static const wchar_t wide[] = {'A', 0x644, 0x1f4a9, 0};
  mbstate_t state = {0};
  const char *source = text;
  wchar_t decoded[4] = {0};
  if (mbsnrtowcs(0, &source, sizeof(text), 0, &state) != 3 || source != text || !mbsinit(&state)) return 1;
  if (mbsrtowcs(decoded, &source, 2, &state) != 2 || source != text + 3 || decoded[0] != 'A' || decoded[1] != 0x644) return 2;
  if (mbsrtowcs(decoded + 2, &source, 2, &state) != 1 || source || decoded[2] != 0x1f4a9 || decoded[3]) return 3;
  source = text;
  errno = 0;
  if (mbsnrtowcs(decoded, &source, 2, 4, &state) != (size_t)-1 || errno != EILSEQ || source != text + 2 || !mbsinit(&state)) return 4;
  source = text;
  errno = 0;
  if (mbsnrtowcs(0, &source, 2, 0, &state) != (size_t)-1 || errno != EILSEQ || source != text || !mbsinit(&state)) return 5;
  if (mbrlen("\xd9", 1, &state) != (size_t)-2) return 6;
  if (mbsnrtowcs(decoded, &source, 0, 4, &state) || source != text || !mbsinit(&state)) return 7;
  const wchar_t *input = wide;
  char encoded[8] = {0};
  if (wcsnrtombs(0, &input, 4, 0, &state) != 7 || input != wide) return 8;
  if (wcsrtombs(encoded, &input, 2, &state) != 1 || input != wide + 1 || encoded[0] != 'A') return 9;
  if (wcsrtombs(encoded + 1, &input, 2, &state) != 2 || input != wide + 2) return 10;
  if (wcsrtombs(encoded + 3, &input, 4, &state) != 4 || input != wide + 3) return 11;
  if (wcsrtombs(encoded + 7, &input, 1, &state) || input || memcmp(encoded, text, sizeof(text))) return 12;
  const wchar_t invalid[] = {'A', 0x200000, 0};
  input = invalid;
  errno = 0;
  if (wcsnrtombs(encoded, &input, 3, sizeof(encoded), &state) != (size_t)-1 || errno != EILSEQ || input != invalid + 1) return 13;
  // Pinned encoder accepts this non-Unicode value, but its decoder rejects it.
  if (wcrtomb(encoded, 0xd800, &state) != 3 || memcmp(encoded, "\xed\xa0\x80", 3)) return 14;
  if (wcrtomb(0, 0xffffffff, &state) != 1 || !mbsinit(&state)) return 15;
  input = invalid;
  errno = 0;
  if (wcsnrtombs(0, &input, 3, 0, &state) != (size_t)-1 || errno != EILSEQ || input != invalid) return 16;
  if (mbrlen("\xd9", 1, &state) != (size_t)-2) return 17;
  errno = 0;
  if (wcsnrtombs(encoded, &input, 3, sizeof(encoded), &state) != (size_t)-1 || errno != EILSEQ || !mbsinit(&state) || input != invalid) return 18;
  return 73;
}
int multibyte_locale(void) {
  locale_t plain = newlocale(LC_CTYPE_MASK, "C", 0);
  locale_t utf8 = newlocale(LC_CTYPE_MASK, "C.UTF-8", 0);
  if (!plain || !utf8) return 1;
  locale_t previous = uselocale(plain);
  mbstate_t state = {0};
  wchar_t output = 0;
  int result = 73;
  // Android14's converters remain UTF8 even when the locale advertises 1.
  if (MB_CUR_MAX != 1 || mbrtowc(&output, "\xd9\x84", 2, &state) != 2 || output != 0x644) result = 2;
  uselocale(utf8);
  if (MB_CUR_MAX != 4) result = 3;
  uselocale(previous);
  freelocale(plain); freelocale(utf8);
  return result;
}
int multibyte_thread_stage(int stage) {
  wchar_t output = 0;
  if (!stage) return mbrtowc(&output, "\xd9", 1, 0) == (size_t)-2 ? 73 : 1;
  return mbrtowc(&output, "\x84", 1, 0) == 1 && output == 0x644 ? 73 : 2;
}
