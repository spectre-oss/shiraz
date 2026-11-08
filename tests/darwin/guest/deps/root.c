// SPDX-License-Identifier: GPL-3.0-only
extern int left_state, right_state, left_value(void), right_marker(void),
    selected(void);
extern int optional_feature(void) __attribute__((weak));
volatile int root_state;
__attribute__((constructor)) static void initialize(void) {
  root_state = left_state + right_state;
}
int shadowed(void) { return 100; }
int root_value(void) {
  return left_value() + right_marker() + selected() + root_state;
}
int weak_value(void) { return optional_feature ? optional_feature() : 0; }
