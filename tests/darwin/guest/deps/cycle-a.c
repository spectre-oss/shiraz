// SPDX-License-Identifier: GPL-3.0-only
extern int read_b(void);
volatile int a_state;
__attribute__((constructor)) static void initialize(void) {
  a_state = read_b() + 11;
}
int read_a(void) { return a_state; }
