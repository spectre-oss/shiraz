// SPDX-License-Identifier: GPL-3.0-only
extern int read_a(void);
volatile int b_state;
__attribute__((constructor)) static void initialize(void) {
  b_state = read_a() + 22;
}
int read_b(void) { return b_state; }
