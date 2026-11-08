// SPDX-License-Identifier: GPL-3.0-only
#ifndef SHIRAZ_BIONIC_REFERENCE_CHILD_H
#define SHIRAZ_BIONIC_REFERENCE_CHILD_H
#include <stddef.h>
#define SHIRAZ_REFERENCE_OUTPUT_MAX 65536u
struct shiraz_reference_child {
  int returncode;
  int raw_wait_status;
  int timed_out;
  size_t output_size;
  unsigned char output[SHIRAZ_REFERENCE_OUTPUT_MAX];
};
// deadline_ms is bounded to 1..120000. A timeout is a collected (failed) run;
// collection errors, including output overflow, return -1 with errno preserved.
int shiraz_reference_collect(char *const argv[], unsigned deadline_ms,
                            struct shiraz_reference_child *result);
#endif
