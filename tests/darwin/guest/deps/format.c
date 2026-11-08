// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
static int bounded(char *out, size_t size, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vsnprintf(out, size, format, arguments);
  va_end(arguments);
  return result;
}
static int unbounded(char *out, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vsprintf(out, format, arguments);
  va_end(arguments);
  return result;
}
static int allocated(char **out, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vasprintf(out, format, arguments);
  va_end(arguments);
  return result;
}
static int streamed(FILE *file, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vfprintf(file, format, arguments);
  va_end(arguments);
  return result;
}
static int standard(const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);
  int result = vprintf(format, arguments);
  va_end(arguments);
  return result;
}
int run_format(void) {
  char output[512];
  int count = snprintf(output, sizeof(output), "%d/%ld/%lld/%zu/%x/%.2f/%.*s/%c/%p",
                       -7, 4294967300L, -4294967301LL, (size_t)99, 0x2au,
                       1.25, 3, "abcdef", 'Z', (void *)(uintptr_t)0x1234);
  const char *expected = "-7/4294967300/-4294967301/99/2a/1.25/abc/Z/0x1234";
  CHECK(count == (int)strlen(expected) && !strcmp(output, expected));
  count = bounded(output, sizeof(output), "%d%d%d%d%d%d%d%d%d%d:%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                  1,2,3,4,5,6,7,8,9,10, 1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0);
  expected = "12345678910:1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0";
  CHECK(count == (int)strlen(expected) && !strcmp(output, expected));
  char short_output[5] = {'?', '?', '?', '?', '?'};
  CHECK(bounded(short_output, sizeof(short_output), "%s:%d", "abcdef", 42) == 9);
  CHECK(!strcmp(short_output, "abcd"));
  CHECK(bounded(0, 0, "%s/%d", "abc", 17) == 6);
  char untouched = 'x';
  CHECK(bounded(&untouched, 0, "abc") == 3 && untouched == 'x');
  CHECK(bounded(&untouched, 1, "abc") == 3 && untouched == 0);
  const char bytes[3] = {'a', 'b', 'c'};
  CHECK(bounded(output, sizeof(output), "%.3s", bytes) == 3 && !strcmp(output, "abc"));
  CHECK(bounded(output, sizeof(output), "%*.*s", 6, 3, "abcdef") == 6 && !strcmp(output, "   abc"));
  CHECK(bounded(output, sizeof(output), "a%cb", 0) == 3 && output[0] == 'a' && output[1] == 0 && output[2] == 'b' && output[3] == 0);
  CHECK(sprintf(output, "%lld/%.2f", 9223372036854775807LL, 3.25) == 24);
  CHECK(!strcmp(output, "9223372036854775807/3.25"));
  CHECK(unbounded(output, "%ls/%lc", L"wide", L'W') == 6 && !strcmp(output, "wide/W"));
  char *heap = 0;
  CHECK(asprintf(&heap, "%s/%d", "heap", 73) == 7 && heap && !strcmp(heap, "heap/73"));
  free(heap);
  CHECK(allocated(&heap, "%s/%.1f", "list", 2.5) == 8 && heap && !strcmp(heap, "list/2.5"));
  free(heap);
  FILE *file = fopen("format-data", "w+");
  CHECK(file);
  CHECK(fprintf(file, "direct:%d/%.1f\n", 7, 2.5) == 13);
  CHECK(streamed(file, "list:%d/%.1f\n", 9, 4.5) == 11);
  CHECK(fseek(file, 0, SEEK_SET) == 0);
  memset(output, 0, sizeof(output));
  CHECK(fread(output, 1, 24, file) == 24 && !strcmp(output, "direct:7/2.5\nlist:9/4.5\n"));
  CHECK(fclose(file) == 0);
  // Function-pointer calls avoid compile-time format checking for deliberately
  // unsupported inputs while exercising the actual exported runtime ABI.
  int (*print)(char *, size_t, const char *, ...) = snprintf;
  int marker = 123;
  errno = 0;
  int rejected = print(output, sizeof(output), "before%n", &marker);
  if (rejected != -1) return 8901;
  if (marker != 123) return 8902;
  if (errno != ENOTSUP) return 890300 + errno;
  errno = 0;
  CHECK(print(output, sizeof(output), "%2$d", 1, 2) == -1 && errno == ENOTSUP);
  errno = 0;
  CHECK(print(output, sizeof(output), "%Lf", (long double)1.0) == -1 && errno == ENOTSUP);
  errno = 0;
  CHECK(print(output, sizeof(output), "%1048577d", 1) == -1 && errno == EOVERFLOW);
  errno = 0;
  CHECK(print(output, sizeof(output), "%") == -1 && errno == EINVAL);
  errno = 0;
  CHECK(print(0, 1, "x") == -1 && errno == EFAULT);
  heap = (char *)(uintptr_t)1;
  errno = 0;
  CHECK(allocated(&heap, "%n", &marker) == -1 && !heap && marker == 123 && errno == ENOTSUP);
  CHECK(printf("%s", "") == 0 && standard("%s", "") == 0);
  return 0;
}
