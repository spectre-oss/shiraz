// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <inttypes.h>
#include <stddef.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
static int list_scan(const char *input, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vsscanf(input, format, arguments);
  va_end(arguments);
  return result;
}
int scan_scalars(void) {
  signed char byte = 0;
  short half = 0;
  int integer = 0, count = -1;
  long word = 0;
  long long wide = 0;
  uintmax_t maximum = 0;
  size_t size = 0;
  ptrdiff_t difference = 0;
  void *pointer = 0;
  float small = 0;
  double real = 0;
  const char *text = "-5 -123 42 -999 123456789 99 101 -22 0x1234 2.5 -12.25";
  errno = 73;
  int result = sscanf(text, "%hhd %hd %d %ld %lld %ju %zu %td %p %f %lf%n",
                      &byte, &half, &integer, &word, &wide, &maximum, &size,
                      &difference, &pointer, &small, &real, &count);
  if (result != 11 || byte != -5 || half != -123 || integer != 42 || word != -999 ||
      wide != 123456789 || maximum != 99 || size != 101 || difference != -22 ||
      pointer != (void *)0x1234 || small != 2.5f || real != -12.25 ||
      count != (int)strlen(text) || errno != 73) return 1;
  unsigned hex = 0, octal = 0;
  int automatic = 0;
  if (sscanf("ff 17 0x20", "%x %o %i", &hex, &octal, &automatic) != 3 ||
      hex != 255 || octal != 15 || automatic != 32) return 2;
  return 73;
}
int scan_text(void) {
  int number = 0, count = -1;
  char lower[4] = {0}, upper[4] = {0}, bang = 0, two[3] = {0};
  const char *text = " 12 skip abc-XYZ! %xy";
  if (sscanf(text, " %2d %*s %3[a-z]-%3[A-Z]%c %% %2c%n",
             &number, lower, upper, &bang, two, &count) != 5 || number != 12 ||
      strcmp(lower, "abc") || strcmp(upper, "XYZ") || bang != '!' ||
      memcmp(two, "xy", 2) || count != (int)strlen(text)) return 1;
  char inverted[8] = {0}, bracket[4] = {0};
  if (sscanf("abc,]--", "%7[^,],%3[]-]", inverted, bracket) != 2 ||
      strcmp(inverted, "abc") || strcmp(bracket, "]--")) return 2;
  char one[3] = {0};
  if (sscanf("x", "%2c", one) != 1 || one[0] != 'x' || one[1]) return 3;
  return 73;
}
int scan_list(void) {
  int values[9] = {0};
  if (list_scan("1 2 3 4 5 6 7 8 9", "%d%d%d%d%d%d%d%d%d",
                values, values + 1, values + 2, values + 3, values + 4,
                values + 5, values + 6, values + 7, values + 8) != 9) return 1;
  for (int i = 0; i < 9; ++i) if (values[i] != i + 1) return 2;
  return 73;
}
int scan_failures(void) {
  int value = 73, count = -1;
  if (sscanf("", "%d", &value) != EOF || value != 73) return 1;
  if (sscanf("x", "%d", &value) != 0 || value != 73) return 2;
  if (sscanf("12 x", "%d%d", &value, &count) != 1 || value != 12 || count != -1) return 3;
  if (sscanf("", "%n%d", &count, &value) != EOF || count != 0) return 4;
  signed char small = -1;
  short half = -1;
  long large = -1;
  if (sscanf(" abc", " %hhn%hn%ln", &small, &half, &large) != 0 ||
      small != 1 || half != 1 || large != 1) return 5;
  // Avoid compiler format checking for deliberately unsupported contracts.
  int (*scan)(const char *, const char *, ...) = sscanf;
  _Static_assert(sizeof(long double) == 16, "guest scanf quad destination is binary128");
  long double quad;
  const uint64_t quad_guard[2] = {UINT64_C(0x1122334455667788), UINT64_C(0x8877665544332211)};
  memcpy(&quad, quad_guard, sizeof(quad));
  errno = 0;
  if (scan("1.0", "%Lf", &quad) != EOF || errno != ENOTSUP || memcmp(&quad, quad_guard, sizeof(quad))) return 6;
  errno = 0;
  if (scan("12 1.0", "%d%Lf", &value, &quad) != 1 || value != 12 || errno != ENOTSUP || memcmp(&quad, quad_guard, sizeof(quad))) return 7;
  errno = 0;
  if (scan("abc", "%ls", &quad) != EOF || errno != ENOTSUP) return 8;
  errno = 0;
  if (scan("1", "%999999999d", &value) != EOF || errno != E2BIG) return 9;
  errno = 0;
  if (scan("1", "%d", (int *)0) != EOF || errno != EFAULT) return 10;
  errno = 0;
  if (scan("x", "%[abc", &value) != EOF || errno != EINVAL) return 11;
  return 73;
}
