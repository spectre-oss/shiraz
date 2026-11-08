// SPDX-License-Identifier: GPL-3.0-only
extern int left_value(void), missing_personality_service(void);
volatile int should_not_initialize;
__attribute__((constructor)) static void initialize(void) {
  should_not_initialize = 123;
}
int missing_value(void) { return left_value() + missing_personality_service(); }
