// SPDX-License-Identifier: GPL-3.0-only
#include <stdarg.h>
extern int __android_log_print(int, const char *, const char *, ...);
extern int __android_log_vprint(int, const char *, const char *, va_list);
static int list(const char *format, ...) {
  va_list args; va_start(args, format);
  int result = __android_log_vprint(4, "GuestV", format, args);
  va_end(args); return result;
}
int run_log(void) {
  int result = __android_log_print(4, "Guest", "%d %u %lld %.2f %s %*.*f %d %d %d %d %d %d %d %d",
      -7, 9u, 1234567890123LL, 2.5, "hello", 6, 1, 3.25, 1, 2, 3, 4, 5, 6, 7, 8);
  if (result != 1) return 1;
  result = list("%d %.1f %s %d %d %d %d %d %d %d %d %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f",
      -5, 1.5, "list", 1, 2, 3, 4, 5, 6, 7, 8, 2., 3., 4., 5., 6., 7., 8., 9.);
  if (result != 1) return 2;
  int unchanged = 73;
  if (__android_log_print(4, "Guest", "%n", &unchanged) >= 0 || unchanged != 73) return 3;
  return 0;
}
