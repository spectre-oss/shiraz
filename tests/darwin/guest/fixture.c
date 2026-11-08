// SPDX-License-Identifier: GPL-3.0-only
// Compiled for AArch64 Android, without libc/NDK. Synthetic M5 fixture only.
#include <stdarg.h>

volatile long guest_state;
volatile long guest_bss[8];
volatile long *guest_pointer = &guest_state;
#ifdef SHIRAZ_DESCRIPTOR_GATE
extern int guest_descriptor_cases(void);
#endif
__attribute__((constructor)) static void setup(void) {
  *guest_pointer = 73;
  guest_bss[7] = 91;
#ifdef SHIRAZ_DESCRIPTOR_GATE
  if (guest_descriptor_cases()) *guest_pointer = -1000;
#endif
}
__attribute__((noinline)) long guest_leaf(void) { return guest_state; }
long guest_read(void) { return *guest_pointer + guest_bss[7] + guest_leaf(); }
long guest_twelve(int a, int b, int c, int d, int e, int f, int g, int h, int i,
                  int j, int k, int l) {
  return a + 2L * b + 3L * c + 4L * d + 5L * e + 6L * f + 7L * g + 8L * h +
         9L * i + 10L * j + 11L * k + 12L * l;
}
double guest_mixed(int a, double b, signed char c, float d, short e, double f,
                   int g, double h, int i, double j, int k, double l, int m,
                   double n, int o, double p, int q, double r, int s, double t,
                   int u, double v, int w, double x) {
  return a + 2 * b + 3 * c + 4 * d + 5 * e + 6 * f + 7 * g + 8 * h + 9 * i +
         10 * j + 11 * k + 12 * l + 13 * m + 14 * n + 15 * o + 16 * p + 17 * q +
         18 * r + 19 * s + 20 * t + 21 * u + 22 * v + 23 * w + 24 * x;
}
double guest_variadic(int count, ...) {
  va_list args;
  va_start(args, count);
  double result = 0;
  for (int i = 0; i < count; ++i) {
    int integer = va_arg(args, int);
    double real = va_arg(args, double);
    result += (i + 1) * (integer + real);
  }
  va_end(args);
  return result;
}

// The guest passes its genuine AAPCS64 va_list to a host adapter. The host
// callback has a fixed signature and explicitly decodes this guest
// representation.
double guest_callback(double (*callback)(int, va_list), int count, ...) {
  va_list args;
  va_start(args, count);
  double result = callback(count, args);
  va_end(args);
  return result;
}
