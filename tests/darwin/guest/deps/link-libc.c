// SPDX-License-Identifier: GPL-3.0-only
// Link-time API declarations only. Every body traps if accidentally executed.
// Runtime tests require the explicit libc personality, never this ELF artifact.
#include <stddef.h>
int *__errno(void) { __builtin_trap(); }
void *malloc(size_t n) {
  (void)n;
  __builtin_trap();
}
void *calloc(size_t n, size_t size) {
  (void)n;
  (void)size;
  __builtin_trap();
}
void free(void *p) {
  (void)p;
  __builtin_trap();
}
void *memcpy(void *d, const void *s, size_t n) {
  (void)d;
  (void)s;
  (void)n;
  __builtin_trap();
}
void *memmove(void *d, const void *s, size_t n) {
  (void)d;
  (void)s;
  (void)n;
  __builtin_trap();
}
void *memset(void *d, int c, size_t n) {
  (void)d;
  (void)c;
  (void)n;
  __builtin_trap();
}
int memcmp(const void *a, const void *b, size_t n) {
  (void)a;
  (void)b;
  (void)n;
  __builtin_trap();
}
size_t strlen(const char *s) {
  (void)s;
  __builtin_trap();
}
int strcmp(const char *a, const char *b) {
  (void)a;
  (void)b;
  __builtin_trap();
}
long strtol(const char *s, char **e, int b) {
  (void)s;
  (void)e;
  (void)b;
  __builtin_trap();
}
