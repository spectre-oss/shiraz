// SPDX-License-Identifier: GPL-3.0-only
#include <stdint.h>
#include <stdlib.h>
extern int __cxa_atexit(void (*)(void *), void *, void *);
extern void __cxa_finalize(void *);
struct Output { int count; int values[64]; int errors; };
static struct Output *output;
static int dso_a, dso_b;
static void append(int value) {
  if (output && output->count < 64) output->values[output->count++] = value;
}
static void record(void *value) { append((int)(intptr_t)value); }
static void constructor_destructor(void *unused) { (void)unused; append(0); }
__attribute__((constructor)) static void initialize(void) {
  if (__cxa_atexit(constructor_destructor, 0, &dso_a)) __builtin_trap();
}
static void plain(void) { append(4); }
static void recursive(void *unused) {
  (void)unused;
  append(5);
  if (__cxa_atexit(record, (void *)6, &dso_a)) ++output->errors;
  __cxa_finalize(&dso_a);
}
void lifecycle_stage(struct Output *destination) {
  output = destination;
  if (__cxa_atexit(record, (void *)1, &dso_a) ||
      __cxa_atexit(record, (void *)2, &dso_b) ||
      __cxa_atexit(record, (void *)3, &dso_a) ||
      atexit(plain) || __cxa_atexit(recursive, 0, &dso_a)) ++output->errors;
}
void lifecycle_finalize_a(void) { __cxa_finalize(&dso_a); }
void lifecycle_finalize_b(void) { __cxa_finalize(&dso_b); }
void lifecycle_finalize_all(void) { __cxa_finalize(0); }
void lifecycle_shutdown_stage(struct Output *destination) {
  output = destination;
  if (__cxa_atexit(record, (void *)9, &dso_a) || atexit(plain)) ++output->errors;
}
void *lifecycle_callback(void) { return (void *)record; }
void *lifecycle_key_a(void) { return &dso_a; }
