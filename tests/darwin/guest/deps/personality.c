// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <stdlib.h>
#include <string.h>
extern long host_fail(void);
extern void host_barrier(void);
int memory_test(void) {
  char *data = malloc(128);
  if (!data)
    return 1;
  memset(data, 0, 128);
  memcpy(data, "abcdef", 7);
  memmove(data + 2, data, 5);
  int result = memcmp(data, "ababcde", 7) == 0 && strlen(data) == 7;
  free(data);
  unsigned char *zero = calloc(8, 16);
  if (!zero)
    return 2;
  for (int i = 0; i < 128; ++i)
    if (zero[i])
      result = 0;
  free(zero);
  free(0);
  return result ? 73 : 3;
}
int parse_test(void) {
  char *end = 0;
  errno = 0;
  long value = strtol("17tail", &end, 10);
  if (value != 17 || strcmp(end, "tail") || errno)
    return 1;
  errno = 777;
  value = strtol("23", &end, 10);
  if (value != 23 || errno != 777)
    return 2;
  errno = 0;
  value = strtol("999999999999999999999999999999999999", &end, 10);
  if (value <= 0 || *end || errno != ERANGE)
    return 3;
  return 73;
}
int failure_test(void) {
  errno = 0;
  return host_fail() == -1 && errno == EAGAIN ? 73 : 1;
}
int thread_errno(int value) {
  errno = value;
  host_barrier();
  return errno;
}
