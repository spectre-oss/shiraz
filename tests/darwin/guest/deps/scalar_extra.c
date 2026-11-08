// SPDX-License-Identifier: GPL-3.0-only
#undef _GNU_SOURCE
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static int calls;
static int integer_compare(const void *left, const void *right) {
  ++calls;
  int a = *(const int *)left, b = *(const int *)right;
  return a < b ? -1 : a > b ? 1 : 0;
}
int scalar_extra_strings(void) {
  if (strspn("abc123", "cab") != 3 || strcspn("abc123", "13") != 3) return 1;
  char *copy = strdup("guest-owned-copy");
  if (!copy || strcmp(copy, "guest-owned-copy")) return 2;
  copy[0] = 'G';
  free(copy);
  errno = 73;
  char text[1024], tiny[4];
  if (strerror_r(EDEADLK, text, sizeof(text)) || !*text || errno != 73) return 3;
  if (!strstr(text, "deadlock") && !strstr(text, "Deadlock")) return 4;
  if (strerror_r(EDEADLK, tiny, sizeof(tiny)) != ERANGE || tiny[3] || errno != 73) return 5;
  if (strerror_r(98765, text, sizeof(text)) || strcmp(text, "Unknown error 98765") || errno != 73) return 6;
  if (strcmp(strerror(0), "Success") || errno != 73) return 7;
  if (strcmp(strerror(98765), "Unknown error 98765") || errno != 73) return 8;
  if (strerror_r(EINVAL, text, 0) != ERANGE || errno != 73) return 9;
  return 73;
}
int scalar_extra_random(void) {
  static const int expected[] = {1804289383, 846930886, 1681692777, 1714636915, 1957747793};
  srand(1);
  for (int i = 0; i < 5; ++i) if (rand() != expected[i]) return 1;
  int sequence[8];
  srand(0xffffffffU);
  for (int i = 0; i < 8; ++i) sequence[i] = rand();
  srand(0xffffffffU);
  for (int i = 0; i < 8; ++i) if (rand() != sequence[i] || sequence[i] < 0) return 2;
  return 73;
}
int scalar_extra_sort(void) {
  int values[] = {73, -9, 0, 42, 42, -1, 100, -999};
  calls = 0;
  errno = 73;
  qsort(values, 8, sizeof(int), integer_compare);
  if (!calls || errno != 73) return 1;
  for (int i = 1; i < 8; ++i) if (values[i - 1] > values[i]) return 2;
  qsort(values, 1, sizeof(int), integer_compare);
  return 73;
}
int scalar_extra_perror(void) {
  errno = EDEADLK;
  perror("guest-error");
  return errno == EDEADLK ? 73 : 1;
}
int scalar_extra_bad_callback(void) {
  int values[2] = {2, 1};
  qsort(values, 2, sizeof(int), (int (*)(const void *, const void *))(uintptr_t)1);
  return 1;
}
int scalar_extra_bad_extent(void) {
  int values[2] = {2, 1};
  volatile size_t count = SIZE_MAX;
  qsort(values, count, sizeof(int), integer_compare);
  return 1;
}
