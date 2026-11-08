// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <wchar.h>

_Static_assert(sizeof(wchar_t) == 4 && _Alignof(wchar_t) == 4 &&
               WCHAR_MIN == 0 && WCHAR_MAX == UINT32_MAX,
               "bionic arm64 unsigned UTF-32 wchar ABI");
_Static_assert(sizeof(long) == 8 && sizeof(size_t) == 8 && sizeof(void *) == 8,
               "bionic arm64 LP64 scalar ABI");
_Static_assert(_PC_FILESIZEBITS == 0 && _PC_NAME_MAX == 4 && _PC_PATH_MAX == 5 &&
               _PC_CHOWN_RESTRICTED == 14 && _PC_VDISABLE == 16,
               "pinned bionic pathconf selector ABI");

int scalar_numeric(void) {
  char *end = 0;
  const char *narrow = "-12.25x";
  errno = 73;
  if (atoi(" -42") != -42 || atol("9223372036854775807") != LONG_MAX ||
      atoll("-9223372036854775807") != -9223372036854775807LL ||
      atof("2.5") != 2.5 || strtof("2.5x", &end) != 2.5f || *end != 'x') return 1;
  if (strtod(narrow, &end) != -12.25 || end != narrow + 6 || errno != 73) return 2;
  errno = 0;
  (void)strtod("1e9999", &end);
  if (errno != ERANGE || *end) return 3;
  return 73;
}
int scalar_wide(void) {
  const wchar_t text[] = {0x644, 0x627, 0x645, 0};
  wchar_t buffer[4] = {0};
  wchar_t *end = 0;
  const wchar_t *number = L"-12.25x";
  const wchar_t high[] = {UINT32_MAX, 0}, low[] = {0, 0};
  const wchar_t unicode_number[] = {'1', '2', 0x644, 0};
  const wchar_t invalid_number[] = {'1', UINT32_MAX, 0};
  if (wcslen(text) != 3 || wcschr(text, 0x627) != text + 1 ||
      wmemchr(text, 0x645, 3) != text + 2 || wmemchr(text, 1, 3) ||
      wcscmp(text, text) || wmemcmp(high, low, 1) <= 0 ||
      wcscmp(high, low) <= 0 || wmemchr(high, UINT32_MAX, 1) != high) return 1;
  if (wmemmove(buffer, text, 4) != buffer || wmemcmp(buffer, text, 4)) return 2;
  if (wcstol(L" -42!", &end, 10) != -42 || *end != L'!' ||
      wcstoul(L"ff!", &end, 16) != 255 || *end != L'!' ||
      wcstoll(L"-9223372036854775807", &end, 10) != -9223372036854775807LL || *end ||
      wcstoull(L"18446744073709551615", &end, 10) != ULLONG_MAX || *end ||
      wcstof(L"2.5x", &end) != 2.5f || *end != L'x' ||
      wcstod(number, &end) != -12.25 || end != number + 6) return 3;
  errno = 0;
  (void)wcstod(L"1e9999", &end);
  if (errno != ERANGE || *end) return 4;
  if (wcstol(unicode_number, &end, 10) != 12 || end != unicode_number + 2) return 5;
  errno = 0;
  if (wcstod(invalid_number, &end) != 0 || errno != EILSEQ || end != invalid_number) return 6;
  return 73;
}
int scalar_alignment(void) {
  void *pointer = (void *)73;
  errno = 73;
  if (posix_memalign(&pointer, 3, 64) != EINVAL || pointer != (void *)73 || errno != 73)
    return 1;
  if (posix_memalign(&pointer, 128, 257) || !pointer || ((uintptr_t)pointer & 127) || errno != 73)
    return 2;
  ((volatile char *)pointer)[256] = 42;
  free(pointer);
  return 73;
}
int scalar_paths(const char *owned_file, const char *expected_cwd) {
  char small[2];
  char *cwd = getcwd(0, 0);
  char *canonical = realpath(".", 0);
  if (!cwd || !canonical || strcmp(cwd, expected_cwd) || strcmp(canonical, expected_cwd))
    return 1;
  free(cwd); free(canonical);
  errno = 0;
  if (getcwd(small, sizeof(small)) || errno != ERANGE) return 2;
  errno = 0;
  if (isatty(-1) != 0 || errno != EBADF) return 3;
  if (pathconf(".", _PC_NAME_MAX) < 1) return 4;
  errno = 0;
  if (pathconf(".", 0x7fffffff) != -1 || errno != EINVAL) return 5;
  const char *value = getenv("SHIRAZ_M5_SCALAR_PROBE");
  if (!value || strcmp(value, "scalar-fixture")) return 6;
  errno = 73;
  if (getenv("SHIRAZ_M5_SCALAR_ABSENT") || errno != 73) return 7;
  if (remove(owned_file)) return 8;
  errno = 0;
  if (realpath(owned_file, 0) || errno != ENOENT) return 9;
  return 73;
}
