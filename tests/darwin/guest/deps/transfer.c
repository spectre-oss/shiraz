// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <stdint.h>
#include <sys/sendfile.h>
_Static_assert(sizeof(off_t) == 8 && sizeof(off64_t) == 8 && sizeof(ssize_t) == 8,
               "bionic LP64 transfer offset/return ABI");
ssize_t transfer(int output, int input, int64_t *offset, size_t count, int *error) {
  errno = 73;
  ssize_t value = sendfile(output,input,(off_t *)offset,count);
  *error = errno;
  return value;
}
ssize_t transfer64(int output, int input, int64_t *offset, size_t count, int *error) {
  errno = 73;
  ssize_t value = sendfile64(output,input,(off64_t *)offset,count);
  *error = errno;
  return value;
}
